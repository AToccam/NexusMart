package com.nexusmart.seckill.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.nexusmart.seckill.entity.SeckillGoods;
import com.nexusmart.seckill.mapper.SeckillGoodsMapper;
import com.nexusmart.seckill.util.RedisCacheUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 应用启动时，将秒杀商品库存 + 商品详情预热到 Redis
 */
@Component
public class SeckillInitRunner implements ApplicationRunner {

    public static final String STOCK_KEY_PREFIX = "seckill:stock:";
    public static final String ORDER_KEY_PREFIX = "seckill:order:";
    public static final String GOODS_KEY_PREFIX = "seckill:goods:";

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @Autowired
    private SeckillGoodsMapper seckillGoodsMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RedisCacheUtil cacheUtil;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        List<SeckillGoods> list = seckillGoodsMapper.selectOngoing();
        for (SeckillGoods sg : list) {
            // 预热库存
            redisTemplate.opsForValue().set(
                    STOCK_KEY_PREFIX + sg.getId(),
                    String.valueOf(sg.getStockCount()));

            // 预热商品详情（TTL 随机化，防雪崩：基础 3600 秒 + 随机 0~600 秒）
            String json = MAPPER.writeValueAsString(sg);
            cacheUtil.setWithRandomTTL(
                    GOODS_KEY_PREFIX + sg.getId(), json, 3600, 600);
        }
        System.out.println("========== Redis 秒杀库存 & 商品详情预热完成，共 " + list.size() + " 个商品 ==========");
    }
}
