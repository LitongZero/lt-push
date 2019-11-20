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
