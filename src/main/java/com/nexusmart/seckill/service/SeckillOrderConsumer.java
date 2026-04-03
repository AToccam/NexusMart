package com.nexusmart.seckill.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusmart.seckill.entity.OrderInfo;
import com.nexusmart.seckill.mq.SeckillOrderMessage;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class SeckillOrderConsumer {

    private final ObjectMapper objectMapper;
    private final SeckillOrderAsyncService seckillOrderAsyncService;
    private final SeckillCompensationService compensationService;

    public SeckillOrderConsumer(ObjectMapper objectMapper,
                                SeckillOrderAsyncService seckillOrderAsyncService,
                                SeckillCompensationService compensationService) {
        this.objectMapper = objectMapper;
        this.seckillOrderAsyncService = seckillOrderAsyncService;
        this.compensationService = compensationService;
    }

    @KafkaListener(topics = "${app.kafka.topics.order-create:seckill.order.create.v1}")
    public void onOrderCreateMessage(String payload) {
        SeckillOrderMessage message = parseMessage(payload);
        if (message == null || message.getRequestId() == null) {
            throw new RuntimeException("无效下单消息");
        }

        String requestId = message.getRequestId();
        if (compensationService.isMessageDone(requestId)) {
            return;
        }

        boolean locked = compensationService.tryAcquireMessageLock(requestId, 30);
        if (!locked) {
            return;
        }

        try {
            if (compensationService.isMessageDone(requestId)) {
                return;
            }

            OrderInfo order = seckillOrderAsyncService.createOrderInTransaction(message);
            compensationService.markSuccess(message, order);
        } finally {
            compensationService.releaseMessageLock(requestId);
        }
    }

    @KafkaListener(topics = "${app.kafka.topics.order-create-dlt:seckill.order.create.v1.DLT}")
    public void onOrderCreateDeadLetter(String payload) {
        SeckillOrderMessage message = parseMessage(payload);
        if (message == null || message.getRequestId() == null) {
            return;
        }
        compensationService.markFailedAndRollback(message.getRequestId(), "CONSUME_RETRY_EXHAUSTED");
    }

    private SeckillOrderMessage parseMessage(String payload) {
        try {
            return objectMapper.readValue(payload, SeckillOrderMessage.class);
        } catch (Exception e) {
            return null;
        }
    }
}