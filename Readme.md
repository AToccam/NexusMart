# NexusMart

> 基于 Spring Boot 3 + Spring Cloud Alibaba 构建的分布式电商秒杀系统,涵盖容器化部署、多级缓存、消息队列削峰、读写分离、服务治理等高并发核心技术点。

![Java](https://img.shields.io/badge/Java-17-blue)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.6-brightgreen)
![Spring Cloud](https://img.shields.io/badge/Spring%20Cloud-2023.0.3-blue)
![MySQL](https://img.shields.io/badge/MySQL-8.0-orange)
![Redis](https://img.shields.io/badge/Redis-7-red)
![Kafka](https://img.shields.io/badge/Kafka-3.7-black)
![Docker](https://img.shields.io/badge/Docker-Compose%20v2-2496ED)
![License](https://img.shields.io/badge/License-MIT-green)

---

## 目录

- [项目简介](#项目简介)
- [核心特性](#核心特性)
- [技术栈](#技术栈)
- [系统架构](#系统架构)
- [快速开始](#快速开始)
- [项目结构](#项目结构)
- [数据模型](#数据模型)
- [核心 API](#核心-api)
- [配置说明](#配置说明)
- [验证与压测](#验证与压测)
- [常见运维操作](#常见运维操作)
- [路线图](#路线图)
- [许可证](#许可证)

---

## 项目简介

NexusMart 是一个面向**高并发秒杀场景**的全栈电商系统实战项目,使用工业级技术栈端到端落地了从容器化部署、流量入口、缓存、消息队列、异步下单、最终一致性补偿到服务发现 / 动态配置 / 熔断降级的完整链路。

设计目标:
- 在单机 Docker 环境中即可一键拉起完整分布式拓扑
- 用最贴近生产的方式演示秒杀系统每个关键决策点
- 代码可读 / 配置可调 / 行为可观测,便于学习与改造

## 核心特性

| 维度 | 实现 |
|------|------|
| 容器化部署 | `docker-compose` 一键拉起 9 个组件,服务健康依赖串联 |
| 反向代理 | Nginx 80 端口,动静分离 + `/static/` 强缓存 7 天 |
| 多级缓存 | **Caffeine L1**(进程内)+ **Redis L2**(分布式)+ DB,自动降级 |
| 缓存穿透 | **Guava BloomFilter** 启动时灌入全量商品 ID + 空对象短 TTL 兜底 |
| 缓存击穿 | Redis `SETNX` 分布式互斥锁,只允许单线程重建热点 Key |
| 缓存雪崩 | TTL 随机化 + L1 兜底 + Redis 异常熔断降级到 DB |
| 防超卖 | Redis Lua 脚本原子预扣 + MySQL 行级乐观锁(`version`)双重保险 |
| 防重复下单 | Redis SADD 原子幂等 + DB 联合唯一索引 `(user_id, goods_id)` 兜底 |
| 异步下单 | Kafka 削峰,业务订单号采用 **Snowflake** 雪花算法生成 |
| 失败补偿 | 死信队列 + 定时扫描超时 pending 请求,回补 Redis 库存与幂等位 |
| 支付一致性 | 基于 `eventId` 的幂等消费 + 状态机条件更新,严防非法状态跳转 |
| 读写分离 | AOP + `AbstractRoutingDataSource`,按方法名 / 注解切换主从 |
| 服务治理 | Nacos 服务发现 + 动态配置 + `@RefreshScope` 热刷新 |
| 流量治理 | Spring Cloud Gateway + Redis 令牌桶限流 + Resilience4j 熔断降级 |

## 技术栈

| 层次 | 技术选型 |
|------|----------|
| 后端框架 | Spring Boot 3.2.6 + Spring Cloud 2023.0.3 |
| 微服务套件 | Spring Cloud Alibaba 2023.0.1.2 |
| 数据库 | MySQL 8.0(一主一从,GTID 复制) |
| 缓存 | Redis 7(AOF 持久化)+ Caffeine 3.1.8 |
| 布隆过滤器 | Guava 33.3 `BloomFilter` |
| 消息队列 | Apache Kafka 3.7(KRaft 模式,无 Zookeeper) |
| 服务注册 / 配置 | Nacos 2.4 |
| 网关 | Spring Cloud Gateway(响应式) |
| 熔断 / 限流 | Resilience4j + Gateway RequestRateLimiter |
| ORM | MyBatis 3 + HikariCP |
| 容器化 | Docker + Docker Compose v2 |
| 反向代理 | Nginx 1.27 |
| 压力测试 | Apache JMeter |

## 系统架构

```
                           ┌─────────────────────────┐
                           │      JMeter / 浏览器    │
                           └────────────┬────────────┘
                                        │ :80
                                        ▼
                              ┌──────────────────┐
                              │      Nginx       │  动静分离 + IP 限流
                              │  /static/ 强缓存 │  /api/** → Gateway
                              └────────┬─────────┘
                                       │ :8080
                                       ▼
                              ┌──────────────────┐
                              │ Spring Cloud GW  │  Nacos 服务发现 + 令牌桶
                              │  Resilience4j    │  熔断 / 降级 / 路由
                              └────────┬─────────┘
                                       │ lb://nexusmart-seckill
                                       ▼
                              ┌──────────────────┐
                              │  Seckill App     │  L1 Caffeine ← BloomFilter
                              │  (Spring Boot)   │  L2 Redis    ← 雪花 ID
                              │       :8081      │  Kafka Producer / Consumer
                              └──┬───────┬───────┘
                                 │       │
                ┌────────────────┘       └─────────────┐
                │                                       │
                ▼                                       ▼
       ┌──────────────────┐                   ┌──────────────────┐
       │   MySQL Master   │ ← GTID 同步 →    │   MySQL Slave    │
       │   (写 :3307)     │                   │   (读 :3308)     │
       └──────────────────┘                   └──────────────────┘

       ┌──────────┐    ┌──────────┐    ┌──────────┐
       │  Redis   │    │  Kafka   │    │  Nacos   │
       │  :6379   │    │  :9092   │    │  :8848   │
       └──────────┘    └──────────┘    └──────────┘
```

秒杀核心链路:

```
用户请求
  → Nginx 限流(5r/s,burst=10)
  → Gateway 令牌桶 + 熔断
  → SeckillService.submitSeckill
        ├─ Lua 脚本原子执行:SISMEMBER 防重 + DECR 库存 + 写 pending + 全局索引
        ├─ Snowflake 生成 orderNo
        └─ KafkaTemplate.send(seckill.order.create.v1)
  → 同步返回 "排队中"

Kafka Consumer
  → 幂等位检查(messageDone)
  → @Transactional 写订单 + 乐观锁扣 DB 库存 + 写 seckill_order(防重表)
  → 标记 SUCCESS,清理 pending

定时补偿(每 10s)
  → ZSET 扫描超时(>120s)的 pending requestId
  → 验证 DB 无对应订单 → 回补 Redis 库存与幂等位 → 标记 FAILED
```

## 快速开始

### 环境要求

- Docker 24+ / Docker Compose v2
- 8 GB 以上空闲内存(Kafka + Nacos + ES 镜像较大)
- 端口 `80 / 3307 / 3308 / 6379 / 8080 / 8081 / 8848 / 9092` 未被占用

校验:

```bash
docker --version
docker compose version
```

### 一键启动

```bash
docker compose up -d --build
```

构建过程:
1. Maven 多阶段构建 `nexusmart-app` 与 `nexusmart-gateway` 镜像
2. MySQL 主库自动执行 `docker/mysql/init/001_schema.sql` 建表 + 插入演示数据
3. `mysql-master-init` 创建复制账号 `repl`,`mysql-slave-init` 配置 GTID 主从

### 健康检查

```bash
docker compose ps                                          # 全部 healthy
curl http://127.0.0.1:8848/nacos/                          # Nacos 控制台
curl http://127.0.0.1:8080/actuator/health                 # Gateway
curl http://127.0.0.1:8081/actuator/health                 # App
curl http://127.0.0.1/api/goods/seckill/list               # 列表接口经 Nginx → GW → App
```

### 访问入口

| 入口 | 地址 | 说明 |
|------|------|------|
| 前端页面 | http://127.0.0.1 | Nginx 静态托管 + 秒杀演示 UI |
| API 网关 | http://127.0.0.1:8080 | 跳过 Nginx 直连 Gateway |
| 后端直连 | http://127.0.0.1:8081 | 跳过 Gateway,排障用 |
| Nacos 控制台 | http://127.0.0.1:8848/nacos | 用户名 / 密码留空 |

## 项目结构

```
NexusMart/
├── docker-compose.yml          # 编排:MySQL 主从 + Redis + Kafka + Nacos + App + GW + Nginx
├── Dockerfile                  # App 多阶段构建(Maven → JRE 17)
├── nginx.conf                  # 反向代理 + 动静分离 + 限流
├── pom.xml                     # 父 POM
├── docker/
│   ├── kafka/server.properties # Kafka KRaft 单节点配置
│   └── mysql/init/001_schema.sql
├── gateway/                    # Spring Cloud Gateway 子模块
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/main/...
├── nacos/configs/              # Nacos 配置中心备份(yml)
├── scripts/publish-nacos-config.sh
├── jmeter/                     # JMeter 压测脚本
└── src/main/java/com/nexusmart/seckill/
    ├── NexusMartApplication.java
    ├── common/                 # Result / OrderStatus / Redis Key 常量
    ├── config/                 # 数据源 / Kafka / 雪花 ID / 启动预热
    │   └── datasource/         # 读写分离 AOP
    ├── controller/             # GoodsController / SeckillController / UserController
    ├── entity/                 # POJO
    ├── mapper/                 # MyBatis Mapper
    ├── mq/                     # 消息体 DTO
    ├── service/                # 业务核心
    │   ├── SeckillService               # Lua 预扣 + 投递 Kafka
    │   ├── SeckillOrderProducer/Consumer
    │   ├── SeckillOrderAsyncService     # 异步落库 + 防重
    │   ├── SeckillCompensationService   # 死信 + 定时补偿
    │   ├── PaymentResultProducer/Consumer
    │   ├── PaymentEventConsistencyService
    │   ├── BloomFilterService           # 缓存穿透防护
    │   └── GoodsService / UserService
    ├── util/
    │   ├── RedisCacheUtil               # L1+L2+互斥锁
    │   └── SnowflakeIdGenerator
    └── vo/                              # 接口出参
```

## 数据模型

> **设计要点**:冷热数据分离 + 乐观锁防超卖 + 联合唯一索引防重复下单 + 加盐 MD5 防撞库。

### 顾客信息表 `user_info`

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `id` | bigint | PK | 用户全局唯一 ID |
| `nickname` | varchar(255) | NOT NULL, UNIQUE `uk_nickname` | 昵称,防重名 |
| `password` | varchar(32) | - | MD5(salt + 原密码) |
| `salt` | varchar(10) | - | 随机盐,防彩虹表 |
| `register_time` | datetime | DEFAULT CURRENT_TIMESTAMP | 注册时间 |

### 商家信息表 `merchant_info`

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `id` | bigint | PK | 商家 ID |
| `shop_name` | varchar(128) | NOT NULL | 店铺名 |
| `status` | tinyint | DEFAULT 1 | 0=停业,1=正常 |

### 普通商品表 `goods` — 冷数据

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `id` | bigint | PK | 商品 ID |
| `merchant_id` | bigint | NOT NULL | 商家 ID |
| `goods_name` | varchar(128) | NOT NULL | 商品名 |
| `goods_img` | varchar(256) | - | 主图 URL |
| `goods_price` | decimal(10,2) | DEFAULT 0 | 日常价 |
| `goods_stock` | int | DEFAULT 0 | 日常库存 |

### 秒杀商品表 `seckill_goods` — 热数据

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `id` | bigint | PK | 秒杀配置 ID |
| `goods_id` | bigint | UNIQUE `uk_seckill_goods_goods` | 关联 `goods.id` |
| `seckill_price` | decimal(10,2) | - | 秒杀价 |
| `stock_count` | int | - | 秒杀独立库存池 |
| `start_time` / `end_time` | datetime | NOT NULL, KEY `idx_seckill_goods_time` | 活动起止时间 |
| `version` | int | DEFAULT 0 | **乐观锁版本号**,每次扣减 +1 |

### 完整订单表 `order_info`

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `id` | bigint | PK | 订单流水号 |
| `order_no` | bigint | UNIQUE `uk_order_no`, NOT NULL | **业务订单号(雪花 ID)** |
| `user_id` | bigint | NOT NULL, KEY `idx_order_user` | 买家 ID |
| `merchant_id` | bigint | NOT NULL | 卖家 ID |
| `goods_id` | bigint | NOT NULL | 商品 ID |
| `goods_name` | varchar(128) | NOT NULL | 商品名快照 |
| `order_price` | decimal(10,2) | - | 成交价 |
| `status` | tinyint | DEFAULT 0 | 0=QUEUING / 1=SUCCESS / 2=FAILED / 3=PAID / 4=PAY_FAILED |
| `create_time` | datetime | DEFAULT CURRENT_TIMESTAMP | 下单时间 |

### 秒杀订单防重表 `seckill_order`

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `id` | bigint | PK | 主键 |
| `user_id` | bigint | UNIQUE `u_uid_gid` 联合 | 买家 ID |
| `order_id` | bigint | KEY `idx_so_order` | 关联 `order_info.id` |
| `goods_id` | bigint | UNIQUE `u_uid_gid` 联合 | 秒杀商品 ID |

> **安全核心**:`UNIQUE KEY u_uid_gid (user_id, goods_id)` 是高并发下"一人一单"的终极物理防线 —— 上层 Redis 防重失效时,数据库会抛 `DuplicateKeyException`。

## 核心 API

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/goods/seckill/list` | 当前正在进行的秒杀商品列表(走 L1+L2 缓存) |
| GET | `/api/goods/detail?goodsId=` | 商品详情(BloomFilter → L1 → L2 → DB) |
| POST | `/api/seckill/do?userId=&seckillId=` | 提交秒杀(异步返回排队结果) |
| GET | `/api/seckill/orders?userId=` | 查询用户订单列表 |
| GET | `/api/seckill/order/no?orderNo=` | 按业务订单号查订单 |
| POST | `/api/seckill/pay/mock?orderNo=&paid=` | 模拟支付回调,触发 Kafka 支付结果事件 |
| GET | `/api/seckill/config/current` | 查看 Nacos 动态配置当前生效值 |
| GET | `/api/seckill/pressure/ping?forceFail=` | 压测演示接口(配合 Gateway 熔断验证) |
| POST | `/api/user/login` | 用户登录(支持 ID 或昵称) |
| POST | `/api/user/register` | 用户注册(MD5 + Salt) |

联调示例:

```bash
# 提交秒杀
curl -X POST "http://127.0.0.1/api/seckill/do?userId=1&seckillId=1"

# 查询订单
curl "http://127.0.0.1/api/seckill/orders?userId=1"

# 模拟支付成功
curl -X POST "http://127.0.0.1/api/seckill/pay/mock?orderNo=<上一步返回的 orderNo>&paid=true"
```

## 配置说明

### 关键环境变量(在 `docker-compose.yml` 覆盖)

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `DB_MASTER_HOST` / `DB_SLAVE_HOST` | `mysql-master` / `mysql-slave` | 主从 host |
| `REDIS_HOST` / `REDIS_PORT` | `redis` / `6379` | Redis 地址 |
| `KAFKA_BOOTSTRAP_SERVERS` | `kafka:9092` | Kafka broker |
| `NACOS_SERVER_ADDR` | `nacos:8848` | Nacos 注册 / 配置中心 |
| `ID_WORKER` / `ID_DATACENTER` | `1` / `1` | **多实例部署务必区分**,防雪花 ID 冲突 |
| `JAVA_OPTS` | `-Xms256m -Xmx512m` | JVM 堆参数 |

### 应用层配置(`application.yml`)

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `app.cache.empty-ttl-seconds` | 300 | 空对象 TTL(防穿透) |
| `app.cache.lock-ttl-seconds` | 10 | 互斥锁 TTL(防击穿) |
| `app.cache.local.maximum-size` | 10000 | Caffeine L1 容量 |
| `app.cache.local.expire-seconds` | 60 | L1 写入后过期 |
| `app.bloom.expected-insertions` | 100000 | BloomFilter 预期容量 |
| `app.bloom.fpp` | 0.01 | 期望误判率 |
| `app.seckill.pending-ttl-seconds` | 600 | 排队中状态 TTL |
| `app.seckill.compensation.timeout-ms` | 120000 | 超时补偿阈值 |
| `app.seckill.compensation.scan-interval-ms` | 10000 | 补偿扫描间隔 |

### Nacos 动态配置

Data ID:`nexusmart-seckill-dev.yml`,Group:`DEFAULT_GROUP`

| 配置项 | 说明 |
|--------|------|
| `nexusmart.dynamic.message` | 动态文本,演示 `@RefreshScope` 热刷新 |
| `nexusmart.pressure.simulate-latency-ms` | 模拟接口延迟,单位 ms |
| `nexusmart.pressure.failure-rate` | 模拟失败比例,**0~1 之间**(0.3 = 30%) |

修改后无需重启,通过 `/api/seckill/config/current` 可立即看到新值。

## 验证与压测

### 缓存穿透防护

```bash
# 不存在的 goodsId,布隆过滤器应直接拦截,不查 Redis / DB
curl "http://127.0.0.1/api/goods/detail?goodsId=99999999"
# → {"code":400,"msg":"商品不存在"}
```

### 缓存击穿(并发查询热点)

```bash
# 先清空 Redis 中商品详情
docker exec nexusmart-redis redis-cli DEL "seckill:goods:detail:1"

# 100 并发查询同一商品
ab -n 100 -c 100 "http://127.0.0.1/api/goods/detail?goodsId=1"

# 查看后端日志,应仅有 1 条 DB SELECT 日志(其余被 L1+互斥锁吸收)
```

### 秒杀并发压测

JMeter 脚本位置:`jmeter/gateway-governance-test.jmx`

- 线程数:200,Ramp-Up:10s,循环:20 次
- 预期 stock_count(默认 100)被精确扣完,无超卖
- 查询 `seckill_order` 表,行数 ≤ stock 初始值

### 读写分离验证

```bash
# 触发写 + 立即读,在主从日志中应看到分别命中
docker compose logs -f --tail=50 app | grep -E "(useMaster|useSlave|insert|select)"
```

## 常见运维操作

```bash
# 查看实时日志
docker compose logs -f app
docker compose logs -f kafka
docker compose logs -f mysql-master

# 重启某服务
docker compose restart app

# 停止保留数据卷
docker compose down

# 彻底重置(含数据卷)
docker compose down -v

# 数据库手动补救(仅历史卷场景)
docker exec -i nexusmart-mysql-master mysql -uroot -p123456 nexusmart \
    < docker/mysql/init/001_schema.sql
docker exec nexusmart-redis redis-cli FLUSHALL
docker compose up -d app nginx

# 进入容器排障
docker exec -it nexusmart-mysql-master mysql -uroot -p123456
docker exec -it nexusmart-redis redis-cli
docker exec -it nexusmart-kafka /opt/kafka/bin/kafka-topics.sh \
    --bootstrap-server localhost:9092 --list
```

## 路线图

- [x] Module 1 — Docker 部署 + Nginx 反向代理
- [x] Module 2 — 动静分离 + 静态资源强缓存
- [x] Module 3 — Redis 缓存(BloomFilter + Caffeine L1 + 互斥锁 + 随机 TTL)
- [x] Module 4 — MySQL 一主一从读写分离
- [ ] Module 5 — Elasticsearch 商品全文搜索
- [x] Module 6 — Kafka 异步秒杀 + 雪花 ID + 死信补偿
- [ ] Module 7 — ShardingSphere 订单分库分表
- [x] Module 8 — 支付一致性(eventId 幂等 + 状态机)
- [x] Module 9 — Nacos 服务发现 + 动态配置 + Spring Cloud Gateway
- [ ] Module 10 — Sentinel 限流 / 熔断 / 降级(当前由 Resilience4j 承担)

## 许可证

[MIT](LICENSE)
