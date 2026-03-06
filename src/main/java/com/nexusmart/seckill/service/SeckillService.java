package com.nexusmart.seckill.service;

import com.nexusmart.seckill.entity.*;
import com.nexusmart.seckill.mapper.*;
import org.springframework.beans.factory.annotation.Autowired;
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

    /**
     * 执行秒杀：扣库存 + 创建订单（事务保证原子性）
     *
     * @param userId   买家 ID
     * @param seckillId 秒杀商品 ID
     * @return 生成的订单信息
     */
    @Transactional
    public OrderInfo doSeckill(Long userId, Long seckillId) {
        // 1. 校验用户
        UserInfo user = userInfoMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        // 2. 查询秒杀商品
        SeckillGoods seckillGoods = seckillGoodsMapper.selectById(seckillId);
        if (seckillGoods == null) {
            throw new RuntimeException("秒杀商品不存在");
        }
        if (seckillGoods.getStockCount() <= 0) {
            throw new RuntimeException("库存不足，秒杀失败");
        }

        // 3. 防重复下单
        SeckillOrder existing = seckillOrderMapper.selectByUserIdAndGoodsId(userId, seckillGoods.getGoodsId());
        if (existing != null) {
            throw new RuntimeException("您已经抢过该商品，不能重复下单");
        }

        // 4. 乐观锁扣减秒杀库存
        int affected = seckillGoodsMapper.decreaseStockByOptimisticLock(seckillId, seckillGoods.getVersion());
        if (affected == 0) {
            throw new RuntimeException("库存不足或并发冲突，秒杀失败");
        }

        // 5. 查询商品基本信息，用于创建订单快照
        Goods goods = goodsMapper.selectById(seckillGoods.getGoodsId());

        // 6. 创建完整订单
        OrderInfo order = new OrderInfo();
        order.setUserId(userId);
        order.setMerchantId(goods.getMerchantId());
        order.setGoodsId(goods.getId());
        order.setGoodsName(goods.getGoodsName());
        order.setOrderPrice(seckillGoods.getSeckillPrice());
        order.setStatus(0); // 新建未支付
        orderInfoMapper.insert(order);

        // 7. 创建秒杀防重订单
        SeckillOrder seckillOrder = new SeckillOrder();
        seckillOrder.setUserId(userId);
        seckillOrder.setOrderId(order.getId());
        seckillOrder.setGoodsId(goods.getId());
        seckillOrderMapper.insert(seckillOrder);

        return order;
    }
}
