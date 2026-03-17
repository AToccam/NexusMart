package com.nexusmart.seckill;

import com.nexusmart.seckill.service.GoodsService;
import com.nexusmart.seckill.vo.SeckillGoodsVo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 商品详情缓存验证：同一个 goodsId 连续查询应命中缓存。
 */
@SpringBootTest
class GoodsCacheTest {

    @Autowired
    private GoodsService goodsService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    void testGoodsDetailCacheHit() {
        Long goodsId = 1L;
        String key = GoodsService.GOODS_DETAIL_KEY_PREFIX + goodsId;

        redisTemplate.delete(key);

        SeckillGoodsVo first = goodsService.getGoodsDetail(goodsId);
        assertNotNull(first, "首次查询应能从 DB 回源得到商品详情");

        String cached = redisTemplate.opsForValue().get(key);
        assertNotNull(cached, "首次查询后应写入缓存");

        SeckillGoodsVo second = goodsService.getGoodsDetail(goodsId);
        assertNotNull(second, "第二次查询应命中缓存并返回结果");
        assertEquals(first.getGoodsId(), second.getGoodsId());
        assertEquals(first.getGoodsName(), second.getGoodsName());

        Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        assertNotNull(ttl, "缓存 TTL 应存在");
        assertTrue(ttl > 0, "缓存 TTL 应大于 0");
    }

    @Test
    void testGoodsDetailEmptyCacheForNonexistentGoods() {
        Long goodsId = 99999999L;
        String key = GoodsService.GOODS_DETAIL_KEY_PREFIX + goodsId;

        redisTemplate.delete(key);

        SeckillGoodsVo detail = goodsService.getGoodsDetail(goodsId);
        assertNull(detail, "不存在商品应返回 null");

        String cached = redisTemplate.opsForValue().get(key);
        assertEquals("EMPTY", cached, "不存在商品应写入 EMPTY 占位，防止缓存穿透");
    }
}
