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
            if (userClient != null && !userClient.isEmpty()) {
                userClient.forEach((uuid, socketIOClient) -> {
                    socketIOClient.sendEvent("chatevent", pushMsgEntity.getMessage());
                });
            }
        }

    }
}
