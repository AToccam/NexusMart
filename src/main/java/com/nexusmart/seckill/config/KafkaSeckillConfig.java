package com.nexusmart.seckill.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
@EnableKafka
public class KafkaSeckillConfig {

    @Bean
    public NewTopic seckillOrderCreateTopic(
            @Value("${app.kafka.topics.order-create:seckill.order.create.v1}") String topic,
            @Value("${app.kafka.topic.partitions:3}") int partitions,
            @Value("${app.kafka.topic.replicas:1}") short replicas) {
        return TopicBuilder.name(topic)
                .partitions(partitions)
                .replicas(replicas)
                .build();
    }

    @Bean
    public NewTopic seckillOrderCreateDltTopic(
            @Value("${app.kafka.topics.order-create-dlt:seckill.order.create.v1.DLT}") String topic,
            @Value("${app.kafka.topic.partitions:3}") int partitions,
            @Value("${app.kafka.topic.replicas:1}") short replicas) {
        return TopicBuilder.name(topic)
                .partitions(partitions)
                .replicas(replicas)
                .build();
    }

    @Bean
    public NewTopic paymentResultTopic(
            @Value("${app.kafka.topics.payment-result:seckill.payment.result.v1}") String topic,
            @Value("${app.kafka.topic.partitions:3}") int partitions,
            @Value("${app.kafka.topic.replicas:1}") short replicas) {
        return TopicBuilder.name(topic)
                .partitions(partitions)
                .replicas(replicas)
                .build();
    }

    @Bean
    public NewTopic paymentResultDltTopic(
            @Value("${app.kafka.topics.payment-result-dlt:seckill.payment.result.v1.DLT}") String topic,
            @Value("${app.kafka.topic.partitions:3}") int partitions,
            @Value("${app.kafka.topic.replicas:1}") short replicas) {
        return TopicBuilder.name(topic)
                .partitions(partitions)
                .replicas(replicas)
                .build();
    }

    @Bean
    public DefaultErrorHandler kafkaDefaultErrorHandler(
            KafkaTemplate<String, String> kafkaTemplate,
                        @Value("${app.kafka.topics.order-create:seckill.order.create.v1}") String orderCreateTopic,
                        @Value("${app.kafka.topics.order-create-dlt:seckill.order.create.v1.DLT}") String orderCreateDltTopic,
                        @Value("${app.kafka.topics.payment-result:seckill.payment.result.v1}") String paymentResultTopic,
                        @Value("${app.kafka.topics.payment-result-dlt:seckill.payment.result.v1.DLT}") String paymentResultDltTopic,
            @Value("${app.kafka.consumer.retry.max-attempts:3}") long maxAttempts,
            @Value("${app.kafka.consumer.retry.backoff-ms:1000}") long backoffMs) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                                (record, ex) -> {
                                        if (orderCreateTopic.equals(record.topic())) {
                                                return new TopicPartition(orderCreateDltTopic, record.partition());
                                        }
                                        if (paymentResultTopic.equals(record.topic())) {
                                                return new TopicPartition(paymentResultDltTopic, record.partition());
                                        }
                                        return new TopicPartition(record.topic() + ".DLT", record.partition());
                                });

        long retries = Math.max(0, maxAttempts - 1);
        return new DefaultErrorHandler(recoverer, new FixedBackOff(backoffMs, retries));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            DefaultErrorHandler kafkaDefaultErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(kafkaDefaultErrorHandler);
        return factory;
    }
}