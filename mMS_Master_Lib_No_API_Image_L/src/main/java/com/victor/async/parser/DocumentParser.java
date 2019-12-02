package com.victor.async.parser;

import com.victor.async.ByteBufferList;
import com.victor.async.DataEmitter;
import com.victor.async.DataSink;
import com.victor.async.callback.CompletedCallback;
import com.victor.async.future.Future;
import com.victor.async.future.TransformFuture;
import com.victor.async.http.body.DocumentBody;
import com.victor.async.stream.ByteBufferListInputStream;

import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

/**
 * Created by koush on 8/3/13.
 */
public class DocumentParser implements AsyncParser<Document> {
    @Override
    public Future<Document> parse(DataEmitter emitter) {
        return new ByteBufferListParser().parse(emitter)
        .then(new TransformFuture<Document, ByteBufferList>() {
            @Override
            protected void transform(ByteBufferList result) throws Exception {
                DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                DocumentBuilder db = dbf.newDocumentBuilder();
                setComplete(db.parse(new ByteBufferListInputStream(result)));
            }
        });
    }

    @Override
    public void write(DataSink sink, Document value, CompletedCallback completed) {
        new DocumentBody(value).write(null, sink, completed);
    }
}
