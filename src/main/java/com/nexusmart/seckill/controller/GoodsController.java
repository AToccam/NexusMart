package com.nexusmart.seckill.controller;

import com.nexusmart.seckill.common.Result;
import com.nexusmart.seckill.service.GoodsService;
import com.nexusmart.seckill.vo.SeckillGoodsVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/goods")
public class GoodsController {

    @Autowired
    private GoodsService goodsService;

    /** 获取当前正在进行中的秒杀商品列表 */
    @GetMapping("/seckill/list")
    public Result<List<SeckillGoodsVo>> listSeckillGoods() {
        return Result.success(goodsService.listSeckillGoods());
    }

    /** 获取商品详情（带缓存防护） */
    @GetMapping("/detail")
    public Result<SeckillGoodsVo> getGoodsDetail(@RequestParam Long goodsId) {
        SeckillGoodsVo detail = goodsService.getGoodsDetail(goodsId);
        if (detail == null) {
            return Result.error("商品不存在");
        }
        return Result.success(detail);
    }
}
