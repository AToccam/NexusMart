package com.nexusmart.seckill.mapper;

import com.nexusmart.seckill.entity.SeckillGoods;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 秒杀商品 Mapper 接口（核心热数据操作）
 */
@Mapper
public interface SeckillGoodsMapper {

    /** 根据 ID 查询秒杀商品 */
    @Select("SELECT * FROM seckill_goods WHERE id = #{id}")
    SeckillGoods selectById(Long id);

    /** 根据普通商品 ID 查询对应的秒杀配置 */
    @Select("SELECT * FROM seckill_goods WHERE goods_id = #{goodsId}")
    SeckillGoods selectByGoodsId(Long goodsId);

    /** 查询当前正在进行的所有秒杀商品（当前时间在 start_time 和 end_time 之间） */
    @Select("SELECT * FROM seckill_goods WHERE start_time <= NOW() AND end_time >= NOW()")
    List<SeckillGoods> selectOngoing();

    /** 新增秒杀商品配置 */
    @Insert("INSERT INTO seckill_goods(goods_id, seckill_price, stock_count, start_time, end_time, version) " +
            "VALUES(#{goodsId}, #{seckillPrice}, #{stockCount}, #{startTime}, #{endTime}, #{version})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(SeckillGoods seckillGoods);

    /**
     * 乐观锁扣减秒杀库存（核心中的核心！）
     * 1. stock_count > 0   → 防超卖
     * 2. version = #{version} → 乐观锁 CAS，并发安全
     * 3. version = version + 1 → 版本号递增
     *
     * @return 受影响行数：1 = 扣减成功，0 = 库存不足或版本冲突（需要重试或拒绝）
     */
    @Update("UPDATE seckill_goods SET stock_count = stock_count - 1, version = version + 1 " +
            "WHERE id = #{id} AND stock_count > 0 AND version = #{version}")
    int decreaseStockByOptimisticLock(@Param("id") Long id, @Param("version") Integer version);
}
