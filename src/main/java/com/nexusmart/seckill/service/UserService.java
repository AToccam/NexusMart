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
     * 用 ID + 密码登录
     */
    public UserInfo login(Long id, String password) {
        UserInfo user = userInfoMapper.selectById(id);
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
            throw new RuntimeException("昵称不能为空");
        }
        if (password == null || password.length() < 6) {
            throw new RuntimeException("密码长度不能少于6位");
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
