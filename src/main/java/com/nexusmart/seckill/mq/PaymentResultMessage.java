package com.nexusmart.seckill.mq;

import lombok.Data;

@Data
public class PaymentResultMessage {

    private String eventId;
    private Long orderNo;
    private Boolean paid;
    private String paymentNo;
    private String reason;
    private Long createdAt;
}