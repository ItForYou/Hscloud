package com.victor.async.http;

import com.victor.async.AsyncServer;
import com.victor.async.DataEmitter;
import com.victor.async.FilteredDataEmitter;
import com.victor.async.callback.CompletedCallback;
import com.victor.async.http.body.AsyncHttpRequestBody;
import com.victor.async.http.body.JSONObjectBody;
import com.victor.async.http.body.MultipartFormDataBody;
import com.victor.async.http.body.StringBody;
import com.victor.async.http.body.UrlEncodedFormBody;
import com.victor.async.http.filter.ChunkedInputFilter;
import com.victor.async.http.filter.ContentLengthFilter;
import com.victor.async.http.filter.GZIPInputFilter;
import com.victor.async.http.filter.InflaterInputFilter;
import com.victor.async.http.libcore.RawHeaders;
import com.victor.async.http.server.UnknownRequestBody;

public class HttpUtil {
    public static AsyncHttpRequestBody getBody(DataEmitter emitter, CompletedCallback reporter, RawHeaders headers) {
        String contentType = headers.get("Content-Type");
        if (contentType != null) {
            String[] values = contentType.split(";");
            for (int i = 0; i < values.length; i++) {
                values[i] = values[i].trim();
            }
            for (String ct: values) {
                if (UrlEncodedFormBody.CONTENT_TYPE.equals(ct)) {
                    return new UrlEncodedFormBody();
                }
                if (JSONObjectBody.CONTENT_TYPE.equals(ct)) {
                    return new JSONObjectBody();
                }
                if (StringBody.CONTENT_TYPE.equals(ct)) {
                    return new StringBody();
                }
                if (MultipartFormDataBody.CONTENT_TYPE.equals(ct)) {
                    return new MultipartFormDataBody(values);
                }
            }
        }

        return null;
    }
    
    static class EndEmitter extends FilteredDataEmitter {
        private EndEmitter() {
        }
        
        public static EndEmitter create(AsyncServer server, final Exception e) {
            final EndEmitter ret = new EndEmitter();
            // don't need to worry about any race conditions with post and this return value
            // since we are in the server thread.
            server.post(new Runnable() {
                @Override
                public void run() {
                    ret.report(e);
                }
            });
            return ret;
        }
    }
    
    public static DataEmitter getBodyDecoder(DataEmitter emitter, RawHeaders headers, boolean server) {
        long _contentLength;
        try {
            _contentLength = Long.parseLong(headers.get("Content-Length"));
        }
        catch (Exception ex) {
            _contentLength = -1;
        }
        final long contentLength = _contentLength;
        if (-1 != contentLength) {
            if (contentLength < 0) {
                EndEmitter ender = EndEmitter.create(emitter.getServer(), new BodyDecoderException("not using chunked encoding, and no content-length found."));
                ender.setDataEmitter(emitter);
                emitter = ender;
                return emitter;
            }
            if (contentLength == 0) {
                EndEmitter ender = EndEmitter.create(emitter.getServer(), null);
                ender.setDataEmitter(emitter);
                emitter = ender;
                return emitter;
            }
            ContentLengthFilter contentLengthWatcher = new ContentLengthFilter(contentLength);
            contentLengthWatcher.setDataEmitter(emitter);
            emitter = contentLengthWatcher;
        }
        else if ("chunked".equalsIgnoreCase(headers.get("Transfer-Encoding"))) {
            ChunkedInputFilter chunker = new ChunkedInputFilter();
            chunker.setDataEmitter(emitter);
            emitter = chunker;
        }
        else {
            if ((server || headers.getStatusLine().contains("HTTP/1.1")) && !"close".equalsIgnoreCase(headers.get("Connection"))) {
                // if this is the server, and the client has not indicated a request body, the client is done
                EndEmitter ender = EndEmitter.create(emitter.getServer(), null);
                ender.setDataEmitter(emitter);
                emitter = ender;
                return emitter;
            }
        }

        if ("gzip".equals(headers.get("Content-Encoding"))) {
            GZIPInputFilter gunzipper = new GZIPInputFilter();
            gunzipper.setDataEmitter(emitter);
            emitter = gunzipper;
        }        
        else if ("deflate".equals(headers.get("Content-Encoding"))) {
            InflaterInputFilter inflater = new InflaterInputFilter();
            inflater.setDataEmitter(emitter);
            emitter = inflater;
        }

        // conversely, if this is the client (http 1.0), and the server has not indicated a request body, we do not report
        // the close/end event until the server actually closes the connection.
        return emitter;
    }

    public static boolean isKeepAlive(RawHeaders headers) {
        boolean keepAlive;
        String connection = headers.get("Connection");
        if (connection != null) {
            keepAlive = "keep-alive".equalsIgnoreCase(connection);
        }
        else {
            keepAlive = headers.getHttpMinorVersion() >= 1;
        }

        return keepAlive;
    }
}
