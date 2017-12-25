package com.home.dab.okiosocket;

import android.util.Log;

import java.nio.charset.Charset;
import java.text.MessageFormat;

/**
 * Created by dab on 2017/9/12 0012 14:10
 */

public class Configs {
    public static boolean deBug = true;
    public static String myTag = "****";
    public static long reConnectedTime = 4000;//重连间隔
    public static int connectTimeout = 8000;//连接超时时间
    public static long readTimeout = Long.MAX_VALUE;//读超时时间
    public static long writeTimeout = 10_000;//写超时时间
    public static Charset charset = Charset.forName("UTF-8");
    public static String HOST;
    public static int PORT;


    public static class MessageWhat {
        /**
         * 是否连接
         */
        static final int CONNECTED = 0x01;
        /**
         * 重新连接
         */
        static final int RE_CONNECTED = 0x02;
        /**
         * 数据
         */
        static final int DATA = 0x03;
    }

    public static void e(String message) {
        if (!deBug) {
            return;
        }
        StackTraceElement[] stackTrace = new Throwable().getStackTrace();
        String className = stackTrace[1].getFileName();
        String methodName = stackTrace[1].getMethodName();
        int lineNumber = stackTrace[1].getLineNumber();
        Log.e(className, MessageFormat.format("({0}:{1}){2}{3}", className, lineNumber, myTag, message));
    }

}
