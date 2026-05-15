## 变更日志 — 2026-05-15（B 类:补齐 Module 5/7/10 实现）

> 本轮聚焦 `docs/prompt.md` 中尚未落地的三大模块: Elasticsearch 商品搜索、
> ShardingSphere-JDBC 分库分表、Sentinel 流量治理。所有改动通过
> `mvn -DskipTests clean compile`(58 源文件)。

### Module 5 — Elasticsearch 商品搜索(新增)

- **docker-compose.yml**: 新增 `elasticsearch` 服务(8.13.4 单节点, `xpack.security.enabled=false`,
  `discovery.type=single-node`, 健康检查走 `_cluster/health`),`app` 增加 `ES_URIS` 环境变量并
  `depends_on: elasticsearch.service_healthy`。
- **application.yml**: 新增 `spring.data.elasticsearch.uris=${ES_URIS:http://127.0.0.1:9200}`。
- **新增 `com.nexusmart.seckill.search` 包**:
    - `ProductDocument` — `@Document(indexName="nexusmart-product")`,字段 `name`/`description`
      使用 `standard` 分词器(生产环境可换 `ik_smart`,需另装 IK 插件)。
    - `ProductSearchRepository extends ElasticsearchRepository`。
    - `ProductSearchResult` — 分页结果包装。
    - `ProductSearchService` — `reindexAll()` 启动期全量灌入 + `upsert/delete` 增量入口 +
      `search(keyword, page, size)` 多字段 `multi_match`(`name^3, description`)+ 分页。
- **新增 `ProductSearchController.search()`** — `GET /api/products/search?keyword=&page=&size=`,
  同时挂载 `@SentinelResource(value="productSearch")` 资源 + `searchFallback` / `searchBlocked`。
- **新增 `ElasticsearchInitRunner`** — `ApplicationRunner @Order(50)`,启动期全量同步,
  失败仅日志,不阻塞主交易链路。
- **pom.xml**: 新增 `spring-boot-starter-data-elasticsearch`(版本由 Spring Boot parent 管理)。

### Module 7 — ShardingSphere-JDBC 分库分表(新增, profile-gated)

- **新增 `com.nexusmart.seckill.sharding` 包**:
    - `OrderArchive` — 分库分表归档订单 POJO(`order_id` 雪花生成, `user_id` 分库键)。
    - `OrderArchiveMapper` — *不*标注 `@Mapper`,避免被默认主从数据源扫描;
      由 `ShardingDataSourceConfig` 通过 `@MapperScan(sqlSessionFactoryRef="shardingSqlSessionFactory")`
      在 `profile=sharding` 时注册。
    - `ShardingDataSourceConfig` — `@Profile("sharding")`,从 `classpath:sharding.yaml` 启动
      `YamlShardingSphereDataSourceFactory`,产出独立 `shardingDataSource` +
      `shardingSqlSessionFactory` + `shardingSqlSessionTemplate`。
    - `OrderArchiveController` — `@Profile("sharding")`, 仅在分片 profile 下暴露
      `POST /api/orders/archive` + `GET .../by-user|by-order`,演示路由。
- **`src/main/resources/sharding.yaml`**: 完整规则
    - 数据源 ds0/ds1(同 MySQL 实例的两个 schema, 演示便利)
    - 分库: `user_id % 2 → ds${0..1}`
    - 分表: `order_id % 4 → order_archive_${0..3}`
    - 主键: `SNOWFLAKE`(worker-id 通过 `${SHARDING_WORKER_ID:1}` 注入,多实例需区分)
    - `sql-show: true` 方便观察改写后的真实表名
- **DDL**: `src/main/resources/db/migration/V20260515_03__order_archive_sharding_schema.sql`
  建 `nexusmart_ds0` / `nexusmart_ds1` 两个 schema,每库 4 张 `order_archive_*` 物理表。
- **依赖**: `org.apache.shardingsphere:shardingsphere-jdbc-core:5.4.1`(`<optional>true</optional>` +
  排除 `snakeyaml`,避免与 Spring Boot parent 管理的 snakeyaml 版本冲突)。

> **启用方式**: `SPRING_PROFILES_ACTIVE=dev,sharding`。未启用时 `OrderArchiveMapper` 不会被
> 扫描,启动期不会因默认库缺少 `order_archive` 而失败。

### Module 10 — Sentinel 流量治理(新增)

- **pom.xml**: 新增 `com.alibaba.cloud:spring-cloud-starter-alibaba-sentinel`。
- **application.yml**: 新增 `spring.cloud.sentinel.{eager, transport, filter}` 配置块,
  以及 `app.sentinel.flow.*` / `app.sentinel.degrade.*` 阈值参数。
- **新增 `SentinelRuleConfig`** — `@PostConstruct` 装载默认规则:
    - 资源 `getProductDetail`: QPS=100 限流
    - 资源 `productSearch`: QPS=50 限流 + 异常比例 50%/10s 窗口熔断,半开期 1 次探活
- **`GoodsController.getGoodsDetail`**: 加 `@SentinelResource("getProductDetail")` +
  `getProductDetailBlocked` / `getProductDetailFallback`。
- **`ProductSearchController.search`**: 加 `@SentinelResource("productSearch")` +
  `searchBlocked` / `searchFallback`(ES 抖动时返回兜底,而非 5xx)。

### 配置与依赖

- **pom.xml** 新增依赖:
    - `spring-boot-starter-data-elasticsearch`
    - `spring-cloud-starter-alibaba-sentinel`
    - `shardingsphere-jdbc-core:5.4.1` (optional)

### 验证清单

1. `mvn -DskipTests clean compile` — 58 个源文件全部通过 ✅
2. `docker compose up -d --build` 后:
    - `curl http://127.0.0.1:9200/_cluster/health` 应 yellow/green
    - `curl http://127.0.0.1:8081/api/products/search?keyword=demo` 应返回 `items` 列表
3. **限流验证** — JMeter 200 并发打 `/api/goods/detail?goodsId=1`,
   QPS 超过 100 时部分请求落入 `getProductDetailBlocked` 返回 "商品详情访问繁忙"。
4. **熔断验证** — 临时关停 ES 容器后连续打 `/api/products/search?keyword=x`,
   异常比例 > 50% 后窗口内进入熔断,返回 "搜索服务暂不可用,已降级"。
5. **分库分表验证(可选 profile)** — `SPRING_PROFILES_ACTIVE=dev,sharding` 启动后:
    - `POST /api/orders/archive?userId=1&...` → 日志显示 `ds1.order_archive_*`(userId 1 % 2 = 1)
    - `POST /api/orders/archive?userId=2&...` → 日志显示 `ds0.order_archive_*`(userId 2 % 2 = 0)

### 已知局限与改进方向

- ES 中文分词使用 `standard`,生产环境需安装 IK 插件并把 `analyzer` 切到 `ik_smart`。
- 当前 MySQL → ES 双写策略 A,可演进到方案 B(Canal 订阅 binlog)。
- Sentinel 规则代码硬编码,生产建议接 Sentinel Dashboard 或 Nacos 数据源动态下发。
- ShardingSphere 数据源未与现有读写分离 AOP 合并,改造期长,作为独立 profile 演示。

---

## 增补轮次 — 基于 AUDIT-2026-05-15 的 P0/P1 修复

> 对照 `docs/AUDIT-2026-05-15.md` 罗列的 P0/P1 项落地修复。`mvn -DskipTests clean compile` 58 源文件通过 ✅。

### P0-2.4 — Readme 路线图与实现状态对齐

- `Readme.md` 路线图:Module 5/7/10 由 `[ ]` 改为 `[x]`,并在每条后补一句限制说明
  (Module 5 标注「`standard` 分词器,生产化需 IK」,Module 7 标注「`order_archive` 演示 profile」,
   Module 10 标注「规则硬编码,生产化建议接 Dashboard/Nacos」)。
- `Readme.md` 核心特性表中「流量治理」行补上 Sentinel(`@SentinelResource` + QPS 限流 + 异常比例熔断),
  并新增「全文搜索」一行,避免读者被旧描述误导。

### P0-2.3 — Sentinel `blockHandler` 返回 HTTP 429

- `GoodsController.getGoodsDetail` 与 `ProductSearchController.search` 返回类型改为
  `ResponseEntity<Result<...>>`。
- `getProductDetailBlocked` / `searchBlocked` 返回 `ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)`,
  与 prompt Module 10「触发限流时返回 HTTP 429」要求一致。
- `fallback` 仍返回 200 OK + 兜底文案(异常熔断属于「软降级」,不是限流,沿用 prompt 兜底语义)。

### P0-2.2 — `description` 列正式落地(选项 A)

- DDL:
    - `docker/mysql/init/001_schema.sql` 中 `goods` 表追加 `description VARCHAR(1024)` 列,
      并把演示数据 `INSERT` 也带上 description,`ON DUPLICATE KEY UPDATE` 同步更新。
    - 新增迁移 `src/main/resources/db/migration/V20260515_04__goods_add_description.sql`,
      给历史库 `ALTER TABLE goods ADD COLUMN description ...`,并把 NULL 行回填为
      `CONCAT(goods_name, ' - 暂无描述')`,避免旧数据进 ES 后 `description` 为空导致检索退化。
- 实体 / Mapper:
    - `Goods` 实体新增 `description` 字段。
    - `GoodsMapper.insert/update` 同步加入 `description` 列,并新增 `deleteById(Long)`
      供「商品下架」场景调用。
- ES 索引:
    - `ProductSearchService.toDocument` 改为读取 `goods.getDescription()`,
      `multi_match(name^3, description)` 真正生效,不再退化成单字段检索。

### P0-2.1 — MySQL → ES 双写接入业务写路径

- `GoodsService` 新增三个写方法(`createGoods` / `updateGoods` / `removeGoods`),均标注
  `@WriteDataSource` 显式覆盖类级 `@ReadOnlyDataSource`,强制走主库。
- 双写策略:
    - **DB 为权威数据源**,写 DB 成功即视为业务成功。
    - ES 写入失败只记 `ERROR` 日志,不抛出,不影响主交易。
    - 二级影响:`createGoods` 同步把新 ID `put` 到 BloomFilter,
      `updateGoods` / `removeGoods` 同步 `evict` 详情缓存 + 秒杀列表缓存,避免脏读。
- 暴露接口:`GoodsController` 新增 `POST /api/goods`、`PUT /api/goods/{id}`、
  `DELETE /api/goods/{id}`,使双写链路对验证脚本和 JMeter 可触达。
- 启动期 `ElasticsearchInitRunner` 仍负责全量重建索引,运行期增量由本轮新增的双写覆盖,
  最终一致性保证未变。

### P1-2.5 — `Dockerfile` 端口环境变量化

- `Dockerfile`:
    - 显式 `ENV SERVER_PORT=8081` 占位,允许 `docker-compose` 通过 environment 段覆写。
    - `HEALTHCHECK` 改为 `http://localhost:${SERVER_PORT:-8081}/actuator/health`,
      多端口实例不再因健康检查端口写死失败。
    - `ENTRYPOINT` 用 `-Dserver.port=${SERVER_PORT:-8081}` 注入 Spring Boot,
      保证 JVM 实际监听端口与环境变量一致。

### 不在本轮处理 / 留作后续

- P1-2.6 Nginx 双实例 + 三套负载算法切换(需要新增 `app-2` 容器、`ID_WORKER=2`、active conf 切换器,
  改动覆盖 docker-compose / nginx.conf / 文档,作为独立 PR 推进)。
- P1-2.7 / 2.8 ShardingSphere 与 AOP 读写分离合并、`order_info` 接入分片(改造期长,
  风险高,暂保持现状,已在 AUDIT 2.7/2.8 中记录)。
- P1-2.9 / 2.10 `outbox` 表与 Kafka 事务消息(下一轮事务一致性专项处理)。
- P1-2.11 限购语义数量化(下一轮配合 Nacos `limit-per-user` 配置项一起改造)。
