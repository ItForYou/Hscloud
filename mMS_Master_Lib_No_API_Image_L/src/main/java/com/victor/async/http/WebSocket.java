package com.victor.async.http;

import com.victor.async.AsyncSocket;


public interface WebSocket extends AsyncSocket {
    static public interface StringCallback {
        public void onStringAvailable(String s);
    }
    static public interface PongCallback {
        public void onPongReceived(String s);
    }

    public void send(byte[] bytes);
    public void send(String string);
    public void send(byte [] bytes, int offset, int len);
    public void ping(String message);
    
    public void setStringCallback(StringCallback callback);
    public StringCallback getStringCallback();

    public void setPongCallback(PongCallback callback);
    public PongCallback getPongCallback();

    public boolean isBuffering();
    
    public AsyncSocket getSocket();
}
