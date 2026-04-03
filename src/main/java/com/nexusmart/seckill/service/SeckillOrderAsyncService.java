package com.nexusmart.seckill.service;

import com.nexusmart.seckill.common.OrderStatus;
import com.nexusmart.seckill.config.datasource.WriteDataSource;
import com.nexusmart.seckill.entity.Goods;
import com.nexusmart.seckill.entity.OrderInfo;
import com.nexusmart.seckill.entity.SeckillGoods;
import com.nexusmart.seckill.entity.SeckillOrder;
import com.nexusmart.seckill.mapper.GoodsMapper;
import com.nexusmart.seckill.mapper.OrderInfoMapper;
import com.nexusmart.seckill.mapper.SeckillGoodsMapper;
import com.nexusmart.seckill.mapper.SeckillOrderMapper;
import com.nexusmart.seckill.mq.SeckillOrderMessage;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SeckillOrderAsyncService {

    private final SeckillGoodsMapper seckillGoodsMapper;
    private final GoodsMapper goodsMapper;
    private final OrderInfoMapper orderInfoMapper;
    private final SeckillOrderMapper seckillOrderMapper;

    public SeckillOrderAsyncService(SeckillGoodsMapper seckillGoodsMapper,
                                    GoodsMapper goodsMapper,
                                    OrderInfoMapper orderInfoMapper,
                                    SeckillOrderMapper seckillOrderMapper) {
        this.seckillGoodsMapper = seckillGoodsMapper;
        this.goodsMapper = goodsMapper;
        this.orderInfoMapper = orderInfoMapper;
        this.seckillOrderMapper = seckillOrderMapper;
    }

    @WriteDataSource
    @Transactional
    public OrderInfo createOrderInTransaction(SeckillOrderMessage message) {
        OrderInfo existed = orderInfoMapper.selectByOrderNo(message.getOrderNo());
        if (existed != null) {
            return existed;
        }

        SeckillGoods current = seckillGoodsMapper.selectById(message.getSeckillId());
        if (current == null) {
            throw new RuntimeException("秒杀商品不存在");
        }

        int affected = seckillGoodsMapper.decreaseStockByOptimisticLock(current.getId(), current.getVersion());
        if (affected == 0) {
            SeckillGoods latest = seckillGoodsMapper.selectById(message.getSeckillId());
            if (latest == null || latest.getStockCount() <= 0) {
                throw new RuntimeException("库存不足，异步下单失败");
            }
            affected = seckillGoodsMapper.decreaseStockByOptimisticLock(latest.getId(), latest.getVersion());
            if (affected == 0) {
                throw new RuntimeException("库存不足或并发冲突，异步下单失败");
            }
            current = latest;
        }

        Goods goods = goodsMapper.selectById(current.getGoodsId());
        if (goods == null) {
            throw new RuntimeException("商品不存在，异步下单失败");
        }

        OrderInfo order = new OrderInfo();
        order.setOrderNo(message.getOrderNo());
        order.setUserId(message.getUserId());
        order.setMerchantId(goods.getMerchantId());
        order.setGoodsId(goods.getId());
        order.setGoodsName(goods.getGoodsName());
        order.setOrderPrice(current.getSeckillPrice());
        order.setStatus(OrderStatus.SUCCESS.getCode());

        try {
            orderInfoMapper.insert(order);

            SeckillOrder seckillOrder = new SeckillOrder();
            seckillOrder.setUserId(message.getUserId());
            seckillOrder.setOrderId(order.getId());
            seckillOrder.setGoodsId(goods.getId());
            seckillOrderMapper.insert(seckillOrder);
            return order;
        } catch (DuplicateKeyException e) {
            OrderInfo byOrderNo = orderInfoMapper.selectByOrderNo(message.getOrderNo());
            if (byOrderNo != null) {
                return byOrderNo;
            }

            SeckillOrder existedSeckillOrder = seckillOrderMapper
                    .selectByUserIdAndGoodsId(message.getUserId(), goods.getId());
            if (existedSeckillOrder != null) {
                OrderInfo existedOrder = orderInfoMapper.selectById(existedSeckillOrder.getOrderId());
                if (existedOrder != null) {
                    return existedOrder;
                }
            }
            throw e;
        }
    }
}