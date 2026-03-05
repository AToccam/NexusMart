package com.nexusmart.seckill.entity;

import lombok.Data;

/**
 * 商家信息实体类，对应数据库 merchant_info 表
 */
@Data
public class MerchantInfo {

    /** 商家 ID（自增主键） */
    private Long id;

    /** 店铺名称 */
    private String shopName;

    /** 营业状态：0-停业  1-正常 */
    private Integer status;
}
