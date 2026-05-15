package com.nexusmart.seckill.sharding;

import com.nexusmart.seckill.common.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/**
 * 分库分表演示接口（profile=sharding 时启用）。
 *
 * <p>验证路由：
 * <pre>
 *   curl -X POST "http://127.0.0.1:8081/api/orders/archive?userId=1&goodsId=1&price=99"
 *   curl -X POST "http://127.0.0.1:8081/api/orders/archive?userId=2&goodsId=1&price=99"
 *   curl "http://127.0.0.1:8081/api/orders/archive/by-user?userId=1"
 * </pre>
 * 通过应用日志 `sql-show: true` 可观察到 ShardingSphere 改写后的真实物理表名。
 */
@RestController
@RequestMapping("/api/orders/archive")
@Profile("sharding")
public class OrderArchiveController {

    @Autowired
    private OrderArchiveMapper orderArchiveMapper;

    /** 插入一条归档订单（order_id 由 ShardingSphere SNOWFLAKE 自动生成）。 */
    @PostMapping
    public Result<OrderArchive> create(@RequestParam Long userId,
                                       @RequestParam Long goodsId,
                                       @RequestParam BigDecimal price) {
        OrderArchive o = new OrderArchive();
        o.setUserId(userId);
        o.setGoodsId(goodsId);
        o.setOrderPrice(price);
        o.setStatus(1);
        orderArchiveMapper.insert(o);
        return Result.success(o);
    }

    @GetMapping("/by-user")
    public Result<List<OrderArchive>> listByUser(@RequestParam Long userId) {
        return Result.success(orderArchiveMapper.selectByUserId(userId));
    }

    @GetMapping("/by-order")
    public Result<OrderArchive> getByOrder(@RequestParam Long orderId) {
        OrderArchive o = orderArchiveMapper.selectByOrderId(orderId);
        if (o == null) {
            return Result.error("订单不存在");
        }
        return Result.success(o);
    }
}
