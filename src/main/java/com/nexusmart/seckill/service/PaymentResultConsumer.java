package com.nexusmart.seckill.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusmart.seckill.common.OrderStatus;
import com.nexusmart.seckill.config.datasource.WriteDataSource;
import com.nexusmart.seckill.entity.OrderInfo;
import com.nexusmart.seckill.mapper.OrderInfoMapper;
import com.nexusmart.seckill.mq.PaymentResultMessage;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@WriteDataSource
@Component
public class PaymentResultConsumer {

    private final ObjectMapper objectMapper;
    private final OrderInfoMapper orderInfoMapper;
    private final PaymentEventConsistencyService paymentEventConsistencyService;

    public PaymentResultConsumer(ObjectMapper objectMapper,
                                 OrderInfoMapper orderInfoMapper,
                                 PaymentEventConsistencyService paymentEventConsistencyService) {
        this.objectMapper = objectMapper;
        this.orderInfoMapper = orderInfoMapper;
        this.paymentEventConsistencyService = paymentEventConsistencyService;
    }

    @KafkaListener(topics = "${app.kafka.topics.payment-result:seckill.payment.result.v1}")
    public void onPaymentResult(String payload) {
        PaymentResultMessage message = parse(payload);
        if (message == null || message.getEventId() == null || message.getOrderNo() == null || message.getPaid() == null) {
            throw new RuntimeException("无效支付结果消息");
        }

        String eventId = message.getEventId();
        if (paymentEventConsistencyService.isDone(eventId)) {
            return;
        }

        boolean locked = paymentEventConsistencyService.tryLock(eventId, 30);
        if (!locked) {
            return;
        }

        try {
            if (paymentEventConsistencyService.isDone(eventId)) {
                return;
            }

            OrderInfo order = orderInfoMapper.selectByOrderNo(message.getOrderNo());
            if (order == null) {
                throw new RuntimeException("订单不存在，等待重试");
            }

            int targetStatus = Boolean.TRUE.equals(message.getPaid())
                    ? OrderStatus.PAID.getCode()
                    : OrderStatus.PAY_FAILED.getCode();

            int updated = tryUpdateStatusByPaymentResult(message);
            if (updated > 0) {
                paymentEventConsistencyService.markDone(eventId, "UPDATED");
                return;
            }

            if (order.getStatus() == targetStatus) {
                paymentEventConsistencyService.markDone(eventId, "SKIPPED_ALREADY_TARGET_STATUS");
                return;
            }

            if (order.getStatus() == OrderStatus.FAILED.getCode()) {
                paymentEventConsistencyService.markDone(eventId, "SKIPPED_ORDER_FAILED");
                return;
            }

            if (!Boolean.TRUE.equals(message.getPaid()) && order.getStatus() == OrderStatus.PAID.getCode()) {
                paymentEventConsistencyService.markDone(eventId, "SKIPPED_ALREADY_PAID");
                return;
            }

            if (order.getStatus() == OrderStatus.QUEUING.getCode()) {
                throw new RuntimeException("订单尚未完成创建，等待重试");
            }
            throw new RuntimeException("订单状态更新冲突，等待重试");
        } finally {
            paymentEventConsistencyService.unlock(eventId);
        }
    }

    @KafkaListener(topics = "${app.kafka.topics.payment-result-dlt:seckill.payment.result.v1.DLT}")
    public void onPaymentResultDlt(String payload) {
        PaymentResultMessage message = parse(payload);
        if (message == null || message.getEventId() == null || message.getOrderNo() == null) {
            return;
        }

        OrderInfo order = orderInfoMapper.selectByOrderNo(message.getOrderNo());
        if (order == null) {
            paymentEventConsistencyService.markDone(message.getEventId(), "DLT_SKIPPED_ORDER_NOT_FOUND");
            return;
        }

        if (order.getStatus() == OrderStatus.PAID.getCode()) {
            paymentEventConsistencyService.markDone(message.getEventId(), "DLT_SKIPPED_ALREADY_PAID");
            return;
        }

        orderInfoMapper.markPayFailedIfUnpaid(message.getOrderNo());
        paymentEventConsistencyService.markDone(message.getEventId(), "DLT_MARK_PAY_FAILED");
    }

    private int tryUpdateStatusByPaymentResult(PaymentResultMessage message) {
        if (Boolean.TRUE.equals(message.getPaid())) {
            return orderInfoMapper.markPaidIfPayable(message.getOrderNo());
        }
        return orderInfoMapper.markPayFailedIfUnpaid(message.getOrderNo());
    }

    private PaymentResultMessage parse(String payload) {
        try {
            return objectMapper.readValue(payload, PaymentResultMessage.class);
        } catch (Exception e) {
            return null;
        }
    }
}