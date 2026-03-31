package com.nexusmart.seckill;

import com.nexusmart.seckill.entity.*;
import com.nexusmart.seckill.mapper.*;
import com.nexusmart.seckill.common.OrderStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 全量 Mapper 层集成测试（V2.0 新数据库结构）
 * @SpringBootTest 会真正启动 Spring 容器 + 连接数据库。
 * 带 @Transactional 的测试方法会在结束后自动回滚，不污染数据库。
 */
@SpringBootTest
class MapperTest {

    @Autowired private UserInfoMapper userInfoMapper;
    @Autowired private MerchantInfoMapper merchantInfoMapper;
    @Autowired private GoodsMapper goodsMapper;
    @Autowired private SeckillGoodsMapper seckillGoodsMapper;
    @Autowired private OrderInfoMapper orderInfoMapper;
    @Autowired private SeckillOrderMapper seckillOrderMapper;

    // ==================== 1. UserInfoMapper 测试 ====================

    @Test
    void testSelectUserById() {
        UserInfo user = userInfoMapper.selectById(1001L);
        System.out.println("======== 查询测试用户 ========");
        System.out.println(user);
        assertNotNull(user, "ID=1001 的用户不存在，请先执行初始化 SQL");
        assertEquals("TestUser_Alpha", user.getNickname());
    }

    @Test
    @Transactional
    void testInsertUser() {
        UserInfo user = new UserInfo();
        user.setNickname("新用户_测试");
        user.setPassword("e10adc3949ba59abbe56e057f20f883e");
        user.setSalt("abcdef");

        int rows = userInfoMapper.insert(user);
        System.out.println("======== 新增用户测试 ========");
        System.out.println("受影响行数：" + rows + "，回填 ID：" + user.getId());
        assertEquals(1, rows);
        assertNotNull(user.getId());
    }

    // ==================== 2. MerchantInfoMapper 测试 ====================

    @Test
    void testSelectMerchantById() {
        MerchantInfo merchant = merchantInfoMapper.selectById(1L);
        System.out.println("======== 查询测试商家 ========");
        System.out.println(merchant);
        assertNotNull(merchant, "ID=1 的商家不存在");
        assertEquals("NexusMart 自营旗舰店", merchant.getShopName());
    }

    @Test
    void testSelectAllActiveMerchants() {
        List<MerchantInfo> list = merchantInfoMapper.selectAllActive();
        System.out.println("======== 正常营业商家列表 ========");
        list.forEach(System.out::println);
        assertFalse(list.isEmpty());
    }

    // ==================== 3. GoodsMapper 测试 ====================

    @Test
    void testSelectGoodsById() {
        Goods goods = goodsMapper.selectById(1L);
        System.out.println("======== 查询 ID=1 的普通商品 ========");
        System.out.println(goods);
        assertNotNull(goods, "ID=1 的商品不存在");
        assertEquals("RTX 9090 Ti 赛博限量版", goods.getGoodsName());
        assertEquals(1L, goods.getMerchantId());
        System.out.println("商品名称：" + goods.getGoodsName());
        System.out.println("日常价格：" + goods.getGoodsPrice());
        System.out.println("日常库存：" + goods.getGoodsStock());
    }

    @Test
    void testSelectGoodsByMerchantId() {
        List<Goods> list = goodsMapper.selectByMerchantId(1L);
        System.out.println("======== 商家ID=1 的商品列表 ========");
        list.forEach(System.out::println);
        assertFalse(list.isEmpty());
    }

    @Test
    @Transactional
    void testInsertGoods() {
        Goods newGoods = new Goods();
        newGoods.setMerchantId(1L);
        newGoods.setGoodsName("测试商品 - 赛博水杯");
        newGoods.setGoodsImg("https://example.com/cup.png");
        newGoods.setGoodsPrice(new BigDecimal("99.99"));
        newGoods.setGoodsStock(50);

        int rows = goodsMapper.insert(newGoods);
        System.out.println("======== 新增普通商品测试 ========");
        System.out.println("受影响行数：" + rows + "，回填 ID：" + newGoods.getId());
        assertEquals(1, rows);
        assertNotNull(newGoods.getId());
    }

    // ==================== 4. SeckillGoodsMapper 测试（核心！） ====================

    @Test
    void testSelectSeckillGoodsByGoodsId() {
        SeckillGoods sg = seckillGoodsMapper.selectByGoodsId(1L);
        System.out.println("======== 查询商品ID=1 的秒杀配置 ========");
        System.out.println(sg);
        assertNotNull(sg, "商品 ID=1 没有秒杀配置");
        assertEquals(new BigDecimal("9999.00"), sg.getSeckillPrice());
        assertEquals(10, sg.getStockCount());
        assertEquals(0, sg.getVersion());
        System.out.println("秒杀价：" + sg.getSeckillPrice());
        System.out.println("秒杀库存：" + sg.getStockCount());
        System.out.println("版本号：" + sg.getVersion());
    }

    @Test
    void testSelectOngoingSeckillGoods() {
        List<SeckillGoods> list = seckillGoodsMapper.selectOngoing();
        System.out.println("======== 当前进行中的秒杀列表 ========");
        list.forEach(System.out::println);
        // 测试数据时间范围是 2026-03-01 ~ 2026-12-31，当前 2026-03-05 应该能查到
        assertFalse(list.isEmpty(), "当前时间应在秒杀时间范围内");
    }

    @Test
    @Transactional
    void testDecreaseStockByOptimisticLock() {
        // 先查出当前秒杀商品的库存和版本号
        SeckillGoods before = seckillGoodsMapper.selectByGoodsId(1L);
        assertNotNull(before);
        int stockBefore = before.getStockCount();
        int versionBefore = before.getVersion();
        System.out.println("======== 乐观锁秒杀扣减测试 ========");
        System.out.println("扣减前 → 库存：" + stockBefore + "，版本号：" + versionBefore);

        // 用正确的版本号扣减（应成功）
        int rows = seckillGoodsMapper.decreaseStockByOptimisticLock(before.getId(), versionBefore);
        assertEquals(1, rows, "版本号匹配，扣减应成功");
        SeckillGoods after = seckillGoodsMapper.selectById(before.getId());
        assertEquals(stockBefore - 1, after.getStockCount());
        assertEquals(versionBefore + 1, after.getVersion());
        System.out.println("扣减后 → 库存：" + after.getStockCount() + "，版本号：" + after.getVersion() + " ✓");

        // 用旧版本号再扣一次（应失败 = 乐观锁冲突）
        int rows2 = seckillGoodsMapper.decreaseStockByOptimisticLock(before.getId(), versionBefore);
        assertEquals(0, rows2, "旧版本号应被乐观锁拦截");
        System.out.println("旧版本号扣减返回 0，乐观锁拦截生效 ✓");
    }

    // ==================== 5. OrderInfoMapper 测试 ====================

    @Test
    @Transactional
    void testInsertOrder() {
        OrderInfo order = new OrderInfo();
        order.setUserId(1001L);
        order.setMerchantId(1L);
        order.setGoodsId(1L);
        order.setGoodsName("RTX 9090 Ti 赛博限量版");
        order.setOrderPrice(new BigDecimal("9999.00"));
        order.setStatus(OrderStatus.QUEUING.getCode());

        int rows = orderInfoMapper.insert(order);
        System.out.println("======== 创建完整订单测试 ========");
        System.out.println("受影响行数：" + rows + "，回填订单 ID：" + order.getId());
        assertEquals(1, rows);
        assertNotNull(order.getId());

        // 查询确认
        OrderInfo queried = orderInfoMapper.selectById(order.getId());
        assertNotNull(queried);
        assertEquals("RTX 9090 Ti 赛博限量版", queried.getGoodsName());
        assertEquals(new BigDecimal("9999.00"), queried.getOrderPrice());
        System.out.println("订单快照验证 → 商品名：" + queried.getGoodsName() + "，成交价：" + queried.getOrderPrice() + " ✓");
    }

    @Test
    @Transactional
    void testUpdateOrderStatus() {
        // 先插入一条订单
        OrderInfo order = new OrderInfo();
        order.setUserId(7777L);
        order.setMerchantId(1L);
        order.setGoodsId(1L);
        order.setGoodsName("RTX 9090 Ti 赛博限量版");
        order.setOrderPrice(new BigDecimal("9999.00"));
        order.setStatus(OrderStatus.QUEUING.getCode());
        orderInfoMapper.insert(order);

        // 模拟异步处理成功 0 -> 1
        int rows = orderInfoMapper.updateStatus(order.getId(), OrderStatus.SUCCESS.getCode());
        System.out.println("======== 订单状态更新测试 ========");
        assertEquals(1, rows);

        OrderInfo updated = orderInfoMapper.selectById(order.getId());
        assertEquals(OrderStatus.SUCCESS.getCode(), updated.getStatus());
        System.out.println("订单 " + order.getId() + " 状态更新为 1（成功）✓");
    }

    // ==================== 6. SeckillOrderMapper 测试（防重复下单） ====================

    @Test
    @Transactional
    void testSeckillOrderInsertAndDuplicateCheck() {
        // 先创建一条完整订单
        OrderInfo order = new OrderInfo();
        order.setUserId(1001L);
        order.setMerchantId(1L);
        order.setGoodsId(1L);
        order.setGoodsName("RTX 9090 Ti 赛博限量版");
        order.setOrderPrice(new BigDecimal("9999.00"));
        order.setStatus(OrderStatus.QUEUING.getCode());
        orderInfoMapper.insert(order);

        // 插入秒杀防重记录
        SeckillOrder so = new SeckillOrder();
        so.setUserId(1001L);
        so.setOrderId(order.getId());
        so.setGoodsId(1L);
        int rows = seckillOrderMapper.insert(so);
        System.out.println("======== 秒杀防重订单测试 ========");
        System.out.println("受影响行数：" + rows + "，回填 ID：" + so.getId());
        assertEquals(1, rows);

        // 查询防重：同用户 + 同商品，应该能查到
        SeckillOrder exist = seckillOrderMapper.selectByUserIdAndGoodsId(1001L, 1L);
        assertNotNull(exist, "刚插入的秒杀防重记录应能查到");
        System.out.println("防重查询命中 → " + exist + " ✓");

        // 查一个不存在的组合
        SeckillOrder notExist = seckillOrderMapper.selectByUserIdAndGoodsId(8888L, 1L);
        assertNull(notExist, "不存在的组合应返回 null");
        System.out.println("不存在的秒杀记录返回 null ✓");

        // 尝试重复插入（同 user_id + goods_id）→ 唯一索引会抛异常
        SeckillOrder duplicate = new SeckillOrder();
        duplicate.setUserId(1001L);
        duplicate.setOrderId(order.getId());
        duplicate.setGoodsId(1L);
        assertThrows(Exception.class, () -> seckillOrderMapper.insert(duplicate),
                "重复插入应触发唯一索引冲突异常");
        System.out.println("重复下单被唯一索引拦截 ✓");
    }
}
