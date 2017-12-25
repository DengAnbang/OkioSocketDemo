package com.home.dab.okiosocket;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okio.BufferedSink;
import okio.BufferedSource;
import okio.Okio;
import okio.Timeout;

/**
 * Created by dab on 2017/9/8 0008 17:05
 */

public class OkioSocket implements Closeable {

    private static final String TAG = "OkioSocket";
    private BufferedSink mSink;
    private BufferedSource mSource;
    private Thread readThread;
    private Thread writeThread;
    private TaskQueue<Request> msgQueue;//消息队列
    private OnConnectCallBack mOnConnectCallBack;
    private OnMessageCome mOnMessageChange;
    private OnEncryption mOnEncryption;
    private OnDecode mOnDecode;
    private boolean canConnected = true;//是否允许连接
    private boolean isConnect;//是否连接成功
    private boolean isConnecting;//连接中
    private boolean isFirstConnected = true;//第一次连接
    private int reConnectedCount;//当前重连次数
    private final Handler mHandler;
    private boolean isClose;// 是否关闭

    /**
     * 设置是否可以连接(马上会尝试连接)
     *
     * @param canConnected
     */
    public OkioSocket setCanConnected(boolean canConnected) {
        this.canConnected = canConnected;
        reConnected();
        return this;
    }

    /**
     * 设置解密
     *
     * @param onDecode
     */
    public OkioSocket setOnDecode(OnDecode onDecode) {
        mOnDecode = onDecode;
        return this;
    }

    /**
     * 设置加密
     *
     * @param onEncryption
     */
    public OkioSocket setOnEncryption(OnEncryption onEncryption) {
        mOnEncryption = onEncryption;
        return this;
    }

    public OkioSocket setCharset(@NonNull Charset charset) {
        Configs.charset = charset;
        return this;
    }

    /**
     * 设置连接的监听
     *
     * @param onConnectCallBack
     * @return
     */
    public OkioSocket setOnConnectCallBack(OnConnectCallBack onConnectCallBack) {
        mOnConnectCallBack = onConnectCallBack;
        return this;
    }

    /**
     * 设置消息的监听
     *
     * @param onMessageChange
     * @return
     */
    public OkioSocket setOnMessageChange(OnMessageCome onMessageChange) {
        mOnMessageChange = onMessageChange;
        return this;
    }

    /**
     * 设置重新连接的间隔(ms) 最少1000,否则为默认4000
     *
     * @param reConnectedTime
     */
    public OkioSocket setReConnectedTime(long reConnectedTime) {
        if (reConnectedTime >= 1000) {
            Configs.reConnectedTime = reConnectedTime;
        }
        return this;
    }

    /**
     * 设置连接Socket超时的时间(ms),最少1000,否则为默认8000
     *
     * @param connectTimeout
     */
    public OkioSocket setConnectTimeout(int connectTimeout) {
        if (connectTimeout >= 1000) {
            Configs.connectTimeout = connectTimeout;
        }
        return this;
    }

    /**
     * 设置读的超时(读超时以后,暂时会被当成断开连接处理,以后需要了再修改)
     *
     * @param readTimeout
     */
    private OkioSocket setReadTimeout(long readTimeout) {
        if (readTimeout >= 1000) {
            Configs.readTimeout = readTimeout;
        }
        return this;
    }

    /**
     * 设置写的超时(写超时以后,会放弃这个任务,并且会回调OnSendCallBack,如果发送消息时设置了的话)
     *
     * @param writeTimeout
     */
    public OkioSocket setWriteTimeout(long writeTimeout) {
        if (writeTimeout >= 1000) {
            Configs.writeTimeout = writeTimeout;
        }
        return this;
    }

    @SuppressLint("HandlerLeak")
    public OkioSocket() {
        msgQueue = new TaskQueue<>();
        mHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                switch (msg.what) {
                    case Configs.MessageWhat.CONNECTED:
                        if (mOnConnectCallBack != null) {
                            boolean isConnected = msg.getData().getBoolean("isConnected", false);
                            IOException err = (IOException) msg.getData().getSerializable("err");
                            mOnConnectCallBack.onConnect(isConnected, isFirstConnected, err);
                        }
                        break;
                    case Configs.MessageWhat.RE_CONNECTED:
                        reConnected();
                        //重连
                        break;
                    case Configs.MessageWhat.DATA:
                        byte[] data = msg.getData().getByteArray("data");
                        if (data == null) {
                            Configs.e("data ==nul");
                            return;
                        }
                        if (mOnDecode != null) {
                            data = mOnDecode.onDecode(data);
                        }
                        String message = new String(data, Configs.charset);
                        Configs.e("<<<<<<<<" + message);
                        if (mOnMessageChange != null) {
                            mOnMessageChange.onMessageCome(message);
                        }
                        break;
                }
            }
        };
    }

    /**
     * 链接,如果已经在连接成功了或者正在连接中,就不再会连接接了
     *
     * @param host
     * @param port
     */
    public void connect(@NonNull String host, int port) {
        if (!isConnect && !isConnecting) {
            isClose = false;
            connectSocket(host, port);
        }
    }

    private void connectSocket(@NonNull String host, int port) {
        Configs.HOST = host;
        Configs.PORT = port;
        synchronized (OkioSocket.class) {
            if (isConnecting) return;
            isConnecting = true;
            //这里应该可以用线程池来减少多次重连的创建县线程
            readThread = new Thread(new connectService());
            readThread.setName("readThread:" + readThread.getId());
            readThread.start();

        }

    }

    /**
     * 重新链接
     */
    public void reConnected() {
        Configs.e("尝试连接" +
                "\n连接断开了:" + !isConnect +
                "\n没有正在重连:" + !isConnecting +
                "\n没有关闭:" + !isClose +
                "\n允许重连:" + canConnected);
        if (isConnect) return;
        if (isConnecting) return;
        if (isClose) return;
        if (!canConnected) return;
        connectSocket(Configs.HOST, Configs.PORT);
    }

    /**
     * 发送一个消息(添加到队列),不关心发送成功或者失败
     *
     * @param sendMsg
     */
    public void send(@NonNull String sendMsg) {
        send(sendMsg, null);
    }

    /**
     * 发送一个消息(添加到队列,并且监听发送成功或者失败)
     *
     * @param sendMsg
     */
    @Nullable
    public Request send(@NonNull String sendMsg, @Nullable OnSendCallBack onSendCallBack) {
        if (isClose) {
            if (onSendCallBack != null) {
                onSendCallBack.onSendCallBack(new RuntimeException("已经关闭或者未链接成功"));
            }
            return null;
        }
        Request request = new Request(sendMsg, onSendCallBack);
        msgQueue.addTask(request);
        if (writeThread == null) {
            writeThread = new Thread(new sendService());
            writeThread.setName("writeThread:" + writeThread.getId());
            writeThread.start();
        }
        return request;
    }

    /**
     * 移除请求
     *
     * @param requests
     */
    public void remove(Request... requests) {
        if (requests == null) return;
        for (Request request : requests) {
            request.clear();
            msgQueue.remove(request);
        }
    }

    /**
     * 移除请求
     *
     * @param requests
     */
    public void remove(List<Request> requests) {
        if (requests == null) return;
        for (Request request : requests) {
            request.clear();
            msgQueue.remove(request);
        }
    }

    @Override
    public void close() throws IOException {
        isClose = true;
        isConnect = false;
        msgQueue.clear();
        setCanConnected(false);
        if (mHandler != null) {
            mHandler.removeCallbacksAndMessages(null);
        }
        if (mSink != null) {
            mSink.close();
        }
        if (mSource != null) {
            mSource.close();
        }

    }


    /**
     * 链接和读消息
     */
    private class connectService implements Runnable {
        @Override
        public void run() {
            try {
                Configs.e("正在连接:" + Thread.currentThread().getName());
                Socket socket = new Socket();
                socket.connect(new InetSocketAddress(Configs.HOST, Configs.PORT), Configs.connectTimeout);
                mSink = Okio.buffer(Okio.sink(socket));
                mSource = Okio.buffer(Okio.source(socket));
//                initSink(mSink);
//                initSource(mSource);
                sendMessage(Configs.MessageWhat.CONNECTED, bundle -> {
                    bundle.putBoolean("isConnected", socket.isConnected());
                    return bundle;
                });
                isConnecting = false;
                isFirstConnected = false;
                reConnectedCount = 0;
                isConnect = true;
                receiveMsg();
            } catch (IOException e) {
                e.printStackTrace();
                isConnecting = false;
                isConnect = false;
                sendMessage(Configs.MessageWhat.CONNECTED, bundle -> {
                    bundle.putSerializable("err", e);
                    return bundle;
                });
                sendReConnectedMessage();
                reConnectedCount++;
                Configs.e("当前重连次数" + reConnectedCount);
            }
        }
    }

    /**
     * 初始化Source
     *
     * @param source
     */
    private void initSource(BufferedSource source) {
        if (source == null) return;
        if (Configs.readTimeout < 1000) return;
        Timeout timeout = source.timeout();
        timeout.deadline(Configs.readTimeout, TimeUnit.MILLISECONDS);
    }

    /**
     * 初始化Sink
     *
     * @param sink
     */
    private void initSink(BufferedSink sink) {
        if (sink == null) return;
        if (Configs.writeTimeout < 1000) return;
        Timeout timeout = sink.timeout();
        timeout.deadline(Configs.writeTimeout, TimeUnit.MILLISECONDS);
    }

    /**
     * 读消息
     */
    private void receiveMsg() {
        Configs.e("链接成功:" + Thread.currentThread().getName());
//        send("dasdasdsadada");
        try {
            Timeout timeout = mSource.timeout();
            while (!isClose) {
                timeout.clearDeadline();

                int length = mSource.readInt();
                Log.e(TAG, "receiveMsg555555555: "+length );
                timeout.deadline(Configs.readTimeout, TimeUnit.MILLISECONDS);
                byte[] bytes = mSource.readByteArray(length);
                Log.e(TAG, "receiveMsg: "+ Arrays.toString(bytes));
                sendMessage(Configs.MessageWhat.DATA, bundle -> {
                    bundle.putByteArray("data", bytes);
                    return bundle;
                });
            }
        } catch (IOException e) {
            e.printStackTrace();
            sendMessage(Configs.MessageWhat.CONNECTED, bundle -> {
                bundle.putSerializable("err", e);
                return bundle;
            });
            isConnect = false;
            if (e instanceof SocketException || e instanceof SocketTimeoutException) {
                sendReConnectedMessage();
            }
        }
    }

    /**
     * 发送消息
     */
    private class sendService implements Runnable {
        @Override
        public void run() {
            while (!isClose && mSink != null) {
                Request request = msgQueue.popTask();
                if (request == null) {
                    Configs.e(">>>>>>>>>> requestSucceed is null");
                    continue;
                }
                OnSendCallBack onSendCallBack = request.getOnSendCallBack();
                try {
                    String msg = request.getMsg();
                    byte[] bytes = msg.getBytes();
                    if (mOnEncryption != null) {
                        bytes = mOnEncryption.onEncryption(bytes);
                    }
                    mSink.writeInt(bytes.length);
                    mSink.write(bytes);
                    mSink.flush();
                    Configs.e("ThreadName:" + Thread.currentThread().getName() + ">>>>>>>>>>" + msg);
                    if (onSendCallBack != null) {
                        onSendCallBack.onSendCallBack(null);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    if (onSendCallBack != null) {
                        onSendCallBack.onSendCallBack(e);
                    }
                    sendMessage(Configs.MessageWhat.CONNECTED, bundle -> {
                        bundle.putSerializable("err", e);
                        return bundle;
                    });
                    if (e instanceof SocketException) {
                        isConnect = false;
                        sendReConnectedMessage();
                    }
                }
            }

        }
    }

    /**
     * 发送一个消息
     *
     * @param what
     * @return
     */
    private void sendMessage(int what, @NonNull OnSetData onSetData) {
        Message message = mHandler.obtainMessage();
        message.what = what;
        Bundle bundle = new Bundle();
        message.setData(onSetData.setData(bundle));
        mHandler.sendMessage(message);
    }
    ////////////////////////////////////////////////////////////////////////////////////////////////////
    //定义接口

    /**
     * 发送消息
     */
    interface OnSendCallBack {
        void onSendCallBack(@Nullable Exception e);
    }

    /**
     * 发送一个重连的消息
     */
    private void sendReConnectedMessage() {
        Message msg = mHandler.obtainMessage();
        msg.what = Configs.MessageWhat.RE_CONNECTED;
        mHandler.sendMessageDelayed(msg, Configs.reConnectedTime);
    }
    /**
     * 发送一个连接的消息
     */
    private void sendConnectedMessage() {
        Message msg = mHandler.obtainMessage();
        msg.what = Configs.MessageWhat.CONNECTED;
        mHandler.sendMessageDelayed(msg, Configs.reConnectedTime);
    }

    public interface OnSetData {
        /**
         * 内部通讯
         *
         * @param bundle
         * @return
         */
        Bundle setData(Bundle bundle);
    }

    public interface OnConnectCallBack {
        /**
         * @param succeed          是否连接成功
         * @param isFirstConnected 是否是第一次连接,false表示重新连接的
         * @param e
         */
        void onConnect(boolean succeed, boolean isFirstConnected, @Nullable Exception e);
    }

    public interface OnEncryption {
        byte[] onEncryption(byte[] original);
    }

    public interface OnDecode {
        byte[] onDecode(byte[] original);
    }


    public interface OnMessageCome {
        void onMessageCome(String msg);
    }
}
