package com.nexusmart.seckill.mapper;

import com.nexusmart.seckill.entity.UserInfo;
import org.apache.ibatis.annotations.*;

/**
 * 顾客信息 Mapper 接口
 */
@Mapper
public interface UserInfoMapper {

    /** 根据 ID 查询用户 */
    @Select("SELECT * FROM user_info WHERE id = #{id}")
    UserInfo selectById(Long id);

    /** 根据昵称查询用户（登录场景） */
    @Select("SELECT * FROM user_info WHERE nickname = #{nickname}")
    UserInfo selectByNickname(String nickname);

    /** 新增用户（注册） */
    @Insert("INSERT INTO user_info(nickname, password, salt) " +
            "VALUES(#{nickname}, #{password}, #{salt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(UserInfo userInfo);
}
