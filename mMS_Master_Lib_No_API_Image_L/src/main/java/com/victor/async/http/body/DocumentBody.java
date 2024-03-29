package com.victor.async.http.body;

import com.victor.async.DataEmitter;
import com.victor.async.DataSink;
import com.victor.async.Util;
import com.victor.async.callback.CompletedCallback;
import com.victor.async.future.FutureCallback;
import com.victor.async.http.AsyncHttpRequest;
import com.victor.async.parser.DocumentParser;

import org.w3c.dom.Document;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

/**
 * Created by koush on 8/30/13.
 */
public class DocumentBody implements AsyncHttpRequestBody<Document> {
    public DocumentBody() {
        this(null);
    }

    public DocumentBody(Document document) {
        this.document = document;
    }

    ByteArrayOutputStream bout;
    private void prepare() {
        if (bout != null)
            return;

        try {
            DOMSource source = new DOMSource(document);
            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer transformer = tf.newTransformer();
            bout = new ByteArrayOutputStream();
            OutputStreamWriter writer = new OutputStreamWriter(bout);
            StreamResult result = new StreamResult(writer);
            transformer.transform(source, result);
            writer.flush();
        }
        catch (Exception e) {
        }
    }

    @Override
    public void write(AsyncHttpRequest request, DataSink sink, CompletedCallback completed) {
        prepare();
        byte[] bytes = bout.toByteArray();
        Util.writeAll(sink, bytes, completed);
    }

    @Override
    public void parse(DataEmitter emitter, final CompletedCallback completed) {
        new DocumentParser().parse(emitter).setCallback(new FutureCallback<Document>() {
            @Override
            public void onCompleted(Exception e, Document result) {
                document = result;
                completed.onCompleted(e);
            }
        });
    }

    public static final String CONTENT_TYPE = "application/xml";

    @Override
    public String getContentType() {
        return CONTENT_TYPE;
    }

    @Override
    public boolean readFullyOnRequest() {
        return true;
    }

    @Override
    public int length() {
        prepare();
        return bout.size();
    }

    Document document;
    @Override
    public Document get() {
        return document;
    }
}
