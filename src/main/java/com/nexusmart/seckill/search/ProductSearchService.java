package com.nexusmart.seckill.search;

import com.nexusmart.seckill.entity.Goods;
import com.nexusmart.seckill.mapper.GoodsMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Elasticsearch 商品搜索服务：
 * <ul>
 *   <li>启动时把 MySQL goods 全量同步到 ES（双写策略 A，更新场景上层需调用 {@link #upsert}）。</li>
 *   <li>提供按 keyword 多字段 multi_match 检索 + 分页。</li>
 * </ul>
 */
@Service
public class ProductSearchService {

    private static final Logger log = LoggerFactory.getLogger(ProductSearchService.class);

    @Autowired
    private ProductSearchRepository repository;

    @Autowired
    private ElasticsearchOperations operations;

    @Autowired
    private GoodsMapper goodsMapper;

    /**
     * 把 MySQL 中所有 goods 同步到 ES 索引。仅做演示与启动期重建使用。
     */
    public int reindexAll() {
        IndexOperations indexOps = operations.indexOps(ProductDocument.class);
        if (!indexOps.exists()) {
            indexOps.createWithMapping();
        }
        List<Goods> all = goodsMapper.selectAll();
        if (all == null || all.isEmpty()) {
            return 0;
        }
        List<ProductDocument> docs = new ArrayList<>(all.size());
        for (Goods g : all) {
            docs.add(toDocument(g));
        }
        repository.saveAll(docs);
        log.info("[ES] reindexAll done, total={} docs", docs.size());
        return docs.size();
    }

    /**
     * 单条 upsert，业务侧在新增 / 更新商品时调用，保证 MySQL → ES 双写一致。
     */
    public void upsert(Goods goods) {
        if (goods == null || goods.getId() == null) {
            return;
        }
        repository.save(toDocument(goods));
    }

    /**
     * 删除单个文档（商品下架场景）。
     */
    public void delete(Long productId) {
        if (productId == null) {
            return;
        }
        repository.deleteById(productId);
    }

    /**
     * 多字段全文检索 + 分页。
     */
    public ProductSearchResult search(String keyword, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 100);

        Query query;
        if (keyword == null || keyword.isBlank()) {
            query = NativeQuery.builder()
                    .withQuery(q -> q.matchAll(m -> m))
                    .withPageable(PageRequest.of(safePage, safeSize))
                    .build();
        } else {
            final String kw = keyword;
            query = NativeQuery.builder()
                    .withQuery(q -> q
                            .multiMatch(mm -> mm
                                    .query(kw)
                                    .fields("name^3", "description")))
                    .withPageable(PageRequest.of(safePage, safeSize))
                    .build();
        }

        SearchHits<ProductDocument> hits = operations.search(query, ProductDocument.class);
        List<ProductDocument> items = new ArrayList<>((int) hits.getTotalHits());
        for (SearchHit<ProductDocument> hit : hits) {
            items.add(hit.getContent());
        }
        return new ProductSearchResult(items, hits.getTotalHits(), safePage, safeSize);
    }

    private ProductDocument toDocument(Goods g) {
        ProductDocument d = new ProductDocument();
        d.setProductId(g.getId());
        d.setName(g.getGoodsName());
        d.setDescription(g.getDescription());
        d.setCategory("default");
        d.setPrice(g.getGoodsPrice());
        d.setStock(g.getGoodsStock());
        d.setMerchantId(g.getMerchantId());
        return d;
    }
}
