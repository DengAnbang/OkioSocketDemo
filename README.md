# OkioSocketDemo
一个基于okio的Socket连接框架,实现自动连接
### 简单用法
1. ```new OkioSocket()```初始化。
2. 使用```mOkioSocket.connect("192.168.1.3",9091)```表示连接
3. 使用```mOkioSocket.send("message")```发送一条消息,或者使用```send(String sendMsg, OnSendCallBack onSendCallBack)```方法发送,这个方法可以监听到发送成功或者失败,如果成功,则接口中的异常为nil,
否则就是发送失败的异常
4. 使用```mOkioSocket.setOnMessageChange```接口,可以监听到接受到的消息
5. 在关闭的时候,调用```mOkioSocket.close``` 关闭连接

