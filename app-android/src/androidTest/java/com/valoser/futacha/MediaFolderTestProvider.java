package com.valoser.futacha;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

/** Test-only document tree, backed by disk, counting provider round trips (no network). */
public class MediaFolderTestProvider extends ContentProvider {
    public static final String AUTHORITY = "com.valoser.futacha.test.mediafolder";
    private final AtomicInteger queries = new AtomicInteger();
    private final AtomicInteger listings = new AtomicInteger();
    private File root;

    @Override public boolean onCreate() {
        root = new File(getContext().getCacheDir(), "media-folder-provider");
        return root.isDirectory() || root.mkdirs();
    }

    private File file(String id) {
        if (id.equals("root")) return root;
        if (!id.startsWith("root/") || id.contains("..")) throw new IllegalArgumentException(id);
        return new File(root, id.substring(5));
    }

    private String id(File file) {
        String relative = root.toURI().relativize(file.toURI()).getPath();
        return relative.isEmpty() ? "root" : "root/" + relative.replaceAll("/$", "");
    }

    @Override public String getType(Uri uri) {
        return file(DocumentsContract.getDocumentId(uri)).isDirectory()
            ? DocumentsContract.Document.MIME_TYPE_DIR : "application/octet-stream";
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection,
                                  String[] selectionArgs, String sortOrder) {
        queries.incrementAndGet();
        File document = file(DocumentsContract.getDocumentId(uri));
        String[] columns = projection != null ? projection : new String[] {
            DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_FLAGS,
            DocumentsContract.Document.COLUMN_SIZE
        };
        MatrixCursor cursor = new MatrixCursor(columns);
        if ("children".equals(uri.getLastPathSegment())) {
            listings.incrementAndGet();
            File[] children = document.listFiles();
            if (children != null) for (File child : children) addRow(cursor, columns, child);
        } else if (document.exists()) addRow(cursor, columns, document);
        return cursor;
    }

    private void addRow(MatrixCursor cursor, String[] columns, File file) {
        Object[] row = new Object[columns.length];
        for (int i = 0; i < columns.length; i++) {
            switch (columns[i]) {
                case DocumentsContract.Document.COLUMN_DOCUMENT_ID: row[i] = id(file); break;
                case DocumentsContract.Document.COLUMN_DISPLAY_NAME: row[i] = file.getName(); break;
                case DocumentsContract.Document.COLUMN_MIME_TYPE:
                    row[i] = file.isDirectory() ? DocumentsContract.Document.MIME_TYPE_DIR : "image/jpeg"; break;
                case DocumentsContract.Document.COLUMN_FLAGS:
                    row[i] = DocumentsContract.Document.FLAG_SUPPORTS_WRITE
                        | DocumentsContract.Document.FLAG_SUPPORTS_DELETE
                        | DocumentsContract.Document.FLAG_SUPPORTS_RENAME
                        | (file.isDirectory() ? DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE : 0); break;
                case DocumentsContract.Document.COLUMN_SIZE: row[i] = file.length(); break;
            }
        }
        cursor.addRow(row);
    }

    @Override public Bundle call(String method, String arg, Bundle extras) {
        Bundle result = new Bundle();
        if ("grant".equals(method)) {
            getContext().grantUriPermission(arg, DocumentsContract.buildTreeDocumentUri(AUTHORITY, "root"),
                Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
            return result;
        }
        if ("stats".equals(method)) {
            result.putInt("queries", queries.get());
            result.putInt("listings", listings.get());
            return result;
        }
        if ("resetCounts".equals(method)) {
            queries.set(0);
            listings.set(0);
            return result;
        }
        Uri uri = extras.getParcelable("uri");
        File source = file(DocumentsContract.getDocumentId(uri));
        try {
            switch (method) {
                case "android:createDocument": {
                    File target = new File(source, extras.getString(DocumentsContract.Document.COLUMN_DISPLAY_NAME));
                    boolean created = DocumentsContract.Document.MIME_TYPE_DIR.equals(
                        extras.getString(DocumentsContract.Document.COLUMN_MIME_TYPE))
                        ? target.mkdir() : target.createNewFile();
                    if (!created) throw new IOException("Already exists: " + target);
                    result.putParcelable("uri", DocumentsContract.buildDocumentUri(AUTHORITY, id(target)));
                    return result;
                }
                case "android:renameDocument": {
                    File target = new File(source.getParentFile(), extras.getString(DocumentsContract.Document.COLUMN_DISPLAY_NAME));
                    if (target.exists() || !source.renameTo(target)) throw new IOException("Cannot rename " + source);
                    result.putParcelable("uri", DocumentsContract.buildDocumentUri(AUTHORITY, id(target)));
                    return result;
                }
                case "android:deleteDocument":
                    if (!source.delete()) throw new IOException("Cannot delete " + source);
                    return result;
                default: throw new UnsupportedOperationException(method);
            }
        } catch (IOException failure) {
            throw new IllegalStateException(failure);
        }
    }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        return ParcelFileDescriptor.open(file(DocumentsContract.getDocumentId(uri)), ParcelFileDescriptor.parseMode(mode));
    }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { return 0; }
    @Override public int delete(Uri uri, String selection, String[] args) { return 0; }
}
