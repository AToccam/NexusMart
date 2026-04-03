# 异步秒杀环境改造清单（仅记录，不执行部署）

本文档只记录为了支持“Redis 预扣 + Kafka 异步下单 + 重试/DLT + 补偿任务”所需的环境准备项。

## 1. 基础组件

- MySQL 8.x（已有）
- Redis 7.x（已有）
- Kafka 3.x（新增）
- 推荐：Kafka UI（可视化查看 Topic / 消费组 / DLT）

## 2. Kafka 必备配置

应用读取以下变量：

- `KAFKA_BOOTSTRAP_SERVERS`，示例：`127.0.0.1:9092`
- `KAFKA_CONSUMER_GROUP`，示例：`nexusmart-seckill-order-group`
- `KAFKA_TOPIC_ORDER_CREATE`，默认：`seckill.order.create.v1`
- `KAFKA_TOPIC_ORDER_CREATE_DLT`，默认：`seckill.order.create.v1.DLT`

说明：

- 应用启动时会尝试创建业务 Topic 与 DLT Topic（需要 Broker 开启自动创建或具备 Admin 权限）。
- 生产环境建议关闭自动创建，改为显式运维建 Topic。

## 3. Topic 规划建议

- 业务 Topic：`seckill.order.create.v1`
- 死信 Topic：`seckill.order.create.v1.DLT`
- 分区数：`3`（可按并发和消费者数提升）
- 副本数：`1`（开发）/ `3`（生产）

## 4. 数据库变更

请先执行以下迁移脚本：

- `src/main/resources/db/migration/V20260331_01__order_info_add_order_no_and_status.sql`

作用：

- 为 `order_info` 新增 `order_no`
- 回填历史订单 `order_no`
- 给 `order_no` 加唯一索引
- 统一状态定义为：`0=排队中, 1=成功, 2=失败`

## 5. Redis 键空间说明

新增键前缀：

- `seckill:pending:{requestId}`：待处理请求快照
- `seckill:pending:index`：待补偿索引（ZSet）
- `seckill:result:{requestId}`：请求处理结果
- `seckill:msg:done:{requestId}`：消费端幂等完成标记
- `seckill:msg:lock:{requestId}`：消费并发锁
- `seckill:compensate:lock:{requestId}`：补偿并发锁

## 6. 运行前检查

- Redis 中存在秒杀库存预热键：`seckill:stock:{seckillId}`
- Kafka Broker 可连通，业务 Topic / DLT Topic 可写入
- 应用配置中的 Kafka 地址与消费组正确
- MySQL 中 `order_info.order_no` 已建唯一索引

## 7. 生产化建议

- 为 Kafka、Redis、MySQL 增加监控告警（延迟、积压、失败率）
- 对 DLT 建立人工重放流程
- 对补偿任务增加审计日志（requestId、回补原因、时间）
- 多实例部署时确保雪花 `worker-id` / `datacenter-id` 不冲突

## 8. 分库分表（选做，后置）

- 当前阶段先不接入 ShardingSphere，优先稳定“快速返回 + 异步下单 + 幂等补偿”主链路。
- 建议在链路稳定并完成压测后，再评估接入 ShardingSphere-JDBC 或 ShardingSphere-Proxy。
- 推荐分片策略：按 user_id 分库，按 order_no 分表。
