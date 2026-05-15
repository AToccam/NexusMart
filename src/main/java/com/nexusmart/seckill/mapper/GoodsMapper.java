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

    /** 仅查询全部商品 ID，用于布隆过滤器初始化 */
    @Select("SELECT id FROM goods")
    List<Long> selectAllIds();

    /** 根据商家 ID 查询其名下所有商品 */
    @Select("SELECT * FROM goods WHERE merchant_id = #{merchantId}")
    List<Goods> selectByMerchantId(Long merchantId);

    /** 新增商品（自增 ID 会通过 useGeneratedKeys 回填到对象） */
    @Insert("INSERT INTO goods(merchant_id, goods_name, goods_img, goods_price, goods_stock, description) " +
            "VALUES(#{merchantId}, #{goodsName}, #{goodsImg}, #{goodsPrice}, #{goodsStock}, #{description})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Goods goods);

    /** 更新商品信息 */
    @Update("UPDATE goods SET goods_name = #{goodsName}, goods_img = #{goodsImg}, " +
            "goods_price = #{goodsPrice}, goods_stock = #{goodsStock}, description = #{description} " +
            "WHERE id = #{id}")
    int update(Goods goods);

    /** 根据 ID 物理删除商品（演示用：实际生产建议加 `deleted` 软删字段） */
    @Delete("DELETE FROM goods WHERE id = #{id}")
    int deleteById(Long id);
}
