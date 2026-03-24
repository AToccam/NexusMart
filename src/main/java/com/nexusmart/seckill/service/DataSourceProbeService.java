package com.nexusmart.seckill.service;

import com.nexusmart.seckill.config.datasource.ReadOnlyDataSource;
import com.nexusmart.seckill.config.datasource.WriteDataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class DataSourceProbeService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @WriteDataSource
    public Long queryMasterServerId() {
        return jdbcTemplate.queryForObject("SELECT @@server_id", Long.class);
    }

    @ReadOnlyDataSource
    public Long querySlaveServerId() {
        return jdbcTemplate.queryForObject("SELECT @@server_id", Long.class);
    }
}
