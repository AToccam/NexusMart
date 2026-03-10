package com.nexusmart.seckill.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.nexusmart.seckill.config.SeckillInitRunner;
import com.nexusmart.seckill.entity.*;
import com.nexusmart.seckill.mapper.*;
import com.nexusmart.seckill.util.RedisCacheUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SeckillService {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @Autowired
    private SeckillGoodsMapper seckillGoodsMapper;
    @Autowired
    private GoodsMapper goodsMapper;
    @Autowired
    private OrderInfoMapper orderInfoMapper;
    @Autowired
    private SeckillOrderMapper seckillOrderMapper;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private RedisCacheUtil cacheUtil;

    /**
     * 执行秒杀：扣库存 + 创建订单（事务保证原子性）
     * <p>
     * 集成三大 Redis 防护：
     * 1. 缓存穿透 — 不存在的 seckillId 写入空对象，60 秒过期
     * 2. 缓存雪崩 — 商品详情 TTL 随机化（3600 + random(600)）
     * 3. 缓存击穿 — 热点 Key 过期时用互斥锁重建，防止万人同时查 DB
     *
     * @param userId    买家 ID（压测模式下无需真实存在）
     * @param seckillId 秒杀商品 ID
     * @return 生成的订单信息
     */
    @Transactional
    public OrderInfo doSeckill(Long userId, Long seckillId) {

        // ====== 第一层：Redis 缓存查询秒杀商品（防穿透 + 防击穿 + 防雪崩） ======
        String goodsKey = SeckillInitRunner.GOODS_KEY_PREFIX + seckillId;
        String lockName = "seckillGoods:" + seckillId;

        // 使用互斥锁方式查询缓存（防击穿）
        // 内部逻辑：有缓存直接返回；缓存是空对象返回 null（防穿透）；
        //          缓存不存在则抢锁查 DB 并回写（TTL 随机化，防雪崩）
        String goodsJson = cacheUtil.getWithMutex(goodsKey, lockName, () -> {
            // 回源 DB
            SeckillGoods db = seckillGoodsMapper.selectById(seckillId);
            if (db == null) return null;
            try {
                return MAPPER.writeValueAsString(db);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, 3600, 600);

        // 缓存穿透拦截：空对象 → 说明 DB 里根本没有这个 seckillId
        if (goodsJson == null) {
            throw new RuntimeException("秒杀商品不存在");
        }

        SeckillGoods cachedGoods;
        try {
            cachedGoods = MAPPER.readValue(goodsJson, SeckillGoods.class);
        } catch (Exception e) {
            throw new RuntimeException("缓存数据解析失败", e);
        }

        // ====== 第二层：Redis 预检快速拦截 ======

        // Redis 防重复下单
        String orderKey = SeckillInitRunner.ORDER_KEY_PREFIX + seckillId;
        if (Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(orderKey, String.valueOf(userId)))) {
            throw new RuntimeException("您已经抢过该商品，不能重复下单");
        }

        // Redis 预扣库存（原子 decrement）
        String stockKey = SeckillInitRunner.STOCK_KEY_PREFIX + seckillId;
        Long remain = redisTemplate.opsForValue().decrement(stockKey);
        if (remain == null || remain < 0) {
            redisTemplate.opsForValue().increment(stockKey);
            throw new RuntimeException("库存不足，秒杀失败");
        }

        // ====== 第三层：数据库操作（兜底） ======

        try {
            // 数据库查最新版本号（乐观锁需要）
            SeckillGoods seckillGoods = seckillGoodsMapper.selectById(seckillId);

            // 数据库防重复下单（兜底）
            SeckillOrder existing = seckillOrderMapper.selectByUserIdAndGoodsId(userId, cachedGoods.getGoodsId());
            if (existing != null) {
                throw new RuntimeException("您已经抢过该商品，不能重复下单");
            }

            // 乐观锁扣减数据库秒杀库存
            int affected = seckillGoodsMapper.decreaseStockByOptimisticLock(seckillId, seckillGoods.getVersion());
            if (affected == 0) {
                throw new RuntimeException("库存不足或并发冲突，秒杀失败");
            }

            // 查询商品基本信息，用于创建订单快照
            Goods goods = goodsMapper.selectById(cachedGoods.getGoodsId());

            // 创建完整订单
            OrderInfo order = new OrderInfo();
            order.setUserId(userId);
            order.setMerchantId(goods.getMerchantId());
            order.setGoodsId(goods.getId());
            order.setGoodsName(goods.getGoodsName());
            order.setOrderPrice(cachedGoods.getSeckillPrice());
            order.setStatus(0);
            orderInfoMapper.insert(order);

            // 创建秒杀防重订单
            SeckillOrder seckillOrder = new SeckillOrder();
            seckillOrder.setUserId(userId);
            seckillOrder.setOrderId(order.getId());
            seckillOrder.setGoodsId(goods.getId());
            seckillOrderMapper.insert(seckillOrder);

            // 数据库成功后，Redis 记录该用户已购买
            redisTemplate.opsForSet().add(orderKey, String.valueOf(userId));

            // 更新缓存中的库存快照（防止前端展示旧值）
            cachedGoods.setStockCount(cachedGoods.getStockCount() - 1);
            cachedGoods.setVersion(seckillGoods.getVersion() + 1);
            try {
                cacheUtil.setWithRandomTTL(goodsKey, MAPPER.writeValueAsString(cachedGoods), 3600, 600);
            } catch (Exception ignored) {
                // 缓存更新失败不影响主流程
            }

            return order;
        } catch (RuntimeException e) {
            // 数据库操作失败，回补 Redis 库存
            redisTemplate.opsForValue().increment(stockKey);
            throw e;
        }
    }
}
