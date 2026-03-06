package com.nexusmart.seckill.service;

import com.nexusmart.seckill.entity.Goods;
import com.nexusmart.seckill.entity.SeckillGoods;
import com.nexusmart.seckill.mapper.GoodsMapper;
import com.nexusmart.seckill.mapper.SeckillGoodsMapper;
import com.nexusmart.seckill.vo.SeckillGoodsVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class GoodsService {

    @Autowired
    private GoodsMapper goodsMapper;

    @Autowired
    private SeckillGoodsMapper seckillGoodsMapper;

    /**
     * 查询正在进行的秒杀商品列表（合并商品基本信息）
     */
    public List<SeckillGoodsVo> listSeckillGoods() {
        List<SeckillGoods> seckillList = seckillGoodsMapper.selectOngoing();
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
        return voList;
    }
}
