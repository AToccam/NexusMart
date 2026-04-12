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

    /** 根据业务订单号查询订单 */
    @Select("SELECT * FROM order_info WHERE order_no = #{orderNo}")
    OrderInfo selectByOrderNo(Long orderNo);

    /** 根据用户 ID 查询该用户的所有订单 */
    @Select("SELECT * FROM order_info WHERE user_id = #{userId}")
    List<OrderInfo> selectByUserId(Long userId);

    /** 根据 ID 删除订单（重复冲突补偿使用） */
    @Delete("DELETE FROM order_info WHERE id = #{id}")
    int deleteById(Long id);

    /** 新增订单（自增 ID 回填到对象） */
    @Insert("INSERT INTO order_info(order_no, user_id, merchant_id, goods_id, goods_name, order_price, status) " +
            "VALUES(#{orderNo}, #{userId}, #{merchantId}, #{goodsId}, #{goodsName}, #{orderPrice}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(OrderInfo orderInfo);

    /** 更新订单状态（0-排队中 1-成功 2-失败 3-已支付 4-支付失败） */
    @Update("UPDATE order_info SET status = #{status} WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    /** 按业务订单号更新状态（用于跨服务支付回调） */
    @Update("UPDATE order_info SET status = #{status} WHERE order_no = #{orderNo}")
    int updateStatusByOrderNo(@Param("orderNo") Long orderNo, @Param("status") Integer status);

    /**
     * 支付成功只允许由“下单成功/支付失败”流转到“已支付”，防止非法状态跳转。
     */
    @Update("UPDATE order_info SET status = 3 WHERE order_no = #{orderNo} AND status IN (1, 4)")
    int markPaidIfPayable(@Param("orderNo") Long orderNo);

    /**
     * 支付失败只允许由“下单成功”流转到“支付失败”，避免已支付订单被回写。
     */
    @Update("UPDATE order_info SET status = 4 WHERE order_no = #{orderNo} AND status = 1")
    int markPayFailedIfUnpaid(@Param("orderNo") Long orderNo);
}
