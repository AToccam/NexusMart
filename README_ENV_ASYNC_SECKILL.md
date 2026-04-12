# 异步秒杀环境改造清单（仅记录，不执行部署）

本文档只记录为了支持“Redis 预扣 + Kafka 异步下单 + 重试/DLT + 补偿任务”所需的环境准备项。
当前补充了“订单服务与库存服务分离、支付服务异步回调订单状态”的一致性配置要求。

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
- `KAFKA_TOPIC_PAYMENT_RESULT`，默认：`seckill.payment.result.v1`
- `KAFKA_TOPIC_PAYMENT_RESULT_DLT`，默认：`seckill.payment.result.v1.DLT`

说明：

- 应用启动时会尝试创建业务 Topic 与 DLT Topic（需要 Broker 开启自动创建或具备 Admin 权限）。
- 生产环境建议关闭自动创建，改为显式运维建 Topic。

## 3. Topic 规划建议

- 下单业务 Topic：`seckill.order.create.v1`
- 下单死信 Topic：`seckill.order.create.v1.DLT`
- 支付结果 Topic：`seckill.payment.result.v1`
- 支付结果死信 Topic：`seckill.payment.result.v1.DLT`
- 分区数：`3`（可按并发和消费者数提升）
- 副本数：`1`（开发）/ `3`（生产）

## 4. 数据库变更

请先执行以下迁移脚本：

- `src/main/resources/db/migration/V20260331_01__order_info_add_order_no_and_status.sql`
- `src/main/resources/db/migration/V20260403_02__order_info_status_extend_for_payment.sql`

作用：

- 为 `order_info` 新增 `order_no`
- 回填历史订单 `order_no`
- 给 `order_no` 加唯一索引
- 统一状态定义为：`0=排队中, 1=下单成功, 2=下单失败, 3=已支付, 4=支付失败`

## 5. Redis 键空间说明

新增键前缀：

- `seckill:pending:{requestId}`：待处理请求快照
- `seckill:pending:index`：待补偿索引（ZSet）
- `seckill:result:{requestId}`：请求处理结果
- `seckill:msg:done:{requestId}`：消费端幂等完成标记
- `seckill:msg:lock:{requestId}`：消费并发锁
- `seckill:compensate:lock:{requestId}`：补偿并发锁
- `seckill:payment:event:done:{eventId}`：支付事件幂等完成标记
- `seckill:payment:event:lock:{eventId}`：支付事件并发锁

## 6.1 微服务一致性链路（建议）

### A. 下单 + 库存扣减一致性（消息最终一致性）

- 入口服务（Order API）只做 Redis 预扣与限购校验。
- 成功后投递下单消息（可视作发送给库存服务 / 库存域消费者）。
- 消费端本地事务执行：库存扣减 + 订单创建。
- 失败重试，超限进入 DLT；由补偿任务回补 Redis 库存与限购标记。
- 对重复键冲突（同单号重试 / 同用户重复抢购）增加补偿：
	- 回补 `seckill_goods.stock_count`
	- 清理当前事务内可能产生的脏订单记录
	- 返回已存在订单，保证幂等返回

### B. 支付 + 订单状态更新一致性（消息最终一致性）

- 支付服务支付完成后发布支付结果消息（成功/失败）。
- 订单服务消费支付结果并做幂等校验（eventId）。
- 本地事务按状态机更新订单状态：
	- 支付成功：仅允许 `SUCCESS/PAY_FAILED -> PAID`
	- 支付失败：仅允许 `SUCCESS -> PAY_FAILED`
	- 禁止 `PAID -> PAY_FAILED` 回写，防止乱序消息覆盖已支付结果
- 消费失败重试，超限进入 DLT，并在 DLT 消费逻辑中标记支付失败兜底。

### C. 关键一致性约束（实现口径）

- Redis 预扣只负责快速削峰，不作为最终库存真值。
- 最终库存真值以库存库（`seckill_goods`）本地事务结果为准。
- 支付事件使用 `eventId` 幂等键防重，订单状态使用条件更新防逆向覆盖。

## 6.2 关键参数建议

- `app.kafka.consumer.retry.max-attempts`：建议 3~5
- `app.kafka.consumer.retry.backoff-ms`：建议 1000~3000
- `app.seckill.idempotent-ttl-seconds`：建议 >= 7 天
- `app.seckill.compensation.timeout-ms`：建议 60s~180s
- `app.seckill.compensation.scan-interval-ms`：建议 5s~15s
- `app.seckill.compensation.batch-size`：按流量调优（建议 100~500）

## 7. 运行前检查

- Redis 中存在秒杀库存预热键：`seckill:stock:{seckillId}`
- Kafka Broker 可连通，业务 Topic / DLT Topic 可写入
- 应用配置中的 Kafka 地址与消费组正确
- MySQL 中 `order_info.order_no` 已建唯一索引

## 8. 生产化建议

- 为 Kafka、Redis、MySQL 增加监控告警（延迟、积压、失败率）
- 对 DLT 建立人工重放流程
- 对补偿任务增加审计日志（requestId、回补原因、时间）
- 多实例部署时确保雪花 `worker-id` / `datacenter-id` 不冲突

## 9. TCC 方案（可选替代）

- 若你后续需要强一致，可在库存服务和订单服务实现 TCC：Try 预留库存、Confirm 扣减生效、Cancel 释放库存。
- 复杂度较高，建议在消息最终一致性稳定后再评估。

## 10. 分库分表（选做，后置）

- 当前阶段先不接入 ShardingSphere，优先稳定“快速返回 + 异步下单 + 幂等补偿”主链路。
- 建议在链路稳定并完成压测后，再评估接入 ShardingSphere-JDBC 或 ShardingSphere-Proxy。
- 推荐分片策略：按 user_id 分库，按 order_no 分表。
