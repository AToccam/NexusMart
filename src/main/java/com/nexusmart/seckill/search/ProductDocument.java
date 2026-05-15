package com.nexusmart.seckill.search;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.math.BigDecimal;

/**
 * Elasticsearch 商品索引文档。
 *
 * <p>映射对应 nexusmart-product 索引。中文检索字段（name / description）默认使用
 * standard 分词器，生产环境建议安装 IK 插件后改为 analyzer = "ik_smart"。
 */
@Data
@Document(indexName = "nexusmart-product", createIndex = true)
public class ProductDocument {

    @Id
    private Long productId;

    @Field(type = FieldType.Text, analyzer = "standard", searchAnalyzer = "standard")
    private String name;

    @Field(type = FieldType.Text, analyzer = "standard", searchAnalyzer = "standard")
    private String description;

    @Field(type = FieldType.Keyword)
    private String category;

    @Field(type = FieldType.Scaled_Float, scalingFactor = 100)
    private BigDecimal price;

    @Field(type = FieldType.Integer)
    private Integer stock;

    @Field(type = FieldType.Long)
    private Long merchantId;
}
