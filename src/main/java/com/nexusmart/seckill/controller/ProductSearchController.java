package com.nexusmart.seckill.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.nexusmart.seckill.common.Result;
import com.nexusmart.seckill.search.ProductSearchResult;
import com.nexusmart.seckill.search.ProductSearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
public class ProductSearchController {

    @Autowired
    private ProductSearchService productSearchService;

    /**
     * 多字段商品搜索（multi_match）。
     * <p>使用 Sentinel 资源 productSearch：
     * - 异常比例熔断（外部 ES 抖动时触发）
     * - 触发限流时 blockHandler 返回 HTTP 429（prompt 明确要求）
     * - 触发熔断时 fallback 返回降级响应（200 OK + 空结果）
     */
    @GetMapping("/search")
    @SentinelResource(value = "productSearch",
            fallback = "searchFallback",
            blockHandler = "searchBlocked")
    public ResponseEntity<Result<ProductSearchResult>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(Result.success(productSearchService.search(keyword, page, size)));
    }

    /**
     * Sentinel 业务异常降级：ES 不可用时返回空结果而不是抛错（200 OK，软降级）。
     */
    public ResponseEntity<Result<ProductSearchResult>> searchFallback(String keyword, int page, int size, Throwable t) {
        return ResponseEntity.ok(Result.error("搜索服务暂不可用，已降级。原因：" + t.getMessage()));
    }

    /**
     * Sentinel 限流 block handler：返回 HTTP 429 Too Many Requests。
     */
    public ResponseEntity<Result<ProductSearchResult>> searchBlocked(String keyword, int page, int size, BlockException be) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(Result.error("当前搜索请求过多，请稍后重试"));
    }
}
