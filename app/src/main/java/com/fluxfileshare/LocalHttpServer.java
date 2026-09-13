package com.fluxfileshare;

import android.content.Context;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

final class LocalHttpServer {
    private static final int BUFFER_SIZE = 64 * 1024;
    private static final int MAX_REQUEST_BODY_BYTES = 64 * 1024;

    private final Context context;
    private final ShareRepository repository;
    private final boolean loopbackOnly;
    private final String accessScope;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ExecutorService clients = Executors.newCachedThreadPool();
    private ServerSocket serverSocket;
    private Thread acceptThread;
    private int port;

    LocalHttpServer(Context context, ShareRepository repository) {
        this(context, repository, false);
    }

    LocalHttpServer(Context context, ShareRepository repository, boolean loopbackOnly) {
        this(context, repository, loopbackOnly, loopbackOnly ? WebAccessManager.SCOPE_LIVE : WebAccessManager.SCOPE_LOCAL);
    }

    LocalHttpServer(Context context, ShareRepository repository, boolean loopbackOnly, String accessScope) {
        this.context = context.getApplicationContext();
        this.repository = repository;
        this.loopbackOnly = loopbackOnly;
        this.accessScope = WebAccessManager.SCOPE_LIVE.equals(accessScope)
                ? WebAccessManager.SCOPE_LIVE
                : WebAccessManager.SCOPE_LOCAL;
    }

    synchronized int start(int requestedPort) throws IOException {
        if (running.get()) {
            return port;
        }
        if (loopbackOnly) {
            serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), requestedPort));
        } else {
            serverSocket = new ServerSocket(requestedPort);
        }
        port = serverSocket.getLocalPort();
        running.set(true);
        acceptThread = new Thread(this::acceptLoop, "flux-file-share-http");
        acceptThread.start();
        return port;
    }

    synchronized void stop() {
        running.set(false);
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException ignored) {
        }
        clients.shutdownNow();
    }

    private void acceptLoop() {
        while (running.get()) {
            try {
                Socket socket = serverSocket.accept();
                clients.execute(() -> handle(socket));
            } catch (IOException ignored) {
                if (running.get()) {
                    running.set(false);
                }
            }
        }
    }

    private void handle(Socket socket) {
        try (Socket closeable = socket;
             InputStream input = new BufferedInputStream(closeable.getInputStream());
             OutputStream output = new BufferedOutputStream(closeable.getOutputStream())) {
            HttpRequest request = HttpRequest.parse(input);
            if (request == null) {
                return;
            }
            String visitorIp = visitorIp(request, closeable);
            if ("/access/request".equals(request.path) && "POST".equals(request.method)) {
                handleAccessRequest(output, request, visitorIp);
                return;
            }
            if ("/access/status".equals(request.path) && "GET".equals(request.method)) {
                handleAccessStatus(output, request);
                return;
            }
            if ("/access/session".equals(request.path) && "GET".equals(request.method)) {
                handleAccessSession(output, request);
                return;
            }
            if ("/healthz".equals(request.path) && "GET".equals(request.method)) {
                writeJson(output, 200, "OK", "{\"ok\":true,\"files\":" + repository.getFiles().size() + "}");
                return;
            }
            if (!WebAccessManager.isApproved(request.headers, accessScope)) {
                writeText(output, 200, "OK", "text/html; charset=utf-8", renderAccessGate(visitorIp));
                return;
            }
            WebAccessManager.RequestTracker tracker = WebAccessManager.beginRequest(context, request.headers, accessScope);
            try {
                if (!"GET".equals(request.method) && !"HEAD".equals(request.method)) {
                    writeText(output, 405, "Method Not Allowed", "text/plain", "Only GET, HEAD, and access approval POST are supported.");
                    return;
                }
                if ("/".equals(request.path)) {
                    writeText(output, 200, "OK", "text/html; charset=utf-8", renderIndex());
                    return;
                }
                if (request.path.startsWith("/watch/")) {
                    int index = parseIndex(request.path.substring("/watch/".length()));
                    List<SharedFile> files = repository.getFiles();
                    if (index < 0 || index >= files.size()) {
                        writeText(output, 404, "Not Found", "text/plain", "File not found.");
                        return;
                    }
                    writeText(output, 200, "OK", "text/html; charset=utf-8", renderWatch(index, files.get(index)));
                    return;
                }
                if (request.path.startsWith("/file/")) {
                    int index = parseIndex(request.path.substring("/file/".length()));
                    boolean download = request.query.containsKey("download");
                    streamFile(output, request, index, download, tracker);
                    return;
                }
                writeText(output, 404, "Not Found", "text/plain", "Route not found.");
            } finally {
                tracker.close();
            }
        } catch (Exception ignored) {
        }
    }

    private void handleAccessRequest(OutputStream output, HttpRequest request, String visitorIp) throws IOException {
        AccessPayload payload = AccessPayload.parse(request.body);
        if (payload.email.isEmpty()) {
            writeJson(output, 400, "Bad Request", "{\"ok\":false,\"error\":\"Enter a valid email address.\"}");
            return;
        }
        WebAccessManager.AccessRequest accessRequest = WebAccessManager.createRequest(
                context,
                payload.email,
                payload.location,
                payload.device,
                visitorIp,
                request.headers.get("user-agent"),
                accessScope
        );
        writeJson(output, 200, "OK", "{\"ok\":true,\"id\":\"" + jsonEscape(accessRequest.id)
                + "\",\"code\":\"" + jsonEscape(accessRequest.code) + "\"}");
    }

    private void handleAccessStatus(OutputStream output, HttpRequest request) throws IOException {
        String id = request.query.get("id");
        WebAccessManager.AccessRequest accessRequest = WebAccessManager.getRequest(id);
        if (accessRequest == null) {
            writeJson(output, 404, "Not Found", "{\"ok\":false,\"status\":\"expired\"}");
            return;
        }
        if ("approved".equals(accessRequest.status)) {
            writeJson(output, 200, "OK", "{\"ok\":true,\"status\":\"approved\",\"token\":\""
                    + jsonEscape(accessRequest.token) + "\"}");
            return;
        }
        if ("denied".equals(accessRequest.status)) {
            writeJson(output, 200, "OK", "{\"ok\":true,\"status\":\"denied\"}");
            return;
        }
        writeJson(output, 200, "OK", "{\"ok\":true,\"status\":\"pending\",\"code\":\""
                + jsonEscape(accessRequest.code) + "\"}");
    }

    private void handleAccessSession(OutputStream output, HttpRequest request) throws IOException {
        if (WebAccessManager.isApproved(request.headers, accessScope)) {
            writeJson(output, 200, "OK", "{\"ok\":true,\"status\":\"approved\"}");
            return;
        }
        writeJson(output, 401, "Unauthorized", "{\"ok\":false,\"status\":\"removed\"}");
    }

    private String visitorIp(HttpRequest request, Socket socket) {
        String forwardedFor = request.headers.get("x-forwarded-for");
        if (forwardedFor != null && !forwardedFor.trim().isEmpty()) {
            return forwardedFor.split(",", 2)[0].trim();
        }
        InetAddress address = socket.getInetAddress();
        return address == null ? "Unknown" : address.getHostAddress();
    }

    private void streamFile(OutputStream output, HttpRequest request, int index, boolean download, WebAccessManager.RequestTracker tracker) throws IOException {
        List<SharedFile> files = repository.getFiles();
        if (index < 0 || index >= files.size()) {
            writeText(output, 404, "Not Found", "text/plain", "File not found.");
            return;
        }

        SharedFile file = files.get(index);
        long total = resolveSize(file);
        Range range = Range.parse(request.headers.get("range"), total);
        long start = range.start;
        long end = range.end;
        long length = total >= 0 ? (end - start + 1) : -1;

        StringBuilder headers = new StringBuilder();
        int status = range.partial ? 206 : 200;
        headers.append("HTTP/1.1 ").append(status).append(range.partial ? " Partial Content" : " OK").append("\r\n");
        headers.append("Content-Type: ").append(file.effectiveMimeType()).append("\r\n");
        headers.append("Accept-Ranges: bytes\r\n");
        headers.append("Cache-Control: no-store\r\n");
        headers.append("Connection: close\r\n");
        headers.append("Content-Disposition: ").append(download ? "attachment" : "inline")
                .append("; filename=\"").append(headerSafe(file.name)).append("\"\r\n");
        if (total >= 0) {
            headers.append("Content-Length: ").append(length).append("\r\n");
            if (range.partial) {
                headers.append("Content-Range: bytes ").append(start).append("-").append(end).append("/").append(total).append("\r\n");
            }
        }
        headers.append("\r\n");
        output.write(headers.toString().getBytes(StandardCharsets.UTF_8));

        if ("HEAD".equals(request.method)) {
            output.flush();
            return;
        }

        try (InputStream stream = context.getContentResolver().openInputStream(file.uri)) {
            if (stream == null) {
                return;
            }
            skipFully(stream, start);
            copyLimited(stream, output, length, tracker);
        }
        output.flush();
    }

    private long resolveSize(SharedFile file) {
        if (file.size >= 0) {
            return file.size;
        }
        try (ParcelFileDescriptor descriptor = context.getContentResolver().openFileDescriptor(file.uri, "r")) {
            if (descriptor != null) {
                return descriptor.getStatSize();
            }
        } catch (Exception ignored) {
        }
        return -1;
    }

    private String renderIndex() {
        List<SharedFile> files = repository.getFiles();
        int videoCount = 0;
        int audioCount = 0;
        int fileCount = 0;
        int compressedCount = 0;
        for (SharedFile file : files) {
            if (file.isVideo()) {
                videoCount++;
            } else if (file.isAudio()) {
                audioCount++;
            } else if (file.isCompressed()) {
                compressedCount++;
            } else {
                fileCount++;
            }
        }

        Map<String, WebFolderGroup> videoGroups = new LinkedHashMap<>();
        Map<String, WebFolderGroup> fileGroups = new LinkedHashMap<>();
        Map<String, WebFolderGroup> audioGroups = new LinkedHashMap<>();
        Map<String, WebFolderGroup> compressedGroups = new LinkedHashMap<>();
        for (int i = 0; i < files.size(); i++) {
            SharedFile file = files.get(i);
            WebFileRow row = new WebFileRow(i, file, renderWebFileRow(i, file));
            String group = webTabGroup(file);
            if ("video".equals(group)) {
                appendWebFolderRow(videoGroups, row);
            } else if ("audio".equals(group)) {
                appendWebFolderRow(audioGroups, row);
            } else if ("compressed".equals(group)) {
                appendWebFolderRow(compressedGroups, row);
            } else {
                appendWebFolderRow(fileGroups, row);
            }
        }
        String librarySections;
        if (files.isEmpty()) {
            librarySections = "<section class=\"category-section\" data-section=\"all\"><div class=\"library\"><p class=\"empty\">No files are shared right now.</p></div></section>";
        } else {
            librarySections = categorySection("video", "Videos", videoCount, renderWebFolderGroups(videoGroups))
                    + categorySection("files", "Files", fileCount, renderWebFolderGroups(fileGroups))
                    + categorySection("audio", "Songs", audioCount, renderWebFolderGroups(audioGroups))
                    + categorySection("compressed", "Compressed", compressedCount, renderWebFolderGroups(compressedGroups));
        }
        String body = "<main><header class=\"hero\"><div><p>K-Share</p><h1>Shared library</h1>"
                + "<span class=\"meta\">" + files.size() + " files / " + videoCount + " videos / "
                + audioCount + " songs / " + fileCount + " files / " + compressedCount + " compressed</span></div><div class=\"hero-mark\">"
                + iconSvg("cast") + "</div></header>"
                + "<section class=\"tools\" aria-label=\"Library tools\">"
                + "<label class=\"search-box\">" + iconSvg("search") + "<input id=\"searchFiles\" type=\"search\" placeholder=\"Search shared files\" autocomplete=\"off\"></label>"
                + "<label class=\"sort-box\"><span>Sort</span><select id=\"sortFiles\"><option value=\"name\">Name</option><option value=\"size-desc\">Largest</option><option value=\"size-asc\">Smallest</option><option value=\"type\">Type</option></select></label>"
                + "</section>"
                + "<section class=\"tabs\" role=\"tablist\" aria-label=\"Shared file categories\">"
                + tabButton("all", "All", files.size(), true)
                + tabButton("video", "Videos", videoCount, false)
                + tabButton("files", "Files", fileCount, false)
                + tabButton("audio", "Songs", audioCount, false)
                + tabButton("compressed", "Compressed", compressedCount, false)
                + "</section><div class=\"result-summary\" id=\"resultSummary\" role=\"status\" aria-live=\"polite\"></div>"
                + "<div class=\"library-stack\">" + librarySections + "</div>"
                + "<p class=\"empty hidden\" id=\"empty-tab\">No files in this tab.</p></main>"
                + playerShell()
                + "<div class=\"notice hidden\" id=\"webNotice\" role=\"status\" aria-live=\"polite\"></div>"
                + webAppScript();
        return page("K-Share", body);
    }

    private void appendWebFolderRow(Map<String, WebFolderGroup> groups, WebFileRow row) {
        String label = row.file.parentFolderLabel();
        String key = label.toLowerCase(Locale.US);
        WebFolderGroup group = groups.get(key);
        if (group == null) {
            group = new WebFolderGroup(key, label);
            groups.put(key, group);
        }
        group.rows.add(row);
        if (row.file.size > 0) {
            group.totalBytes += row.file.size;
        }
    }

    private StringBuilder renderWebFolderGroups(Map<String, WebFolderGroup> groups) {
        StringBuilder html = new StringBuilder();
        for (WebFolderGroup group : groups.values()) {
            StringBuilder rows = new StringBuilder();
            for (WebFileRow row : group.rows) {
                rows.append(row.html);
            }
            html.append("<details class=\"folder group\" data-name=\"")
                    .append(escape(group.label.toLowerCase(Locale.US)))
                    .append("\" data-search=\"")
                    .append(escape(group.label.toLowerCase(Locale.US)))
                    .append("\" data-size=\"").append(group.totalBytes).append("\">")
                    .append("<summary class=\"folder-summary\"><span class=\"folder-icon\">")
                    .append(iconSvg("folder"))
                    .append("</span><span class=\"folder-copy\"><strong>")
                    .append(escape(group.label))
                    .append("</strong><small>")
                    .append(group.rows.size()).append(group.rows.size() == 1 ? " file" : " files")
                    .append(" / ").append(formatSize(group.totalBytes))
                    .append("</small></span><span class=\"folder-open\">Open</span></summary>")
                    .append("<div class=\"folder-files\">")
                    .append(rows)
                    .append("</div></details>");
        }
        return html;
    }

    private String renderWebFileRow(int index, SharedFile file) {
        String searchable = (file.name == null ? "" : file.name)
                + " " + file.parentFolderLabel()
                + " " + file.mediaLabel()
                + " " + file.category()
                + " " + file.effectiveMimeType()
                + " " + formatLabel(file);
        StringBuilder row = new StringBuilder();
        row.append("<article class=\"file group\" data-kind=\"").append(file.category())
                .append("\" data-tab-group=\"").append(webTabGroup(file))
                .append("\" data-name=\"")
                .append(escape((file.name == null ? "" : file.name).toLowerCase(Locale.US)))
                .append("\" data-search=\"")
                .append(escape(searchable.toLowerCase(Locale.US)))
                .append("\" data-size=\"").append(file.size).append("\">")
                .append("<div class=\"file-icon\">").append(iconFor(file)).append("<small>").append(escape(formatLabel(file))).append("</small></div>")
                .append("<div class=\"file-info\"><strong>").append(escape(file.name)).append("</strong>")
                .append("<span>").append(escape(file.mediaLabel())).append(" / ")
                .append(escape(file.effectiveMimeType())).append(" / ").append(formatSize(file.size)).append("</span></div>")
                .append("<nav class=\"file-actions\" aria-label=\"File actions\">");
        if (file.isPlayableMedia()) {
            row.append("<button class=\"action primary player-open\" type=\"button\" data-index=\"").append(index)
                    .append("\" data-kind=\"").append(file.isAudio() ? "audio" : "video")
                    .append("\" data-title=\"").append(escape(file.name))
                    .append("\" data-type=\"").append(escape(file.effectiveMimeType()))
                    .append("\">").append(iconSvg(file.isAudio() ? "audio" : "play"))
                    .append("<span>").append(file.isAudio() ? "Listen" : "Watch").append("</span></button>");
            row.append("<button class=\"action vlc-open\" type=\"button\" data-index=\"").append(index)
                    .append("\" data-type=\"").append(escape(file.effectiveMimeType())).append("\">")
                    .append(iconSvg("vlc")).append("<span>Open in VLC</span></button>");
        } else if (file.isDocument()) {
            row.append("<a class=\"action primary\" href=\"/file/").append(index).append("\">")
                    .append(iconSvg("document")).append("<span>Open</span></a>");
        }
        row.append("<a class=\"action\" href=\"/file/").append(index).append("\">").append(iconSvg("link")).append("<span>Stream</span></a>")
                .append("<a class=\"action\" href=\"/file/").append(index).append("?download=1\">").append(iconSvg("download")).append("<span>Download</span></a>")
                .append("</nav></article>");
        return row.toString();
    }

    private String categorySection(String key, String label, int count, StringBuilder rows) {
        return "<section class=\"category-section\" data-section=\"" + key + "\">"
                + "<div class=\"section-head\"><span>" + escape(label) + "</span><strong data-section-count=\"" + key + "\">" + count + "</strong></div>"
                + "<section class=\"library\">" + rows + "</section></section>";
    }

    private String tabButton(String key, String label, int count, boolean active) {
        return "<button class=\"tab tab-" + key + (active ? " active" : "") + "\" data-tab=\"" + key + "\" type=\"button\">"
                + "<span class=\"tab-icon\">" + tabIcon(key) + "</span><span class=\"tab-label\">" + label
                + "</span><strong>" + count + "</strong></button>";
    }

    private String renderWatch(int index, SharedFile file) {
        String player;
        if (file.isAudio()) {
            player = "<section class=\"audio-shell\"><div class=\"disc\"><span></span></div><audio controls autoplay src=\"/file/" + index + "\"></audio></section>";
        } else {
            player = "<section class=\"watch-player\"><video id=\"watchMedia\" controls autoplay playsinline preload=\"metadata\" src=\"/file/" + index + "\"></video>"
                    + "<div class=\"watch-tools\"><input id=\"watchSubtitleInput\" class=\"subtitle-input\" type=\"file\" accept=\".vtt,.srt,.ass,.ssa,text/vtt,text/plain\">"
                    + "<button class=\"action\" id=\"watchSubtitleButton\" type=\"button\">" + iconSvg("subtitles") + "<span>Subtitles</span></button>"
                    + "<span id=\"watchSubtitleStatus\" class=\"subtitle-status hidden\"></span></div></section>";
        }
        String body = "<main><header><p><a href=\"/\">Back</a></p><h1>" + escape(file.name) + "</h1>"
                + "<span class=\"meta\">" + escape(file.mediaLabel()) + " · " + escape(file.effectiveMimeType()) + " · " + formatSize(file.size) + "</span></header>"
                + player
                + "<p class=\"hint\">If this browser cannot play the codec, open the stream URL in VLC or another native player.</p>"
                + "<p class=\"download\"><button class=\"action vlc-open\" id=\"watchVlc\" type=\"button\">" + iconSvg("vlc") + "<span>Open in VLC</span></button>"
                + "<a href=\"/file/" + index + "\">Open stream URL</a><a href=\"/file/" + index + "?download=1\">Download file</a></p></main>"
                + "<div class=\"notice hidden\" id=\"webNotice\" role=\"status\" aria-live=\"polite\"></div>"
                + "<script>" + vlcLauncherFunctions() + subtitleFunctions()
                + "setupSubtitleUpload(document.getElementById('watchMedia'),document.getElementById('watchSubtitleInput'),document.getElementById('watchSubtitleButton'),document.getElementById('watchSubtitleStatus'));"
                + "document.getElementById('watchVlc').addEventListener('click',()=>launchVlc(" + index + ",'" + escapeJs(file.effectiveMimeType()) + "'));</script>";
        return page(file.name, body);
    }

    private String renderAccessGate(String visitorIp) {
        String scopeLabel = WebAccessManager.scopeLabel(accessScope);
        String body = "<main class=\"access-main\"><section class=\"access-card\">"
                + "<div class=\"access-mark\">" + iconSvg("lock") + "</div>"
                + "<p>" + escape(scopeLabel) + "</p><h1>Request access</h1>"
                + "<span class=\"meta\">Enter your email and share your browser location. The phone owner must approve before " + escape(scopeLabel) + " files open.</span>"
                + "<form id=\"accessForm\" class=\"access-form\">"
                + "<label>Email address<input id=\"accessEmail\" type=\"email\" inputmode=\"email\" autocomplete=\"email\" placeholder=\"you@example.com\" required></label>"
                + "<button id=\"accessButton\" type=\"submit\">" + iconSvg("lock") + "<span>Share location and request</span></button>"
                + "</form>"
                + "<div id=\"accessCode\" class=\"access-code hidden\"></div>"
                + "<p id=\"accessStatus\" class=\"access-status\">Visitor IP visible to phone: " + escape(visitorIp) + "</p>"
                + "</section></main>"
                + "<script>"
                + "const form=document.getElementById('accessForm'),email=document.getElementById('accessEmail'),button=document.getElementById('accessButton'),status=document.getElementById('accessStatus'),code=document.getElementById('accessCode');"
                + "function setStatus(value){status.textContent=value;}"
                + "function setBusy(value){button.disabled=value;button.style.opacity=value?'.65':'1';}"
                + "function deviceLabel(){const bits=[];bits.push(navigator.platform||'Unknown platform');bits.push(navigator.vendor||'Unknown vendor');bits.push((screen&&screen.width&&screen.height)?screen.width+'x'+screen.height:'Unknown screen');bits.push(Intl.DateTimeFormat().resolvedOptions().timeZone||'Unknown timezone');return bits.join(' / ');}"
                + "function preciseLocation(){return new Promise((resolve,reject)=>{if(!window.isSecureContext||!navigator.geolocation){resolve({label:'Precise location unavailable on this local HTTP connection. Browser IP was recorded.',precise:false});return;}navigator.geolocation.getCurrentPosition(pos=>{const c=pos.coords;resolve({label:'Lat '+c.latitude.toFixed(5)+', Lon '+c.longitude.toFixed(5)+' / accuracy '+Math.round(c.accuracy)+'m',precise:true});},err=>reject(new Error(err&&err.message?err.message:'Location permission is required.')),{enableHighAccuracy:true,timeout:15000,maximumAge:0});});}"
                + "async function readPayload(response){const text=await response.text();try{return text?JSON.parse(text):{};}catch(error){if(text&&text.indexOf('Only GET')===0)throw new Error('Approval request was rejected by the server. Deploy the latest relay/app with approval POST support, then reload this page.');throw new Error(text||('Server returned HTTP '+response.status));}}"
                + "async function requestAccess(event){event.preventDefault();const value=email.value.trim();if(!value||!value.includes('@')){setStatus('Enter a valid email address.');return;}setBusy(true);setStatus('Waiting for browser location permission...');try{const location=await preciseLocation();setStatus('Sending approval request to the phone...');const response=await fetch('/access/request',{method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify({email:value,location:location.label,device:deviceLabel(),userAgent:navigator.userAgent||''})});const payload=await readPayload(response);if(!response.ok||!payload.ok)throw new Error(payload.error||('Server returned HTTP '+response.status));code.textContent='Approval code '+payload.code;code.classList.remove('hidden');setStatus('Approval request sent. Keep this tab open while the phone owner approves.');poll(payload.id);}catch(error){setStatus(error.message||'Could not request access.');setBusy(false);}}"
                + "async function poll(id){try{const response=await fetch('/access/status?id='+encodeURIComponent(id),{cache:'no-store'});const payload=await readPayload(response);if(payload.status==='approved'&&payload.token){document.cookie='flux_access='+payload.token+'; Max-Age=43200; Path=/; SameSite=Lax';setStatus('Approved. Opening shared files...');setTimeout(()=>location.href='/',500);return;}if(payload.status==='denied'){setStatus('Access denied by the phone owner.');setBusy(false);return;}if(payload.status==='expired'){setStatus('Approval request expired. Send a new request.');setBusy(false);return;}}catch(error){}setTimeout(()=>poll(id),1800);}"
                + "form.addEventListener('submit',requestAccess);"
                + "</script>";
        return page("Request access", body);
    }

    private String page(String title, String body) {
        return "<!doctype html><html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1, viewport-fit=cover\">"
                + "<title>" + escape(title) + "</title><style>"
                + ":root{color-scheme:dark;--bg:#071013;--panel:#0e181c;--card:#122027;--card2:#172a31;--line:#27434c;--ink:#f6fbf9;--muted:#9db2b0;--teal:#3dc6be;--amber:#ffaa60;--video:#ff8a56;--files:#7e95ff;--songs:#3dc6be;--zip:#f59e0b;--all:#37d5c8;--danger:#ff7e68;--blue:#7e95ff}"
                + "*{box-sizing:border-box}body{margin:0;min-height:100vh;background:radial-gradient(circle at 12% 0%,#174e56 0,#071013 32%,#05080a 100%),linear-gradient(145deg,#071013,#101923);color:var(--ink);font-family:ui-sans-serif,system-ui,-apple-system,BlinkMacSystemFont,sans-serif}"
                + "main{width:min(1120px,100%);margin:0 auto;padding:calc(28px + env(safe-area-inset-top)) calc(18px + env(safe-area-inset-right)) calc(56px + env(safe-area-inset-bottom)) calc(18px + env(safe-area-inset-left))}.hero{display:flex;align-items:center;justify-content:space-between;gap:18px;padding:26px 0 24px;margin-bottom:18px}.hero p{margin:0 0 8px;text-transform:uppercase;font-size:12px;font-weight:900;letter-spacing:.16em;color:var(--amber)}h1{font-size:clamp(34px,8vw,72px);line-height:.95;margin:0;letter-spacing:-.05em}.hero-mark{display:grid;place-items:center;width:76px;height:76px;border:1px solid var(--line);border-radius:24px;background:linear-gradient(135deg,#102a30,#1a383f);color:var(--teal)}.hero-mark svg{width:36px;height:36px}.meta{display:block;color:var(--muted);margin-top:12px;font-size:14px}"
                + ".tabs{display:grid;grid-template-columns:repeat(5,minmax(0,1fr));gap:10px;margin:18px 0 10px}.tab{--tab:var(--all);--tab-ink:#041113;position:relative;appearance:none;display:grid;justify-items:center;align-content:center;min-height:96px;overflow:hidden;border:1px solid var(--line);border-radius:20px;background:linear-gradient(145deg,#0e181ce8,#132228d9);color:var(--ink);padding:12px 10px;text-align:center;cursor:pointer;box-shadow:0 18px 50px #00000026;transition:transform .18s ease,border-color .18s ease,background .18s ease}.tab::before{content:\"\";position:absolute;inset:0;background:radial-gradient(circle at 50% 0%,var(--tab),transparent 58%);opacity:.16;pointer-events:none}.tab>*{position:relative}.tab:hover{transform:translateY(-1px);border-color:var(--tab)}.tab-all{--tab:var(--all);--tab-ink:#03292b}.tab-video{--tab:var(--video);--tab-ink:#321409}.tab-files{--tab:var(--files);--tab-ink:#111a3d}.tab-audio{--tab:var(--songs);--tab-ink:#06383a}.tab-compressed{--tab:var(--zip);--tab-ink:#2b1700}.tab-icon{display:grid;place-items:center;width:34px;height:32px;border-radius:12px;background:#ffffff0c;margin:0 auto 8px}.tab-icon svg{width:19px;height:19px;color:var(--tab)}.tab-label{display:block;color:var(--muted);font-size:11px;font-weight:900;text-transform:uppercase;letter-spacing:.08em}.tab strong{display:block;margin-top:1px;font-size:27px;line-height:1.05}.tab.active{background:linear-gradient(135deg,var(--tab),#ffffff 175%);border-color:transparent;color:var(--tab-ink);box-shadow:0 20px 58px #00000038}.tab.active::before{opacity:0}.tab.active .tab-label,.tab.active .tab-icon svg{color:var(--tab-ink)}.tab.active .tab-icon{background:#ffffff66}.result-summary{display:flex;align-items:center;gap:8px;margin:0 0 14px;padding:10px 12px;border:1px solid var(--line);border-radius:16px;background:#0e181ccc;color:var(--muted);font-size:13px;font-weight:900}.result-summary strong{color:var(--ink)}"
                + ".tools{display:grid;grid-template-columns:1fr auto;gap:10px;margin:0 0 18px}.search-box,.sort-box{display:flex;align-items:center;gap:10px;border:1px solid var(--line);border-radius:18px;background:#0e181ccc;padding:12px 14px}.search-box svg{width:20px;height:20px;color:var(--teal)}input,select{width:100%;border:0;outline:0;background:transparent;color:var(--ink);font:inherit;font-weight:800}select{min-width:138px}.sort-box span{font-size:12px;text-transform:uppercase;letter-spacing:.1em;color:var(--muted);font-weight:900}"
                + ".library-stack{display:grid;gap:14px}.category-section{display:grid;gap:8px}.category-section[hidden]{display:none!important}.section-head{display:flex;align-items:center;justify-content:space-between;gap:10px;padding:0 4px;color:var(--muted);font-size:12px;font-weight:1000;text-transform:uppercase;letter-spacing:.12em}.section-head strong{display:grid;place-items:center;min-width:34px;height:24px;padding:0 9px;border:1px solid var(--line);border-radius:999px;background:#0e181ccc;color:var(--teal);font-size:12px;letter-spacing:0}.library{display:grid;gap:8px;border:1px solid var(--line);border-radius:24px;background:#0e181ccc;padding:8px;box-shadow:0 20px 70px #00000035}.folder{border:1px solid #27434c;border-radius:20px;background:linear-gradient(145deg,#0d181df2,#132228e8);overflow:hidden}.folder[hidden]{display:none!important}.folder-summary{display:grid;grid-template-columns:48px 1fr auto;align-items:center;gap:12px;min-height:68px;padding:10px 12px;cursor:pointer;list-style:none}.folder-summary::-webkit-details-marker{display:none}.folder-icon{display:grid;place-items:center;width:46px;height:44px;border-radius:16px;background:#123339;color:var(--teal)}.folder-icon svg{width:22px;height:22px}.folder-copy{min-width:0}.folder-copy strong{display:block;font-size:17px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.folder-copy small{display:block;margin-top:4px;color:var(--muted);font-size:12px;font-weight:800}.folder-open{display:grid;place-items:center;min-width:58px;height:34px;border:1px solid #2b6a6d;border-radius:999px;background:#0e2c31;color:#bdfbf6;font-size:12px;font-weight:1000}.folder[open] .folder-open{background:var(--teal);border-color:var(--teal);color:#041113}.folder[open] .folder-open::before{content:'Hide';font-size:0}.folder[open] .folder-open{font-size:0}.folder[open] .folder-open::after{content:'Hide';font-size:12px}.folder-files{display:grid;gap:8px;padding:0 8px 8px}.file{display:grid;grid-template-columns:62px 1fr auto;align-items:center;gap:14px;padding:12px;border:1px solid #213840;border-radius:18px;background:#081317cc}.file-icon{display:grid;place-items:center;width:58px;height:54px;border-radius:18px;background:#172a31;color:var(--ink);font-weight:900;font-size:12px}.file-icon svg{width:23px;height:23px}.file-icon small{font-size:10px;line-height:1;margin-top:2px}.file[data-kind=video] .file-icon{background:#3a2019;color:#ffb088}.file[data-kind=audio] .file-icon{background:#123339;color:#69f2e9}.file[data-kind=document] .file-icon,.file[data-kind=other] .file-icon{background:#20264d;color:#b7c2ff}.file[data-kind=compressed] .file-icon{background:#39280d;color:#ffd166}.file-info strong{display:block;font-size:17px;overflow-wrap:anywhere}.file-info span{display:block;color:var(--muted);margin-top:5px;font-size:13px}"
                + ".file-actions{display:flex;gap:8px;flex-wrap:wrap;justify-content:flex-end}.action{display:inline-flex;align-items:center;gap:7px;border:1px solid var(--line);padding:10px 12px;border-radius:14px;background:#132228;color:var(--ink);font-weight:900;text-decoration:none;white-space:nowrap;cursor:pointer;font:inherit}.action svg{width:17px;height:17px}.action.primary{background:var(--teal);border-color:var(--teal);color:#041113}.action.vlc-open{color:var(--amber);border-color:#6b4931;background:#2a201a}.empty{font-size:18px;color:var(--muted);padding:22px}.hidden{display:none!important}.notice{position:fixed;z-index:100;left:50%;bottom:calc(18px + env(safe-area-inset-bottom));transform:translateX(-50%);width:min(620px,calc(100% - 32px));padding:14px 16px;border:1px solid #6b4931;border-radius:16px;background:#241b16f2;color:var(--ink);box-shadow:0 18px 60px #0008;font-weight:800;text-align:center}"
                + ".player-overlay{position:fixed;inset:0;z-index:50;background:#020506d9;backdrop-filter:blur(12px);display:grid;place-items:center;padding:calc(18px + env(safe-area-inset-top)) calc(18px + env(safe-area-inset-right)) calc(18px + env(safe-area-inset-bottom)) calc(18px + env(safe-area-inset-left))}.player-card{width:min(960px,100%);border:1px solid var(--line);border-radius:26px;background:linear-gradient(145deg,#12232a,#091115 62%,#10161f);box-shadow:0 35px 100px #00000088;overflow:hidden}.player-head,.mini-head{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:12px 14px;border-bottom:1px solid var(--line)}.player-head strong,.mini-head strong{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.player-controls{display:flex;gap:8px;align-items:center}.subtitle-input{position:absolute;inline-size:1px;block-size:1px;opacity:0;pointer-events:none}.icon-btn{display:grid;place-items:center;width:40px;height:38px;border:1px solid var(--line);border-radius:14px;background:#132228;color:var(--ink);cursor:pointer}.icon-btn.subtitle-btn{color:var(--teal);border-color:#2b6a6d;background:#0e2c31}.icon-btn.danger{color:var(--danger);border-color:#66312c;background:#40221f}.icon-btn svg{width:19px;height:19px}.player-body{padding:12px}.player-body video,.watch-player video{width:100%;max-height:72vh;background:#000;border-radius:20px;box-shadow:inset 0 0 0 1px #ffffff12}.player-body audio{width:100%}.subtitle-status{margin-top:10px;display:inline-flex;align-items:center;gap:8px;max-width:100%;padding:8px 10px;border:1px solid #2b6a6d;border-radius:999px;background:#0e2c31;color:#bdfbf6;font-size:12px;font-weight:900}.watch-player{display:grid;gap:10px}.watch-tools{display:flex;align-items:center;gap:8px;flex-wrap:wrap}.watch-tools .subtitle-status{margin-top:0}.mini-player{position:fixed;z-index:60;right:calc(16px + env(safe-area-inset-right));bottom:calc(16px + env(safe-area-inset-bottom));width:min(420px,calc(100% - 32px - env(safe-area-inset-left) - env(safe-area-inset-right)));border:1px solid var(--line);border-radius:22px;background:#0e181cf2;box-shadow:0 24px 70px #00000088;overflow:hidden}.mini-body{padding:10px}.mini-body video{width:100%;max-height:180px;background:#000;border-radius:16px}.mini-body audio{width:100%}.login-main,.access-main{display:grid;place-items:center;min-height:100vh}.login-card,.access-card{width:min(560px,100%);border:1px solid var(--line);border-radius:30px;background:linear-gradient(145deg,#0e181cf2,#12272eeb);padding:28px;box-shadow:0 30px 90px #00000066}.login-card p,.access-card p{margin:18px 0 8px;text-transform:uppercase;font-size:12px;font-weight:900;letter-spacing:.16em;color:var(--amber)}.login-card h1,.access-card h1{font-size:clamp(42px,12vw,74px)}.login-card form,.access-form{display:grid;gap:12px;margin-top:22px}.login-card input,.access-form input{height:58px;border:1px solid var(--line);border-radius:18px;background:#132228;color:var(--ink);padding:0 16px;font-size:18px;font-weight:900}.login-card input{font-size:24px;letter-spacing:.15em;text-align:center}.access-form label{display:grid;gap:8px;color:var(--muted);font-size:12px;font-weight:900;text-transform:uppercase;letter-spacing:.1em}.login-card button,.access-form button{display:flex;align-items:center;justify-content:center;gap:10px;height:56px;border:0;border-radius:18px;background:var(--teal);color:#041113;font:inherit;font-weight:900;cursor:pointer}.login-card button svg,.access-form button svg{width:20px;height:20px}.access-mark{display:grid;place-items:center;width:64px;height:64px;border-radius:22px;background:#123339;color:var(--teal);border:1px solid #2b6a6d}.access-mark svg{width:30px;height:30px}.access-code{margin-top:18px;padding:16px;border:1px solid #6b4931;border-radius:18px;background:#2a201a;color:var(--amber);font-size:28px;font-weight:1000;text-align:center;letter-spacing:.08em}.access-status{color:var(--muted);text-transform:none!important;letter-spacing:0!important;font-size:14px!important;line-height:1.45}.login-error{display:block;color:var(--danger);margin-top:16px}.download{display:flex;gap:8px;flex-wrap:wrap;margin-top:18px}.download a{border:1px solid var(--line);padding:10px 12px;border-radius:14px;background:#132228;color:var(--ink);font-weight:900;text-decoration:none}.hint{font-size:14px;color:var(--muted)}@media(max-width:760px){main{padding-top:calc(18px + env(safe-area-inset-top))}.hero{padding:14px 0 12px}.tabs{display:flex;overflow-x:auto;gap:8px;margin-right:calc(-18px - env(safe-area-inset-right));padding-right:calc(18px + env(safe-area-inset-right));scroll-snap-type:x proximity}.tab{min-width:126px;scroll-snap-align:start}.tools{grid-template-columns:1fr}.file{grid-template-columns:52px 1fr;padding:13px}.file-icon{width:50px;height:48px}.file-actions{grid-column:1/-1;justify-content:flex-start}.hero-mark{display:none}.action span{display:none}.action{padding:10px 11px}.player-controls{gap:6px}.icon-btn{width:38px;height:36px}}"
                + "</style></head><body>" + body + "</body></html>";
    }

    private static String playerShell() {
        return "<section id=\"playerOverlay\" class=\"player-overlay hidden\" aria-hidden=\"true\">"
                + "<div class=\"player-card\"><div class=\"player-head\"><strong id=\"playerTitle\">Player</strong>"
                + "<div class=\"player-controls\">"
                + "<input id=\"subtitleInput\" class=\"subtitle-input\" type=\"file\" accept=\".vtt,.srt,.ass,.ssa,text/vtt,text/plain\">"
                + "<button class=\"icon-btn subtitle-btn\" id=\"subtitlePlayer\" type=\"button\" title=\"Upload subtitles\">" + iconSvg("subtitles") + "</button>"
                + "<button class=\"icon-btn vlc-open\" id=\"openVlcPlayer\" type=\"button\" title=\"Open in VLC\">" + iconSvg("vlc") + "</button>"
                + "<button class=\"icon-btn\" id=\"minimizePlayer\" type=\"button\" title=\"Close to pop-up player\">" + iconSvg("close") + "</button>"
                + "<button class=\"icon-btn danger\" id=\"stopPlayer\" type=\"button\" title=\"Stop playback\">" + iconSvg("stop") + "</button>"
                + "</div></div><div class=\"player-body\"><div id=\"playerMount\"></div><div id=\"subtitleStatus\" class=\"subtitle-status hidden\"></div></div></div></section>"
                + "<section id=\"miniPlayer\" class=\"mini-player hidden\" aria-live=\"polite\">"
                + "<div class=\"mini-head\"><strong id=\"miniTitle\">Now playing</strong><div class=\"player-controls\">"
                + "<button class=\"icon-btn vlc-open\" id=\"openVlcMiniPlayer\" type=\"button\" title=\"Open in VLC\">" + iconSvg("vlc") + "</button>"
                + "<button class=\"icon-btn\" id=\"expandPlayer\" type=\"button\" title=\"Open full player\">" + iconSvg("expand") + "</button>"
                + "<button class=\"icon-btn danger\" id=\"stopMiniPlayer\" type=\"button\" title=\"Stop playback\">" + iconSvg("stop") + "</button>"
                + "</div></div><div class=\"mini-body\" id=\"miniMount\"></div></section>";
    }

    private static String webAppScript() {
        return "<script>"
                + vlcLauncherFunctions()
                + subtitleFunctions()
                + "const buttons=[...document.querySelectorAll('[data-tab]')];"
                + "const files=[...document.querySelectorAll('.file')];"
                + "const folders=[...document.querySelectorAll('.folder')];"
                + "const sections=[...document.querySelectorAll('[data-section]')];"
                + "const empty=document.getElementById('empty-tab');"
                + "const search=document.getElementById('searchFiles');"
                + "const sort=document.getElementById('sortFiles');"
                + "const resultSummary=document.getElementById('resultSummary');"
                + "let activeTab='all';"
                + "const tabQueries={all:'',video:'',files:'',audio:'',compressed:''};"
                + "function tabLabel(tab){const button=buttons.find(item=>item.dataset.tab===tab);const label=button&&button.querySelector('.tab-label');return label?label.textContent:'Files';}"
                + "function compactQuery(query){return query.length>22?query.slice(0,22)+'...':query;}"
                + "function queryForTab(tab){return (tabQueries[tab]||'').trim().toLowerCase();}"
                + "function saveActiveQuery(){if(search)tabQueries[activeTab]=search.value||'';}"
                + "function syncSearchBox(){if(!search)return;search.value=tabQueries[activeTab]||'';search.placeholder='Search '+tabLabel(activeTab).toLowerCase();}"
                + "function matchesTab(file,tab){return tab==='all'||file.dataset.tabGroup===tab;}"
                + "function matchesActiveTab(file){return matchesTab(file,activeTab);}"
                + "function folderMatchesSearch(folder,query){return !query||((folder.dataset.search||folder.dataset.name||'').includes(query));}"
                + "function matchesSearch(file,query){if(!query)return true;const folder=file.closest('.folder');return ((file.dataset.search||file.dataset.name||'').includes(query))||(folder&&folderMatchesSearch(folder,query));}"
                + "function countForTab(tab,query){let count=0;files.forEach(file=>{if(matchesTab(file,tab)&&matchesSearch(file,query))count++;});return count;}"
                + "function updateTabCounts(){buttons.forEach(button=>{const tab=button.dataset.tab;const count=countForTab(tab,queryForTab(tab));const strong=button.querySelector('strong');if(strong)strong.textContent=count;} );}"
                + "function sectionFiles(section){return [...section.querySelectorAll('.file')];}"
                + "function sectionFolders(section){return [...section.querySelectorAll('.folder')];}"
                + "function sectionIsActive(section){return activeTab==='all'||section.dataset.section===activeTab;}"
                + "function updateResultSummary(shown,query){if(!resultSummary)return;const label=tabLabel(activeTab);const noun=shown===1?'result':'results';const queryLabel=query?' for \"'+compactQuery(query)+'\"':'';if(activeTab==='all'){resultSummary.textContent='All · '+shown+' '+noun+queryLabel;}else{resultSummary.textContent=label+' · '+shown+' '+noun+queryLabel;}}"
                + "function applyFilters(){let shown=0;const query=queryForTab(activeTab);sections.forEach(section=>{let sectionShown=0;const activeSection=sectionIsActive(section);sectionFolders(section).forEach(folder=>{let folderShown=0;const folderMatch=folderMatchesSearch(folder,query);[...folder.querySelectorAll('.file')].forEach(file=>{const visible=activeSection&&(folderMatch||matchesSearch(file,query));file.hidden=!visible;if(visible){shown++;sectionShown++;folderShown++;}});folder.hidden=!activeSection||folderShown===0;});const count=section.querySelector('[data-section-count]');if(count)count.textContent=sectionShown;section.hidden=!activeSection||sectionShown===0;});updateTabCounts();updateResultSummary(shown,query);empty.textContent=query?'No matches in '+tabLabel(activeTab)+'.':'No files in '+tabLabel(activeTab)+'.';empty.classList.toggle('hidden',shown!==0);}"
                + "function sortFilesInFolder(folder,value){const mount=folder.querySelector('.folder-files');if(!mount)return;const sorted=[...folder.querySelectorAll('.file')].sort((a,b)=>{if(value==='size-desc')return Number(b.dataset.size)-Number(a.dataset.size);if(value==='size-asc')return Number(a.dataset.size)-Number(b.dataset.size);if(value==='type')return a.dataset.kind.localeCompare(b.dataset.kind)||a.dataset.name.localeCompare(b.dataset.name);return a.dataset.name.localeCompare(b.dataset.name);});sorted.forEach(file=>mount.appendChild(file));}"
                + "function applySort(){const value=(sort&&sort.value)||'name';sections.forEach(section=>{const library=section.querySelector('.library');if(!library)return;const sorted=sectionFolders(section).sort((a,b)=>{if(value==='size-desc')return Number(b.dataset.size)-Number(a.dataset.size);if(value==='size-asc')return Number(a.dataset.size)-Number(b.dataset.size);return a.dataset.name.localeCompare(b.dataset.name);});sorted.forEach(folder=>{sortFilesInFolder(folder,value);library.appendChild(folder);});});applyFilters();}"
                + "buttons.forEach(button=>button.addEventListener('click',()=>{"
                + "saveActiveQuery();"
                + "activeTab=button.dataset.tab;"
                + "syncSearchBox();"
                + "buttons.forEach(item=>item.classList.toggle('active',item===button));"
                + "applyFilters();"
                + "}));"
                + "if(search)search.addEventListener('input',()=>{tabQueries[activeTab]=search.value||'';applyFilters();});"
                + "if(sort)sort.addEventListener('change',applySort);"
                + "syncSearchBox();"
                + "applyFilters();"
                + "const overlay=document.getElementById('playerOverlay');"
                + "const mini=document.getElementById('miniPlayer');"
                + "const playerMount=document.getElementById('playerMount');"
                + "const miniMount=document.getElementById('miniMount');"
                + "const playerTitle=document.getElementById('playerTitle');"
                + "const miniTitle=document.getElementById('miniTitle');"
                + "const subtitleInput=document.getElementById('subtitleInput');"
                + "const subtitleButton=document.getElementById('subtitlePlayer');"
                + "const subtitleStatus=document.getElementById('subtitleStatus');"
                + "let mediaEl=null,currentKey='',currentIndex='',currentType='';"
                + "function createMedia(kind,src,type){const el=document.createElement(kind==='audio'?'audio':'video');el.controls=true;el.autoplay=true;el.preload='metadata';if(kind==='video'){el.playsInline=true;}const source=document.createElement('source');source.src=src;source.type=type;el.appendChild(source);return el;}"
                + "function openPlayer(button){const index=button.dataset.index;const kind=button.dataset.kind;const type=button.dataset.type;const title=button.dataset.title;const src='/file/'+index;const key=kind+':'+index;if(!mediaEl||currentKey!==key){stopPlayback(false);mediaEl=createMedia(kind,src,type);currentKey=key;setSubtitleStatus(subtitleStatus,'');}currentIndex=index;currentType=type;if(subtitleButton)subtitleButton.classList.toggle('hidden',kind!=='video');if(kind!=='video')setSubtitleStatus(subtitleStatus,'');playerTitle.textContent=title;miniTitle.textContent=title;overlay.classList.remove('hidden');overlay.setAttribute('aria-hidden','false');mini.classList.add('hidden');playerMount.appendChild(mediaEl);mediaEl.play().catch(()=>{});}"
                + "function minimizePlayer(){if(!mediaEl)return;overlay.classList.add('hidden');overlay.setAttribute('aria-hidden','true');mini.classList.remove('hidden');miniMount.appendChild(mediaEl);}"
                + "function expandPlayer(){if(!mediaEl)return;mini.classList.add('hidden');overlay.classList.remove('hidden');overlay.setAttribute('aria-hidden','false');playerMount.appendChild(mediaEl);}"
                + "function stopPlayback(hide=true){if(mediaEl){removeUserSubtitles(mediaEl);mediaEl.pause();mediaEl.removeAttribute('src');mediaEl.textContent='';mediaEl.load();mediaEl.remove();mediaEl=null;currentKey='';currentIndex='';currentType='';}setSubtitleStatus(subtitleStatus,'');if(subtitleButton)subtitleButton.classList.add('hidden');if(hide){overlay.classList.add('hidden');overlay.setAttribute('aria-hidden','true');mini.classList.add('hidden');}}"
                + "setupSubtitleUpload(()=>mediaEl,subtitleInput,subtitleButton,subtitleStatus);if(subtitleButton)subtitleButton.classList.add('hidden');"
                + "document.querySelectorAll('.player-open').forEach(button=>button.addEventListener('click',()=>openPlayer(button)));"
                + "document.querySelectorAll('.file .vlc-open').forEach(button=>button.addEventListener('click',()=>launchVlc(button.dataset.index,button.dataset.type)));"
                + "document.getElementById('openVlcPlayer').addEventListener('click',()=>{if(mediaEl)mediaEl.pause();launchVlc(currentIndex,currentType);});"
                + "document.getElementById('openVlcMiniPlayer').addEventListener('click',()=>{if(mediaEl)mediaEl.pause();launchVlc(currentIndex,currentType);});"
                + "document.getElementById('minimizePlayer').addEventListener('click',minimizePlayer);"
                + "document.getElementById('expandPlayer').addEventListener('click',expandPlayer);"
                + "document.getElementById('stopPlayer').addEventListener('click',()=>stopPlayback(true));"
                + "document.getElementById('stopMiniPlayer').addEventListener('click',()=>stopPlayback(true));"
                + "document.addEventListener('keydown',event=>{if(event.key==='Escape'&&!overlay.classList.contains('hidden'))minimizePlayer();});"
                + "async function monitorAccess(){try{const response=await fetch('/access/session',{cache:'no-store'});if(response.status===401||response.status===403){document.cookie='flux_access=; Max-Age=0; Path=/; SameSite=Lax';stopPlayback(true);showWebNotice('Access removed by the phone owner. Request access again to continue.');setTimeout(()=>location.href='/',1800);return;}}catch(error){}setTimeout(monitorAccess,5000);}setTimeout(monitorAccess,5000);"
                + "</script>";
    }

    private static String vlcLauncherFunctions() {
        return "let vlcNoticeTimer;"
                + "function showWebNotice(message){const notice=document.getElementById('webNotice');if(!notice)return;notice.textContent=message;notice.classList.remove('hidden');clearTimeout(vlcNoticeTimer);vlcNoticeTimer=setTimeout(()=>notice.classList.add('hidden'),6500);}"
                + "function copyStreamUrl(value){if(navigator.clipboard&&window.isSecureContext){navigator.clipboard.writeText(value).catch(()=>{});return;}const area=document.createElement('textarea');area.value=value;area.style.position='fixed';area.style.opacity='0';document.body.appendChild(area);area.select();try{document.execCommand('copy');}catch(error){}area.remove();}"
                + "function launchVlc(index,type){if(index===undefined||index===null||index==='')return;const stream=new URL('/file/'+index,window.location.href);if(!/Android/i.test(navigator.userAgent)){copyStreamUrl(stream.href);showWebNotice('Direct VLC launch is available on Android. The network stream URL was copied for VLC: '+stream.href);return;}const scheme=stream.protocol.slice(0,-1);const mime=(type||'video/*').replace(/[^a-zA-Z0-9.+*\\/-]/g,'')||'video/*';const fallback=encodeURIComponent('https://play.google.com/store/apps/details?id=org.videolan.vlc');const intent='intent://'+stream.host+stream.pathname+stream.search+'#Intent;scheme='+scheme+';package=org.videolan.vlc;type='+mime+';S.browser_fallback_url='+fallback+';end';window.location.href=intent;}";
    }

    private static String subtitleFunctions() {
        return "function setSubtitleStatus(statusEl,message){if(!statusEl)return;statusEl.textContent=message||'';statusEl.classList.toggle('hidden',!message);}"
                + "function stripSubtitleBom(text){return text&&text.charCodeAt(0)===65279?text.slice(1):text;}"
                + "function removeUserSubtitles(media){if(!media)return;media.querySelectorAll('track[data-user-subtitle]').forEach(track=>{if(track.dataset.url)URL.revokeObjectURL(track.dataset.url);track.remove();});}"
                + "function padSubtitle(value,size){value=String(value||'0');while(value.length<size)value='0'+value;return value;}"
                + "function normalizeAssTime(value){const parts=String(value||'').trim().split(':');if(parts.length!==3)return'';const seconds=parts[2].split(/[.,]/);return padSubtitle(parts[0],2)+':'+padSubtitle(parts[1],2)+':'+padSubtitle(seconds[0],2)+'.'+padSubtitle((seconds[1]||'0').slice(0,3),3);}"
                + "function assToVtt(raw){const cues=[];stripSubtitleBom(raw).replace(/\\r/g,'').split('\\n').forEach(line=>{const trimmed=line.trim();if(!trimmed.toLowerCase().startsWith('dialogue:'))return;let rest=trimmed.slice(trimmed.indexOf(':')+1).trim();const fields=[];for(let i=0;i<9;i++){const comma=rest.indexOf(',');if(comma<0)return;fields.push(rest.slice(0,comma));rest=rest.slice(comma+1);}const start=normalizeAssTime(fields[1]);const end=normalizeAssTime(fields[2]);const text=rest.replace(/\\{[^}]*\\}/g,'').replace(/\\\\N/g,'\\n').replace(/\\\\n/g,'\\n').replace(/\\\\h/g,' ').trim();if(start&&end&&text)cues.push(start+' --> '+end+'\\n'+text);});return 'WEBVTT\\n\\n'+cues.join('\\n\\n');}"
                + "function srtToVtt(raw){return 'WEBVTT\\n\\n'+stripSubtitleBom(raw).replace(/\\r/g,'').replace(/(\\d{1,2}:\\d{2}:\\d{2}),(\\d{3})/g,'$1.$2');}"
                + "function subtitleToVtt(text,name){const clean=stripSubtitleBom(String(text||''));if(clean.replace(/^\\s+/,'').startsWith('WEBVTT'))return clean;const lower=String(name||'').toLowerCase();return lower.endsWith('.ass')||lower.endsWith('.ssa')?assToVtt(clean):srtToVtt(clean);}"
                + "function applySubtitleText(media,text,name,statusEl){if(!media||media.tagName.toLowerCase()!=='video')throw new Error('Open a video first');const vtt=subtitleToVtt(text,name);if(!vtt.includes('-->'))throw new Error('No subtitle cues found');removeUserSubtitles(media);const url=URL.createObjectURL(new Blob([vtt],{type:'text/vtt'}));const track=document.createElement('track');track.kind='subtitles';track.label=String(name||'Subtitles').replace(/\\.[^.]+$/,'')||'Subtitles';track.srclang='en';track.default=true;track.src=url;track.dataset.userSubtitle='1';track.dataset.url=url;media.appendChild(track);track.addEventListener('load',()=>{try{track.track.mode='showing';}catch(error){}});setTimeout(()=>{try{track.track.mode='showing';}catch(error){}},150);setSubtitleStatus(statusEl,'CC '+(name||'Subtitles'));}"
                + "function setupSubtitleUpload(mediaProvider,input,button,statusEl){if(!input||!button)return;button.addEventListener('click',()=>input.click());input.addEventListener('change',()=>{const file=input.files&&input.files[0];if(!file)return;const media=typeof mediaProvider==='function'?mediaProvider():mediaProvider;if(!media||media.tagName.toLowerCase()!=='video'){showWebNotice('Open video first.');input.value='';return;}const reader=new FileReader();reader.onload=()=>{try{applySubtitleText(media,reader.result,file.name,statusEl);showWebNotice('Subtitles loaded.');}catch(error){showWebNotice(error.message||'Could not load subtitles.');}finally{input.value='';}};reader.onerror=()=>{showWebNotice('Could not read subtitles.');input.value='';};reader.readAsText(file);});}";
    }

    private static String tabIcon(String key) {
        if ("video".equals(key)) {
            return iconSvg("video");
        }
        if ("audio".equals(key)) {
            return iconSvg("audio");
        }
        if ("files".equals(key) || "document".equals(key)) {
            return iconSvg("document");
        }
        if ("compressed".equals(key)) {
            return iconSvg("archive");
        }
        return iconSvg("grid");
    }

    private static String iconFor(SharedFile file) {
        String icon;
        if (file.isVideo()) {
            icon = iconSvg("video");
        } else if (file.isAudio()) {
            icon = iconSvg("audio");
        } else if (file.isCompressed()) {
            icon = iconSvg("archive");
        } else if (file.isDocument()) {
            icon = iconSvg("document");
        } else {
            icon = iconSvg("file");
        }
        return icon;
    }

    private static String webTabGroup(SharedFile file) {
        if (file.isVideo()) {
            return "video";
        }
        if (file.isAudio()) {
            return "audio";
        }
        if (file.isCompressed()) {
            return "compressed";
        }
        return "files";
    }

    private static String formatLabel(SharedFile file) {
        String name = file.name == null ? "" : file.name;
        int dot = name.lastIndexOf('.');
        if (dot >= 0 && dot < name.length() - 1) {
            String extension = name.substring(dot + 1).trim();
            if (!extension.isEmpty()) {
                return extension.length() > 5
                        ? extension.substring(0, 5).toUpperCase(Locale.US)
                        : extension.toUpperCase(Locale.US);
            }
        }
        if (file.isVideo()) {
            return "VID";
        }
        if (file.isAudio()) {
            return "AUD";
        }
        if (file.isDocument()) {
            return "DOC";
        }
        return "FILE";
    }

    private static String iconSvg(String name) {
        String path;
        if ("video".equals(name)) {
            path = "M4 5h16a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2Zm2 2 2 4h3L9 7H6Zm5 0 2 4h3l-2-4h-3Zm5 0 2 4h2V7h-4Z";
        } else if ("audio".equals(name)) {
            path = "M12 3v10.6A4 4 0 1 0 14 17V7h6V3h-8Z";
        } else if ("document".equals(name)) {
            path = "M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8l-6-6Zm-1 7V3.5L18.5 9H13ZM8 13h8v2H8v-2Zm0 4h8v2H8v-2Z";
        } else if ("archive".equals(name)) {
            path = "M6 2h12a2 2 0 0 1 2 2v16a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2Zm5 2v2h2V4h-2Zm0 4v2h2V8h-2Zm0 4v2h2v-2h-2Zm-3 5h8v2H8v-2Z";
        } else if ("play".equals(name)) {
            path = "M8 5v14l11-7L8 5Z";
        } else if ("download".equals(name)) {
            path = "M5 20h14v-2H5v2ZM19 9h-4V3H9v6H5l7 7 7-7Z";
        } else if ("link".equals(name)) {
            path = "M3.9 12a5 5 0 0 1 5-5h4v2h-4a3 3 0 0 0 0 6h4v2h-4a5 5 0 0 1-5-5Zm6 1h4v-2h-4v2Zm1-6h4a5 5 0 0 1 0 10h-4v-2h4a3 3 0 0 0 0-6h-4V7Z";
        } else if ("close".equals(name)) {
            path = "m18.3 5.7-1.4-1.4-4.9 4.9-4.9-4.9-1.4 1.4 4.9 4.9-4.9 4.9 1.4 1.4 4.9-4.9 4.9 4.9 1.4-1.4-4.9-4.9 4.9-4.9Z";
        } else if ("stop".equals(name)) {
            path = "M6 6h12v12H6V6Z";
        } else if ("minimize".equals(name)) {
            path = "M5 11h14v2H5v-2Z";
        } else if ("expand".equals(name)) {
            path = "M5 5h6v2H8.4l3.3 3.3-1.4 1.4L7 8.4V11H5V5Zm8 0h6v6h-2V8.4l-3.3 3.3-1.4-1.4L15.6 7H13V5ZM7 15.6l3.3-3.3 1.4 1.4L8.4 17H11v2H5v-6h2v2.6ZM13.7 12.3l3.3 3.3V13h2v6h-6v-2h2.6l-3.3-3.3 1.4-1.4Z";
        } else if ("cast".equals(name)) {
            path = "M3 6a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2v9h-2V6H5v2H3V6Zm0 5a8 8 0 0 1 8 8H9a6 6 0 0 0-6-6v-2Zm0 4a4 4 0 0 1 4 4H5a2 2 0 0 0-2-2v-2Zm0 3h1v1H3v-1Z";
        } else if ("grid".equals(name)) {
            path = "M4 4h7v7H4V4Zm9 0h7v7h-7V4ZM4 13h7v7H4v-7Zm9 0h7v7h-7v-7Z";
        } else if ("search".equals(name)) {
            path = "M10 4a6 6 0 1 0 3.7 10.7l4.3 4.3 1.4-1.4-4.3-4.3A6 6 0 0 0 10 4Zm0 2a4 4 0 1 1 0 8 4 4 0 0 1 0-8Z";
        } else if ("vlc".equals(name)) {
            path = "M10.4 2h3.2l1.1 4H9.3l1.1-4ZM8.8 8h6.4l1.1 4H7.7l1.1-4ZM7.1 14h9.8l1.4 5H20v3H4v-3h1.7l1.4-5Z";
        } else if ("subtitles".equals(name)) {
            path = "M4 5h16a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2Zm1 4v2h5V9H5Zm7 0v2h7V9h-7Zm-7 4v2h8v-2H5Zm10 0v2h4v-2h-4Z";
        } else if ("lock".equals(name)) {
            path = "M7 10V8a5 5 0 0 1 10 0v2h1a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2v-8a2 2 0 0 1 2-2h1Zm2 0h6V8a3 3 0 0 0-6 0v2Z";
        } else if ("folder".equals(name)) {
            path = "M3 6a2 2 0 0 1 2-2h5l2 2h7a2 2 0 0 1 2 2v1H3V6Zm0 5h18l-2 8H5l-2-8Z";
        } else {
            path = "M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8l-6-6Zm-1 7V3.5L18.5 9H13Z";
        }
        return "<svg viewBox=\"0 0 24 24\" fill=\"currentColor\" aria-hidden=\"true\"><path d=\"" + path + "\"></path></svg>";
    }

    private static void writeText(OutputStream output, int code, String status, String contentType, String body) throws IOException {
        byte[] data = body.getBytes(StandardCharsets.UTF_8);
        String headers = "HTTP/1.1 " + code + " " + status + "\r\n"
                + "Content-Type: " + contentType + "\r\n"
                + "Content-Length: " + data.length + "\r\n"
                + "Cache-Control: no-store\r\n"
                + "Connection: close\r\n\r\n";
        output.write(headers.getBytes(StandardCharsets.UTF_8));
        output.write(data);
        output.flush();
    }

    private static void writeJson(OutputStream output, int code, String status, String body) throws IOException {
        writeText(output, code, status, "application/json; charset=utf-8", body);
    }

    private static void skipFully(InputStream stream, long bytes) throws IOException {
        long remaining = bytes;
        while (remaining > 0) {
            long skipped = stream.skip(remaining);
            if (skipped <= 0 && stream.read() == -1) {
                return;
            }
            remaining -= Math.max(1, skipped);
        }
    }

    private static void copyLimited(InputStream input, OutputStream output, long maxBytes, WebAccessManager.RequestTracker tracker) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        long remaining = maxBytes;
        while (maxBytes < 0 || remaining > 0) {
            if (tracker != null && tracker.isRevoked()) {
                throw new IOException("Access session was removed.");
            }
            int wanted = maxBytes < 0 ? buffer.length : (int) Math.min(buffer.length, remaining);
            int read = input.read(buffer, 0, wanted);
            if (read == -1) {
                return;
            }
            output.write(buffer, 0, read);
            if (tracker != null) {
                tracker.addBytes(read);
            }
            if (maxBytes >= 0) {
                remaining -= read;
            }
        }
    }

    private static int parseIndex(String raw) {
        try {
            String value = raw.split("/", 2)[0];
            return Integer.parseInt(value);
        } catch (Exception e) {
            return -1;
        }
    }

    private static String formatSize(long bytes) {
        if (bytes < 0) {
            return "Unknown size";
        }
        String[] units = {"B", "KB", "MB", "GB"};
        double value = bytes;
        int unit = 0;
        while (value >= 1024 && unit < units.length - 1) {
            value /= 1024;
            unit++;
        }
        return String.format(Locale.US, value >= 10 || unit == 0 ? "%.0f %s" : "%.1f %s", value, units[unit]);
    }

    private static String escape(String raw) {
        return raw == null ? "" : raw.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String escapeJs(String raw) {
        return raw == null ? "" : raw.replace("\\", "\\\\").replace("'", "\\'").replace("\r", "").replace("\n", "");
    }

    private static String jsonEscape(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\t", "\\t");
    }

    private static String headerSafe(String raw) {
        return raw == null ? "file" : raw.replace("\\", "_").replace("\"", "_").replace("\r", "_").replace("\n", "_");
    }

    private static final class Range {
        final boolean partial;
        final long start;
        final long end;

        Range(boolean partial, long start, long end) {
            this.partial = partial;
            this.start = start;
            this.end = end;
        }

        static Range parse(String header, long total) {
            if (header == null || !header.startsWith("bytes=") || total <= 0) {
                return new Range(false, 0, Math.max(0, total - 1));
            }
            try {
                String[] parts = header.substring("bytes=".length()).split("-", 2);
                long start = parts[0].isEmpty() ? 0 : Long.parseLong(parts[0]);
                long end = parts.length > 1 && !parts[1].isEmpty() ? Long.parseLong(parts[1]) : total - 1;
                start = Math.max(0, Math.min(start, total - 1));
                end = Math.max(start, Math.min(end, total - 1));
                return new Range(true, start, end);
            } catch (NumberFormatException e) {
                return new Range(false, 0, total - 1);
            }
        }
    }

    private static final class AccessPayload {
        final String email;
        final String location;
        final String device;

        AccessPayload(String email, String location, String device) {
            this.email = email;
            this.location = location;
            this.device = device;
        }

        static AccessPayload parse(String body) {
            if (body == null) {
                return new AccessPayload("", "", "");
            }
            try {
                JSONObject json = new JSONObject(body);
                return new AccessPayload(
                        normalize(json.optString("email", ""), 120).toLowerCase(Locale.US),
                        normalize(json.optString("location", ""), 240),
                        normalize(json.optString("device", ""), 160)
                );
            } catch (Exception ignored) {
                Map<String, String> form = new HashMap<>();
                for (String pair : body.split("&")) {
                    if (pair.isEmpty()) {
                        continue;
                    }
                    String[] keyValue = pair.split("=", 2);
                    form.put(HttpRequest.urlDecode(keyValue[0]), keyValue.length > 1 ? HttpRequest.urlDecode(keyValue[1]) : "");
                }
                return new AccessPayload(
                        normalize(form.get("email"), 120).toLowerCase(Locale.US),
                        normalize(form.get("location"), 240),
                        normalize(form.get("device"), 160)
                );
            }
        }

        private static String normalize(String value, int max) {
            if (value == null) {
                return "";
            }
            String cleaned = value.replace("\r", " ").replace("\n", " ").trim();
            return cleaned.length() > max ? cleaned.substring(0, max) : cleaned;
        }
    }

    private static final class HttpRequest {
        final String method;
        final String path;
        final Map<String, String> query;
        final Map<String, String> headers;
        final String body;

        HttpRequest(String method, String path, Map<String, String> query, Map<String, String> headers, String body) {
            this.method = method;
            this.path = path;
            this.query = query;
            this.headers = headers;
            this.body = body;
        }

        static HttpRequest parse(InputStream input) throws IOException {
            String requestLine = readLine(input);
            if (requestLine == null || requestLine.trim().isEmpty()) {
                return null;
            }
            String[] parts = requestLine.split(" ");
            if (parts.length < 2) {
                return null;
            }
            Map<String, String> headers = new HashMap<>();
            String line;
            while ((line = readLine(input)) != null && !line.isEmpty()) {
                int separator = line.indexOf(':');
                if (separator > 0) {
                    headers.put(line.substring(0, separator).trim().toLowerCase(Locale.US), line.substring(separator + 1).trim());
                }
            }
            String target = parts[1];
            String path = target;
            Map<String, String> query = new HashMap<>();
            int queryStart = target.indexOf('?');
            if (queryStart >= 0) {
                path = target.substring(0, queryStart);
                for (String pair : target.substring(queryStart + 1).split("&")) {
                    if (!pair.isEmpty()) {
                        String[] keyValue = pair.split("=", 2);
                        query.put(urlDecode(keyValue[0]), keyValue.length > 1 ? urlDecode(keyValue[1]) : "");
                    }
                }
            }
            int bodyLength = parseContentLength(headers.get("content-length"));
            String body = "";
            if (bodyLength > 0) {
                if (bodyLength > MAX_REQUEST_BODY_BYTES) {
                    throw new IOException("Request body is too large");
                }
                byte[] bytes = new byte[bodyLength];
                int offset = 0;
                while (offset < bodyLength) {
                    int read = input.read(bytes, offset, bodyLength - offset);
                    if (read == -1) {
                        break;
                    }
                    offset += read;
                }
                body = new String(bytes, 0, offset, StandardCharsets.UTF_8);
            }
            return new HttpRequest(parts[0].toUpperCase(Locale.US), path, query, headers, body);
        }

        private static int parseContentLength(String value) throws IOException {
            if (value == null || value.trim().isEmpty()) {
                return 0;
            }
            try {
                return Math.max(0, Integer.parseInt(value.trim()));
            } catch (NumberFormatException error) {
                throw new IOException("Invalid content length");
            }
        }

        private static String readLine(InputStream input) throws IOException {
            StringBuilder line = new StringBuilder();
            int value;
            while ((value = input.read()) != -1) {
                if (value == '\n') {
                    break;
                }
                if (value != '\r') {
                    line.append((char) value);
                }
                if (line.length() > 8192) {
                    throw new IOException("Header line too long");
                }
            }
            return value == -1 && line.length() == 0 ? null : line.toString();
        }

        static String urlDecode(String raw) {
            try {
                return URLDecoder.decode(raw, "UTF-8");
            } catch (Exception e) {
                return raw;
            }
        }
    }

    private static final class WebFileRow {
        final int index;
        final SharedFile file;
        final String html;

        WebFileRow(int index, SharedFile file, String html) {
            this.index = index;
            this.file = file;
            this.html = html;
        }
    }

    private static final class WebFolderGroup {
        final String key;
        final String label;
        final List<WebFileRow> rows = new ArrayList<>();
        long totalBytes;

        WebFolderGroup(String key, String label) {
            this.key = key == null ? "" : key;
            this.label = label == null || label.trim().isEmpty() ? "Selected files" : label;
        }
    }
}
