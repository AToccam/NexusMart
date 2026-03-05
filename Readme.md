# NexusMart 分布式秒杀系统 - 数据库架构设计文档

## 1. 架构设计思想
本系统的底层数据模型专为高并发秒杀场景设计，核心采用了以下工业界主流架构思想：
* **冷热数据分离**：将日常浏览的普通商品/订单与极高频写操作的秒杀商品/订单进行物理分表，避免秒杀洪峰拖垮整个电商主站。
* **乐观锁机制 (Optimistic Locking)**：在秒杀商品表引入 `version` 字段，利用数据库底层的行级锁特性，作为防超卖的最后一道防线。
* **联合唯一索引兜底**：在秒杀订单表建立 `(user_id, goods_id)` 的唯一约束，从数据库层面彻底阻断黄牛脚本的重复刷单。
* **安全加盐设计**：用户密码采用 `MD5 + 独立随机 Salt` 的方式加密存储，防止彩虹表攻击与数据库撞库。



---

## 2. 基础信息层

### 2.1 顾客信息表 (`user_info`)
记录C端用户的基本注册与安全信息。

| 字段名 | 数据类型 | 约束 | 默认值 | 描述说明 |
| :--- | :--- | :--- | :--- | :--- |
| `id` | bigint(20) | PK, AUTO_INCREMENT | - | 用户全局唯一ID |
| `nickname` | varchar(255) | NOT NULL | - | 用户昵称 |
| `password` | varchar(32) | - | NULL | MD5加密后的密码密文 |
| `salt` | varchar(10) | - | NULL | 随机密码盐，用于保障数据安全 |
| `register_time` | datetime | - | CURRENT_TIMESTAMP | 账号注册时间 |

### 2.2 商家信息表 (`merchant_info`)
支持平台多商家入驻的基础表。

| 字段名 | 数据类型 | 约束 | 默认值 | 描述说明 |
| :--- | :--- | :--- | :--- | :--- |
| `id` | bigint(20) | PK, AUTO_INCREMENT | - | 商家全局唯一ID |
| `shop_name` | varchar(128) | NOT NULL | - | 店铺名称 |
| `status` | tinyint(4) | - | 1 | 营业状态：0停业，1正常 |

---

## 3. 商品层 (冷热分离)

### 3.1 普通商品表 (`goods`)
存储所有商品的日常静态数据（冷数据），面向普通的浏览和搜索流量。

| 字段名 | 数据类型 | 约束 | 默认值 | 描述说明 |
| :--- | :--- | :--- | :--- | :--- |
| `id` | bigint(20) | PK, AUTO_INCREMENT | - | 普通商品ID |
| `merchant_id` | bigint(20) | NOT NULL | - | 关联的商家ID |
| `goods_name` | varchar(128) | NOT NULL | - | 商品名称 |
| `goods_img` | varchar(256) | - | NULL | 商品主图的URL地址 |
| `goods_price` | decimal(10,2) | - | 0.00 | 日常销售价格 |
| `goods_stock` | int(11) | - | 0 | 日常可售库存 |

### 3.2 秒杀商品表 (`seckill_goods`)
专门应对高频瞬时流量的秒杀专表（热数据）。它依附于普通商品，但独立管理秒杀价格与库存。

| 字段名 | 数据类型 | 约束 | 默认值 | 描述说明 |
| :--- | :--- | :--- | :--- | :--- |
| `id` | bigint(20) | PK, AUTO_INCREMENT | - | 秒杀配置主键ID |
| `goods_id` | bigint(20) | NOT NULL | - | 关联的普通商品ID (`goods.id`) |
| `seckill_price`| decimal(10,2) | - | 0.00 | 秒杀专属特价 |
| `stock_count` | int(11) | - | 0 | 秒杀专属独立库存池 |
| `start_time` | datetime | NOT NULL | - | 秒杀活动开始时间 |
| `end_time` | datetime | NOT NULL | - | 秒杀活动结束时间 |
| `version` | int(11) | - | 0 | **乐观锁版本号**，每次成功扣减库存+1 |

---

## 4. 订单与交易层 (冗余与防刷)

### 4.1 完整订单表 (`order_info`)
记录一笔交易的完整生命周期与资金快照。

| 字段名 | 数据类型 | 约束 | 默认值 | 描述说明 |
| :--- | :--- | :--- | :--- | :--- |
| `id` | bigint(20) | PK, AUTO_INCREMENT | - | 订单流水号 |
| `user_id` | bigint(20) | NOT NULL | - | 购买者ID |
| `merchant_id` | bigint(20) | NOT NULL | - | 售卖者ID |
| `goods_id` | bigint(20) | NOT NULL | - | 购买的商品ID |
| `goods_name` | varchar(128) | NOT NULL | - | 商品名称快照（防后续商品改名） |
| `order_price` | decimal(10,2) | - | 0.00 | 实际支付成交价 |
| `status` | tinyint(4) | - | 0 | 订单状态：0未支付, 1已支付, 2已发货, 3已退款 |
| `create_time` | datetime | - | CURRENT_TIMESTAMP | 订单创建时间 |

### 4.2 秒杀订单防重表 (`seckill_order`)
极其轻量的路由表，利用联合唯一索引作为高并发下防止同一用户重复购买的终极物理防线。

| 字段名 | 数据类型 | 约束 | 默认值 | 描述说明 |
| :--- | :--- | :--- | :--- | :--- |
| `id` | bigint(20) | PK, AUTO_INCREMENT | - | 主键ID |
| `user_id` | bigint(20) | NOT NULL | - | 购买者ID |
| `order_id` | bigint(20) | NOT NULL | - | 关联的完整订单ID (`order_info.id`) |
| `goods_id` | bigint(20) | NOT NULL | - | 秒杀商品ID |

> **安全核心机制**：本表强制设定了 `UNIQUE KEY u_uid_gid (user_id, goods_id)`。当并发请求穿透上层锁到达数据库时，底层只会允许唯一一条记录插入成功，其余抛出 `DuplicateKeyException`，从而确保一人一单。