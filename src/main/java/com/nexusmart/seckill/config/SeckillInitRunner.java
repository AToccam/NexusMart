package com.nexusmart.seckill.config;

import com.nexusmart.seckill.entity.SeckillGoods;
import com.nexusmart.seckill.mapper.SeckillGoodsMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 应用启动时，将秒杀商品库存预热到 Redis
 */
@Component
public class SeckillInitRunner implements ApplicationRunner {

    public static final String STOCK_KEY_PREFIX = "seckill:stock:";
    public static final String ORDER_KEY_PREFIX = "seckill:order:";

    @Autowired
    private SeckillGoodsMapper seckillGoodsMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Override
    public void run(ApplicationArguments args) {
        List<SeckillGoods> list = seckillGoodsMapper.selectOngoing();
        for (SeckillGoods sg : list) {
            // key = seckill:stock:{seckillId}  value = 库存数
            redisTemplate.opsForValue().set(
                    STOCK_KEY_PREFIX + sg.getId(),
                    String.valueOf(sg.getStockCount()));
        }
        System.out.println("========== Redis 秒杀库存预热完成，共 " + list.size() + " 个商品 ==========");
    }
}
