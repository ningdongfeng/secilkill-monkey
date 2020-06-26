package com.seckill.seckillweb.controller;

import com.alibaba.fastjson.JSONObject;
import com.seckill.constants.Constants;
import com.seckill.model.Orders;
import com.seckill.model.Goods;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.core.MessageCreator;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Session;
import java.math.BigDecimal;
import java.util.*;

@Controller
public class ProductController {

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private JmsTemplate jmsTemplate;

    @GetMapping("/seckill/index")
    public String index(Model model) {
        //在高并发的情况下 用户可能会连续刷页面 如果我们用查询数据库的方式去获取数据 会导致数据库压力特别大
        //用redis把我们的商品数据信息 先缓存--->>缓存预热
        //商品信息有可能变化 如果还用缓存 隔一段时间就要更新缓存里面的信息
        List<Goods> productList = new ArrayList<>();
        Set<String> keys = redisTemplate.keys(Constants.REDIS_GOODS + "*");
        Iterator<String> iterator = keys.iterator();
        for (String key : keys) {
            String productJason = redisTemplate.opsForValue().get(key);
            Goods product = JSONObject.parseObject(productJason, Goods.class);
            productList.add(product);
        }
        model.addAttribute("productList", productList);
        return "product";
    }

    @GetMapping("/seckill/detail/{id}")
    public String detail(Model model, @PathVariable("id") Integer productId) {
        String productJson = redisTemplate.opsForValue().get(Constants.REDIS_GOODS + productId);
        Goods product = JSONObject.parseObject(productJson, Goods.class);
        model.addAttribute("product", product);
        //当前时间
        long nowTime = System.currentTimeMillis();
        model.addAttribute("nowTime", nowTime);
        return "detail";
    }

    @PostMapping("/seckill/random/{id}")
    public @ResponseBody
    Map<String, Object>
    random(@PathVariable("id") Integer productId) {
        //获取当前时间
        long nowTime = System.currentTimeMillis();
        //返回结果集
        Map<String, Object> ret2FrontObjet = new HashMap<>();        //项目中尽量少使用new关键字，可以创建一个公共返回的类
        //获取当前商品信息
        String productJson = redisTemplate.opsForValue().get(Constants.REDIS_GOODS + productId);
        Goods product = JSONObject.parseObject(productJson, Goods.class);
        //判断秒杀是否真的开始
        if (product.getStarttime().getTime() > nowTime) {
            //秒杀还未开始
            ret2FrontObjet.put("message", "秒杀还未开始！");
        } else if (nowTime > product.getEndtime().getTime()) {
            //秒杀已经结束
            ret2FrontObjet.put("message", "秒杀已经结束！");
        } else {
            //秒杀正式开始
            ret2FrontObjet.put("message", Constants.OK);
            ret2FrontObjet.put("data", product.getRandomname());
        }
        return ret2FrontObjet;
    }

    //生成订单orders记录并返回
    @PostMapping("/seckill/product/{frontRandom}/{productId}")
    public @ResponseBody
    Map<String, Object> product(@PathVariable("frontRandom") String frontRandom,
                                @PathVariable("productId") Integer productId) {
        Map<String, Object> retMap = new HashMap<>();
        //redis查出该商品信息，判断randomname是否一致
        String productJSON = redisTemplate.opsForValue().get(Constants.REDIS_GOODS + productId);
        Goods product = JSONObject.parseObject(productJSON, Goods.class);
        //1.验证用户是否登陆
        if (frontRandom.length() != 36) {
            //2.验证商品
            retMap.put(Constants.MESSAGE, "商品信息错误，请刷新页面！");
            return retMap;
        }
        //3.该页面的商品信息和之前查找的信息不一致
        if (!StringUtils.equalsIgnoreCase(frontRandom ,product.getRandomname())) {
            retMap.put(Constants.MESSAGE, "商品信息已经改变！");
            return retMap;
        }
        //4.判断是否还有库存
        String redisStore = redisTemplate.opsForValue().get(Constants.REDIS_STORE + product.getId());
        if (Integer.parseInt(redisStore) <= 0) {
            retMap.put(Constants.MESSAGE, "商品已经售完！");
            return retMap;
        }
        //5.是否已经购买过
        String uid = "8888";
        //redis中购买记录 格式：sexkill：buy：商品id：uid
        String reidsBuy = redisTemplate.opsForValue().get(Constants.REDIS_BUY + productId + ":" + uid);
        if (StringUtils.isNotEmpty(reidsBuy)) {
            retMap.put(Constants.MESSAGE, "你已经购买过该商品了！");
            return retMap;
        }
        ///6.限流
        //从Redis中查询出当前商品的访问量
        Long currentSize = redisTemplate.opsForList().size(Constants.REDIS_LIMIT + productId);
        if (currentSize > Constants.MAX_LIMIT) {
            //超过最大限流值，拒绝访问
            retMap.put(Constants.MESSAGE, "服务器繁忙，请稍后再试！~");
            return retMap;
        } else {
            //可以继续执行秒杀
            // 先向Redis的限流List中放一条数据  返回放完数据之后List的长度
            Long afterPushSize = redisTemplate.opsForList().leftPush(Constants.REDIS_LIMIT + productId,String.valueOf(uid));
    /*放完元素之后再次判断List的长度是否大于限流值
    主要处理多线程情况下，很多线程都满足限流条件，都向Redis的List添加元素，避免List元素超出限流值
    */
            if (afterPushSize > Constants.MAX_LIMIT) {
                redisTemplate.opsForList().rightPop(Constants.REDIS_LIMIT + productId);
                //超过最大限流值，拒绝访问
                retMap.put(Constants.MESSAGE,"服务器繁忙，请稍后再试！~");
                return retMap;
            }
        }
        //7.减库存
        Long leftStore = redisTemplate.opsForValue().decrement(Constants.REDIS_STORE +productId,1);
        //8.下单到activemq队列里
        if(leftStore >= 0){
            //可以秒杀，执行下单操作
            //标记用户已经买过该商品
            redisTemplate.opsForValue().set(Constants.REDIS_BUY + productId +":" +uid,String.valueOf(uid));
            //创建订单对象
            Orders orders = new Orders();
            orders.setBuynum(1);
            orders.setBuyprice(product.getPrice());
            orders.setCreatetime(new Date());
            orders.setGoodsid(productId);
            orders.setOrdermoney(product.getPrice().multiply(new BigDecimal(1)));
            orders.setStatus(1);//待支付
            orders.setUid(Integer.parseInt(uid));
            //将订单对象转换为json字符串
            String ordersJSON = JSONObject.toJSONString(orders);
            //通过JmsTemplate向ActiveMQ发送消息
            jmsTemplate.send(new MessageCreator() {
                @Override
                public Message createMessage(Session session) throws JMSException {
                    return session.createTextMessage(ordersJSON);
                }
            });
            retMap.put(Constants.MESSAGE,Constants.OK);
            return retMap;
        }else{
            //不可以卖了，不能执行下单操作
    /*
    此时Redis中的商品库存可能已经减成负数了，但是对我们业务的处理没有任何影响
    但为了保持数据的一致性，我们将值再恢复一下
     */
            redisTemplate.opsForValue().increment(Constants.REDIS_STORE + productId,1);
            retMap.put(Constants.MESSAGE,"来晚了，商品已经抢光了");
            return retMap;
        }
    }

    //
    @PostMapping("/seckill/resoult/{id}")
    public @ResponseBody Map<String,Object> resoult(@PathVariable("id") Integer id){
        Map<String,Object> retMap = new HashMap<>();
        //在Redis中暂时没有查询到结果
        retMap.put(Constants.MESSAGE,"秒杀失败！");
        //用户再次查询，肯定处于登录状态，可以从session获取用户信息（我们这里省略了用户登录）
        String resultJSON = redisTemplate.opsForValue().get(Constants.REDIS_RESULT + id);
//        return StringUtils.isEmpty(resultJSON)?returnObject : JSONObject.parseObject(resultJSON,ReturnObject.class);
        if (StringUtils.isNotEmpty(resultJSON)){
            retMap.put(Constants.MESSAGE,"秒杀成功！");
        }
        return retMap;
    }
}
