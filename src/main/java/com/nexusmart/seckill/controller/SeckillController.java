package com.nexusmart.seckill.controller;

import com.nexusmart.seckill.common.Result;
import com.nexusmart.seckill.entity.OrderInfo;
import com.nexusmart.seckill.mapper.OrderInfoMapper;
import com.nexusmart.seckill.service.SeckillService;
import com.nexusmart.seckill.vo.SeckillSubmitResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/seckill")
public class SeckillController {

    @Autowired
    private SeckillService seckillService;

    @Autowired
    private OrderInfoMapper orderInfoMapper;

    /**
     * 执行秒杀
     * @param userId    买家 ID
     * @param seckillId 秒杀商品 ID
     */
    @PostMapping("/do")
    public Result<SeckillSubmitResponse> doSeckill(@RequestParam Long userId,
                                                   @RequestParam Long seckillId) {
        try {
            SeckillSubmitResponse response = seckillService.submitSeckill(userId, seckillId);
            return Result.success(response);
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /** 按订单 ID 查询订单 */
    @GetMapping("/order/id")
    public Result<OrderInfo> getOrderById(@RequestParam Long orderId) {
        OrderInfo order = orderInfoMapper.selectById(orderId);
        if (order == null) {
            return Result.error("订单不存在");
        }
        return Result.success(order);
    }

    /** 按业务订单号查询订单 */
    @GetMapping("/order/no")
    public Result<OrderInfo> getOrderByNo(@RequestParam Long orderNo) {
        OrderInfo order = orderInfoMapper.selectByOrderNo(orderNo);
        if (order == null) {
            return Result.error("订单不存在");
        }
        return Result.success(order);
    }

    /** 查询某个用户的所有订单 */
    @GetMapping("/orders")
    public Result<List<OrderInfo>> listOrders(@RequestParam Long userId) {
        return Result.success(orderInfoMapper.selectByUserId(userId));
    }
}
