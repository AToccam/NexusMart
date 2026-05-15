package com.nexusmart.seckill.config;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.alibaba.csp.sentinel.slots.block.degrade.circuitbreaker.CircuitBreakerStrategy;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Sentinel 规则代码初始化。
 *
 * <p>真实生产建议通过 Nacos / Sentinel Dashboard 下发规则；这里在代码里写死，
 * 保证最小可用、零依赖即可演示限流 + 熔断 + 降级。
 *
 * <p>资源清单：
 * <ul>
 *   <li>getProductDetail：商品详情 QPS 限流（默认 100）</li>
 *   <li>productSearch：ES 搜索熔断（异常比例 > 50%，熔断 10s）+ QPS 限流（默认 50）</li>
 * </ul>
 */
@Configuration
public class SentinelRuleConfig {

    private static final Logger log = LoggerFactory.getLogger(SentinelRuleConfig.class);

    @Value("${app.sentinel.flow.product-detail-qps:100}")
    private int productDetailQps;

    @Value("${app.sentinel.flow.product-search-qps:50}")
    private int productSearchQps;

    @Value("${app.sentinel.degrade.product-search-error-ratio:0.5}")
    private double productSearchErrorRatio;

    @Value("${app.sentinel.degrade.product-search-window-seconds:10}")
    private int productSearchWindowSeconds;

    @PostConstruct
    public void init() {
        loadFlowRules();
        loadDegradeRules();
        log.info("[Sentinel] rules loaded: productDetailQps={}, productSearchQps={}, productSearchErrorRatio={}",
                productDetailQps, productSearchQps, productSearchErrorRatio);
    }

    private void loadFlowRules() {
        List<FlowRule> rules = new ArrayList<>();

        FlowRule detail = new FlowRule("getProductDetail");
        detail.setGrade(RuleConstant.FLOW_GRADE_QPS);
        detail.setCount(productDetailQps);
        rules.add(detail);

        FlowRule search = new FlowRule("productSearch");
        search.setGrade(RuleConstant.FLOW_GRADE_QPS);
        search.setCount(productSearchQps);
        rules.add(search);

        FlowRuleManager.loadRules(rules);
    }

    private void loadDegradeRules() {
        List<DegradeRule> rules = new ArrayList<>();

        DegradeRule rule = new DegradeRule("productSearch");
        rule.setGrade(CircuitBreakerStrategy.ERROR_RATIO.getType());
        rule.setCount(productSearchErrorRatio);
        rule.setStatIntervalMs(productSearchWindowSeconds * 1000);
        rule.setMinRequestAmount(10);
        rule.setTimeWindow(productSearchWindowSeconds);
        rules.add(rule);

        DegradeRuleManager.loadRules(rules);
    }
}
