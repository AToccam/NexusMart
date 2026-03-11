package com.nexusmart.seckill.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.nexusmart.seckill.entity.Goods;
import com.nexusmart.seckill.entity.SeckillGoods;
import com.nexusmart.seckill.mapper.GoodsMapper;
import com.nexusmart.seckill.mapper.SeckillGoodsMapper;
import com.nexusmart.seckill.util.RedisCacheUtil;
import com.nexusmart.seckill.vo.SeckillGoodsVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class GoodsService {

    public static final String SECKILL_LIST_KEY = "seckill:goods:list";
    private static final String SECKILL_LIST_LOCK = "seckill:goods:list";

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @Autowired
    private GoodsMapper goodsMapper;

    @Autowired
    private SeckillGoodsMapper seckillGoodsMapper;

    @Autowired
    private RedisCacheUtil cacheUtil;

    /**
     * 查询正在进行的秒杀商品列表（优先走 Redis 缓存，防击穿/穿透/雪崩）
     */
    public List<SeckillGoodsVo> listSeckillGoods() {
        String json = cacheUtil.getWithMutex(
                SECKILL_LIST_KEY,
                SECKILL_LIST_LOCK,
                this::loadSeckillGoodsFromDb,
                600, 120);  // 基础 TTL 600s + 随机 0~120s

        if (json == null) {
            return Collections.emptyList();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<List<SeckillGoodsVo>>() {});
        } catch (JsonProcessingException e) {
            throw new RuntimeException("反序列化秒杀商品列表失败", e);
        }
    }

    /**
     * 从 DB 加载秒杀商品列表（仅在缓存未命中时由 getWithMutex 回调）
     */
    private String loadSeckillGoodsFromDb() {
        List<SeckillGoods> seckillList = seckillGoodsMapper.selectOngoing();
        if (seckillList == null || seckillList.isEmpty()) {
            return null;
        }
        List<SeckillGoodsVo> voList = new ArrayList<>();
        for (SeckillGoods sg : seckillList) {
            Goods goods = goodsMapper.selectById(sg.getGoodsId());
            if (goods == null) continue;

            SeckillGoodsVo vo = new SeckillGoodsVo();
            vo.setGoodsId(goods.getId());
            vo.setGoodsName(goods.getGoodsName());
            vo.setGoodsImg(goods.getGoodsImg());
            vo.setGoodsPrice(goods.getGoodsPrice());
            vo.setSeckillId(sg.getId());
            vo.setSeckillPrice(sg.getSeckillPrice());
            vo.setStockCount(sg.getStockCount());
            vo.setStartTime(sg.getStartTime());
            vo.setEndTime(sg.getEndTime());
            voList.add(vo);
        }
        try {
            return MAPPER.writeValueAsString(voList);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("序列化秒杀商品列表失败", e);
        }
    }
}
