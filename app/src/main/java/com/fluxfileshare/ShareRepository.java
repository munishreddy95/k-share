package com.fluxfileshare;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.provider.MediaStore;
import android.util.AtomicFile;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

final class ShareRepository {
    private static final String PREFS = "shared_files";
    private static final String KEY_LOCAL_FILES = "files";
    private static final String KEY_LIVE_FILES = "live_files";
    private static final String DISCOVERED_FILE = "discovered_files.json";

    private final Context context;
    private final SharedPreferences prefs;
    private final String filesKey;

    ShareRepository(Context context) {
        this(context, KEY_LOCAL_FILES);
    }

    static ShareRepository live(Context context) {
        return new ShareRepository(context, KEY_LIVE_FILES);
    }

    private ShareRepository(Context context, String filesKey) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        this.filesKey = filesKey;
    }

    void replaceFiles(List<Uri> uris) {
        saveUris(uris);
    }

    void addFiles(List<Uri> uris) {
        Map<String, Uri> merged = new LinkedHashMap<>();
        for (SharedFile file : getFiles()) {
            merged.put(file.uri.toString(), file.uri);
        }
        for (Uri uri : uris) {
            merged.put(uri.toString(), uri);
        }
        saveUris(new ArrayList<>(merged.values()));
    }

    void addFile(Uri uri) {
        List<Uri> uris = new ArrayList<>();
        uris.add(uri);
        addFiles(uris);
    }

    void removeFile(Uri uri) {
        List<Uri> uris = new ArrayList<>();
        uris.add(uri);
        removeFiles(uris);
    }

    void removeFiles(List<Uri> uris) {
        Map<String, Uri> removeSet = new LinkedHashMap<>();
        for (Uri uri : uris) {
            removeSet.put(uri.toString(), uri);
        }
        List<Uri> remaining = new ArrayList<>();
        for (SharedFile file : getFiles()) {
            if (!removeSet.containsKey(file.uri.toString())) {
                remaining.add(file.uri);
            }
        }
        saveUris(remaining);
    }

    List<SharedFile> inspectFiles(List<Uri> uris) {
        List<SharedFile> files = new ArrayList<>();
        for (Uri uri : uris) {
            files.add(inspect(uri));
        }
        return files;
    }

    private void saveUris(List<Uri> uris) {
        ContentResolver resolver = context.getContentResolver();
        JSONArray files = new JSONArray();
        for (Uri uri : uris) {
            try {
                resolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (SecurityException ignored) {
                // Some providers grant transient access only; keep it for the current process.
            }

            SharedFile file = inspect(uri);
            JSONObject item = new JSONObject();
            try {
                item.put("uri", file.uri.toString());
                item.put("name", file.name);
                item.put("mimeType", file.mimeType);
                item.put("size", file.size);
                item.put("parentFolder", file.parentFolderLabel());
                files.put(item);
            } catch (JSONException ignored) {
            }
        }
        prefs.edit().putString(filesKey, files.toString()).apply();
    }

    List<SharedFile> getFiles() {
        String raw = prefs.getString(filesKey, "[]");
        try {
            JSONArray array = new JSONArray(raw);
            List<SharedFile> files = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                files.add(new SharedFile(
                        Uri.parse(item.getString("uri")),
                        item.optString("name", "file-" + (i + 1)),
                        item.optString("mimeType", "application/octet-stream"),
                        item.optLong("size", -1),
                        item.optString("parentFolder", null)
                ));
            }
            return files;
        } catch (JSONException e) {
            return Collections.emptyList();
        }
    }

    void saveDiscoveredFiles(List<SharedFile> discoveredFiles) {
        JSONArray files = new JSONArray();
        for (SharedFile file : discoveredFiles) {
            JSONObject item = new JSONObject();
            try {
                item.put("uri", file.uri.toString());
                item.put("name", file.name);
                item.put("mimeType", file.mimeType);
                item.put("size", file.size);
                item.put("parentFolder", file.parentFolderLabel());
                files.put(item);
            } catch (JSONException ignored) {
            }
        }
        AtomicFile cache = new AtomicFile(new File(context.getFilesDir(), DISCOVERED_FILE));
        FileOutputStream output = null;
        try {
            output = cache.startWrite();
            output.write(files.toString().getBytes(StandardCharsets.UTF_8));
            cache.finishWrite(output);
        } catch (Exception e) {
            if (output != null) {
                cache.failWrite(output);
            }
        }
    }

    List<SharedFile> getDiscoveredFiles() {
        AtomicFile cache = new AtomicFile(new File(context.getFilesDir(), DISCOVERED_FILE));
        if (!cache.getBaseFile().exists()) {
            return Collections.emptyList();
        }
        try {
            String raw = new String(cache.readFully(), StandardCharsets.UTF_8);
            JSONArray array = new JSONArray(raw);
            List<SharedFile> files = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                files.add(new SharedFile(
                        Uri.parse(item.getString("uri")),
                        item.optString("name", "file-" + (i + 1)),
                        item.optString("mimeType", "application/octet-stream"),
                        item.optLong("size", -1),
                        item.optString("parentFolder", null)
                ));
            }
            return files;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    void clear() {
        prefs.edit().remove(filesKey).apply();
    }

    SharedFile inspect(Uri uri) {
        if ("file".equals(uri.getScheme())) {
            String path = uri.getPath();
            if (path == null || path.trim().isEmpty()) {
                return new SharedFile(uri, "shared-file", null, -1);
            }
            File file = new File(path);
            return new SharedFile(
                    uri,
                    file.getName().isEmpty() ? "shared-file" : file.getName(),
                    null,
                    file.exists() ? file.length() : -1,
                    file.getParent()
            );
        }
        String name = "shared-file";
        long size = -1;
        String parentFolder = null;
        try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                int sizeColumn = cursor.getColumnIndex(OpenableColumns.SIZE);
                int relativePathColumn = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH);
                int dataColumn = cursor.getColumnIndex(MediaStore.MediaColumns.DATA);
                if (nameColumn >= 0) {
                    String value = cursor.getString(nameColumn);
                    if (value != null && !value.trim().isEmpty()) {
                        name = value;
                    }
                }
                if (sizeColumn >= 0 && !cursor.isNull(sizeColumn)) {
                    size = cursor.getLong(sizeColumn);
                }
                parentFolder = parentFolderFromColumns(
                        cursorString(cursor, relativePathColumn),
                        cursorString(cursor, dataColumn)
                );
            }
        } catch (Exception ignored) {
        }
        return new SharedFile(uri, name, context.getContentResolver().getType(uri), size, parentFolder);
    }

    private static String parentFolderFromColumns(String relativePath, String dataPath) {
        if (relativePath != null && !relativePath.trim().isEmpty()) {
            return relativePath;
        }
        if (dataPath == null || dataPath.trim().isEmpty()) {
            return null;
        }
        File parent = new File(dataPath).getParentFile();
        return parent == null ? null : parent.getPath();
    }

    private static String cursorString(Cursor cursor, int column) {
        if (cursor == null || column < 0) {
            return null;
        }
        try {
            return cursor.isNull(column) ? null : cursor.getString(column);
        } catch (Exception ignored) {
            return null;
        }
    }
}
