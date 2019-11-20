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
