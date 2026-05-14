package com.nexusmart.seckill.util;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * Redis 缓存工具类，封装缓存穿透 / 雪崩 / 击穿三大防护策略
 * <p>
 * 加入 Caffeine 作为进程内 L1 缓存，形成 L1(Caffeine) → L2(Redis) → DB 三级访问：
 * 1. 雪崩兜底：L2 整体不可用时仍可命中 L1
 * 2. 降本：极热点 key 由 L1 吸收，Redis 网络往返减少
 * 3. Redis 抛异常时自动降级到只读 L1+回源 DB（防雪崩-熔断降级）
 */
@Component
public class RedisCacheUtil {

    private static final Logger log = LoggerFactory.getLogger(RedisCacheUtil.class);

    /** 空对象占位符（缓存穿透防护） */
    public static final String EMPTY_VALUE = "EMPTY";

    /** 分布式互斥锁前缀 */
    private static final String LOCK_PREFIX = "lock:";

    @Autowired
    private StringRedisTemplate redisTemplate;

    /** 空对象 TTL（秒），默认 300s（5 分钟），可通过配置覆盖 */
    @Value("${app.cache.empty-ttl-seconds:300}")
    private long emptyTtlSeconds;

    /** 互斥锁自动过期时间（秒） */
    @Value("${app.cache.lock-ttl-seconds:10}")
    private long lockTtlSeconds;

    /** L1 缓存最大条目数 */
    @Value("${app.cache.local.maximum-size:10000}")
    private long localMaxSize;

    /** L1 缓存写入后过期时间（秒）— 故意远小于 Redis TTL，避免 L1 长期陈旧 */
    @Value("${app.cache.local.expire-seconds:60}")
    private long localExpireSeconds;

    private Cache<String, String> localCache;

    @PostConstruct
    public void initLocalCache() {
        this.localCache = Caffeine.newBuilder()
                .maximumSize(localMaxSize)
                .expireAfterWrite(localExpireSeconds, TimeUnit.SECONDS)
                .recordStats()
                .build();
    }

    // ==================== 缓存穿透：空对象缓存 ====================

    /**
     * 写入缓存（带 TTL 随机化，防雪崩）。同步写 L1 + L2。
     */
    public void setWithRandomTTL(String key, String value, long baseSeconds, long randomRange) {
        long ttl = baseSeconds + ThreadLocalRandom.current().nextLong(randomRange + 1);
        try {
            redisTemplate.opsForValue().set(key, value, ttl, TimeUnit.SECONDS);
        } catch (DataAccessException e) {
            log.warn("Redis 写入失败，降级仅写本地缓存: key={}", key, e);
        }
        localCache.put(key, value);
    }

    /**
     * 缓存空对象（短 TTL，防止穿透）。同步写 L1 + L2。
     */
    public void setEmpty(String key, long emptySeconds) {
        try {
            redisTemplate.opsForValue().set(key, EMPTY_VALUE, emptySeconds, TimeUnit.SECONDS);
        } catch (DataAccessException e) {
            log.warn("Redis 空值写入失败，降级仅写本地缓存: key={}", key, e);
        }
        localCache.put(key, EMPTY_VALUE);
    }

    public boolean isEmpty(String value) {
        return EMPTY_VALUE.equals(value);
    }

    public long getEmptyTtlSeconds() {
        return emptyTtlSeconds;
    }

    /**
     * 主动失效本地 + Redis 缓存（用于商品更新等场景）。
     */
    public void evict(String key) {
        localCache.invalidate(key);
        try {
            redisTemplate.delete(key);
        } catch (DataAccessException e) {
            log.warn("Redis evict 失败: key={}", key, e);
        }
    }

    // ==================== 缓存击穿：互斥锁 ====================

    /** SETNX 互斥锁 */
    public boolean tryLock(String lockKey, long expireSeconds) {
        try {
            Boolean ok = redisTemplate.opsForValue()
                    .setIfAbsent(LOCK_PREFIX + lockKey, "1", expireSeconds, TimeUnit.SECONDS);
            return Boolean.TRUE.equals(ok);
        } catch (DataAccessException e) {
            // Redis 不可用时退化为"无锁直接回源 DB"：单实例下风险可控
            log.warn("Redis tryLock 失败，降级直接回源 DB: key={}", lockKey, e);
            return true;
        }
    }

    public void unlock(String lockKey) {
        try {
            redisTemplate.delete(LOCK_PREFIX + lockKey);
        } catch (DataAccessException e) {
            log.warn("Redis unlock 失败: key={}", lockKey, e);
        }
    }

    /**
     * 带 L1+L2+互斥锁的缓存查询（防击穿/穿透/雪崩）：
     * <ol>
     *     <li>查 L1（Caffeine）。命中且非空对象 → 直接返回；命中空对象 → 返回 null</li>
     *     <li>L1 未命中 → 查 L2（Redis）。命中则回填 L1 后返回</li>
     *     <li>L2 也未命中 → 抢互斥锁，只有一个线程查 DB 并回写 L1+L2</li>
     *     <li>Redis 抛异常时退化为"只读 L1 + 回源 DB"，由 L1 吸收高峰</li>
     * </ol>
     */
    public String getWithMutex(String key, String lockName,
                               java.util.function.Supplier<String> dbLoader,
                               long baseSeconds, long randomRange) {
        // L1
        String local = localCache.getIfPresent(key);
        if (local != null) {
            return isEmpty(local) ? null : local;
        }

        // L2
        String cached = safeRedisGet(key);
        if (cached != null) {
            localCache.put(key, cached);
            return isEmpty(cached) ? null : cached;
        }

        // 互斥锁回源
        if (tryLock(lockName, lockTtlSeconds)) {
            try {
                // Double-check L2，可能其他线程已经写入
                cached = safeRedisGet(key);
                if (cached != null) {
                    localCache.put(key, cached);
                    return isEmpty(cached) ? null : cached;
                }

                String dbValue = dbLoader.get();
                if (dbValue == null) {
                    setEmpty(key, emptyTtlSeconds);
                    return null;
                }
                setWithRandomTTL(key, dbValue, baseSeconds, randomRange);
                return dbValue;
            } finally {
                unlock(lockName);
            }
        }

        // 抢锁失败 → 短暂休眠后递归重试
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return getWithMutex(key, lockName, dbLoader, baseSeconds, randomRange);
    }

    private String safeRedisGet(String key) {
        try {
            return redisTemplate.opsForValue().get(key);
        } catch (DataAccessException e) {
            log.warn("Redis 读取失败，仅使用 L1+DB 回源: key={}", key, e);
            return null;
        }
    }
}
