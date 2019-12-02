package com.victor.async.http.body;

import com.victor.async.DataEmitter;
import com.victor.async.DataSink;
import com.victor.async.callback.CompletedCallback;
import com.victor.async.http.AsyncHttpRequest;

public interface AsyncHttpRequestBody<T> {
    public void write(AsyncHttpRequest request, DataSink sink, CompletedCallback completed);
    public void parse(DataEmitter emitter, CompletedCallback completed);
    public String getContentType();
    public boolean readFullyOnRequest();
    public int length();
    public T get();
}
