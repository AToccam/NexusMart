ALTER TABLE order_info
    MODIFY COLUMN status TINYINT NOT NULL DEFAULT 0 COMMENT '订单状态：0排队中,1下单成功,2下单失败,3已支付,4支付失败';
