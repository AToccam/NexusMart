package com.nexusmart.seckill.service;

import com.nexusmart.seckill.common.OrderStatus;
import com.nexusmart.seckill.common.SeckillRedisKeys;
import com.nexusmart.seckill.config.SeckillInitRunner;
import com.nexusmart.seckill.mq.SeckillOrderMessage;
import com.nexusmart.seckill.util.SnowflakeIdGenerator;
import com.nexusmart.seckill.vo.SeckillSubmitResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Arrays;

@Service
public class SeckillService {

    private static final DefaultRedisScript<Long> PRE_DEDUCT_SCRIPT = new DefaultRedisScript<>();

    static {
        PRE_DEDUCT_SCRIPT.setResultType(Long.class);
        PRE_DEDUCT_SCRIPT.setScriptText(
                "if redis.call('SISMEMBER', KEYS[2], ARGV[1]) == 1 then return -2 end " +
                "local stock = tonumber(redis.call('GET', KEYS[1]) or '-1') " +
                "if stock <= 0 then return -1 end " +
                "redis.call('DECR', KEYS[1]) " +
                "redis.call('SADD', KEYS[2], ARGV[1]) " +
                "redis.call('HSET', KEYS[3], " +
                "'userId', ARGV[1], " +
                "'seckillId', ARGV[2], " +
                "'requestId', ARGV[3], " +
                "'orderNo', ARGV[4], " +
                "'createdAt', ARGV[5]) " +
                "redis.call('EXPIRE', KEYS[3], tonumber(ARGV[6])) " +
                "redis.call('ZADD', KEYS[4], tonumber(ARGV[5]), ARGV[3]) " +
                "redis.call('SET', KEYS[5], 'QUEUING', 'EX', tonumber(ARGV[7])) " +
                "return 1");
    }

    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private SnowflakeIdGenerator snowflakeIdGenerator;
    @Autowired
    private SeckillOrderProducer seckillOrderProducer;
    @Autowired
    private SeckillCompensationService compensationService;

    @Value("${app.seckill.pending-ttl-seconds:600}")
    private long pendingTtlSeconds;

    @Value("${app.seckill.result-ttl-seconds:86400}")
    private long resultTtlSeconds;

    /**
     * 快速返回模式：入口只做 Redis 原子校验与预扣，成功后投递 Kafka 消息。
     *
     * @param userId    买家 ID
     * @param seckillId 秒杀商品 ID
     * @return 排队结果（requestId + orderNo）
     */
    public SeckillSubmitResponse submitSeckill(Long userId, Long seckillId) {
        long orderNo = snowflakeIdGenerator.nextId();
        String requestId = "REQ-" + orderNo;
        long now = System.currentTimeMillis();

        String stockKey = SeckillInitRunner.STOCK_KEY_PREFIX + seckillId;
        String orderSetKey = SeckillInitRunner.ORDER_KEY_PREFIX + seckillId;
        String pendingKey = SeckillRedisKeys.pendingKey(requestId);
        String resultKey = SeckillRedisKeys.resultKey(requestId);

        Long luaResult = redisTemplate.execute(
                PRE_DEDUCT_SCRIPT,
                Arrays.asList(stockKey, orderSetKey, pendingKey, SeckillRedisKeys.PENDING_INDEX_KEY, resultKey),
                String.valueOf(userId),
                String.valueOf(seckillId),
                requestId,
                String.valueOf(orderNo),
                String.valueOf(now),
                String.valueOf(pendingTtlSeconds),
                String.valueOf(resultTtlSeconds));

        if (luaResult == null) {
            throw new RuntimeException("系统繁忙，请稍后重试");
        }
        if (luaResult == -1L) {
            throw new RuntimeException("库存不足或活动未开始");
        }
        if (luaResult == -2L) {
            throw new RuntimeException("您已经抢过该商品，不能重复下单");
        }
        if (luaResult != 1L) {
            throw new RuntimeException("秒杀请求校验失败");
        }

        SeckillOrderMessage message = new SeckillOrderMessage();
        message.setRequestId(requestId);
        message.setOrderNo(orderNo);
        message.setUserId(userId);
        message.setSeckillId(seckillId);
        message.setCreatedAt(now);

        try {
            seckillOrderProducer.sendCreateOrderMessage(message);
        } catch (RuntimeException e) {
            compensationService.markFailedAndRollback(requestId, "PUBLISH_FAILED");
            throw new RuntimeException("秒杀请求入队失败，请稍后重试", e);
        }

        SeckillSubmitResponse response = new SeckillSubmitResponse();
        response.setRequestId(requestId);
        response.setOrderNo(orderNo);
        response.setStatus(OrderStatus.QUEUING.getCode());
        response.setStatusDesc(OrderStatus.QUEUING.getDesc());
        return response;
    }
}
