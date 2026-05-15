package com.nexusmart.seckill.sharding;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 分库分表归档订单表。
 *
 * <p>分片策略：
 * <ul>
 *   <li>分库键 user_id：user_id % 2 → ds0 / ds1</li>
 *   <li>分表键 order_id：order_id % 4 → order_archive_0 / _1 / _2 / _3</li>
 * </ul>
 *
 * <p>order_id 必须使用雪花算法生成，避免分库分表下 auto_increment 全局冲突。
 */
@Data
public class OrderArchive {

    /** 业务订单 ID（雪花算法生成，分表键） */
    private Long orderId;

    /** 买家 ID（分库键） */
    private Long userId;

    /** 商品 ID */
    private Long goodsId;

    /** 成交价 */
    private BigDecimal orderPrice;

    /** 订单状态：0=排队中 1=已成交 2=已取消 */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createTime;
}
