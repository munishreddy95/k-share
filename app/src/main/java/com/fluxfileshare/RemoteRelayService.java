package com.fluxfileshare;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Base64;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.SSLSocket;

public final class RemoteRelayService extends Service {
    static final String ACTION_START = "com.fluxfileshare.REMOTE_START";
    static final String ACTION_STOP = "com.fluxfileshare.REMOTE_STOP";
    static final String BROADCAST_REMOTE_STATUS = "com.fluxfileshare.REMOTE_STATUS";
    static final String EXTRA_LOCAL_PORT = "local_port";
    static final String EXTRA_USE_PRIVATE_SERVER = "use_private_server";
    static final String EXTRA_RUNNING = "running";
    static final String EXTRA_CONNECTED = "connected";
    static final String EXTRA_PUBLIC_URL = "public_url";
    static final String EXTRA_SHORT_URL = "short_url";
    static final String EXTRA_FULL_URL = "full_url";
    static final String EXTRA_TINY_URL_ERROR = "tiny_url_error";
    static final String EXTRA_MESSAGE = "message";
    static final String EXTRA_ERROR = "error";

    private static final String CHANNEL_ID = "global_sharing";
    private static final int NOTIFICATION_ID = 1003;
    private static final int CONNECT_TIMEOUT_MS = 15000;
    private static final int LOCAL_CONNECT_TIMEOUT_MS = 10000;
    private static final int LOCAL_READ_TIMEOUT_MS = 120000;
    private static final int MAX_FRAME_BYTES = 2 * 1024 * 1024;
    private static final int STREAM_CHUNK_BYTES = 48 * 1024;
    private static final int HEADER_LIMIT_BYTES = 64 * 1024;
    private static final int[] RECONNECT_DELAYS_MS = {2000, 4000, 8000, 15000, 30000};
    private static final String WEBSOCKET_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    private static volatile boolean currentRunning;
    private static volatile boolean currentConnected;
    private static volatile String currentPublicUrl = "";
    private static volatile String currentShortUrl = "";
    private static volatile String currentFullUrl = "";
    private static volatile String currentTinyUrlError = "";
    private static volatile String currentMessage = "Global link off.";

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean stopped = new AtomicBoolean(false);
    private final Object writeLock = new Object();
    private final SecureRandom secureRandom = new SecureRandom();
    private final ConcurrentHashMap<String, Socket> activeLocalSockets = new ConcurrentHashMap<>();
    private ExecutorService requestExecutor = Executors.newCachedThreadPool();
    private Thread relayThread;
    private Socket relaySocket;
    private InputStream relayInput;
    private OutputStream relayOutput;
    private volatile boolean foregroundStarted;
    private String slug = "";
    private String token = "";
    private String publicUrl = "";
    private String shortUrl = "";
    private String fullUrl = "";
    private String tinyUrlError = "";
    private LocalHttpServer privateLocalServer;
    private int privateLocalPort;

    static boolean isRunning() {
        return currentRunning;
    }

    static boolean isConnected() {
        return currentConnected;
    }

    static String currentPublicUrl() {
        return currentPublicUrl;
    }

    static String currentShortUrl() {
        return currentShortUrl;
    }

    static String currentFullUrl() {
        return currentFullUrl;
    }

    static String currentTinyUrlError() {
        return currentTinyUrlError;
    }

    static String currentMessage() {
        return currentMessage;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopRelay("Global link stopped.", true);
            return START_NOT_STICKY;
        }

        int localPort = intent == null ? 0 : intent.getIntExtra(EXTRA_LOCAL_PORT, 0);
        boolean usePrivateServer = intent == null || intent.getBooleanExtra(EXTRA_USE_PRIVATE_SERVER, true);

        if (!isConfigured()) {
            broadcast(false, false, "", "Relay is not configured.", "Add FLUX_RELAY_HOST, FLUX_RELAY_PORT, and FLUX_RELAY_PUBLIC_BASE before creating a global link.");
            stopSelf();
            return START_NOT_STICKY;
        }

        ensureChannel();
        startForeground(NOTIFICATION_ID, notification("Opening global link...", ""));
        foregroundStarted = true;
        if (running.get()) {
            broadcast(true, currentConnected, currentPublicUrl, currentMessage, null);
            return START_STICKY;
        }

        try {
            if (usePrivateServer || localPort <= 0) {
                localPort = ensurePrivateLocalServer();
            }
        } catch (IOException error) {
            broadcast(false, false, "", "Global link off.", "Could not start private relay server: " + error.getMessage());
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
            return START_NOT_STICKY;
        }

        stopped.set(false);
        requestExecutor = Executors.newCachedThreadPool();
        slug = buildSlug();
        token = randomHex(16);
        fullUrl = buildPublicUrl(slug, token);
        shortUrl = "";
        tinyUrlError = "";
        publicUrl = fullUrl;
        running.set(true);
        currentRunning = true;
        currentConnected = false;
        currentFullUrl = fullUrl;
        currentShortUrl = shortUrl;
        currentTinyUrlError = tinyUrlError;
        currentPublicUrl = preferredDisplayUrl(publicUrl, fullUrl, shortUrl);
        currentMessage = "Connecting to relay...";
        broadcast(true, false, publicUrl, currentMessage, null);
        int finalLocalPort = localPort;
        relayThread = new Thread(() -> relayLoop(finalLocalPort), "flux-global-relay");
        relayThread.start();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        stopRelay("Global link stopped.", false);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void relayLoop(int localPort) {
        int attempt = 0;
        while (running.get()) {
            try {
                connectRelay();
                attempt = 0;
                sendRegisterFrame();
                while (running.get()) {
                    JSONObject frame = readFrame();
                    if (frame == null) {
                        throw new IOException("Relay closed the connection");
                    }
                    handleRelayFrame(frame, localPort);
                }
            } catch (Exception error) {
                closeRelaySocket();
                if (!running.get()) {
                    break;
                }
                int delay = RECONNECT_DELAYS_MS[Math.min(attempt, RECONNECT_DELAYS_MS.length - 1)];
                attempt++;
                currentConnected = false;
                currentMessage = "Network lost. Reconnecting global link...";
                broadcast(true, false, publicUrl, currentMessage, error.getMessage());
                updateNotification(currentMessage, publicUrl);
                sleep(delay);
            }
        }
    }

    private void handleRelayFrame(JSONObject frame, int localPort) {
        String type = frame.optString("type", "");
        if ("registered".equals(type)) {
            shortUrl = frame.optString("shortUrl", shortUrl);
            fullUrl = frame.optString("fullUrl", fullUrl);
            tinyUrlError = frame.optString("tinyUrlError", tinyUrlError);
            String serverUrl = preferredDisplayUrl(
                    frame.optString("publicUrl", publicUrl),
                    fullUrl,
                    shortUrl
            );
            if (!TextUtils.isEmpty(serverUrl)) {
                publicUrl = serverUrl;
                currentPublicUrl = serverUrl;
            }
            currentShortUrl = shortUrl == null ? "" : shortUrl;
            currentFullUrl = fullUrl == null ? "" : fullUrl;
            currentTinyUrlError = tinyUrlError == null ? "" : tinyUrlError;
            currentConnected = true;
            currentMessage = "Global link online.";
            broadcast(true, true, publicUrl, currentMessage, null);
            updateNotification("Global link online", publicUrl);
            return;
        }
        if ("request".equals(type)) {
            requestExecutor.execute(() -> handleRemoteRequest(frame, localPort));
            return;
        }
        if ("cancel".equals(type)) {
            closeLocalSocket(frame.optString("id", ""));
            return;
        }
        if ("ping".equals(type)) {
            JSONObject pong = new JSONObject();
            try {
                pong.put("type", "pong");
                sendFrame(pong);
            } catch (Exception ignored) {
            }
        }
    }

    private void handleRemoteRequest(JSONObject frame, int localPort) {
        String id = frame.optString("id", "");
        if (TextUtils.isEmpty(id)) {
            return;
        }
        Socket localSocket = new Socket();
        activeLocalSockets.put(id, localSocket);
        try {
            String method = frame.optString("method", "GET").toUpperCase(Locale.US);
            String path = frame.optString("path", "/");
            JSONObject headers = frame.optJSONObject("headers");
            byte[] body = decodeProxyBody(frame.optString("body", ""));
            localSocket.connect(new InetSocketAddress("127.0.0.1", localPort), LOCAL_CONNECT_TIMEOUT_MS);
            localSocket.setSoTimeout(LOCAL_READ_TIMEOUT_MS);
            try (Socket closeable = localSocket;
                 OutputStream output = new BufferedOutputStream(closeable.getOutputStream());
                 InputStream input = new BufferedInputStream(closeable.getInputStream())) {
                writeLocalRequest(output, method, path, headers, body);
                LocalResponseHeader responseHeader = readLocalResponseHeader(input);
                sendResponseStart(id, responseHeader);
                if (!"HEAD".equals(method)) {
                    byte[] buffer = new byte[STREAM_CHUNK_BYTES];
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        sendResponseChunk(id, buffer, read);
                    }
                }
                sendResponseEnd(id);
            }
        } catch (Exception error) {
            sendResponseError(id, error.getMessage());
        } finally {
            activeLocalSockets.remove(id);
            closeQuietly(localSocket);
        }
    }

    private byte[] decodeProxyBody(String encoded) throws IOException {
        if (TextUtils.isEmpty(encoded)) {
            return new byte[0];
        }
        try {
            return Base64.decode(encoded, Base64.DEFAULT);
        } catch (IllegalArgumentException error) {
            throw new IOException("Relay request body is invalid", error);
        }
    }

    private void writeLocalRequest(OutputStream output, String method, String path, JSONObject headers, byte[] body) throws IOException {
        String safeMethod = ("HEAD".equals(method) || "GET".equals(method) || "POST".equals(method)) ? method : "GET";
        String safePath = TextUtils.isEmpty(path) || !path.startsWith("/") ? "/" : path;
        byte[] safeBody = "POST".equals(safeMethod) && body != null ? body : new byte[0];
        StringBuilder request = new StringBuilder();
        request.append(safeMethod).append(' ').append(safePath).append(" HTTP/1.1\r\n");
        request.append("Host: 127.0.0.1\r\n");
        request.append("Connection: close\r\n");
        if (headers != null) {
            Iterator<String> keys = headers.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                if (isForwardableHeader(key)) {
                    request.append(key).append(": ").append(headerSafe(headers.optString(key))).append("\r\n");
                }
            }
        }
        if ("POST".equals(safeMethod)) {
            request.append("Content-Length: ").append(safeBody.length).append("\r\n");
        }
        request.append("\r\n");
        output.write(request.toString().getBytes(StandardCharsets.UTF_8));
        if (safeBody.length > 0) {
            output.write(safeBody);
        }
        output.flush();
    }

    private LocalResponseHeader readLocalResponseHeader(InputStream input) throws IOException {
        byte[] marker = {'\r', '\n', '\r', '\n'};
        int matched = 0;
        int value;
        ByteArrayOutputStream headerBytes = new ByteArrayOutputStream();
        while ((value = input.read()) != -1) {
            headerBytes.write(value);
            if (value == marker[matched]) {
                matched++;
                if (matched == marker.length) {
                    break;
                }
            } else {
                matched = value == marker[0] ? 1 : 0;
            }
            if (headerBytes.size() > HEADER_LIMIT_BYTES) {
                throw new IOException("Local response headers are too large");
            }
        }
        if (headerBytes.size() == 0) {
            throw new IOException("No response from local server");
        }
        String raw = new String(headerBytes.toByteArray(), StandardCharsets.ISO_8859_1);
        String[] lines = raw.split("\\r?\\n");
        if (lines.length == 0 || !lines[0].startsWith("HTTP/")) {
            throw new IOException("Invalid local HTTP response");
        }
        String[] statusParts = lines[0].split(" ", 3);
        int status = statusParts.length > 1 ? parseStatus(statusParts[1]) : 502;
        JSONObject headers = new JSONObject();
        for (int i = 1; i < lines.length; i++) {
            int separator = lines[i].indexOf(':');
            if (separator <= 0) {
                continue;
            }
            String key = lines[i].substring(0, separator).trim().toLowerCase(Locale.US);
            if (isResponseHeaderAllowed(key)) {
                try {
                    headers.put(key, lines[i].substring(separator + 1).trim());
                } catch (Exception ignored) {
                }
            }
        }
        return new LocalResponseHeader(status, headers);
    }

    private void connectRelay() throws IOException {
        closeRelaySocket();
        Socket socket;
        if (BuildConfig.FLUX_RELAY_TLS) {
            SSLSocket sslSocket = (SSLSocket) SSLSocketFactory.getDefault().createSocket();
            SSLParameters parameters = sslSocket.getSSLParameters();
            parameters.setEndpointIdentificationAlgorithm("HTTPS");
            if (BuildConfig.FLUX_RELAY_HOST.matches(".*[A-Za-z].*")) {
                parameters.setServerNames(Collections.singletonList(new SNIHostName(BuildConfig.FLUX_RELAY_HOST)));
            }
            sslSocket.setSSLParameters(parameters);
            sslSocket.connect(new InetSocketAddress(BuildConfig.FLUX_RELAY_HOST, BuildConfig.FLUX_RELAY_PORT), CONNECT_TIMEOUT_MS);
            sslSocket.startHandshake();
            socket = sslSocket;
        } else {
            socket = new Socket();
            socket.connect(new InetSocketAddress(BuildConfig.FLUX_RELAY_HOST, BuildConfig.FLUX_RELAY_PORT), CONNECT_TIMEOUT_MS);
        }
        socket.setKeepAlive(true);
        relaySocket = socket;
        relayInput = new BufferedInputStream(socket.getInputStream());
        relayOutput = new BufferedOutputStream(socket.getOutputStream());
        performWebSocketHandshake();
    }

    private void performWebSocketHandshake() throws IOException {
        byte[] nonce = new byte[16];
        secureRandom.nextBytes(nonce);
        String key = Base64.encodeToString(nonce, Base64.NO_WRAP);
        String path = websocketPath();
        String host = BuildConfig.FLUX_RELAY_HOST;
        int port = BuildConfig.FLUX_RELAY_PORT;
        boolean defaultPort = (BuildConfig.FLUX_RELAY_TLS && port == 443) || (!BuildConfig.FLUX_RELAY_TLS && port == 80);
        String hostHeader = defaultPort ? host : host + ":" + port;
        String request = "GET " + path + " HTTP/1.1\r\n"
                + "Host: " + hostHeader + "\r\n"
                + "Upgrade: websocket\r\n"
                + "Connection: Upgrade\r\n"
                + "Sec-WebSocket-Key: " + key + "\r\n"
                + "Sec-WebSocket-Version: 13\r\n"
                + "User-Agent: K-Share-Android\r\n"
                + "\r\n";
        relayOutput.write(request.getBytes(StandardCharsets.UTF_8));
        relayOutput.flush();

        String rawHeaders = readHeaderBlock(relayInput);
        String[] lines = rawHeaders.split("\\r?\\n");
        if (lines.length == 0 || !lines[0].contains(" 101 ")) {
            throw new IOException("Relay did not accept WebSocket upgrade");
        }
        Map<String, String> headers = parseHeaders(lines, 1);
        String accept = headers.get("sec-websocket-accept");
        if (!websocketAccept(key).equals(accept)) {
            throw new IOException("Relay WebSocket handshake validation failed");
        }
    }

    private void sendRegisterFrame() throws Exception {
        JSONObject frame = new JSONObject();
        frame.put("type", "register");
        frame.put("slug", slug);
        frame.put("token", token);
        frame.put("deviceName", deviceName());
        frame.put("app", "K-Share Android");
        sendFrame(frame);
    }

    private JSONObject readFrame() throws Exception {
        InputStream input = relayInput;
        if (input == null) {
            return null;
        }
        while (true) {
            int first = input.read();
            int second = input.read();
            if (first == -1 || second == -1) {
                return null;
            }
            boolean fin = (first & 0x80) != 0;
            int opcode = first & 0x0f;
            boolean masked = (second & 0x80) != 0;
            long length = second & 0x7f;
            if (!fin) {
                throw new IOException("Fragmented relay frames are not supported");
            }
            if (length == 126) {
                length = readUnsignedShort(input);
            } else if (length == 127) {
                length = readUnsignedLong(input);
            }
            if (length > MAX_FRAME_BYTES) {
                throw new IOException("Relay frame is too large");
            }
            byte[] mask = null;
            if (masked) {
                mask = readFully(input, 4);
            }
            byte[] payload = readFully(input, (int) length);
            if (masked) {
                for (int i = 0; i < payload.length; i++) {
                    payload[i] = (byte) (payload[i] ^ mask[i % 4]);
                }
            }
            if (opcode == 0x8) {
                return null;
            }
            if (opcode == 0x9) {
                sendWebSocketControl(0xA, payload);
                continue;
            }
            if (opcode == 0xA) {
                continue;
            }
            if (opcode != 0x1 && opcode != 0x2) {
                throw new IOException("Unsupported relay frame opcode");
            }
            return new JSONObject(new String(payload, StandardCharsets.UTF_8));
        }
    }

    private void sendFrame(JSONObject frame) throws IOException {
        byte[] payload = frame.toString().getBytes(StandardCharsets.UTF_8);
        if (payload.length > MAX_FRAME_BYTES) {
            throw new IOException("Relay frame is too large");
        }
        writeWebSocketFrame(0x1, payload);
    }

    private void sendWebSocketControl(int opcode, byte[] payload) throws IOException {
        if (payload.length > 125) {
            throw new IOException("Relay control frame is too large");
        }
        writeWebSocketFrame(opcode, payload);
    }

    private void writeWebSocketFrame(int opcode, byte[] payload) throws IOException {
        synchronized (writeLock) {
            if (relayOutput == null) {
                throw new IOException("Relay is not connected");
            }
            ByteArrayOutputStream frame = new ByteArrayOutputStream();
            frame.write(0x80 | opcode);
            int length = payload.length;
            if (length <= 125) {
                frame.write(0x80 | length);
            } else if (length <= 0xffff) {
                frame.write(0x80 | 126);
                frame.write((length >> 8) & 0xff);
                frame.write(length & 0xff);
            } else {
                frame.write(0x80 | 127);
                for (int shift = 56; shift >= 32; shift -= 8) {
                    frame.write(0);
                }
                for (int shift = 24; shift >= 0; shift -= 8) {
                    frame.write((length >> shift) & 0xff);
                }
            }
            byte[] mask = new byte[4];
            secureRandom.nextBytes(mask);
            frame.write(mask);
            for (int i = 0; i < payload.length; i++) {
                frame.write(payload[i] ^ mask[i % 4]);
            }
            relayOutput.write(frame.toByteArray());
            relayOutput.flush();
        }
    }

    private String websocketPath() {
        String path = BuildConfig.FLUX_RELAY_AGENT_PATH;
        if (TextUtils.isEmpty(path)) {
            return "/agent";
        }
        return path.startsWith("/") ? path : "/" + path;
    }

    private String websocketAccept(String key) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hashed = digest.digest((key + WEBSOCKET_GUID).getBytes(StandardCharsets.ISO_8859_1));
            return Base64.encodeToString(hashed, Base64.NO_WRAP);
        } catch (Exception error) {
            throw new IOException("Could not validate WebSocket handshake", error);
        }
    }

    private String readHeaderBlock(InputStream input) throws IOException {
        byte[] marker = {'\r', '\n', '\r', '\n'};
        int matched = 0;
        int value;
        ByteArrayOutputStream headerBytes = new ByteArrayOutputStream();
        while ((value = input.read()) != -1) {
            headerBytes.write(value);
            if (value == marker[matched]) {
                matched++;
                if (matched == marker.length) {
                    break;
                }
            } else {
                matched = value == marker[0] ? 1 : 0;
            }
            if (headerBytes.size() > HEADER_LIMIT_BYTES) {
                throw new IOException("Relay response headers are too large");
            }
        }
        if (headerBytes.size() == 0) {
            throw new IOException("Relay did not respond");
        }
        return new String(headerBytes.toByteArray(), StandardCharsets.ISO_8859_1);
    }

    private Map<String, String> parseHeaders(String[] lines, int startIndex) {
        Map<String, String> headers = new HashMap<>();
        for (int i = startIndex; i < lines.length; i++) {
            int separator = lines[i].indexOf(':');
            if (separator > 0) {
                headers.put(
                        lines[i].substring(0, separator).trim().toLowerCase(Locale.US),
                        lines[i].substring(separator + 1).trim()
                );
            }
        }
        return headers;
    }

    private int readUnsignedShort(InputStream input) throws IOException {
        int high = input.read();
        int low = input.read();
        if (high == -1 || low == -1) {
            throw new IOException("Relay frame ended early");
        }
        return (high << 8) | low;
    }

    private long readUnsignedLong(InputStream input) throws IOException {
        long value = 0;
        for (int i = 0; i < 8; i++) {
            int current = input.read();
            if (current == -1) {
                throw new IOException("Relay frame ended early");
            }
            value = (value << 8) | current;
        }
        return value;
    }

    private byte[] readFully(InputStream input, int length) throws IOException {
        byte[] data = new byte[length];
        int offset = 0;
        while (offset < length) {
            int read = input.read(data, offset, length - offset);
            if (read == -1) {
                throw new IOException("Relay frame ended early");
            }
            offset += read;
        }
        return data;
    }

    private void sendResponseStart(String id, LocalResponseHeader header) throws Exception {
        JSONObject frame = new JSONObject();
        frame.put("type", "responseStart");
        frame.put("id", id);
        frame.put("status", header.status);
        frame.put("headers", header.headers);
        sendFrame(frame);
    }

    private void sendResponseChunk(String id, byte[] data, int length) throws Exception {
        JSONObject frame = new JSONObject();
        frame.put("type", "responseChunk");
        frame.put("id", id);
        frame.put("data", Base64.encodeToString(data, 0, length, Base64.NO_WRAP));
        sendFrame(frame);
    }

    private void sendResponseEnd(String id) throws Exception {
        JSONObject frame = new JSONObject();
        frame.put("type", "responseEnd");
        frame.put("id", id);
        sendFrame(frame);
    }

    private void sendResponseError(String id, String message) {
        try {
            byte[] data = ("Phone could not serve this request: " + (message == null ? "unknown error" : message))
                    .getBytes(StandardCharsets.UTF_8);
            JSONObject headers = new JSONObject();
            headers.put("content-type", "text/plain; charset=utf-8");
            headers.put("content-length", data.length);
            headers.put("cache-control", "no-store");
            LocalResponseHeader header = new LocalResponseHeader(502, headers);
            sendResponseStart(id, header);
            sendResponseChunk(id, data, data.length);
            sendResponseEnd(id);
        } catch (Exception ignored) {
        }
    }

    private boolean isConfigured() {
        return !TextUtils.isEmpty(BuildConfig.FLUX_RELAY_HOST)
                && BuildConfig.FLUX_RELAY_PORT > 0
                && !TextUtils.isEmpty(BuildConfig.FLUX_RELAY_PUBLIC_BASE);
    }

    private String preferredDisplayUrl(String relayPublicUrl, String relayFullUrl, String relayShortUrl) {
        if (!TextUtils.isEmpty(relayShortUrl)) {
            return relayShortUrl;
        }
        if (!TextUtils.isEmpty(relayPublicUrl)) {
            return relayPublicUrl;
        }
        return TextUtils.isEmpty(relayFullUrl) ? "" : relayFullUrl;
    }

    private String buildPublicUrl(String slugValue, String tokenValue) {
        String base = BuildConfig.FLUX_RELAY_PUBLIC_BASE.trim();
        String url;
        if (base.contains("{slug}")) {
            url = base.replace("{slug}", slugValue);
        } else {
            url = base.replaceAll("/+$", "") + "/" + slugValue + "/";
        }
        if (!url.endsWith("/") && !url.contains("?")) {
            url += "/";
        }
        return url + (url.contains("?") ? "&" : "?") + "token=" + tokenValue;
    }

    private String buildSlug() {
        String device = (Build.MANUFACTURER + "-" + Build.MODEL)
                .toLowerCase(Locale.US)
                .replaceAll("[^a-z0-9-]", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
        if (device.length() > 22) {
            device = device.substring(0, 22).replaceAll("-$", "");
        }
        return (TextUtils.isEmpty(device) ? "android" : device) + "-" + randomHex(4);
    }

    private String randomHex(int bytes) {
        byte[] data = new byte[bytes];
        secureRandom.nextBytes(data);
        StringBuilder builder = new StringBuilder(bytes * 2);
        for (byte value : data) {
            builder.append(String.format(Locale.US, "%02x", value & 0xff));
        }
        return builder.toString();
    }

    private String deviceName() {
        String name = (Build.MANUFACTURER + " " + Build.MODEL).trim();
        return TextUtils.isEmpty(name) ? "Android phone" : name;
    }

    private int parseStatus(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ignored) {
            return 502;
        }
    }

    private boolean isForwardableHeader(String key) {
        if (key == null) {
            return false;
        }
        String lower = key.toLowerCase(Locale.US);
        return !"connection".equals(lower)
                && !"content-length".equals(lower)
                && !"expect".equals(lower)
                && !"host".equals(lower)
                && !"keep-alive".equals(lower)
                && !"proxy-authenticate".equals(lower)
                && !"proxy-authorization".equals(lower)
                && !"te".equals(lower)
                && !"trailer".equals(lower)
                && !"transfer-encoding".equals(lower)
                && !"upgrade".equals(lower);
    }

    private boolean isResponseHeaderAllowed(String key) {
        if (key == null) {
            return false;
        }
        String lower = key.toLowerCase(Locale.US);
        return !"connection".equals(lower)
                && !"keep-alive".equals(lower)
                && !"proxy-authenticate".equals(lower)
                && !"proxy-authorization".equals(lower)
                && !"te".equals(lower)
                && !"trailer".equals(lower)
                && !"transfer-encoding".equals(lower)
                && !"upgrade".equals(lower)
                && !"set-cookie".equals(lower)
                && !"server".equals(lower);
    }

    private String headerSafe(String value) {
        return value == null ? "" : value.replace("\r", "").replace("\n", "");
    }

    private void stopRelay(String message, boolean requestStopSelf) {
        if (!stopped.compareAndSet(false, true)) {
            if (requestStopSelf) {
                stopSelf();
            }
            return;
        }
        running.set(false);
        currentRunning = false;
        currentConnected = false;
        currentPublicUrl = "";
        currentShortUrl = "";
        currentFullUrl = "";
        currentTinyUrlError = "";
        currentMessage = message;
        Thread thread = relayThread;
        if (thread != null && thread != Thread.currentThread()) {
            thread.interrupt();
        }
        closeRelaySocketAsync();
        closeAllLocalSocketsAsync();
        stopPrivateLocalServer();
        if (requestExecutor != null) {
            requestExecutor.shutdownNow();
        }
        broadcast(false, false, "", message, null);
        if (foregroundStarted) {
            foregroundStarted = false;
            stopForeground(STOP_FOREGROUND_REMOVE);
        }
        if (requestStopSelf) {
            stopSelf();
        }
    }

    private synchronized int ensurePrivateLocalServer() throws IOException {
        if (privateLocalServer != null && privateLocalPort > 0) {
            return privateLocalPort;
        }
        privateLocalServer = new LocalHttpServer(this, ShareRepository.live(this), true);
        privateLocalPort = privateLocalServer.start(0);
        return privateLocalPort;
    }

    private synchronized void stopPrivateLocalServer() {
        if (privateLocalServer != null) {
            privateLocalServer.stop();
            privateLocalServer = null;
        }
        privateLocalPort = 0;
    }

    private void closeRelaySocket() {
        closeQuietly(detachRelaySocket());
    }

    private void closeRelaySocketAsync() {
        closeSocketInBackground(detachRelaySocket());
    }

    private Socket detachRelaySocket() {
        synchronized (writeLock) {
            Socket socket = relaySocket;
            relaySocket = null;
            relayInput = null;
            relayOutput = null;
            return socket;
        }
    }

    private void closeLocalSocket(String id) {
        Socket socket = activeLocalSockets.remove(id);
        closeQuietly(socket);
    }

    private void closeAllLocalSocketsAsync() {
        List<Socket> sockets = new ArrayList<>();
        for (String id : activeLocalSockets.keySet()) {
            Socket socket = activeLocalSockets.remove(id);
            if (socket != null) {
                sockets.add(socket);
            }
        }
        if (sockets.isEmpty()) {
            return;
        }
        Thread closer = new Thread(() -> {
            for (Socket socket : sockets) {
                closeQuietly(socket);
            }
        }, "flux-global-local-close");
        closer.start();
    }

    private void closeSocketInBackground(Socket socket) {
        if (socket == null) {
            return;
        }
        Thread closer = new Thread(() -> closeQuietly(socket), "flux-global-relay-close");
        closer.start();
    }

    private void closeQuietly(Socket socket) {
        if (socket == null) {
            return;
        }
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }

    private void sleep(int delayMs) {
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private Notification notification(String text, String url) {
        PendingIntent open = openWebShareIntent(1);
        Intent stopIntent = new Intent(this, MainActivity.class)
                .setAction(MainActivity.ACTION_CONFIRM_STOP_LIVE_SHARE)
                .putExtra(MainActivity.EXTRA_OPEN_TAB, MainActivity.TAB_WEB)
                .putExtra(MainActivity.EXTRA_WEB_FLOW, MainActivity.WEB_FLOW_LIVE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent stop = PendingIntent.getActivity(
                this,
                2,
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        Notification.Builder builder = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("K-Share global link")
                .setContentText(TextUtils.isEmpty(url) ? text : text + " / tap to open")
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .setContentIntent(open)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop global link", stop)
                .setOngoing(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setSubText("Web Share");
        }
        return builder.build();
    }

    private void updateNotification(String text, String url) {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, notification(text, url));
        }
    }

    private PendingIntent openWebShareIntent(int requestCode) {
        Intent openIntent = new Intent(this, MainActivity.class)
                .setAction(MainActivity.ACTION_OPEN_WEB_SHARE)
                .putExtra(MainActivity.EXTRA_OPEN_TAB, MainActivity.TAB_WEB)
                .putExtra(MainActivity.EXTRA_WEB_FLOW, MainActivity.WEB_FLOW_LIVE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(
                this,
                requestCode,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private void ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Global web sharing", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Keeps the remote K-Share relay link connected.");
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    private void broadcast(boolean serviceRunning, boolean connected, String url, String message, String error) {
        currentRunning = serviceRunning;
        currentConnected = connected;
        if (serviceRunning) {
            currentPublicUrl = preferredDisplayUrl(url, currentFullUrl, currentShortUrl);
        } else {
            currentPublicUrl = "";
            currentShortUrl = "";
            currentFullUrl = "";
            currentTinyUrlError = "";
        }
        currentMessage = message == null ? "" : message;
        Intent status = new Intent(BROADCAST_REMOTE_STATUS);
        status.setPackage(getPackageName());
        status.putExtra(EXTRA_RUNNING, serviceRunning);
        status.putExtra(EXTRA_CONNECTED, connected);
        status.putExtra(EXTRA_PUBLIC_URL, currentPublicUrl);
        status.putExtra(EXTRA_SHORT_URL, currentShortUrl);
        status.putExtra(EXTRA_FULL_URL, currentFullUrl);
        status.putExtra(EXTRA_TINY_URL_ERROR, currentTinyUrlError);
        status.putExtra(EXTRA_MESSAGE, currentMessage);
        if (error != null) {
            status.putExtra(EXTRA_ERROR, error);
        }
        sendBroadcast(status);
    }

    private static final class LocalResponseHeader {
        final int status;
        final JSONObject headers;

        LocalResponseHeader(int status, JSONObject headers) {
            this.status = status;
            this.headers = headers;
        }
    }
}
