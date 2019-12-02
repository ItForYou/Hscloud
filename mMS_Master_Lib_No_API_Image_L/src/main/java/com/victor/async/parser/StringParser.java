package com.victor.async.parser;

import com.victor.async.ByteBufferList;
import com.victor.async.DataEmitter;
import com.victor.async.DataSink;
import com.victor.async.callback.CompletedCallback;
import com.victor.async.future.Future;
import com.victor.async.future.TransformFuture;

import java.nio.charset.Charset;

/**
 * Created by koush on 5/27/13.
 */
public class StringParser implements AsyncParser<String> {
    @Override
    public Future<String> parse(DataEmitter emitter) {
        final String charset = emitter.charset();
        return new ByteBufferListParser().parse(emitter)
        .then(new TransformFuture<String, ByteBufferList>() {
            @Override
            protected void transform(ByteBufferList result) throws Exception {
                setComplete(result.readString(charset != null ? Charset.forName(charset) : null));
            }
        });
    }

    @Override
    public void write(DataSink sink, String value, CompletedCallback completed) {
        new ByteBufferListParser().write(sink, new ByteBufferList(value.getBytes()), completed);
    }
}
