package com.nexusmart.seckill.sharding;

import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.shardingsphere.driver.api.yaml.YamlShardingSphereDataSourceFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;

import javax.sql.DataSource;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 分库分表数据源 + MyBatis 工厂（仅在 profile=sharding 时启用）。
 *
 * <p>启用方式：
 * <pre>
 *   SPRING_PROFILES_ACTIVE=dev,sharding ./mvnw spring-boot:run
 * </pre>
 *
 * <p>未启用时不创建任何 Bean，OrderArchiveMapper 不会被扫描，启动期不会因
 * 默认库缺少逻辑表 order_archive 而失败。
 */
@Configuration
@Profile("sharding")
@MapperScan(
        basePackages = "com.nexusmart.seckill.sharding",
        sqlSessionFactoryRef = "shardingSqlSessionFactory")
public class ShardingDataSourceConfig {

    @Bean(name = "shardingDataSource")
    public DataSource shardingDataSource() throws IOException, java.sql.SQLException {
        ClassPathResource resource = new ClassPathResource("sharding.yaml");
        // ShardingSphere 5.x 工厂要求 File，先解到临时文件，兼容 jar 内运行
        File tmp = File.createTempFile("sharding-", ".yaml");
        tmp.deleteOnExit();
        try (var in = resource.getInputStream()) {
            Files.copy(in, Path.of(tmp.getAbsolutePath()), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        return YamlShardingSphereDataSourceFactory.createDataSource(tmp);
    }

    @Bean(name = "shardingSqlSessionFactory")
    public SqlSessionFactory shardingSqlSessionFactory(DataSource shardingDataSource) throws Exception {
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(shardingDataSource);
        return factory.getObject();
    }

    @Bean(name = "shardingSqlSessionTemplate")
    public SqlSessionTemplate shardingSqlSessionTemplate(SqlSessionFactory shardingSqlSessionFactory) {
        return new SqlSessionTemplate(shardingSqlSessionFactory);
    }
}
