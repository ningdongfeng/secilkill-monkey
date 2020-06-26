package com.seckill.config;

import com.seckill.listener.MyMessageListener;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.annotation.Reference;
import org.springframework.jms.listener.DefaultMessageListenerContainer;

/**
 * ClassName:ActiveMQConfig
 * PackAge:com.bjpowernode.seckill.config
 * Decription:
 *
 * @date:2019/7/30 20:35
 * @author:ningdongfeng
 */

@Configuration
public class ActiveMQConfig {
    @Reference
    private ActiveMQConnectionFactory connectionFactory;

    @Reference
    private MyMessageListener myMessageListener;

    @Value("${spring.jms.template.default-destination}")
    private String destination;

    @Value("${spring.jms.pub-sub-domain}")
    private boolean pubSubDomain;

    @Bean //@Bean注解就相当于配置文件的bean标签
    public DefaultMessageListenerContainer defaultMessageListenerContainer(){
        DefaultMessageListenerContainer listenerContainer = new DefaultMessageListenerContainer();
        listenerContainer.setConnectionFactory(connectionFactory);
        listenerContainer.setDestinationName(destination);
        listenerContainer.setMessageListener(myMessageListener);
        //设置消息发送模式方式为点对点（保证消息一定可以被消费！）
        listenerContainer.setPubSubDomain(pubSubDomain);
        listenerContainer.setMaxConcurrentConsumers(8*2);
        return listenerContainer;
    }
}
