package com.victor.mms;

import java.util.List;

import android.annotation.TargetApi;
import android.net.Uri;
import android.os.Build;

@TargetApi(Build.VERSION_CODES.KITKAT)
public class DocumentManager {
    private static final String DOCUMENT_URIS =
            "com.android.providers.media.documents " +
            "com.android.externalstorage.documents " +
            "com.android.providers.downloads.documents " +
            "com.android.providers.media.documents";

    private static final String PATH_DOCUMENT = "document";

    public static String getDocumentId(Uri documentUri) {
        final List<String> paths = documentUri.getPathSegments();
        if (paths.size() < 2) {
            throw new IllegalArgumentException("Not a document: " + documentUri);
        }

        if (!PATH_DOCUMENT.equals(paths.get(0))) {
            throw new IllegalArgumentException("Not a document: " + documentUri);
        }
        return paths.get(1);
    }

    public static boolean isDocumentUri(Uri uri) {
        final List<String> paths = uri.getPathSegments();
        if (paths.size() < 2) {
            return false;
        }
        if (!PATH_DOCUMENT.equals(paths.get(0))) {
            return false;
        }
        return DOCUMENT_URIS.contains(uri.getAuthority());
    }
}
