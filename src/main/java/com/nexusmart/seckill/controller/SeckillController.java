package com.nexusmart.seckill.controller;

import com.nexusmart.seckill.common.Result;
import com.nexusmart.seckill.entity.OrderInfo;
import com.nexusmart.seckill.mapper.OrderInfoMapper;
import com.nexusmart.seckill.mq.PaymentResultMessage;
import com.nexusmart.seckill.service.PaymentResultProducer;
import com.nexusmart.seckill.service.SeckillService;
import com.nexusmart.seckill.vo.SeckillSubmitResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.UUID;

@RestController
@RequestMapping("/api/seckill")
@RefreshScope
public class SeckillController {

    @Autowired
    private SeckillService seckillService;

    @Autowired
    private OrderInfoMapper orderInfoMapper;

    @Autowired
    private PaymentResultProducer paymentResultProducer;

    @Value("${nexusmart.dynamic.message:hello-from-local-config}")
    private String dynamicMessage;

    @Value("${nexusmart.pressure.simulate-latency-ms:0}")
    private long simulateLatencyMs;

    /** 失败率：0~1 之间的比例（如 0.3 = 30% 失败），值会被夹紧到 [0,1] */
    @Value("${nexusmart.pressure.failure-rate:0}")
    private double failureRate;

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

    /**
     * 模拟支付服务回调（联调用）：发布支付结果消息。
     */
    @PostMapping("/pay/mock")
    public Result<String> mockPayCallback(@RequestParam Long orderNo,
                                          @RequestParam Boolean paid,
                                          @RequestParam(required = false) String reason) {
        try {
            PaymentResultMessage message = new PaymentResultMessage();
            message.setEventId("PAY-EVT-" + UUID.randomUUID());
            message.setOrderNo(orderNo);
            message.setPaid(paid);
            message.setPaymentNo("PAY-" + System.currentTimeMillis());
            message.setReason(reason);
            message.setCreatedAt(System.currentTimeMillis());

            paymentResultProducer.sendPaymentResult(message);
            return Result.success("支付结果已发布，eventId=" + message.getEventId());
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /** 查看当前动态配置值，便于验证 Nacos 刷新生效 */
    @GetMapping("/config/current")
    public Result<Map<String, Object>> currentConfig() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("dynamicMessage", dynamicMessage);
        payload.put("simulateLatencyMs", simulateLatencyMs);
        payload.put("failureRate", failureRate);
        return Result.success(payload);
    }

    /**
     * 压测演示接口：可按配置模拟慢调用与失败，供网关熔断/限流/降级测试使用。
     */
    @GetMapping("/pressure/ping")
    public Result<String> pressurePing(@RequestParam(required = false, defaultValue = "false") boolean forceFail) {
        if (simulateLatencyMs > 0) {
            try {
                TimeUnit.MILLISECONDS.sleep(simulateLatencyMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Result.error("请求被中断");
            }
        }

        if (forceFail || shouldFailByRatio()) {
            throw new RuntimeException("模拟服务异常，用于验证熔断与降级");
        }
        return Result.success("pong: " + dynamicMessage + " @" + System.currentTimeMillis());
    }

    private boolean shouldFailByRatio() {
        // failureRate 语义为 0~1 的比例（如 0.3 = 30% 失败概率）
        double normalized = Math.max(0, Math.min(1, failureRate));
        if (normalized <= 0) {
            return false;
        }
        return ThreadLocalRandom.current().nextDouble() < normalized;
    }
}
