package com.victor.async.http.body;

import com.victor.async.ByteBufferList;
import com.victor.async.DataEmitter;
import com.victor.async.DataSink;
import com.victor.async.LineEmitter;
import com.victor.async.LineEmitter.StringCallback;
import com.victor.async.NullDataCallback;
import com.victor.async.callback.CompletedCallback;
import com.victor.async.callback.ContinuationCallback;
import com.victor.async.callback.DataCallback;
import com.victor.async.future.Continuation;
import com.victor.async.http.AsyncHttpRequest;
import com.victor.async.http.Multimap;
import com.victor.async.http.libcore.RawHeaders;
import com.victor.async.http.server.BoundaryEmitter;

import java.io.File;
import java.util.ArrayList;
import java.util.UUID;

public class MultipartFormDataBody extends BoundaryEmitter implements AsyncHttpRequestBody<Multimap> {
    LineEmitter liner;
    RawHeaders formData;
    ByteBufferList last;
    String lastName;

    public interface MultipartCallback {
        public void onPart(Part part);
    }

    @Override
    public void parse(DataEmitter emitter, final CompletedCallback completed) {
        setDataEmitter(emitter);
        setEndCallback(completed);
    }

    void handleLast() {
        if (last == null)
            return;
        
        if (formData == null)
            formData = new RawHeaders();
        
        formData.add(lastName, last.peekString());
        
        lastName = null;
        last = null;
    }
    
    public String getField(String name) {
        if (formData == null)
            return null;
        return formData.get(name);
    }
    
    @Override
    protected void onBoundaryEnd() {
        super.onBoundaryEnd();
        handleLast();
    }
    
    @Override
    protected void onBoundaryStart() {
        final RawHeaders headers = new RawHeaders();
        liner = new LineEmitter();
        liner.setLineCallback(new StringCallback() {
            @Override
            public void onStringAvailable(String s) {
                if (!"\r".equals(s)){
                    headers.addLine(s);
                }
                else {
                    handleLast();
                    
                    liner = null;
                    setDataCallback(null);
                    Part part = new Part(headers);
                    if (mCallback != null)
                        mCallback.onPart(part);
                    if (getDataCallback() == null) {
                        if (part.isFile()) {
                            setDataCallback(new NullDataCallback());
                            return;
                        }

                        lastName = part.getName();
                        last = new ByteBufferList();
                        setDataCallback(new DataCallback() {
                            @Override
                            public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
                                bb.get(last);
                            }
                        });
                    }
                }
            }
        });
        setDataCallback(liner);
    }

    public static final String CONTENT_TYPE = "multipart/form-data";
    String contentType = CONTENT_TYPE;
    public MultipartFormDataBody(String[] values) {
        for (String value: values) {
            String[] splits = value.split("=");
            if (splits.length != 2)
                continue;
            if (!"boundary".equals(splits[0]))
                continue;
            setBoundary(splits[1]);
            return;
        }
        report(new Exception ("No boundary found for multipart/form-data"));
    }

    MultipartCallback mCallback;
    public void setMultipartCallback(MultipartCallback callback) {
        mCallback = callback;
    }
    
    public MultipartCallback getMultipartCallback() {
        return mCallback;
    }

    int written;
    @Override
    public void write(AsyncHttpRequest request, final DataSink sink, final CompletedCallback completed) {
        if (mParts == null) {
            sink.end();
            return;
        }
        
        Continuation c = new Continuation(new CompletedCallback() {
            @Override
            public void onCompleted(Exception ex) {
                completed.onCompleted(ex);
//                if (ex == null)
//                    sink.end();
//                else
//                    sink.close();
            }
        });

        for (final Part part: mParts) {
            c.add(new ContinuationCallback() {
                @Override
                public void onContinue(Continuation continuation, CompletedCallback next) throws Exception {
                    part.getRawHeaders().setStatusLine(getBoundaryStart());
                    byte[] bytes = part.getRawHeaders().toHeaderString().getBytes();
                    com.victor.async.Util.writeAll(sink, bytes, next);
                    written += bytes.length;
                }
            })
            .add(new ContinuationCallback() {
                @Override
                public void onContinue(Continuation continuation, CompletedCallback next) throws Exception {
                    long partLength = part.length();
                    if (partLength >= 0)
                        written += partLength;
                    part.write(sink, next);
                }
            })
            .add(new ContinuationCallback() {
                @Override
                public void onContinue(Continuation continuation, CompletedCallback next) throws Exception {
                    byte[] bytes = "\r\n".getBytes();
                    com.victor.async.Util.writeAll(sink, bytes, next);
                    written += bytes.length;
                }
            });
        }
        c.add(new ContinuationCallback() {
            @Override
            public void onContinue(Continuation continuation, CompletedCallback next) throws Exception {
                byte[] bytes = (getBoundaryEnd()).getBytes();
                com.victor.async.Util.writeAll(sink, bytes, next);
                written += bytes.length;
                
                assert written == totalToWrite;
            }
        });
        c.start();
    }

    @Override
    public String getContentType() {
        if (getBoundary() == null) {
            setBoundary("----------------------------" + UUID.randomUUID().toString().replace("-", ""));
        }
        return CONTENT_TYPE + "; boundary=" + getBoundary();
    }

    @Override
    public boolean readFullyOnRequest() {
        return false;
    }

    int totalToWrite;
    @Override
    public int length() {
        if (getBoundary() == null) {
            setBoundary("----------------------------" + UUID.randomUUID().toString().replace("-", ""));
        }

        int length = 0;
        for (final Part part: mParts) {
            part.getRawHeaders().setStatusLine(getBoundaryStart());
            if (part.length() == -1)
                return -1;
            length += part.length() + part.getRawHeaders().toHeaderString().getBytes().length + "\r\n".length();
        }
        length += (getBoundaryEnd()).getBytes().length;
        return totalToWrite = length;
    }
    
    public MultipartFormDataBody() {
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public void addFilePart(String name, File file) {
        addPart(new FilePart(name, file));
    }
    
    public void addStringPart(String name, String value) {
        addPart(new StringPart(name, value));
    }
    
    private ArrayList<Part> mParts;
    public void addPart(Part part) {
        if (mParts == null)
            mParts = new ArrayList<Part>();
        mParts.add(part);
    }

    @Override
    public Multimap get() {
        return new Multimap(formData);
    }
}
