package com.nexusmart.seckill.mapper;

import com.nexusmart.seckill.entity.SeckillOrder;
import org.apache.ibatis.annotations.*;

/**
 * 秒杀防重订单 Mapper 接口
 * 依赖 UNIQUE KEY (user_id, goods_id) 做数据库层终极兜底
 */
@Mapper
public interface SeckillOrderMapper {

    /**
     * 查询某用户是否已秒杀过该商品（防重复下单查询）
     * 返回 null = 未购买，非 null = 已经买过
     */
    @Select("SELECT * FROM seckill_order WHERE user_id = #{userId} AND goods_id = #{goodsId}")
    SeckillOrder selectByUserIdAndGoodsId(@Param("userId") Long userId,
                                          @Param("goodsId") Long goodsId);

    /**
     * 新增秒杀订单记录
     * 如果同一 (user_id, goods_id) 重复插入，会触发唯一索引冲突 → 数据库直接拒绝
     */
    @Insert("INSERT INTO seckill_order(user_id, order_id, goods_id) " +
            "VALUES(#{userId}, #{orderId}, #{goodsId})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(SeckillOrder seckillOrder);
}
