package com.victor.async.http.socketio.transport;

import android.net.Uri;
import android.net.Uri.Builder;

import com.victor.async.AsyncServer;
import com.victor.async.callback.CompletedCallback;
import com.victor.async.http.AsyncHttpClient;
import com.victor.async.http.AsyncHttpGet;
import com.victor.async.http.AsyncHttpPost;
import com.victor.async.http.AsyncHttpRequest;
import com.victor.async.http.AsyncHttpResponse;
import com.victor.async.http.body.StringBody;

public class XHRPollingTransport implements SocketIOTransport {
    private AsyncHttpClient client;
    private Uri sessionUrl;
    private StringCallback stringCallback;
    private CompletedCallback closedCallback;
    private boolean connected;

    private static final String SEPARATOR = "\ufffd";

    public XHRPollingTransport(AsyncHttpClient client, String sessionUrl) {
        this.client = client;
        this.sessionUrl = Uri.parse(sessionUrl);

        doLongPolling();
        connected = true;
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public void setClosedCallback(CompletedCallback handler) {
        closedCallback = handler;
    }

    @Override
    public void disconnect() {
        connected = false;
        close(null);
    }

    private void close(Exception ex) {
        if (closedCallback != null)
            closedCallback.onCompleted(ex);
    }

    @Override
    public AsyncServer getServer() {
        return client.getServer();
    }

    @Override
    public void send(String message) {
        if (message.startsWith("5")) {
            postMessage(message);
            return;
        }

        AsyncHttpRequest request = new AsyncHttpPost(computedRequestUrl());
        request.setBody(new StringBody(message));

        client.executeString(request, new AsyncHttpClient.StringCallback() {
            @Override
            public void onCompleted(Exception e, AsyncHttpResponse source, String result) {
                if (e != null) {
                    close(e);
                    return;
                }

                sendResult(result);
            }
        });
    }

    private void postMessage(String message) {
        if (!message.startsWith("5"))
            return;

        AsyncHttpRequest request = new AsyncHttpPost(computedRequestUrl());
        request.setBody(new StringBody(message));
        client.executeString(request, null);
    }

    private void doLongPolling() {
        this.client.executeString(new AsyncHttpGet(computedRequestUrl()), new AsyncHttpClient.StringCallback() {
            @Override
            public void onCompleted(Exception e, AsyncHttpResponse source, String result) {
                if (e != null) {
                    close(e);
                    return;
                }

                sendResult(result);
                doLongPolling();
            }
        });
    }

    private void sendResult(String result) {
        if (stringCallback == null)
            return;

        if (!result.contains(SEPARATOR)) {
            stringCallback.onStringAvailable(result);
            return;
        }

        String [] results = result.split(SEPARATOR);
        for (int i = 1; i < results.length; i = i + 2) {
            stringCallback.onStringAvailable(results[i+1]);
        }
    }

    /**
     * Return an url with a time-based parameter to avoid caching issues
     */
    private String computedRequestUrl() {
        String currentTime = String.valueOf(System.currentTimeMillis());
        return sessionUrl.buildUpon().appendQueryParameter("t", currentTime)
                .build().toString();
    }

    @Override
    public void setStringCallback(StringCallback callback) {
        stringCallback = callback;
    }

    @Override
    public boolean heartbeats() {
        return false;
    }
}
