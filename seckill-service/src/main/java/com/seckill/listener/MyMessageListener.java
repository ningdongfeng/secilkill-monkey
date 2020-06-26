package com.seckill.listener;

import com.alibaba.fastjson.JSONObject;
import com.seckill.model.Orders;
import com.seckill.service.OrdersService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.annotation.Reference;
import org.springframework.stereotype.Component;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.TextMessage;

/**
 * ClassName:MyMessageListener
 * PackAge:com.bjpowernode.seckill.listener
 * Decription:
 *
 * @date:2019/7/30 20:36
 * @author:ningdongfeng
 */

@Component
public class MyMessageListener {
    @Reference
    private OrdersService ordersService;

    public void onMessage(Message message) {
        if(message instanceof TextMessage){
            try {
                String ordersJSON = ((TextMessage) message).getText();
                System.out.println("SpringBoot监听器异步接收到的消息为：" + ordersJSON);
                Orders orders = JSONObject.parseObject(ordersJSON,Orders.class);
                try {
                    //接收到消息，下订单
                    ordersService.addOrders(orders);
                } catch (Exception e) {
                    e.printStackTrace();
                    //下单失败了，要将之前的一些处理恢复一下
                    ordersService.processException(orders);
                }
            } catch (JMSException e) {
                e.printStackTrace();
            }
        }
    }
}
