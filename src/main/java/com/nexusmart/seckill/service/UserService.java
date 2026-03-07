package com.nexusmart.seckill.service;

import com.nexusmart.seckill.entity.UserInfo;
import com.nexusmart.seckill.mapper.UserInfoMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;

@Service
public class UserService {

    @Autowired
    private UserInfoMapper userInfoMapper;

    private final SecureRandom random = new SecureRandom();

    /**
     * 登录：支持 ID 或用户名 + 密码
     * @param account 用户输入的账号（可能是数字 ID 或用户名）
     */
    public UserInfo login(String account, String password) {
        UserInfo user = null;
        // 如果输入的是纯数字，优先按 ID 查询
        if (account.matches("\\d+")) {
            user = userInfoMapper.selectById(Long.parseLong(account));
        }
        // ID 未找到或输入的不是纯数字，按用户名查询
        if (user == null) {
            user = userInfoMapper.selectByNickname(account);
        }
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        String encrypted = md5(user.getSalt() + password);
        if (!encrypted.equals(user.getPassword())) {
            throw new RuntimeException("密码错误");
        }
        return user;
    }

    /**
     * 注册：随机分配一个未占用的 ID，存入加盐 MD5 密码
     */
    public UserInfo register(String nickname, String password) {
        if (nickname == null || nickname.isBlank()) {
            throw new RuntimeException("用户名不能为空");
        }
        // 用户名不能为纯数字（避免与 ID 混淆）
        if (nickname.matches("\\d+")) {
            throw new RuntimeException("用户名不能为纯数字");
        }
        if (nickname.length() > 50) {
            throw new RuntimeException("用户名长度不能超过50");
        }
        if (password == null || password.length() < 6) {
            throw new RuntimeException("密码长度不能少于6位");
        }
        // 用户名唯一校验
        if (userInfoMapper.selectByNickname(nickname) != null) {
            throw new RuntimeException("该用户名已被占用");
        }

        // 生成随机盐（6位十六进制）
        String salt = HexFormat.of().formatHex(randomBytes(3));

        // 加盐 MD5
        String encrypted = md5(salt + password);

        // 随机分配一个 1000~9999999 范围内未占用的 ID
        Long assignedId = generateUniqueId();

        UserInfo user = new UserInfo();
        user.setId(assignedId);
        user.setNickname(nickname);
        user.setPassword(encrypted);
        user.setSalt(salt);
        userInfoMapper.insertWithId(user);

        // 不返回密码和盐
        user.setPassword(null);
        user.setSalt(null);
        return user;
    }

    private Long generateUniqueId() {
        for (int i = 0; i < 100; i++) {
            long id = 1000 + random.nextLong(9999000); // 1000 ~ 9999999
            if (userInfoMapper.selectById(id) == null) {
                return id;
            }
        }
        throw new RuntimeException("无法分配用户 ID，请稍后重试");
    }

    private byte[] randomBytes(int len) {
        byte[] bytes = new byte[len];
        random.nextBytes(bytes);
        return bytes;
    }

    private String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes());
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
