package com.nexusmart.seckill.common;

/**
 * 秒杀订单状态：用于异步链路的排队与结果标识。
 */
public enum OrderStatus {

    QUEUING(0, "排队中"),
    SUCCESS(1, "成功"),
    FAILED(2, "失败");

    private final int code;
    private final String desc;

    OrderStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }
}