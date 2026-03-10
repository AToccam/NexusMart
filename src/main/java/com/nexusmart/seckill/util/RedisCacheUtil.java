package com.nexusmart.seckill.util;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * Redis 缓存工具类，封装缓存穿透 / 雪崩 / 击穿三大防护策略
 */
@Component
public class RedisCacheUtil {

    /** 空对象占位符（缓存穿透防护） */
    public static final String EMPTY_VALUE = "EMPTY";

    /** 分布式互斥锁前缀 */
    private static final String LOCK_PREFIX = "lock:";

    @Autowired
    private StringRedisTemplate redisTemplate;

    // ==================== 缓存穿透：空对象缓存 ====================

    /**
     * 写入缓存（带 TTL 随机化，防雪崩）
     *
     * @param key          Redis key
     * @param value        缓存值
     * @param baseSeconds  基础过期秒数
     * @param randomRange  随机浮动秒数（0 ~ randomRange）
     */
    public void setWithRandomTTL(String key, String value, long baseSeconds, long randomRange) {
        long ttl = baseSeconds + ThreadLocalRandom.current().nextLong(randomRange + 1);
        redisTemplate.opsForValue().set(key, value, ttl, TimeUnit.SECONDS);
    }

    /**
     * 缓存空对象（短 TTL，防止穿透）
     *
     * @param key          Redis key
     * @param emptySeconds 空对象过期秒数（建议 60~120）
     */
    public void setEmpty(String key, long emptySeconds) {
        redisTemplate.opsForValue().set(key, EMPTY_VALUE, emptySeconds, TimeUnit.SECONDS);
    }

    /**
     * 判断缓存值是否为空对象占位符
     */
    public boolean isEmpty(String value) {
        return EMPTY_VALUE.equals(value);
    }

    // ==================== 缓存击穿：互斥锁 ====================

    /**
     * 尝试获取分布式互斥锁（SETNX）
     *
     * @param lockKey     锁名称
     * @param expireSeconds 锁自动过期时间（防止死锁）
     * @return true=获取成功, false=已被他人持有
     */
    public boolean tryLock(String lockKey, long expireSeconds) {
        Boolean ok = redisTemplate.opsForValue()
                .setIfAbsent(LOCK_PREFIX + lockKey, "1", expireSeconds, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(ok);
    }

    /**
     * 释放互斥锁
     */
    public void unlock(String lockKey) {
        redisTemplate.delete(LOCK_PREFIX + lockKey);
    }

    /**
     * 带互斥锁的缓存查询（防击穿）
     * <p>
     * 1. 查 Redis，有值直接返回（空对象返回 null）
     * 2. Redis 无值 → 抢锁 → 只有一个线程查 DB 并回写缓存
     * 3. 没抢到锁的线程休眠后重试
     *
     * @param key          缓存 key
     * @param lockName     锁名
     * @param dbLoader     从 DB 加载数据的回调
     * @param baseSeconds  缓存基础 TTL
     * @param randomRange  TTL 随机浮动
     * @return 缓存值或 null（表示 DB 中也不存在）
     */
    public String getWithMutex(String key, String lockName,
                               java.util.function.Supplier<String> dbLoader,
                               long baseSeconds, long randomRange) {
        // 第一次查 Redis
        String cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            return isEmpty(cached) ? null : cached;
        }

        // Redis 无数据 → 尝试抢互斥锁
        if (tryLock(lockName, 10)) {
            try {
                // Double-check：抢到锁后再查一次，可能别的线程已经写入了
                cached = redisTemplate.opsForValue().get(key);
                if (cached != null) {
                    return isEmpty(cached) ? null : cached;
                }

                // 查数据库
                String dbValue = dbLoader.get();
                if (dbValue == null) {
                    // DB 也没有 → 写入空对象（防穿透），60 秒过期
                    setEmpty(key, 60);
                    return null;
                }

                // DB 有数据 → 写入缓存（TTL 随机化，防雪崩）
                setWithRandomTTL(key, dbValue, baseSeconds, randomRange);
                return dbValue;
            } finally {
                unlock(lockName);
            }
        }

        // 没抢到锁 → 休眠后重试
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        // 递归重试（锁持有者很快就会写入缓存）
        return getWithMutex(key, lockName, dbLoader, baseSeconds, randomRange);
    }
}
