package com.victor.async.stream;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

import com.victor.async.AsyncServer;
import com.victor.async.ByteBufferList;
import com.victor.async.DataSink;
import com.victor.async.callback.CompletedCallback;
import com.victor.async.callback.WritableCallback;

public class OutputStreamDataSink implements DataSink {
    public OutputStreamDataSink(AsyncServer server) {
        this(server, null);
    }

    @Override
    public void end() {
        try {
            if (mStream != null)
                mStream.close();
            reportClose(null);
        }
        catch (IOException e) {
            reportClose(e);
        }
    }

    @Override
    public void close() {
        end();
    }

    AsyncServer server;
    public OutputStreamDataSink(AsyncServer server, OutputStream stream) {
        this.server = server;
        setOutputStream(stream);
    }

    OutputStream mStream;
    public void setOutputStream(OutputStream stream) {
        mStream = stream;
    }
    
    public OutputStream getOutputStream() throws IOException {
        return mStream;
    }

    @Override
    public void write(final ByteBuffer bb) {
        try {
            getOutputStream().write(bb.array(), bb.arrayOffset() + bb.position(), bb.remaining());
        }
        catch (IOException e) {
            reportClose(e);
        }
        bb.position(0);
        bb.limit(0);
    }

    @Override
    public void write(final ByteBufferList bb) {
        try {
            while (bb.size() > 0) {
                ByteBuffer b = bb.remove();
                getOutputStream().write(b.array(), b.arrayOffset() + b.position(), b.remaining());
                ByteBufferList.reclaim(b);
            }
        }
        catch (IOException e) {
            reportClose(e);
        }
        finally {
            bb.recycle();
        }
    }

    WritableCallback mWritable;
    @Override
    public void setWriteableCallback(WritableCallback handler) {
        mWritable = handler;        
    }

    @Override
    public WritableCallback getWriteableCallback() {
        return mWritable;
    }

    @Override
    public boolean isOpen() {
        return closeReported;
    }

    boolean closeReported;
    Exception closeException;
    public void reportClose(Exception ex) {
        if (closeReported)
            return;
        closeReported = true;
        closeException = ex;

        if (mClosedCallback != null)
            mClosedCallback.onCompleted(closeException);
    }
    
    CompletedCallback mClosedCallback;
    @Override
    public void setClosedCallback(CompletedCallback handler) {
        mClosedCallback = handler;        
    }

    @Override
    public CompletedCallback getClosedCallback() {
        return mClosedCallback;
    }

    @Override
    public AsyncServer getServer() {
        return server;
    }

    WritableCallback outputStreamCallback;
    public void setOutputStreamWritableCallback(WritableCallback outputStreamCallback) {
        this.outputStreamCallback = outputStreamCallback;
    }
}
