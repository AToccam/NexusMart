package com.nexusmart.seckill.entity;

import lombok.Data;
import java.math.BigDecimal;

/**
 * 普通商品实体类，对应数据库 goods 表（冷数据，日常浏览用）
 */
@Data
public class Goods {

    /** 商品 ID（自增主键） */
    private Long id;

    /** 所属商家 ID */
    private Long merchantId;

    /** 商品名称 */
    private String goodsName;

    /** 商品图片 URL */
    private String goodsImg;

    /** 日常售价（金额一律用 BigDecimal，禁止 float/double） */
    private BigDecimal goodsPrice;

    /** 日常库存 */
    private Integer goodsStock;
}
