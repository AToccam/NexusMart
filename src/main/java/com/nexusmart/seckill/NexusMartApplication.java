package com.nexusmart.seckill;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * NexusMart 秒杀系统核心启动类
 */
@SpringBootApplication
public class NexusMartApplication {

    public static void main(String[] args) {
        SpringApplication.run(NexusMartApplication.class, args);
        System.out.println("========== NexusMart Core Engine Started Successfully! ==========");
    }
}