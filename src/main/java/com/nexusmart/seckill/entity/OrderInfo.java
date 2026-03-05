package com.nexusmart.seckill.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 完整订单实体类，对应数据库 order_info 表（记录交易快照）
 */
@Data
public class OrderInfo {

    /** 订单 ID（自增主键） */
    private Long id;

    /** 买家 ID */
    private Long userId;

    /** 卖家（商家）ID */
    private Long merchantId;

    /** 商品 ID */
    private Long goodsId;

    /** 冗余商品名称（防止商品改名后订单信息丢失） */
    private String goodsName;

    /** 实际成交价 */
    private BigDecimal orderPrice;

    /** 订单状态：0-新建未支付  1-已支付  2-已发货  3-已退款 */
    private Integer status;

    /** 下单时间（由数据库 DEFAULT CURRENT_TIMESTAMP 自动填充） */
    private LocalDateTime createTime;
}
