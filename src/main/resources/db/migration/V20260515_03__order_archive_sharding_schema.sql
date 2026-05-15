-- ============================================================
-- Module 7: 分库分表归档订单
-- 两个物理库（ds0 / ds1），每库 4 张物理表（order_archive_0..3）
-- 由 ShardingSphere-JDBC 在执行期改写逻辑表 order_archive → 物理表
-- 主键 order_id 使用雪花算法生成，禁止依赖 auto_increment
-- ============================================================

CREATE DATABASE IF NOT EXISTS nexusmart_ds0 DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS nexusmart_ds1 DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- ds0
USE nexusmart_ds0;
CREATE TABLE IF NOT EXISTS order_archive_0 (
    order_id    BIGINT PRIMARY KEY,
    user_id     BIGINT NOT NULL,
    goods_id    BIGINT NOT NULL,
    order_price DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    status      TINYINT NOT NULL DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    KEY idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS order_archive_1 LIKE order_archive_0;
CREATE TABLE IF NOT EXISTS order_archive_2 LIKE order_archive_0;
CREATE TABLE IF NOT EXISTS order_archive_3 LIKE order_archive_0;

-- ds1
USE nexusmart_ds1;
CREATE TABLE IF NOT EXISTS order_archive_0 (
    order_id    BIGINT PRIMARY KEY,
    user_id     BIGINT NOT NULL,
    goods_id    BIGINT NOT NULL,
    order_price DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    status      TINYINT NOT NULL DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    KEY idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS order_archive_1 LIKE order_archive_0;
CREATE TABLE IF NOT EXISTS order_archive_2 LIKE order_archive_0;
CREATE TABLE IF NOT EXISTS order_archive_3 LIKE order_archive_0;
