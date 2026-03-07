package com.nexusmart.seckill.controller;

import com.nexusmart.seckill.common.Result;
import com.nexusmart.seckill.entity.UserInfo;
import com.nexusmart.seckill.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
public class UserController {

    @Autowired
    private UserService userService;

    /** 登录：account 可以是用户 ID 或用户名 */
    @PostMapping("/login")
    public Result<UserInfo> login(@RequestParam String account,
                                  @RequestParam String password) {
        try {
            UserInfo user = userService.login(account, password);
            // 不返回敏感字段
            user.setPassword(null);
            user.setSalt(null);
            return Result.success(user);
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /** 注册 */
    @PostMapping("/register")
    public Result<UserInfo> register(@RequestParam String nickname,
                                     @RequestParam String password,
                                     @RequestParam String confirmPassword) {
        if (!password.equals(confirmPassword)) {
            return Result.error("两次密码输入不一致");
        }
        try {
            UserInfo user = userService.register(nickname, password);
            return Result.success(user);
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }
}
