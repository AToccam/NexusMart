package com.nexusmart.seckill.mq;

import lombok.Data;

@Data
public class SeckillOrderMessage {

    private String requestId;
    private Long orderNo;
    private Long userId;
    private Long seckillId;
    private Long createdAt;
}