package com.nexusmart.seckill.mapper;

import com.nexusmart.seckill.entity.Goods;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 普通商品 Mapper 接口
 */
@Mapper
public interface GoodsMapper {

    /** 根据 ID 查询商品 */
    @Select("SELECT * FROM goods WHERE id = #{id}")
    Goods selectById(Long id);

    /** 查询全部商品列表 */
    @Select("SELECT * FROM goods")
    List<Goods> selectAll();

    /** 根据商家 ID 查询其名下所有商品 */
    @Select("SELECT * FROM goods WHERE merchant_id = #{merchantId}")
    List<Goods> selectByMerchantId(Long merchantId);

    /** 新增商品（自增 ID 会通过 useGeneratedKeys 回填到对象） */
    @Insert("INSERT INTO goods(merchant_id, goods_name, goods_img, goods_price, goods_stock) " +
            "VALUES(#{merchantId}, #{goodsName}, #{goodsImg}, #{goodsPrice}, #{goodsStock})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Goods goods);

    /** 更新商品信息 */
    @Update("UPDATE goods SET goods_name = #{goodsName}, goods_img = #{goodsImg}, " +
            "goods_price = #{goodsPrice}, goods_stock = #{goodsStock} WHERE id = #{id}")
    int update(Goods goods);
}
