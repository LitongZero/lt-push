# Netty-SocketIO 集群解决方案

# [CSDN博客地址](https://blog.csdn.net/LitongZero/article/details/103507488)
# [Gitee地址](https://gitee.com/litong-zero/lt-push)

> #### `Netty-SocketIO`作为一个Socket框架，使用非常方便，并且使用`Netty开发`性能也有保证。
>
> #### 但是，我在使用`Netty-SocketIO`框架时，却发现，国内的资料比较少，虽然有些Demo级别的技术分享，但是关于集群解决方案，并没有什么较好的解决方法。

[TOC]


### 所以，博主结合GitHub上的`Issues`，实现了一种集群的解决方案。

## 一. 解决方案原理

使用Redis订阅\发布模式解决，实现多集群间通讯。

注：
1.官方好像推荐使用Redisson来解决集群问题，有兴趣的同学可以试试，我是没试出来。。
2.最新版Redis支持数据流存储，可通过缓存`SocketIOClient`来实现，有兴趣的同学可以试试。
## 二.服务端

使用SpringBoot+Netty-SocketIO+Redis，可通过修改启动端口，模拟多服务端

### 1.版本

`netty-socketio`: 1.7.11

`spring-boot-`: 2.2.1.RELEASE

`JDK`: 1.8

### 2.项目结构
![在这里插入图片描述](https://img-blog.csdnimg.cn/20191212110431168.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L0xpdG9uZ1plcm8=,size_16,color_FFFFFF,t_70)
### 3.架构\原理图
![在这里插入图片描述](https://img-blog.csdnimg.cn/20191212133435420.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L0xpdG9uZ1plcm8=,size_16,color_FFFFFF,t_70)

### 3.代码

#### com.lt.push.config

##### ClientCache

本地(数据中心)类

```java
package com.lt.push.config;

import com.corundumstudio.socketio.SocketIOClient;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author litong
 * @date 2019/11/6 16:01
 */
@Component
public class ClientCache {

    /**
     * 本地缓存
     */
    private static Map<String, HashMap<UUID, SocketIOClient>> concurrentHashMap = new ConcurrentHashMap<>();

    /**
     * 存入本地缓存
     *
     * @param userId         用户ID
     * @param sessionId      页面sessionID
     * @param socketIOClient 页面对应的通道连接信息
     */
    public void saveClient(String userId, UUID sessionId, SocketIOClient socketIOClient) {
        HashMap<UUID, SocketIOClient> sessionIdClientCache = concurrentHashMap.get(userId);
        if (sessionIdClientCache == null) {
            sessionIdClientCache = new HashMap<>();
        }
        sessionIdClientCache.put(sessionId, socketIOClient);
        concurrentHashMap.put(userId, sessionIdClientCache);
    }

    /**
     * 根据用户ID获取所有通道信息
     *
     * @param userId
     * @return
     */
    public HashMap<UUID, SocketIOClient> getUserClient(String userId) {
        return concurrentHashMap.get(userId);
    }

    /**
     * 根据用户ID及页面sessionID删除页面链接信息
     *
     * @param userId
     * @param sessionId
     */
    public void deleteSessionClient(String userId, UUID sessionId) {
        concurrentHashMap.get(userId).remove(sessionId);
    }
}

```

##### EventListenner

事件监听/注册中心

```java
package com.lt.push.config;

import com.corundumstudio.socketio.AckRequest;
import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.annotation.OnConnect;
import com.corundumstudio.socketio.annotation.OnDisconnect;
import com.corundumstudio.socketio.annotation.OnEvent;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.UUID;

/**
 * @author litong
 * @date 2019/11/6 15:59
 */
@Component
public class EventListenner {
    @Resource
    private ClientCache clientCache;

    /**
     * 客户端连接
     *
     * @param client
     */
    @OnConnect
    public void onConnect(SocketIOClient client) {
        String userId = client.getHandshakeData().getSingleUrlParam("userId");
        UUID sessionId = client.getSessionId();
        clientCache.saveClient(userId, sessionId, client);
        System.out.println("建立连接");
    }

    /**
     * 客户端断开
     *
     * @param client
     */
    @OnDisconnect
    public void onDisconnect(SocketIOClient client) {
        String userId = client.getHandshakeData().getSingleUrlParam("userId");
        clientCache.deleteSessionClient(userId, client.getSessionId());
        System.out.println("关闭连接");
    }

    //消息接收入口，当接收到消息后，查找发送目标客户端，并且向该客户端发送消息，且给自己发送消息
    // 暂未使用
    @OnEvent("messageevent")
    public void onEvent(SocketIOClient client, AckRequest request) {
    }
}
```

##### MessagePushConfig

Socket线程开启类

```java
package com.lt.push.config;

import com.corundumstudio.socketio.Configuration;
import com.corundumstudio.socketio.SocketConfig;
import com.corundumstudio.socketio.SocketIOServer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * @author litong
 * @date 2019/11/1 13:32
 */
@Component
@Slf4j
public class MessagePushConfig implements InitializingBean {
    @Resource
    private EventListenner eventListenner;

    @Value("${push.server.port}")
    private int serverPort;

    @Autowired
    private SocketIOServer socketIOServer;

    @Override
    public void afterPropertiesSet() throws Exception {
        socketIOServer.start();
        System.out.println("启动正常");
    }

    @Bean
    public SocketIOServer socketIOServer() {
        Configuration config = new Configuration();
        config.setPort(serverPort);

        SocketConfig socketConfig = new SocketConfig();
        socketConfig.setReuseAddress(true);
        socketConfig.setTcpNoDelay(true);
        socketConfig.setSoLinger(0);
        config.setSocketConfig(socketConfig);
        config.setHostname("localhost");

        SocketIOServer server = new SocketIOServer(config);
        server.addListeners(eventListenner);
        return server;
    }
}

```

##### RedisConfig

redis配置订阅\发布模式

```java
package com.lt.push.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * @author litong
 * @date 2019/10/17 14:33
 */
@Configuration
public class RedisConfig {
    @Autowired
    private RedisTemplate redisTemplate;

    @Bean
    public RedisTemplate<String, Object> stringSerializerRedisTemplate() {
        RedisSerializer<String> stringSerializer = new StringRedisSerializer();
        redisTemplate.setKeySerializer(stringSerializer);
        redisTemplate.setValueSerializer(stringSerializer);
        redisTemplate.setHashKeySerializer(stringSerializer);
        redisTemplate.setHashValueSerializer(stringSerializer);
        return redisTemplate;
    }

    @Bean(destroyMethod = "destroy")
    public RedisMessageListenerContainer redisMessageListenerContainer(RedisConnectionFactory factory,
                                                                       MessageListener redisMessageListener) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(factory);

        //可以添加多个 messageListener
        container.addMessageListener(redisMessageListener, new PatternTopic("index"));

        return container;
    }
}
```

##### RedisSub

redis发布配置

```java
package com.lt.push.config;

import com.alibaba.fastjson.JSONObject;
import com.corundumstudio.socketio.SocketIOClient;
import com.lt.push.entity.PushMsgEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.UUID;

/**
 * @author litong
 * @date 2019/10/17 15:33
 */
@Service(value = "redisMessageListener")
@Slf4j
public class RedisSub implements MessageListener {
    @Resource
    private ClientCache clientCache;
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String msgString = (String) redisTemplate.getValueSerializer().deserialize(message.getBody());
        PushMsgEntity pushMsgEntity = JSONObject.parseObject(msgString, PushMsgEntity.class);
        String channel = (String) redisTemplate.getValueSerializer().deserialize(message.getChannel());

        if (!StringUtils.isEmpty(channel) && !StringUtils.isEmpty(pushMsgEntity)) {
            // 向客户端推送消息
            HashMap<UUID, SocketIOClient> userClient = clientCache.getUserClient(pushMsgEntity.getUid());
            if (userClient != null & !userClient.isEmpty()) {
                userClient.forEach((uuid, socketIOClient) -> {
                    socketIOClient.sendEvent("chatevent", pushMsgEntity.getMessage());
                });
            }
        }

    }
}
```

#### com.lt.push/controller

##### PushMsgReq

Req请求类

```java
package com.lt.push.controller.request;

import lombok.Data;

/**
 * @author litong
 * @date 2019/11/13 16:44
 */
@Data
public class PushMsgReq {
    private String uid;
    private String msg;
}

```

##### PushController

推送消息控制层

```java
package com.lt.push.controller;

import com.alibaba.fastjson.JSON;
import com.lt.push.controller.request.PushMsgReq;
import com.lt.push.entity.PushMsgEntity;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author litong
 * @date 2019/11/6 16:02
 */
@RestController
@RequestMapping("/push")
@AllArgsConstructor
@Slf4j
public class PushController {

    private RedisTemplate redisTemplate;


    @PostMapping("/user")
    public String pushUser(@RequestBody PushMsgReq pushMsgReq) {
        // 接受消息，存储到MongoDB中，
        // 发布到redis中
        PushMsgEntity pushMsgEntity = PushMsgEntity.builder()
                .uid(pushMsgReq.getUid()).message(pushMsgReq.getMsg()).build();
        redisTemplate.convertAndSend("index", JSON.toJSONString(pushMsgEntity));
        return "success";
    }
}

```

#### entity

##### PushMsgEntity

推送实体类

```java
package com.lt.push.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author litong
 * @date 2019/11/12 16:24
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PushMsgEntity {
    private String uid;
    private String message;

}

```

#### util

##### R

返回数据格式类

```java
package com.lt.push.util;

import lombok.Data;

/**
 * @author litong
 * @date 2019/11/10 15:15
 */
@Data
public class R<T> {

    private Integer code;

    private String msg;

    private T data;

    public static <T> R<T> ok() {
        return restResult(null, 1000, "成功");
    }

    public static <T> R<T> ok(T data) {
        return restResult(data, 1000, null);
    }

    private static <T> R<T> restResult(T data, int code, String msg) {
        R<T> apiResult = new R<>();
        apiResult.setCode(code);
        apiResult.setData(data);
        apiResult.setMsg(msg);
        return apiResult;
    }
}

```

#### LtPushApplication

启动类

```java
package com.lt.push;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class LtPushApplication {

    public static void main(String[] args) {
        SpringApplication.run(LtPushApplication.class, args);
    }

}

```

#### resources

配置文件

##### application.yml

```java
spring:
  application:
    name: lt-push
  profiles:
    active: local

```

##### application-local.yml

```java
server:
  port: 8081

spring:
  redis:
    host: localhost
    port: 6379
    password:
# 推送服务、地址端口
push:
  server:
    host: localhost
    port: 8082
```



## 三.客户端

使用网页模拟，可自行修改客户端代码，模拟多用户

为了方便，socket.io.js，我直接使用的线上的，可自行下载后放到项目中

端口可根据实际进行修改，在控制台查看推送信息



```html
<script src="https://cdn.bootcss.com/socket.io/2.1.1/socket.io.js"></script>

<script type="text/javascript">
    // 此处userId为用户标识，后面推送时使用
    var socket = io.connect('http://localhost:8082?userId=1', {
        'reconnection delay' : 2000,
        'force new connection' : true
    });

    socket.on('message', function(data) {
        // here is your handler on messages from server
        console.log(data)
    });

    socket.on('chatevent', function(data) {
        // here is your handler on chatevent from server
        console.log(data)
    });

    socket.on('connect', function() {
        // connection established, now we can send an objects
        
        // send json-object to server
        // '@class' property should be defined and should
        // equals to full class name.
        var obj = { '@class' : 'com.sample.SomeClass'
        }
        socket.json.send(obj);

        // send event-object to server
        // '@class' property is NOT necessary in this case
        var event = {

        }
        socket.emit('someevent', event);

    });

</script>

```

## 四.测试

模拟集群：启动多个Java服务器，端口不一样即可

模拟多客户：副本多个页面，配置ip不一样即可

接口测试用例
```
POST http://127.0.0.1:8083/push/user
Accept: application/json
Content-Type: application/json

{
"uid": "2",
"msg": "这是服务1给2发的"
}
```

## 五.源码下载
[GitHub地址](https://github.com/LitongZero/lt-push)
觉得不错可以给我点个star/赞。

###### 小小赞助，谢谢！
![在这里插入图片描述](https://img-blog.csdnimg.cn/20190913113709828.png)
