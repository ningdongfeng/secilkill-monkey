package com.seckill.task;

import com.alibaba.fastjson.JSONObject;
import com.seckill.constants.Constants;
import com.seckill.mapper.GoodsMapper;
import com.seckill.model.Goods;
import com.seckill.model.GoodsExample;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.annotation.Reference;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.List;
import java.util.Set;

/**
 * ClassName:RedisTask
 * PackAge:com.bjpowernode.seckill.task
 * Decription:
 *
 * @date:2019/7/29 8:38
 * @author:ningdongfeng
 */

@Configuration
@EnableScheduling
public class RedisTask {
    @Reference
    private GoodsMapper productMapper;
    @Reference
    private RedisTemplate<String,String> redisTemplate;

    /**
     * 每5秒执行一次定时任务，初始化一遍秒杀商品信息
     * 缓存预热
     */
    @Scheduled(cron = "0/5 * * * * *")
    public void initRedisGoods(){
        System.out.println("缓存预热...........");
        //查询所有秒杀商品数据
        GoodsExample example = new GoodsExample();
        List<Goods> goodsList = productMapper.selectByExample(example);
        //放入到Redis中
        for(Goods goods:goodsList){
            //因为后续还需要查询商品的详情，如果将整个List放进去，后续查询详情麻烦些
            String goodsJSON = JSONObject.toJSONString(goods);
            redisTemplate.opsForValue().set(Constants.REDIS_GOODS +goods.getId(),goodsJSON);
            //定时将数据库中商品库存写入缓存
            redisTemplate.opsForValue().setIfAbsent(Constants.REDIS_STORE+goods.getId(),goods.getStore().toString());
        }
    }
    /**
     * 没3秒进行一次同步，将缓存中的数据同步到数据库
     */
    @Scheduled(cron = "0/3 * * * * *")
    public void updateToDB(){
        Goods product = new Goods();
        System.out.println("同步redis中的数据至数据库中！");
        Set<String> keys = redisTemplate.keys(Constants.REDIS_STORE + "*");
        for (String key : keys) {
            int store = Integer.parseInt(redisTemplate.opsForValue().get(key));
            int productId = Integer.parseInt(key.split(":")[2]);
            product.setId(productId);
            product.setStore(store);
            productMapper.updateByPrimaryKeySelective(product);
        }
    }

}
