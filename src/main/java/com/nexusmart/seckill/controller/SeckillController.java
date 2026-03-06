package com.nexusmart.seckill.controller;

import com.nexusmart.seckill.common.Result;
import com.nexusmart.seckill.entity.OrderInfo;
import com.nexusmart.seckill.service.SeckillService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/seckill")
public class SeckillController {

    @Autowired
    private SeckillService seckillService;

    /**
     * 执行秒杀
     * @param userId    买家 ID
     * @param seckillId 秒杀商品 ID
     */
    @PostMapping("/do")
    public Result<OrderInfo> doSeckill(@RequestParam Long userId,
                                       @RequestParam Long seckillId) {
        try {
            OrderInfo order = seckillService.doSeckill(userId, seckillId);
            return Result.success(order);
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }
}
