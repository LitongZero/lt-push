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
