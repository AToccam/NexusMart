package com.nexusmart.seckill.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.nexusmart.seckill.common.Result;
import com.nexusmart.seckill.entity.Goods;
import com.nexusmart.seckill.service.GoodsService;
import com.nexusmart.seckill.vo.SeckillGoodsVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/goods")
public class GoodsController {

    @Autowired
    private GoodsService goodsService;

    /** 获取当前正在进行中的秒杀商品列表 */
    @GetMapping("/seckill/list")
    public Result<List<SeckillGoodsVo>> listSeckillGoods() {
        return Result.success(goodsService.listSeckillGoods());
    }

    /**
     * 获取商品详情（带缓存防护 + Sentinel QPS 限流 / 异常熔断）。
     *
     * <p>Sentinel 资源 getProductDetail：
     * - 限流阈值由 {@link com.nexusmart.seckill.config.SentinelRuleConfig} 默认埋入（QPS=100）。
     * - 触发限流 → blockHandler 返回 HTTP 429 Too Many Requests（prompt 明确要求）。
     * - 业务抛异常或熔断打开 → fallback 返回兜底（兜底返回空对象，避免上游 5xx）。
     */
    @GetMapping("/detail")
    @SentinelResource(value = "getProductDetail",
            blockHandler = "getProductDetailBlocked",
            fallback = "getProductDetailFallback")
    public ResponseEntity<Result<SeckillGoodsVo>> getGoodsDetail(@RequestParam Long goodsId) {
        SeckillGoodsVo detail = goodsService.getGoodsDetail(goodsId);
        if (detail == null) {
            return ResponseEntity.ok(Result.error("商品不存在"));
        }
        return ResponseEntity.ok(Result.success(detail));
    }

    /**
     * 限流 / 熔断 block handler：注意签名末尾必须是 BlockException，返回类型必须与原方法一致。
     * <p>触发限流时返回 HTTP 429（Too Many Requests），与 prompt Module 10 要求一致。
     */
    public ResponseEntity<Result<SeckillGoodsVo>> getProductDetailBlocked(Long goodsId, BlockException be) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(Result.error("商品详情访问繁忙，请稍后重试"));
    }

    /** 业务异常 fallback：保护下游，返回友好提示（200 OK，软降级）。 */
    public ResponseEntity<Result<SeckillGoodsVo>> getProductDetailFallback(Long goodsId, Throwable t) {
        return ResponseEntity.ok(Result.error("商品详情暂不可用，已降级"));
    }

    // ────────────────────────────────────────────────────────────────────────
    // 商品写入接口（演示用，实际生产应加管理员鉴权 + 防重放）
    // 调用链：Controller → GoodsService（@WriteDataSource 主库）→ MySQL + Elasticsearch 双写
    // ES 写失败不影响主交易，由 GoodsService 内部捕获并记日志。
    // ────────────────────────────────────────────────────────────────────────

    /** 新增商品：写主库 + 同步入 ES 索引 + 注册到布隆过滤器。 */
    @PostMapping
    public Result<Long> createGoods(@RequestBody Goods goods) {
        Long id = goodsService.createGoods(goods);
        return Result.success(id);
    }

    /** 更新商品：写主库 + ES upsert + 失效详情缓存。 */
    @PutMapping("/{id}")
    public Result<Boolean> updateGoods(@PathVariable Long id, @RequestBody Goods goods) {
        goods.setId(id);
        return Result.success(goodsService.updateGoods(goods));
    }

    /** 下架商品：删主库 + 删 ES 文档 + 失效详情缓存。 */
    @DeleteMapping("/{id}")
    public Result<Boolean> removeGoods(@PathVariable Long id) {
        return Result.success(goodsService.removeGoods(id));
    }
}
