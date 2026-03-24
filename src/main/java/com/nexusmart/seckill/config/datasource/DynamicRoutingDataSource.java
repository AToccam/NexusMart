package com.nexusmart.seckill.config.datasource;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

public class DynamicRoutingDataSource extends AbstractRoutingDataSource {

    @Override
    protected Object determineCurrentLookupKey() {
        DataSourceType type = DynamicDataSourceContextHolder.get();
        return type == null ? DataSourceType.MASTER : type;
    }
}
