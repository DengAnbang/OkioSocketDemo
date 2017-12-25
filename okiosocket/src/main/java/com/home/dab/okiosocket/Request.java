package com.home.dab.okiosocket;

/**
 * Created by dab on 2017/9/27 0027 09:45
 */

public class Request {

    private String msg;
    private OkioSocket.OnSendCallBack mOnSendCallBack;

    public Request(String msg, OkioSocket.OnSendCallBack onSendCallBack) {
        this.msg = msg;
        mOnSendCallBack = onSendCallBack;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public OkioSocket.OnSendCallBack getOnSendCallBack() {
        return mOnSendCallBack;
    }

    public void setOnSendCallBack(OkioSocket.OnSendCallBack onSendCallBack) {
        mOnSendCallBack = onSendCallBack;
    }

    /**
     * 清除掉回调,防止内存泄露
     */
    public void clear() {
        mOnSendCallBack = null;
    }
}
