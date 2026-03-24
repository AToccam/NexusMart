package com.nexusmart.seckill;

import com.nexusmart.seckill.service.DataSourceProbeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
class ReadWriteSplitTest {

    @Autowired
    private DataSourceProbeService probeService;

    @Test
    void testReadWriteRouteToDifferentServerId() {
        Long masterServerId = probeService.queryMasterServerId();
        Long slaveServerId = probeService.querySlaveServerId();

        assertNotNull(masterServerId, "主库 server_id 不应为空");
        assertNotNull(slaveServerId, "从库 server_id 不应为空");
        assertNotEquals(masterServerId, slaveServerId,
                "读写路由未生效：主从 server_id 相同，请确认 mysql-master/mysql-slave 与复制链路已启动");
    }
}
