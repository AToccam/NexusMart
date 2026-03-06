package com.nexusmart.seckill.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 秒杀商品展示 VO，整合 goods 和 seckill_goods 信息
 */
@Data
public class SeckillGoodsVo {

    // ---- 来自 goods 表 ----
    private Long goodsId;
    private String goodsName;
    private String goodsImg;
    private BigDecimal goodsPrice;

    // ---- 来自 seckill_goods 表 ----
    private Long seckillId;
    private BigDecimal seckillPrice;
    private Integer stockCount;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
}
