package com.seckill.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.seckill.constants.Constants;
import com.seckill.mapper.OrdersMapper;
import com.seckill.model.Orders;
import com.seckill.service.OrdersService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.annotation.Reference;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * ClassName:OrdersServiceImpl
 * PackAge:com.bjpowernode.seckill.service.impl
 * Decription:
 *
 * @date:2019/7/30 20:45
 * @author:ningdongfeng
 */

@Service
public class OrdersServiceImpl implements OrdersService {
    @Reference
    private OrdersMapper ordersMapper;

    @Reference
    private RedisTemplate<String,String> redisTemplate;

    @Transactional
    @Override
    public int addOrders(Orders  orders) {
        int addRow = ordersMapper.insertSelective(orders);
        if(addRow >0){
            /*下单成功，告知前台秒杀最终结果，我们这是service项目，由消息消费者调用，不能
            直接和前台打交道，所以需要前台重新发送请求，去数据库订单表中查询结果，但是这样
            对数据库带来压力，所以我们将秒杀最终结果放到Redis中，然后前台页面去Redis中查询
             */
            //用我们自定义我的RTO对象封装秒杀结果
            Map<String,Object> retMap = new HashMap<>();
            retMap.put(Constants.MESSAGE,Constants.OK);
            retMap.put("orders",orders);
            String returnJSON = JSONObject.toJSONString(retMap);
            redisTemplate.opsForValue().set(Constants.REDIS_RESULT +
                    orders.getGoodsid() +":" + orders.getUid(),returnJSON);
            //当前这个人秒杀全部结束，应该把当前这个人从限流列表中删除,让后面的人再进来秒杀
            redisTemplate.opsForList().rightPop(Constants.REDIS_LIMIT + orders.getGoodsid());
        }else{
            //下单失败，抛出运行时异常
            throw new RuntimeException("秒杀下单失败");
        }
        return addRow;
    }
    /**
     下单失败之后，进行之前处理数据的恢复
     */
    @Override
    public void processException(Orders orders) {
        // 1.库存恢复
        redisTemplate.opsForValue().increment(Constants.REDIS_STORE + orders.getGoodsid(),1);
        //2.购买标记清除
        redisTemplate.delete(Constants.REDIS_BUY + orders.getGoodsid() +":" + orders.getUid());
        // 3.限流列表中删除一个元素
        redisTemplate.opsForList().rightPop(Constants.REDIS_LIMIT + orders.getGoodsid());
        //4.将失败信息放到Redis中，便于前台页面再次获取
        Map<String,Object> retMap = new HashMap<>();
        retMap.put(Constants.MESSAGE,"秒杀失败");
        retMap.put("orders",orders);
        String returnJSON = JSONObject.toJSONString(retMap);
        redisTemplate.opsForValue().set(Constants.REDIS_RESULT +
                orders.getGoodsid() + ":" + orders.getUid(), returnJSON);

    }

}
