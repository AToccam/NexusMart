package com.nexusmart.seckill.search;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * 商品搜索结果分页包装。
 */
@Data
@AllArgsConstructor
public class ProductSearchResult {
    private List<ProductDocument> items;
    private long total;
    private int page;
    private int size;
}
