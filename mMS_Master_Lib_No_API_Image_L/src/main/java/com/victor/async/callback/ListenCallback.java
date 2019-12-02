package com.victor.async.callback;

import com.victor.async.AsyncServerSocket;
import com.victor.async.AsyncSocket;


public interface ListenCallback extends CompletedCallback {
    public void onAccepted(AsyncSocket socket);
    public void onListening(AsyncServerSocket socket);
}
