package com.victor.async.callback;

import com.victor.async.ByteBufferList;
import com.victor.async.DataEmitter;


public interface DataCallback {
    public void onDataAvailable(DataEmitter emitter, ByteBufferList bb);
}
