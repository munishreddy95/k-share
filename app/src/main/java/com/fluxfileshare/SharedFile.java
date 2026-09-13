package com.fluxfileshare;

import android.net.Uri;

final class SharedFile {
    final Uri uri;
    final String name;
    final String mimeType;
    final long size;
    final String parentFolder;

    SharedFile(Uri uri, String name, String mimeType, long size) {
        this(uri, name, mimeType, size, null);
    }

    SharedFile(Uri uri, String name, String mimeType, long size, String parentFolder) {
        this.uri = uri;
        this.name = name;
        this.mimeType = mimeType == null ? "application/octet-stream" : mimeType;
        this.size = size;
        this.parentFolder = normalizeParentFolder(parentFolder, uri);
    }

    boolean isVideo() {
        String type = effectiveMimeType();
        return type.startsWith("video/");
    }

    boolean isAudio() {
        String type = effectiveMimeType();
        return type.startsWith("audio/");
    }

    boolean isCompressed() {
        String type = effectiveMimeType();
        String lowerName = name == null ? "" : name.toLowerCase(java.util.Locale.US);
        return "application/zip".equals(type)
                || "application/x-zip-compressed".equals(type)
                || "application/vnd.rar".equals(type)
                || "application/x-rar-compressed".equals(type)
                || "application/x-7z-compressed".equals(type)
                || "application/gzip".equals(type)
                || "application/x-gzip".equals(type)
                || "application/x-tar".equals(type)
                || "application/x-bzip2".equals(type)
                || "application/x-xz".equals(type)
                || "application/java-archive".equals(type)
                || "application/vnd.android.package-archive".equals(type)
                || lowerName.endsWith(".zip")
                || lowerName.endsWith(".rar")
                || lowerName.endsWith(".7z")
                || lowerName.endsWith(".tar")
                || lowerName.endsWith(".gz")
                || lowerName.endsWith(".tgz")
                || lowerName.endsWith(".bz2")
                || lowerName.endsWith(".xz")
                || lowerName.endsWith(".jar")
                || lowerName.endsWith(".apk");
    }

    boolean isPlayableMedia() {
        return isVideo() || isAudio();
    }

    boolean isDocument() {
        String type = effectiveMimeType();
        String lowerName = name == null ? "" : name.toLowerCase(java.util.Locale.US);
        return type.startsWith("text/")
                || "application/pdf".equals(type)
                || "application/msword".equals(type)
                || "application/vnd.openxmlformats-officedocument.wordprocessingml.document".equals(type)
                || "application/vnd.ms-excel".equals(type)
                || "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet".equals(type)
                || "application/vnd.ms-powerpoint".equals(type)
                || "application/vnd.openxmlformats-officedocument.presentationml.presentation".equals(type)
                || "application/rtf".equals(type)
                || "text/rtf".equals(type)
                || "application/json".equals(type)
                || "application/xml".equals(type)
                || "text/xml".equals(type)
                || "application/vnd.oasis.opendocument.text".equals(type)
                || "application/vnd.oasis.opendocument.spreadsheet".equals(type)
                || "application/vnd.oasis.opendocument.presentation".equals(type)
                || lowerName.endsWith(".pdf")
                || lowerName.endsWith(".doc")
                || lowerName.endsWith(".docx")
                || lowerName.endsWith(".xls")
                || lowerName.endsWith(".xlsx")
                || lowerName.endsWith(".ppt")
                || lowerName.endsWith(".pptx")
                || lowerName.endsWith(".txt")
                || lowerName.endsWith(".csv")
                || lowerName.endsWith(".md")
                || lowerName.endsWith(".rtf")
                || lowerName.endsWith(".json")
                || lowerName.endsWith(".xml")
                || lowerName.endsWith(".log")
                || lowerName.endsWith(".odt")
                || lowerName.endsWith(".ods")
                || lowerName.endsWith(".odp");
    }

    String category() {
        if (isVideo()) {
            return "video";
        }
        if (isAudio()) {
            return "audio";
        }
        if (isCompressed()) {
            return "compressed";
        }
        if (isDocument()) {
            return "document";
        }
        return "other";
    }

    String mediaLabel() {
        if (isVideo()) {
            return "Video";
        }
        if (isAudio()) {
            return "Audio";
        }
        if (isCompressed()) {
            return "Compressed";
        }
        if (isDocument()) {
            return "Document";
        }
        return "File";
    }

    String parentFolderLabel() {
        return parentFolder;
    }

    String effectiveMimeType() {
        if (!"application/octet-stream".equals(mimeType)) {
            return mimeType;
        }
        String lowerName = name == null ? "" : name.toLowerCase(java.util.Locale.US);
        if (lowerName.endsWith(".mp4")) {
            return "video/mp4";
        }
        if (lowerName.endsWith(".webm")) {
            return "video/webm";
        }
        if (lowerName.endsWith(".ogv") || lowerName.endsWith(".ogg")) {
            return "video/ogg";
        }
        if (lowerName.endsWith(".mov")) {
            return "video/quicktime";
        }
        if (lowerName.endsWith(".m4v")) {
            return "video/x-m4v";
        }
        if (lowerName.endsWith(".mkv")) {
            return "video/x-matroska";
        }
        if (lowerName.endsWith(".avi")) {
            return "video/x-msvideo";
        }
        if (lowerName.endsWith(".mp3")) {
            return "audio/mpeg";
        }
        if (lowerName.endsWith(".m4a") || lowerName.endsWith(".aac")) {
            return "audio/aac";
        }
        if (lowerName.endsWith(".wav")) {
            return "audio/wav";
        }
        if (lowerName.endsWith(".flac")) {
            return "audio/flac";
        }
        if (lowerName.endsWith(".opus")) {
            return "audio/opus";
        }
        if (lowerName.endsWith(".zip")) {
            return "application/zip";
        }
        if (lowerName.endsWith(".rar")) {
            return "application/vnd.rar";
        }
        if (lowerName.endsWith(".7z")) {
            return "application/x-7z-compressed";
        }
        if (lowerName.endsWith(".tar")) {
            return "application/x-tar";
        }
        if (lowerName.endsWith(".gz") || lowerName.endsWith(".tgz")) {
            return "application/gzip";
        }
        if (lowerName.endsWith(".bz2")) {
            return "application/x-bzip2";
        }
        if (lowerName.endsWith(".xz")) {
            return "application/x-xz";
        }
        if (lowerName.endsWith(".jar")) {
            return "application/java-archive";
        }
        if (lowerName.endsWith(".apk")) {
            return "application/vnd.android.package-archive";
        }
        if (lowerName.endsWith(".pdf")) {
            return "application/pdf";
        }
        if (lowerName.endsWith(".doc")) {
            return "application/msword";
        }
        if (lowerName.endsWith(".docx")) {
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        }
        if (lowerName.endsWith(".xls")) {
            return "application/vnd.ms-excel";
        }
        if (lowerName.endsWith(".xlsx")) {
            return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        }
        if (lowerName.endsWith(".ppt")) {
            return "application/vnd.ms-powerpoint";
        }
        if (lowerName.endsWith(".pptx")) {
            return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
        }
        if (lowerName.endsWith(".txt") || lowerName.endsWith(".md")) {
            return "text/plain";
        }
        if (lowerName.endsWith(".csv")) {
            return "text/csv";
        }
        if (lowerName.endsWith(".rtf")) {
            return "application/rtf";
        }
        if (lowerName.endsWith(".json")) {
            return "application/json";
        }
        if (lowerName.endsWith(".xml")) {
            return "application/xml";
        }
        if (lowerName.endsWith(".log")) {
            return "text/plain";
        }
        if (lowerName.endsWith(".odt")) {
            return "application/vnd.oasis.opendocument.text";
        }
        if (lowerName.endsWith(".ods")) {
            return "application/vnd.oasis.opendocument.spreadsheet";
        }
        if (lowerName.endsWith(".odp")) {
            return "application/vnd.oasis.opendocument.presentation";
        }
        return mimeType;
    }

    private static String normalizeParentFolder(String value, Uri uri) {
        String folder = value == null ? "" : Uri.decode(value).replace("\\", "/").trim();
        if (folder.isEmpty() && uri != null && "file".equals(uri.getScheme())) {
            String path = uri.getPath();
            if (path != null) {
                java.io.File parent = new java.io.File(path).getParentFile();
                folder = parent == null ? "" : parent.getPath();
            }
        }
        while (folder.endsWith("/")) {
            folder = folder.substring(0, folder.length() - 1);
        }
        folder = stripPrefix(folder, "/storage/emulated/0/");
        folder = stripPrefix(folder, "/sdcard/");
        folder = stripPrefix(folder, "storage/emulated/0/");
        folder = stripPrefix(folder, "sdcard/");
        folder = stripPrefix(folder, "primary:");
        while (folder.startsWith("/")) {
            folder = folder.substring(1);
        }
        return folder.isEmpty() ? "Selected files" : folder;
    }

    private static String stripPrefix(String value, String prefix) {
        return value.regionMatches(true, 0, prefix, 0, prefix.length())
                ? value.substring(prefix.length())
                : value;
    }
}
