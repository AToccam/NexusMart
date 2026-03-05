package com.nexusmart.seckill.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 秒杀商品实体类，对应数据库 seckill_goods 表（热数据，高频扣减核心）
 */
@Data
public class SeckillGoods {

    /** 秒杀商品 ID（自增主键） */
    private Long id;

    /** 关联的普通商品 ID */
    private Long goodsId;

    /** 秒杀特价 */
    private BigDecimal seckillPrice;

    /** 秒杀专属库存 */
    private Integer stockCount;

    /** 秒杀开始时间 */
    private LocalDateTime startTime;

    /** 秒杀结束时间 */
    private LocalDateTime endTime;

    /** 乐观锁版本号（每次扣减库存 +1） */
    private Integer version;
}
