CREATE TABLE IF NOT EXISTS user_info (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  nickname VARCHAR(255) NOT NULL,
  password VARCHAR(64) DEFAULT NULL,
  salt VARCHAR(32) DEFAULT NULL,
  register_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_nickname (nickname)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS merchant_info (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  shop_name VARCHAR(128) NOT NULL,
  status TINYINT NOT NULL DEFAULT 1
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS goods (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  merchant_id BIGINT NOT NULL,
  goods_name VARCHAR(128) NOT NULL,
  goods_img VARCHAR(256) DEFAULT NULL,
  goods_price DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  goods_stock INT NOT NULL DEFAULT 0,
  description VARCHAR(1024) DEFAULT NULL COMMENT '商品描述，参与 ES multi_match 全文检索',
  KEY idx_goods_merchant (merchant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS seckill_goods (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  goods_id BIGINT NOT NULL,
  seckill_price DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  stock_count INT NOT NULL DEFAULT 0,
  start_time DATETIME NOT NULL,
  end_time DATETIME NOT NULL,
  version INT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_seckill_goods_goods (goods_id),
  KEY idx_seckill_goods_time (start_time, end_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS order_info (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_no BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  merchant_id BIGINT NOT NULL,
  goods_id BIGINT NOT NULL,
  goods_name VARCHAR(128) NOT NULL,
  order_price DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  status TINYINT NOT NULL DEFAULT 0 COMMENT '0=QUEUING,1=SUCCESS,2=FAILED,3=PAID,4=PAY_FAILED',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_order_no (order_no),
  KEY idx_order_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS seckill_order (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  order_id BIGINT NOT NULL,
  goods_id BIGINT NOT NULL,
  UNIQUE KEY u_uid_gid (user_id, goods_id),
  KEY idx_so_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO merchant_info (id, shop_name, status)
VALUES (1, 'NexusMart Official', 1)
ON DUPLICATE KEY UPDATE shop_name = VALUES(shop_name), status = VALUES(status);

INSERT INTO goods (id, merchant_id, goods_name, goods_img, goods_price, goods_stock, description)
VALUES (1, 1, 'NexusMart Demo Goods', '/images/demo-goods.png', 199.00, 1000,
        'NexusMart 演示商品，用于验证全文搜索 multi_match 多字段检索能力。')
ON DUPLICATE KEY UPDATE goods_name = VALUES(goods_name), goods_price = VALUES(goods_price),
                        goods_stock = VALUES(goods_stock), description = VALUES(description);

INSERT INTO seckill_goods (id, goods_id, seckill_price, stock_count, start_time, end_time, version)
VALUES (1, 1, 99.00, 100, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 30 DAY), 0)
ON DUPLICATE KEY UPDATE seckill_price = VALUES(seckill_price), stock_count = VALUES(stock_count), end_time = VALUES(end_time);

INSERT INTO user_info (id, nickname, password, salt)
VALUES (1, 'test_user', 'd41d8cd98f00b204e9800998ecf8427e', 'salt')
ON DUPLICATE KEY UPDATE nickname = VALUES(nickname);
