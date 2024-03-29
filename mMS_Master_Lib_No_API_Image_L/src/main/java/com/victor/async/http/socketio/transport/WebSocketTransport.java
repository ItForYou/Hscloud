package com.victor.async.http.socketio.transport;

import com.victor.async.AsyncServer;
import com.victor.async.NullDataCallback;
import com.victor.async.callback.CompletedCallback;
import com.victor.async.http.WebSocket;

public class WebSocketTransport implements SocketIOTransport {
    private WebSocket webSocket;
    private StringCallback stringCallback;

    public WebSocketTransport(WebSocket webSocket) {
        this.webSocket = webSocket;

        this.webSocket.setDataCallback(new NullDataCallback());
    }

    @Override
    public boolean isConnected() {
        return this.webSocket.isOpen();
    }

    @Override
    public void setClosedCallback(CompletedCallback handler) {
        this.webSocket.setClosedCallback(handler);
    }

    @Override
    public void disconnect() {
        this.webSocket.close();
    }

    @Override
    public AsyncServer getServer() {
        return this.webSocket.getServer();
    }

    @Override
    public void send(String message) {
        this.webSocket.send(message);
    }

    @Override
    public void setStringCallback(final StringCallback callback) {
        if (this.stringCallback == callback)
            return;

        if (callback == null) {
            this.webSocket.setStringCallback(null);
        } else {
            this.webSocket.setStringCallback(new WebSocket.StringCallback() {
                @Override
                public void onStringAvailable(String s) {
                    callback.onStringAvailable(s);
                }
            });
        }

        this.stringCallback = callback;
    }

    @Override
    public boolean heartbeats() {
        return true;
    }
}
