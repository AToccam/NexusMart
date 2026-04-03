package com.nexusmart.seckill.vo;

import lombok.Data;

@Data
public class SeckillSubmitResponse {

    private String requestId;
    private Long orderNo;
    private Integer status;
    private String statusDesc;
}