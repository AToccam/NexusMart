package com.nexusmart.seckill.search;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

/**
 * 商品搜索仓储：Spring Data 自动生成实现，复杂查询由 Service 层使用
 * ElasticsearchOperations 直接构造。
 */
@Repository
public interface ProductSearchRepository extends ElasticsearchRepository<ProductDocument, Long> {
}
