package com.nexusmart.seckill.entity;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 顾客信息实体类，对应数据库 user_info 表
 */
@Data
public class UserInfo {

    /** 用户 ID（自增主键） */
    private Long id;

    /** 昵称 */
    private String nickname;

    /** MD5 加密后的密码（通常还会加盐） */
    private String password;

    /** 密码盐值 */
    private String salt;

    /** 注册时间 */
    private LocalDateTime registerTime;
}
