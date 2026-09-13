package com.fluxfileshare;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.text.TextUtils;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class WebAccessManager {
    static final String BROADCAST_ACCESS_REQUEST = "com.fluxfileshare.WEB_ACCESS_REQUEST";
    static final String BROADCAST_SESSION_STATUS = "com.fluxfileshare.WEB_ACCESS_SESSION_STATUS";
    static final String EXTRA_REQUEST_ID = "request_id";
    static final String EXTRA_EMAIL = "email";
    static final String EXTRA_LOCATION = "location";
    static final String EXTRA_DEVICE = "device";
    static final String EXTRA_IP = "ip";
    static final String EXTRA_USER_AGENT = "user_agent";
    static final String EXTRA_CODE = "code";
    static final String EXTRA_SCOPE = "scope";
    static final String EXTRA_SESSION_COUNT = "session_count";
    static final String EXTRA_ACTIVE_COUNT = "active_count";
    static final String EXTRA_TOTAL_BYTES = "total_bytes";
    static final String EXTRA_LOCAL_SESSION_COUNT = "local_session_count";
    static final String EXTRA_LOCAL_ACTIVE_COUNT = "local_active_count";
    static final String EXTRA_LOCAL_TOTAL_BYTES = "local_total_bytes";
    static final String EXTRA_LIVE_SESSION_COUNT = "live_session_count";
    static final String EXTRA_LIVE_ACTIVE_COUNT = "live_active_count";
    static final String EXTRA_LIVE_TOTAL_BYTES = "live_total_bytes";
    static final String SCOPE_LOCAL = "local";
    static final String SCOPE_LIVE = "live";

    private static final String CHANNEL_ID = "web_access_requests";
    private static final int NOTIFICATION_ID = 1005;
    private static final long REQUEST_TTL_MS = 5 * 60 * 1000L;
    private static final long SESSION_TTL_MS = 12 * 60 * 60 * 1000L;
    private static final int TOKEN_BYTES = 24;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final ConcurrentHashMap<String, AccessRequest> REQUESTS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, AccessSession> SESSIONS = new ConcurrentHashMap<>();

    private WebAccessManager() {
    }

    static AccessRequest createRequest(
            Context context,
            String email,
            String location,
            String device,
            String ip,
            String userAgent,
            String scope
    ) {
        cleanup();
        AccessRequest request = new AccessRequest(
                randomHex(16),
                randomCode(),
                normalizeScope(scope),
                normalizeEmail(email),
                trim(location, 240),
                trim(device, 160),
                trim(ip, 80),
                trim(userAgent, 240),
                System.currentTimeMillis()
        );
        REQUESTS.put(request.id, request);
        Intent intent = new Intent(BROADCAST_ACCESS_REQUEST);
        intent.setPackage(context.getPackageName());
        intent.putExtra(EXTRA_REQUEST_ID, request.id);
        intent.putExtra(EXTRA_EMAIL, request.email);
        intent.putExtra(EXTRA_LOCATION, request.location);
        intent.putExtra(EXTRA_DEVICE, request.device);
        intent.putExtra(EXTRA_IP, request.ip);
        intent.putExtra(EXTRA_USER_AGENT, request.userAgent);
        intent.putExtra(EXTRA_CODE, request.code);
        intent.putExtra(EXTRA_SCOPE, request.scope);
        context.sendBroadcast(intent);
        notifyAccessRequest(context, request);
        return request;
    }

    static AccessDecision approve(String requestId) {
        AccessRequest request = REQUESTS.get(requestId);
        if (request == null || request.expired()) {
            REQUESTS.remove(requestId);
            return AccessDecision.expired();
        }
        String token = randomHex(TOKEN_BYTES);
        request.status = "approved";
        request.token = token;
        request.decidedAt = System.currentTimeMillis();
        SESSIONS.put(token, new AccessSession(request, token, request.decidedAt + SESSION_TTL_MS));
        return AccessDecision.approved(token);
    }

    static void deny(String requestId) {
        AccessRequest request = REQUESTS.get(requestId);
        if (request != null) {
            request.status = "denied";
            request.decidedAt = System.currentTimeMillis();
        }
    }

    static void clearNotification(Context context) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.cancel(NOTIFICATION_ID);
        }
    }

    static AccessRequest getRequest(String requestId) {
        cleanup();
        return REQUESTS.get(requestId);
    }

    static List<AccessRequest> pendingRequests() {
        cleanup();
        List<AccessRequest> pending = new ArrayList<>();
        for (AccessRequest request : REQUESTS.values()) {
            if ("pending".equals(request.status) && !request.expired()) {
                pending.add(request);
            }
        }
        return pending;
    }

    static boolean isApproved(Map<String, String> headers) {
        return isApproved(headers, SCOPE_LOCAL);
    }

    static boolean isApproved(Map<String, String> headers, String scope) {
        cleanup();
        String token = accessTokenFromHeaders(headers);
        if (TextUtils.isEmpty(token)) {
            return false;
        }
        String normalizedScope = normalizeScope(scope);
        AccessSession session = SESSIONS.get(token);
        if (session == null || session.revoked || session.expiresAt < System.currentTimeMillis()) {
            SESSIONS.remove(token);
            return false;
        }
        if (!normalizedScope.equals(session.scope)) {
            return false;
        }
        return true;
    }

    static RequestTracker beginRequest(Context context, Map<String, String> headers) {
        return beginRequest(context, headers, SCOPE_LOCAL);
    }

    static RequestTracker beginRequest(Context context, Map<String, String> headers, String scope) {
        cleanup();
        String token = accessTokenFromHeaders(headers);
        if (TextUtils.isEmpty(token)) {
            return RequestTracker.empty();
        }
        String normalizedScope = normalizeScope(scope);
        AccessSession session = SESSIONS.get(token);
        if (session == null || session.revoked || session.expiresAt < System.currentTimeMillis()) {
            SESSIONS.remove(token);
            broadcastSessionStatus(context);
            return RequestTracker.empty();
        }
        if (!normalizedScope.equals(session.scope)) {
            return RequestTracker.empty();
        }
        session.beginRequest();
        broadcastSessionStatus(context);
        return new RequestTracker(context.getApplicationContext(), session);
    }

    static List<AccessSession> activeSessions() {
        return activeSessions(null);
    }

    static List<AccessSession> activeSessions(String scope) {
        cleanup();
        String normalizedScope = scope == null ? "" : normalizeScope(scope);
        List<AccessSession> sessions = new ArrayList<>();
        for (AccessSession session : SESSIONS.values()) {
            if (TextUtils.isEmpty(normalizedScope) || normalizedScope.equals(session.scope)) {
                sessions.add(session);
            }
        }
        return sessions;
    }

    static boolean revokeSession(Context context, String token) {
        if (TextUtils.isEmpty(token)) {
            return false;
        }
        cleanup();
        AccessSession session = SESSIONS.remove(token);
        if (session == null) {
            broadcastSessionStatus(context);
            return false;
        }
        session.revoke();
        broadcastSessionStatus(context);
        return true;
    }

    static int connectedCount() {
        return connectedCount(null);
    }

    static int connectedCount(String scope) {
        cleanup();
        if (TextUtils.isEmpty(scope)) {
            return SESSIONS.size();
        }
        return activeSessions(scope).size();
    }

    static int activeCount() {
        return activeCount(null);
    }

    static int activeCount(String scope) {
        cleanup();
        int active = 0;
        String normalizedScope = TextUtils.isEmpty(scope) ? "" : normalizeScope(scope);
        for (AccessSession session : SESSIONS.values()) {
            if (!TextUtils.isEmpty(normalizedScope) && !normalizedScope.equals(session.scope)) {
                continue;
            }
            if (session.activeRequests > 0) {
                active++;
            }
        }
        return active;
    }

    static long totalBytesServed() {
        return totalBytesServed(null);
    }

    static long totalBytesServed(String scope) {
        cleanup();
        long total = 0;
        String normalizedScope = TextUtils.isEmpty(scope) ? "" : normalizeScope(scope);
        for (AccessSession session : SESSIONS.values()) {
            if (!TextUtils.isEmpty(normalizedScope) && !normalizedScope.equals(session.scope)) {
                continue;
            }
            total += session.bytesServed;
        }
        return total;
    }

    static void broadcastSessionStatus(Context context) {
        if (context == null) {
            return;
        }
        cleanup();
        Intent intent = new Intent(BROADCAST_SESSION_STATUS);
        intent.setPackage(context.getPackageName());
        intent.putExtra(EXTRA_SESSION_COUNT, connectedCount());
        intent.putExtra(EXTRA_ACTIVE_COUNT, activeCount());
        intent.putExtra(EXTRA_TOTAL_BYTES, totalBytesServed());
        intent.putExtra(EXTRA_LOCAL_SESSION_COUNT, connectedCount(SCOPE_LOCAL));
        intent.putExtra(EXTRA_LOCAL_ACTIVE_COUNT, activeCount(SCOPE_LOCAL));
        intent.putExtra(EXTRA_LOCAL_TOTAL_BYTES, totalBytesServed(SCOPE_LOCAL));
        intent.putExtra(EXTRA_LIVE_SESSION_COUNT, connectedCount(SCOPE_LIVE));
        intent.putExtra(EXTRA_LIVE_ACTIVE_COUNT, activeCount(SCOPE_LIVE));
        intent.putExtra(EXTRA_LIVE_TOTAL_BYTES, totalBytesServed(SCOPE_LIVE));
        context.sendBroadcast(intent);
    }

    static String accessTokenFromHeaders(Map<String, String> headers) {
        if (headers == null) {
            return "";
        }
        String cookie = headers.get("cookie");
        if (TextUtils.isEmpty(cookie)) {
            return "";
        }
        String[] parts = cookie.split(";");
        for (String part : parts) {
            String trimmed = part.trim();
            int separator = trimmed.indexOf('=');
            if (separator <= 0) {
                continue;
            }
            String name = trimmed.substring(0, separator).trim();
            if ("flux_access".equals(name)) {
                return trimmed.substring(separator + 1).trim();
            }
        }
        return "";
    }

    private static void cleanup() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, AccessRequest>> requestIterator = REQUESTS.entrySet().iterator();
        while (requestIterator.hasNext()) {
            AccessRequest request = requestIterator.next().getValue();
            if (request.expired() && !"approved".equals(request.status)) {
                requestIterator.remove();
            }
        }
        Iterator<Map.Entry<String, AccessSession>> sessionIterator = SESSIONS.entrySet().iterator();
        while (sessionIterator.hasNext()) {
            AccessSession session = sessionIterator.next().getValue();
            if (session.revoked || session.expiresAt < now) {
                sessionIterator.remove();
            }
        }
    }

    private static String normalizeEmail(String value) {
        String email = trim(value, 120).toLowerCase(Locale.US);
        return email.contains("@") ? email : "";
    }

    private static String normalizeScope(String value) {
        return SCOPE_LIVE.equals(value) ? SCOPE_LIVE : SCOPE_LOCAL;
    }

    static String scopeLabel(String scope) {
        return SCOPE_LIVE.equals(normalizeScope(scope)) ? "Live Share" : "Local Share";
    }

    private static String trim(String value, int max) {
        if (value == null) {
            return "";
        }
        String cleaned = value.replace("\r", " ").replace("\n", " ").trim();
        return cleaned.length() > max ? cleaned.substring(0, max) : cleaned;
    }

    private static String randomCode() {
        return String.format(Locale.US, "%02d", RANDOM.nextInt(90) + 10);
    }

    private static String randomHex(int bytes) {
        byte[] data = new byte[bytes];
        RANDOM.nextBytes(data);
        StringBuilder builder = new StringBuilder(bytes * 2);
        for (byte value : data) {
            builder.append(String.format(Locale.US, "%02x", value & 0xff));
        }
        return builder.toString();
    }

    private static void notifyAccessRequest(Context context, AccessRequest request) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Web access approvals",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Requests from people trying to open your shared files.");
            manager.createNotificationChannel(channel);
        }
        Intent openIntent = new Intent(context, MainActivity.class)
                .setAction(MainActivity.ACTION_OPEN_WEB_SHARE)
                .putExtra(MainActivity.EXTRA_OPEN_TAB, MainActivity.TAB_WEB)
                .putExtra(
                        MainActivity.EXTRA_WEB_FLOW,
                        SCOPE_LIVE.equals(request.scope) ? MainActivity.WEB_FLOW_LIVE : MainActivity.WEB_FLOW_LOCAL
                )
                .putExtra(MainActivity.EXTRA_WEB_ACCESS_REQUEST_ID, request.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                1006 + ((request.id.hashCode() & 0x7fffffff) % 10000),
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(context, CHANNEL_ID)
                : new Notification.Builder(context);
        builder.setContentTitle("Approve web access?")
                .setContentText(scopeLabel(request.scope) + ": " + request.email + " requested access. Code " + request.code)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_ALARM)
                .setDefaults(Notification.DEFAULT_SOUND | Notification.DEFAULT_VIBRATE)
                .setFullScreenIntent(pendingIntent, true)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setPriority(Notification.PRIORITY_HIGH);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setTimeoutAfter(REQUEST_TTL_MS);
        }
        try {
            manager.notify(NOTIFICATION_ID, builder.build());
        } catch (SecurityException ignored) {
        }
    }

    static final class AccessRequest {
        final String id;
        final String code;
        final String scope;
        final String email;
        final String location;
        final String device;
        final String ip;
        final String userAgent;
        final long createdAt;
        volatile String status = "pending";
        volatile String token = "";
        volatile long decidedAt;

        AccessRequest(
                String id,
                String code,
                String scope,
                String email,
                String location,
                String device,
                String ip,
                String userAgent,
                long createdAt
        ) {
            this.id = id;
            this.code = code;
            this.scope = scope;
            this.email = email;
            this.location = location;
            this.device = device;
            this.ip = ip;
            this.userAgent = userAgent;
            this.createdAt = createdAt;
        }

        boolean expired() {
            return System.currentTimeMillis() - createdAt > REQUEST_TTL_MS;
        }
    }

    static final class AccessDecision {
        final String status;
        final String token;

        private AccessDecision(String status, String token) {
            this.status = status;
            this.token = token;
        }

        static AccessDecision approved(String token) {
            return new AccessDecision("approved", token);
        }

        static AccessDecision expired() {
            return new AccessDecision("expired", "");
        }
    }

    static final class AccessSession {
        final String token;
        final String scope;
        final String email;
        final String location;
        final String device;
        final String ip;
        final String userAgent;
        final long approvedAt;
        final long expiresAt;
        volatile long lastSeenAt;
        volatile long bytesServed;
        volatile int activeRequests;
        volatile int requestCount;
        volatile boolean revoked;

        AccessSession(AccessRequest request, String token, long expiresAt) {
            this.token = token;
            this.scope = request.scope;
            this.email = request.email;
            this.location = request.location;
            this.device = request.device;
            this.ip = request.ip;
            this.userAgent = request.userAgent;
            this.approvedAt = System.currentTimeMillis();
            this.lastSeenAt = approvedAt;
            this.expiresAt = expiresAt;
        }

        synchronized void beginRequest() {
            activeRequests++;
            requestCount++;
            lastSeenAt = System.currentTimeMillis();
        }

        synchronized void addBytes(long bytes) {
            if (bytes <= 0) {
                return;
            }
            bytesServed += bytes;
            lastSeenAt = System.currentTimeMillis();
        }

        synchronized void endRequest() {
            activeRequests = Math.max(0, activeRequests - 1);
            lastSeenAt = System.currentTimeMillis();
        }

        synchronized void revoke() {
            revoked = true;
            lastSeenAt = System.currentTimeMillis();
        }
    }

    static final class RequestTracker implements AutoCloseable {
        private final Context context;
        private final AccessSession session;
        private boolean closed;
        private long lastBroadcastAt;

        private RequestTracker(Context context, AccessSession session) {
            this.context = context;
            this.session = session;
        }

        static RequestTracker empty() {
            return new RequestTracker(null, null);
        }

        void addBytes(long bytes) {
            if (session != null) {
                session.addBytes(bytes);
                long now = System.currentTimeMillis();
                if (context != null && now - lastBroadcastAt > 1500) {
                    lastBroadcastAt = now;
                    broadcastSessionStatus(context);
                }
            }
        }

        boolean isRevoked() {
            return session != null && (session.revoked || !SESSIONS.containsKey(session.token));
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            if (session != null) {
                session.endRequest();
                broadcastSessionStatus(context);
            }
        }
    }
}
