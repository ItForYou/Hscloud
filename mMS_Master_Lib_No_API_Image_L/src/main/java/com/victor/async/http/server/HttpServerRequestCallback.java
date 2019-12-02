package com.victor.async.http.server;


public interface HttpServerRequestCallback {
    public void onRequest(AsyncHttpServerRequest request, AsyncHttpServerResponse response);
}
