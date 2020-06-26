package com.seckill.service;

import com.seckill.model.Orders;

/**
 * ClassName:OrdersService
 * PackAge:com.bjpowernode.seckill.service
 * Decription:
 *
 * @date:2019/7/30 20:39
 * @author:ningdongfeng
 */


public interface OrdersService {

    int addOrders(Orders orders);

    void processException(Orders orders);
}
