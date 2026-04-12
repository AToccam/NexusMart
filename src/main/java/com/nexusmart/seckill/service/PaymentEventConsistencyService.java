package com.nexusmart.seckill.service;

import com.nexusmart.seckill.common.SeckillRedisKeys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class PaymentEventConsistencyService {

    private final StringRedisTemplate redisTemplate;

    @Value("${app.seckill.idempotent-ttl-seconds:604800}")
    private long doneTtlSeconds;

    public PaymentEventConsistencyService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean isDone(String eventId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(SeckillRedisKeys.paymentEventDoneKey(eventId)));
    }

    public boolean tryLock(String eventId, long seconds) {
        Boolean ok = redisTemplate.opsForValue().setIfAbsent(
                SeckillRedisKeys.paymentEventLockKey(eventId),
                "1",
                seconds,
                TimeUnit.SECONDS);
        return Boolean.TRUE.equals(ok);
    }

    public void unlock(String eventId) {
        redisTemplate.delete(SeckillRedisKeys.paymentEventLockKey(eventId));
    }

    public void markDone(String eventId, String result) {
        redisTemplate.opsForValue().set(
                SeckillRedisKeys.paymentEventDoneKey(eventId),
                result,
                doneTtlSeconds,
                TimeUnit.SECONDS);
    }
}