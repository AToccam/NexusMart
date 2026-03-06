package com.nexusmart.seckill.service;

import com.nexusmart.seckill.config.SeckillInitRunner;
import com.nexusmart.seckill.entity.*;
import com.nexusmart.seckill.mapper.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SeckillService {

    @Autowired
    private SeckillGoodsMapper seckillGoodsMapper;
    @Autowired
    private GoodsMapper goodsMapper;
    @Autowired
    private OrderInfoMapper orderInfoMapper;
    @Autowired
    private SeckillOrderMapper seckillOrderMapper;
    @Autowired
    private UserInfoMapper userInfoMapper;
    @Autowired
    private StringRedisTemplate redisTemplate;

    /**
     * 执行秒杀：扣库存 + 创建订单（事务保证原子性）
     *
     * @param userId   买家 ID
     * @param seckillId 秒杀商品 ID
     * @return 生成的订单信息
     */
    @Transactional
    public OrderInfo doSeckill(Long userId, Long seckillId) {
        // 1. 校验用户（暂时跳过，允许任意 userId）
        // UserInfo user = userInfoMapper.selectById(userId);
        // if (user == null) {
        //     throw new RuntimeException("用户不存在");
        // }

        // ====== Redis 预检：快速拦截，减少数据库压力 ======

        // 2. Redis 防重复下单（检查 Set 中是否已存在该用户）
        String orderKey = SeckillInitRunner.ORDER_KEY_PREFIX + seckillId;
        if (Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(orderKey, String.valueOf(userId)))) {
            throw new RuntimeException("您已经抢过该商品，不能重复下单");
        }

        // 3. Redis 预扣库存（原子 decrement，返回扣减后的值）
        String stockKey = SeckillInitRunner.STOCK_KEY_PREFIX + seckillId;
        Long remain = redisTemplate.opsForValue().decrement(stockKey);
        if (remain == null || remain < 0) {
            // 库存不足，回补刚扣的 1
            redisTemplate.opsForValue().increment(stockKey);
            throw new RuntimeException("库存不足，秒杀失败");
        }

        // ====== 通过 Redis 预检，进入数据库操作 ======

        try {
            // 4. 查询秒杀商品
            SeckillGoods seckillGoods = seckillGoodsMapper.selectById(seckillId);
            if (seckillGoods == null) {
                throw new RuntimeException("秒杀商品不存在");
            }

            // 5. 数据库防重复下单（兜底）
            SeckillOrder existing = seckillOrderMapper.selectByUserIdAndGoodsId(userId, seckillGoods.getGoodsId());
            if (existing != null) {
                throw new RuntimeException("您已经抢过该商品，不能重复下单");
            }

            // 6. 乐观锁扣减数据库秒杀库存
            int affected = seckillGoodsMapper.decreaseStockByOptimisticLock(seckillId, seckillGoods.getVersion());
            if (affected == 0) {
                throw new RuntimeException("库存不足或并发冲突，秒杀失败");
            }

            // 7. 查询商品基本信息，用于创建订单快照
            Goods goods = goodsMapper.selectById(seckillGoods.getGoodsId());

            // 8. 创建完整订单
            OrderInfo order = new OrderInfo();
            order.setUserId(userId);
            order.setMerchantId(goods.getMerchantId());
            order.setGoodsId(goods.getId());
            order.setGoodsName(goods.getGoodsName());
            order.setOrderPrice(seckillGoods.getSeckillPrice());
            order.setStatus(0); // 新建未支付
            orderInfoMapper.insert(order);

            // 9. 创建秒杀防重订单
            SeckillOrder seckillOrder = new SeckillOrder();
            seckillOrder.setUserId(userId);
            seckillOrder.setOrderId(order.getId());
            seckillOrder.setGoodsId(goods.getId());
            seckillOrderMapper.insert(seckillOrder);

            // 10. 数据库成功后，Redis 记录该用户已购买
            redisTemplate.opsForSet().add(orderKey, String.valueOf(userId));

            return order;
        } catch (RuntimeException e) {
            // 数据库操作失败，回补 Redis 库存
            redisTemplate.opsForValue().increment(stockKey);
            throw e;
        }
    }
}
