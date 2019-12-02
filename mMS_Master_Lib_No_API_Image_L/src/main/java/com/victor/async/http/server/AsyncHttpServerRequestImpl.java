package com.victor.async.http.server;

import java.util.regex.Matcher;

import com.victor.async.AsyncSocket;
import com.victor.async.DataEmitter;
import com.victor.async.FilteredDataEmitter;
import com.victor.async.LineEmitter;
import com.victor.async.LineEmitter.StringCallback;
import com.victor.async.callback.CompletedCallback;
import com.victor.async.callback.DataCallback;
import com.victor.async.http.body.AsyncHttpRequestBody;
import com.victor.async.http.HttpUtil;
import com.victor.async.http.libcore.RawHeaders;
import com.victor.async.http.libcore.RequestHeaders;

public abstract class AsyncHttpServerRequestImpl extends FilteredDataEmitter implements AsyncHttpServerRequest, CompletedCallback {
    private RawHeaders mRawHeaders = new RawHeaders();
    AsyncSocket mSocket;
    Matcher mMatcher;

    private CompletedCallback mReporter = new CompletedCallback() {
        @Override
        public void onCompleted(Exception error) {
            AsyncHttpServerRequestImpl.this.onCompleted(error);
        }
    };

    @Override
    public void onCompleted(Exception e) {
//        if (mBody != null)
//            mBody.onCompleted(e);
        report(e);
    }

    abstract protected void onHeadersReceived();
    
    protected void onNotHttp() {
        System.out.println("not http: " + mRawHeaders.getStatusLine());
        System.out.println("not http: " + mRawHeaders.getStatusLine().length());
    }

    protected AsyncHttpRequestBody onUnknownBody(RawHeaders headers) {
        return null;
    }
    
    StringCallback mHeaderCallback = new StringCallback() {
        @Override
        public void onStringAvailable(String s) {
            try {
                if (mRawHeaders.getStatusLine() == null) {
                    mRawHeaders.setStatusLine(s);
                    if (!mRawHeaders.getStatusLine().contains("HTTP/")) {
                        onNotHttp();
                        mSocket.setDataCallback(null);
                    }
                }
                else if (!"\r".equals(s)){
                    mRawHeaders.addLine(s);
                }
                else {
                    DataEmitter emitter = HttpUtil.getBodyDecoder(mSocket, mRawHeaders, true);
//                    emitter.setEndCallback(mReporter);
                    mBody = HttpUtil.getBody(emitter, mReporter, mRawHeaders);
                    if (mBody == null) {
                        mBody = onUnknownBody(mRawHeaders);
                        if (mBody == null)
                            mBody = new UnknownRequestBody(mRawHeaders.get("Content-Type"));
                    }
                    mBody.parse(emitter, mReporter);
                    mHeaders = new RequestHeaders(null, mRawHeaders);
                    onHeadersReceived();
                }
            }
            catch (Exception ex) {
                onCompleted(ex);
            }
        }
    };

    RawHeaders getRawHeaders() {
        return mRawHeaders;
    }

    String method;
    @Override
    public String getMethod() {
        return method;
    }
    
    void setSocket(AsyncSocket socket) {
        mSocket = socket;

        LineEmitter liner = new LineEmitter();
        mSocket.setDataCallback(liner);
        liner.setLineCallback(mHeaderCallback);
    }
    
    @Override
    public AsyncSocket getSocket() {
        return mSocket;
    }

    private RequestHeaders mHeaders;
    @Override
    public RequestHeaders getHeaders() {
        return mHeaders;
    }

    @Override
    public void setDataCallback(DataCallback callback) {
        mSocket.setDataCallback(callback);
    }

    @Override
    public DataCallback getDataCallback() {
        return mSocket.getDataCallback();
    }

    @Override
    public boolean isChunked() {
        return mSocket.isChunked();
    }

    @Override
    public Matcher getMatcher() {
        return mMatcher;
    }

    AsyncHttpRequestBody mBody;
    @Override
    public AsyncHttpRequestBody getBody() {
        return mBody;
    }

    @Override
    public void pause() {
        mSocket.pause();
    }

    @Override
    public void resume() {
        mSocket.resume();
    }

    @Override
    public boolean isPaused() {
        return mSocket.isPaused();
    }
}
