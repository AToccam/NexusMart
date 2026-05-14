## 变更日志 — 2026-05-14（A 类:存量模块审查与修复）

> 本轮聚焦"修复现有 Module 1–4/6/8 已知问题",不新增 Module 5/7/10。docker-compose 维持单实例,负载均衡仍由 `Nginx → Gateway(Nacos lb)` 承担。所有改动已通过 `mvn clean compile`(47 源文件)。

### Module 1 — 容器化与负载均衡
- [nginx.conf](nginx.conf) upstream 上方追加三种算法切换示例注释(轮询 / 加权轮询 weight=3:1 / IP Hash),方便按需启用对比。
- 保留现有单实例架构(Nginx → Gateway → app),理由:已通过 Nacos 服务发现自动负载均衡,语义比静态 upstream 更现代,且避免雪花 ID `worker-id` 冲突。

### Module 2 — 动静分离
- [nginx.conf](nginx.conf) 新增 `location /static/` 块:`root /usr/share/nginx/html`、`expires 7d`、`Cache-Control: public, max-age=604800, immutable`、`access_log off`、`try_files $uri =404`。
- 拆分静态资源:
    - 新增 [src/main/resources/static/static/css/style.css](src/main/resources/static/static/css/style.css)
    - 新增 [src/main/resources/static/static/js/app.js](src/main/resources/static/static/js/app.js)
- 精简 [index.html](src/main/resources/static/index.html):移除内联 `<style>`/`<script>`,改用 `<link rel="stylesheet" href="/static/css/style.css">` 与 `<script src="/static/js/app.js"></script>`。

### Module 3 — 分布式缓存(主要改造)
- **新增布隆过滤器**:[BloomFilterService.java](src/main/java/com/nexusmart/seckill/service/BloomFilterService.java)
    - 基于 Guava `BloomFilter<Long>`,启动时灌入 `goods.id` 与 `seckill_goods.id` 全量。
    - FPP 默认 0.01(1% 误判率),预期容量 100k(可在 `app.bloom.*` 调)。
    - `AtomicReference` 持有快照,`rebuild()` 整体替换,读端无半成品风险。
    - 暴露 `addGoodsId/addSeckillId`,后续后台新增商品可增量写入。
    - 未初始化时 `mightContain*` 保守返回 `true`,防止冷启动期误拦截。
- **新增 Mapper 方法**:[GoodsMapper.selectAllIds()](src/main/java/com/nexusmart/seckill/mapper/GoodsMapper.java)、[SeckillGoodsMapper.selectAllIds()](src/main/java/com/nexusmart/seckill/mapper/SeckillGoodsMapper.java),仅取 ID 列,启动期低开销。
- **Caffeine L1 缓存**:重写 [RedisCacheUtil.java](src/main/java/com/nexusmart/seckill/util/RedisCacheUtil.java)
    - 形成 L1(Caffeine 进程内) → L2(Redis) → DB 三级访问。
    - L1 默认 `maximumSize=10000`、`expireAfterWrite=60s`(故意远小于 Redis TTL,避免本地长期陈旧)。
    - `getWithMutex` 改造:先查 L1,L1 未命中查 L2 命中后回填 L1;互斥锁回源 DB 写入时同步双写 L1+L2。
    - 新增 `evict(key)` 主动失效双层缓存,便于商品更新等场景。
- **Redis 不可用降级**:`safeRedisGet` / `setWithRandomTTL` / `setEmpty` / `tryLock` 全部捕获 `DataAccessException`,降级为"只读 L1 + 回源 DB"或"无锁回源 DB"(单实例风险可控)。配合 Sentinel 熔断后续可平滑接入。
- **空对象 TTL 60s → 300s**:符合 prompt 防穿透要求,改为可配置 `app.cache.empty-ttl-seconds`。
- **GoodsService 入口拦截**:[GoodsService.getGoodsDetail()](src/main/java/com/nexusmart/seckill/service/GoodsService.java) 在查缓存前先 `bloomFilterService.mightContainGoodsId(goodsId)`,过滤器拒绝 → 直接返回 null,完全不下穿到 L1/L2/DB。
- **启动预热**:[SeckillInitRunner.run()](src/main/java/com/nexusmart/seckill/config/SeckillInitRunner.java) 增加 `bloomFilterService.rebuild()`,放在库存/详情预热之前,保证业务首次查询即享受穿透防护。

### Module 4 — 读写分离
- 保留现有 AOP + `AbstractRoutingDataSource` 方案,功能上与 prompt 要求等价(后续切 ShardingSphere-JDBC 留待 D 类合并 Module 7 时一并处理)。

### Module 6 — Kafka 秒杀下单
- 无功能性问题,保留现有 Lua 预扣 + 雪花 ID + 异步落库 + DLT + 定时补偿 链路。

### Module 8 — 支付一致性
- [PaymentResultConsumer.onPaymentResult()](src/main/java/com/nexusmart/seckill/service/PaymentResultConsumer.java) 修复 `targetStatus` 未使用警告:把变量定义下移到"条件更新未命中、重读最新订单状态"分支,语义和位置一致,消除 IDE 警告。

### 跨模块 — 配置语义与可观测性
- 修复 [SeckillController.shouldFailByRatio()](src/main/java/com/nexusmart/seckill/controller/SeckillController.java) **failureRate 单位歧义**:
    - 原实现:`Math.min(100, ...)` + `nextDouble(100) < normalized`,即把属性当 0–100 百分比。
    - `failure-rate` 这一命名通常被理解为 0–1 比例(如 `0.3 = 30%`),与代码不一致。
    - 改为夹紧到 `[0,1]` + `nextDouble() < normalized`,并在字段注释 / yml 注释 / Nacos 注释三处统一标注语义。
- 新增 [application.yml](src/main/resources/application.yml) 配置块:
    - `app.cache.empty-ttl-seconds` / `lock-ttl-seconds` / `local.maximum-size` / `local.expire-seconds`
    - `app.bloom.expected-insertions` / `fpp`
- [nacos/configs/nexusmart-seckill-dev.yml](nacos/configs/nexusmart-seckill-dev.yml) 补充 `failure-rate` 单位说明。

### 依赖
- [pom.xml](pom.xml) 新增:
    - `com.github.ben-manes.caffeine:caffeine:3.1.8` — L1 本地缓存
    - `com.google.guava:guava:33.3.1-jre` — BloomFilter

### 验证清单(供本地手动复核)
1. `mvn clean compile` — 47 个源文件全部通过 ✅
2. `docker-compose up -d`,访问 `http://localhost/api/goods/detail?goodsId=99999999` — 期望直接 `商品不存在`,且 MySQL slow log 中**无 SELECT** 命中(布隆过滤器前置拦截生效)
3. 浏览器开 DevTools 看 `index.html`,确认 `/static/css/style.css` 和 `/static/js/app.js` 走 200(首次)→ 304 / `Cache-Control: max-age=604800`(再次)
4. JMeter 静态文件 vs 动态接口压测,响应时间差距应在 5–20 倍量级
5. 热点商品并发查询观察日志:首次查询触发 DB 回源 + 双写 L1+L2,后续应仅命中 L1(`safeRedisGet` 不被调用)
