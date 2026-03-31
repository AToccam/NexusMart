ALTER TABLE order_info
    ADD COLUMN order_no BIGINT NULL COMMENT '业务订单号（雪花ID）' AFTER id;

UPDATE order_info
SET order_no = id
WHERE order_no IS NULL;

ALTER TABLE order_info
    MODIFY COLUMN order_no BIGINT NOT NULL COMMENT '业务订单号（雪花ID）';

ALTER TABLE order_info
    ADD UNIQUE KEY uk_order_no (order_no);

ALTER TABLE order_info
    MODIFY COLUMN status TINYINT NOT NULL DEFAULT 0 COMMENT '订单状态：0排队中,1成功,2失败';
