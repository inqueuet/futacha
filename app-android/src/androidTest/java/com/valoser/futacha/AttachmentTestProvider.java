package com.valoser.futacha;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;

// Runs in the test APK's separate process, where only Android/Java are on the classpath.
public class AttachmentTestProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) {
        return uri.getLastPathSegment().endsWith("mp4") ? "video/mp4" : "image/jpeg";
    }
    @Override public Cursor query(Uri uri, String[] projection, String selection,
                                  String[] selectionArgs, String sortOrder) {
        File file = new File(getContext().getFilesDir(), uri.getLastPathSegment());
        String[] columns = projection != null ? projection : new String[] {
            OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE
        };
        MatrixCursor cursor = new MatrixCursor(columns);
        Object[] row = new Object[columns.length];
        for (int i = 0; i < columns.length; i++) {
            if (OpenableColumns.DISPLAY_NAME.equals(columns[i])) {
                row[i] = "true".equals(uri.getQueryParameter("nameless")) ? null : file.getName();
            } else if (OpenableColumns.SIZE.equals(columns[i])) {
                String size = uri.getQueryParameter("size");
                row[i] = size != null ? Long.parseLong(size) : file.length();
            }
        }
        cursor.addRow(row);
        return cursor;
    }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File file = new File(getContext().getFilesDir(), uri.getLastPathSegment());
        file.getParentFile().mkdirs();
        int flags = "r".equals(mode) ? ParcelFileDescriptor.MODE_READ_ONLY :
            ParcelFileDescriptor.MODE_WRITE_ONLY | ParcelFileDescriptor.MODE_CREATE | ParcelFileDescriptor.MODE_TRUNCATE;
        return ParcelFileDescriptor.open(file, flags);
    }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { return 0; }
    @Override public int delete(Uri uri, String selection, String[] args) {
        return new File(getContext().getFilesDir(), uri.getLastPathSegment()).delete() ? 1 : 0;
    }
}
