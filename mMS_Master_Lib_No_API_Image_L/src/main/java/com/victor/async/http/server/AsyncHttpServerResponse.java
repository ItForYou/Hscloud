package com.victor.async.http.server;

import java.io.File;
import java.io.InputStream;

import org.json.JSONObject;

import com.victor.async.AsyncSocket;
import com.victor.async.DataSink;
import com.victor.async.callback.CompletedCallback;
import com.victor.async.http.libcore.ResponseHeaders;

public interface AsyncHttpServerResponse extends DataSink, CompletedCallback {
    public void end();
    public void send(String contentType, String string);
    public void send(String string);
    public void send(JSONObject json);
    public void sendFile(File file);
    public void sendStream(InputStream inputStream, long totalLength);
    public void responseCode(int code);
    public ResponseHeaders getHeaders();
    public void writeHead();
    public void setContentType(String contentType);
    public void redirect(String location);
    /**
     * Alias for end. Used with CompletedEmitters
     */
    public void onCompleted(Exception ex);
    public AsyncSocket getSocket();
}
