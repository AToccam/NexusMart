package com.nexusmart.gateway.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class FallbackController {

    @GetMapping("/fallback/seckill")
    public Mono<ResponseEntity<Map<String, Object>>> seckillFallback(ServerHttpRequest request) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("code", 503);
        response.put("message", "网关触发服务降级，请稍后重试");
        response.put("path", request.getURI().getPath());
        response.put("timestamp", Instant.now().toString());
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response));
    }
}
