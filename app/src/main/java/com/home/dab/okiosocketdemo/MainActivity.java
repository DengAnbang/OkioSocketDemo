package com.home.dab.okiosocketdemo;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;

import com.home.dab.okiosocket.OkioSocket;

public class MainActivity extends AppCompatActivity {
    OkioSocket mOkioSocket;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mOkioSocket = new OkioSocket();
        mOkioSocket.connect("192.168.1.3",9091);
        findViewById(R.id.tv_send).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mOkioSocket.send("服务器");
            }
        });

    }
}
