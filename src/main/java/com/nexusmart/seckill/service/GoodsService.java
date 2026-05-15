package com.nexusmart.seckill.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.nexusmart.seckill.entity.Goods;
import com.nexusmart.seckill.entity.SeckillGoods;
import com.nexusmart.seckill.mapper.GoodsMapper;
import com.nexusmart.seckill.mapper.SeckillGoodsMapper;
import com.nexusmart.seckill.config.datasource.ReadOnlyDataSource;
import com.nexusmart.seckill.config.datasource.WriteDataSource;
import com.nexusmart.seckill.search.ProductSearchService;
import com.nexusmart.seckill.util.RedisCacheUtil;
import com.nexusmart.seckill.vo.SeckillGoodsVo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@ReadOnlyDataSource
public class GoodsService {

    private static final Logger log = LoggerFactory.getLogger(GoodsService.class);

    public static final String SECKILL_LIST_KEY = "seckill:goods:list";
    private static final String SECKILL_LIST_LOCK = "seckill:goods:list";
    public static final String GOODS_DETAIL_KEY_PREFIX = "seckill:goods:detail:";
    private static final String GOODS_DETAIL_LOCK_PREFIX = "seckill:goods:detail:";

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @Autowired
    private GoodsMapper goodsMapper;

    @Autowired
    private SeckillGoodsMapper seckillGoodsMapper;

    @Autowired
    private RedisCacheUtil cacheUtil;

    @Autowired
    private BloomFilterService bloomFilterService;

    /**
     * ES 搜索服务。Spring 启动顺序：{@code GoodsService} 被本类依赖时已能正常注入；
     * 极端情况下（ES 启动期不可达）双写失败仅记日志，**绝不影响主交易**。
     */
    @Autowired
    private ProductSearchService productSearchService;

    /**
     * 查询正在进行的秒杀商品列表（优先走 Redis 缓存，防击穿/穿透/雪崩）
     */
    public List<SeckillGoodsVo> listSeckillGoods() {
        String json = cacheUtil.getWithMutex(
                SECKILL_LIST_KEY,
                SECKILL_LIST_LOCK,
                this::loadSeckillGoodsFromDb,
                600, 120);  // 基础 TTL 600s + 随机 0~120s

        if (json == null) {
            return Collections.emptyList();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<List<SeckillGoodsVo>>() {});
        } catch (JsonProcessingException e) {
            throw new RuntimeException("反序列化秒杀商品列表失败", e);
        }
    }

    /**
     * 查询商品详情（优先走 Redis 缓存，防击穿/穿透/雪崩）
     * <p>
     * 穿透防护链：BloomFilter → L1(Caffeine) → L2(Redis) → DB(空值兜底)
     */
    public SeckillGoodsVo getGoodsDetail(Long goodsId) {
        if (goodsId == null) {
            return null;
        }
        // 布隆过滤器前置拦截：完全不存在的 goodsId 直接返回，不下穿到 Redis/DB
        if (!bloomFilterService.mightContainGoodsId(goodsId)) {
            return null;
        }

        String key = GOODS_DETAIL_KEY_PREFIX + goodsId;
        String lockName = GOODS_DETAIL_LOCK_PREFIX + goodsId;

        String json = cacheUtil.getWithMutex(
                key,
                lockName,
                () -> loadGoodsDetailFromDb(goodsId),
                1800, 300); // 基础 TTL 1800s + 随机 0~300s

        if (json == null) {
            return null;
        }

        try {
            return MAPPER.readValue(json, SeckillGoodsVo.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("反序列化商品详情失败", e);
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // 商品写路径：MySQL ↔ Elasticsearch 双写（策略 A）
    //
    // 约定：
    //   1. **DB 为权威数据源**，写 DB 成功即视为业务成功。
    //   2. ES 写入失败只记 ERROR 日志，不影响主交易；最终一致性由启动期 reindex
    //      与（未来可选）Canal binlog 订阅补齐。
    //   3. 方法名以 create/update/remove 开头，避免被默认路由识别为只读方法；
    //      同时显式标注 @WriteDataSource 覆盖类级 @ReadOnlyDataSource，确保走主库。
    //   4. 商品下架后需同步 deleteById ES 文档与本地详情缓存，否则会出现「DB 已删、
    //      搜索仍可命中」的脏数据。
    // ────────────────────────────────────────────────────────────────────────

    /**
     * 新增商品：写主库 → 异步式 ES 入索引 → 失效相关缓存。
     * @return 自增 ID 回填到入参对象后返回
     */
    @WriteDataSource
    public Long createGoods(Goods goods) {
        if (goods == null) {
            throw new IllegalArgumentException("goods 不能为空");
        }
        goodsMapper.insert(goods);
        syncToEs(goods);
        bloomFilterService.addGoodsId(goods.getId());
        return goods.getId();
    }

    /**
     * 更新商品：写主库 → ES upsert → 失效详情缓存。
     */
    @WriteDataSource
    public boolean updateGoods(Goods goods) {
        if (goods == null || goods.getId() == null) {
            throw new IllegalArgumentException("goods.id 不能为空");
        }
        int affected = goodsMapper.update(goods);
        if (affected > 0) {
            syncToEs(goods);
            invalidateDetailCache(goods.getId());
        }
        return affected > 0;
    }

    /**
     * 下架商品：删主库 → 删 ES 文档 → 失效详情缓存。
     * <p>布隆过滤器不支持删除（false-positive 由空对象 TTL 兜底），这里不动它。
     */
    @WriteDataSource
    public boolean removeGoods(Long goodsId) {
        if (goodsId == null) {
            return false;
        }
        int affected = goodsMapper.deleteById(goodsId);
        if (affected > 0) {
            removeFromEs(goodsId);
            invalidateDetailCache(goodsId);
        }
        return affected > 0;
    }

    private void syncToEs(Goods goods) {
        try {
            productSearchService.upsert(goods);
        } catch (Exception e) {
            log.error("[ES] upsert 商品索引失败，goodsId={}，已忽略（DB 仍为权威）。原因：{}",
                    goods.getId(), e.getMessage(), e);
        }
    }

    private void removeFromEs(Long goodsId) {
        try {
            productSearchService.delete(goodsId);
        } catch (Exception e) {
            log.error("[ES] delete 商品索引失败，goodsId={}，已忽略。原因：{}",
                    goodsId, e.getMessage(), e);
        }
    }

    private void invalidateDetailCache(Long goodsId) {
        try {
            cacheUtil.evict(GOODS_DETAIL_KEY_PREFIX + goodsId);
            cacheUtil.evict(SECKILL_LIST_KEY);
        } catch (Exception e) {
            log.warn("[Cache] 失效商品详情缓存失败，goodsId={}，下次访问会自然过期。原因：{}",
                    goodsId, e.getMessage());
        }
    }

    /**
     * 从 DB 加载秒杀商品列表（仅在缓存未命中时由 getWithMutex 回调）
     */
    private String loadSeckillGoodsFromDb() {
        List<SeckillGoods> seckillList = seckillGoodsMapper.selectOngoing();
        if (seckillList == null || seckillList.isEmpty()) {
            return null;
        }
        List<SeckillGoodsVo> voList = new ArrayList<>();
        for (SeckillGoods sg : seckillList) {
            Goods goods = goodsMapper.selectById(sg.getGoodsId());
            if (goods == null) continue;

            SeckillGoodsVo vo = new SeckillGoodsVo();
            vo.setGoodsId(goods.getId());
            vo.setGoodsName(goods.getGoodsName());
            vo.setGoodsImg(goods.getGoodsImg());
            vo.setGoodsPrice(goods.getGoodsPrice());
            vo.setSeckillId(sg.getId());
            vo.setSeckillPrice(sg.getSeckillPrice());
            vo.setStockCount(sg.getStockCount());
            vo.setStartTime(sg.getStartTime());
            vo.setEndTime(sg.getEndTime());
            voList.add(vo);
        }
        try {
            return MAPPER.writeValueAsString(voList);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("序列化秒杀商品列表失败", e);
        }
    }

    /**
     * 从 DB 加载商品详情（仅在缓存未命中时由 getWithMutex 回调）
     */
    private String loadGoodsDetailFromDb(Long goodsId) {
        Goods goods = goodsMapper.selectById(goodsId);
        if (goods == null) {
            return null;
        }

        SeckillGoods sg = seckillGoodsMapper.selectByGoodsId(goodsId);

        SeckillGoodsVo vo = new SeckillGoodsVo();
        vo.setGoodsId(goods.getId());
        vo.setGoodsName(goods.getGoodsName());
        vo.setGoodsImg(goods.getGoodsImg());
        vo.setGoodsPrice(goods.getGoodsPrice());

        if (sg != null) {
            vo.setSeckillId(sg.getId());
            vo.setSeckillPrice(sg.getSeckillPrice());
            vo.setStockCount(sg.getStockCount());
            vo.setStartTime(sg.getStartTime());
            vo.setEndTime(sg.getEndTime());
        }

        try {
            return MAPPER.writeValueAsString(vo);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("序列化商品详情失败", e);
        }
    }
}
