package com.nexusmart.seckill.mapper;

import com.nexusmart.seckill.entity.OrderInfo;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 完整订单 Mapper 接口
 */
@Mapper
public interface OrderInfoMapper {

    /** 根据 ID 查询订单 */
    @Select("SELECT * FROM order_info WHERE id = #{id}")
    OrderInfo selectById(Long id);

    /** 根据用户 ID 查询该用户的所有订单 */
    @Select("SELECT * FROM order_info WHERE user_id = #{userId}")
    List<OrderInfo> selectByUserId(Long userId);

    /** 新增订单（自增 ID 回填到对象） */
    @Insert("INSERT INTO order_info(user_id, merchant_id, goods_id, goods_name, order_price, status) " +
            "VALUES(#{userId}, #{merchantId}, #{goodsId}, #{goodsName}, #{orderPrice}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(OrderInfo orderInfo);

    /**
     * 更新订单状态（0-未支付  1-已支付  2-已发货  3-已退款）
     */
    @Update("UPDATE order_info SET status = #{status} WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);
}
