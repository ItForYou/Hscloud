package com.victor.async.http.body;

import com.victor.async.DataSink;
import com.victor.async.callback.CompletedCallback;
import com.victor.async.http.Multimap;
import com.victor.async.http.libcore.RawHeaders;
import org.apache.http.NameValuePair;

import java.io.File;
import java.util.List;
import java.util.Map;

public class Part {
    public static final String CONTENT_DISPOSITION = "Content-Disposition";
    
    RawHeaders mHeaders;
    Multimap mContentDisposition;
    public Part(RawHeaders headers) {
        mHeaders = headers;
        mContentDisposition = Multimap.parseHeader(mHeaders, CONTENT_DISPOSITION);
    }
    
    public String getName() {
        return mContentDisposition.getString("name");
    }
    
    private long length = -1;
    public Part(String name, long length, List<NameValuePair> contentDisposition) {
        this.length = length;
        mHeaders = new RawHeaders();
        StringBuilder builder = new StringBuilder(String.format("form-data; name=\"%s\"", name));
        if (contentDisposition != null) {
            for (NameValuePair pair: contentDisposition) {
                builder.append(String.format("; %s=\"%s\"", pair.getName(), pair.getValue()));
            }
        }
        mHeaders.set(CONTENT_DISPOSITION, builder.toString());
        mContentDisposition = Multimap.parseHeader(mHeaders, CONTENT_DISPOSITION);
    }

    public RawHeaders getRawHeaders() {
        return mHeaders;
    }

    public String getContentType() {
        return mHeaders.get("Content-Type");
    }

    public void setContentType(String contentType) {
        mHeaders.set("Content-Type", contentType);
    }

    public String getFilename() {
        String file = mContentDisposition.getString("filename");
        if (file == null)
            return null;
        return new File(file).getName();
    }

    public boolean isFile() {
        return mContentDisposition.containsKey("filename");
    }
    
    public long length() {
        return length;
    }
    
    public void write(DataSink sink, CompletedCallback callback) {
        assert false;
    }
}
