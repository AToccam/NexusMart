package com.nexusmart.seckill.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 完整订单实体类，对应数据库 order_info 表（记录交易快照）
 */
@Data
public class OrderInfo {

    /** 订单 ID（数据库自增主键） */
    private Long id;

    /** 业务订单号（雪花 ID，全局唯一） */
    private Long orderNo;

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

    /** 订单状态：0-排队中 1-成功 2-失败 */
    private Integer status;

    /** 下单时间（由数据库 DEFAULT CURRENT_TIMESTAMP 自动填充） */
    private LocalDateTime createTime;
}
