# 🚀 电商秒杀系统 — 全栈分布式架构实战项目

> **致 Claude Opus：** 你是一位资深分布式系统架构师兼全栈工程师。以下是一个完整的电商秒杀系统项目，涵盖容器化部署、缓存、消息队列、分库分表、事务一致性、服务治理等核心分布式技术点。请按照模块顺序逐步实现，每个模块均需提供**完整可运行的代码**、**配置文件**和**验证说明**。

---

## 技术栈约定

| 层次 | 技术选型 |
|------|---------|
| 后端框架 | Spring Boot 3.x + Spring Cloud |
| 数据库 | MySQL 8.x（主从） |
| 缓存 | Redis 7.x |
| 消息队列 | Apache Kafka |
| 搜索引擎 | Elasticsearch 8.x |
| 分库分表 | ShardingSphere-JDBC |
| 服务注册/配置 | Nacos |
| 网关 | Spring Cloud Gateway |
| 流量治理 | Sentinel |
| 容器化 | Docker + Docker Compose |
| 反向代理 | Nginx |
| 压力测试 | Apache JMeter |

---

## 模块一：容器环境搭建与负载均衡

### 1.1 目标
- 使用 Docker 容器化运行：MySQL、后端服务（多实例）、Nginx
- 后端服务分别监听 **8081** 和 **8082** 端口
- Nginx 监听 **80** 端口，反向代理并负载均衡后端服务

### 1.2 任务清单

#### ① Dockerfile
为 Spring Boot 后端服务编写 `Dockerfile`，要求：
- 基于 `eclipse-temurin:17-jdk-alpine`
- 支持通过环境变量 `SERVER_PORT` 动态指定启动端口
- 配置健康检查（`HEALTHCHECK`）

#### ② docker-compose.yml
编写完整的 `docker-compose.yml`，包含以下服务：
```
services:
  mysql        # MySQL 8，挂载初始化 SQL
  backend-1    # 后端实例，端口 8081
  backend-2    # 后端实例，端口 8082
  nginx        # Nginx，端口 80，依赖 backend-1/backend-2
```
- 服务间使用自定义 bridge 网络通信
- MySQL 数据目录挂载到宿主机，确保数据持久化
- 后端服务依赖 MySQL 健康检查后再启动（`depends_on: condition: service_healthy`）

#### ③ Nginx 负载均衡配置
编写 `nginx.conf`，实现以下三种负载均衡算法（分别注释说明，可切换）：
- **轮询（Round Robin）**：默认算法
- **加权轮询（Weighted Round Robin）**：backend-1 权重 3，backend-2 权重 1
- **IP Hash**：同一客户端 IP 固定路由到同一后端

#### ④ 验证方式
提供 JMeter 测试方案（线程数 100，循环 10 次，Ramp-Up 5 秒），观察：
- 平均响应时间
- 后端日志中各实例处理的请求数（验证负载是否均衡）

---

## 模块二：动静分离

### 2.1 目标
- 编写简单的前端静态页面，通过 Nginx 直接提供静态资源
- 动态请求（`/api/**`）由 Nginx 转发到后端服务
- 对比静态资源与动态接口的响应时间差异

### 2.2 任务清单

#### ① 前端静态页面
编写 `index.html`（含内联或引用的 CSS/JS），实现：
- 商品列表展示（调用后端 API 动态加载数据）
- 页面包含至少一个本地 JS 文件（`/static/js/app.js`）和一个 CSS 文件（`/static/css/style.css`）
- 使用 `fetch` 调用 `/api/products` 接口展示商品卡片

#### ② Nginx 动静分离配置
在 `nginx.conf` 中配置：
```nginx
# 静态资源：Nginx 直接返回
location /static/ {
    root /usr/share/nginx/html;
    expires 7d;
}

# 动态接口：转发到后端集群
location /api/ {
    proxy_pass http://backend_cluster;
}
```

#### ③ JMeter 压测对比
分别压测：
- 静态文件接口（`GET /static/css/style.css`）
- 动态接口（`GET /api/products`）

记录并对比两者的**平均响应时间**、**吞吐量（TPS）**、**错误率**。

---

## 模块三：分布式缓存（Redis）

### 3.1 目标
- 为商品详情页引入 Redis 缓存，降低数据库查询压力
- 完整处理缓存**穿透**、**击穿**、**雪崩**问题

### 3.2 任务清单

#### ① 基础缓存实现
在 `ProductService` 中实现缓存逻辑：
```
查询商品详情流程：
Redis 命中 → 直接返回
Redis 未命中 → 查 MySQL → 写入 Redis（TTL 30分钟）→ 返回
```
Key 格式：`product:detail:{productId}`

#### ② 缓存穿透防护
策略：**布隆过滤器（Bloom Filter）** + **空值缓存**
- 系统启动时将全量商品 ID 加载到 Bloom Filter
- 查询前先过滤不存在的 ID
- 若数据库也不存在，缓存空值（TTL 5分钟）
- 提供完整的 `BloomFilterService` 实现代码

#### ③ 缓存击穿防护
策略：**互斥锁（Redis SETNX）**
- 缓存失效时，使用分布式锁保证只有一个线程重建缓存
- 其他线程等待或返回兜底数据
- 提供锁超时（10秒）和重试逻辑

#### ④ 缓存雪崩防护
策略：**TTL 随机化** + **多级缓存** + **熔断降级**
- Redis TTL = 基础时间（30分钟）+ 随机时间（0~10分钟）
- 本地缓存（Caffeine）作为 L1 缓存，Redis 作为 L2 缓存
- Redis 不可用时自动降级到数据库查询（配合 Sentinel 熔断）

---

## 模块四：读写分离

### 4.1 目标
- 搭建 MySQL 一主一从环境
- 应用层实现读写分离：写操作走主库，读操作走从库

### 4.2 任务清单

#### ① MySQL 主从搭建
在 `docker-compose.yml` 中添加：
```
mysql-master   # 主库，端口 3306
mysql-slave    # 从库，端口 3307
```
提供完整的主从配置文件（`my-master.cnf`、`my-slave.cnf`）和初始化脚本（配置 REPLICATION USER、CHANGE MASTER TO）。

#### ② 读写分离实现
使用 **ShardingSphere-JDBC** 的读写分离规则配置（`application.yml`），实现：
- `INSERT`/`UPDATE`/`DELETE` → 主库
- `SELECT` → 从库（多从时轮询）

#### ③ 验证读写分离
提供测试代码，分别执行写操作和读操作，通过日志打印实际执行的数据源名称，验证路由正确性。

---

## 模块五：Elasticsearch 商品搜索

### 5.1 目标
- 搭建 Elasticsearch 环境
- 实现商品的全文搜索功能（按名称、描述搜索，支持分页）

### 5.2 任务清单

#### ① ES 环境搭建
在 `docker-compose.yml` 中添加：
```
elasticsearch  # 单节点，端口 9200
kibana         # 可视化，端口 5601（可选）
```

#### ② 商品索引设计
提供商品 Index Mapping（中文分词使用 `ik_smart`），字段包括：
`productId`、`name`（text，ik_smart）、`description`（text，ik_smart）、`price`、`category`、`stock`

#### ③ 数据同步
实现 MySQL → ES 的数据同步（选择一种方案）：
- **方案A**：写数据库时同步写 ES（双写）
- **方案B**：监听 MySQL binlog（Canal）异步同步到 ES

#### ④ 搜索接口实现
`GET /api/products/search?keyword=手机&page=0&size=10`

实现多字段匹配（`multi_match`）+ 评分排序，提供完整 Controller/Service/Repository 代码。

---

## 模块六：消息队列与秒杀下单

### 6.1 目标
- 实现高并发秒杀下单功能
- Redis 缓存库存 + Kafka 异步处理订单 + 削峰填谷

### 6.2 任务清单

#### ① 秒杀核心流程
```
用户请求 → 网关限流
         → Redis 校验幂等性（防重复下单）
         → Redis 原子扣减库存（DECR，判断>=0）
         → 发送订单消息到 Kafka Topic: seckill-orders
         → 返回"排队中"（异步处理）

Kafka Consumer → 创建订单记录（MySQL）
              → 扣减数据库库存
              → 更新订单状态为"已创建"
```

#### ② 订单 ID 生成
实现**雪花算法（Snowflake）**生成订单 ID：
- 64位结构：1位符号 + 41位时间戳 + 10位机器ID + 12位序列号
- 支持按**用户ID**或**订单ID**查询订单

#### ③ 幂等性设计
防止重复下单，Redis Key：`seckill:lock:{userId}:{productId}`
- 使用 `SETNX` 设置，过期时间 24小时
- 同一用户同一商品只能秒杀成功一次

#### ④ 防超卖设计
使用 Redis Lua 脚本原子性执行库存扣减：
```lua
-- 原子检查并扣减库存
local stock = redis.call('GET', KEYS[1])
if tonumber(stock) <= 0 then return 0 end
redis.call('DECR', KEYS[1])
return 1
```

#### ⑤ 数据一致性
描述最终一致性保障方案：
- Kafka 消息持久化（`acks=all`，`min.insync.replicas=1`）
- Consumer 消费失败时的重试策略（最多重试3次，进入死信队列）
- 定时任务扫描异常订单并补偿

#### ⑥ 在 docker-compose.yml 中添加
```
zookeeper      # 端口 2181
kafka          # 端口 9092
```

---

## 模块七：分库分表

### 7.1 目标
- 使用 **ShardingSphere-JDBC** 对订单表进行分库分表
- 按用户ID分库（2个数据库），按订单ID分表（每库4张表，共8张表）

### 7.2 任务清单

#### ① 分片策略
```
分库键：user_id
分库算法：user_id % 2 → db0 / db1

分表键：order_id
分表算法：order_id % 4 → order_0 / order_1 / order_2 / order_3
```

#### ② 配置实现
提供完整的 ShardingSphere-JDBC YAML 配置（`sharding.yaml`），包含：
- 数据源配置（ds0、ds1）
- 分库分表规则
- 默认数据源（用于非分片表）

#### ③ 验证
提供测试用例，插入不同 userId 的订单，通过日志验证数据被正确路由到对应的库和表。

#### ④ 分库分表后的 ID 生成
说明为何必须使用雪花算法（避免全局 ID 冲突），以及如何配置 ShardingSphere 内置的雪花 ID 生成器。

---

## 模块八：事务与数据一致性

### 8.1 背景
订单服务和库存服务是两个独立微服务，各自拥有独立数据库。秒杀下单时需要保证：
1. **库存预扣减 + 订单创建** 的一致性
2. **订单支付 + 订单状态更新** 的一致性

### 8.2 任务清单

#### ① Redis 库存预扣减（防超卖 + 限购）
- 使用 Redis + Lua 脚本实现原子预扣减
- 每个用户限购数量校验（`seckill:user:buy:{userId}:{productId}` 计数）
- 预扣减成功后，消息投递到 Kafka

#### ② 方案A：基于消息的最终一致性
```
订单服务：
  1. 发送"创建订单"消息到 Kafka（本地事务保存消息记录）
  2. 提交本地事务（订单入库）

库存服务：
  1. 消费"创建订单"消息
  2. 执行库存扣减（幂等消费，根据 orderId 去重）
  3. 发送"库存扣减完成"消息

定时补偿：
  - 扫描超时未确认的消息，执行重试或回滚
```
提供完整实现代码，包括本地消息表设计（`outbox` 表）。

#### ③ 方案B：TCC 事务（可选，加分项）
实现三个阶段：
- **Try**：预扣库存（冻结），预建订单（PENDING 状态）
- **Confirm**：确认扣减，订单状态 → CREATED
- **Cancel**：释放冻结库存，订单状态 → CANCELLED

提供 TCC 接口定义和核心业务实现。

#### ④ 订单支付一致性
场景：用户支付成功后，订单状态需更新为 PAID，同时触发发货流程。

使用 **本地事务 + 消息事务**（Spring `@Transactional` + Kafka 事务消息）保证：
- 订单状态更新与消息发送的原子性

---

## 模块九：服务注册发现与配置中心

### 9.1 目标
- 搭建 Nacos 实现服务注册与配置管理
- 搭建 Spring Cloud Gateway 作为统一入口
- 验证动态路由和配置热更新

### 9.2 任务清单

#### ① 环境搭建
在 `docker-compose.yml` 中添加：
```
nacos          # 端口 8848，standalone 模式
gateway        # Spring Cloud Gateway，端口 8080
```

#### ② 服务注册
所有后端微服务（订单服务、库存服务、商品服务）注册到 Nacos，提供 `bootstrap.yml` 配置示例。

#### ③ 配置中心
在 Nacos 控制台创建配置（`Data ID: product-service.yaml`），包含：
```yaml
product:
  cache:
    ttl: 30
  seckill:
    limit-per-user: 1
```
在代码中使用 `@Value` 或 `@RefreshScope` 读取配置，演示**动态更新**（修改 Nacos 配置后，无需重启服务即生效）。

#### ④ Gateway 路由配置
```yaml
routes:
  - id: product-service
    uri: lb://product-service    # 负载均衡
    predicates:
      - Path=/api/products/**
  - id: order-service
    uri: lb://order-service
    predicates:
      - Path=/api/orders/**
```

#### ⑤ 验证
通过网关地址（`http://localhost:8080/api/products`）调用服务，验证：
- 路由正确性
- 多实例负载均衡
- 配置热更新效果（修改 TTL 值，观察行为变化）

---

## 模块十：流量治理（Sentinel）

### 10.1 目标
- 使用 Sentinel 对关键接口实现限流、熔断、降级
- 使用 JMeter 压测验证治理效果

### 10.2 任务清单

#### ① 限流规则
对 `GET /api/products/{id}` 配置 QPS 限流（阈值：100 QPS）：
```java
@SentinelResource(value = "getProductDetail",
    blockHandler = "getProductDetailFallback")
public Product getProductDetail(Long id) { ... }
```
触发限流时返回降级响应（HTTP 429）。

#### ② 熔断规则
对调用外部服务（ES 搜索）配置熔断：
- 策略：异常比例 > 50%，熔断 10秒
- 半开状态：尝试1次请求，成功则恢复

#### ③ 降级规则
- 熔断触发时，降级到 MySQL 查询或返回缓存兜底数据
- 提供完整的 `blockHandler` 和 `fallback` 实现

#### ④ JMeter 压测验证
- 线程数：200，循环：20次，Ramp-Up：10秒
- 观察：触发限流后的响应时间变化、错误率、吞吐量
- 截图 JMeter 聚合报告，标注限流触发时刻

---

## 整体项目结构建议

```
seckill-system/
├── docker-compose.yml          # 全量服务编排
├── nginx/
│   ├── nginx.conf
│   └── html/                   # 静态资源
├── mysql/
│   ├── master/my-master.cnf
│   ├── slave/my-slave.cnf
│   └── init.sql
├── services/
│   ├── gateway/                # Spring Cloud Gateway
│   ├── product-service/        # 商品服务
│   ├── order-service/          # 订单服务
│   └── inventory-service/      # 库存服务
├── jmeter/
│   ├── load-balance-test.jmx
│   ├── static-dynamic-test.jmx
│   └── seckill-stress-test.jmx
└── docs/
    └── architecture.md         # 架构说明
```

---

## 输出要求

对于每个模块，请提供：

1. **完整代码**：所有 Java 类、配置文件、SQL 脚本，无省略
2. **运行指令**：`docker-compose up -d` 后的完整启动和验证步骤
3. **验证截图说明**：描述在哪里看到什么日志/指标来验证功能正确
4. **原理说明**：每个关键设计决策的 2-3 句原理解释
5. **已知问题**：指出该方案在生产环境中的局限性及改进方向

> 请逐模块推进，完成一个模块后再进入下一个，避免代码遗漏。每个模块完成时请说明"**模块X已完成，可开始模块X+1**"。
```
