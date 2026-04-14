package com.nexusmart.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

@Configuration
public class GatewayKeyResolverConfig {

    @Bean("ipOrUserKeyResolver")
    public KeyResolver ipOrUserKeyResolver() {
        return exchange -> {
            String userId = exchange.getRequest().getQueryParams().getFirst("userId");
            if (StringUtils.hasText(userId)) {
                return Mono.just("uid:" + userId);
            }

            InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
            String remoteIp = "unknown";
            if (remoteAddress != null && remoteAddress.getAddress() != null) {
                remoteIp = remoteAddress.getAddress().getHostAddress();
            }
            return Mono.just("ip:" + remoteIp);
        };
    }
}
