package com.nexusmart.seckill.service;

import com.nexusmart.seckill.common.SeckillRedisKeys;
import com.nexusmart.seckill.config.SeckillInitRunner;
import com.nexusmart.seckill.entity.OrderInfo;
import com.nexusmart.seckill.mapper.OrderInfoMapper;
import com.nexusmart.seckill.mq.SeckillOrderMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
public class SeckillCompensationService {

    private final StringRedisTemplate redisTemplate;
    private final OrderInfoMapper orderInfoMapper;

    @Value("${app.seckill.pending-ttl-seconds:600}")
    private long pendingTtlSeconds;

    @Value("${app.seckill.result-ttl-seconds:86400}")
    private long resultTtlSeconds;

    @Value("${app.seckill.idempotent-ttl-seconds:604800}")
    private long idempotentTtlSeconds;

    @Value("${app.seckill.compensation.timeout-ms:120000}")
    private long compensationTimeoutMs;

    @Value("${app.seckill.compensation.batch-size:200}")
    private long compensationBatchSize;

    public SeckillCompensationService(StringRedisTemplate redisTemplate,
                                      OrderInfoMapper orderInfoMapper) {
        this.redisTemplate = redisTemplate;
        this.orderInfoMapper = orderInfoMapper;
    }

    public boolean isMessageDone(String requestId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(SeckillRedisKeys.messageDoneKey(requestId)));
    }

    public boolean tryAcquireMessageLock(String requestId, long seconds) {
        Boolean ok = redisTemplate.opsForValue().setIfAbsent(
                SeckillRedisKeys.messageLockKey(requestId),
                "1",
                seconds,
                TimeUnit.SECONDS);
        return Boolean.TRUE.equals(ok);
    }

    public void releaseMessageLock(String requestId) {
        redisTemplate.delete(SeckillRedisKeys.messageLockKey(requestId));
    }

    public void markSuccess(SeckillOrderMessage message, OrderInfo order) {
        String doneKey = SeckillRedisKeys.messageDoneKey(message.getRequestId());
        redisTemplate.opsForValue().set(doneKey, "SUCCESS", idempotentTtlSeconds, TimeUnit.SECONDS);

        String resultVal = "SUCCESS:" + order.getOrderNo() + ":" + order.getId();
        redisTemplate.opsForValue().set(
                SeckillRedisKeys.resultKey(message.getRequestId()),
                resultVal,
                resultTtlSeconds,
                TimeUnit.SECONDS);

        cleanupPending(message.getRequestId());
    }

    public void markFailedAndRollback(String requestId, String reason) {
        String lockKey = SeckillRedisKeys.compensateLockKey(requestId);
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", 20, TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(locked)) {
            return;
        }

        try {
            String doneKey = SeckillRedisKeys.messageDoneKey(requestId);
            if (Boolean.TRUE.equals(redisTemplate.hasKey(doneKey))) {
                cleanupPending(requestId);
                return;
            }

            String pendingKey = SeckillRedisKeys.pendingKey(requestId);
            Map<Object, Object> pending = redisTemplate.opsForHash().entries(pendingKey);
            if (pending == null || pending.isEmpty()) {
                redisTemplate.opsForZSet().remove(SeckillRedisKeys.PENDING_INDEX_KEY, requestId);
                return;
            }

            Long orderNo = parseLong(pending.get("orderNo"));
            if (orderNo != null) {
                OrderInfo existed = orderInfoMapper.selectByOrderNo(orderNo);
                if (existed != null) {
                    redisTemplate.opsForValue().set(doneKey, "SUCCESS", idempotentTtlSeconds, TimeUnit.SECONDS);
                    redisTemplate.opsForValue().set(
                            SeckillRedisKeys.resultKey(requestId),
                            "SUCCESS:" + existed.getOrderNo() + ":" + existed.getId(),
                            resultTtlSeconds,
                            TimeUnit.SECONDS);
                    cleanupPending(requestId);
                    return;
                }
            }

            Long userId = parseLong(pending.get("userId"));
            Long seckillId = parseLong(pending.get("seckillId"));
            if (userId != null && seckillId != null) {
                redisTemplate.opsForValue().increment(SeckillInitRunner.STOCK_KEY_PREFIX + seckillId);
                redisTemplate.opsForSet().remove(SeckillInitRunner.ORDER_KEY_PREFIX + seckillId, String.valueOf(userId));
            }

            redisTemplate.opsForValue().set(doneKey, "FAILED:" + reason, idempotentTtlSeconds, TimeUnit.SECONDS);
            redisTemplate.opsForValue().set(
                    SeckillRedisKeys.resultKey(requestId),
                    "FAILED:" + reason,
                    resultTtlSeconds,
                    TimeUnit.SECONDS);

            cleanupPending(requestId);
        } finally {
            redisTemplate.delete(lockKey);
        }
    }

    @Scheduled(fixedDelayString = "${app.seckill.compensation.scan-interval-ms:10000}")
    public void scanAndCompensateTimeoutRequests() {
        long deadline = System.currentTimeMillis() - compensationTimeoutMs;
        Set<String> requestIds = redisTemplate.opsForZSet().rangeByScore(
                SeckillRedisKeys.PENDING_INDEX_KEY,
                0,
                deadline,
                0,
                compensationBatchSize);

        if (requestIds == null || requestIds.isEmpty()) {
            return;
        }

        for (String requestId : requestIds) {
            markFailedAndRollback(requestId, "TIMEOUT_COMPENSATED");
        }
    }

    private void cleanupPending(String requestId) {
        redisTemplate.delete(SeckillRedisKeys.pendingKey(requestId));
        redisTemplate.opsForZSet().remove(SeckillRedisKeys.PENDING_INDEX_KEY, requestId);
    }

    public long getPendingTtlSeconds() {
        return pendingTtlSeconds;
    }

    public long getResultTtlSeconds() {
        return resultTtlSeconds;
    }

    private Long parseLong(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}