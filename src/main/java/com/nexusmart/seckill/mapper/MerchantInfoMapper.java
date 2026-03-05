package com.nexusmart.seckill.mapper;

import com.nexusmart.seckill.entity.MerchantInfo;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 商家信息 Mapper 接口
 */
@Mapper
public interface MerchantInfoMapper {

    /** 根据 ID 查询商家 */
    @Select("SELECT * FROM merchant_info WHERE id = #{id}")
    MerchantInfo selectById(Long id);

    /** 查询所有正常营业的商家 */
    @Select("SELECT * FROM merchant_info WHERE status = 1")
    List<MerchantInfo> selectAllActive();

    /** 新增商家 */
    @Insert("INSERT INTO merchant_info(shop_name, status) VALUES(#{shopName}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(MerchantInfo merchantInfo);

    /** 更新商家营业状态 */
    @Update("UPDATE merchant_info SET status = #{status} WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);
}
