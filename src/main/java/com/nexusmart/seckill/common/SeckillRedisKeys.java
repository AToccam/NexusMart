package com.nexusmart.seckill.common;

public final class SeckillRedisKeys {

    public static final String PENDING_KEY_PREFIX = "seckill:pending:";
    public static final String PENDING_INDEX_KEY = "seckill:pending:index";
    public static final String RESULT_KEY_PREFIX = "seckill:result:";
    public static final String MESSAGE_DONE_KEY_PREFIX = "seckill:msg:done:";
    public static final String MESSAGE_LOCK_KEY_PREFIX = "seckill:msg:lock:";
    public static final String COMPENSATE_LOCK_KEY_PREFIX = "seckill:compensate:lock:";
    public static final String PAYMENT_EVENT_DONE_KEY_PREFIX = "seckill:payment:event:done:";
    public static final String PAYMENT_EVENT_LOCK_KEY_PREFIX = "seckill:payment:event:lock:";

    private SeckillRedisKeys() {
    }

    public static String pendingKey(String requestId) {
        return PENDING_KEY_PREFIX + requestId;
    }

    public static String resultKey(String requestId) {
        return RESULT_KEY_PREFIX + requestId;
    }

    public static String messageDoneKey(String requestId) {
        return MESSAGE_DONE_KEY_PREFIX + requestId;
    }

    public static String messageLockKey(String requestId) {
        return MESSAGE_LOCK_KEY_PREFIX + requestId;
    }

    public static String compensateLockKey(String requestId) {
        return COMPENSATE_LOCK_KEY_PREFIX + requestId;
    }

    public static String paymentEventDoneKey(String eventId) {
        return PAYMENT_EVENT_DONE_KEY_PREFIX + eventId;
    }

    public static String paymentEventLockKey(String eventId) {
        return PAYMENT_EVENT_LOCK_KEY_PREFIX + eventId;
    }
}