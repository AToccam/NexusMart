package com.nexusmart.seckill.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusmart.seckill.mq.SeckillOrderMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class SeckillOrderProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.kafka.topics.order-create:seckill.order.create.v1}")
    private String orderCreateTopic;

    @Value("${app.kafka.producer.send-timeout-ms:3000}")
    private long sendTimeoutMs;

    public SeckillOrderProducer(KafkaTemplate<String, String> kafkaTemplate,
                                ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public void sendCreateOrderMessage(SeckillOrderMessage message) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("消息序列化失败", e);
        }

        try {
            kafkaTemplate.send(orderCreateTopic, message.getRequestId(), payload)
                    .get(sendTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            throw new RuntimeException("发送Kafka下单消息失败", e);
        }
    }
}