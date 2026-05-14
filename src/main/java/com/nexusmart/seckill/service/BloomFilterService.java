package com.nexusmart.seckill.service;

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import com.nexusmart.seckill.mapper.GoodsMapper;
import com.nexusmart.seckill.mapper.SeckillGoodsMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 商品 ID 布隆过滤器，用于在查询商品详情前快速拒绝不存在的 ID，
 * 配合 RedisCacheUtil 的空对象缓存共同抵御缓存穿透。
 * <p>
 * 实现要点：
 * 1. 使用 Guava {@link BloomFilter}：进程内、无外部依赖。
 * 2. AtomicReference 持有当前实例，{@link #rebuild()} 时整体替换，
 *    保证读端永远拿到一致的快照（不会读到正在构建中的半成品）。
 * 3. 误判率 0.01（1%），上层依赖 DB 空值缓存兜底，可接受。
 */
@Service
public class BloomFilterService {

    private static final Logger log = LoggerFactory.getLogger(BloomFilterService.class);

    @Value("${app.bloom.expected-insertions:100000}")
    private int expectedInsertions;

    @Value("${app.bloom.fpp:0.01}")
    private double fpp;

    @Autowired
    private GoodsMapper goodsMapper;

    @Autowired
    private SeckillGoodsMapper seckillGoodsMapper;

    private final AtomicReference<BloomFilter<Long>> goodsIdFilter = new AtomicReference<>();
    private final AtomicReference<BloomFilter<Long>> seckillIdFilter = new AtomicReference<>();

    /**
     * 从 DB 重建两套布隆过滤器（商品 ID + 秒杀商品 ID）。
     * 仅在系统启动或后台运营变更商品库时调用。
     */
    public synchronized void rebuild() {
        BloomFilter<Long> nextGoods = BloomFilter.create(Funnels.longFunnel(), expectedInsertions, fpp);
        List<Long> goodsIds = goodsMapper.selectAllIds();
        if (goodsIds != null) {
            for (Long id : goodsIds) {
                if (id != null) nextGoods.put(id);
            }
        }
        goodsIdFilter.set(nextGoods);

        BloomFilter<Long> nextSeckill = BloomFilter.create(Funnels.longFunnel(), expectedInsertions, fpp);
        List<Long> seckillIds = seckillGoodsMapper.selectAllIds();
        if (seckillIds != null) {
            for (Long id : seckillIds) {
                if (id != null) nextSeckill.put(id);
            }
        }
        seckillIdFilter.set(nextSeckill);

        log.info("BloomFilter rebuild done. goods={}, seckill={}",
                goodsIds == null ? 0 : goodsIds.size(),
                seckillIds == null ? 0 : seckillIds.size());
    }

    /**
     * 新增商品时增量写入，避免新商品在过滤器重建前被误判为"不存在"。
     */
    public void addGoodsId(Long id) {
        BloomFilter<Long> filter = goodsIdFilter.get();
        if (filter != null && id != null) {
            filter.put(id);
        }
    }

    public void addSeckillId(Long id) {
        BloomFilter<Long> filter = seckillIdFilter.get();
        if (filter != null && id != null) {
            filter.put(id);
        }
    }

    /**
     * 判断商品 ID 是否"可能存在"。
     * 未初始化时返回 true（保守放行，避免冷启动期间打挂数据库前先拒了正常请求）。
     */
    public boolean mightContainGoodsId(Long id) {
        if (id == null) return false;
        BloomFilter<Long> filter = goodsIdFilter.get();
        if (filter == null) return true;
        return filter.mightContain(id);
    }

    public boolean mightContainSeckillId(Long id) {
        if (id == null) return false;
        BloomFilter<Long> filter = seckillIdFilter.get();
        if (filter == null) return true;
        return filter.mightContain(id);
    }
}
