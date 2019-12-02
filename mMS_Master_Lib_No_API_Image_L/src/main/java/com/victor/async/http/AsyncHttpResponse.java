package com.victor.async.http;

import com.victor.async.AsyncSocket;
import com.victor.async.DataEmitter;
import com.victor.async.DataSink;
import com.victor.async.callback.CompletedCallback;
import com.victor.async.http.libcore.ResponseHeaders;

public interface AsyncHttpResponse extends AsyncSocket {
    public void setEndCallback(CompletedCallback handler);
    public CompletedCallback getEndCallback();
    public ResponseHeaders getHeaders();
    public void end();
    public AsyncSocket detachSocket();
    public AsyncHttpRequest getRequest();
}
