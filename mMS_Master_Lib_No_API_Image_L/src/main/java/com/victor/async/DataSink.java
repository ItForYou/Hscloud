package com.victor.async;

import java.nio.ByteBuffer;

import com.victor.async.callback.CompletedCallback;
import com.victor.async.callback.WritableCallback;

public interface DataSink {
    public void write(ByteBuffer bb);
    public void write(ByteBufferList bb);
    public void setWriteableCallback(WritableCallback handler);
    public WritableCallback getWriteableCallback();
    
    public boolean isOpen();
    public void close();
    public void end();
    public void setClosedCallback(CompletedCallback handler);
    public CompletedCallback getClosedCallback();
    public AsyncServer getServer();
}
