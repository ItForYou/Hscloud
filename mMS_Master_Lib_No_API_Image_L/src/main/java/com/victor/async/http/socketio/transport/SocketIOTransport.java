package com.victor.async.http.socketio.transport;

import com.victor.async.AsyncServer;
import com.victor.async.callback.CompletedCallback;

/**
 * A socket.io transport.
 *
 * Please, refer to the documentation in https://github.com/LearnBoost/socket.io-spec
 */
public interface SocketIOTransport {
    static public interface StringCallback {
        public void onStringAvailable(String s);
    }

    /**
     * Send message to the server
     * @param string
     */
    public void send(String string);

    /**
     * Close connection
     */
    public void disconnect();

    public void setStringCallback(StringCallback callback);
    public void setClosedCallback(CompletedCallback handler);

    public AsyncServer getServer();
    public boolean isConnected();

    /**
     * Indicates whether heartbeats are enabled for this transport
     * @return
     */
    public boolean heartbeats();
}
