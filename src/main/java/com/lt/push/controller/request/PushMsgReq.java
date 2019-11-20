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
