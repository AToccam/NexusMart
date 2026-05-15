package com.nexusmart.seckill.sharding;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 分库分表订单 Mapper：所有 SQL 中表名统一写逻辑表名 order_archive，
 * ShardingSphere 会在执行期自动改写为物理表名（order_archive_0..3）并路由到 ds0/ds1。
 *
 * <p>注意：不在此接口标注 {@code @Mapper}，由 {@code ShardingDataSourceConfig}
 * 仅在 profile=sharding 时通过 {@code @MapperScan} 注册到独立 sqlSessionFactory，
 * 避免默认主从数据源误绑定（默认库下没有逻辑表 order_archive，会启动报错）。
 */
public interface OrderArchiveMapper {

    @Insert("INSERT INTO order_archive(order_id, user_id, goods_id, order_price, status, create_time) " +
            "VALUES(#{orderId}, #{userId}, #{goodsId}, #{orderPrice}, #{status}, NOW())")
    int insert(OrderArchive order);

    @Select("SELECT order_id, user_id, goods_id, order_price, status, create_time " +
            "FROM order_archive WHERE user_id = #{userId}")
    List<OrderArchive> selectByUserId(@Param("userId") Long userId);

    @Select("SELECT order_id, user_id, goods_id, order_price, status, create_time " +
            "FROM order_archive WHERE order_id = #{orderId}")
    OrderArchive selectByOrderId(@Param("orderId") Long orderId);
}
