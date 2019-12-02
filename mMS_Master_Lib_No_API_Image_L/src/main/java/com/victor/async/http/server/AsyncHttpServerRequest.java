package com.victor.async.http.server;

import com.victor.async.AsyncSocket;
import com.victor.async.DataEmitter;
import com.victor.async.http.body.AsyncHttpRequestBody;
import com.victor.async.http.Multimap;
import com.victor.async.http.libcore.RequestHeaders;

import java.util.regex.Matcher;

public interface AsyncHttpServerRequest extends DataEmitter {
    public RequestHeaders getHeaders();
    public Matcher getMatcher();
    public AsyncHttpRequestBody getBody();
    public AsyncSocket getSocket();
    public String getPath();
    public Multimap getQuery();
    public String getMethod();
}
