-- 给 goods 表新增 description 列，用于 Elasticsearch multi_match 多字段检索。
-- 旧库通过执行本迁移获得新列；新库由 docker/mysql/init/001_schema.sql 直接创建。
ALTER TABLE goods
    ADD COLUMN description VARCHAR(1024) DEFAULT NULL COMMENT '商品描述，参与 ES multi_match 全文检索';

UPDATE goods
SET description = CONCAT(goods_name, ' - 暂无描述')
WHERE description IS NULL;
