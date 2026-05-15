package com.nexusmart.seckill.config;

import com.nexusmart.seckill.search.ProductSearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 启动期把 MySQL goods 全量灌入 Elasticsearch。
 *
 * <p>失败时仅打印日志，不影响主流程（ES 是检索增强组件，非核心交易依赖）。
 */
@Component
@Order(50)
public class ElasticsearchInitRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchInitRunner.class);

    @Autowired
    private ProductSearchService productSearchService;

    @Override
    public void run(ApplicationArguments args) {
        try {
            int n = productSearchService.reindexAll();
            log.info("[ES] startup reindex success, docs={}", n);
        } catch (Exception e) {
            log.warn("[ES] startup reindex skipped, reason={}", e.getMessage());
        }
    }
}
