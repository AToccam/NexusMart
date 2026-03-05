package com.nexusmart.seckill.entity;

import lombok.Data;

/**
 * 秒杀防重订单实体类，对应数据库 seckill_order 表
 * 利用唯一索引 (user_id, goods_id) 做数据库层面的终极防重复下单兜底
 */
@Data
public class SeckillOrder {

    /** 秒杀订单 ID（自增主键） */
    private Long id;

    /** 买家 ID */
    private Long userId;

    /** 关联的完整订单 ID */
    private Long orderId;

    /** 买到的商品 ID */
    private Long goodsId;
}
