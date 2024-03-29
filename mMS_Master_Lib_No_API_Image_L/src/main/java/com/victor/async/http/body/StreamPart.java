package com.victor.async.http.body;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.apache.http.NameValuePair;

import com.victor.async.DataSink;
import com.victor.async.callback.CompletedCallback;

public abstract class StreamPart extends Part {
    public StreamPart(String name, long length, List<NameValuePair> contentDisposition) {
        super(name, length, contentDisposition);
    }
    
    @Override
    public void write(DataSink sink, CompletedCallback callback) {
        try {
            InputStream is = getInputStream();
            com.victor.async.Util.pump(is, sink, callback);
        }
        catch (Exception e) {
            callback.onCompleted(e);
        }
    }
    
    protected abstract InputStream getInputStream() throws IOException;
}
