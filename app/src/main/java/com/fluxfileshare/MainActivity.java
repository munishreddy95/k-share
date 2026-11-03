package com.fluxfileshare;

import android.annotation.SuppressLint;
import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.Context;
import android.content.ContentValues;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.DhcpInfo;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.webkit.MimeTypeMap;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.journeyapps.barcodescanner.BarcodeEncoder;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MainActivity extends Activity {
    private static final String TAG = "KShare";
    static final String ACTION_OPEN_WEB_SHARE = "com.fluxfileshare.OPEN_WEB_SHARE";
    static final String ACTION_CONFIRM_STOP_LOCAL_SHARE = "com.fluxfileshare.CONFIRM_STOP_LOCAL_SHARE";
    static final String ACTION_CONFIRM_STOP_LIVE_SHARE = "com.fluxfileshare.CONFIRM_STOP_LIVE_SHARE";
    static final String EXTRA_OPEN_TAB = "open_tab";
    static final String EXTRA_WEB_FLOW = "web_flow";
    static final String EXTRA_WEB_ACCESS_REQUEST_ID = "web_access_request_id";
    static final String TAB_WEB = "web";
    static final String WEB_FLOW_LOCAL = "local";
    static final String WEB_FLOW_LIVE = "live";
    private static final int REQUEST_NOTIFICATIONS = 43;
    private static final int REQUEST_MEDIA_PERMISSIONS = 44;
    private static final int REQUEST_NEARBY_PERMISSIONS = 46;
    private static final int REQUEST_NEARBY_FILES = 47;
    private static final int REQUEST_CAMERA = 48;
    private static final int REQUEST_QR_SCAN = 49;
    private static final int REQUEST_DOCUMENT_STORAGE = 50;
    private static final int NEARBY_SCAN_RECEIVER_QR = 1;
    private static final int NEARBY_SCAN_DESKTOP_QR = 2;
    private static final int FILE_SCAN_BATCH_SIZE = 100;
    private static final int FILE_RENDER_PAGE_SIZE = 100;
    private static final String[] DOCUMENT_MIME_FILTERS = {
            "text/%",
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/rtf",
            "application/json",
            "application/xml",
            "application/vnd.oasis.opendocument.text",
            "application/vnd.oasis.opendocument.spreadsheet",
            "application/vnd.oasis.opendocument.presentation"
    };
    private static final String[] DOCUMENT_EXTENSION_FILTERS = {
            "%.pdf",
            "%.doc",
            "%.docx",
            "%.xls",
            "%.xlsx",
            "%.ppt",
            "%.pptx",
            "%.txt",
            "%.csv",
            "%.md",
            "%.rtf",
            "%.json",
            "%.xml",
            "%.log",
            "%.odt",
            "%.ods",
            "%.odp"
    };
    private static final String[] COMPRESSED_MIME_FILTERS = {
            "application/zip",
            "application/x-zip-compressed",
            "application/vnd.rar",
            "application/x-rar-compressed",
            "application/x-7z-compressed",
            "application/gzip",
            "application/x-gzip",
            "application/x-tar",
            "application/x-bzip2",
            "application/x-xz",
            "application/java-archive",
            "application/vnd.android.package-archive"
    };
    private static final String[] COMPRESSED_EXTENSION_FILTERS = {
            "%.zip",
            "%.rar",
            "%.7z",
            "%.tar",
            "%.gz",
            "%.tgz",
            "%.bz2",
            "%.xz",
            "%.jar",
            "%.apk"
    };
    private static final String FILE_CATEGORY_VIDEOS = "videos";
    private static final String FILE_CATEGORY_FILES = "files";
    private static final String FILE_CATEGORY_SONGS = "songs";
    private static final String FILE_CATEGORY_COMPRESSED = "compressed";
    private static final int NEARBY_TRANSFER_PORT = 8988;
    private static final int NEARBY_QR_SEND_PORT = 8990;
    private static final int NEARBY_CONTROL_PORT = 8991;
    private static final int LAN_DISCOVERY_PORT = 8989;
    private static final int TRANSFER_BUFFER_SIZE = 64 * 1024;
    private static final String LAN_DISCOVERY_MAGIC = "FLUX_LAN_V1";
    private static final String NEARBY_CONNECTED_EVENT = "FLUX_NEARBY_CONNECTED_V1";
    private static final String NEARBY_TRANSFER_MAGIC = "FLUX_NEARBY_V4";
    private static final String NEARBY_QUEUE_MAGIC = "FLUX_NEARBY_QUEUE_V1";
    private static final String NEARBY_PRESENCE_MAGIC = "FLUX_NEARBY_PRESENCE_V1";
    private static final String NEARBY_CANCEL_MAGIC = "FLUX_NEARBY_CANCEL_V1";
    private static final String NEARBY_FILE_CANCEL_MAGIC = "FLUX_NEARBY_FILE_CANCEL_V1";
    private static final String NEARBY_PING_MAGIC = "FLUX_NEARBY_PING_V1";
    private static final String NEARBY_PONG_MAGIC = "FLUX_NEARBY_PONG_V1";
    private static final String NEARBY_ACK_MAGIC = "FLUX_NEARBY_ACK_V1";
    private static final String NEARBY_SESSION_MISMATCH_MAGIC = "FLUX_NEARBY_SESSION_MISMATCH_V1";
    private static final String NEARBY_SESSION_QUERY_KEY = "server";
    private static final String NEARBY_SESSION_ERROR = "Different Nearby session. Refresh QR and scan again.";
    private static final int NEARBY_CHUNK_DONE = -1;
    private static final int NEARBY_CHUNK_CANCELLED = -2;
    private static final long NEARBY_RECEIVER_CANCEL_OFFSET = -1L;
    private static final int NEARBY_MESSAGE_PRESENCE = 0;
    private static final int NEARBY_MESSAGE_TRANSFER = 1;
    private static final int NEARBY_MESSAGE_CANCEL = 2;
    private static final int NEARBY_MESSAGE_QUEUE = 3;
    private static final int NEARBY_MESSAGE_FILE_CANCEL = 4;
    private static final String PUBLIC_RECEIVE_DIR = "K-Share";
    private static final float MIN_AUTO_TEXT_SCALE = 0.82f;
    private static final float MAX_AUTO_TEXT_SCALE = 1.0f;
    private static final float MIN_AUTO_UI_SCALE = 0.78f;
    private static final float MAX_AUTO_UI_SCALE = 0.98f;

    private ShareRepository repository;
    private ShareRepository liveRepository;
    private LinearLayout mobileSharePanel;
    private LinearLayout webSharePanel;
    private LinearLayout localShareFlowPanel;
    private LinearLayout liveShareFlowPanel;
    private LinearLayout nearbyDeviceList;
    private LinearLayout fileList;
    private TextView addressView;
    private TextView remoteAddressView;
    private TextView nearbyStatusView;
    private TextView nearbyTransferView;
    private TextView nearbyQrStatusView;
    private TextView statusView;
    private TextView remoteStatusView;
    private TextView webVisitorsStatusView;
    private ImageView localWebQrImageView;
    private ImageView remoteWebQrImageView;
    private LinearLayout localWebQrCard;
    private LinearLayout remoteWebQrCard;
    private ImageView nearbyQrImageView;
    private LinearLayout nearbyQrCard;
    private LinearLayout nearbyRoleButtons;
    private Button chooseNearbyFilesButton;
    private Button scanNearbySenderQrButton;
    private Button nearbyCancelSharingButton;
    private LinearLayout nearbySendingCard;
    private LinearLayout nearbyReceivedCard;
    private LinearLayout nearbySendList;
    private LinearLayout nearbyReceivedList;
    private Button sendNearbyButton;
    private Button mobileTabButton;
    private Button webTabButton;
    private Button localWebFlowButton;
    private Button liveWebFlowButton;
    private Button shareableFilterButton;
    private Button sharedFilterButton;
    private TextView shareableFilterBadgeView;
    private TextView sharedFilterBadgeView;
    private TextView fileFilteredCountView;
    private TextView librarySummaryView;
    private Button videosCategoryButton;
    private Button filesCategoryButton;
    private Button songsCategoryButton;
    private Button compressedCategoryButton;
    private Button serverButton;
    private Button remoteShareButton;
    private Button webVisitorsButton;
    private Button openLibraryButton;
    private ImageButton menuButton;
    private ImageButton refreshFilesButton;
    private EditText fileSearchInput;
    private TextView fileScanStatusView;
    private ProgressBar fileScanProgressBar;
    private Dialog fileLibraryDialog;
    private PopupWindow appMenuPopover;
    private PopupWindow fileCategoryPopover;
    private int activePort;
    private boolean pendingRemoteStart;
    private boolean remoteRelayRunning;
    private boolean remoteRelayConnected;
    private int webVisitorCount;
    private int activeWebVisitorCount;
    private long webVisitorBytesServed;
    private String lastRenderedLocalUrl = "";
    private String remoteShareUrl = "";
    private String remoteShortShareUrl = "";
    private String remoteFullShareUrl = "";
    private String remoteTinyUrlError = "";
    private WifiP2pManager wifiP2pManager;
    private WifiP2pManager.Channel wifiP2pChannel;
    private BroadcastReceiver wifiP2pReceiver;
    private IntentFilter wifiP2pFilter;
    private WifiP2pInfo latestWifiP2pInfo;
    private NearbyEndpoint connectedNearbyReceiver;
    private WifiManager.MulticastLock multicastLock;
    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback nearbyNetworkCallback;
    private ServerSocket nearbyServerSocket;
    private Socket nearbyIncomingSocket;
    private ServerSocket nearbyControlServerSocket;
    private Socket activeNearbySendSocket;
    private String nearbyRemoteControlHost;
    private int nearbyRemoteControlPort;
    private String nearbyReceiverSessionToken = "";
    private String outgoingNearbySessionToken = "";
    private String expectedNearbySenderSessionToken = "";
    private boolean nearbyControlRunning;
    private ServerSocket outgoingNearbyServerSocket;
    private final ExecutorService nearbyTransferExecutor = Executors.newCachedThreadPool();
    private final ExecutorService nearbyDiscoveryExecutor = Executors.newCachedThreadPool();
    private final ExecutorService fileScanExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(android.os.Looper.getMainLooper());
    private boolean showWebShareTab;
    private boolean showLiveWebShareTab;
    private boolean showSharedFilter;
    private boolean nearbyReceiverRegistered;
    private boolean nearbyNotificationReceiverRegistered;
    private boolean wifiDirectEnabled;
    private boolean nearbyDiscovering;
    private boolean nearbyReceiveRunning;
    private boolean outgoingNearbyServerRunning;
    private boolean outgoingReceiverConnected;
    private boolean incomingNearbyConnected;
    private boolean nearbyReceiverMode;
    private boolean nearbySendingTransferStarted;
    private boolean nearbyReceivingTransferStarted;
    private boolean nearbyAwaitingReconnect;
    private volatile boolean nearbyNetworkLost;
    private volatile int nearbyHeartbeatGeneration;
    private boolean lanDiscoveryRunning;
    private boolean nearbySending;
    private int pendingNearbyScanMode = NEARBY_SCAN_RECEIVER_QR;
    private int nearbyNextSendIndex;
    private final Object nearbyPauseLock = new Object();
    private volatile boolean nearbyTransferPaused;
    private long lastNearbyNotificationAt;
    private boolean requestedNotificationPermissionThisSession;
    private boolean requestedMediaPermissionThisSession;
    private boolean pendingRefreshAfterDocumentStorageSettings;
    private volatile boolean fileScanRunning;
    private String fileScanMessage = "";
    private boolean fileScanUiRunning;
    private int visibleAvailableFileLimit = FILE_RENDER_PAGE_SIZE;
    private int cachedDiscoveredFileCount;
    private String fileSearchQuery = "";
    private final Map<String, String> fileSearchQueries = new LinkedHashMap<>();
    private final Set<String> expandedFileFolders = new HashSet<>();
    private String activeFileCategory = FILE_CATEGORY_FILES;
    private boolean suppressFileSearchChange;
    private Runnable pendingFileSearchRender;
    private final List<SharedFile> availableFiles = new ArrayList<>();
    private final List<SharedFile> nearbySessionFiles = new ArrayList<>();
    private final List<NearbyTransferItem> nearbySendingItems = new ArrayList<>();
    private final List<SharedFile> nearbyReshareFiles = new ArrayList<>();
    private final List<NearbyTransferItem> nearbyReshareItems = new ArrayList<>();
    private final List<NearbyTransferItem> nearbyReceivedItems = new ArrayList<>();
    private final List<Socket> nearbyIncomingSockets = Collections.synchronizedList(new ArrayList<>());
    private final List<Socket> outgoingNearbySockets = Collections.synchronizedList(new ArrayList<>());
    private final List<WifiP2pDevice> nearbyPeers = new ArrayList<>();
    private final List<LanPeer> lanPeers = new ArrayList<>();
    private final Set<String> shownWebAccessRequests = new HashSet<>();

    private final BroadcastReceiver statusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int port = intent.getIntExtra(ShareService.EXTRA_STARTED_PORT, 0);
            String error = intent.getStringExtra("error");
            if (error != null) {
                pendingRemoteStart = false;
                Toast.makeText(MainActivity.this, "Could not start server: " + error, Toast.LENGTH_LONG).show();
            }
            activePort = Math.max(0, port);
            renderStatus();
        }
    };

    private final BroadcastReceiver remoteStatusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || !RemoteRelayService.BROADCAST_REMOTE_STATUS.equals(intent.getAction())) {
                return;
            }
            remoteRelayRunning = intent.getBooleanExtra(RemoteRelayService.EXTRA_RUNNING, false);
            remoteRelayConnected = intent.getBooleanExtra(RemoteRelayService.EXTRA_CONNECTED, false);
            remoteShortShareUrl = safeString(intent.getStringExtra(RemoteRelayService.EXTRA_SHORT_URL));
            remoteFullShareUrl = safeString(intent.getStringExtra(RemoteRelayService.EXTRA_FULL_URL));
            remoteTinyUrlError = safeString(intent.getStringExtra(RemoteRelayService.EXTRA_TINY_URL_ERROR));
            remoteShareUrl = preferredRemoteShareUrl(
                    intent.getStringExtra(RemoteRelayService.EXTRA_PUBLIC_URL),
                    remoteShortShareUrl,
                    remoteFullShareUrl
            );
            String error = intent.getStringExtra(RemoteRelayService.EXTRA_ERROR);
            if (!TextUtils.isEmpty(error) && !remoteRelayRunning) {
                Toast.makeText(MainActivity.this, error, Toast.LENGTH_LONG).show();
            }
            renderRemoteStatus(intent.getStringExtra(RemoteRelayService.EXTRA_MESSAGE));
        }
    };

    private final BroadcastReceiver nearbyNotificationReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || !NearbyTransferService.BROADCAST_CONTROL.equals(intent.getAction())) {
                return;
            }
            handleNearbyNotificationCommand(intent.getStringExtra(NearbyTransferService.EXTRA_COMMAND));
        }
    };

    private final BroadcastReceiver webAccessReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || !WebAccessManager.BROADCAST_ACCESS_REQUEST.equals(intent.getAction())) {
                return;
            }
            String requestId = intent.getStringExtra(WebAccessManager.EXTRA_REQUEST_ID);
            if (TextUtils.isEmpty(requestId) || shownWebAccessRequests.contains(requestId)) {
                return;
            }
            WebAccessManager.AccessRequest request = WebAccessManager.getRequest(requestId);
            if (request != null) {
                showWebAccessApprovalDialog(request);
            }
        }
    };

    private final BroadcastReceiver webSessionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || !WebAccessManager.BROADCAST_SESSION_STATUS.equals(intent.getAction())) {
                return;
            }
            refreshWebVisitorState();
            renderWebVisitorStatus();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        repository = new ShareRepository(this);
        liveRepository = ShareRepository.live(this);
        loadCachedDiscoveredFiles();
        activePort = ShareService.currentPort();
        remoteRelayRunning = RemoteRelayService.isRunning();
        remoteRelayConnected = RemoteRelayService.isConnected();
        refreshWebVisitorState();
        remoteShortShareUrl = RemoteRelayService.currentShortUrl();
        remoteFullShareUrl = RemoteRelayService.currentFullUrl();
        remoteTinyUrlError = RemoteRelayService.currentTinyUrlError();
        remoteShareUrl = preferredRemoteShareUrl(
                RemoteRelayService.currentPublicUrl(),
                remoteShortShareUrl,
                remoteFullShareUrl
        );
        buildUi();
        handleLaunchIntent(getIntent());
        if (cachedDiscoveredFileCount > 0) {
            updateFileScanUi(cachedDiscoveredFileCount + " cached.", false);
        }
        registerNearbyNetworkCallback();
        startNearbyReceiveServer();
        renderFiles();
        renderStatus();
        renderRemoteStatus(RemoteRelayService.currentMessage());
        renderWebVisitorStatus();
        renderNearbyState();
        registerNearbyNotificationReceiver();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleLaunchIntent(intent);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateFileCategoryTabs();
        renderWebShareFlowTabs();
    }

    @Override
    protected void onResume() {
        super.onResume();
        showDefaultNearbyQrIfIdle();
        refreshWebVisitorState();
        renderWebVisitorStatus();
        showPendingWebAccessRequests();
        if (pendingRefreshAfterDocumentStorageSettings) {
            pendingRefreshAfterDocumentStorageSettings = false;
            if (hasDocumentStorageAccess()) {
                Toast.makeText(this, "Document access allowed. Scanning files.", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Document access not allowed. Scanning media only.", Toast.LENGTH_LONG).show();
            }
            startProgressiveFileScan(true);
        }
    }

    @Override
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter(ShareService.BROADCAST_STATUS);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(statusReceiver, filter, RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(statusReceiver, filter);
        }
        IntentFilter remoteFilter = new IntentFilter(RemoteRelayService.BROADCAST_REMOTE_STATUS);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(remoteStatusReceiver, remoteFilter, RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(remoteStatusReceiver, remoteFilter);
        }
        IntentFilter accessFilter = new IntentFilter(WebAccessManager.BROADCAST_ACCESS_REQUEST);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(webAccessReceiver, accessFilter, RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(webAccessReceiver, accessFilter);
        }
        IntentFilter webSessionFilter = new IntentFilter(WebAccessManager.BROADCAST_SESSION_STATUS);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(webSessionReceiver, webSessionFilter, RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(webSessionReceiver, webSessionFilter);
        }
        refreshWebVisitorState();
        renderWebVisitorStatus();
        showPendingWebAccessRequests();
    }

    @Override
    protected void onStop() {
        unregisterReceiver(statusReceiver);
        unregisterReceiver(remoteStatusReceiver);
        unregisterReceiver(webAccessReceiver);
        unregisterReceiver(webSessionReceiver);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        unregisterNearbyNotificationReceiver();
        unregisterNearbyNetworkCallback();
        stopNearbyHeartbeat();
        cancelOutgoingNearbySession(false);
        stopNearbyReceiveServer();
        stopNearbyTransferKeepAlive();
        stopLanFallbackDiscovery();
        nearbyTransferExecutor.shutdownNow();
        nearbyDiscoveryExecutor.shutdownNow();
        fileScanExecutor.shutdownNow();
        super.onDestroy();
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void registerNearbyNotificationReceiver() {
        if (nearbyNotificationReceiverRegistered) {
            return;
        }
        IntentFilter filter = new IntentFilter(NearbyTransferService.BROADCAST_CONTROL);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(nearbyNotificationReceiver, filter, RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(nearbyNotificationReceiver, filter);
        }
        nearbyNotificationReceiverRegistered = true;
    }

    private void unregisterNearbyNotificationReceiver() {
        if (!nearbyNotificationReceiverRegistered) {
            return;
        }
        try {
            unregisterReceiver(nearbyNotificationReceiver);
        } catch (Exception ignored) {
        }
        nearbyNotificationReceiverRegistered = false;
    }

    private void handleLaunchIntent(Intent intent) {
        activePort = Math.max(activePort, ShareService.currentPort());
        if (intent == null) {
            return;
        }
        String accessRequestId = intent.getStringExtra(EXTRA_WEB_ACCESS_REQUEST_ID);
        boolean confirmStopLocal = ACTION_CONFIRM_STOP_LOCAL_SHARE.equals(intent.getAction());
        boolean confirmStopLive = ACTION_CONFIRM_STOP_LIVE_SHARE.equals(intent.getAction());
        boolean openWebShare = ACTION_OPEN_WEB_SHARE.equals(intent.getAction())
                || TAB_WEB.equals(intent.getStringExtra(EXTRA_OPEN_TAB))
                || confirmStopLocal
                || confirmStopLive
                || !TextUtils.isEmpty(accessRequestId);
        if (!openWebShare) {
            return;
        }
        showWebShareTab = true;
        String requestedFlow = intent.getStringExtra(EXTRA_WEB_FLOW);
        if (confirmStopLive || WEB_FLOW_LIVE.equals(requestedFlow)) {
            showLiveWebShareTab = true;
        } else if (confirmStopLocal || WEB_FLOW_LOCAL.equals(requestedFlow)) {
            showLiveWebShareTab = false;
        } else if (!TextUtils.isEmpty(accessRequestId)) {
            WebAccessManager.AccessRequest request = WebAccessManager.getRequest(accessRequestId);
            showLiveWebShareTab = request != null && WebAccessManager.SCOPE_LIVE.equals(request.scope);
        }
        loadCachedDiscoveredFiles();
        renderModeTabs();
        renderStatus();
        renderRemoteStatus(RemoteRelayService.currentMessage());
        renderWebVisitorStatus();
        if (webSharePanel != null) {
            webSharePanel.requestFocus();
        }
        if (confirmStopLocal) {
            mainHandler.post(this::confirmStopLocalSharing);
        } else if (confirmStopLive) {
            mainHandler.post(this::confirmStopRemoteSharing);
        }
        if (!TextUtils.isEmpty(accessRequestId)) {
            enableUrgentWebAccessWindow();
            mainHandler.post(() -> showWebAccessRequestFromIntent(accessRequestId));
        }
    }

    private void enableUrgentWebAccessWindow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
    }

    private void disableUrgentWebAccessWindowIfIdle() {
        if (!WebAccessManager.pendingRequests().isEmpty()) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(false);
            setTurnScreenOn(false);
        } else {
            getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
    }

    private void showWebAccessRequestFromIntent(String requestId) {
        WebAccessManager.AccessRequest request = WebAccessManager.getRequest(requestId);
        if (request == null || !"pending".equals(request.status)) {
            Toast.makeText(this, "Access request is no longer pending.", Toast.LENGTH_SHORT).show();
            disableUrgentWebAccessWindowIfIdle();
            return;
        }
        showWebAccessApprovalDialog(request);
    }

    private void registerNearbyNetworkCallback() {
        connectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (connectivityManager == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return;
        }
        nearbyNetworkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                mainHandler.postDelayed(() -> {
                    refreshWebShareNetworkState();
                    handleNearbyNetworkAvailable(network);
                }, 500);
            }

            @Override
            public void onLost(Network network) {
                mainHandler.postDelayed(() -> {
                    refreshWebShareNetworkState();
                    handleNearbyNetworkLost();
                }, 1200);
            }
        };
        try {
            NetworkRequest request = new NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .build();
            connectivityManager.registerNetworkCallback(request, nearbyNetworkCallback);
        } catch (Exception e) {
            Log.w(TAG, "Could not monitor Wi-Fi state.", e);
            nearbyNetworkCallback = null;
        }
    }

    private void unregisterNearbyNetworkCallback() {
        if (connectivityManager == null || nearbyNetworkCallback == null) {
            return;
        }
        try {
            connectivityManager.unregisterNetworkCallback(nearbyNetworkCallback);
        } catch (Exception ignored) {
        }
        nearbyNetworkCallback = null;
    }

    private void refreshWebShareNetworkState() {
        int currentPort = ShareService.currentPort();
        if (currentPort > 0) {
            activePort = currentPort;
        }
        if (activePort <= 0) {
            return;
        }

        String previousUrl = lastRenderedLocalUrl;
        renderStatus();
        String currentUrl = currentUrl();
        if (!TextUtils.equals(previousUrl, currentUrl) && !TextUtils.isEmpty(currentUrl)) {
            Log.i(TAG, "Web share URL refreshed after network change: " + currentUrl);
        }
    }

    private void handleNearbyNetworkLost() {
        if (hasActiveWifiNetwork()) {
            Log.i(TAG, "Ignoring transient Wi-Fi loss because an active Wi-Fi network is still available.");
            return;
        }
        nearbyNetworkLost = true;
        resetNearbySessionTokens();
        boolean receiverFlow = nearbyReceiverMode || incomingNearbyConnected;
        closeNearbyConnectionState(
                receiverFlow,
                "Network lost."
        );
        stopNearbyReceiveServer();
        Toast.makeText(this, "Network lost.", Toast.LENGTH_LONG).show();
    }

    private boolean hasActiveWifiNetwork() {
        if (connectivityManager == null) {
            return false;
        }
        Network activeNetwork = connectivityManager.getActiveNetwork();
        if (activeNetwork == null) {
            return false;
        }
        NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(activeNetwork);
        return capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
    }

    private void handleNearbyNetworkAvailable(Network network) {
        if (connectivityManager == null) {
            return;
        }
        NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
        if (capabilities == null || !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            return;
        }
        boolean wasLost = nearbyNetworkLost;
        nearbyNetworkLost = false;
        if (!wasLost) {
            return;
        }
        startNearbyReceiveServer();
        if (nearbyReceiverMode) {
            if (nearbyRoleButtons != null) {
                nearbyRoleButtons.setVisibility(View.GONE);
            }
            if (nearbyQrCard != null) {
                nearbyQrCard.setVisibility(View.VISIBLE);
            }
            updateNearbyQrStatus("QR ready.");
            renderNearbyTransferState("Network restored.");
            mainHandler.postDelayed(this::showDefaultNearbyQrIfIdle, 500);
        } else {
            showNearbyRoleSelection();
            renderNearbyTransferState("Network restored.");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_QR_SCAN) {
            int scanMode = pendingNearbyScanMode;
            pendingNearbyScanMode = NEARBY_SCAN_RECEIVER_QR;
            handleNearbyQrResult(
                    resultCode == RESULT_OK && data != null ? data.getStringExtra(QrScannerActivity.EXTRA_QR_TEXT) : null,
                    scanMode
            );
            return;
        }
        if (resultCode != RESULT_OK || data == null) {
            return;
        }
        if (requestCode == REQUEST_NEARBY_FILES) {
            handleNearbyFileSelection(data);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_NEARBY_PERMISSIONS) {
            if (allGranted(grantResults)) {
                startNearbyDiscovery();
            } else {
                Toast.makeText(this, "Nearby permission needed.", Toast.LENGTH_LONG).show();
            }
            return;
        }
        if (requestCode == REQUEST_CAMERA) {
            if (allGranted(grantResults)) {
                mainHandler.postDelayed(() -> launchNearbyQrScanner(pendingNearbyScanMode), 250);
            } else {
                Toast.makeText(this, "Camera permission needed.", Toast.LENGTH_LONG).show();
            }
            return;
        }
        if (requestCode != REQUEST_MEDIA_PERMISSIONS) {
            return;
        }
        for (int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Media permission needed.", Toast.LENGTH_LONG).show();
                return;
            }
        }
        Toast.makeText(this, "Scanning files.", Toast.LENGTH_SHORT).show();
        maybeRequestDocumentStorageThenScan(true);
    }

    private void buildUi() {
        int paper = Color.rgb(6, 14, 18);
        int ink = Color.rgb(241, 248, 246);
        int muted = Color.rgb(148, 163, 184);
        int accent = Color.rgb(61, 198, 190);
        int hero = Color.rgb(12, 25, 31);
        int panel = Color.rgb(10, 21, 27);

        getWindow().setStatusBarColor(paper);
        getWindow().setNavigationBarColor(paper);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(true);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getWindow().getDecorView().setSystemUiVisibility(0);
        }

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackground(appBackground());

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        applySafeAreaPadding(root);
        scroll.addView(root);

        LinearLayout heroCard = card(hero);
        heroCard.setBackground(heroBackground());
        heroCard.setGravity(Gravity.CENTER_HORIZONTAL);
        heroCard.setPadding(dp(12), dp(8), dp(12), dp(10));
        root.addView(heroCard, fullWidthWithMargins(0, 0, 0, dp(12)));

        LinearLayout brandRow = new LinearLayout(this);
        brandRow.setOrientation(LinearLayout.HORIZONTAL);
        brandRow.setGravity(Gravity.CENTER_VERTICAL);
        heroCard.addView(brandRow, fullWidthWithMargins(0, 0, 0, dp(6), dp(34)));

        menuButton = smallIconButton(
                R.drawable.ic_menu,
                ink,
                Color.TRANSPARENT,
                Color.TRANSPARENT,
                "Menu",
                "Open menu"
        );
        brandRow.addView(menuButton, new LinearLayout.LayoutParams(dp(34), dp(34)));

        View leftSpacer = new View(this);
        brandRow.addView(leftSpacer, new LinearLayout.LayoutParams(0, 1, 1));

        ImageView wifiMark = new ImageView(this);
        wifiMark.setImageDrawable(tintedIcon(R.drawable.ic_wifi, accent, 30));
        brandRow.addView(wifiMark, new LinearLayout.LayoutParams(dp(38), dp(34)));

        View rightSpacer = new View(this);
        brandRow.addView(rightSpacer, new LinearLayout.LayoutParams(0, 1, 1));

        View rightBalance = new View(this);
        brandRow.addView(rightBalance, new LinearLayout.LayoutParams(dp(34), dp(34)));

        TextView title = text("K-Share", 19, ink, true);
        title.setGravity(Gravity.CENTER);
        title.setIncludeFontPadding(false);
        heroCard.addView(title, fullWidthWithMargins(0, dp(2), 0, 0));

        TextView subtitle = text("Nearby • Local Web • Live Share", 10, muted, true);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, dp(4), 0, 0);
        heroCard.addView(subtitle, fullWidthWithMargins(0, 0, 0, 0));

        LinearLayout modeTabs = new LinearLayout(this);
        modeTabs.setOrientation(LinearLayout.HORIZONTAL);
        modeTabs.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(modeTabs, fullWidthWithMargins(0, 0, 0, dp(10)));

        mobileTabButton = button("Nearby");
        mobileTabButton.setTooltipText("Mobile to mobile sharing with Wi-Fi or Bluetooth");
        LinearLayout.LayoutParams mobileTabParams = new LinearLayout.LayoutParams(0, dp(42), 1);
        mobileTabParams.setMargins(0, 0, dp(6), 0);
        modeTabs.addView(mobileTabButton, mobileTabParams);

        webTabButton = button("Web");
        webTabButton.setTooltipText("Share selected files through the phone web server");
        LinearLayout.LayoutParams webTabParams = new LinearLayout.LayoutParams(0, dp(42), 1);
        webTabParams.setMargins(dp(6), 0, 0, 0);
        modeTabs.addView(webTabButton, webTabParams);

        mobileSharePanel = card(panel);
        mobileSharePanel.setPadding(dp(14), dp(14), dp(14), dp(14));
        root.addView(mobileSharePanel, fullWidthWithMargins(0, 0, 0, dp(14)));

        TextView nearbyTitle = text("Choose share flow", 16, ink, true);
        mobileSharePanel.addView(nearbyTitle);

        nearbyStatusView = text("", 13, Color.rgb(203, 213, 225), true);
        nearbyStatusView.setPadding(dp(14), dp(12), dp(14), dp(12));
        nearbyStatusView.setBackground(rounded(Color.rgb(12, 25, 31), dp(16), Color.rgb(38, 60, 69), dp(1)));
        nearbyStatusView.setVisibility(View.GONE);

        nearbyTransferView = text("", 13, muted, true);
        nearbyTransferView.setPadding(dp(14), dp(12), dp(14), dp(12));
        nearbyTransferView.setBackground(rounded(Color.rgb(9, 39, 44), dp(16), Color.rgb(47, 111, 115), dp(1)));
        nearbyTransferView.setVisibility(View.GONE);

        mobileSharePanel.addView(nearbyStatusView, fullWidthWithMargins(0, 0, 0, dp(10)));
        mobileSharePanel.addView(nearbyTransferView, fullWidthWithMargins(0, 0, 0, dp(14)));

        nearbyRoleButtons = new LinearLayout(this);
        nearbyRoleButtons.setOrientation(LinearLayout.VERTICAL);
        nearbyRoleButtons.setGravity(Gravity.CENTER_VERTICAL);
        mobileSharePanel.addView(nearbyRoleButtons, fullWidthWithMargins(0, 0, 0, dp(14)));

        View senderButton = roleCard(
                "Sender",
                "Send files to another device",
                R.drawable.ic_share,
                Color.rgb(37, 99, 235),
                Color.rgb(239, 246, 255),
                Color.rgb(191, 219, 254)
        );
        nearbyRoleButtons.addView(senderButton, fullWidthWithMargins(0, dp(10), 0, dp(8), dp(68)));

        View receiverButton = roleCard(
                "Receiver",
                "Receive files from another device",
                R.drawable.ic_qr_code,
                Color.rgb(22, 163, 74),
                Color.rgb(240, 253, 244),
                Color.rgb(187, 247, 208)
        );
        nearbyRoleButtons.addView(receiverButton, fullWidthWithMargins(0, 0, 0, dp(8), dp(68)));

        View desktopQrButton = roleCard(
                "Desktop QR",
                "Scan desktop QR to send or receive",
                R.drawable.ic_qr_code,
                Color.rgb(245, 158, 11),
                Color.rgb(255, 251, 235),
                Color.rgb(253, 230, 138)
        );
        nearbyRoleButtons.addView(desktopQrButton, fullWidthWithMargins(0, 0, 0, 0, dp(68)));

        mobileSharePanel.addView(currentNetworkCard(), fullWidthWithMargins(0, 0, 0, dp(14)));

        LinearLayout qrSendCard = card(panel);
        nearbyQrCard = qrSendCard;
        qrSendCard.setBackground(gradient(
                new int[]{Color.rgb(9, 39, 44), Color.rgb(12, 25, 31)},
                dp(22),
                Color.rgb(47, 111, 115),
                dp(1),
                GradientDrawable.Orientation.TOP_BOTTOM
        ));
        qrSendCard.setPadding(dp(12), dp(12), dp(12), dp(12));
        qrSendCard.setVisibility(View.GONE);
        mobileSharePanel.addView(qrSendCard, fullWidthWithMargins(0, 0, 0, dp(14)));

        TextView qrSendTitle = text("Receive Files", 17, ink, true);
        qrSendTitle.setGravity(Gravity.CENTER);
        qrSendCard.addView(qrSendTitle);

        chooseNearbyFilesButton = button("Select files");
        styleButton(chooseNearbyFilesButton, accent, Color.rgb(4, 14, 16), 0, 14);
        setButtonIcon(chooseNearbyFilesButton, R.drawable.ic_folder, Color.rgb(4, 14, 16));
        chooseNearbyFilesButton.setTooltipText("Select files for this nearby transfer");
        chooseNearbyFilesButton.setVisibility(View.GONE);
        qrSendCard.addView(chooseNearbyFilesButton, fullWidthWithMargins(0, 0, 0, dp(8), dp(44)));

        nearbyQrStatusView = text("QR ready.", 13, muted, true);
        nearbyQrStatusView.setGravity(Gravity.CENTER);
        nearbyQrStatusView.setPadding(0, 0, 0, dp(10));
        qrSendCard.addView(nearbyQrStatusView);

        nearbyQrImageView = new ImageView(this);
        nearbyQrImageView.setAdjustViewBounds(true);
        nearbyQrImageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        nearbyQrImageView.setBackground(rounded(Color.WHITE, dp(18), Color.rgb(226, 232, 240), dp(1)));
        nearbyQrImageView.setPadding(dp(14), dp(14), dp(14), dp(14));
        nearbyQrImageView.setVisibility(View.GONE);
        qrSendCard.addView(nearbyQrImageView, fullWidthWithMargins(0, 0, 0, 0, responsiveQrCardSize()));

        scanNearbySenderQrButton = button("Scan sender QR");
        styleButton(scanNearbySenderQrButton, accent, Color.rgb(4, 14, 16), 0, 14);
        setButtonIcon(scanNearbySenderQrButton, R.drawable.ic_qr_code, Color.rgb(4, 14, 16));
        scanNearbySenderQrButton.setTooltipText("Scan desktop sender QR to receive files");
        scanNearbySenderQrButton.setVisibility(View.GONE);
        qrSendCard.addView(scanNearbySenderQrButton, fullWidthWithMargins(0, dp(8), 0, 0, dp(44)));

        nearbyCancelSharingButton = button("Cancel");
        styleButton(nearbyCancelSharingButton, Color.rgb(69, 26, 26), Color.rgb(252, 165, 165), Color.rgb(127, 29, 29), 14);
        setButtonIcon(nearbyCancelSharingButton, R.drawable.ic_stop, Color.rgb(252, 165, 165));
        nearbyCancelSharingButton.setTooltipText("Cancel the current QR sharing session");
        nearbyCancelSharingButton.setVisibility(View.GONE);
        qrSendCard.addView(nearbyCancelSharingButton, fullWidthWithMargins(0, dp(8), 0, 0, dp(44)));

        LinearLayout receiveCard = card(panel);
        receiveCard.setPadding(dp(12), dp(12), dp(12), dp(12));
        receiveCard.setVisibility(View.GONE);

        TextView receiveTitle = text("Receive", 17, ink, true);
        receiveCard.addView(receiveTitle);

        Button scanQrButton = button("Scan QR");
        styleButton(scanQrButton, accent, Color.rgb(4, 14, 16), 0, 14);
        setButtonIcon(scanQrButton, R.drawable.ic_refresh, Color.rgb(4, 14, 16));
        scanQrButton.setTooltipText("Scan sender QR code and receive files");
        receiveCard.addView(scanQrButton, fullWidthWithMargins(0, 0, 0, 0, dp(44)));

        LinearLayout sendListCard = card(panel);
        nearbySendingCard = sendListCard;
        sendListCard.setPadding(dp(12), dp(12), dp(12), dp(12));
        sendListCard.setVisibility(View.GONE);
        mobileSharePanel.addView(sendListCard, fullWidthWithMargins(0, 0, 0, dp(14)));

        TextView sendListTitle = text("Sending", 17, ink, true);
        sendListCard.addView(sendListTitle);

        nearbySendList = new LinearLayout(this);
        nearbySendList.setOrientation(LinearLayout.VERTICAL);
        sendListCard.addView(nearbySendList);

        LinearLayout receivedListCard = card(panel);
        nearbyReceivedCard = receivedListCard;
        receivedListCard.setPadding(dp(12), dp(12), dp(12), dp(12));
        receivedListCard.setVisibility(View.GONE);
        mobileSharePanel.addView(receivedListCard, fullWidthWithMargins(0, 0, 0, dp(14)));

        TextView receivedListTitle = text("Received", 17, ink, true);
        receivedListCard.addView(receivedListTitle);

        nearbyReceivedList = new LinearLayout(this);
        nearbyReceivedList.setOrientation(LinearLayout.VERTICAL);
        receivedListCard.addView(nearbyReceivedList);

        webSharePanel = new LinearLayout(this);
        webSharePanel.setOrientation(LinearLayout.VERTICAL);
        root.addView(webSharePanel);

        LinearLayout libraryCard = card(panel);
        libraryCard.setPadding(dp(12), dp(12), dp(12), dp(12));
        webSharePanel.addView(libraryCard, fullWidthWithMargins(0, 0, 0, dp(10)));

        LinearLayout libraryHeader = new LinearLayout(this);
        libraryHeader.setOrientation(LinearLayout.HORIZONTAL);
        libraryHeader.setGravity(Gravity.CENTER_VERTICAL);
        libraryCard.addView(libraryHeader, fullWidthWithMargins(0, 0, 0, dp(10)));

        LinearLayout libraryCopy = new LinearLayout(this);
        libraryCopy.setOrientation(LinearLayout.VERTICAL);
        libraryHeader.addView(libraryCopy, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView filesTitle = text("File Library", 16, ink, true);
        filesTitle.setIncludeFontPadding(false);
        libraryCopy.addView(filesTitle);

        librarySummaryView = text("", 11, muted, false);
        librarySummaryView.setPadding(0, dp(4), 0, 0);
        libraryCopy.addView(librarySummaryView);

        openLibraryButton = button("Open");
        styleButton(openLibraryButton, Color.rgb(12, 25, 31), accent, Color.rgb(38, 60, 69), 16);
        setButtonIcon(openLibraryButton, R.drawable.ic_folder, accent);
        openLibraryButton.setTooltipText("Open Videos, Files, Songs, and Compressed files");
        libraryHeader.addView(openLibraryButton, new LinearLayout.LayoutParams(dp(104), dp(40)));

        LinearLayout webFlowTabs = new LinearLayout(this);
        webFlowTabs.setOrientation(LinearLayout.HORIZONTAL);
        webFlowTabs.setGravity(Gravity.CENTER);
        webFlowTabs.setPadding(0, 0, 0, dp(10));
        webSharePanel.addView(webFlowTabs, fullWidthWithMargins(0, 0, 0, dp(4), dp(50)));

        localWebFlowButton = button("Local");
        localWebFlowButton.setTooltipText("Share a separate file list on the same Wi-Fi");
        webFlowTabs.addView(localWebFlowButton, toolbarButtonParams(0, dp(6)));

        liveWebFlowButton = button("Live");
        liveWebFlowButton.setTooltipText("Share a separate file list through the internet relay");
        webFlowTabs.addView(liveWebFlowButton, toolbarButtonParams(dp(6), 0));

        LinearLayout webHeroCard = card(hero);
        localShareFlowPanel = webHeroCard;
        webHeroCard.setBackground(heroBackground());
        webHeroCard.setPadding(dp(13), dp(13), dp(13), dp(13));
        webSharePanel.addView(webHeroCard, fullWidthWithMargins(0, 0, 0, dp(14)));

        TextView webTitle = text("Local Share", 19, ink, true);
        webHeroCard.addView(webTitle);

        statusView = text("", 12, muted, true);
        statusView.setPadding(0, dp(9), 0, dp(4));
        webHeroCard.addView(statusView);

        addressView = text("", 16, ink, true);
        addressView.setPadding(dp(12), dp(9), dp(12), dp(9));
        addressView.setSingleLine(false);
        addressView.setBackground(gradient(
                new int[]{Color.rgb(12, 25, 31), Color.rgb(15, 32, 39)},
                dp(16),
                Color.rgb(38, 60, 69),
                dp(1),
                GradientDrawable.Orientation.LEFT_RIGHT
        ));
        webHeroCard.addView(addressView, fullWidthWithMargins(0, dp(6), 0, dp(10)));

        localWebQrCard = webQrCard("Local QR", "Scan on the same Wi-Fi", accent, false);
        localWebQrCard.setVisibility(View.GONE);
        webHeroCard.addView(localWebQrCard, fullWidthWithMargins(0, 0, 0, dp(14)));

        serverButton = button("Start server");
        styleButton(serverButton, accent, Color.rgb(4, 14, 16), 0, 16);
        setButtonIcon(serverButton, R.drawable.ic_play_arrow, Color.rgb(4, 14, 16));
        serverButton.setTooltipText("Start or stop the local file sharing server");
        webHeroCard.addView(serverButton, fullWidthWithMargins(0, 0, 0, dp(10), dp(42)));

        webVisitorsStatusView = text("", 13, muted, true);
        webVisitorsStatusView.setPadding(0, 0, 0, dp(6));
        webVisitorsStatusView.setVisibility(View.GONE);
        webHeroCard.addView(webVisitorsStatusView);

        webVisitorsButton = button("Web visitors");
        styleButton(webVisitorsButton, Color.rgb(12, 25, 31), accent, Color.rgb(38, 60, 69), 16);
        setButtonIcon(webVisitorsButton, R.drawable.ic_lock, accent);
        webVisitorsButton.setTooltipText("Show connected web visitors and data used");
        webVisitorsButton.setVisibility(View.GONE);
        webHeroCard.addView(webVisitorsButton, fullWidthWithMargins(0, 0, 0, dp(10), dp(40)));

        liveShareFlowPanel = card(hero);
        liveShareFlowPanel.setBackground(gradient(
                new int[]{Color.rgb(24, 18, 12), Color.rgb(12, 25, 31), Color.rgb(6, 14, 18)},
                dp(22),
                Color.rgb(79, 54, 34),
                dp(1),
                GradientDrawable.Orientation.TL_BR
        ));
        liveShareFlowPanel.setPadding(dp(13), dp(13), dp(13), dp(13));
        webSharePanel.addView(liveShareFlowPanel, fullWidthWithMargins(0, 0, 0, dp(14)));

        TextView liveTitle = text("Live Share", 19, ink, true);
        liveShareFlowPanel.addView(liveTitle);

        remoteStatusView = text("", 12, muted, true);
        remoteStatusView.setPadding(0, dp(1), 0, dp(4));
        liveShareFlowPanel.addView(remoteStatusView);

        remoteAddressView = text("", 14, ink, true);
        remoteAddressView.setPadding(dp(12), dp(9), dp(12), dp(9));
        remoteAddressView.setSingleLine(false);
        remoteAddressView.setBackground(gradient(
                new int[]{Color.rgb(29, 22, 16), Color.rgb(18, 27, 30)},
                dp(16),
                Color.rgb(79, 54, 34),
                dp(1),
                GradientDrawable.Orientation.LEFT_RIGHT
        ));
        liveShareFlowPanel.addView(remoteAddressView, fullWidthWithMargins(0, dp(6), 0, dp(8)));

        remoteWebQrCard = webQrCard("Global QR", "Scan from anywhere", Color.rgb(255, 170, 96), true);
        remoteWebQrCard.setVisibility(View.GONE);
        liveShareFlowPanel.addView(remoteWebQrCard, fullWidthWithMargins(0, 0, 0, dp(10)));

        remoteShareButton = button("Start global link");
        styleButton(remoteShareButton, Color.rgb(255, 170, 96), Color.rgb(29, 16, 5), 0, 16);
        setButtonIcon(remoteShareButton, R.drawable.ic_share, Color.rgb(29, 16, 5));
        remoteShareButton.setTooltipText("Create or stop an internet relay link for shared files");
        liveShareFlowPanel.addView(remoteShareButton, fullWidthWithMargins(0, 0, 0, 0, dp(42)));

        menuButton.setOnClickListener(v -> showAppMenu());
        openLibraryButton.setOnClickListener(v -> showFileLibraryDialog());
        localWebFlowButton.setOnClickListener(v -> selectWebShareFlow(false));
        liveWebFlowButton.setOnClickListener(v -> selectWebShareFlow(true));
        webVisitorsButton.setOnClickListener(v -> showWebVisitorsDialog());
        senderButton.setOnClickListener(v -> {
            nearbyReceiverMode = false;
            nearbyAwaitingReconnect = false;
            resetNearbySenderQueue();
            updateNearbyTransferCardsVisibility();
            if (nearbyQrCard != null) {
                nearbyQrCard.setVisibility(View.GONE);
            }
            hideNearbyQrCode();
            updateNearbyQrStatus("Scan receiver QR.");
            scanNearbyQrCode(NEARBY_SCAN_RECEIVER_QR);
        });
        receiverButton.setOnClickListener(v -> {
            nearbyReceiverMode = true;
            nearbyAwaitingReconnect = false;
            nearbyReceiverSessionToken = newNearbySessionToken();
            startNearbyTransferKeepAlive("Ready to receive nearby files.");
            updateNearbyTransferCardsVisibility();
            if (nearbyQrCard != null) {
                nearbyQrCard.setVisibility(View.VISIBLE);
            }
            showDefaultNearbyQrIfIdle();
        });
        desktopQrButton.setOnClickListener(v -> {
            nearbyReceiverMode = false;
            nearbyAwaitingReconnect = false;
            resetNearbySenderQueue();
            updateNearbyTransferCardsVisibility();
            if (nearbyQrCard != null) {
                nearbyQrCard.setVisibility(View.GONE);
            }
            hideNearbyQrCode();
            updateNearbyQrStatus("Scan desktop QR.");
            renderNearbyTransferState("Scan desktop QR.");
            scanNearbyQrCode(NEARBY_SCAN_DESKTOP_QR);
        });
        chooseNearbyFilesButton.setOnClickListener(v -> {
            if (nearbySending && connectedNearbyReceiver == null) {
                renderNearbyTransferState("Running.");
                return;
            }
            openNearbyFilePicker();
        });
        nearbyCancelSharingButton.setOnClickListener(v -> {
            if (nearbyReceiverMode || incomingNearbyConnected) {
                cancelIncomingNearbySession();
            } else {
                cancelOutgoingNearbySession(true);
            }
        });
        scanNearbySenderQrButton.setOnClickListener(v -> scanNearbyQrCode(NEARBY_SCAN_DESKTOP_QR));
        scanQrButton.setOnClickListener(v -> scanNearbyQrCode(NEARBY_SCAN_RECEIVER_QR));
        serverButton.setOnClickListener(v -> {
            if (activePort > 0) {
                confirmStopLocalSharing();
            } else {
                startSharing();
            }
        });
        remoteShareButton.setOnClickListener(v -> {
            if (remoteRelayRunning) {
                confirmStopRemoteSharing();
            } else {
                confirmStartRemoteSharing();
            }
        });
        addressView.setOnClickListener(v -> {
            String url = currentUrl();
            if (!TextUtils.isEmpty(url)) {
                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setType("text/plain");
                intent.putExtra(Intent.EXTRA_TEXT, url);
                startActivity(Intent.createChooser(intent, "Share address"));
            }
        });
        remoteAddressView.setOnClickListener(v -> {
            if (remoteRelayConnected && !TextUtils.isEmpty(remoteShareUrl)) {
                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setType("text/plain");
                intent.putExtra(Intent.EXTRA_TEXT, remoteShareUrl);
                startActivity(Intent.createChooser(intent, "Share global link"));
            }
        });
        mobileTabButton.setOnClickListener(v -> {
            showWebShareTab = false;
            renderModeTabs();
        });
        webTabButton.setOnClickListener(v -> {
            showWebShareTab = true;
            renderModeTabs();
        });

        setContentView(scroll);
        renderModeTabs();
        renderNearbyTransferState("Ready.");
        renderNearbyTransferLists();
        showDefaultNearbyQrIfIdle();
    }

    private ShareRepository activeWebRepository() {
        if (showLiveWebShareTab && liveRepository != null) {
            return liveRepository;
        }
        return repository;
    }

    private String activeWebAccessScope() {
        return showLiveWebShareTab ? WebAccessManager.SCOPE_LIVE : WebAccessManager.SCOPE_LOCAL;
    }

    private String activeWebShareName() {
        return showLiveWebShareTab ? "Live Share" : "Local Share";
    }

    private String activeSharedFileLabel() {
        return showLiveWebShareTab ? "live shared" : "local shared";
    }

    private void selectWebShareFlow(boolean live) {
        if (showLiveWebShareTab == live) {
            renderWebShareFlowTabs();
            return;
        }
        showLiveWebShareTab = live;
        showSharedFilter = false;
        visibleAvailableFileLimit = FILE_RENDER_PAGE_SIZE;
        loadCachedDiscoveredFiles();
        renderWebShareFlowTabs();
        renderFiles();
    }

    private void refreshDiscoveredFiles(boolean showToast) {
        if (fileScanRunning) {
            Toast.makeText(this, "Refreshing...", Toast.LENGTH_SHORT).show();
            return;
        }
        List<String> missingPermissions = missingMediaPermissions();
        if (!missingPermissions.isEmpty()) {
            if (!requestedMediaPermissionThisSession) {
                requestedMediaPermissionThisSession = true;
                requestPermissions(missingPermissions.toArray(new String[0]), REQUEST_MEDIA_PERMISSIONS);
            } else if (showToast) {
                Toast.makeText(this, "Media permission needed.", Toast.LENGTH_LONG).show();
            }
            return;
        }
        maybeRequestDocumentStorageThenScan(showToast);
    }

    private void loadCachedDiscoveredFiles() {
        Set<String> sharedUris = uriStrings(activeWebRepository().getFiles());
        List<SharedFile> cachedFiles = repository.getDiscoveredFiles();
        cachedDiscoveredFileCount = cachedFiles.size();
        availableFiles.clear();
        for (SharedFile file : cachedFiles) {
            if (shouldHideDiscoveredFile(file)) {
                continue;
            }
            if (!sharedUris.contains(file.uri.toString())) {
                availableFiles.add(file);
            }
        }
        visibleAvailableFileLimit = FILE_RENDER_PAGE_SIZE;
    }

    private void showPendingWebAccessRequests() {
        for (WebAccessManager.AccessRequest request : WebAccessManager.pendingRequests()) {
            if (!shownWebAccessRequests.contains(request.id)) {
                showWebAccessApprovalDialog(request);
            }
        }
    }

    private void refreshWebVisitorState() {
        String scope = activeWebAccessScope();
        webVisitorCount = WebAccessManager.connectedCount(scope);
        activeWebVisitorCount = WebAccessManager.activeCount(scope);
        webVisitorBytesServed = WebAccessManager.totalBytesServed(scope);
    }

    private void renderWebVisitorStatus() {
        refreshWebVisitorState();
        if (webVisitorsButton == null || webVisitorsStatusView == null) {
            return;
        }
        boolean hasVisitors = webVisitorCount > 0;
        webVisitorsButton.setVisibility(hasVisitors ? View.VISIBLE : View.GONE);
        webVisitorsStatusView.setVisibility(hasVisitors ? View.VISIBLE : View.GONE);
        if (!hasVisitors) {
            webVisitorsStatusView.setText("");
            return;
        }
        String visitorLabel = webVisitorCount == 1 ? "1 visitor" : webVisitorCount + " visitors";
        String activeLabel = activeWebVisitorCount > 0
                ? activeWebVisitorCount + " active now"
                : "idle";
        webVisitorsStatusView.setText(activeWebShareName() + " • " + visitorLabel + " • " + activeLabel + " • " + formatSize(webVisitorBytesServed) + " used");
        webVisitorsButton.setText(visitorLabel);
    }

    private void showWebVisitorsDialog() {
        refreshWebVisitorState();
        String scope = activeWebAccessScope();
        if (WebAccessManager.activeSessions(scope).isEmpty()) {
            renderWebVisitorStatus();
            Toast.makeText(this, "No " + activeWebShareName() + " visitors.", Toast.LENGTH_SHORT).show();
            return;
        }

        int ink = Color.rgb(241, 248, 246);
        int muted = Color.rgb(148, 163, 184);
        int accent = Color.rgb(61, 198, 190);
        int panel = Color.rgb(10, 21, 27);

        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);

        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setPadding(dp(14), dp(14), dp(14), dp(14));
        shell.setBackground(cardBackground(panel, dp(24)));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        shell.addView(header, fullWidthWithMargins(0, 0, 0, dp(10)));

        LinearLayout titleCopy = new LinearLayout(this);
        titleCopy.setOrientation(LinearLayout.VERTICAL);
        header.addView(titleCopy, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView title = text(activeWebShareName() + " visitors", 20, ink, true);
        title.setIncludeFontPadding(false);
        titleCopy.addView(title);

        TextView summary = text("", 11, muted, false);
        summary.setPadding(0, dp(4), 0, 0);
        titleCopy.addView(summary);

        ImageButton closeButton = smallIconButton(
                R.drawable.ic_close,
                Color.rgb(252, 165, 165),
                Color.rgb(69, 26, 26),
                Color.rgb(127, 29, 29),
                "Close visitors",
                "Close connected visitors"
        );
        closeButton.setOnClickListener(v -> dialog.dismiss());
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(dp(36), dp(36));
        closeParams.setMargins(dp(8), 0, 0, 0);
        header.addView(closeButton, closeParams);

        TextView note = text("Remove a visitor to revoke " + activeWebShareName() + " browser access.", 11, Color.rgb(254, 215, 170), true);
        note.setPadding(dp(10), dp(8), dp(10), dp(8));
        note.setBackground(rounded(Color.rgb(43, 30, 16), dp(14), Color.rgb(180, 83, 9), dp(1)));
        shell.addView(note, fullWidthWithMargins(0, 0, 0, dp(9)));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setClipToPadding(false);
        LinearLayout visitorList = new LinearLayout(this);
        visitorList.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(visitorList);
        shell.addView(scroll, fullWidthWithMargins(0, 0, 0, 0, Math.min(dp(420), getResources().getDisplayMetrics().heightPixels - dp(220))));

        final Runnable[] renderRows = new Runnable[1];
        renderRows[0] = () -> {
            List<WebAccessManager.AccessSession> sessions = WebAccessManager.activeSessions(scope);
            refreshWebVisitorState();
            renderWebVisitorStatus();
            if (sessions.isEmpty()) {
                dialog.dismiss();
                return;
            }
            summary.setText(webVisitorCount + " connected / " + activeWebVisitorCount + " active / " + formatSize(webVisitorBytesServed) + " used");
            visitorList.removeAllViews();
            for (WebAccessManager.AccessSession session : sessions) {
                visitorList.addView(webVisitorSessionRow(session, renderRows[0]), fullWidthWithMargins(0, 0, 0, dp(10)));
            }
        };

        dialog.setContentView(shell);
        dialog.show();
        android.view.Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            int screenWidth = getResources().getDisplayMetrics().widthPixels;
            int minWidth = Math.min(screenWidth - dp(56), dp(280));
            int width = Math.min(screenWidth - dp(112), dp(440));
            window.setLayout(Math.max(minWidth, width), android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        renderRows[0].run();
    }

    private LinearLayout webVisitorSessionRow(WebAccessManager.AccessSession session, Runnable rerender) {
        int activeColor = session.activeRequests > 0 ? Color.rgb(34, 197, 94) : Color.rgb(148, 163, 184);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(10), dp(10), dp(10), dp(10));
        card.setBackground(rowBackground());

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(top, fullWidthWithMargins(0, 0, 0, dp(8)));

        TextView avatar = text(accessInitial(session.email), 15, Color.rgb(4, 14, 16), true);
        avatar.setGravity(Gravity.CENTER);
        avatar.setIncludeFontPadding(false);
        avatar.setBackground(rounded(Color.rgb(61, 198, 190), dp(14), 0, 0));
        LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(dp(38), dp(38));
        avatarParams.setMargins(0, 0, dp(9), 0);
        top.addView(avatar, avatarParams);

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        top.addView(copy, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView email = text(accessValue(session.email), 14, Color.rgb(241, 248, 246), true);
        email.setSingleLine(true);
        email.setEllipsize(TextUtils.TruncateAt.END);
        copy.addView(email);

        TextView state = text((session.activeRequests > 0 ? "Active now" : "Idle") + " / " + formatAgo(session.lastSeenAt), 11, activeColor, true);
        state.setPadding(0, dp(3), 0, 0);
        copy.addView(state);

        Button remove = button("Remove");
        styleButton(remove, Color.rgb(69, 26, 26), Color.rgb(252, 165, 165), Color.rgb(127, 29, 29), 14);
        setButtonIcon(remove, R.drawable.ic_close, Color.rgb(252, 165, 165), 18);
        remove.setTooltipText("Remove this connected visitor");
        remove.setOnClickListener(v -> {
            boolean removed = WebAccessManager.revokeSession(this, session.token);
            refreshWebVisitorState();
            renderWebVisitorStatus();
            Toast.makeText(
                    this,
                    removed ? "Removed access for " + accessValue(session.email) : "Visitor was already disconnected.",
                    Toast.LENGTH_SHORT
            ).show();
            if (rerender != null) {
                rerender.run();
            }
        });
        LinearLayout.LayoutParams removeParams = new LinearLayout.LayoutParams(dp(98), dp(36));
        removeParams.setMargins(dp(8), 0, 0, 0);
        top.addView(remove, removeParams);

        TextView usage = text(
                formatSize(session.bytesServed) + " used / " + session.requestCount + " requests",
                11,
                Color.rgb(203, 213, 225),
                true
        );
        usage.setPadding(dp(9), dp(7), dp(9), dp(7));
        usage.setBackground(rounded(Color.rgb(8, 18, 23), dp(14), Color.rgb(38, 60, 69), dp(1)));
        card.addView(usage, fullWidthWithMargins(0, 0, 0, dp(7)));

        card.addView(webVisitorMiniDetail("Location", session.location));
        card.addView(webVisitorMiniDetail("Device", session.device));
        card.addView(webVisitorMiniDetail("IP", session.ip));
        return card;
    }

    private TextView webVisitorMiniDetail(String label, String value) {
        TextView view = text(label + ": " + accessValue(value), 11, Color.rgb(148, 163, 184), false);
        view.setSingleLine(true);
        view.setEllipsize(TextUtils.TruncateAt.END);
        view.setPadding(0, dp(2), 0, 0);
        return view;
    }

    private void showWebAccessApprovalDialog(WebAccessManager.AccessRequest request) {
        if (request == null || TextUtils.isEmpty(request.id) || shownWebAccessRequests.contains(request.id)) {
            return;
        }
        shownWebAccessRequests.add(request.id);
        mainHandler.post(() -> {
            if (isFinishing()) {
                shownWebAccessRequests.remove(request.id);
                return;
            }
            int ink = Color.rgb(241, 248, 246);
            int muted = Color.rgb(148, 163, 184);
            int accent = Color.rgb(61, 198, 190);
            int panel = Color.rgb(10, 21, 27);
            int warn = Color.rgb(255, 170, 96);

            Dialog dialog = new Dialog(this);
            dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
            dialog.setCanceledOnTouchOutside(false);
            dialog.setOnDismissListener(v -> {
                shownWebAccessRequests.remove(request.id);
                disableUrgentWebAccessWindowIfIdle();
            });

            ScrollView scroller = new ScrollView(this);
            scroller.setFillViewport(false);
            scroller.setClipToPadding(false);

            LinearLayout shell = new LinearLayout(this);
            shell.setOrientation(LinearLayout.VERTICAL);
            shell.setPadding(dp(16), dp(16), dp(16), dp(16));
            shell.setBackground(gradient(
                    new int[]{Color.rgb(8, 18, 23), panel, Color.rgb(6, 14, 18)},
                    dp(28),
                    Color.rgb(38, 60, 69),
                    dp(1),
                    GradientDrawable.Orientation.TL_BR
            ));
            scroller.addView(shell);

            LinearLayout header = new LinearLayout(this);
            header.setOrientation(LinearLayout.HORIZONTAL);
            header.setGravity(Gravity.CENTER_VERTICAL);
            shell.addView(header, fullWidthWithMargins(0, 0, 0, dp(14)));

            LinearLayout iconShell = new LinearLayout(this);
            iconShell.setGravity(Gravity.CENTER);
            iconShell.setBackground(gradient(
                    new int[]{Color.rgb(57, 37, 12), Color.rgb(24, 26, 27)},
                    dp(18),
                    Color.rgb(180, 83, 9),
                    dp(1),
                    GradientDrawable.Orientation.TL_BR
            ));
            ImageView headerIcon = new ImageView(this);
            headerIcon.setImageDrawable(tintedIcon(R.drawable.ic_lock, warn, 26));
            iconShell.addView(headerIcon, new LinearLayout.LayoutParams(dp(28), dp(28)));
            LinearLayout.LayoutParams iconShellParams = new LinearLayout.LayoutParams(dp(52), dp(52));
            iconShellParams.setMargins(0, 0, dp(12), 0);
            header.addView(iconShell, iconShellParams);

            LinearLayout titleCopy = new LinearLayout(this);
            titleCopy.setOrientation(LinearLayout.VERTICAL);
            header.addView(titleCopy, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

            TextView eyebrow = text("ACCESS REQUEST", 11, warn, true);
            eyebrow.setIncludeFontPadding(false);
            eyebrow.setLetterSpacing(0.18f);
            titleCopy.addView(eyebrow);

            TextView title = text("Approve " + WebAccessManager.scopeLabel(request.scope) + "?", 24, ink, true);
            title.setIncludeFontPadding(false);
            title.setPadding(0, dp(5), 0, 0);
            titleCopy.addView(title);

            TextView subtitle = text("Someone wants to open your " + WebAccessManager.scopeLabel(request.scope) + " files.", 12, muted, false);
            subtitle.setPadding(0, dp(5), 0, 0);
            titleCopy.addView(subtitle);

            LinearLayout codeCard = new LinearLayout(this);
            codeCard.setOrientation(LinearLayout.VERTICAL);
            codeCard.setGravity(Gravity.CENTER);
            codeCard.setPadding(dp(14), dp(14), dp(14), dp(14));
            codeCard.setBackground(gradient(
                    new int[]{Color.rgb(57, 37, 12), Color.rgb(31, 27, 20)},
                    dp(20),
                    Color.rgb(180, 83, 9),
                    dp(1),
                    GradientDrawable.Orientation.LEFT_RIGHT
            ));
            shell.addView(codeCard, fullWidthWithMargins(0, 0, 0, dp(12)));

            TextView codeLabel = text("Approval code", 12, Color.rgb(253, 186, 116), true);
            codeLabel.setIncludeFontPadding(false);
            codeLabel.setGravity(Gravity.CENTER);
            codeCard.addView(codeLabel);

            TextView codeValue = text(TextUtils.isEmpty(request.code) ? "----" : request.code, 28, Color.WHITE, true);
            codeValue.setGravity(Gravity.CENTER);
            codeValue.setIncludeFontPadding(false);
            codeValue.setLetterSpacing(0.08f);
            codeValue.setPadding(dp(22), 0, dp(22), 0);
            codeValue.setBackground(rounded(Color.rgb(12, 25, 31), dp(16), Color.rgb(255, 170, 96), dp(1)));
            LinearLayout.LayoutParams codeValueParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(54));
            codeValueParams.setMargins(0, dp(10), 0, dp(8));
            codeCard.addView(codeValue, codeValueParams);

            TextView codeHint = text("Match this with the browser before approving.", 11, Color.rgb(203, 213, 225), false);
            codeHint.setGravity(Gravity.CENTER);
            codeCard.addView(codeHint);

            shell.addView(webAccessRequesterCard(request.email), fullWidthWithMargins(0, 0, 0, dp(10)));
            shell.addView(webAccessDetailRow("Location", request.location, R.drawable.ic_wifi, Color.rgb(34, 197, 94)), fullWidthWithMargins(0, 0, 0, dp(8)));
            shell.addView(webAccessDetailRow("Device", request.device, R.drawable.ic_phone_android, Color.rgb(126, 149, 255)), fullWidthWithMargins(0, 0, 0, dp(8)));
            shell.addView(webAccessDetailRow("IP address", request.ip, R.drawable.ic_lock, accent), fullWidthWithMargins(0, 0, 0, dp(8)));
            shell.addView(webAccessDetailRow("Browser", request.userAgent, R.drawable.ic_help_outline, Color.rgb(203, 213, 225)), fullWidthWithMargins(0, 0, 0, dp(12)));

            TextView warning = text("Only approve if you expected this request and recognize the details above.", 12, Color.rgb(254, 215, 170), true);
            warning.setPadding(dp(12), dp(10), dp(12), dp(10));
            warning.setBackground(rounded(Color.rgb(43, 30, 16), dp(16), Color.rgb(180, 83, 9), dp(1)));
            shell.addView(warning, fullWidthWithMargins(0, 0, 0, dp(14)));

            LinearLayout actions = new LinearLayout(this);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            actions.setGravity(Gravity.CENTER);
            shell.addView(actions, fullWidthWithMargins(0, 0, 0, 0, dp(48)));

            Button denyButton = button("Deny");
            styleButton(denyButton, Color.rgb(69, 26, 26), Color.rgb(252, 165, 165), Color.rgb(127, 29, 29), 16);
            setButtonIcon(denyButton, R.drawable.ic_close, Color.rgb(252, 165, 165));
            denyButton.setTooltipText("Reject this web access request");
            denyButton.setOnClickListener(v -> {
                WebAccessManager.deny(request.id);
                WebAccessManager.clearNotification(this);
                Toast.makeText(this, "Access denied.", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            });
            actions.addView(denyButton, toolbarButtonParams(0, dp(6)));

            Button approveButton = button("Approve");
            styleButton(approveButton, Color.rgb(20, 83, 45), Color.rgb(187, 247, 208), Color.rgb(34, 197, 94), 16);
            setButtonIcon(approveButton, R.drawable.ic_done, Color.rgb(187, 247, 208));
            approveButton.setTooltipText("Allow this visitor to open shared files");
            approveButton.setOnClickListener(v -> {
                WebAccessManager.AccessDecision decision = WebAccessManager.approve(request.id);
                WebAccessManager.clearNotification(this);
                if ("approved".equals(decision.status)) {
                    WebAccessManager.broadcastSessionStatus(this);
                    refreshWebVisitorState();
                    renderWebVisitorStatus();
                    Toast.makeText(this, "Access approved for " + accessValue(request.email), Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Approval request expired.", Toast.LENGTH_LONG).show();
                }
                dialog.dismiss();
            });
            actions.addView(approveButton, toolbarButtonParams(dp(6), 0));

            Button laterButton = button("Decide later");
            styleButton(laterButton, Color.rgb(12, 25, 31), muted, Color.rgb(38, 60, 69), 16);
            laterButton.setTooltipText("Keep this request pending");
            laterButton.setOnClickListener(v -> dialog.dismiss());
            shell.addView(laterButton, fullWidthWithMargins(0, dp(10), 0, 0, dp(44)));

            dialog.setContentView(scroller);
            dialog.show();
            android.view.Window window = dialog.getWindow();
            if (window != null) {
                window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
                window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                android.view.WindowManager.LayoutParams attributes = window.getAttributes();
                attributes.dimAmount = 0.72f;
                window.setAttributes(attributes);
                window.setLayout(centeredDialogWidth(dp(440)), android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
            }
        });
    }

    private LinearLayout webAccessRequesterCard(String email) {
        int accent = Color.rgb(61, 198, 190);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(12), dp(12), dp(12));
        row.setBackground(rowBackground());

        TextView avatar = text(accessInitial(email), 18, Color.rgb(4, 14, 16), true);
        avatar.setGravity(Gravity.CENTER);
        avatar.setIncludeFontPadding(false);
        avatar.setBackground(rounded(accent, dp(16), 0, 0));
        LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(dp(44), dp(44));
        avatarParams.setMargins(0, 0, dp(12), 0);
        row.addView(avatar, avatarParams);

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        row.addView(copy, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView label = text("Requester", 11, Color.rgb(148, 163, 184), true);
        label.setIncludeFontPadding(false);
        copy.addView(label);

        TextView value = text(accessValue(email), 15, Color.rgb(241, 248, 246), true);
        value.setSingleLine(true);
        value.setEllipsize(TextUtils.TruncateAt.END);
        value.setPadding(0, dp(4), 0, 0);
        copy.addView(value);

        TextView pill = text("Pending", 11, Color.rgb(254, 215, 170), true);
        pill.setGravity(Gravity.CENTER);
        pill.setPadding(dp(10), 0, dp(10), 0);
        pill.setBackground(rounded(Color.rgb(43, 30, 16), dp(14), Color.rgb(180, 83, 9), dp(1)));
        LinearLayout.LayoutParams pillParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(28));
        pillParams.setMargins(dp(10), 0, 0, 0);
        row.addView(pill, pillParams);
        return row;
    }

    private LinearLayout webAccessDetailRow(String label, String value, int iconId, int accentColor) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        row.setBackground(rounded(Color.rgb(12, 25, 31), dp(16), Color.rgb(38, 60, 69), dp(1)));

        LinearLayout iconShell = new LinearLayout(this);
        iconShell.setGravity(Gravity.CENTER);
        iconShell.setBackground(rounded(blendColor(accentColor, Color.BLACK, 0.68f), dp(14), blendColor(accentColor, Color.WHITE, 0.2f), dp(1)));
        ImageView icon = new ImageView(this);
        icon.setImageDrawable(tintedIcon(iconId, accentColor, 22));
        iconShell.addView(icon, new LinearLayout.LayoutParams(dp(24), dp(24)));
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(42), dp(42));
        iconParams.setMargins(0, 0, dp(10), 0);
        row.addView(iconShell, iconParams);

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        row.addView(copy, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView labelView = text(label, 11, Color.rgb(148, 163, 184), true);
        labelView.setIncludeFontPadding(false);
        copy.addView(labelView);

        TextView valueView = text(accessValue(value), 13, Color.rgb(241, 248, 246), false);
        valueView.setSingleLine(true);
        valueView.setEllipsize(TextUtils.TruncateAt.END);
        valueView.setPadding(0, dp(3), 0, 0);
        copy.addView(valueView);
        return row;
    }

    private String accessInitial(String value) {
        String cleaned = value == null ? "" : value.trim();
        if (cleaned.isEmpty()) {
            return "?";
        }
        return cleaned.substring(0, 1).toUpperCase(Locale.US);
    }

    private String accessValue(String value) {
        if (TextUtils.isEmpty(value)) {
            return "Unknown";
        }
        String cleaned = value.replace("\r", " ").replace("\n", " ").trim();
        return cleaned.length() > 180 ? cleaned.substring(0, 180) + "..." : cleaned;
    }

    private String formatAgo(long timestamp) {
        if (timestamp <= 0) {
            return "Unknown";
        }
        long seconds = Math.max(0, (System.currentTimeMillis() - timestamp) / 1000);
        if (seconds < 10) {
            return "just now";
        }
        if (seconds < 60) {
            return seconds + "s ago";
        }
        long minutes = seconds / 60;
        if (minutes < 60) {
            return minutes + "m ago";
        }
        long hours = minutes / 60;
        return hours + "h ago";
    }

    private void maybeRequestDocumentStorageThenScan(boolean showToast) {
        if (needsDocumentStorageAccess()) {
            showDocumentStorageAccessPrompt(showToast);
            return;
        }
        startProgressiveFileScan(showToast);
    }

    private boolean needsDocumentStorageAccess() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager();
    }

    private boolean hasDocumentStorageAccess() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager();
    }

    private void showDocumentStorageAccessPrompt(boolean showToast) {
        new AlertDialog.Builder(this)
                .setTitle("Find documents")
                .setMessage("Android needs All files access before K-Share can automatically find PDFs, DOCX, ZIPs, and other documents. Videos and songs can still be scanned without it.")
                .setPositiveButton("Open settings", (dialog, which) -> openDocumentStorageSettings())
                .setNegativeButton("Media only", (dialog, which) -> startProgressiveFileScan(showToast))
                .show();
    }

    private void openDocumentStorageSettings() {
        pendingRefreshAfterDocumentStorageSettings = true;
        Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
        intent.setData(Uri.parse("package:" + getPackageName()));
        try {
            startActivityForResult(intent, REQUEST_DOCUMENT_STORAGE);
        } catch (Exception e) {
            try {
                startActivityForResult(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION), REQUEST_DOCUMENT_STORAGE);
            } catch (Exception secondError) {
                pendingRefreshAfterDocumentStorageSettings = false;
                Toast.makeText(this, "Could not open storage settings.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void startProgressiveFileScan(boolean showToast) {
        fileScanRunning = true;
        visibleAvailableFileLimit = FILE_RENDER_PAGE_SIZE;
        updateFileScanUi("Scanning...", true);
        fileScanExecutor.execute(() -> {
            Map<String, SharedFile> discovered = new LinkedHashMap<>();
            try {
                scanMediaStoreProgressively(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        MediaStore.Video.Media._ID,
                        "videos",
                        discovered
                );
                scanMediaStoreProgressively(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        MediaStore.Audio.Media._ID,
                        "audio files",
                        discovered
                );
                scanIndexedDocumentsProgressively(discovered);
                scanIndexedCompressedFilesProgressively(discovered);
                if (hasDocumentStorageAccess()) {
                    scanFileSystemDocumentsAndCompressedProgressively(discovered);
                }

                if (Thread.currentThread().isInterrupted()) {
                    return;
                }

                List<SharedFile> completed = new ArrayList<>(discovered.values());
                repository.saveDiscoveredFiles(completed);
                mainHandler.post(() -> finishProgressiveFileScan(completed, showToast, null));
            } catch (Exception e) {
                if (Thread.currentThread().isInterrupted()) {
                    return;
                }
                Log.e(TAG, "File refresh failed.", e);
                List<SharedFile> partial = new ArrayList<>(discovered.values());
                repository.saveDiscoveredFiles(partial);
                mainHandler.post(() -> finishProgressiveFileScan(
                        partial,
                        showToast,
                        "Partial scan."
                ));
            }
        });
    }

    private void scanMediaStoreProgressively(
            Uri collectionUri,
            String idColumnName,
            String stageLabel,
            Map<String, SharedFile> discovered
    ) {
        List<String> projection = new ArrayList<>();
        projection.add(idColumnName);
        projection.add(MediaStore.MediaColumns.DISPLAY_NAME);
        projection.add(MediaStore.MediaColumns.MIME_TYPE);
        projection.add(MediaStore.MediaColumns.SIZE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            projection.add(MediaStore.MediaColumns.RELATIVE_PATH);
        }
        projection.add(MediaStore.MediaColumns.DATA);
        List<SharedFile> batch = new ArrayList<>(FILE_SCAN_BATCH_SIZE);
        try (Cursor cursor = getContentResolver().query(
                collectionUri,
                projection.toArray(new String[0]),
                null,
                null,
                MediaStore.MediaColumns.DATE_MODIFIED + " DESC"
        )) {
            if (cursor == null) {
                return;
            }
            int idColumn = cursor.getColumnIndexOrThrow(idColumnName);
            int nameColumn = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME);
            int mimeColumn = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE);
            int sizeColumn = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE);
            int relativePathColumn = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH);
            int dataColumn = cursor.getColumnIndex(MediaStore.MediaColumns.DATA);
            while (!Thread.currentThread().isInterrupted() && cursor.moveToNext()) {
                Uri uri = ContentUris.withAppendedId(collectionUri, cursor.getLong(idColumn));
                String name = nameColumn >= 0 ? cursor.getString(nameColumn) : null;
                String mimeType = mimeColumn >= 0 ? cursor.getString(mimeColumn) : null;
                long size = sizeColumn >= 0 && !cursor.isNull(sizeColumn) ? cursor.getLong(sizeColumn) : -1;
                SharedFile file = new SharedFile(
                        uri,
                        TextUtils.isEmpty(name) ? "media-file" : name,
                        mimeType,
                        size,
                        parentFolderFromColumns(cursorString(cursor, relativePathColumn), cursorString(cursor, dataColumn))
                );
                discovered.put(uri.toString(), file);
                batch.add(file);
                if (batch.size() >= FILE_SCAN_BATCH_SIZE) {
                    dispatchDiscoveredBatch(batch, discovered.size(), stageLabel);
                    batch = new ArrayList<>(FILE_SCAN_BATCH_SIZE);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not scan " + stageLabel + ".", e);
        }
        dispatchDiscoveredBatch(batch, discovered.size(), stageLabel);
    }

    private String[] indexedFileProjection() {
        List<String> projection = new ArrayList<>();
        projection.add(MediaStore.Files.FileColumns._ID);
        projection.add(MediaStore.MediaColumns.DISPLAY_NAME);
        projection.add(MediaStore.MediaColumns.MIME_TYPE);
        projection.add(MediaStore.MediaColumns.SIZE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            projection.add(MediaStore.MediaColumns.RELATIVE_PATH);
        }
        projection.add(MediaStore.MediaColumns.DATA);
        return projection.toArray(new String[0]);
    }

    private void scanIndexedDocumentsProgressively(Map<String, SharedFile> discovered) {
        Uri collectionUri = MediaStore.Files.getContentUri("external");
        String[] projection = indexedFileProjection();
        List<SharedFile> batch = new ArrayList<>(FILE_SCAN_BATCH_SIZE);
        try (Cursor cursor = getContentResolver().query(
                collectionUri,
                projection,
                documentSelection(),
                documentSelectionArgs(),
                MediaStore.MediaColumns.DATE_MODIFIED + " DESC"
        )) {
            if (cursor == null) {
                return;
            }
            int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID);
            int nameColumn = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME);
            int mimeColumn = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE);
            int sizeColumn = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE);
            int relativePathColumn = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH);
            int dataColumn = cursor.getColumnIndex(MediaStore.MediaColumns.DATA);
            while (!Thread.currentThread().isInterrupted() && cursor.moveToNext()) {
                String name = nameColumn >= 0 ? cursor.getString(nameColumn) : null;
                String mimeType = mimeColumn >= 0 ? cursor.getString(mimeColumn) : null;
                if (shouldSkipIndexedStoragePath(cursorString(cursor, relativePathColumn), cursorString(cursor, dataColumn))) {
                    continue;
                }
                if (!isDocumentCandidate(name, mimeType)) {
                    continue;
                }
                Uri uri = ContentUris.withAppendedId(collectionUri, cursor.getLong(idColumn));
                long size = sizeColumn >= 0 && !cursor.isNull(sizeColumn) ? cursor.getLong(sizeColumn) : -1;
                SharedFile file = new SharedFile(
                        uri,
                        TextUtils.isEmpty(name) ? "document-file" : name,
                        mimeType,
                        size,
                        parentFolderFromColumns(cursorString(cursor, relativePathColumn), cursorString(cursor, dataColumn))
                );
                discovered.put(uri.toString(), file);
                batch.add(file);
                if (batch.size() >= FILE_SCAN_BATCH_SIZE) {
                    dispatchDiscoveredBatch(batch, discovered.size(), "documents");
                    batch = new ArrayList<>(FILE_SCAN_BATCH_SIZE);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not scan indexed documents.", e);
        }
        dispatchDiscoveredBatch(batch, discovered.size(), "documents");
    }

    private void scanIndexedCompressedFilesProgressively(Map<String, SharedFile> discovered) {
        Uri collectionUri = MediaStore.Files.getContentUri("external");
        String[] projection = indexedFileProjection();
        List<SharedFile> batch = new ArrayList<>(FILE_SCAN_BATCH_SIZE);
        try (Cursor cursor = getContentResolver().query(
                collectionUri,
                projection,
                compressedSelection(),
                compressedSelectionArgs(),
                MediaStore.MediaColumns.DATE_MODIFIED + " DESC"
        )) {
            if (cursor == null) {
                return;
            }
            int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID);
            int nameColumn = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME);
            int mimeColumn = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE);
            int sizeColumn = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE);
            int relativePathColumn = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH);
            int dataColumn = cursor.getColumnIndex(MediaStore.MediaColumns.DATA);
            while (!Thread.currentThread().isInterrupted() && cursor.moveToNext()) {
                String name = nameColumn >= 0 ? cursor.getString(nameColumn) : null;
                String mimeType = mimeColumn >= 0 ? cursor.getString(mimeColumn) : null;
                if (shouldSkipIndexedStoragePath(cursorString(cursor, relativePathColumn), cursorString(cursor, dataColumn))) {
                    continue;
                }
                SharedFile file = new SharedFile(
                        ContentUris.withAppendedId(collectionUri, cursor.getLong(idColumn)),
                        TextUtils.isEmpty(name) ? "compressed-file" : name,
                        mimeType,
                        sizeColumn >= 0 && !cursor.isNull(sizeColumn) ? cursor.getLong(sizeColumn) : -1,
                        parentFolderFromColumns(cursorString(cursor, relativePathColumn), cursorString(cursor, dataColumn))
                );
                if (!file.isCompressed()) {
                    continue;
                }
                discovered.put(file.uri.toString(), file);
                batch.add(file);
                if (batch.size() >= FILE_SCAN_BATCH_SIZE) {
                    dispatchDiscoveredBatch(batch, discovered.size(), "compressed files");
                    batch = new ArrayList<>(FILE_SCAN_BATCH_SIZE);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not scan indexed compressed files.", e);
        }
        dispatchDiscoveredBatch(batch, discovered.size(), "compressed files");
    }

    private void scanFileSystemDocumentsAndCompressedProgressively(Map<String, SharedFile> discovered) {
        File root = Environment.getExternalStorageDirectory();
        if (root == null || !root.exists() || !root.canRead()) {
            return;
        }
        ArrayDeque<File> pending = new ArrayDeque<>();
        Set<String> visitedDirectories = new HashSet<>();
        Set<String> knownSignatures = fileSignatures(new ArrayList<>(discovered.values()));
        List<SharedFile> batch = new ArrayList<>(FILE_SCAN_BATCH_SIZE);
        pending.add(root);

        while (!Thread.currentThread().isInterrupted() && !pending.isEmpty()) {
            File current = pending.removeLast();
            if (current == null || !current.exists()) {
                continue;
            }
            if (current.isDirectory()) {
                if (shouldSkipStorageDirectory(current, root)) {
                    continue;
                }
                String canonicalPath;
                try {
                    canonicalPath = current.getCanonicalPath();
                } catch (Exception e) {
                    canonicalPath = current.getAbsolutePath();
                }
                if (!visitedDirectories.add(canonicalPath)) {
                    continue;
                }
                File[] children = current.listFiles();
                if (children == null) {
                    continue;
                }
                for (File child : children) {
                    pending.add(child);
                }
                continue;
            }
            if (!current.isFile() || !current.canRead() || current.isHidden()) {
                continue;
            }

            String name = current.getName();
            String mimeType = mimeTypeFromName(name);
            SharedFile file = new SharedFile(Uri.fromFile(current), name, mimeType, current.length(), parentFolderForFile(root, current));
            if (!file.isDocument() && !file.isCompressed()) {
                continue;
            }
            if (!knownSignatures.add(fileSignature(file.name, file.size))) {
                continue;
            }
            discovered.put(file.uri.toString(), file);
            batch.add(file);
            if (batch.size() >= FILE_SCAN_BATCH_SIZE) {
                dispatchDiscoveredBatch(batch, discovered.size(), "device files");
                batch = new ArrayList<>(FILE_SCAN_BATCH_SIZE);
            }
        }
        dispatchDiscoveredBatch(batch, discovered.size(), "device files");
    }

    private boolean shouldSkipStorageDirectory(File directory, File root) {
        if (directory.equals(root)) {
            return false;
        }
        String name = directory.getName();
        if (TextUtils.isEmpty(name)) {
            return true;
        }
        String lowerName = name.toLowerCase(Locale.US);
        if (lowerName.startsWith(".") || "lost.dir".equals(lowerName)) {
            return true;
        }
        return isBlockedStoragePath(directory.getAbsolutePath());
    }

    private boolean shouldSkipIndexedStoragePath(String relativePath, String dataPath) {
        return isBlockedStoragePath(relativePath) || isBlockedStoragePath(dataPath);
    }

    private boolean shouldHideDiscoveredFile(SharedFile file) {
        if (file == null || file.uri == null) {
            return true;
        }
        return isBlockedStoragePath(file.uri.getPath()) || isBlockedStoragePath(file.uri.toString());
    }

    private boolean isBlockedStoragePath(String value) {
        if (TextUtils.isEmpty(value)) {
            return false;
        }
        String path = Uri.decode(value).replace("\\", "/").toLowerCase(Locale.US);
        while (path.startsWith("/")) {
            path = path.substring(1);
        }
        return path.startsWith("android/data/")
                || path.startsWith("android/obb/")
                || path.contains("/android/data/")
                || path.contains("/android/obb/")
                || path.contains(":android/data/")
                || path.contains(":android/obb/");
    }

    private String cursorString(Cursor cursor, int column) {
        if (cursor == null || column < 0) {
            return null;
        }
        try {
            return cursor.isNull(column) ? null : cursor.getString(column);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String parentFolderFromColumns(String relativePath, String dataPath) {
        if (!TextUtils.isEmpty(relativePath)) {
            return relativePath;
        }
        if (TextUtils.isEmpty(dataPath)) {
            return null;
        }
        File parent = new File(dataPath).getParentFile();
        return parent == null ? null : parent.getPath();
    }

    private String parentFolderForFile(File root, File file) {
        if (file == null) {
            return null;
        }
        File parent = file.getParentFile();
        if (parent == null) {
            return null;
        }
        try {
            String rootPath = root.getCanonicalPath();
            String parentPath = parent.getCanonicalPath();
            if (parentPath.equals(rootPath)) {
                return "Device storage";
            }
            if (parentPath.startsWith(rootPath + File.separator)) {
                return parentPath.substring(rootPath.length() + 1);
            }
            return parentPath;
        } catch (Exception e) {
            return parent.getPath();
        }
    }

    private Set<String> fileSignatures(List<SharedFile> files) {
        Set<String> signatures = new HashSet<>();
        for (SharedFile file : files) {
            signatures.add(fileSignature(file.name, file.size));
        }
        return signatures;
    }

    private String fileSignature(String name, long size) {
        return (name == null ? "" : name.trim().toLowerCase(Locale.US)) + "|" + size;
    }

    private String mimeTypeFromName(String name) {
        if (TextUtils.isEmpty(name)) {
            return "application/octet-stream";
        }
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "application/octet-stream";
        }
        String extension = name.substring(dot + 1).toLowerCase(Locale.US);
        String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        return TextUtils.isEmpty(mimeType) ? "application/octet-stream" : mimeType;
    }

    private String documentSelection() {
        StringBuilder selection = new StringBuilder("(");
        boolean first = true;
        for (String ignored : DOCUMENT_MIME_FILTERS) {
            if (!first) {
                selection.append(" OR ");
            }
            selection.append(MediaStore.MediaColumns.MIME_TYPE).append(" LIKE ?");
            first = false;
        }
        for (String ignored : DOCUMENT_EXTENSION_FILTERS) {
            selection.append(" OR ")
                    .append(MediaStore.MediaColumns.DISPLAY_NAME)
                    .append(" LIKE ?");
        }
        selection.append(")");
        return selection.toString();
    }

    private String[] documentSelectionArgs() {
        List<String> args = new ArrayList<>();
        Collections.addAll(args, DOCUMENT_MIME_FILTERS);
        Collections.addAll(args, DOCUMENT_EXTENSION_FILTERS);
        return args.toArray(new String[0]);
    }

    private String compressedSelection() {
        return indexedFileSelection(COMPRESSED_MIME_FILTERS, COMPRESSED_EXTENSION_FILTERS);
    }

    private String[] compressedSelectionArgs() {
        return indexedFileSelectionArgs(COMPRESSED_MIME_FILTERS, COMPRESSED_EXTENSION_FILTERS);
    }

    private String indexedFileSelection(String[] mimeFilters, String[] extensionFilters) {
        StringBuilder selection = new StringBuilder("(");
        boolean first = true;
        for (String ignored : mimeFilters) {
            if (!first) {
                selection.append(" OR ");
            }
            selection.append(MediaStore.MediaColumns.MIME_TYPE).append(" LIKE ?");
            first = false;
        }
        for (String ignored : extensionFilters) {
            if (!first) {
                selection.append(" OR ");
            }
            selection.append(MediaStore.MediaColumns.DISPLAY_NAME).append(" LIKE ?");
            first = false;
        }
        selection.append(")");
        return selection.toString();
    }

    private String[] indexedFileSelectionArgs(String[] mimeFilters, String[] extensionFilters) {
        List<String> args = new ArrayList<>();
        Collections.addAll(args, mimeFilters);
        Collections.addAll(args, extensionFilters);
        return args.toArray(new String[0]);
    }

    private void dispatchDiscoveredBatch(List<SharedFile> batch, int total, String stageLabel) {
        if (batch.isEmpty()) {
            mainHandler.post(() -> updateFileScanUi(stageLabel + ": " + total, true));
            return;
        }
        List<SharedFile> snapshot = new ArrayList<>(batch);
        mainHandler.post(() -> {
            mergeAvailableFiles(snapshot);
            updateFileScanUi(stageLabel + ": " + total, true);
            if (!showSharedFilter) {
                if (total <= FILE_RENDER_PAGE_SIZE || total % 500 == 0) {
                    renderFiles();
                } else {
                    updateFilterButtons();
                }
            } else {
                updateFilterButtons();
            }
        });
    }

    private void finishProgressiveFileScan(List<SharedFile> discovered, boolean showToast, String warning) {
        Set<String> sharedUris = uriStrings(activeWebRepository().getFiles());
        availableFiles.clear();
        for (SharedFile file : discovered) {
            if (!sharedUris.contains(file.uri.toString())) {
                availableFiles.add(file);
            }
        }
        fileScanRunning = false;
        cachedDiscoveredFileCount = discovered.size();
        renderFiles();
        String result = warning == null
                ? discovered.size() + " cached."
                : warning + " " + discovered.size() + ".";
        updateFileScanUi(result, false);
        if (showToast) {
            Toast.makeText(this, result, Toast.LENGTH_LONG).show();
        }
    }

    private void updateFileScanUi(String message, boolean running) {
        fileScanMessage = message == null ? "" : message;
        fileScanUiRunning = running;
        if (refreshFilesButton != null) {
            refreshFilesButton.setEnabled(!running);
            refreshFilesButton.setAlpha(running ? 0.55f : 1f);
        }
        if (fileScanStatusView != null) {
            fileScanStatusView.setText(fileScanMessage);
            fileScanStatusView.setVisibility(!TextUtils.isEmpty(fileScanMessage) || running ? View.VISIBLE : View.GONE);
        }
        if (fileScanProgressBar != null) {
            fileScanProgressBar.setVisibility(running ? View.VISIBLE : View.GONE);
        }
    }

    private void mergeAvailableFiles(List<SharedFile> files) {
        Set<String> sharedUris = uriStrings(activeWebRepository().getFiles());
        Set<String> availableUris = uriStrings(availableFiles);
        for (SharedFile file : files) {
            String uri = file.uri.toString();
            if (!sharedUris.contains(uri) && availableUris.add(uri)) {
                availableFiles.add(file);
            }
        }
    }

    private Set<String> uriStrings(List<SharedFile> files) {
        Set<String> uris = new HashSet<>();
        for (SharedFile file : files) {
            uris.add(file.uri.toString());
        }
        return uris;
    }

    private void setupWifiDirect() {
        wifiP2pManager = (WifiP2pManager) getSystemService(WIFI_P2P_SERVICE);
        if (wifiP2pManager == null) {
            return;
        }
        wifiP2pChannel = wifiP2pManager.initialize(this, getMainLooper(), () -> {
            wifiP2pChannel = null;
            nearbyDiscovering = false;
            renderNearbyState();
            Toast.makeText(this, "Wi-Fi Direct channel disconnected.", Toast.LENGTH_SHORT).show();
        });
        wifiP2pFilter = new IntentFilter();
        wifiP2pFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        wifiP2pFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        wifiP2pFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        wifiP2pFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);
        wifiP2pReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                handleWifiDirectEvent(intent);
            }
        };
    }

    private void registerWifiDirectReceiver() {
        if (wifiP2pReceiver == null || wifiP2pFilter == null || nearbyReceiverRegistered) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(wifiP2pReceiver, wifiP2pFilter, RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(wifiP2pReceiver, wifiP2pFilter);
        }
        nearbyReceiverRegistered = true;
    }

    private void unregisterWifiDirectReceiver() {
        if (!nearbyReceiverRegistered) {
            return;
        }
        unregisterReceiver(wifiP2pReceiver);
        nearbyReceiverRegistered = false;
    }

    private void handleWifiDirectEvent(Intent intent) {
        String action = intent.getAction();
        if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action)) {
            int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, WifiP2pManager.WIFI_P2P_STATE_DISABLED);
            wifiDirectEnabled = state == WifiP2pManager.WIFI_P2P_STATE_ENABLED;
            if (!wifiDirectEnabled) {
                nearbyPeers.clear();
                nearbyDiscovering = false;
                renderNearbyDevices();
            }
            renderNearbyState();
            return;
        }
        if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {
            requestNearbyPeers();
            return;
        }
        if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {
            NetworkInfo networkInfo = intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO);
            if (networkInfo != null && networkInfo.isConnected()) {
                requestConnectionInfo();
            } else {
                latestWifiP2pInfo = null;
                renderNearbyState("Not connected.");
                renderNearbyTransferState("Connect first.");
            }
            return;
        }
        if (WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION.equals(action)) {
            renderNearbyState();
        }
    }

    private void startNearbyDiscovery() {
        Log.i(TAG, "Starting nearby discovery. wifiDirectAvailable=" + (wifiP2pManager != null && wifiP2pChannel != null));
        startLanFallbackDiscovery();
        if (wifiP2pManager == null || wifiP2pChannel == null) {
            Log.w(TAG, "Wi-Fi Direct manager/channel unavailable; using LAN fallback only.");
            Toast.makeText(this, "Wi-Fi fallback ready.", Toast.LENGTH_LONG).show();
            renderNearbyState("Local Wi-Fi ready.");
            return;
        }
        if (!hasNearbyPermission()) {
            Log.i(TAG, "Requesting nearby permissions before Wi-Fi Direct discovery.");
            requestPermissions(nearbyPermissions().toArray(new String[0]), REQUEST_NEARBY_PERMISSIONS);
            return;
        }
        String unavailableReason = nearbyUnavailableReason();
        if (unavailableReason != null) {
            Log.w(TAG, "Nearby discovery blocked: " + unavailableReason);
            renderNearbyState(unavailableReason);
            if (unavailableReason.contains("Location")) {
                openLocationSettings();
            }
            return;
        }
        renderNearbyState("Searching...");
        cleanupWifiDirectBeforeDiscovery(() -> discoverNearbyPeersNow(true));
    }

    private void discoverNearbyPeersNow(boolean allowRetry) {
        if (wifiP2pManager == null || wifiP2pChannel == null || !hasNearbyPermission()) {
            renderNearbyState();
            return;
        }
        String unavailableReason = nearbyUnavailableReason();
        if (unavailableReason != null) {
            renderNearbyState(unavailableReason);
            return;
        }
        try {
            wifiP2pManager.discoverPeers(wifiP2pChannel, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    Log.i(TAG, "Wi-Fi Direct discovery started.");
                    nearbyDiscovering = true;
                    renderNearbyState("Searching...");
                }

                @Override
                public void onFailure(int reason) {
                    Log.w(TAG, "Wi-Fi Direct discoverPeers failed. reason=" + reason + " message=" + wifiP2pFailure(reason));
                    nearbyDiscovering = false;
                    if (allowRetry && (reason == WifiP2pManager.ERROR || reason == WifiP2pManager.BUSY)) {
                        renderNearbyState("Retrying...");
                        mainHandler.postDelayed(() -> cleanupWifiDirectBeforeDiscovery(() -> discoverNearbyPeersNow(false)), 1400);
                        return;
                    }
                    renderNearbyState("Local Wi-Fi ready.");
                }
            });
        } catch (SecurityException e) {
            Log.w(TAG, "Wi-Fi Direct discovery permission failure.", e);
            nearbyDiscovering = false;
            renderNearbyState("Permission needed.");
        } catch (Exception e) {
            Log.w(TAG, "Wi-Fi Direct discovery crashed before callback.", e);
            nearbyDiscovering = false;
            renderNearbyState("Local Wi-Fi ready.");
        }
    }

    private String nearbyUnavailableReason() {
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT)) {
            return "Wi-Fi Direct unsupported.";
        }
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        if (wifiManager != null && !wifiManager.isWifiEnabled()) {
            return "Wi-Fi needed.";
        }
        LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (locationManager != null && !isLocationEnabled(locationManager)) {
            return "Location needed.";
        }
        return null;
    }

    private boolean isLocationEnabled(LocationManager locationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return locationManager.isLocationEnabled();
        }
        try {
            return Settings.Secure.getInt(getContentResolver(), Settings.Secure.LOCATION_MODE)
                    != Settings.Secure.LOCATION_MODE_OFF;
        } catch (Settings.SettingNotFoundException e) {
            return true;
        }
    }

    private void openLocationSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
        } catch (Exception ignored) {
        }
    }

    private void cleanupWifiDirectBeforeDiscovery(Runnable afterCleanup) {
        if (wifiP2pManager == null || wifiP2pChannel == null) {
            afterCleanup.run();
            return;
        }
        try {
            wifiP2pManager.cancelConnect(wifiP2pChannel, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    removeWifiDirectGroup(afterCleanup);
                }

                @Override
                public void onFailure(int reason) {
                    removeWifiDirectGroup(afterCleanup);
                }
            });
        } catch (Exception ignored) {
            removeWifiDirectGroup(afterCleanup);
        }
    }

    private void removeWifiDirectGroup(Runnable afterCleanup) {
        if (wifiP2pManager == null || wifiP2pChannel == null) {
            afterCleanup.run();
            return;
        }
        try {
            wifiP2pManager.removeGroup(wifiP2pChannel, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    latestWifiP2pInfo = null;
                    afterCleanup.run();
                }

                @Override
                public void onFailure(int reason) {
                    afterCleanup.run();
                }
            });
        } catch (Exception ignored) {
            afterCleanup.run();
        }
    }

    private void stopNearbyDiscovery(boolean showToast) {
        stopLanFallbackDiscovery();
        if (wifiP2pManager == null || wifiP2pChannel == null || !hasNearbyPermission()) {
            nearbyDiscovering = false;
            renderNearbyState();
            return;
        }
        try {
            wifiP2pManager.stopPeerDiscovery(wifiP2pChannel, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    nearbyDiscovering = false;
                    renderNearbyState();
                    if (showToast) {
                        Toast.makeText(MainActivity.this, "Discovery stopped.", Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onFailure(int reason) {
                    nearbyDiscovering = false;
                    renderNearbyState("Stop failed.");
                }
            });
        } catch (SecurityException e) {
            nearbyDiscovering = false;
            renderNearbyState("Permission needed.");
        } catch (Exception e) {
            nearbyDiscovering = false;
            renderNearbyState("Stop failed.");
        }
    }

    private void requestNearbyPeers() {
        if (wifiP2pManager == null || wifiP2pChannel == null || !hasNearbyPermission()) {
            return;
        }
        try {
            wifiP2pManager.requestPeers(wifiP2pChannel, (WifiP2pDeviceList peers) -> {
                nearbyPeers.clear();
                nearbyPeers.addAll(peers.getDeviceList());
                renderNearbyDevices();
                renderNearbyState(nearbyPeers.isEmpty()
                        ? "No devices."
                        : nearbyPeers.size() + " found.");
            });
        } catch (SecurityException e) {
            renderNearbyState("Permission needed.");
        } catch (Exception e) {
            renderNearbyState("Read failed.");
        }
    }

    private void connectNearbyDevice(WifiP2pDevice device) {
        if (wifiP2pManager == null || wifiP2pChannel == null || !hasNearbyPermission()) {
            startNearbyDiscovery();
            return;
        }
        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = device.deviceAddress;
        renderNearbyState("Connecting...");
        try {
            wifiP2pManager.connect(wifiP2pChannel, config, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    renderNearbyState("Request sent.");
                }

                @Override
                public void onFailure(int reason) {
                    renderNearbyState("Connect failed.");
                }
            });
        } catch (SecurityException e) {
            renderNearbyState("Permission needed.");
        } catch (Exception e) {
            renderNearbyState("Connect failed.");
        }
    }

    private void requestConnectionInfo() {
        if (wifiP2pManager == null || wifiP2pChannel == null) {
            return;
        }
        try {
            wifiP2pManager.requestConnectionInfo(wifiP2pChannel, (WifiP2pInfo info) -> {
                latestWifiP2pInfo = info;
                if (info == null || !info.groupFormed) {
                    renderNearbyState();
                    renderNearbyTransferState("Connect first.");
                    return;
                }
                String role = info.isGroupOwner ? "host" : "client";
                String host = info.groupOwnerAddress == null ? "unknown host" : info.groupOwnerAddress.getHostAddress();
                renderNearbyState("Connected.");
                if (info.isGroupOwner) {
                    startNearbyReceiveServer();
                    renderNearbyTransferState("Ready.");
                } else {
                    renderNearbyTransferState("Ready.");
                }
            });
        } catch (SecurityException e) {
            renderNearbyState("Permission needed.");
        } catch (Exception e) {
            renderNearbyState("Read failed.");
        }
    }

    private boolean hasNearbyPermission() {
        for (String permission : nearbyPermissions()) {
            if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private List<String> nearbyPermissions() {
        List<String> permissions = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES);
        } else {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
            permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }
        return permissions;
    }

    private boolean allGranted(int[] grantResults) {
        if (grantResults.length == 0) {
            return false;
        }
        for (int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private void renderNearbyState() {
        renderNearbyState("Ready.");
    }

    private void renderNearbyState(String message) {
        if (nearbyStatusView != null) {
            nearbyStatusView.setText(message);
        }
        updateSendNearbyButton();
    }

    private void resetNearbySenderQueue() {
        clearNearbyTransferPause();
        nearbySessionFiles.clear();
        nearbySendingItems.clear();
        nearbyReshareFiles.clear();
        nearbyReshareItems.clear();
        nearbyNextSendIndex = 0;
        nearbySendingTransferStarted = false;
        nearbySending = false;
        renderNearbyTransferLists();
    }

    private void startNearbyTransferKeepAlive(String text) {
        startNearbyTransferKeepAlive(text, canPauseNearbyNotificationAction(), canCancelNearbyNotificationAction());
    }

    private void startNearbyTransferKeepAlive(String text, boolean canPause, boolean canCancel) {
        requestNearbyNotificationPermissionIfNeeded();
        try {
            Intent intent = new Intent(this, NearbyTransferService.class)
                    .setAction(NearbyTransferService.ACTION_START)
                    .putExtra(NearbyTransferService.EXTRA_TEXT, text)
                    .putExtra(NearbyTransferService.EXTRA_TRANSFER_ACTIVE, canPause || canCancel)
                    .putExtra(NearbyTransferService.EXTRA_CAN_PAUSE, canPause)
                    .putExtra(NearbyTransferService.EXTRA_CAN_CANCEL, canCancel)
                    .putExtra(NearbyTransferService.EXTRA_PAUSED, nearbyTransferPaused);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not start nearby transfer keep-alive service.", e);
        }
    }

    private void stopNearbyTransferKeepAlive() {
        lastNearbyNotificationAt = 0;
        try {
            stopService(new Intent(this, NearbyTransferService.class));
        } catch (Exception e) {
            Log.w(TAG, "Could not stop nearby transfer keep-alive service.", e);
        }
    }

    private void requestNearbyNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                || requestedNotificationPermissionThisSession) {
            return;
        }
        requestedNotificationPermissionThisSession = true;
        mainHandler.post(() -> requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATIONS));
    }

    private boolean canPauseNearbyNotificationAction() {
        if (nearbyTransferPaused) {
            return true;
        }
        return hasActiveNearbyTransferItem(nearbySendingItems)
                || hasActiveNearbyTransferItem(nearbyReceivedItems);
    }

    private boolean canCancelNearbyNotificationAction() {
        return nearbySending
                || incomingNearbyConnected
                || outgoingReceiverConnected
                || connectedNearbyReceiver != null
                || outgoingNearbyServerRunning
                || nearbySendingTransferStarted
                || nearbyReceivingTransferStarted;
    }

    private boolean hasActiveNearbyTransferItem(List<NearbyTransferItem> items) {
        try {
            for (int i = 0; i < items.size(); i++) {
                NearbyTransferItem item = items.get(i);
                if (item != null && isActiveTransferStatus(item.status)) {
                    return true;
                }
            }
        } catch (IndexOutOfBoundsException ignored) {
            // The UI can add files while a background copy updates the notification.
        }
        return false;
    }

    private void setNearbyTransferPaused(boolean paused) {
        synchronized (nearbyPauseLock) {
            nearbyTransferPaused = paused;
            if (!paused) {
                nearbyPauseLock.notifyAll();
            }
        }
        updateNearbyItemsForPauseState(nearbySendingItems, paused);
        updateNearbyItemsForPauseState(nearbyReceivedItems, paused);
        startNearbyTransferKeepAlive(paused ? "Transfer paused." : "Transfer resumed.", true, canCancelNearbyNotificationAction());
        renderNearbyTransferState(paused ? "Transfer paused." : "Transfer resumed.");
        renderNearbyTransferLists();
    }

    private void updateNearbyItemsForPauseState(List<NearbyTransferItem> items, boolean paused) {
        for (NearbyTransferItem item : items) {
            if (paused && ("Copying".equals(item.status) || "Resuming".equals(item.status))) {
                updateNearbyTransferItem(item, "Paused", item.transferredBytes, item.totalBytes, item.location);
            } else if (!paused && "Paused".equals(item.status)) {
                updateNearbyTransferItem(item, "Resuming", item.transferredBytes, item.totalBytes, item.location);
            }
        }
    }

    private void clearNearbyTransferPause() {
        synchronized (nearbyPauseLock) {
            nearbyTransferPaused = false;
            nearbyPauseLock.notifyAll();
        }
    }

    private void handleNearbyNotificationCommand(String command) {
        if (NearbyTransferService.COMMAND_PAUSE.equals(command)) {
            setNearbyTransferPaused(true);
            return;
        }
        if (NearbyTransferService.COMMAND_RESUME.equals(command)) {
            setNearbyTransferPaused(false);
            return;
        }
        if (NearbyTransferService.COMMAND_CANCEL.equals(command)) {
            cancelNearbySessionFromNotification();
        }
    }

    private void cancelNearbySessionFromNotification() {
        if (nearbyReceiverMode || incomingNearbyConnected || nearbyReceivingTransferStarted) {
            cancelIncomingNearbySession();
        } else if (connectedNearbyReceiver != null || outgoingReceiverConnected || outgoingNearbyServerRunning || nearbySendingTransferStarted) {
            cancelOutgoingNearbySession(true);
        } else {
            stopNearbyTransferKeepAlive();
            showNearbyRoleSelection();
            updateNearbyQrStatus("Cancelled.");
            renderNearbyTransferState("Cancelled.");
        }
    }

    private void waitIfNearbyTransferPaused(NearbyTransferItem item, long copied, long size) throws java.io.IOException {
        boolean markedPaused = false;
        synchronized (nearbyPauseLock) {
            while (nearbyTransferPaused) {
                if (!markedPaused) {
                    updateNearbyTransferItem(item, "Paused", copied, size, "");
                    markedPaused = true;
                }
                try {
                    nearbyPauseLock.wait(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new java.io.IOException("Transfer pause interrupted", e);
                }
            }
        }
        if (markedPaused) {
            updateNearbyTransferItem(item, copied > 0 ? "Resuming" : "Copying", copied, size, "");
        }
    }

    private void renderNearbyTransferState(String message) {
        if (nearbyTransferView != null) {
            nearbyTransferView.setText(message);
        }
        if (nearbyTransferView != null && nearbyTransferView.getVisibility() != View.VISIBLE && nearbyQrStatusView != null) {
            nearbyQrStatusView.setText(message);
        }
        updateSendNearbyButton();
    }

    private void updateSendNearbyButton() {
        if (sendNearbyButton == null) {
            return;
        }
        boolean connectedClient = latestWifiP2pInfo != null
                && latestWifiP2pInfo.groupFormed
                && !latestWifiP2pInfo.isGroupOwner
                && latestWifiP2pInfo.groupOwnerAddress != null;
        sendNearbyButton.setEnabled(connectedClient && !nearbySending && !nearbySessionFiles.isEmpty());
    }

    private void renderNearbyTransferLists() {
        updateNearbyTransferCardsVisibility();
        renderNearbyTransferList(nearbySendList, nearbySendingItems, "Nothing sending.", true);
        renderNearbyTransferList(nearbyReceivedList, nearbyReceivedItems, "Nothing received.", false);
    }

    private void updateNearbyTransferCardsVisibility() {
        if (nearbySendingCard != null) {
            nearbySendingCard.setVisibility(
                    !nearbyReceiverMode && nearbySendingTransferStarted ? View.VISIBLE : View.GONE
            );
        }
        if (nearbyReceivedCard != null) {
            nearbyReceivedCard.setVisibility(
                    nearbyReceiverMode && nearbyReceivingTransferStarted ? View.VISIBLE : View.GONE
            );
        }
    }

    private void renderNearbyTransferList(LinearLayout listView, List<NearbyTransferItem> items, String emptyMessage, boolean senderList) {
        if (listView == null) {
            return;
        }
        listView.removeAllViews();
        if (items.isEmpty()) {
            TextView empty = emptyState(emptyMessage);
            listView.addView(empty);
            return;
        }
        for (NearbyTransferItem item : items) {
            listView.addView(nearbyTransferRow(item, senderList));
        }
    }

    private LinearLayout nearbyTransferRow(NearbyTransferItem item, boolean senderList) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        row.setBackground(rowBackground());
        row.setElevation(dp(2));
        row.setLayoutParams(fullWidthWithMargins(0, dp(8), 0, 0));

        TextView title = text(item.name, 14, Color.rgb(241, 248, 246), true);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(title);

        String detail = item.status + " • " + formatSize(item.transferredBytes) + " / " + formatSize(item.totalBytes);
        if (item.bytesPerSecond > 0 || isActiveTransferStatus(item.status)) {
            detail += " • " + formatSpeed(item.bytesPerSecond);
        }
        if (!TextUtils.isEmpty(item.location)) {
            detail += " • " + item.location;
        }
        TextView status = text(detail, 12, Color.rgb(148, 163, 184), false);
        status.setPadding(0, dp(4), 0, 0);
        row.addView(status);

        ProgressBar progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(1000);
        int progressValue = item.totalBytes > 0
                ? (int) Math.min(1000, Math.max(0, item.transferredBytes * 1000 / item.totalBytes))
                : 0;
        progress.setProgress(progressValue);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            progress.getProgressDrawable().setTint(Color.rgb(22, 163, 74));
        }
        row.addView(progress, fullWidthWithMargins(0, dp(8), 0, 0, dp(5)));

        boolean canPause = canPauseResumeTransfer(item);
        boolean canCancel = canCancelTransferItem(item);
        boolean canReshare = senderList && canReshareTransferItem(item);
        if (canPause || canCancel || canReshare) {
            LinearLayout actions = new LinearLayout(this);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            actions.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(actions, fullWidthWithMargins(0, dp(8), 0, 0, dp(36)));

            if (canPause) {
                Button actionButton = button(nearbyTransferPaused ? "Resume" : "Pause");
                LinearLayout.LayoutParams pauseParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1);
                pauseParams.setMargins(0, 0, (canCancel || canReshare) ? dp(8) : 0, 0);
                actions.addView(actionButton, pauseParams);
                if (nearbyTransferPaused) {
                    styleButton(actionButton, Color.rgb(12, 25, 31), Color.rgb(61, 198, 190), Color.rgb(38, 60, 69), 14);
                    setButtonIcon(actionButton, R.drawable.ic_play_arrow, Color.rgb(61, 198, 190));
                    actionButton.setTooltipText("Resume this transfer");
                } else {
                    styleButton(actionButton, Color.rgb(12, 25, 31), Color.rgb(61, 198, 190), Color.rgb(38, 60, 69), 14);
                    setButtonIcon(actionButton, R.drawable.ic_pause, Color.rgb(61, 198, 190));
                    actionButton.setTooltipText("Pause this transfer");
                }
                actionButton.setOnClickListener(v -> setNearbyTransferPaused(!nearbyTransferPaused));
            }

            if (canCancel) {
                Button cancelButton = button("Cancel");
                styleButton(cancelButton, Color.rgb(69, 26, 26), Color.rgb(252, 165, 165), Color.rgb(127, 29, 29), 14);
                setButtonIcon(cancelButton, R.drawable.ic_close, Color.rgb(252, 165, 165));
                cancelButton.setTooltipText("Cancel this file");
                cancelButton.setOnClickListener(v -> {
                    if (senderList) {
                        cancelNearbySendingItem(item);
                    } else {
                        cancelNearbyReceivedItem(item);
                    }
                });
                LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1);
                cancelParams.setMargins(0, 0, canReshare ? dp(8) : 0, 0);
                actions.addView(cancelButton, cancelParams);
            }

            if (canReshare) {
                Button reshareButton = button("Re-share");
                styleButton(reshareButton, Color.rgb(20, 83, 45), Color.rgb(187, 247, 208), Color.rgb(34, 197, 94), 14);
                setButtonIcon(reshareButton, R.drawable.ic_share, Color.rgb(187, 247, 208));
                reshareButton.setTooltipText("Share this cancelled file again");
                reshareButton.setOnClickListener(v -> reshareNearbySendingItem(item));
                actions.addView(reshareButton, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1));
            }
        }
        return row;
    }

    private boolean isActiveTransferStatus(String status) {
        return "Copying".equals(status) || "Resuming".equals(status) || "Paused".equals(status);
    }

    private boolean canPauseResumeTransfer(NearbyTransferItem item) {
        return item != null
                && (nearbySending || incomingNearbyConnected || nearbyReceivingTransferStarted)
                && isActiveTransferStatus(item.status);
    }

    private boolean canCancelTransferItem(NearbyTransferItem item) {
        return item != null
                && !"Completed".equals(item.status)
                && !"Cancelled".equals(item.status)
                && !"Interrupted".equals(item.status)
                && !"Skipped".equals(item.status);
    }

    private boolean canReshareTransferItem(NearbyTransferItem item) {
        return item != null
                && connectedNearbyReceiver != null
                && "Cancelled".equals(item.status)
                && nearbySendingItems.indexOf(item) >= 0
                && nearbySendingItems.indexOf(item) < nearbySessionFiles.size();
    }

    private void cancelNearbySendingItem(NearbyTransferItem item) {
        if (item == null || !canCancelTransferItem(item)) {
            return;
        }
        item.cancelRequested = true;
        updateNearbyTransferItem(item, "Cancelled", item.transferredBytes, item.totalBytes, "");
        if (connectedNearbyReceiver != null && !TextUtils.isEmpty(item.fileId)) {
            notifyReceiverFileCancellation(connectedNearbyReceiver, item.fileId);
        }
        renderNearbyTransferState("Cancelled.");
        drainNearbySendQueue();
    }

    private void cancelNearbyReceivedItem(NearbyTransferItem item) {
        if (item == null || !canCancelTransferItem(item)) {
            return;
        }
        item.cancelRequested = true;
        updateNearbyTransferItem(item, "Cancelled", item.transferredBytes, item.totalBytes, "");
        if (!TextUtils.isEmpty(nearbyRemoteControlHost)
                && nearbyRemoteControlPort > 0
                && !TextUtils.isEmpty(item.remoteFileId)) {
            notifySenderFileCancellation(item.remoteFileId);
        }
        renderNearbyTransferState("Cancelled.");
    }

    private void reshareNearbySendingItem(NearbyTransferItem item) {
        int index = nearbySendingItems.indexOf(item);
        if (index < 0 || index >= nearbySessionFiles.size()) {
            Toast.makeText(this, "Original file missing.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (connectedNearbyReceiver == null) {
            renderNearbyTransferState("Connect first.");
            return;
        }

        SharedFile file = nearbySessionFiles.get(index);
        long size = resolveSharedFileSize(file);
        item.cancelRequested = false;
        item.transferredBytes = 0;
        item.totalBytes = size;
        item.bytesPerSecond = 0;
        item.lastSpeedSampleAt = 0;
        item.lastSpeedSampleBytes = 0;
        item.lastRenderAt = 0;
        item.location = "";
        item.status = nearbySending ? "Queued" : "Pending";
        if (!nearbyReshareItems.contains(item)) {
            nearbyReshareFiles.add(file);
            nearbyReshareItems.add(item);
        }

        if (nearbySending) {
            announceQueuedFilesToReceiver(connectedNearbyReceiver, Collections.singletonList(file));
        }
        updateNearbyQrStatus("Re-sharing.");
        renderNearbyTransferState(nearbySending ? "Queued." : "Re-sharing...");
        renderNearbyTransferLists();
        drainNearbySendQueue();
    }

    private void renderNearbyDevices() {
        if (nearbyDeviceList == null) {
            return;
        }
        nearbyDeviceList.removeAllViews();
        if (nearbyPeers.isEmpty() && lanPeers.isEmpty()) {
            nearbyDeviceList.addView(emptyState("No peers."));
            return;
        }
        for (WifiP2pDevice device : nearbyPeers) {
            nearbyDeviceList.addView(nearbyDeviceRow(device));
        }
        for (LanPeer peer : lanPeers) {
            nearbyDeviceList.addView(lanPeerRow(peer));
        }
    }

    private LinearLayout nearbyDeviceRow(WifiP2pDevice device) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(12), dp(12), dp(12));
        row.setBackground(rowBackground());
        row.setElevation(dp(2));
        row.setLayoutParams(fullWidthWithMargins(0, 0, 0, dp(10)));

        ImageView icon = new ImageView(this);
        icon.setImageDrawable(tintedIcon(R.drawable.ic_share, Color.rgb(61, 198, 190)));
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(34), dp(34));
        iconParams.setMargins(0, 0, dp(12), 0);
        row.addView(icon, iconParams);

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        row.addView(copy, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        String name = TextUtils.isEmpty(device.deviceName) ? "Nearby Android device" : device.deviceName;
        TextView title = text(name, 15, Color.rgb(241, 248, 246), true);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        copy.addView(title);

        TextView details = text(wifiP2pStatus(device.status), 13, Color.rgb(148, 163, 184), false);
        details.setSingleLine(true);
        details.setEllipsize(TextUtils.TruncateAt.END);
        copy.addView(details);

        Button connect = button(device.status == WifiP2pDevice.CONNECTED ? "Connected" : "Connect");
        styleButton(connect, Color.rgb(12, 25, 31), Color.rgb(61, 198, 190), Color.rgb(38, 60, 69), 14);
        setButtonIcon(connect, R.drawable.ic_share, Color.rgb(61, 198, 190));
        connect.setEnabled(device.status != WifiP2pDevice.CONNECTED);
        connect.setTooltipText("Connect to this nearby device");
        connect.setOnClickListener(v -> connectNearbyDevice(device));
        LinearLayout.LayoutParams connectParams = new LinearLayout.LayoutParams(dp(98), dp(38));
        connectParams.setMargins(dp(12), 0, 0, 0);
        row.addView(connect, connectParams);
        return row;
    }

    private LinearLayout lanPeerRow(LanPeer peer) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(12), dp(12), dp(12));
        row.setBackground(rowBackground());
        row.setElevation(dp(2));
        row.setLayoutParams(fullWidthWithMargins(0, 0, 0, dp(10)));

        ImageView icon = new ImageView(this);
        icon.setImageDrawable(tintedIcon(R.drawable.ic_share, Color.rgb(22, 163, 74)));
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(34), dp(34));
        iconParams.setMargins(0, 0, dp(12), 0);
        row.addView(icon, iconParams);

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        row.addView(copy, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView title = text(peer.name, 15, Color.rgb(241, 248, 246), true);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        copy.addView(title);

        TextView details = text(peer.host, 13, Color.rgb(148, 163, 184), false);
        details.setSingleLine(true);
        details.setEllipsize(TextUtils.TruncateAt.END);
        copy.addView(details);

        Button send = button("Send");
        styleButton(send, Color.rgb(20, 83, 45), Color.rgb(187, 247, 208), Color.rgb(34, 197, 94), 14);
        setButtonIcon(send, R.drawable.ic_share, Color.rgb(187, 247, 208));
        send.setEnabled(!nearbySessionFiles.isEmpty() && !nearbySending);
        send.setTooltipText("Send chosen nearby files over local Wi-Fi");
        send.setOnClickListener(v -> sendSharedFilesToHost(peer.host));
        LinearLayout.LayoutParams sendParams = new LinearLayout.LayoutParams(dp(84), dp(38));
        sendParams.setMargins(dp(12), 0, 0, 0);
        row.addView(send, sendParams);
        return row;
    }

    private void startLanFallbackDiscovery() {
        if (lanDiscoveryRunning) {
            Log.i(TAG, "LAN fallback discovery already running.");
            return;
        }
        Log.i(TAG, "Starting LAN fallback discovery on UDP " + LAN_DISCOVERY_PORT + " and TCP " + NEARBY_TRANSFER_PORT + ".");
        lanDiscoveryRunning = true;
        acquireMulticastLock();
        startNearbyReceiveServer();
        nearbyDiscoveryExecutor.execute(this::listenForLanPeers);
        nearbyDiscoveryExecutor.execute(this::broadcastLanPresence);
        renderNearbyTransferState("Local Wi-Fi ready.");
    }

    private void stopLanFallbackDiscovery() {
        lanDiscoveryRunning = false;
        releaseMulticastLock();
        lanPeers.clear();
        renderNearbyDevices();
    }

    private void acquireMulticastLock() {
        try {
            WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
            if (wifiManager == null) {
                return;
            }
            if (multicastLock == null) {
                multicastLock = wifiManager.createMulticastLock("flux-fileshare-lan-discovery");
                multicastLock.setReferenceCounted(false);
            }
            if (!multicastLock.isHeld()) {
                multicastLock.acquire();
            }
        } catch (Exception ignored) {
        }
    }

    private void releaseMulticastLock() {
        try {
            if (multicastLock != null && multicastLock.isHeld()) {
                multicastLock.release();
            }
        } catch (Exception ignored) {
        }
    }

    private void listenForLanPeers() {
        try (DatagramSocket socket = new DatagramSocket(LAN_DISCOVERY_PORT)) {
            socket.setBroadcast(true);
            socket.setSoTimeout(1800);
            byte[] buffer = new byte[512];
            while (lanDiscoveryRunning) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                try {
                    socket.receive(packet);
                } catch (SocketTimeoutException ignored) {
                    continue;
                }
                String message = new String(packet.getData(), 0, packet.getLength(), java.nio.charset.StandardCharsets.UTF_8);
                if (!message.startsWith(LAN_DISCOVERY_MAGIC + "|")) {
                    continue;
                }
                String[] parts = message.split("\\|", 3);
                String name = parts.length >= 2 && !TextUtils.isEmpty(parts[1]) ? parts[1] : "K-Share nearby phone";
                String host = packet.getAddress().getHostAddress();
                if (isLocalHost(host)) {
                    continue;
                }
                mainHandler.post(() -> {
                    addOrUpdateLanPeer(new LanPeer(name, host));
                    renderNearbyDevices();
                    renderNearbyState("Found: " + name);
                });
            }
        } catch (Exception e) {
            if (lanDiscoveryRunning) {
                Log.w(TAG, "LAN fallback listener failed.", e);
                mainHandler.post(() -> renderNearbyTransferState("Local Wi-Fi failed."));
            }
        }
    }

    private void broadcastLanPresence() {
        String name = Build.MANUFACTURER + " " + Build.MODEL;
        byte[] data = (LAN_DISCOVERY_MAGIC + "|" + name + "|" + NEARBY_TRANSFER_PORT).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        while (lanDiscoveryRunning) {
            try (DatagramSocket socket = new DatagramSocket()) {
                socket.setBroadcast(true);
                for (InetAddress address : lanBroadcastAddresses()) {
                    DatagramPacket packet = new DatagramPacket(data, data.length, address, LAN_DISCOVERY_PORT);
                    socket.send(packet);
                }
            } catch (Exception e) {
                Log.w(TAG, "LAN fallback broadcast failed.", e);
            }
            try {
                Thread.sleep(1500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private List<InetAddress> lanBroadcastAddresses() throws java.net.UnknownHostException {
        List<InetAddress> addresses = new ArrayList<>();
        addresses.add(InetAddress.getByName("255.255.255.255"));
        try {
            WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
            if (wifiManager != null) {
                DhcpInfo dhcp = wifiManager.getDhcpInfo();
                if (dhcp != null && dhcp.ipAddress != 0 && dhcp.netmask != 0) {
                    int broadcast = (dhcp.ipAddress & dhcp.netmask) | ~dhcp.netmask;
                    addresses.add(intToInetAddress(broadcast));
                }
            }
        } catch (Exception ignored) {
        }
        return addresses;
    }

    private InetAddress intToInetAddress(int hostAddress) throws java.net.UnknownHostException {
        byte[] addressBytes = new byte[]{
                (byte) (hostAddress & 0xff),
                (byte) ((hostAddress >> 8) & 0xff),
                (byte) ((hostAddress >> 16) & 0xff),
                (byte) ((hostAddress >> 24) & 0xff)
        };
        return InetAddress.getByAddress(addressBytes);
    }

    private void addOrUpdateLanPeer(LanPeer peer) {
        for (int i = 0; i < lanPeers.size(); i++) {
            if (lanPeers.get(i).host.equals(peer.host)) {
                lanPeers.set(i, peer);
                return;
            }
        }
        lanPeers.add(peer);
    }

    private boolean isLocalHost(String host) {
        try {
            for (NetworkInterface networkInterface : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                for (java.net.InetAddress address : Collections.list(networkInterface.getInetAddresses())) {
                    if (host.equals(address.getHostAddress())) {
                        return true;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private String wifiP2pStatus(int status) {
        switch (status) {
            case WifiP2pDevice.AVAILABLE:
                return "Available";
            case WifiP2pDevice.INVITED:
                return "Invited";
            case WifiP2pDevice.CONNECTED:
                return "Connected";
            case WifiP2pDevice.FAILED:
                return "Failed";
            case WifiP2pDevice.UNAVAILABLE:
                return "Unavailable";
            default:
                return "Unknown status";
        }
    }

    private String wifiP2pFailure(int reason) {
        switch (reason) {
            case WifiP2pManager.P2P_UNSUPPORTED:
                return "Wi-Fi Direct is unsupported";
            case WifiP2pManager.BUSY:
                return "Wi-Fi Direct is busy";
            case WifiP2pManager.ERROR:
            default:
                return "Wi-Fi Direct system error. Emulators often cannot discover nearby devices.";
        }
    }

    private void startNearbyReceiveServer() {
        if (nearbyReceiveRunning) {
            Log.i(TAG, "Nearby receive server already running.");
            return;
        }
        nearbyReceiveRunning = true;
        nearbyTransferExecutor.execute(() -> {
            try (ServerSocket server = new ServerSocket(NEARBY_TRANSFER_PORT)) {
                Log.i(TAG, "Nearby receive server listening on TCP " + NEARBY_TRANSFER_PORT + ".");
                nearbyServerSocket = server;
                while (nearbyReceiveRunning) {
                    Socket socket = server.accept();
                    nearbyTransferExecutor.execute(() -> handleNearbyIncomingSocket(socket));
                }
            } catch (Exception e) {
                if (nearbyReceiveRunning) {
                    Log.w(TAG, "Nearby receive server stopped unexpectedly.", e);
                    runOnUiThread(() -> renderNearbyTransferState("Receive stopped."));
                }
            } finally {
                Log.i(TAG, "Nearby receive server closed.");
                nearbyReceiveRunning = false;
                nearbyServerSocket = null;
            }
        });
    }

    private void handleNearbyIncomingSocket(Socket socket) {
        String peerHost = socket.getInetAddress().getHostAddress();
        nearbyIncomingSocket = socket;
        nearbyIncomingSockets.add(socket);
        Log.i(TAG, "Incoming nearby transfer from " + peerHost + ".");
        try (Socket ignored = socket) {
            int receivedMessage = receiveNearbyFiles(socket);
            if (receivedMessage == NEARBY_MESSAGE_TRANSFER) {
                runOnUiThread(() -> finishIncomingNearbyConnection("Done."));
            } else if (receivedMessage == NEARBY_MESSAGE_QUEUE) {
                runOnUiThread(() -> renderNearbyTransferState("Queued."));
            } else if (receivedMessage == NEARBY_MESSAGE_FILE_CANCEL) {
                runOnUiThread(() -> renderNearbyTransferState("Cancelled."));
            } else if (receivedMessage == NEARBY_MESSAGE_CANCEL) {
                runOnUiThread(() -> {
                    stopNearbyHeartbeat();
                    clearNearbyPartialFiles();
                    removeIncompleteReceivedItems();
                    Toast.makeText(this, "Sender cancelled.", Toast.LENGTH_LONG).show();
                    resetNearbyRoleSelection("Cancelled.");
                });
            } else {
                runOnUiThread(() -> renderNearbyTransferState("Connected."));
            }
        } catch (Exception transferError) {
            Log.w(TAG, "Incoming nearby transfer failed from " + peerHost + ".", transferError);
            runOnUiThread(() -> {
                if (nearbyNetworkLost) {
                    renderNearbyTransferState("Network lost.");
                    return;
                }
                if (senderClosedSessionMessage(transferError)) {
                    handleNearbyPeerLost();
                    return;
                }
                if (nearbySessionMismatchMessage(transferError)) {
                    renderNearbyTransferState(NEARBY_SESSION_ERROR);
                    Toast.makeText(this, NEARBY_SESSION_ERROR, Toast.LENGTH_LONG).show();
                    resetNearbyRoleSelection("Ready.");
                    return;
                }
                String message = "Receive failed: " + transferError.getMessage();
                renderNearbyTransferState("Receive failed.");
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                resetNearbyRoleSelection("Ready.");
            });
        } finally {
            nearbyIncomingSockets.remove(socket);
            if (nearbyIncomingSocket == socket) {
                nearbyIncomingSocket = null;
            }
        }
    }

    private void stopNearbyReceiveServer() {
        nearbyReceiveRunning = false;
        if (nearbyIncomingSocket != null) {
            try {
                nearbyIncomingSocket.close();
            } catch (Exception ignored) {
            }
            nearbyIncomingSocket = null;
        }
        synchronized (nearbyIncomingSockets) {
            for (Socket socket : nearbyIncomingSockets) {
                try {
                    socket.close();
                } catch (Exception ignored) {
                }
            }
            nearbyIncomingSockets.clear();
        }
        if (nearbyServerSocket != null) {
            try {
                nearbyServerSocket.close();
            } catch (Exception ignored) {
            }
            nearbyServerSocket = null;
        }
    }

    private void sendSharedFilesNearby() {
        List<SharedFile> files = new ArrayList<>(nearbySessionFiles);
        if (files.isEmpty()) {
            Toast.makeText(this, "Select files first.", Toast.LENGTH_SHORT).show();
            updateSendNearbyButton();
            return;
        }
        if (latestWifiP2pInfo == null || !latestWifiP2pInfo.groupFormed || latestWifiP2pInfo.groupOwnerAddress == null) {
            renderNearbyTransferState("Connect first.");
            return;
        }
        if (latestWifiP2pInfo.isGroupOwner) {
            renderNearbyTransferState("Send from client.");
            return;
        }
        sendSharedFilesToHost(latestWifiP2pInfo.groupOwnerAddress.getHostAddress());
    }

    private void openNearbyFilePicker() {
        if (nearbySending && connectedNearbyReceiver == null) {
            renderNearbyTransferState("Running.");
            return;
        }
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(Intent.createChooser(intent, "Select files to send"), REQUEST_NEARBY_FILES);
    }

    private void handleNearbyFileSelection(Intent data) {
        if (nearbySending && connectedNearbyReceiver == null) {
            renderNearbyTransferState("Running.");
            return;
        }
        List<Uri> uris = new ArrayList<>();
        if (data.getClipData() != null) {
            for (int i = 0; i < data.getClipData().getItemCount(); i++) {
                Uri uri = data.getClipData().getItemAt(i).getUri();
                if (uri != null) {
                    uris.add(uri);
                }
            }
        } else if (data.getData() != null) {
            uris.add(data.getData());
        }
        if (uris.isEmpty()) {
            renderNearbyTransferState("No files.");
            return;
        }
        for (Uri uri : uris) {
            takePersistableReadPermission(uri, data);
        }
        List<SharedFile> selectedFiles = repository.inspectFiles(uris);
        if (selectedFiles.isEmpty()) {
            renderNearbyTransferState("Unreadable.");
            return;
        }
        List<SharedFile> cancelledDuplicates = cancelledNearbySelectionDuplicates(selectedFiles);
        if (!cancelledDuplicates.isEmpty()) {
            selectedFiles.removeAll(cancelledDuplicates);
            showCancelledNearbyDuplicateAlert(cancelledDuplicates);
            if (selectedFiles.isEmpty()) {
                renderNearbyTransferState("Use Re-share.");
                return;
            }
        }

        if (connectedNearbyReceiver != null) {
            int addedCount = appendNearbySendFiles(selectedFiles);
            if (nearbySending) {
                announceQueuedFilesToReceiver(connectedNearbyReceiver, selectedFiles);
            }
            hideNearbyQrCode();
            updateNearbyQrStatus((nearbySending ? "Queued " : "Selected ") + addedCount + " file" + (addedCount == 1 ? "." : "s."));
            renderNearbyTransferState(nearbySending ? "Queued." : "Sending...");
            updateSendNearbyButton();
            renderNearbyTransferLists();
            drainNearbySendQueue();
            return;
        }

        nearbySessionFiles.clear();
        nearbySendingItems.clear();
        nearbyNextSendIndex = 0;
        appendNearbySendFiles(selectedFiles);
        updateNearbyQrStatus(nearbySessionFiles.size() + " selected.");
        renderNearbyTransferState("Selected.");
        updateSendNearbyButton();
        renderNearbyTransferLists();
        startOutgoingNearbyServer();
    }

    private int appendNearbySendFiles(List<SharedFile> selectedFiles) {
        int addedCount = 0;
        for (SharedFile file : selectedFiles) {
            long size = resolveSharedFileSize(file);
            nearbySessionFiles.add(file);
            nearbySendingItems.add(new NearbyTransferItem(
                    nearbyFileId(file, size),
                    file.name == null ? "nearby-file" : file.name,
                    size,
                    nearbySending ? "Queued" : "Pending"
            ));
            addedCount++;
        }
        return addedCount;
    }

    private List<SharedFile> cancelledNearbySelectionDuplicates(List<SharedFile> selectedFiles) {
        List<SharedFile> duplicates = new ArrayList<>();
        for (SharedFile file : selectedFiles) {
            if (findCancelledNearbySendingItem(file) != null) {
                duplicates.add(file);
            }
        }
        return duplicates;
    }

    private NearbyTransferItem findCancelledNearbySendingItem(SharedFile selectedFile) {
        long selectedSize = resolveSharedFileSize(selectedFile);
        String selectedFileId = nearbyFileId(selectedFile, selectedSize);
        String selectedUri = selectedFile.uri == null ? "" : selectedFile.uri.toString();
        for (int i = 0; i < nearbySendingItems.size() && i < nearbySessionFiles.size(); i++) {
            NearbyTransferItem item = nearbySendingItems.get(i);
            if (!"Cancelled".equals(item.status)) {
                continue;
            }
            SharedFile existingFile = nearbySessionFiles.get(i);
            String existingUri = existingFile.uri == null ? "" : existingFile.uri.toString();
            if (selectedUri.equals(existingUri) || selectedFileId.equals(item.fileId)) {
                return item;
            }
        }
        return null;
    }

    private void showCancelledNearbyDuplicateAlert(List<SharedFile> duplicateFiles) {
        String message = duplicateFiles.size() == 1
                ? "This file is already in the sharing list but got cancelled. Tap Re-share on the existing row to share it again."
                : duplicateFiles.size() + " files are already in the sharing list but got cancelled. Tap Re-share on those existing rows to share them again.";
        new AlertDialog.Builder(this)
                .setTitle("Use Re-share")
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }

    private void takePersistableReadPermission(Uri uri, Intent data) {
        if (uri == null || data == null || (data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION) == 0) {
            return;
        }
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) {
            // Some document providers grant temporary access only.
        }
    }

    private void startOutgoingNearbyServer() {
        cancelOutgoingNearbySession(false);
        if (nearbySessionFiles.isEmpty()) {
            updateNearbyQrStatus("Select files first.");
            return;
        }
        String ip = localIp();
        if (TextUtils.isEmpty(ip)) {
            updateNearbyQrStatus("Wi-Fi needed.");
            return;
        }
        outgoingNearbyServerRunning = true;
        outgoingReceiverConnected = false;
        startNearbyTransferKeepAlive("Waiting...");
        String payload = nearbyQrPayload(ip, NEARBY_QR_SEND_PORT);
        showNearbyQr(payload, nearbySessionFiles.size());
        nearbyTransferExecutor.execute(() -> {
            try (ServerSocket server = new ServerSocket(NEARBY_QR_SEND_PORT)) {
                outgoingNearbyServerSocket = server;
                Log.i(TAG, "Outgoing nearby QR sender listening on " + ip + ":" + NEARBY_QR_SEND_PORT + ".");
                while (outgoingNearbyServerRunning) {
                    try (Socket socket = server.accept()) {
                        outgoingNearbySockets.add(socket);
                        outgoingReceiverConnected = true;
                        List<SharedFile> files = new ArrayList<>(nearbySessionFiles);
                        String peerHost = socket.getInetAddress().getHostAddress();
                        Log.i(TAG, "QR receiver connected from " + peerHost + ".");
                        runOnUiThread(() -> {
                            showOutgoingNearbyConnection(peerHost);
                            hideNearbyQrCode();
                            updateNearbyQrStatus("Copying...");
                            renderNearbyTransferState("Copying...");
                        });
                        try {
                            sendNearbyFiles(socket, files, nearbySendingItems, outgoingNearbySessionToken);
                            stopOutgoingNearbyServer();
                            runOnUiThread(() -> {
                                if (nearbyCancelSharingButton != null) {
                                    nearbyCancelSharingButton.setVisibility(View.GONE);
                                }
                                updateNearbyQrStatus("Done.");
                                renderNearbyTransferState("Sent.");
                                if (nearbyRoleButtons != null) {
                                    nearbyRoleButtons.setVisibility(View.VISIBLE);
                                }
                                showDefaultNearbyQrIfIdle();
                            });
                        } finally {
                            outgoingNearbySockets.remove(socket);
                        }
                    }
                }
            } catch (Exception e) {
                if (outgoingNearbyServerRunning) {
                    Log.w(TAG, "Outgoing nearby QR sender stopped unexpectedly.", e);
                    runOnUiThread(() -> updateNearbyQrStatus("Sender stopped."));
                }
            } finally {
                outgoingNearbyServerRunning = false;
                outgoingNearbyServerSocket = null;
            }
        });
    }

    private void stopOutgoingNearbyServer() {
        outgoingNearbyServerRunning = false;
        if (outgoingNearbyServerSocket != null) {
            try {
                outgoingNearbyServerSocket.close();
            } catch (Exception ignored) {
            }
            outgoingNearbyServerSocket = null;
        }
    }

    private void startNearbyControlServer() {
        if (nearbyControlRunning) {
            return;
        }
        nearbyControlRunning = true;
        nearbyTransferExecutor.execute(() -> {
            try (ServerSocket server = new ServerSocket(NEARBY_CONTROL_PORT)) {
                nearbyControlServerSocket = server;
                Log.i(TAG, "Nearby control server listening on TCP " + NEARBY_CONTROL_PORT + ".");
                while (nearbyControlRunning) {
                    try (Socket socket = server.accept()) {
                        DataInputStream input = new DataInputStream(socket.getInputStream());
                        String event = input.readUTF();
                        String peerName = input.readUTF();
                        String message = input.readUTF();
                        if (NEARBY_CONNECTED_EVENT.equals(event) && NEARBY_PING_MAGIC.equals(message)) {
                            DataOutputStream output = new DataOutputStream(socket.getOutputStream());
                            output.writeUTF(NEARBY_PONG_MAGIC);
                            output.flush();
                        } else if (NEARBY_CONNECTED_EVENT.equals(event) && NEARBY_CANCEL_MAGIC.equals(message)) {
                            runOnUiThread(() -> handleRemoteNearbyCancellation(peerName));
                        } else if (NEARBY_CONNECTED_EVENT.equals(event) && NEARBY_FILE_CANCEL_MAGIC.equals(message)) {
                            String fileId = input.readUTF();
                            runOnUiThread(() -> handleRemoteNearbyFileCancellation(fileId));
                        }
                    }
                }
            } catch (Exception e) {
                if (nearbyControlRunning) {
                    Log.w(TAG, "Nearby control server stopped unexpectedly.", e);
                }
            } finally {
                nearbyControlRunning = false;
                nearbyControlServerSocket = null;
            }
        });
    }

    private void stopNearbyControlServer() {
        nearbyControlRunning = false;
        if (nearbyControlServerSocket != null) {
            try {
                nearbyControlServerSocket.close();
            } catch (Exception ignored) {
            }
            nearbyControlServerSocket = null;
        }
    }

    private void startNearbyHeartbeat(String host, int port) {
        int generation = ++nearbyHeartbeatGeneration;
        nearbyTransferExecutor.execute(() -> {
            int failures = 0;
            while (generation == nearbyHeartbeatGeneration) {
                try (Socket socket = new Socket()) {
                    socket.connect(new InetSocketAddress(host, port), 2000);
                    socket.setSoTimeout(2000);
                    DataOutputStream output = new DataOutputStream(socket.getOutputStream());
                    output.writeUTF(NEARBY_CONNECTED_EVENT);
                    output.writeUTF(nearbyDeviceName());
                    output.writeUTF(NEARBY_PING_MAGIC);
                    output.flush();
                    DataInputStream input = new DataInputStream(socket.getInputStream());
                    if (!NEARBY_PONG_MAGIC.equals(input.readUTF())) {
                        throw new java.io.IOException("Invalid heartbeat response");
                    }
                    failures = 0;
                } catch (Exception e) {
                    failures++;
                    if (failures >= 3 && generation == nearbyHeartbeatGeneration) {
                        nearbyHeartbeatGeneration++;
                        runOnUiThread(this::handleNearbyPeerLost);
                        return;
                    }
                }
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        });
    }

    private void stopNearbyHeartbeat() {
        nearbyHeartbeatGeneration++;
    }

    private void handleNearbyPeerLost() {
        closeNearbyConnectionState(
                true,
                "Sender disconnected."
        );
        showDefaultNearbyQrIfIdle();
        Toast.makeText(this, "Sender disconnected.", Toast.LENGTH_LONG).show();
    }

    private void closeNearbyConnectionState(boolean keepReceiverMode, String message) {
        clearNearbyTransferPause();
        stopNearbyTransferKeepAlive();
        stopNearbyHeartbeat();
        stopNearbyControlServer();
        stopOutgoingNearbyServer();
        if (activeNearbySendSocket != null) {
            try {
                activeNearbySendSocket.close();
            } catch (Exception ignored) {
            }
            activeNearbySendSocket = null;
        }
        if (nearbyIncomingSocket != null) {
            try {
                nearbyIncomingSocket.close();
            } catch (Exception ignored) {
            }
            nearbyIncomingSocket = null;
        }
        synchronized (nearbyIncomingSockets) {
            for (Socket socket : nearbyIncomingSockets) {
                try {
                    socket.close();
                } catch (Exception ignored) {
                }
            }
            nearbyIncomingSockets.clear();
        }
        synchronized (outgoingNearbySockets) {
            for (Socket socket : outgoingNearbySockets) {
                try {
                    socket.close();
                } catch (Exception ignored) {
                }
            }
            outgoingNearbySockets.clear();
        }
        connectedNearbyReceiver = null;
        incomingNearbyConnected = false;
        outgoingReceiverConnected = false;
        nearbyAwaitingReconnect = false;
        nearbyRemoteControlHost = "";
        nearbyRemoteControlPort = 0;
        expectedNearbySenderSessionToken = "";
        nearbySending = false;
        if (!nearbyReceiverMode && nearbySendingTransferStarted) {
            markUnfinishedSendingItemsInterrupted();
        }
        nearbyReceiverMode = keepReceiverMode;
        if (chooseNearbyFilesButton != null) {
            chooseNearbyFilesButton.setEnabled(true);
            chooseNearbyFilesButton.setText("Select files");
            setButtonIcon(chooseNearbyFilesButton, R.drawable.ic_folder, Color.rgb(4, 14, 16));
            chooseNearbyFilesButton.setVisibility(View.GONE);
        }
        if (nearbyCancelSharingButton != null) {
            nearbyCancelSharingButton.setVisibility(View.GONE);
        }
        if (keepReceiverMode) {
            if (nearbyRoleButtons != null) {
                nearbyRoleButtons.setVisibility(View.GONE);
            }
            if (nearbyQrCard != null) {
                nearbyQrCard.setVisibility(View.VISIBLE);
            }
            hideNearbyQrCode();
        } else {
            showNearbyRoleSelection();
        }
        updateNearbyQrStatus(message);
        renderNearbyTransferState(message);
        if (nearbyTransferView != null) {
            nearbyTransferView.setVisibility(View.VISIBLE);
        }
        updateNearbyTransferCardsVisibility();
    }

    private void handleRemoteNearbyCancellation(String peerName) {
        clearNearbyTransferPause();
        stopNearbyTransferKeepAlive();
        if (activeNearbySendSocket != null) {
            try {
                activeNearbySendSocket.close();
            } catch (Exception ignored) {
            }
            activeNearbySendSocket = null;
        }
        nearbySending = false;
        outgoingNearbyServerRunning = false;
        stopOutgoingNearbyServer();
        outgoingReceiverConnected = false;
        connectedNearbyReceiver = null;
        nearbySendingTransferStarted = false;
        markUnfinishedSendingItemsCancelled();
        showNearbyRoleSelection();
        updateNearbyTransferCardsVisibility();
        String peer = TextUtils.isEmpty(peerName) ? "The receiver" : peerName;
        updateNearbyQrStatus("Cancelled.");
        renderNearbyTransferState("Cancelled.");
        Toast.makeText(this, "Receiver cancelled.", Toast.LENGTH_LONG).show();
    }

    private void handleRemoteNearbyFileCancellation(String fileId) {
        NearbyTransferItem item = findSendingTransferItem(fileId);
        if (item == null) {
            Log.w(TAG, "Receiver asked to cancel unknown nearby file " + fileId + ".");
            return;
        }
        item.cancelRequested = true;
        updateNearbyTransferItem(item, "Cancelled", item.transferredBytes, item.totalBytes, "");
        renderNearbyTransferState("Receiver cancelled.");
    }

    private void handleReceiverFileCancellation(String fileId) {
        NearbyTransferItem item = findReceivedTransferItemByRemoteFileId(fileId);
        if (item == null) {
            Log.w(TAG, "Sender cancelled unknown nearby file " + fileId + ".");
            return;
        }
        item.cancelRequested = true;
        updateNearbyTransferItem(item, "Cancelled", item.transferredBytes, item.totalBytes, "");
        renderNearbyTransferState("Sender cancelled.");
    }

    private void cancelOutgoingNearbySession(boolean userInitiated) {
        clearNearbyTransferPause();
        stopNearbyTransferKeepAlive();
        NearbyEndpoint receiverToNotify = connectedNearbyReceiver;
        outgoingNearbyServerRunning = false;
        stopOutgoingNearbyServer();
        if (activeNearbySendSocket != null) {
            try {
                activeNearbySendSocket.close();
            } catch (Exception ignored) {
            }
            activeNearbySendSocket = null;
        }
        synchronized (outgoingNearbySockets) {
            for (Socket socket : outgoingNearbySockets) {
                try {
                    socket.close();
                } catch (Exception ignored) {
                }
            }
            outgoingNearbySockets.clear();
        }
        outgoingReceiverConnected = false;
        connectedNearbyReceiver = null;
        if (userInitiated) {
            nearbySendingTransferStarted = false;
        }
        if (userInitiated && receiverToNotify != null) {
            notifyReceiverSessionEvent(receiverToNotify.host, receiverToNotify.port, receiverToNotify.sessionToken, NEARBY_CANCEL_MAGIC);
        }
        stopNearbyControlServer();
        if (chooseNearbyFilesButton != null) {
            chooseNearbyFilesButton.setVisibility(View.GONE);
        }
        if (userInitiated) {
            resetNearbySessionTokens();
            showNearbyRoleSelection();
            markUnfinishedSendingItemsCancelled();
            updateNearbyQrStatus("Cancelled.");
            renderNearbyTransferState("Cancelled.");
            if (nearbyCancelSharingButton != null) {
                nearbyCancelSharingButton.setVisibility(View.GONE);
            }
            showDefaultNearbyQrIfIdle();
        }
    }

    private void markUnfinishedSendingItemsCancelled() {
        for (NearbyTransferItem item : nearbySendingItems) {
            if (!"Completed".equals(item.status)) {
                item.status = "Cancelled";
            }
        }
        renderNearbyTransferLists();
    }

    private void markUnfinishedSendingItemsInterrupted() {
        for (NearbyTransferItem item : nearbySendingItems) {
            if (!"Completed".equals(item.status)) {
                item.status = "Interrupted";
            }
        }
        renderNearbyTransferLists();
    }

    private String nearbyQrPayload(String host, int port) {
        outgoingNearbySessionToken = newNearbySessionToken();
        return "fluxnearby://receive?host=" + host + "&port=" + port
                + "&" + NEARBY_SESSION_QUERY_KEY + "=" + outgoingNearbySessionToken;
    }

    private String nearbyReceiverQrPayload(String host, int port) {
        String token = ensureNearbyReceiverSessionToken();
        return "fluxnearby://receiver?host=" + host + "&port=" + port
                + "&" + NEARBY_SESSION_QUERY_KEY + "=" + token;
    }

    private String newNearbySessionToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private String ensureNearbyReceiverSessionToken() {
        if (TextUtils.isEmpty(nearbyReceiverSessionToken)) {
            nearbyReceiverSessionToken = newNearbySessionToken();
        }
        return nearbyReceiverSessionToken;
    }

    private void resetNearbySessionTokens() {
        nearbyReceiverSessionToken = "";
        outgoingNearbySessionToken = "";
        expectedNearbySenderSessionToken = "";
    }

    private String nearbyConnectedEvent(String sessionToken) {
        return TextUtils.isEmpty(sessionToken)
                ? NEARBY_CONNECTED_EVENT
                : NEARBY_CONNECTED_EVENT + "|" + sessionToken;
    }

    private String parseNearbyConnectedSession(String connectionEvent) throws java.io.IOException {
        if (NEARBY_CONNECTED_EVENT.equals(connectionEvent)) {
            return "";
        }
        String prefix = NEARBY_CONNECTED_EVENT + "|";
        if (connectionEvent != null && connectionEvent.startsWith(prefix)) {
            return connectionEvent.substring(prefix.length());
        }
        throw new java.io.IOException("Missing nearby connection event");
    }

    private boolean nearbySessionMatches(String expectedToken, String actualToken) {
        return !TextUtils.isEmpty(expectedToken) && TextUtils.equals(expectedToken, actualToken);
    }

    private boolean activeReceiverSessionAccepts(String magic) {
        if (NEARBY_CANCEL_MAGIC.equals(magic) || NEARBY_FILE_CANCEL_MAGIC.equals(magic)) {
            return true;
        }
        return nearbyReceiverMode || incomingNearbyConnected;
    }

    private void writeNearbyPresenceResponse(DataOutputStream output, String response) {
        try {
            output.writeUTF(response);
            output.flush();
        } catch (Exception ignored) {
        }
    }

    private void showDefaultNearbyQrIfIdle() {
        if (nearbyQrImageView == null || nearbyQrCard == null || nearbyQrCard.getVisibility() != View.VISIBLE
                || !nearbyReceiverMode || outgoingNearbyServerRunning || outgoingReceiverConnected
                || incomingNearbyConnected || connectedNearbyReceiver != null) {
            return;
        }
        if (chooseNearbyFilesButton != null) {
            chooseNearbyFilesButton.setVisibility(View.GONE);
        }
        if (scanNearbySenderQrButton != null) {
            scanNearbySenderQrButton.setVisibility(View.GONE);
        }
        String ip = localIp();
        if (TextUtils.isEmpty(ip)) {
            hideNearbyQrCode();
            updateNearbyQrStatus("Wi-Fi needed.");
            return;
        }
        showNearbyQrBitmap(nearbyReceiverQrPayload(ip, NEARBY_TRANSFER_PORT));
        if (nearbyCancelSharingButton != null) {
            nearbyCancelSharingButton.setVisibility(View.GONE);
        }
        updateNearbyQrStatus("QR ready.");
    }

    private void showNearbyQr(String payload, int fileCount) {
        if (showNearbyQrBitmap(payload)) {
            if (scanNearbySenderQrButton != null) {
                scanNearbySenderQrButton.setVisibility(View.GONE);
            }
            if (nearbyCancelSharingButton != null) {
                nearbyCancelSharingButton.setVisibility(View.VISIBLE);
            }
            updateNearbyQrStatus(fileCount + " ready.");
        }
    }

    private boolean showNearbyQrBitmap(String payload) {
        try {
            int qrSize = responsiveQrBitmapSize();
            Bitmap bitmap = new BarcodeEncoder().encodeBitmap(payload, BarcodeFormat.QR_CODE, qrSize, qrSize);
            if (nearbyQrImageView != null) {
                nearbyQrImageView.setImageBitmap(bitmap);
                nearbyQrImageView.setVisibility(View.VISIBLE);
            }
            return true;
        } catch (WriterException e) {
            updateNearbyQrStatus("QR failed.");
            return false;
        }
    }

    private void hideNearbyQrCode() {
        if (nearbyQrImageView != null) {
            nearbyQrImageView.setImageBitmap(null);
            nearbyQrImageView.setVisibility(View.GONE);
        }
    }

    private int responsiveQrCardSize() {
        int width = getResources().getDisplayMetrics().widthPixels;
        return Math.max(rawDp(250), Math.min(width - dp(80), rawDp(360)));
    }

    private int responsiveQrBitmapSize() {
        return Math.max(rawDp(220), responsiveQrCardSize() - dp(24));
    }

    private int webQrImageCardSize() {
        return isLandscape() ? dp(104) : dp(96);
    }

    private int webQrBitmapSize() {
        return Math.max(rawDp(180), webQrImageCardSize() - dp(16));
    }

    private void showIncomingNearbyConnection(String peerHost) {
        incomingNearbyConnected = true;
        startNearbyTransferKeepAlive("Receiving nearby files...", false, true);
        if (nearbyRoleButtons != null) {
            nearbyRoleButtons.setVisibility(View.GONE);
        }
        if (nearbyTransferView != null) {
            nearbyTransferView.setVisibility(View.VISIBLE);
        }
        updateNearbyTransferCardsVisibility();
        hideNearbyQrCode();
        if (chooseNearbyFilesButton != null) {
            chooseNearbyFilesButton.setVisibility(View.GONE);
        }
        if (scanNearbySenderQrButton != null) {
            scanNearbySenderQrButton.setVisibility(View.GONE);
        }
        if (nearbyCancelSharingButton != null) {
            nearbyCancelSharingButton.setVisibility(View.VISIBLE);
        }
        String peer = TextUtils.isEmpty(peerHost) ? "sender" : peerHost;
        updateNearbyQrStatus("Connected.");
        renderNearbyTransferState("Receiving...");
    }

    private void showOutgoingNearbyConnection(String peerHost) {
        outgoingReceiverConnected = true;
        startNearbyTransferKeepAlive("Sending nearby files...", false, true);
        if (nearbyRoleButtons != null) {
            nearbyRoleButtons.setVisibility(View.GONE);
        }
        if (nearbyTransferView != null) {
            nearbyTransferView.setVisibility(View.VISIBLE);
        }
        updateNearbyTransferCardsVisibility();
        if (scanNearbySenderQrButton != null) {
            scanNearbySenderQrButton.setVisibility(View.GONE);
        }
        if (nearbyCancelSharingButton != null) {
            nearbyCancelSharingButton.setVisibility(View.VISIBLE);
        }
        String peer = TextUtils.isEmpty(peerHost) ? "receiver" : peerHost;
        updateNearbyQrStatus("Connected.");
        renderNearbyTransferState("Sending...");
    }

    private void finishIncomingNearbyConnection(String message) {
        incomingNearbyConnected = false;
        updateNearbyQrStatus(message);
        renderNearbyTransferState(message);
    }

    private void resetNearbyRoleSelection(String message) {
        stopNearbyTransferKeepAlive();
        incomingNearbyConnected = false;
        nearbyReceivingTransferStarted = false;
        nearbyRemoteControlHost = "";
        nearbyRemoteControlPort = 0;
        showNearbyRoleSelection();
        updateNearbyQrStatus(message);
        renderNearbyTransferState(message);
        updateNearbyTransferCardsVisibility();
    }

    private void cancelIncomingNearbySession() {
        clearNearbyTransferPause();
        stopNearbyTransferKeepAlive();
        stopNearbyHeartbeat();
        if (!TextUtils.isEmpty(nearbyRemoteControlHost) && nearbyRemoteControlPort > 0) {
            notifyReceiverSessionEvent(nearbyRemoteControlHost, nearbyRemoteControlPort, "", NEARBY_CANCEL_MAGIC);
        }
        incomingNearbyConnected = false;
        nearbyReceivingTransferStarted = false;
        nearbyRemoteControlHost = "";
        nearbyRemoteControlPort = 0;
        clearNearbyPartialFiles();
        removeIncompleteReceivedItems();
        showNearbyRoleSelection();
        if (nearbyIncomingSocket != null) {
            try {
                nearbyIncomingSocket.close();
            } catch (Exception ignored) {
            }
            nearbyIncomingSocket = null;
        }
        synchronized (nearbyIncomingSockets) {
            for (Socket socket : nearbyIncomingSockets) {
                try {
                    socket.close();
                } catch (Exception ignored) {
                }
            }
            nearbyIncomingSockets.clear();
        }
        updateNearbyQrStatus("Cancelled.");
        renderNearbyTransferState("Cancelled.");
        updateNearbyTransferCardsVisibility();
    }

    private void showNearbyRoleSelection() {
        resetNearbySessionTokens();
        nearbyAwaitingReconnect = false;
        if (nearbyRoleButtons != null) {
            nearbyRoleButtons.setVisibility(View.VISIBLE);
        }
        if (nearbyQrCard != null) {
            nearbyQrCard.setVisibility(View.GONE);
        }
        if (chooseNearbyFilesButton != null) {
            chooseNearbyFilesButton.setText("Select files");
            chooseNearbyFilesButton.setVisibility(View.GONE);
        }
        if (scanNearbySenderQrButton != null) {
            scanNearbySenderQrButton.setVisibility(View.GONE);
        }
        if (nearbyCancelSharingButton != null) {
            nearbyCancelSharingButton.setVisibility(View.GONE);
        }
        if (nearbyTransferView != null) {
            nearbyTransferView.setVisibility(View.GONE);
        }
        hideNearbyQrCode();
    }

    private void updateNearbyQrStatus(String message) {
        if (nearbyQrStatusView != null) {
            nearbyQrStatusView.setText(message);
        }
    }

    private void scanNearbyQrCode(int scanMode) {
        pendingNearbyScanMode = scanMode;
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA);
            return;
        }
        launchNearbyQrScanner(scanMode);
    }

    private void launchNearbyQrScanner(int scanMode) {
        try {
            Intent intent = new Intent(this, QrScannerActivity.class);
            if (scanMode == NEARBY_SCAN_DESKTOP_QR) {
                intent.putExtra(QrScannerActivity.EXTRA_SCAN_TITLE, "Scan Desktop QR");
                intent.putExtra(QrScannerActivity.EXTRA_SCAN_HINT, "Scan the QR shown in K-Share desktop");
                intent.putExtra(QrScannerActivity.EXTRA_SCAN_STATUS, "Point camera at the desktop QR");
            } else {
                intent.putExtra(QrScannerActivity.EXTRA_SCAN_TITLE, "Scan Receiver QR");
                intent.putExtra(QrScannerActivity.EXTRA_SCAN_HINT, "Scan the receiver's QR code");
                intent.putExtra(QrScannerActivity.EXTRA_SCAN_STATUS, "Point camera at the receiver QR");
            }
            startActivityForResult(intent, REQUEST_QR_SCAN);
        } catch (Exception e) {
            renderNearbyTransferState("Scanner failed.");
        }
    }

    private void handleNearbyQrResult(String contents, int scanMode) {
        boolean desktopScan = scanMode == NEARBY_SCAN_DESKTOP_QR;
        if (TextUtils.isEmpty(contents)) {
            renderNearbyTransferState(desktopScan ? "Desktop scan cancelled." : "Scan cancelled.");
            return;
        }
        NearbyEndpoint endpoint = parseNearbyEndpoint(contents);
        if (endpoint == null) {
            renderNearbyTransferState(desktopScan ? "Invalid desktop QR." : "Invalid QR.");
            return;
        }
        if (endpoint.receiver) {
            connectedNearbyReceiver = endpoint;
            nearbyReceiverMode = false;
            nearbyAwaitingReconnect = false;
            expectedNearbySenderSessionToken = "";
            if (nearbyRoleButtons != null) {
                nearbyRoleButtons.setVisibility(View.GONE);
            }
            if (nearbyQrCard != null) {
                nearbyQrCard.setVisibility(View.VISIBLE);
            }
            updateNearbyTransferCardsVisibility();
            hideNearbyQrCode();
            if (chooseNearbyFilesButton != null) {
                chooseNearbyFilesButton.setText("Select files");
                setButtonIcon(chooseNearbyFilesButton, R.drawable.ic_folder, Color.rgb(4, 14, 16));
                chooseNearbyFilesButton.setVisibility(View.GONE);
            }
            if (scanNearbySenderQrButton != null) {
                scanNearbySenderQrButton.setVisibility(View.GONE);
            }
            if (nearbyCancelSharingButton != null) {
                nearbyCancelSharingButton.setVisibility(View.VISIBLE);
            }
            updateNearbyQrStatus(desktopScan ? "Connecting desktop..." : "Verifying...");
            renderNearbyTransferState(desktopScan ? "Connecting desktop..." : "Verifying...");
            startNearbyControlServer();
            notifyReceiverPresence(endpoint);
            return;
        }
        nearbyReceiverMode = true;
        nearbyAwaitingReconnect = false;
        expectedNearbySenderSessionToken = endpoint.sessionToken;
        if (nearbyRoleButtons != null) {
            nearbyRoleButtons.setVisibility(View.GONE);
        }
        if (nearbyQrCard != null) {
            nearbyQrCard.setVisibility(View.VISIBLE);
        }
        hideNearbyQrCode();
        if (chooseNearbyFilesButton != null) {
            chooseNearbyFilesButton.setVisibility(View.GONE);
        }
        if (scanNearbySenderQrButton != null) {
            scanNearbySenderQrButton.setVisibility(View.GONE);
        }
        if (nearbyCancelSharingButton != null) {
            nearbyCancelSharingButton.setVisibility(View.GONE);
        }
        updateNearbyTransferCardsVisibility();
        updateNearbyQrStatus(desktopScan ? "Connecting desktop..." : "Connecting...");
        receiveFilesFromQrSender(endpoint.host, endpoint.port, endpoint.sessionToken);
    }

    private NearbyEndpoint parseNearbyEndpoint(String payload) {
        if (payload == null
                || (!payload.startsWith("fluxnearby://receive?") && !payload.startsWith("fluxnearby://receiver?"))) {
            return null;
        }
        boolean receiver = payload.startsWith("fluxnearby://receiver?");
        String query = payload.substring(payload.indexOf('?') + 1);
        String host = "";
        String sessionToken = "";
        int port = receiver ? NEARBY_TRANSFER_PORT : NEARBY_QR_SEND_PORT;
        for (String part : query.split("&")) {
            int equals = part.indexOf('=');
            if (equals <= 0) {
                continue;
            }
            String key = part.substring(0, equals);
            String value = part.substring(equals + 1);
            if ("host".equals(key)) {
                host = value;
            } else if ("port".equals(key)) {
                try {
                    port = Integer.parseInt(value);
                } catch (NumberFormatException ignored) {
                }
            } else if (NEARBY_SESSION_QUERY_KEY.equals(key) || "session".equals(key) || "sid".equals(key)) {
                sessionToken = value;
            }
        }
        if (TextUtils.isEmpty(host) || TextUtils.isEmpty(sessionToken)) {
            return null;
        }
        return new NearbyEndpoint(host, port, receiver, sessionToken);
    }

    private void receiveFilesFromQrSender(String host, int port, String expectedSessionToken) {
        renderNearbyTransferState("Connecting...");
        nearbyTransferExecutor.execute(() -> {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, port), 15000);
                int receivedMessage = receiveNearbyFiles(socket, expectedSessionToken, true);
                if (receivedMessage == NEARBY_MESSAGE_TRANSFER) {
                    runOnUiThread(() -> finishIncomingNearbyConnection("Done."));
                } else if (receivedMessage == NEARBY_MESSAGE_QUEUE) {
                    runOnUiThread(() -> renderNearbyTransferState("Queued."));
                } else if (receivedMessage == NEARBY_MESSAGE_FILE_CANCEL) {
                    runOnUiThread(() -> renderNearbyTransferState("Cancelled."));
                } else if (receivedMessage == NEARBY_MESSAGE_CANCEL) {
                    runOnUiThread(() -> {
                        stopNearbyHeartbeat();
                        clearNearbyPartialFiles();
                        removeIncompleteReceivedItems();
                        resetNearbyRoleSelection("Cancelled.");
                    });
                }
            } catch (Exception e) {
                Log.w(TAG, "QR receive failed from " + host + ":" + port + ".", e);
                runOnUiThread(() -> {
                    String message = nearbySessionMismatchMessage(e)
                            ? NEARBY_SESSION_ERROR
                            : senderClosedSessionMessage(e)
                            ? "Sender closed."
                            : "Receive failed.";
                    renderNearbyTransferState(message);
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                    resetNearbyRoleSelection("Ready.");
                });
            }
        });
    }

    private boolean senderClosedSessionMessage(Exception e) {
        if (e instanceof java.io.EOFException || e instanceof java.net.SocketException) {
            return true;
        }
        String message = e.getMessage();
        if (message == null) {
            return false;
        }
        String lower = message.toLowerCase(Locale.US);
        return lower.contains("transfer ended early")
                || lower.contains("connection reset")
                || lower.contains("socket closed")
                || lower.contains("broken pipe")
                || lower.contains("connection refused")
                || lower.contains("failed to connect");
    }

    private boolean nearbySessionMismatchMessage(Exception e) {
        String message = e == null ? null : e.getMessage();
        return message != null && message.toLowerCase(Locale.US).contains("different nearby session");
    }

    private String normalizeNearbyHost(String value) {
        if (value == null) {
            return "";
        }
        String host = value.trim();
        if (host.startsWith("http://")) {
            host = host.substring("http://".length());
        } else if (host.startsWith("https://")) {
            host = host.substring("https://".length());
        }
        int slash = host.indexOf('/');
        if (slash >= 0) {
            host = host.substring(0, slash);
        }
        if (host.startsWith("[") && host.contains("]")) {
            return host.substring(1, host.indexOf(']')).trim();
        }
        int colon = host.indexOf(':');
        if (colon > 0) {
            host = host.substring(0, colon);
        }
        return host.trim();
    }

    private void sendSharedFilesToHost(String host) {
        sendSharedFilesToHost(host, NEARBY_TRANSFER_PORT);
    }

    private void sendSharedFilesToHost(String host, int port) {
        List<SharedFile> files = new ArrayList<>(nearbySessionFiles);
        List<NearbyTransferItem> transferItems = new ArrayList<>(nearbySendingItems);
        sendNearbyBatchToHost(host, port, "", files, transferItems, false);
    }

    private void drainNearbySendQueue() {
        if (connectedNearbyReceiver == null) {
            updateSendNearbyButton();
            return;
        }
        if (nearbySending) {
            updateSendNearbyButton();
            return;
        }
        if (!nearbyReshareFiles.isEmpty()) {
            NearbyEndpoint receiver = connectedNearbyReceiver;
            List<SharedFile> files = new ArrayList<>(nearbyReshareFiles);
            List<NearbyTransferItem> transferItems = new ArrayList<>(nearbyReshareItems);
            nearbyReshareFiles.clear();
            nearbyReshareItems.clear();
            sendNearbyBatchToHost(receiver.host, receiver.port, receiver.sessionToken, files, transferItems, true);
            return;
        }
        if (nearbyNextSendIndex >= nearbySessionFiles.size()) {
            if (nearbySendingTransferStarted) {
                renderNearbyTransferState("Done.");
            }
            updateSendNearbyButton();
            return;
        }

        NearbyEndpoint receiver = connectedNearbyReceiver;
        List<SharedFile> files = new ArrayList<>(nearbySessionFiles.subList(nearbyNextSendIndex, nearbySessionFiles.size()));
        List<NearbyTransferItem> transferItems = new ArrayList<>(nearbySendingItems.subList(nearbyNextSendIndex, nearbySendingItems.size()));
        nearbyNextSendIndex = nearbySessionFiles.size();
        sendNearbyBatchToHost(receiver.host, receiver.port, receiver.sessionToken, files, transferItems, true);
    }

    private void sendNearbyBatchToHost(
            String host,
            int port,
            String sessionToken,
            List<SharedFile> files,
            List<NearbyTransferItem> transferItems,
            boolean continueQueue
    ) {
        if (files.isEmpty()) {
            Toast.makeText(this, "Select files first.", Toast.LENGTH_SHORT).show();
            updateSendNearbyButton();
            return;
        }
        startNearbyTransferKeepAlive("Sending...");
        nearbySending = true;
        nearbySendingTransferStarted = true;
        updateNearbyTransferCardsVisibility();
        if (chooseNearbyFilesButton != null) {
            chooseNearbyFilesButton.setEnabled(true);
            chooseNearbyFilesButton.setText("Add files");
            chooseNearbyFilesButton.setTooltipText("Add more files to this sharing session");
            chooseNearbyFilesButton.setVisibility(connectedNearbyReceiver != null ? View.VISIBLE : chooseNearbyFilesButton.getVisibility());
        }
        renderNearbyTransferState("Sending " + files.size() + ".");
        nearbyTransferExecutor.execute(() -> {
            boolean completed = false;
            try (Socket socket = new Socket()) {
                activeNearbySendSocket = socket;
                Log.i(TAG, "Sending " + files.size() + " nearby file(s) to " + host + ":" + port + ".");
                socket.bind(null);
                socket.connect(new InetSocketAddress(host, port), 15000);
                sendNearbyFiles(socket, files, transferItems, sessionToken);
                completed = true;
                Log.i(TAG, "Nearby send completed to " + host + ".");
                runOnUiThread(() -> renderNearbyTransferState("Sent " + files.size() + "."));
            } catch (Exception e) {
                Log.w(TAG, "Nearby send failed to " + host + ".", e);
                runOnUiThread(() -> {
                    if (connectedNearbyReceiver != null) {
                        prepareSenderForReconnect("Send interrupted: " + e.getMessage());
                    } else {
                        String message = "Send failed.";
                        renderNearbyTransferState(message);
                        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                    }
                });
            } finally {
                boolean completedBatch = completed;
                activeNearbySendSocket = null;
                runOnUiThread(() -> {
                    nearbySending = false;
                    if (chooseNearbyFilesButton != null) {
                        chooseNearbyFilesButton.setEnabled(true);
                        if (connectedNearbyReceiver != null) {
                            chooseNearbyFilesButton.setText("Add files");
                            chooseNearbyFilesButton.setVisibility(View.VISIBLE);
                        }
                    }
                    updateSendNearbyButton();
                    renderNearbyDevices();
                    if (continueQueue && completedBatch && connectedNearbyReceiver != null
                            && (!nearbyReshareFiles.isEmpty() || nearbyNextSendIndex < nearbySessionFiles.size())) {
                        drainNearbySendQueue();
                    } else if (continueQueue && completedBatch && connectedNearbyReceiver != null) {
                        renderNearbyTransferState("Done.");
                    }
                });
            }
        });
    }

    private void notifyReceiverPresence(NearbyEndpoint endpoint) {
        nearbyTransferExecutor.execute(() -> {
            try {
                sendReceiverSessionEvent(endpoint.host, endpoint.port, endpoint.sessionToken, NEARBY_PRESENCE_MAGIC);
                runOnUiThread(() -> {
                    connectedNearbyReceiver = endpoint;
                    startNearbyTransferKeepAlive("Connected.");
                    if (chooseNearbyFilesButton != null) {
                        chooseNearbyFilesButton.setText("Select files");
                        chooseNearbyFilesButton.setTooltipText("Select files to send to this receiver");
                        setButtonIcon(chooseNearbyFilesButton, R.drawable.ic_folder, Color.rgb(4, 14, 16));
                        chooseNearbyFilesButton.setVisibility(View.VISIBLE);
                    }
                    updateNearbyQrStatus("Connected.");
                    renderNearbyTransferState("Select files.");
                });
            } catch (Exception e) {
                Log.w(TAG, "Receiver presence verification failed.", e);
                runOnUiThread(() -> {
                    String message = nearbySessionMismatchMessage(e) ? NEARBY_SESSION_ERROR : "Receiver offline.";
                    closeNearbyConnectionState(
                            false,
                            message
                    );
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void announceQueuedFilesToReceiver(NearbyEndpoint endpoint, List<SharedFile> files) {
        if (endpoint == null || files == null || files.isEmpty()) {
            return;
        }
        nearbyTransferExecutor.execute(() -> {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(endpoint.host, endpoint.port), 5000);
                DataOutputStream output = new DataOutputStream(socket.getOutputStream());
                List<SharedFile> sendable = new ArrayList<>();
                List<Long> sizes = new ArrayList<>();
                for (SharedFile file : files) {
                    long size = resolveSharedFileSize(file);
                    if (size >= 0) {
                        sendable.add(file);
                        sizes.add(size);
                    }
                }
                output.writeUTF(nearbyConnectedEvent(endpoint.sessionToken));
                output.writeUTF(nearbyDeviceName());
                output.writeUTF(NEARBY_QUEUE_MAGIC);
                output.writeInt(sendable.size());
                for (int i = 0; i < sendable.size(); i++) {
                    SharedFile file = sendable.get(i);
                    long size = sizes.get(i);
                    String name = file.name == null ? "shared-file" : file.name;
                    output.writeUTF(nearbyFileId(file, size));
                    output.writeUTF(name);
                    output.writeUTF(file.effectiveMimeType());
                    output.writeLong(size);
                }
                output.flush();
                Log.i(TAG, "Announced " + sendable.size() + " queued nearby file(s) to receiver.");
            } catch (Exception e) {
                Log.w(TAG, "Could not announce queued nearby files.", e);
            }
        });
    }

    private void notifyReceiverFileCancellation(NearbyEndpoint endpoint, String fileId) {
        if (endpoint == null || TextUtils.isEmpty(fileId)) {
            return;
        }
        nearbyTransferExecutor.execute(() -> {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(endpoint.host, endpoint.port), 5000);
                DataOutputStream output = new DataOutputStream(socket.getOutputStream());
                output.writeUTF(nearbyConnectedEvent(endpoint.sessionToken));
                output.writeUTF(nearbyDeviceName());
                output.writeUTF(NEARBY_FILE_CANCEL_MAGIC);
                output.writeUTF(fileId);
                output.flush();
                Log.i(TAG, "Notified receiver to cancel nearby file " + fileId + ".");
            } catch (Exception e) {
                Log.w(TAG, "Could not notify receiver about cancelled file.", e);
            }
        });
    }

    private void notifySenderFileCancellation(String fileId) {
        if (TextUtils.isEmpty(fileId)
                || TextUtils.isEmpty(nearbyRemoteControlHost)
                || nearbyRemoteControlPort <= 0) {
            return;
        }
        nearbyTransferExecutor.execute(() -> {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(nearbyRemoteControlHost, nearbyRemoteControlPort), 5000);
                DataOutputStream output = new DataOutputStream(socket.getOutputStream());
                output.writeUTF(NEARBY_CONNECTED_EVENT);
                output.writeUTF(nearbyDeviceName());
                output.writeUTF(NEARBY_FILE_CANCEL_MAGIC);
                output.writeUTF(fileId);
                output.flush();
                Log.i(TAG, "Notified sender to cancel nearby file " + fileId + ".");
            } catch (Exception e) {
                Log.w(TAG, "Could not notify sender about cancelled file.", e);
            }
        });
    }

    private void prepareSenderForReconnect(String message) {
        String closeMessage = "Connection closed.";
        closeNearbyConnectionState(false, closeMessage);
        Toast.makeText(this, closeMessage, Toast.LENGTH_LONG).show();
    }

    private void notifyReceiverSessionEvent(String host, int port, String sessionToken, String sessionMagic) {
        nearbyTransferExecutor.execute(() -> {
            try {
                sendReceiverSessionEvent(host, port, sessionToken, sessionMagic);
            } catch (Exception e) {
                Log.w(TAG, "Could not notify nearby session at " + host + ":" + port + ".", e);
            }
        });
    }

    private void sendReceiverSessionEvent(String host, int port, String sessionToken, String sessionMagic) throws java.io.IOException {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, port), 5000);
                socket.setSoTimeout(5000);
                DataOutputStream output = new DataOutputStream(socket.getOutputStream());
                output.writeUTF(nearbyConnectedEvent(sessionToken));
                output.writeUTF(nearbyDeviceName());
                output.writeUTF(sessionMagic);
                if (NEARBY_PRESENCE_MAGIC.equals(sessionMagic)) {
                    output.writeInt(NEARBY_CONTROL_PORT);
                }
                output.flush();
                if (NEARBY_PRESENCE_MAGIC.equals(sessionMagic)) {
                    DataInputStream input = new DataInputStream(socket.getInputStream());
                    String response = input.readUTF();
                    if (NEARBY_SESSION_MISMATCH_MAGIC.equals(response)) {
                        throw new java.io.IOException(NEARBY_SESSION_ERROR);
                    }
                    if (!NEARBY_ACK_MAGIC.equals(response)) {
                        throw new java.io.IOException("Receiver did not confirm the Nearby session");
                    }
                }
                Log.i(TAG, "Nearby session event sent to " + host + ":" + port + ". type=" + sessionMagic);
            }
    }

    private void sendNearbyFiles(Socket socket, List<SharedFile> files, List<NearbyTransferItem> transferItems, String sessionToken) throws java.io.IOException {
        nearbySendingTransferStarted = true;
        mainHandler.post(this::updateNearbyTransferCardsVisibility);
        DataOutputStream output = new DataOutputStream(socket.getOutputStream());
        DataInputStream inputFromReceiver = new DataInputStream(socket.getInputStream());
        List<SharedFile> sendable = new ArrayList<>();
        List<Long> sizes = new ArrayList<>();
        List<NearbyTransferItem> sendableItems = new ArrayList<>();
        for (int i = 0; i < files.size(); i++) {
            SharedFile file = files.get(i);
            NearbyTransferItem item = i < transferItems.size() ? transferItems.get(i) : null;
            long size = resolveSharedFileSize(file);
            if (size >= 0) {
                sendable.add(file);
                sizes.add(size);
                sendableItems.add(item);
            } else {
                updateNearbyTransferItem(item, "Skipped", 0, size, "Could not read file size");
            }
        }
        if (sendable.isEmpty()) {
            throw new java.io.IOException("No shared files had a readable size");
        }
        output.writeUTF(nearbyConnectedEvent(sessionToken));
        output.writeUTF(nearbyDeviceName());
        output.flush();
        output.writeUTF(NEARBY_TRANSFER_MAGIC);
        output.writeInt(sendable.size());
        for (int i = 0; i < sendable.size(); i++) {
            SharedFile file = sendable.get(i);
            long size = sizes.get(i);
            String name = file.name == null ? "shared-file" : file.name;
            output.writeUTF(nearbyFileId(file, size));
            output.writeUTF(name);
            output.writeUTF(file.effectiveMimeType());
            output.writeLong(size);
        }
        output.flush();
        byte[] buffer = new byte[TRANSFER_BUFFER_SIZE];
        for (int i = 0; i < sendable.size(); i++) {
            SharedFile file = sendable.get(i);
            long size = sizes.get(i);
            NearbyTransferItem item = i < sendableItems.size() ? sendableItems.get(i) : null;
            String name = file.name == null ? "shared-file" : file.name;
            long requestedOffset = inputFromReceiver.readLong();
            if (requestedOffset == NEARBY_RECEIVER_CANCEL_OFFSET) {
                if (item != null) {
                    item.cancelRequested = true;
                }
                output.writeInt(NEARBY_CHUNK_CANCELLED);
                output.flush();
                updateNearbyTransferItem(item, "Cancelled", 0, size, "");
                continue;
            }
            if (requestedOffset < 0 || requestedOffset > size) {
                throw new java.io.IOException("Receiver requested an invalid resume offset");
            }
            long copied = requestedOffset;
            if (item != null && item.cancelRequested) {
                output.writeInt(NEARBY_CHUNK_CANCELLED);
                output.flush();
                updateNearbyTransferItem(item, "Cancelled", copied, size, "");
                continue;
            }
            updateNearbyTransferItem(item, requestedOffset > 0 ? "Resuming" : "Copying", copied, size, "");
            boolean completed = false;
            boolean cancelled = false;
            try (InputStream input = getContentResolver().openInputStream(file.uri)) {
                if (input == null) {
                    throw new java.io.IOException("Could not open " + file.name);
                }
                skipFully(input, requestedOffset);
                int read;
                while ((read = input.read(buffer)) != -1) {
                    waitIfNearbyTransferPaused(item, copied, size);
                    if (item != null && item.cancelRequested) {
                        output.writeInt(NEARBY_CHUNK_CANCELLED);
                        output.flush();
                        cancelled = true;
                        break;
                    }
                    output.writeInt(read);
                    output.write(buffer, 0, read);
                    copied += read;
                    updateNearbyTransferItem(item, "Copying", copied, size, "");
                }
                if (!cancelled) {
                    output.writeInt(NEARBY_CHUNK_DONE);
                    output.flush();
                    completed = true;
                }
            } catch (java.io.IOException e) {
                updateNearbyTransferItem(item, "Interrupted", copied, size, "");
                throw e;
            }
            if (cancelled) {
                updateNearbyTransferItem(item, "Cancelled", copied, size, "");
            } else if (completed) {
                updateNearbyTransferItem(item, "Completed", size, size, "");
            }
        }
        output.flush();
    }

    private void updateNearbyTransferItem(NearbyTransferItem item, String status, long transferredBytes, long totalBytes, String location) {
        if (item == null) {
            return;
        }
        long now = System.currentTimeMillis();
        boolean statusChanged = !TextUtils.equals(item.status, status);
        item.status = status;
        item.transferredBytes = Math.max(0, transferredBytes);
        item.totalBytes = totalBytes;
        item.location = location == null ? "" : location;
        if (isActiveTransferStatus(status)) {
            if (item.lastSpeedSampleAt == 0) {
                item.lastSpeedSampleAt = now;
                item.lastSpeedSampleBytes = item.transferredBytes;
            } else {
                long elapsedMs = Math.max(1, now - item.lastSpeedSampleAt);
                if (elapsedMs >= 500) {
                    long movedBytes = Math.max(0, item.transferredBytes - item.lastSpeedSampleBytes);
                    item.bytesPerSecond = movedBytes * 1000 / elapsedMs;
                    item.lastSpeedSampleAt = now;
                    item.lastSpeedSampleBytes = item.transferredBytes;
                }
            }
        } else if ("Completed".equals(status) || "Cancelled".equals(status) || "Interrupted".equals(status) || "Skipped".equals(status)) {
            item.bytesPerSecond = 0;
        }
        boolean terminal = "Completed".equals(status)
                || "Cancelled".equals(status)
                || "Interrupted".equals(status)
                || "Skipped".equals(status)
                || "Paused".equals(status);
        if (statusChanged || terminal || now - item.lastRenderAt >= 250) {
            item.lastRenderAt = now;
            runOnUiThread(this::renderNearbyTransferLists);
        }
        updateNearbyNotificationForItem(item, statusChanged || terminal, now);
    }

    private void updateNearbyNotificationForItem(NearbyTransferItem item, boolean immediate, long now) {
        boolean canPause = canPauseNearbyNotificationAction();
        boolean canCancel = canCancelNearbyNotificationAction();
        if (!canPause && !canCancel) {
            return;
        }
        if (!immediate && now - lastNearbyNotificationAt < 1000) {
            return;
        }
        lastNearbyNotificationAt = now;
        startNearbyTransferKeepAlive(nearbyNotificationText(item), canPause, canCancel);
    }

    private String nearbyNotificationText(NearbyTransferItem item) {
        StringBuilder text = new StringBuilder();
        text.append(item.status).append(" ").append(item.name);
        if (item.totalBytes >= 0) {
            text.append(" • ")
                    .append(formatSize(item.transferredBytes))
                    .append(" / ")
                    .append(formatSize(item.totalBytes));
        }
        if (item.bytesPerSecond > 0 || isActiveTransferStatus(item.status)) {
            text.append(" • ").append(formatSpeed(item.bytesPerSecond));
        }
        return text.toString();
    }

    private long resolveSharedFileSize(SharedFile file) {
        if (file.size >= 0) {
            return file.size;
        }
        try (ParcelFileDescriptor descriptor = getContentResolver().openFileDescriptor(file.uri, "r")) {
            if (descriptor != null) {
                return descriptor.getStatSize();
            }
        } catch (Exception ignored) {
        }
        return -1;
    }

    private int receiveNearbyFiles(Socket socket) throws java.io.IOException {
        return receiveNearbyFiles(socket, ensureNearbyReceiverSessionToken(), false);
    }

    private int receiveNearbyFiles(Socket socket, String expectedSessionToken, boolean senderQrFlow) throws java.io.IOException {
        DataInputStream input = new DataInputStream(socket.getInputStream());
        DataOutputStream outputToSender = new DataOutputStream(socket.getOutputStream());
        String connectionEvent = input.readUTF();
        String actualSessionToken = parseNearbyConnectedSession(connectionEvent);
        String senderName = input.readUTF();
        String displayName = TextUtils.isEmpty(senderName) ? "sender" : senderName;
        String magic = input.readUTF();
        if (!senderQrFlow && !activeReceiverSessionAccepts(magic)) {
            if (NEARBY_PRESENCE_MAGIC.equals(magic)) {
                writeNearbyPresenceResponse(outputToSender, NEARBY_SESSION_MISMATCH_MAGIC);
            }
            throw new java.io.IOException(NEARBY_SESSION_ERROR);
        }
        if (!nearbySessionMatches(expectedSessionToken, actualSessionToken)) {
            if (NEARBY_PRESENCE_MAGIC.equals(magic)) {
                writeNearbyPresenceResponse(outputToSender, NEARBY_SESSION_MISMATCH_MAGIC);
            }
            throw new java.io.IOException(NEARBY_SESSION_ERROR);
        }
        if (NEARBY_PRESENCE_MAGIC.equals(magic)) {
            mainHandler.post(() -> showIncomingNearbyConnection(displayName));
            nearbyRemoteControlHost = socket.getInetAddress().getHostAddress();
            nearbyRemoteControlPort = input.readInt();
            startNearbyHeartbeat(nearbyRemoteControlHost, nearbyRemoteControlPort);
            writeNearbyPresenceResponse(outputToSender, NEARBY_ACK_MAGIC);
            return NEARBY_MESSAGE_PRESENCE;
        }
        if (NEARBY_CANCEL_MAGIC.equals(magic)) {
            return NEARBY_MESSAGE_CANCEL;
        }
        if (NEARBY_FILE_CANCEL_MAGIC.equals(magic)) {
            String fileId = input.readUTF();
            mainHandler.post(() -> handleReceiverFileCancellation(fileId));
            return NEARBY_MESSAGE_FILE_CANCEL;
        }
        if (NEARBY_QUEUE_MAGIC.equals(magic)) {
            nearbyReceivingTransferStarted = true;
            mainHandler.post(this::updateNearbyTransferCardsVisibility);
            int count = input.readInt();
            readNearbyIncomingFileHeaders(input, displayName, count);
            runOnUiThread(() -> renderNearbyTransferState(count + " queued."));
            return NEARBY_MESSAGE_QUEUE;
        }
        if (!NEARBY_TRANSFER_MAGIC.equals(magic)) {
            throw new java.io.IOException("Unknown sender");
        }
        mainHandler.post(() -> showIncomingNearbyConnection(displayName));
        nearbyReceivingTransferStarted = true;
        mainHandler.post(this::updateNearbyTransferCardsVisibility);
        int count = input.readInt();
        List<NearbyIncomingFile> incomingFiles = readNearbyIncomingFileHeaders(input, displayName, count);
        byte[] buffer = new byte[TRANSFER_BUFFER_SIZE];
        for (NearbyIncomingFile incomingFile : incomingFiles) {
            String name = incomingFile.name;
            String mimeType = incomingFile.mimeType;
            long size = incomingFile.size;
            File partialFile = resolveNearbyPartialFile(incomingFile.resumeId, size);
            long savedBytes = Math.min(partialFile.exists() ? partialFile.length() : 0, size);
            if (savedBytes > 0 && !requestResumeDecision(name, savedBytes, size)) {
                savedBytes = 0;
            }
            Log.i(TAG, "Resume checkpoint for " + name + ": " + savedBytes + " / " + size + " bytes.");
            if (savedBytes == 0 && partialFile.exists() && !partialFile.delete()) {
                try (RandomAccessFile reset = new RandomAccessFile(partialFile, "rw")) {
                    reset.setLength(0);
                }
            }
            NearbyTransferItem item = incomingFile.item;
            if (item.cancelRequested) {
                outputToSender.writeLong(NEARBY_RECEIVER_CANCEL_OFFSET);
                outputToSender.flush();
                int cancelMarker = input.readInt();
                if (cancelMarker != NEARBY_CHUNK_CANCELLED) {
                    throw new java.io.IOException("Sender did not confirm cancelled file");
                }
                deleteNearbyPartialFile(partialFile);
                updateNearbyTransferItem(item, "Cancelled", 0, size, "");
                continue;
            }
            outputToSender.writeLong(savedBytes);
            outputToSender.flush();
            long remaining = size - savedBytes;
            long copied = savedBytes;
            updateNearbyTransferItem(item, savedBytes > 0 ? "Resuming" : "Copying", copied, size, "");
            boolean completed = false;
            boolean cancelled = false;
            try (RandomAccessFile output = new RandomAccessFile(partialFile, "rw")) {
                output.seek(savedBytes);
                while (true) {
                    waitIfNearbyTransferPaused(item, copied, size);
                    int chunkLength = input.readInt();
                    if (chunkLength == NEARBY_CHUNK_DONE) {
                        completed = true;
                        break;
                    }
                    if (chunkLength == NEARBY_CHUNK_CANCELLED) {
                        cancelled = true;
                        break;
                    }
                    if (chunkLength <= 0 || chunkLength > TRANSFER_BUFFER_SIZE) {
                        throw new java.io.IOException("Invalid transfer chunk");
                    }
                    int remainingChunk = chunkLength;
                    while (remainingChunk > 0) {
                        int readSize = Math.min(buffer.length, remainingChunk);
                        input.readFully(buffer, 0, readSize);
                        if (!item.cancelRequested) {
                            output.write(buffer, 0, readSize);
                            copied += readSize;
                            remaining = Math.max(0, remaining - readSize);
                            updateNearbyTransferItem(item, "Copying", copied, size, "");
                        }
                        remainingChunk -= readSize;
                    }
                }
            } catch (java.io.IOException e) {
                updateNearbyTransferItem(item, "Interrupted", copied, size, partialFile.getAbsolutePath());
                throw e;
            }
            if (cancelled || item.cancelRequested) {
                deleteNearbyPartialFile(partialFile);
                updateNearbyTransferItem(item, "Cancelled", copied, size, "");
                continue;
            }
            if (!completed || remaining > 0) {
                updateNearbyTransferItem(item, "Interrupted", copied, size, partialFile.getAbsolutePath());
                throw new java.io.IOException("Transfer ended early");
            }
            String location = publishReceivedPartial(partialFile, name, mimeType);
            updateNearbyTransferItem(item, "Completed", size, size, location);
        }
        runOnUiThread(() -> renderNearbyTransferState("Received " + count + "."));
        return NEARBY_MESSAGE_TRANSFER;
    }

    private List<NearbyIncomingFile> readNearbyIncomingFileHeaders(
            DataInputStream input,
            String senderName,
            int count
    ) throws java.io.IOException {
        List<NearbyIncomingFile> incomingFiles = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String senderFileId = input.readUTF();
            String name = safeFileName(input.readUTF());
            String mimeType = input.readUTF();
            long size = input.readLong();
            String resumeId = nearbyResumeId(senderName, name, size);
            NearbyTransferItem existingItem = findReceivedTransferItem(resumeId);
            NearbyTransferItem item = existingItem != null
                    ? existingItem
                    : new NearbyTransferItem(resumeId, name, size, "Pending");
            item.totalBytes = size;
            item.remoteFileId = senderFileId;
            if ("Cancelled".equals(item.status)) {
                item.cancelRequested = false;
                item.transferredBytes = 0;
                item.bytesPerSecond = 0;
                item.lastSpeedSampleAt = 0;
                item.lastSpeedSampleBytes = 0;
                item.lastRenderAt = 0;
                item.location = "";
                item.status = "Pending";
            }
            if (!"Copying".equals(item.status)
                    && !"Resuming".equals(item.status)
                    && !"Cancelled".equals(item.status)
                    && !"Completed".equals(item.status)) {
                item.status = "Pending";
                item.location = "";
            }
            if (existingItem == null) {
                mainHandler.post(() -> {
                    nearbyReceivedItems.add(item);
                    renderNearbyTransferLists();
                });
            } else {
                mainHandler.post(this::renderNearbyTransferLists);
            }
            incomingFiles.add(new NearbyIncomingFile(resumeId, name, mimeType, size, item));
        }
        return incomingFiles;
    }

    private NearbyTransferItem findReceivedTransferItem(String fileId) {
        for (NearbyTransferItem item : nearbyReceivedItems) {
            if (fileId.equals(item.fileId)) {
                return item;
            }
        }
        return null;
    }

    private NearbyTransferItem findReceivedTransferItemByRemoteFileId(String remoteFileId) {
        for (NearbyTransferItem item : nearbyReceivedItems) {
            if (remoteFileId.equals(item.remoteFileId)) {
                return item;
            }
        }
        return null;
    }

    private NearbyTransferItem findSendingTransferItem(String fileId) {
        for (NearbyTransferItem item : nearbySendingItems) {
            if (fileId.equals(item.fileId)) {
                return item;
            }
        }
        return null;
    }

    private String nearbyDeviceName() {
        String model = Build.MODEL;
        return TextUtils.isEmpty(model) ? "K-Share device" : model.trim();
    }

    private String nearbyFileId(SharedFile file, long size) {
        String name = file.name == null ? "shared-file" : file.name;
        return sha256(name.trim().toLowerCase(Locale.US) + "|" + size);
    }

    private String nearbyResumeId(String senderName, String fileName, long size) {
        String sender = TextUtils.isEmpty(senderName) ? "sender" : senderName.trim().toLowerCase(Locale.US);
        String name = TextUtils.isEmpty(fileName) ? "nearby-file" : fileName.trim().toLowerCase(Locale.US);
        return sha256(sender + "|" + name + "|" + size);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(bytes.length * 2);
            for (byte current : bytes) {
                result.append(String.format(Locale.US, "%02x", current & 0xff));
            }
            return result.toString();
        } catch (Exception e) {
            return Integer.toHexString(value.hashCode());
        }
    }

    private File nearbyPartialFile(String fileId) throws java.io.IOException {
        File directory = new File(getFilesDir(), "nearby-partials");
        if (!directory.exists() && !directory.mkdirs()) {
            throw new java.io.IOException("Could not create resumable transfer storage");
        }
        return new File(directory, sha256(fileId) + ".part");
    }

    private File resolveNearbyPartialFile(String resumeId, long expectedSize) throws java.io.IOException {
        File preferred = nearbyPartialFile(resumeId);
        if (preferred.exists()) {
            return preferred;
        }
        File directory = preferred.getParentFile();
        File[] candidates = directory == null ? null : directory.listFiles((dir, name) -> name.endsWith(".part"));
        if (candidates != null && candidates.length == 1
                && candidates[0].length() > 0 && candidates[0].length() <= expectedSize) {
            if (candidates[0].renameTo(preferred)) {
                Log.i(TAG, "Migrated an earlier nearby resume checkpoint.");
                return preferred;
            }
            return candidates[0];
        }
        return preferred;
    }

    private boolean requestResumeDecision(String name, long savedBytes, long totalBytes) throws java.io.IOException {
        CountDownLatch decisionReady = new CountDownLatch(1);
        AtomicBoolean resume = new AtomicBoolean(false);
        mainHandler.post(() -> new AlertDialog.Builder(this)
                .setTitle("Resume file transfer?")
                .setMessage(name + " has " + formatSize(savedBytes) + " of " + formatSize(totalBytes) + " saved.")
                .setPositiveButton("Resume", (dialog, which) -> {
                    resume.set(true);
                    decisionReady.countDown();
                })
                .setNegativeButton("Restart", (dialog, which) -> decisionReady.countDown())
                .setOnCancelListener(dialog -> decisionReady.countDown())
                .show());
        try {
            decisionReady.await();
            return resume.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new java.io.IOException("Resume decision was interrupted", e);
        }
    }

    private void skipFully(InputStream input, long byteCount) throws java.io.IOException {
        long remaining = byteCount;
        while (remaining > 0) {
            long skipped = input.skip(remaining);
            if (skipped > 0) {
                remaining -= skipped;
                continue;
            }
            if (input.read() == -1) {
                throw new java.io.IOException("Source file changed before resume");
            }
            remaining--;
        }
    }

    private String publishReceivedPartial(File partialFile, String name, String mimeType) throws java.io.IOException {
        ReceivedOutput target = createPublicReceivedOutput(name, mimeType);
        try (InputStream input = new java.io.FileInputStream(partialFile);
             OutputStream output = target.output) {
            byte[] buffer = new byte[TRANSFER_BUFFER_SIZE];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
        } catch (java.io.IOException e) {
            abandonReceivedOutput(target);
            throw e;
        }
        publishReceivedOutput(target);
        if (!partialFile.delete()) {
            Log.w(TAG, "Could not delete completed partial file " + partialFile.getName() + ".");
        }
        return target.displayLocation;
    }

    private void clearNearbyPartialFiles() {
        File directory = new File(getFilesDir(), "nearby-partials");
        File[] files = directory.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (!file.delete()) {
                Log.w(TAG, "Could not delete cancelled partial file " + file.getName() + ".");
            }
        }
    }

    private void deleteNearbyPartialFile(File partialFile) {
        if (partialFile != null && partialFile.exists() && !partialFile.delete()) {
            Log.w(TAG, "Could not delete cancelled partial file " + partialFile.getName() + ".");
        }
    }

    private void removeIncompleteReceivedItems() {
        for (int i = nearbyReceivedItems.size() - 1; i >= 0; i--) {
            if (!"Completed".equals(nearbyReceivedItems.get(i).status)) {
                nearbyReceivedItems.remove(i);
            }
        }
        renderNearbyTransferLists();
    }

    private ReceivedOutput createPublicReceivedOutput(String name, String mimeType) throws java.io.IOException {
        String safeName = safeFileName(name);
        String type = TextUtils.isEmpty(mimeType) ? "application/octet-stream" : mimeType;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Downloads.DISPLAY_NAME, safeName);
            values.put(MediaStore.Downloads.MIME_TYPE, type);
            values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/" + PUBLIC_RECEIVE_DIR);
            values.put(MediaStore.Downloads.IS_PENDING, 1);
            Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri == null) {
                throw new java.io.IOException("Could not create Downloads item");
            }
            OutputStream output = getContentResolver().openOutputStream(uri);
            if (output == null) {
                throw new java.io.IOException("Could not open Downloads item");
            }
            return new ReceivedOutput(output, "Downloads/" + PUBLIC_RECEIVE_DIR + "/" + safeName, uri);
        }

        File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File directory = new File(downloads, PUBLIC_RECEIVE_DIR);
        if (!directory.exists() && !directory.mkdirs()) {
            throw new java.io.IOException("Could not create Downloads folder");
        }
        File target = uniqueFile(directory, safeName);
        return new ReceivedOutput(new FileOutputStream(target), "Downloads/" + PUBLIC_RECEIVE_DIR + "/" + target.getName(), null);
    }

    private void publishReceivedOutput(ReceivedOutput target) {
        if (target.uri == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return;
        }
        ContentValues values = new ContentValues();
        values.put(MediaStore.Downloads.IS_PENDING, 0);
        getContentResolver().update(target.uri, values, null, null);
    }

    private void abandonReceivedOutput(ReceivedOutput target) {
        if (target.uri == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return;
        }
        try {
            getContentResolver().delete(target.uri, null, null);
        } catch (Exception e) {
            Log.w(TAG, "Could not remove incomplete received file.", e);
        }
    }

    private String safeFileName(String value) {
        String name = TextUtils.isEmpty(value) ? "received-file" : value;
        return name.replace("\\", "_").replace("/", "_").replace(":", "_").replace("*", "_").replace("?", "_").replace("\"", "_").replace("<", "_").replace(">", "_").replace("|", "_");
    }

    private File uniqueFile(File directory, String name) {
        File file = new File(directory, name);
        if (!file.exists()) {
            return file;
        }
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String extension = dot > 0 ? name.substring(dot) : "";
        int index = 1;
        while (true) {
            File candidate = new File(directory, base + "-" + index + extension);
            if (!candidate.exists()) {
                return candidate;
            }
            index++;
        }
    }

    private List<String> missingMediaPermissions() {
        List<String> missingPermissions = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(Manifest.permission.READ_MEDIA_VIDEO);
            }
            if (checkSelfPermission(Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(Manifest.permission.READ_MEDIA_AUDIO);
            }
        } else if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            missingPermissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        }
        return missingPermissions;
    }

    private boolean isDocumentCandidate(String name, String mimeType) {
        String type = mimeType == null ? "" : mimeType.toLowerCase(Locale.US);
        String lowerName = name == null ? "" : name.toLowerCase(Locale.US);
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

    private void startSharing() {
        if (repository.getFiles().isEmpty()) {
            Toast.makeText(this, "Add files to Local Share first.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATIONS);
        }
        Intent intent = new Intent(this, ShareService.class).setAction(ShareService.ACTION_START);
        intent.putExtra(ShareService.EXTRA_PORT, ShareService.DEFAULT_PORT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    private void stopSharing() {
        startService(new Intent(this, ShareService.class).setAction(ShareService.ACTION_STOP));
        activePort = 0;
        pendingRemoteStart = false;
        renderStatus();
        renderWebVisitorStatus();
    }

    private void confirmStopLocalSharing() {
        if (activePort <= 0) {
            return;
        }
        showStopWebShareDialog(
                "LOCAL SHARE",
                "Stop Local Share?",
                "People on this Wi-Fi will lose access to the local link.",
                WebAccessManager.SCOPE_LOCAL,
                Color.rgb(61, 198, 190),
                this::stopSharing
        );
    }

    private void confirmStartRemoteSharing() {
        if (liveRepository.getFiles().isEmpty()) {
            Toast.makeText(this, "Add files to Live Share first.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!remoteRelayConfigured()) {
            showGlobalLinkUnavailableDialog();
            return;
        }
        showCreateGlobalLinkDialog();
    }

    private void showGlobalLinkUnavailableDialog() {
        int ink = Color.rgb(241, 248, 246);
        int muted = Color.rgb(148, 163, 184);
        int warn = Color.rgb(255, 170, 96);

        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);

        LinearLayout shell = globalLinkDialogShell();
        shell.addView(globalLinkDialogHeader(
                "GLOBAL LINK",
                "Relay not configured",
                "Add a deployed relay host before creating worldwide Web Share links.",
                R.drawable.ic_lock,
                warn
        ), fullWidthWithMargins(0, 0, 0, dp(10)));

        TextView body = text("K-Share can still share files on your local Wi-Fi. Global sharing needs the relay host, port, and public base baked into the app build.", 13, muted, false);
        body.setLineSpacing(dp(2), 1.0f);
        body.setPadding(dp(12), dp(12), dp(12), dp(12));
        body.setBackground(rounded(Color.rgb(12, 25, 31), dp(16), Color.rgb(38, 60, 69), dp(1)));
        shell.addView(body, fullWidthWithMargins(0, 0, 0, dp(14)));

        Button okButton = button("OK");
        styleButton(okButton, warn, Color.rgb(29, 16, 5), 0, 16);
        setButtonIcon(okButton, R.drawable.ic_done, Color.rgb(29, 16, 5));
        okButton.setOnClickListener(v -> dialog.dismiss());
        shell.addView(okButton, fullWidthWithMargins(0, 0, 0, 0, dp(46)));

        showFluxDialog(dialog, shell, dp(520));
    }

    private void showCreateGlobalLinkDialog() {
        int ink = Color.rgb(241, 248, 246);
        int muted = Color.rgb(148, 163, 184);
        int accent = Color.rgb(61, 198, 190);
        int warn = Color.rgb(255, 170, 96);
        int selectedCount = liveRepository.getFiles().size();

        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);

        LinearLayout shell = globalLinkDialogShell();
        shell.addView(globalLinkDialogHeader(
                "GLOBAL WEB SHARE",
                "Create global link?",
                "Share only your Live Share files through the relay.",
                R.drawable.ic_share,
                warn
        ), fullWidthWithMargins(0, 0, 0, dp(14)));

        LinearLayout stats = new LinearLayout(this);
        stats.setOrientation(LinearLayout.HORIZONTAL);
        stats.setGravity(Gravity.CENTER_VERTICAL);
        stats.setPadding(dp(10), dp(10), dp(10), dp(10));
        stats.setBackground(gradient(
                new int[]{Color.rgb(57, 37, 12), Color.rgb(31, 27, 20)},
                dp(18),
                Color.rgb(180, 83, 9),
                dp(1),
                GradientDrawable.Orientation.LEFT_RIGHT
        ));
        shell.addView(stats, fullWidthWithMargins(0, 0, 0, dp(10)));

        TextView selected = text(String.valueOf(selectedCount), 24, Color.WHITE, true);
        selected.setGravity(Gravity.CENTER);
        selected.setIncludeFontPadding(false);
        selected.setBackground(rounded(Color.rgb(12, 25, 31), dp(16), warn, dp(1)));
        LinearLayout.LayoutParams selectedParams = new LinearLayout.LayoutParams(dp(54), dp(48));
        selectedParams.setMargins(0, 0, dp(10), 0);
        stats.addView(selected, selectedParams);

        LinearLayout statCopy = new LinearLayout(this);
        statCopy.setOrientation(LinearLayout.VERTICAL);
        stats.addView(statCopy, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView statTitle = text(selectedCount == 1 ? "1 selected file" : selectedCount + " selected files", 14, ink, true);
        statTitle.setIncludeFontPadding(false);
        statCopy.addView(statTitle);

        TextView statHint = text("Only Live Shared files will be visible.", 11, Color.rgb(203, 213, 225), false);
        statHint.setPadding(0, dp(4), 0, 0);
        statCopy.addView(statHint);

        shell.addView(globalLinkInfoRow("Visitor approval", "Visitors request access before files open.", R.drawable.ic_lock, accent), fullWidthWithMargins(0, 0, 0, dp(7)));
        shell.addView(globalLinkInfoRow("Private relay link", "Anyone with the link can request access.", R.drawable.ic_qr_code, warn), fullWidthWithMargins(0, 0, 0, dp(7)));
        shell.addView(globalLinkInfoRow("Phone stays in control", "Stop the link or remove visitors any time.", R.drawable.ic_phone_android, Color.rgb(126, 149, 255)), fullWidthWithMargins(0, 0, 0, dp(10)));

        TextView warning = text("Keep K-Share running while downloads are active.", 11, muted, false);
        warning.setPadding(dp(10), dp(8), dp(10), dp(8));
        warning.setBackground(rounded(Color.rgb(12, 25, 31), dp(14), Color.rgb(38, 60, 69), dp(1)));
        shell.addView(warning, fullWidthWithMargins(0, 0, 0, dp(10)));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER);
        shell.addView(actions, fullWidthWithMargins(0, 0, 0, 0, dp(44)));

        Button cancelButton = button("Cancel");
        styleButton(cancelButton, Color.rgb(12, 25, 31), muted, Color.rgb(38, 60, 69), 16);
        cancelButton.setOnClickListener(v -> dialog.dismiss());
        actions.addView(cancelButton, toolbarButtonParams(0, dp(6)));

        Button createButton = button("Create link");
        styleButton(createButton, warn, Color.rgb(29, 16, 5), 0, 16);
        setButtonIcon(createButton, R.drawable.ic_share, Color.rgb(29, 16, 5));
        createButton.setTooltipText("Create global link");
        createButton.setOnClickListener(v -> {
            dialog.dismiss();
            startRemoteSharingConfirmed();
        });
        actions.addView(createButton, toolbarButtonParams(dp(6), 0));

        showFluxDialog(dialog, shell, dp(520));
    }

    private LinearLayout globalLinkDialogShell() {
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setPadding(dp(14), dp(14), dp(14), dp(14));
        shell.setBackground(gradient(
                new int[]{Color.rgb(8, 18, 23), Color.rgb(10, 21, 27), Color.rgb(6, 14, 18)},
                dp(24),
                Color.rgb(38, 60, 69),
                dp(1),
                GradientDrawable.Orientation.TL_BR
        ));
        return shell;
    }

    private LinearLayout globalLinkDialogHeader(String eyebrow, String title, String subtitle, int iconId, int accentColor) {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout iconShell = new LinearLayout(this);
        iconShell.setGravity(Gravity.CENTER);
        iconShell.setBackground(rounded(
                blendColor(accentColor, Color.BLACK, 0.68f),
                dp(16),
                blendColor(accentColor, Color.WHITE, 0.14f),
                dp(1)
        ));
        ImageView icon = new ImageView(this);
        icon.setImageDrawable(tintedIcon(iconId, accentColor, 23));
        iconShell.addView(icon, new LinearLayout.LayoutParams(dp(25), dp(25)));
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(44), dp(44));
        iconParams.setMargins(0, 0, dp(10), 0);
        header.addView(iconShell, iconParams);

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        header.addView(copy, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView eyebrowView = text(eyebrow, 10, accentColor, true);
        eyebrowView.setIncludeFontPadding(false);
        eyebrowView.setLetterSpacing(0.16f);
        copy.addView(eyebrowView);

        TextView titleView = text(title, 21, Color.rgb(241, 248, 246), true);
        titleView.setIncludeFontPadding(false);
        titleView.setPadding(0, dp(4), 0, 0);
        copy.addView(titleView);

        TextView subtitleView = text(subtitle, 11, Color.rgb(148, 163, 184), false);
        subtitleView.setPadding(0, dp(4), 0, 0);
        copy.addView(subtitleView);
        return header;
    }

    private LinearLayout globalLinkInfoRow(String title, String description, int iconId, int accentColor) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), dp(8), dp(10), dp(8));
        row.setBackground(rounded(Color.rgb(12, 25, 31), dp(14), Color.rgb(38, 60, 69), dp(1)));

        ImageView icon = new ImageView(this);
        icon.setImageDrawable(tintedIcon(iconId, accentColor, 20));
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(24), dp(24));
        iconParams.setMargins(0, 0, dp(9), 0);
        row.addView(icon, iconParams);

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        row.addView(copy, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView titleView = text(title, 12, Color.rgb(241, 248, 246), true);
        titleView.setIncludeFontPadding(false);
        copy.addView(titleView);

        TextView descriptionView = text(description, 11, Color.rgb(148, 163, 184), false);
        descriptionView.setPadding(0, dp(3), 0, 0);
        copy.addView(descriptionView);
        return row;
    }

    private void showFluxDialog(Dialog dialog, View content, int maxWidth) {
        dialog.setContentView(content);
        dialog.show();
        android.view.Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            android.view.WindowManager.LayoutParams attributes = window.getAttributes();
            attributes.dimAmount = 0.72f;
            window.setAttributes(attributes);
            window.setLayout(centeredDialogWidth(maxWidth), android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private int centeredDialogWidth(int maxWidth) {
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int horizontalGutter = isLandscape() ? dp(128) : dp(112);
        int compactWidth = Math.min(screenWidth - horizontalGutter, maxWidth);
        int minimumWidth = Math.min(screenWidth - dp(56), dp(280));
        return Math.max(minimumWidth, compactWidth);
    }

    private void startRemoteSharingConfirmed() {
        if (liveRepository.getFiles().isEmpty()) {
            Toast.makeText(this, "Add files to Live Share first.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATIONS);
        }
        Intent intent = new Intent(this, RemoteRelayService.class).setAction(RemoteRelayService.ACTION_START);
        intent.putExtra(RemoteRelayService.EXTRA_USE_PRIVATE_SERVER, true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
        remoteRelayRunning = true;
        remoteRelayConnected = false;
        renderRemoteStatus("Connecting to relay...");
    }

    private void stopRemoteSharing() {
        pendingRemoteStart = false;
        startService(new Intent(this, RemoteRelayService.class).setAction(RemoteRelayService.ACTION_STOP));
        remoteRelayRunning = false;
        remoteRelayConnected = false;
        remoteShareUrl = "";
        remoteShortShareUrl = "";
        remoteFullShareUrl = "";
        remoteTinyUrlError = "";
        renderRemoteStatus("Global link off.");
        renderWebVisitorStatus();
    }

    private void confirmStopRemoteSharing() {
        if (!remoteRelayRunning) {
            return;
        }
        showStopWebShareDialog(
                "LIVE SHARE",
                "Stop Live Share?",
                "Remote visitors will lose access to the relay link.",
                WebAccessManager.SCOPE_LIVE,
                Color.rgb(255, 170, 96),
                this::stopRemoteSharing
        );
    }

    private void showStopWebShareDialog(
            String eyebrow,
            String title,
            String message,
            String scope,
            int accentColor,
            Runnable confirmAction
    ) {
        int ink = Color.rgb(241, 248, 246);
        int muted = Color.rgb(148, 163, 184);
        int danger = Color.rgb(252, 165, 165);
        int visitorCount = WebAccessManager.connectedCount(scope);
        int activeCount = WebAccessManager.activeCount(scope);
        long bytesUsed = WebAccessManager.totalBytesServed(scope);

        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);

        LinearLayout shell = globalLinkDialogShell();
        shell.addView(globalLinkDialogHeader(
                eyebrow,
                title,
                message,
                R.drawable.ic_stop,
                accentColor
        ), fullWidthWithMargins(0, 0, 0, dp(12)));

        TextView impact = text(
                visitorCount + " connected / " + activeCount + " active / " + formatSize(bytesUsed) + " used",
                13,
                ink,
                true
        );
        impact.setPadding(dp(12), dp(10), dp(12), dp(10));
        impact.setBackground(rounded(Color.rgb(12, 25, 31), dp(16), Color.rgb(38, 60, 69), dp(1)));
        shell.addView(impact, fullWidthWithMargins(0, 0, 0, dp(10)));

        TextView warning = text("This does not delete your selected files. It only closes the active sharing session.", 11, muted, false);
        warning.setPadding(dp(12), dp(10), dp(12), dp(10));
        warning.setBackground(rounded(Color.rgb(43, 30, 16), dp(16), Color.rgb(180, 83, 9), dp(1)));
        shell.addView(warning, fullWidthWithMargins(0, 0, 0, dp(12)));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER);
        shell.addView(actions, fullWidthWithMargins(0, 0, 0, 0, dp(44)));

        Button keepButton = button("Keep running");
        styleButton(keepButton, Color.rgb(12, 25, 31), muted, Color.rgb(38, 60, 69), 16);
        keepButton.setOnClickListener(v -> dialog.dismiss());
        actions.addView(keepButton, toolbarButtonParams(0, dp(6)));

        Button stopButton = button("Stop");
        styleButton(stopButton, Color.rgb(69, 26, 26), danger, Color.rgb(127, 29, 29), 16);
        setButtonIcon(stopButton, R.drawable.ic_stop, danger);
        stopButton.setOnClickListener(v -> {
            dialog.dismiss();
            confirmAction.run();
        });
        actions.addView(stopButton, toolbarButtonParams(dp(6), 0));

        showFluxDialog(dialog, shell, dp(500));
    }

    private String preferredRemoteShareUrl(String publicUrl, String shortUrl, String fullUrl) {
        if (!TextUtils.isEmpty(shortUrl)) {
            return shortUrl;
        }
        if (!TextUtils.isEmpty(publicUrl)) {
            return publicUrl;
        }
        return safeString(fullUrl);
    }

    private String safeString(String value) {
        return value == null ? "" : value;
    }

    private boolean remoteRelayConfigured() {
        return !TextUtils.isEmpty(BuildConfig.FLUX_RELAY_HOST)
                && BuildConfig.FLUX_RELAY_PORT > 0
                && !TextUtils.isEmpty(BuildConfig.FLUX_RELAY_PUBLIC_BASE);
    }

    private void showFileLibraryDialog() {
        if (fileLibraryDialog != null && fileLibraryDialog.isShowing()) {
            renderFiles();
            return;
        }

        int ink = Color.rgb(241, 248, 246);
        int muted = Color.rgb(148, 163, 184);
        int accent = Color.rgb(61, 198, 190);
        int panel = Color.rgb(10, 21, 27);

        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setPadding(dp(14), dp(14), dp(14), dp(14));
        shell.setBackground(cardBackground(panel, dp(26)));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        shell.addView(header, fullWidthWithMargins(0, 0, 0, dp(10), dp(42)));

        LinearLayout titleCopy = new LinearLayout(this);
        titleCopy.setOrientation(LinearLayout.VERTICAL);
        header.addView(titleCopy, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView title = text(activeWebShareName() + " Library", 20, ink, true);
        title.setIncludeFontPadding(false);
        titleCopy.addView(title);

        TextView subtitle = text("Choose files for " + activeWebShareName() + " only.", 12, muted, false);
        subtitle.setSingleLine(true);
        subtitle.setEllipsize(TextUtils.TruncateAt.END);
        subtitle.setPadding(0, dp(4), 0, 0);
        titleCopy.addView(subtitle);

        ImageButton closeButton = smallIconButton(
                R.drawable.ic_close,
                Color.rgb(252, 165, 165),
                Color.rgb(69, 26, 26),
                Color.rgb(127, 29, 29),
                "Close library",
                "Close file library"
        );
        closeButton.setOnClickListener(v -> dialog.dismiss());
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(dp(40), dp(40));
        closeParams.setMargins(dp(10), 0, 0, 0);
        header.addView(closeButton, closeParams);

        LinearLayout actionToolbar = new LinearLayout(this);
        actionToolbar.setOrientation(LinearLayout.HORIZONTAL);
        actionToolbar.setGravity(Gravity.CENTER);
        shell.addView(actionToolbar, fullWidthWithMargins(0, 0, 0, dp(10), dp(48)));

        refreshFilesButton = iconButton(
                R.drawable.ic_refresh,
                accent,
                Color.rgb(12, 25, 31),
                Color.rgb(38, 60, 69),
                "Refresh files",
                "Find new videos, audio, and documents"
        );
        actionToolbar.addView(refreshFilesButton, toolbarButtonParams(0, dp(6)));

        ImageButton clearButton = iconButton(
                R.drawable.ic_close,
                Color.rgb(252, 165, 165),
                Color.rgb(69, 26, 26),
                Color.rgb(127, 29, 29),
                "Clear " + activeWebShareName(),
                "Remove every file from the " + activeWebShareName() + " list"
        );
        actionToolbar.addView(clearButton, toolbarButtonParams(dp(6), 0));

        fileScanStatusView = text("", 13, muted, false);
        fileScanStatusView.setPadding(dp(2), 0, dp(2), dp(6));
        fileScanStatusView.setVisibility(View.GONE);
        shell.addView(fileScanStatusView);

        fileScanProgressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        fileScanProgressBar.setIndeterminate(true);
        fileScanProgressBar.setVisibility(View.GONE);
        shell.addView(fileScanProgressBar, fullWidthWithMargins(0, 0, 0, dp(10), dp(5)));
        updateFileScanUi(fileScanMessage, fileScanUiRunning);

        fileSearchInput = new EditText(this);
        fileSearchInput.setSingleLine(true);
        fileSearchQuery = fileSearchQueryForCategory(activeFileCategory);
        fileSearchInput.setText(fileSearchQuery);
        fileSearchInput.setSelection(fileSearchInput.length());
        fileSearchInput.setTextColor(ink);
        fileSearchInput.setHintTextColor(muted);
        fileSearchInput.setHint(fileSearchHint());
        fileSearchInput.setTextSize(scaledSp(15));
        fileSearchInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        fileSearchInput.setPadding(dp(14), 0, dp(14), 0);
        fileSearchInput.setCompoundDrawablePadding(dp(10));
        fileSearchInput.setCompoundDrawablesWithIntrinsicBounds(
                tintedIcon(R.drawable.ic_search, accent),
                null,
                null,
                null
        );
        fileSearchInput.setBackground(gradient(
                new int[]{Color.rgb(8, 18, 23), Color.rgb(12, 25, 31)},
                dp(16),
                Color.rgb(38, 60, 69),
                dp(1),
                GradientDrawable.Orientation.LEFT_RIGHT
        ));
        fileSearchInput.setTooltipText("Search all discovered files");
        shell.addView(fileSearchInput, fullWidthWithMargins(0, 0, 0, dp(10), dp(44)));

        LinearLayout categoryTabs = new LinearLayout(this);
        categoryTabs.setOrientation(LinearLayout.HORIZONTAL);
        categoryTabs.setGravity(Gravity.CENTER);
        categoryTabs.setPadding(0, 0, 0, dp(10));
        shell.addView(categoryTabs, fullWidthWithMargins(0, 0, 0, 0, dp(52)));

        videosCategoryButton = button("");
        categoryTabs.addView(videosCategoryButton, categoryTabParams(0, dp(4)));

        filesCategoryButton = button("");
        categoryTabs.addView(filesCategoryButton, categoryTabParams(dp(4), dp(4)));

        songsCategoryButton = button("");
        categoryTabs.addView(songsCategoryButton, categoryTabParams(dp(4), dp(4)));

        compressedCategoryButton = button("");
        categoryTabs.addView(compressedCategoryButton, categoryTabParams(dp(4), 0));

        LinearLayout filters = new LinearLayout(this);
        filters.setOrientation(LinearLayout.HORIZONTAL);
        filters.setGravity(Gravity.CENTER_VERTICAL);
        filters.setPadding(0, 0, 0, dp(12));
        shell.addView(filters);

        FrameLayout shareFilterFrame = new FrameLayout(this);
        LinearLayout.LayoutParams shareFilterParams = new LinearLayout.LayoutParams(0, dp(54), 1);
        shareFilterParams.setMargins(0, 0, dp(6), 0);
        filters.addView(shareFilterFrame, shareFilterParams);

        shareableFilterButton = button("Share");
        shareableFilterButton.setTooltipText("Show discovered files that are not shared yet");
        shareFilterFrame.addView(shareableFilterButton, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));
        shareableFilterBadgeView = filterCountBadge();
        FrameLayout.LayoutParams shareBadgeParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                dp(22),
                Gravity.TOP | Gravity.END
        );
        shareBadgeParams.setMargins(0, dp(3), dp(8), 0);
        shareFilterFrame.addView(shareableFilterBadgeView, shareBadgeParams);

        FrameLayout sharedFilterFrame = new FrameLayout(this);
        LinearLayout.LayoutParams sharedFilterParams = new LinearLayout.LayoutParams(0, dp(54), 1);
        sharedFilterParams.setMargins(dp(6), 0, 0, 0);
        filters.addView(sharedFilterFrame, sharedFilterParams);

        sharedFilterButton = button("Shared");
        sharedFilterButton.setTooltipText("Show files currently exposed by " + activeWebShareName());
        sharedFilterFrame.addView(sharedFilterButton, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));
        sharedFilterBadgeView = filterCountBadge();
        FrameLayout.LayoutParams sharedBadgeParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                dp(22),
                Gravity.TOP | Gravity.END
        );
        sharedBadgeParams.setMargins(0, dp(3), dp(8), 0);
        sharedFilterFrame.addView(sharedFilterBadgeView, sharedBadgeParams);

        fileFilteredCountView = text("", 12, Color.rgb(203, 213, 225), true);
        fileFilteredCountView.setSingleLine(true);
        fileFilteredCountView.setEllipsize(TextUtils.TruncateAt.END);
        fileFilteredCountView.setPadding(dp(10), 0, dp(10), 0);
        fileFilteredCountView.setGravity(Gravity.CENTER_VERTICAL);
        fileFilteredCountView.setBackground(rounded(Color.rgb(8, 18, 23), dp(14), Color.rgb(38, 60, 69), dp(1)));
        shell.addView(fileFilteredCountView, fullWidthWithMargins(0, 0, 0, dp(8), dp(32)));

        ScrollView fileScroll = new ScrollView(this);
        fileScroll.setFillViewport(false);
        fileScroll.setClipToPadding(false);
        fileScroll.setPadding(0, 0, 0, dp(2));
        fileList = new LinearLayout(this);
        fileList.setOrientation(LinearLayout.VERTICAL);
        fileScroll.addView(fileList);
        shell.addView(fileScroll, fullWidthWithMargins(0, 0, 0, 0, fileLibraryListHeight()));

        fileSearchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence value, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence value, int start, int before, int count) {
                if (suppressFileSearchChange) {
                    return;
                }
                if (pendingFileSearchRender != null) {
                    mainHandler.removeCallbacks(pendingFileSearchRender);
                }
                String nextQuery = value == null ? "" : value.toString().trim();
                String category = activeFileCategory;
                pendingFileSearchRender = () -> {
                    fileSearchQueries.put(category, nextQuery);
                    if (!TextUtils.equals(activeFileCategory, category)) {
                        return;
                    }
                    fileSearchQuery = nextQuery;
                    visibleAvailableFileLimit = FILE_RENDER_PAGE_SIZE;
                    renderFiles();
                };
                mainHandler.postDelayed(pendingFileSearchRender, 180);
            }

            @Override
            public void afterTextChanged(Editable value) {
            }
        });
        videosCategoryButton.setOnClickListener(v -> selectFileCategory(FILE_CATEGORY_VIDEOS));
        filesCategoryButton.setOnClickListener(v -> selectFileCategory(FILE_CATEGORY_FILES));
        songsCategoryButton.setOnClickListener(v -> selectFileCategory(FILE_CATEGORY_SONGS));
        compressedCategoryButton.setOnClickListener(v -> selectFileCategory(FILE_CATEGORY_COMPRESSED));
        refreshFilesButton.setOnClickListener(v -> refreshDiscoveredFiles(true));
        clearButton.setOnClickListener(v -> {
            activeWebRepository().clear();
            loadCachedDiscoveredFiles();
            renderFiles();
        });
        shareableFilterButton.setOnClickListener(v -> {
            showSharedFilter = false;
            visibleAvailableFileLimit = FILE_RENDER_PAGE_SIZE;
            renderFiles();
        });
        sharedFilterButton.setOnClickListener(v -> {
            showSharedFilter = true;
            visibleAvailableFileLimit = FILE_RENDER_PAGE_SIZE;
            renderFiles();
        });

        dialog.setContentView(shell);
        dialog.setOnDismissListener(v -> clearFileLibraryDialogViews());
        fileLibraryDialog = dialog;
        dialog.show();
        android.view.Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            int screenWidth = getResources().getDisplayMetrics().widthPixels;
            int maxWidth = isLandscape() ? dp(820) : dp(560);
            int width = Math.min(screenWidth - dp(20), maxWidth);
            window.setLayout(Math.max(dp(300), width), android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        renderFiles();
    }

    private int fileLibraryListHeight() {
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        int reserved = isLandscape() ? dp(270) : dp(332);
        int maximum = isLandscape() ? dp(260) : dp(390);
        return Math.max(dp(170), Math.min(maximum, screenHeight - reserved));
    }

    private void clearFileLibraryDialogViews() {
        if (fileSearchInput != null) {
            fileSearchQuery = fileSearchInput.getText().toString().trim();
            fileSearchQueries.put(activeFileCategory, fileSearchQuery);
        }
        if (pendingFileSearchRender != null) {
            mainHandler.removeCallbacks(pendingFileSearchRender);
            pendingFileSearchRender = null;
        }
        if (fileCategoryPopover != null) {
            fileCategoryPopover.dismiss();
            fileCategoryPopover = null;
        }
        fileLibraryDialog = null;
        fileSearchInput = null;
        fileList = null;
        refreshFilesButton = null;
        fileScanStatusView = null;
        fileScanProgressBar = null;
        videosCategoryButton = null;
        filesCategoryButton = null;
        songsCategoryButton = null;
        compressedCategoryButton = null;
        shareableFilterButton = null;
        sharedFilterButton = null;
        shareableFilterBadgeView = null;
        sharedFilterBadgeView = null;
        fileFilteredCountView = null;
        updateLibrarySummary();
    }

    private void updateLibrarySummary() {
        if (librarySummaryView != null) {
            librarySummaryView.setText(librarySummaryText());
        }
        if (openLibraryButton != null) {
            boolean open = fileLibraryDialog != null && fileLibraryDialog.isShowing();
            ShareRepository webRepository = activeWebRepository();
            openLibraryButton.setText(open ? "Viewing" : activeWebShareName());
            String summary = "Open " + activeWebShareName() + " library. "
                    + availableFiles.size() + " ready, " + webRepository.getFiles().size() + " shared.";
            openLibraryButton.setTooltipText(summary);
            openLibraryButton.setContentDescription(summary);
        }
    }

    private String librarySummaryText() {
        int readyCount = availableFiles.size();
        int sharedCount = activeWebRepository().getFiles().size();
        return activeWebShareName() + " · " + readyCount + " ready · " + sharedCount + " shared\n"
                + fileCategoryLabel(FILE_CATEGORY_VIDEOS) + " " + categoryPair(FILE_CATEGORY_VIDEOS)
                + " · " + fileCategoryLabel(FILE_CATEGORY_FILES) + " " + categoryPair(FILE_CATEGORY_FILES)
                + " · " + fileCategoryLabel(FILE_CATEGORY_SONGS) + " " + categoryPair(FILE_CATEGORY_SONGS)
                + " · " + fileCategoryLabel(FILE_CATEGORY_COMPRESSED) + " " + categoryPair(FILE_CATEGORY_COMPRESSED);
    }

    private String categoryPair(String category) {
        return categoryCount(availableFiles, category) + "/" + categoryCount(activeWebRepository().getFiles(), category);
    }

    private void renderFiles() {
        updateLibrarySummary();
        updateSendNearbyButton();
        if (fileList == null) {
            updateFileCategoryTabs();
            updateFilterButtons();
            return;
        }
        fileList.removeAllViews();
        updateFileCategoryTabs();
        updateFilterButtons();
        updateFilteredCountView();

        if (showSharedFilter) {
            ShareRepository webRepository = activeWebRepository();
            List<SharedFile> sharedFiles = webRepository.getFiles();
            if (sharedFiles.isEmpty()) {
                TextView empty = emptyState("No shared files.");
                fileList.addView(empty);
                return;
            }
            List<SharedFile> filteredSharedFiles = filterFiles(sharedFiles);
            if (filteredSharedFiles.isEmpty()) {
                fileList.addView(emptyState(emptyFileCategoryMessage()));
                return;
            }
            renderGroupedFileList(filteredSharedFiles, true);
            return;
        }

        if (availableFiles.isEmpty()) {
            TextView emptyAvailable = emptyState("No files.");
            fileList.addView(emptyAvailable);
            return;
        }

        List<SharedFile> filteredAvailableFiles = filterFiles(availableFiles);
        if (filteredAvailableFiles.isEmpty()) {
            fileList.addView(emptyState(emptyFileCategoryMessage()));
            return;
        }
        renderGroupedFileList(filteredAvailableFiles, false);
    }

    private void renderGroupedFileList(List<SharedFile> files, boolean sharedMode) {
        List<FileFolderGroup> groups = fileFolderGroups(files);
        int visibleCount = Math.min(visibleAvailableFileLimit, groups.size());
        for (FileFolderGroup group : new ArrayList<>(groups.subList(0, visibleCount))) {
            fileList.addView(folderRow(group, sharedMode));
            if (expandedFileFolders.contains(folderExpansionKey(sharedMode, group.key))) {
                for (SharedFile file : group.files) {
                    LinearLayout child = fileRow(file, sharedMode ? "Remove" : "Share", () -> {
                        if (sharedMode) {
                            activeWebRepository().removeFile(file.uri);
                            if (!containsUri(availableFiles, file.uri)) {
                                availableFiles.add(file);
                            }
                        } else {
                            activeWebRepository().addFile(file.uri);
                            availableFiles.remove(file);
                        }
                        renderFiles();
                    });
                    child.setLayoutParams(fullWidthWithMargins(dp(16), 0, 0, dp(8)));
                    fileList.addView(child);
                }
            }
        }
        addLoadMoreButton(visibleCount, groups.size(), "folders");
    }

    private List<FileFolderGroup> fileFolderGroups(List<SharedFile> files) {
        Map<String, FileFolderGroup> groups = new LinkedHashMap<>();
        for (SharedFile file : files) {
            String label = file.parentFolderLabel();
            String key = label.toLowerCase(Locale.US);
            FileFolderGroup group = groups.get(key);
            if (group == null) {
                group = new FileFolderGroup(key, label);
                groups.put(key, group);
            }
            group.files.add(file);
            if (file.size > 0) {
                group.totalBytes += file.size;
            }
        }
        return new ArrayList<>(groups.values());
    }

    private LinearLayout folderRow(FileFolderGroup group, boolean sharedMode) {
        boolean expanded = expandedFileFolders.contains(folderExpansionKey(sharedMode, group.key));
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(10), dp(10), dp(10));
        row.setBackground(rowBackground());
        row.setElevation(dp(2));
        row.setOnClickListener(v -> toggleFolder(group, sharedMode));
        row.setTooltipText(group.label + " · " + group.files.size() + " files");

        ImageView icon = new ImageView(this);
        icon.setImageDrawable(tintedIcon(R.drawable.ic_folder, Color.rgb(61, 198, 190)));
        icon.setBackground(glassButtonBackground(Color.rgb(12, 45, 51), Color.rgb(38, 90, 96), dp(15)));
        icon.setPadding(dp(9), dp(9), dp(9), dp(9));
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(44), dp(42));
        iconParams.setMargins(0, 0, dp(11), 0);
        row.addView(icon, iconParams);

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        row.addView(copy, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView name = text(group.label, 15, Color.rgb(241, 248, 246), true);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        copy.addView(name);

        TextView details = text(group.files.size() + " files · " + formatSize(group.totalBytes) + " · " + (expanded ? "shown" : "tap to view"), 12, Color.rgb(148, 163, 184), false);
        details.setSingleLine(true);
        details.setEllipsize(TextUtils.TruncateAt.END);
        copy.addView(details);

        Button actionButton = button(sharedMode ? "Remove all" : "Share all");
        if (sharedMode) {
            styleButton(actionButton, Color.rgb(69, 26, 26), Color.rgb(252, 165, 165), Color.rgb(127, 29, 29), 13);
            setButtonIcon(actionButton, R.drawable.ic_close, Color.rgb(252, 165, 165), 18);
        } else {
            styleButton(actionButton, Color.rgb(12, 25, 31), Color.rgb(61, 198, 190), Color.rgb(38, 60, 69), 13);
            setButtonIcon(actionButton, R.drawable.ic_share, Color.rgb(61, 198, 190), 18);
        }
        actionButton.setOnClickListener(v -> {
            if (sharedMode) {
                removeSharedFolder(group);
            } else {
                shareAvailableFolder(group);
            }
        });
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(dp(108), dp(36));
        actionParams.setMargins(dp(10), 0, 0, 0);
        row.addView(actionButton, actionParams);

        return row;
    }

    private void toggleFolder(FileFolderGroup group, boolean sharedMode) {
        String key = folderExpansionKey(sharedMode, group.key);
        if (!expandedFileFolders.remove(key)) {
            expandedFileFolders.add(key);
        }
        renderFiles();
    }

    private String folderExpansionKey(boolean sharedMode, String folderKey) {
        return (sharedMode ? "shared" : "share") + ":" + activeFileCategory + ":" + folderKey;
    }

    private void shareAvailableFolder(FileFolderGroup group) {
        List<Uri> uris = new ArrayList<>();
        for (SharedFile file : group.files) {
            uris.add(file.uri);
        }
        activeWebRepository().addFiles(uris);
        availableFiles.removeAll(group.files);
        expandedFileFolders.remove(folderExpansionKey(false, group.key));
        renderFiles();
    }

    private void removeSharedFolder(FileFolderGroup group) {
        List<Uri> uris = new ArrayList<>();
        for (SharedFile file : group.files) {
            uris.add(file.uri);
            if (!containsUri(availableFiles, file.uri)) {
                availableFiles.add(file);
            }
        }
        activeWebRepository().removeFiles(uris);
        expandedFileFolders.remove(folderExpansionKey(true, group.key));
        renderFiles();
    }

    private List<SharedFile> filterFiles(List<SharedFile> files) {
        List<SharedFile> matches = new ArrayList<>();
        for (SharedFile file : files) {
            if (!matchesActiveFileCategory(file)) {
                continue;
            }
            if (matchesFileSearch(file)) {
                matches.add(file);
            }
        }
        return matches;
    }

    private boolean matchesFileSearch(SharedFile file) {
        return matchesFileSearch(file, fileSearchQuery);
    }

    private boolean matchesFileSearch(SharedFile file, String rawQuery) {
        String query = TextUtils.isEmpty(rawQuery) ? "" : rawQuery.toLowerCase(Locale.US);
        if (TextUtils.isEmpty(query)) {
            return true;
        }
        String searchable = (file.name == null ? "" : file.name)
                + " " + file.parentFolderLabel()
                + " " + file.mediaLabel()
                + " " + file.category()
                + " " + file.effectiveMimeType()
                + " " + fileFormatLabel(file);
        return searchable.toLowerCase(Locale.US).contains(query);
    }

    private boolean matchesActiveFileCategory(SharedFile file) {
        return matchesFileCategory(file, activeFileCategory);
    }

    private boolean matchesFileCategory(SharedFile file, String category) {
        if (FILE_CATEGORY_VIDEOS.equals(category)) {
            return file.isVideo();
        }
        if (FILE_CATEGORY_SONGS.equals(category)) {
            return file.isAudio();
        }
        if (FILE_CATEGORY_COMPRESSED.equals(category)) {
            return file.isCompressed();
        }
        return !file.isVideo() && !file.isAudio() && !file.isCompressed();
    }

    private void selectFileCategory(String category) {
        if (TextUtils.equals(activeFileCategory, category)) {
            updateFileCategoryTabs();
            showFileCategoryPopover(category);
            return;
        }
        saveActiveFileSearchQuery();
        activeFileCategory = category;
        fileSearchQuery = fileSearchQueryForCategory(category);
        syncFileSearchInputToActiveCategory();
        visibleAvailableFileLimit = FILE_RENDER_PAGE_SIZE;
        renderFiles();
        mainHandler.post(() -> showFileCategoryPopover(category));
    }

    private void saveActiveFileSearchQuery() {
        if (fileSearchInput != null) {
            fileSearchQuery = fileSearchInput.getText().toString().trim();
        }
        fileSearchQueries.put(activeFileCategory, fileSearchQuery);
    }

    private String fileSearchQueryForCategory(String category) {
        String query = fileSearchQueries.get(category);
        return query == null ? "" : query;
    }

    private void syncFileSearchInputToActiveCategory() {
        if (fileSearchInput == null) {
            return;
        }
        suppressFileSearchChange = true;
        fileSearchInput.setText(fileSearchQuery);
        fileSearchInput.setSelection(fileSearchInput.length());
        fileSearchInput.setHint(fileSearchHint());
        suppressFileSearchChange = false;
    }

    private String fileSearchHint() {
        if (FILE_CATEGORY_VIDEOS.equals(activeFileCategory)) {
            return "Search videos";
        }
        if (FILE_CATEGORY_SONGS.equals(activeFileCategory)) {
            return "Search songs";
        }
        if (FILE_CATEGORY_COMPRESSED.equals(activeFileCategory)) {
            return "Search compressed files";
        }
        return "Search files";
    }

    private String emptyFileCategoryMessage() {
        if (!TextUtils.isEmpty(fileSearchQuery)) {
            return "No matches.";
        }
        if (FILE_CATEGORY_VIDEOS.equals(activeFileCategory)) {
            return "No videos.";
        }
        if (FILE_CATEGORY_SONGS.equals(activeFileCategory)) {
            return "No songs.";
        }
        if (FILE_CATEGORY_COMPRESSED.equals(activeFileCategory)) {
            return "No compressed files.";
        }
        return "No files.";
    }

    private void addLoadMoreButton(int visibleCount, int totalCount) {
        addLoadMoreButton(visibleCount, totalCount, "");
    }

    private void addLoadMoreButton(int visibleCount, int totalCount, String unitLabel) {
        if (visibleCount < totalCount) {
            int remaining = totalCount - visibleCount;
            String label = TextUtils.isEmpty(unitLabel) ? "More" : "More " + unitLabel;
            Button loadMoreButton = button(label + " (" + remaining + ")");
            styleButton(loadMoreButton, Color.rgb(12, 25, 31), Color.rgb(61, 198, 190), Color.rgb(38, 60, 69), 16);
            loadMoreButton.setOnClickListener(v -> {
                visibleAvailableFileLimit += FILE_RENDER_PAGE_SIZE;
                renderFiles();
            });
            fileList.addView(loadMoreButton, fullWidthWithMargins(0, dp(4), 0, 0, dp(50)));
        }
    }

    private void renderModeTabs() {
        if (mobileSharePanel == null || webSharePanel == null || mobileTabButton == null || webTabButton == null) {
            return;
        }
        mobileSharePanel.setVisibility(showWebShareTab ? View.GONE : View.VISIBLE);
        webSharePanel.setVisibility(showWebShareTab ? View.VISIBLE : View.GONE);
        renderWebShareFlowTabs();

        styleButton(mobileTabButton,
                showWebShareTab ? Color.rgb(12, 25, 31) : Color.rgb(61, 198, 190),
                showWebShareTab ? Color.rgb(148, 163, 184) : Color.rgb(4, 14, 16),
                showWebShareTab ? Color.rgb(38, 60, 69) : 0,
                14);
        setButtonIcon(mobileTabButton,
                R.drawable.ic_share,
                showWebShareTab ? Color.rgb(148, 163, 184) : Color.rgb(4, 14, 16));

        styleButton(webTabButton,
                showWebShareTab ? Color.rgb(61, 198, 190) : Color.rgb(12, 25, 31),
                showWebShareTab ? Color.rgb(4, 14, 16) : Color.rgb(148, 163, 184),
                showWebShareTab ? 0 : Color.rgb(38, 60, 69),
                14);
        setButtonIcon(webTabButton,
                R.drawable.ic_lock,
                showWebShareTab ? Color.rgb(4, 14, 16) : Color.rgb(148, 163, 184));
    }

    private void renderWebShareFlowTabs() {
        if (localShareFlowPanel == null
                || liveShareFlowPanel == null
                || localWebFlowButton == null
                || liveWebFlowButton == null) {
            return;
        }
        localShareFlowPanel.setVisibility(showLiveWebShareTab ? View.GONE : View.VISIBLE);
        liveShareFlowPanel.setVisibility(showLiveWebShareTab ? View.VISIBLE : View.GONE);

        styleButton(
                localWebFlowButton,
                showLiveWebShareTab ? Color.rgb(12, 25, 31) : Color.rgb(61, 198, 190),
                showLiveWebShareTab ? Color.rgb(148, 163, 184) : Color.rgb(4, 14, 16),
                showLiveWebShareTab ? Color.rgb(38, 60, 69) : 0,
                14
        );
        setButtonIcon(
                localWebFlowButton,
                R.drawable.ic_wifi,
                showLiveWebShareTab ? Color.rgb(148, 163, 184) : Color.rgb(4, 14, 16)
        );

        styleButton(
                liveWebFlowButton,
                showLiveWebShareTab ? Color.rgb(255, 170, 96) : Color.rgb(12, 25, 31),
                showLiveWebShareTab ? Color.rgb(29, 16, 5) : Color.rgb(148, 163, 184),
                showLiveWebShareTab ? 0 : Color.rgb(38, 60, 69),
                14
        );
        setButtonIcon(
                liveWebFlowButton,
                R.drawable.ic_share,
                showLiveWebShareTab ? Color.rgb(29, 16, 5) : Color.rgb(148, 163, 184)
        );
        updateLibrarySummary();
        renderWebVisitorStatus();
    }

    private void showAppMenu() {
        if (menuButton == null) {
            return;
        }
        if (appMenuPopover != null && appMenuPopover.isShowing()) {
            appMenuPopover.dismiss();
            return;
        }
        LinearLayout menu = new LinearLayout(this);
        menu.setOrientation(LinearLayout.VERTICAL);
        menu.setPadding(dp(10), dp(10), dp(10), dp(10));
        menu.setBackground(gradient(
                new int[]{Color.rgb(12, 25, 31), Color.rgb(15, 32, 39)},
                dp(22),
                Color.rgb(38, 60, 69),
                dp(1),
                GradientDrawable.Orientation.TOP_BOTTOM
        ));
        menu.addView(appMenuItem("Nearby Share", R.drawable.ic_share, Color.rgb(61, 198, 190), () -> {
            showWebShareTab = false;
            renderModeTabs();
        }));
        menu.addView(appMenuItem("Web Share", R.drawable.ic_lock, Color.rgb(61, 198, 190), () -> {
            showWebShareTab = true;
            renderModeTabs();
        }));
        menu.addView(appMenuItem("Refresh files", R.drawable.ic_refresh, Color.rgb(22, 163, 74), () -> {
            showWebShareTab = true;
            renderModeTabs();
            refreshDiscoveredFiles(true);
        }));
        boolean running = activePort > 0;
        menu.addView(appMenuItem(
                running ? "Stop server" : "Start server",
                running ? R.drawable.ic_stop : R.drawable.ic_play_arrow,
                running ? Color.rgb(220, 38, 38) : Color.rgb(37, 99, 235),
                () -> {
                    showWebShareTab = true;
                    showLiveWebShareTab = false;
                    loadCachedDiscoveredFiles();
                    renderModeTabs();
                    if (activePort > 0) {
                        confirmStopLocalSharing();
                    } else {
                        startSharing();
                    }
                }
        ));
        appMenuPopover = new PopupWindow(
                menu,
                Math.min(getResources().getDisplayMetrics().widthPixels - dp(44), dp(260)),
                LinearLayout.LayoutParams.WRAP_CONTENT,
                true
        );
        appMenuPopover.setOutsideTouchable(true);
        appMenuPopover.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        appMenuPopover.setElevation(dp(10));
        try {
            appMenuPopover.showAsDropDown(menuButton, 0, dp(6));
        } catch (Exception e) {
            Toast.makeText(this, "Menu unavailable.", Toast.LENGTH_SHORT).show();
        }
    }

    private Button appMenuItem(String label, int iconId, int color, Runnable action) {
        Button item = button(label);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setTooltipText(label);
        item.setContentDescription(label);
        item.setPadding(dp(12), 0, dp(12), 0);
        styleButton(item, Color.TRANSPARENT, Color.rgb(241, 248, 246), 0, 14);
        setButtonIcon(item, iconId, color, 22);
        item.setOnClickListener(v -> {
            if (appMenuPopover != null) {
                appMenuPopover.dismiss();
            }
            action.run();
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(44)
        );
        params.setMargins(0, 0, 0, dp(6));
        item.setLayoutParams(params);
        return item;
    }

    private void updateFilterButtons() {
        updateLibrarySummary();
        if (shareableFilterButton == null || sharedFilterButton == null) {
            return;
        }
        shareableFilterButton.setEnabled(showSharedFilter);
        sharedFilterButton.setEnabled(!showSharedFilter);
        int shareCount = categorySearchCount(availableFiles, activeFileCategory);
        int sharedCount = categorySearchCount(activeWebRepository().getFiles(), activeFileCategory);
        shareableFilterButton.setSingleLine(true);
        shareableFilterButton.setMaxLines(1);
        shareableFilterButton.setTextSize(scaledSp(14));
        sharedFilterButton.setSingleLine(true);
        sharedFilterButton.setMaxLines(1);
        sharedFilterButton.setTextSize(scaledSp(14));
        shareableFilterButton.setText("Share");
        sharedFilterButton.setText("Shared");
        String label = fileCategoryLabel(activeFileCategory);
        shareableFilterButton.setTooltipText(label + " ready to share: " + shareCount);
        sharedFilterButton.setTooltipText(label + " already shared: " + sharedCount);
        shareableFilterButton.setContentDescription(label + " ready to share: " + shareCount);
        sharedFilterButton.setContentDescription(label + " already shared: " + sharedCount);
        styleButton(shareableFilterButton,
                showSharedFilter ? Color.rgb(12, 25, 31) : Color.rgb(61, 198, 190),
                showSharedFilter ? Color.rgb(148, 163, 184) : Color.rgb(4, 14, 16),
                showSharedFilter ? Color.rgb(38, 60, 69) : 0,
                14);
        setButtonIcon(shareableFilterButton,
                R.drawable.ic_share,
                showSharedFilter ? Color.rgb(148, 163, 184) : Color.rgb(4, 14, 16));
        styleButton(sharedFilterButton,
                showSharedFilter ? Color.rgb(61, 198, 190) : Color.rgb(12, 25, 31),
                showSharedFilter ? Color.rgb(4, 14, 16) : Color.rgb(148, 163, 184),
                showSharedFilter ? 0 : Color.rgb(38, 60, 69),
                14);
        setButtonIcon(sharedFilterButton,
                R.drawable.ic_done,
                showSharedFilter ? Color.rgb(4, 14, 16) : Color.rgb(148, 163, 184));
        updateFilterBadge(
                shareableFilterBadgeView,
                shareCount,
                showSharedFilter ? Color.rgb(30, 41, 59) : Color.rgb(4, 14, 16),
                showSharedFilter ? Color.rgb(203, 213, 225) : Color.rgb(61, 198, 190),
                showSharedFilter ? 0 : Color.rgb(61, 198, 190)
        );
        updateFilterBadge(
                sharedFilterBadgeView,
                sharedCount,
                showSharedFilter ? Color.rgb(4, 14, 16) : Color.rgb(20, 83, 45),
                showSharedFilter ? Color.rgb(61, 198, 190) : Color.rgb(187, 247, 208),
                showSharedFilter ? Color.rgb(61, 198, 190) : 0
        );
    }

    private void updateFilteredCountView() {
        if (fileFilteredCountView == null) {
            return;
        }
        int shareCount = categorySearchCount(availableFiles, activeFileCategory);
        int sharedCount = categorySearchCount(activeWebRepository().getFiles(), activeFileCategory);
        int currentCount = showSharedFilter ? sharedCount : shareCount;
        int folderCount = fileFolderGroups(filterFiles(showSharedFilter ? activeWebRepository().getFiles() : availableFiles)).size();
        String mode = showSharedFilter ? "Shared" : "Share";
        String label = fileCategoryLabel(activeFileCategory);
        String query = TextUtils.isEmpty(fileSearchQuery)
                ? ""
                : " for \"" + compactQueryLabel(fileSearchQuery) + "\"";
        String resultLabel = currentCount == 1 ? "result" : "results";
        String folderLabel = folderCount == 1 ? "folder" : "folders";
        String text = mode + " · " + currentCount + " " + resultLabel + " in " + folderCount + " " + folderLabel + query
                + " · " + shareCount + "/" + sharedCount;
        fileFilteredCountView.setText(text);
        fileFilteredCountView.setTooltipText("Filtered count. Share " + shareCount + ", Shared " + sharedCount + ".");
        fileFilteredCountView.setContentDescription(text);
    }

    private String compactQueryLabel(String query) {
        String value = query == null ? "" : query.trim();
        return value.length() > 18 ? value.substring(0, 18) + "..." : value;
    }

    private void updateFileCategoryTabs() {
        if (videosCategoryButton == null
                || filesCategoryButton == null
                || songsCategoryButton == null
                || compressedCategoryButton == null) {
            return;
        }
        configureFileCategoryButton(
                videosCategoryButton,
                FILE_CATEGORY_VIDEOS,
                "Videos",
                R.drawable.ic_movie,
                Color.rgb(255, 138, 86),
                categorySearchCount(availableFiles, FILE_CATEGORY_VIDEOS),
                categorySearchCount(activeWebRepository().getFiles(), FILE_CATEGORY_VIDEOS)
        );
        configureFileCategoryButton(
                filesCategoryButton,
                FILE_CATEGORY_FILES,
                "Files",
                R.drawable.ic_description,
                Color.rgb(126, 149, 255),
                categorySearchCount(availableFiles, FILE_CATEGORY_FILES),
                categorySearchCount(activeWebRepository().getFiles(), FILE_CATEGORY_FILES)
        );
        configureFileCategoryButton(
                songsCategoryButton,
                FILE_CATEGORY_SONGS,
                "Songs",
                R.drawable.ic_music_note,
                Color.rgb(61, 198, 190),
                categorySearchCount(availableFiles, FILE_CATEGORY_SONGS),
                categorySearchCount(activeWebRepository().getFiles(), FILE_CATEGORY_SONGS)
        );
        configureFileCategoryButton(
                compressedCategoryButton,
                FILE_CATEGORY_COMPRESSED,
                "Compressed",
                R.drawable.ic_archive,
                Color.rgb(245, 158, 11),
                categorySearchCount(availableFiles, FILE_CATEGORY_COMPRESSED),
                categorySearchCount(activeWebRepository().getFiles(), FILE_CATEGORY_COMPRESSED)
        );
    }

    private void configureFileCategoryButton(
            Button button,
            String category,
            String label,
            int iconId,
            int accentColor,
            int shareCount,
            int sharedCount
    ) {
        boolean active = TextUtils.equals(activeFileCategory, category);
        boolean landscape = isLandscape();
        button.setSingleLine(true);
        button.setMaxLines(1);
        button.setTextSize(scaledSp(14));
        button.setText(landscape ? label : "");
        button.setContentDescription(label);
        String tooltip = label + " · Share " + shareCount + " · Shared " + sharedCount;
        button.setTooltipText(tooltip);
        button.setOnLongClickListener(v -> {
            showFileCategoryPopover(category);
            return true;
        });
        button.setMinWidth(0);
        button.setPadding(dp(8), 0, dp(8), 0);
        styleButton(
                button,
                active ? accentColor : Color.rgb(12, 25, 31),
                active ? Color.rgb(4, 14, 16) : accentColor,
                active ? 0 : Color.rgb(38, 60, 69),
                14
        );
        if (landscape) {
            button.setCompoundDrawablePadding(0);
            button.setCompoundDrawables(null, null, null, null);
        } else {
            setButtonCenteredIcon(button, iconId, active ? Color.rgb(4, 14, 16) : accentColor, 24);
        }
    }

    private void showFileCategoryPopover(String category) {
        View anchor = fileCategoryButtonFor(category);
        if (anchor == null) {
            return;
        }
        if (fileCategoryPopover != null) {
            fileCategoryPopover.dismiss();
        }
        String label = fileCategoryLabel(category);
        int shareCount = categorySearchCount(availableFiles, category);
        int sharedCount = categorySearchCount(activeWebRepository().getFiles(), category);
        TextView bubble = text(label + " · Share " + shareCount + " · Shared " + sharedCount, 12, Color.WHITE, true);
        bubble.setGravity(Gravity.CENTER);
        bubble.setPadding(dp(12), dp(8), dp(12), dp(8));
        bubble.setBackground(rounded(Color.rgb(15, 32, 39), dp(16), Color.rgb(38, 60, 69), dp(1)));
        fileCategoryPopover = new PopupWindow(
                bubble,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                false
        );
        fileCategoryPopover.setOutsideTouchable(true);
        fileCategoryPopover.setElevation(dp(6));
        try {
            fileCategoryPopover.showAsDropDown(anchor, 0, dp(6));
            mainHandler.postDelayed(() -> {
                if (fileCategoryPopover != null) {
                    fileCategoryPopover.dismiss();
                }
            }, 1400);
        } catch (Exception e) {
            Toast.makeText(this, label + " · Share " + shareCount + " · Shared " + sharedCount, Toast.LENGTH_SHORT).show();
        }
    }

    private View fileCategoryButtonFor(String category) {
        if (FILE_CATEGORY_VIDEOS.equals(category)) {
            return videosCategoryButton;
        }
        if (FILE_CATEGORY_SONGS.equals(category)) {
            return songsCategoryButton;
        }
        if (FILE_CATEGORY_COMPRESSED.equals(category)) {
            return compressedCategoryButton;
        }
        return filesCategoryButton;
    }

    private String fileCategoryLabel(String category) {
        if (FILE_CATEGORY_VIDEOS.equals(category)) {
            return "Videos";
        }
        if (FILE_CATEGORY_SONGS.equals(category)) {
            return "Songs";
        }
        if (FILE_CATEGORY_COMPRESSED.equals(category)) {
            return "Compressed";
        }
        return "Files";
    }

    private int categoryCount(List<SharedFile> files, String category) {
        int count = 0;
        for (SharedFile file : files) {
            if (matchesFileCategory(file, category)) {
                count++;
            }
        }
        return count;
    }

    private int categorySearchCount(List<SharedFile> files, String category) {
        String query = fileSearchQueryForCategory(category);
        int count = 0;
        for (SharedFile file : files) {
            if (matchesFileCategory(file, category) && matchesFileSearch(file, query)) {
                count++;
            }
        }
        return count;
    }

    private boolean isLandscape() {
        return getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    private LinearLayout fileRow(SharedFile file, String actionLabel, Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(12), dp(12), dp(12));
        row.setBackground(rowBackground());
        row.setElevation(dp(2));
        LinearLayout.LayoutParams rowParams = fullWidthWithMargins(0, 0, 0, dp(10));
        row.setLayoutParams(rowParams);

        LinearLayout badge = formatBadge(file);
        badge.setBackground(glassButtonBackground(fileAccent(file), 0, dp(16)));
        badge.setElevation(dp(2));
        LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(dp(50), dp(46));
        badgeParams.setMargins(0, 0, dp(12), 0);
        row.addView(badge, badgeParams);

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        row.addView(copy, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView name = text(file.name, 15, Color.rgb(241, 248, 246), true);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        copy.addView(name);

        TextView details = text(file.mediaLabel() + " · " + formatSize(file.size), 13, Color.rgb(148, 163, 184), false);
        details.setSingleLine(true);
        details.setEllipsize(TextUtils.TruncateAt.END);
        copy.addView(details);

        Button actionButton = button(actionLabel);
        if ("Remove".equals(actionLabel)) {
            styleButton(actionButton, Color.rgb(69, 26, 26), Color.rgb(252, 165, 165), Color.rgb(127, 29, 29), 14);
            setButtonIcon(actionButton, R.drawable.ic_close, Color.rgb(252, 165, 165));
            actionButton.setTooltipText("Remove this file from " + activeWebShareName());
        } else {
            styleButton(actionButton, Color.rgb(12, 25, 31), Color.rgb(61, 198, 190), Color.rgb(38, 60, 69), 14);
            setButtonIcon(actionButton, R.drawable.ic_share, Color.rgb(61, 198, 190));
            actionButton.setTooltipText("Add this file to " + activeWebShareName());
        }
        actionButton.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(dp(84), dp(38));
        actionParams.setMargins(dp(12), 0, 0, 0);
        row.addView(actionButton, actionParams);
        return row;
    }

    private LinearLayout formatBadge(SharedFile file) {
        LinearLayout badge = new LinearLayout(this);
        badge.setOrientation(LinearLayout.VERTICAL);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(6), dp(6), dp(6), dp(5));
        badge.setTooltipText(fileFormatLabel(file) + " " + file.mediaLabel());

        ImageView icon = new ImageView(this);
        icon.setImageDrawable(tintedIcon(fileIcon(file), Color.WHITE));
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(22), dp(22));
        badge.addView(icon, iconParams);

        TextView format = text(fileFormatLabel(file), 10, Color.WHITE, true);
        format.setSingleLine(true);
        format.setGravity(Gravity.CENTER);
        format.setIncludeFontPadding(false);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        textParams.setMargins(0, dp(3), 0, 0);
        badge.addView(format, textParams);
        return badge;
    }

    private String fileFormatLabel(SharedFile file) {
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

    private boolean containsUri(List<SharedFile> files, Uri uri) {
        for (SharedFile file : files) {
            if (file.uri.equals(uri)) {
                return true;
            }
        }
        return false;
    }

    private void renderStatus() {
        boolean running = activePort > 0;
        String url = currentUrl();
        statusView.setText(running ? "Running." : "Stopped.");
        if (running && TextUtils.isEmpty(url)) {
            addressView.setText("No local IP.");
        } else {
            addressView.setText(running ? url : "Ready.");
        }
        lastRenderedLocalUrl = running ? url : "";
        renderWebQr(localWebQrCard, localWebQrImageView, running ? url : "");
        serverButton.setText(running ? "Stop server" : "Start server");
        if (running) {
            styleButton(serverButton, Color.rgb(69, 26, 26), Color.rgb(252, 165, 165), Color.rgb(127, 29, 29), 16);
            setButtonIcon(serverButton, R.drawable.ic_stop, Color.rgb(252, 165, 165));
            serverButton.setTooltipText("Stop sharing files on the local network");
        } else {
            styleButton(serverButton, Color.rgb(61, 198, 190), Color.rgb(4, 14, 16), 0, 16);
            setButtonIcon(serverButton, R.drawable.ic_play_arrow, Color.rgb(4, 14, 16));
            serverButton.setTooltipText("Start sharing selected files on the local network");
        }
    }

    private void renderRemoteStatus(String message) {
        if (remoteStatusView == null || remoteAddressView == null || remoteShareButton == null) {
            return;
        }
        String status = message;
        if (TextUtils.isEmpty(status)) {
            status = remoteRelayRunning
                    ? (remoteRelayConnected ? "Global link online." : "Connecting to relay...")
                    : "Global link off.";
        }
        if (remoteRelayRunning && remoteRelayConnected && TextUtils.isEmpty(remoteShortShareUrl) && !TextUtils.isEmpty(remoteFullShareUrl)) {
            status = TextUtils.isEmpty(remoteTinyUrlError)
                    ? "Tiny URL unavailable. Using relay link."
                    : "Tiny URL unavailable. Check relay setup.";
        }
        remoteStatusView.setText(status);
        boolean remoteLinkReady = remoteRelayRunning && remoteRelayConnected && !TextUtils.isEmpty(remoteShareUrl);
        if (remoteLinkReady) {
            remoteAddressView.setText(remoteShareUrl);
        } else if (remoteRelayRunning) {
            remoteAddressView.setText("Creating global link...");
        } else {
            remoteAddressView.setText("Global link off.");
        }
        renderWebQr(
                remoteWebQrCard,
                remoteWebQrImageView,
                remoteLinkReady ? remoteShareUrl : ""
        );
        remoteShareButton.setText(remoteRelayRunning ? "Stop global link" : "Start global link");
        if (remoteRelayRunning) {
            int fill = remoteRelayConnected ? Color.rgb(29, 55, 38) : Color.rgb(63, 45, 22);
            int textColor = remoteRelayConnected ? Color.rgb(187, 247, 208) : Color.rgb(254, 215, 170);
            int stroke = remoteRelayConnected ? Color.rgb(34, 197, 94) : Color.rgb(180, 83, 9);
            styleButton(remoteShareButton, fill, textColor, stroke, 16);
            setButtonIcon(remoteShareButton, R.drawable.ic_stop, textColor);
            remoteShareButton.setTooltipText("Stop the internet relay link");
        } else {
            styleButton(remoteShareButton, Color.rgb(255, 170, 96), Color.rgb(29, 16, 5), 0, 16);
            setButtonIcon(remoteShareButton, R.drawable.ic_share, Color.rgb(29, 16, 5));
            remoteShareButton.setTooltipText("Create an internet relay link for shared files");
        }
    }

    private String currentUrl() {
        String ip = localIp();
        if (activePort <= 0 || ip == null) {
            return "";
        }
        return "http://" + ip + ":" + activePort + "/";
    }

    private String localIp() {
        String connectedLanIp = activeLanNetworkIp();
        if (connectedLanIp != null) {
            return connectedLanIp;
        }
        String interfaceLanIp = interfaceLanIp();
        if (interfaceLanIp != null) {
            return interfaceLanIp;
        }
        return null;
    }

    private String activeLanNetworkIp() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (connectivityManager == null) {
            return null;
        }

        Network activeNetwork = connectivityManager.getActiveNetwork();
        if (activeNetwork == null) {
            return null;
        }

        NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(activeNetwork);
        if (capabilities == null
                || (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                && !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))) {
            return null;
        }

        LinkProperties properties = connectivityManager.getLinkProperties(activeNetwork);
        if (properties == null) {
            return null;
        }

        for (LinkAddress linkAddress : properties.getLinkAddresses()) {
            java.net.InetAddress address = linkAddress.getAddress();
            if (isUsableLanAddress(address)) {
                return address.getHostAddress();
            }
        }
        return null;
    }

    private String interfaceLanIp() {
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            String wifiAddress = interfaceLanIpForPriority(interfaces, true);
            if (wifiAddress != null) {
                return wifiAddress;
            }
            return interfaceLanIpForPriority(interfaces, false);
        } catch (Exception ignored) {
        }
        return null;
    }

    private String interfaceLanIpForPriority(List<NetworkInterface> interfaces, boolean wifiOnly) throws java.net.SocketException {
        for (NetworkInterface networkInterface : interfaces) {
            String name = networkInterface.getName();
            if (!networkInterface.isUp()
                    || networkInterface.isLoopback()
                    || !isLanInterface(name)
                    || (wifiOnly && !isWifiLikeInterface(name))) {
                continue;
            }
            for (java.net.InetAddress address : Collections.list(networkInterface.getInetAddresses())) {
                if (isUsableLanAddress(address)) {
                    return address.getHostAddress();
                }
            }
        }
        return null;
    }

    private boolean isLanInterface(String name) {
        if (name == null) {
            return false;
        }
        String lowerName = name.toLowerCase(Locale.US);
        return lowerName.startsWith("wlan")
                || lowerName.startsWith("swlan")
                || lowerName.startsWith("ap")
                || lowerName.startsWith("eth");
    }

    private boolean isWifiLikeInterface(String name) {
        if (name == null) {
            return false;
        }
        String lowerName = name.toLowerCase(Locale.US);
        return lowerName.startsWith("wlan")
                || lowerName.startsWith("swlan")
                || lowerName.startsWith("ap");
    }

    private boolean isUsableLanAddress(java.net.InetAddress address) {
        if (!(address instanceof Inet4Address)
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isMulticastAddress()) {
            return false;
        }

        byte[] bytes = address.getAddress();
        int first = bytes[0] & 0xff;
        int second = bytes[1] & 0xff;
        return first == 10
                || (first == 172 && second >= 16 && second <= 31)
                || (first == 192 && second == 168);
    }

    private LinearLayout card(int color) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackground(cardBackground(color, dp(22)));
        layout.setElevation(dp(3));
        return layout;
    }

    private LinearLayout roleCard(
            String title,
            String description,
            int iconId,
            int accentColor,
            int backgroundColor,
            int strokeColor
    ) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(10), dp(10), dp(10));
        row.setBackground(gradient(
                new int[]{Color.rgb(12, 25, 31), Color.rgb(15, 32, 39)},
                dp(18),
                blendColor(strokeColor, Color.rgb(38, 60, 69), 0.45f),
                dp(1),
                GradientDrawable.Orientation.LEFT_RIGHT
        ));
        row.setClickable(true);
        row.setFocusable(true);
        row.setElevation(dp(2));
        row.setTooltipText(title);
        row.setContentDescription(title + ". " + description);

        LinearLayout iconShell = new LinearLayout(this);
        iconShell.setGravity(Gravity.CENTER);
        iconShell.setBackground(rounded(backgroundColor, dp(16), strokeColor, dp(1)));
        ImageView icon = new ImageView(this);
        icon.setImageDrawable(tintedIcon(iconId, accentColor, 28));
        iconShell.addView(icon, new LinearLayout.LayoutParams(dp(30), dp(30)));
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(52), dp(52));
        iconParams.setMargins(0, 0, dp(12), 0);
        row.addView(iconShell, iconParams);

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        row.addView(copy, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView titleView = text(title, 15, Color.rgb(241, 248, 246), true);
        titleView.setIncludeFontPadding(false);
        copy.addView(titleView);

        TextView descriptionView = text(description, 12, Color.rgb(148, 163, 184), false);
        descriptionView.setPadding(0, dp(5), 0, 0);
        copy.addView(descriptionView);

        ImageView arrow = new ImageView(this);
        arrow.setImageDrawable(tintedIcon(R.drawable.ic_chevron_right, accentColor, 22));
        LinearLayout.LayoutParams arrowParams = new LinearLayout.LayoutParams(dp(28), dp(28));
        arrowParams.setMargins(dp(8), 0, 0, 0);
        row.addView(arrow, arrowParams);
        return row;
    }

    private LinearLayout currentNetworkCard() {
        String ip = localIp();
        boolean connected = !TextUtils.isEmpty(ip);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(12), dp(12), dp(12));
        row.setBackground(rounded(Color.rgb(12, 25, 31), dp(18), Color.rgb(38, 60, 69), dp(1)));
        row.setElevation(dp(2));

        ImageView icon = new ImageView(this);
        icon.setImageDrawable(tintedIcon(R.drawable.ic_wifi, Color.rgb(37, 99, 235), 28));
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(34), dp(34));
        iconParams.setMargins(0, 0, dp(12), 0);
        row.addView(icon, iconParams);

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        row.addView(copy, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView label = text("Current Wi-Fi", 11, Color.rgb(148, 163, 184), true);
        label.setIncludeFontPadding(false);
        copy.addView(label);

        TextView name = text(connected ? ip : "No local network", 15, Color.rgb(241, 248, 246), true);
        name.setPadding(0, dp(3), 0, 0);
        copy.addView(name);

        TextView note = text("Both devices must be on the same Wi-Fi.", 11, Color.rgb(148, 163, 184), false);
        note.setPadding(0, dp(5), 0, 0);
        copy.addView(note);

        TextView pill = text(connected ? "Connected" : "Offline", 11,
                connected ? Color.rgb(134, 239, 172) : Color.rgb(252, 165, 165),
                true);
        pill.setGravity(Gravity.CENTER);
        pill.setPadding(dp(10), 0, dp(10), 0);
        pill.setBackground(rounded(
                connected ? Color.rgb(20, 83, 45) : Color.rgb(69, 26, 26),
                dp(14),
                connected ? Color.rgb(34, 197, 94) : Color.rgb(248, 113, 113),
                dp(1)
        ));
        row.addView(pill, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(28)));
        return row;
    }

    private LinearLayout webQrCard(String title, String subtitle, int accentColor, boolean remote) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(12), dp(12), dp(12));
        row.setBackground(gradient(
                new int[]{Color.rgb(12, 25, 31), Color.rgb(15, 32, 39)},
                dp(18),
                blendColor(accentColor, Color.rgb(38, 60, 69), 0.55f),
                dp(1),
                GradientDrawable.Orientation.LEFT_RIGHT
        ));
        row.setElevation(dp(2));

        ImageView qr = new ImageView(this);
        qr.setAdjustViewBounds(true);
        qr.setScaleType(ImageView.ScaleType.FIT_CENTER);
        qr.setBackground(rounded(Color.WHITE, dp(16), Color.rgb(226, 232, 240), dp(1)));
        qr.setPadding(dp(10), dp(10), dp(10), dp(10));
        qr.setContentDescription(title);
        int qrSize = webQrImageCardSize();
        LinearLayout.LayoutParams qrParams = new LinearLayout.LayoutParams(qrSize, qrSize);
        qrParams.setMargins(0, 0, dp(12), 0);
        row.addView(qr, qrParams);

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        row.addView(copy, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView titleView = text(title, 15, Color.rgb(241, 248, 246), true);
        titleView.setIncludeFontPadding(false);
        copy.addView(titleView);

        TextView subtitleView = text(subtitle, 12, Color.rgb(148, 163, 184), false);
        subtitleView.setPadding(0, dp(5), 0, 0);
        copy.addView(subtitleView);

        TextView hint = text("Tap address to share link", 11, accentColor, true);
        hint.setPadding(0, dp(6), 0, 0);
        copy.addView(hint);

        if (remote) {
            remoteWebQrImageView = qr;
        } else {
            localWebQrImageView = qr;
        }
        return row;
    }

    private void renderWebQr(LinearLayout card, ImageView imageView, String payload) {
        if (card == null || imageView == null) {
            return;
        }
        if (TextUtils.isEmpty(payload)) {
            imageView.setImageBitmap(null);
            card.setVisibility(View.GONE);
            return;
        }
        try {
            int qrSize = webQrBitmapSize();
            Bitmap bitmap = new BarcodeEncoder().encodeBitmap(payload, BarcodeFormat.QR_CODE, qrSize, qrSize);
            imageView.setImageBitmap(bitmap);
            card.setVisibility(View.VISIBLE);
        } catch (WriterException e) {
            imageView.setImageBitmap(null);
            card.setVisibility(View.GONE);
        }
    }

    private LinearLayout featureCard(String title, String description, String status) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(14), dp(14), dp(14));
        row.setBackground(rowBackground());
        LinearLayout.LayoutParams rowParams = fullWidthWithMargins(0, 0, 0, dp(10));
        row.setLayoutParams(rowParams);

        ImageView icon = new ImageView(this);
        icon.setImageDrawable(tintedIcon(R.drawable.ic_share, Color.rgb(61, 198, 190)));
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(30), dp(30));
        iconParams.setMargins(0, 0, dp(12), 0);
        row.addView(icon, iconParams);

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        row.addView(copy, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView name = text(title, 16, Color.rgb(235, 244, 242), true);
        copy.addView(name);

        TextView detail = text(description, 13, Color.rgb(143, 164, 161), false);
        detail.setPadding(0, dp(3), 0, 0);
        copy.addView(detail);

        TextView pill = text(status, 12, Color.rgb(7, 12, 15), true);
        pill.setGravity(Gravity.CENTER);
        pill.setPadding(dp(10), 0, dp(10), 0);
        pill.setBackground(rounded(Color.rgb(255, 170, 96), dp(14), 0, 0));
        LinearLayout.LayoutParams pillParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(30));
        pillParams.setMargins(dp(10), 0, 0, 0);
        row.addView(pill, pillParams);
        return row;
    }

    private void applySafeAreaPadding(View view) {
        int left = dp(16);
        int top = dp(10) + systemDimension("status_bar_height", dp(28));
        int right = dp(16);
        int bottom = dp(12) + systemDimension("navigation_bar_height", dp(16));
        view.setPadding(left, top, right, bottom);
        view.setOnApplyWindowInsetsListener((target, insets) -> {
            int insetTop = Math.max(systemDimension("status_bar_height", dp(28)), insets.getSystemWindowInsetTop());
            int insetBottom = Math.max(systemDimension("navigation_bar_height", dp(16)), insets.getSystemWindowInsetBottom());
            target.setPadding(
                    left + insets.getSystemWindowInsetLeft(),
                    dp(10) + insetTop,
                    right + insets.getSystemWindowInsetRight(),
                    dp(12) + insetBottom
            );
            return insets;
        });
        view.requestApplyInsets();
    }

    private int systemDimension(String name, int fallback) {
        int resourceId = getResources().getIdentifier(name, "dimen", "android");
        if (resourceId <= 0) {
            return fallback;
        }
        return getResources().getDimensionPixelSize(resourceId);
    }

    private TextView emptyState(String value) {
        TextView view = text(value, 15, Color.rgb(148, 163, 184), false);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(18), dp(22), dp(18), dp(22));
        view.setBackground(rowBackground());
        return view;
    }

    private int fileIcon(SharedFile file) {
        if (file.isVideo()) {
            return R.drawable.ic_movie;
        }
        if (file.isAudio()) {
            return R.drawable.ic_music_note;
        }
        if (file.isCompressed()) {
            return R.drawable.ic_archive;
        }
        if (file.isDocument()) {
            return R.drawable.ic_description;
        }
        return R.drawable.ic_insert_drive_file;
    }

    private int fileAccent(SharedFile file) {
        if (file.isVideo()) {
            return Color.rgb(255, 138, 86);
        }
        if (file.isAudio()) {
            return Color.rgb(61, 198, 190);
        }
        if (file.isCompressed()) {
            return Color.rgb(245, 158, 11);
        }
        if (file.isDocument()) {
            return Color.rgb(126, 149, 255);
        }
        return Color.rgb(143, 164, 161);
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(scaledSp(sp));
        view.setTextColor(color);
        view.setIncludeFontPadding(true);
        view.setTypeface(Typeface.create(bold ? "sans-serif-medium" : "sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        return view;
    }

    private Button button(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(scaledSp(14));
        button.setAllCaps(false);
        button.setMinHeight(0);
        button.setMinWidth(0);
        button.setPadding(dp(12), 0, dp(12), 0);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        button.setLetterSpacing(0.02f);
        return button;
    }

    private TextView filterCountBadge() {
        TextView badge = text("0", 11, Color.WHITE, true);
        badge.setGravity(Gravity.CENTER);
        badge.setIncludeFontPadding(false);
        badge.setMinWidth(dp(24));
        badge.setPadding(dp(7), 0, dp(7), 0);
        badge.setClickable(false);
        badge.setFocusable(false);
        return badge;
    }

    private void updateFilterBadge(TextView badge, int count, int backgroundColor, int textColor, int strokeColor) {
        if (badge == null) {
            return;
        }
        badge.setText(formatBadgeCount(count));
        badge.setTextColor(textColor);
        badge.setBackground(rounded(backgroundColor, dp(11), strokeColor, dp(1)));
        badge.setElevation(dp(4));
    }

    private String formatBadgeCount(int count) {
        return count > 999 ? "999+" : String.valueOf(Math.max(0, count));
    }

    private float scaledSp(int sp) {
        return sp * responsiveScale(MIN_AUTO_TEXT_SCALE, MAX_AUTO_TEXT_SCALE);
    }

    private void setButtonIcon(Button button, int drawableId, int color) {
        setButtonIcon(button, drawableId, color, 22);
    }

    private void setButtonIcon(Button button, int drawableId, int color, int sizeDp) {
        button.setGravity(Gravity.CENTER);
        button.setCompoundDrawablePadding(dp(8));
        button.setCompoundDrawables(tintedIcon(drawableId, color, sizeDp), null, null, null);
    }

    private void setButtonTopIcon(Button button, int drawableId, int color, int sizeDp) {
        button.setGravity(Gravity.CENTER);
        button.setCompoundDrawablePadding(0);
        button.setCompoundDrawables(null, tintedIcon(drawableId, color, sizeDp), null, null);
    }

    private void setButtonCenteredIcon(Button button, int drawableId, int color, int sizeDp) {
        button.setGravity(Gravity.CENTER);
        button.setPadding(0, 0, 0, 0);
        button.setCompoundDrawablePadding(0);
        button.setCompoundDrawables(tintedIcon(drawableId, color, sizeDp), null, null, null);
    }

    private ImageButton iconButton(int drawableId, int iconColor, int backgroundColor, int strokeColor, String description, String tooltip) {
        ImageButton button = new ImageButton(this);
        button.setImageDrawable(tintedIcon(drawableId, iconColor));
        button.setBackground(glassButtonBackground(backgroundColor, strokeColor, dp(18)));
        button.setElevation(dp(3));
        button.setScaleType(ImageView.ScaleType.CENTER);
        button.setPadding(dp(14), dp(14), dp(14), dp(14));
        button.setContentDescription(description);
        button.setTooltipText(tooltip);
        return button;
    }

    private ImageButton smallIconButton(int drawableId, int iconColor, int backgroundColor, int strokeColor, String description, String tooltip) {
        ImageButton button = iconButton(drawableId, iconColor, backgroundColor, strokeColor, description, tooltip);
        button.setPadding(dp(9), dp(9), dp(9), dp(9));
        return button;
    }

    private Drawable tintedIcon(int drawableId, int color) {
        return tintedIcon(drawableId, color, 22);
    }

    private Drawable tintedIcon(int drawableId, int color, int sizeDp) {
        Drawable drawable = getDrawable(drawableId);
        if (drawable == null) {
            return null;
        }
        drawable = drawable.mutate();
        drawable.setTint(color);
        drawable.setBounds(0, 0, dp(sizeDp), dp(sizeDp));
        return drawable;
    }

    private void styleButton(Button button, int backgroundColor, int textColor, int strokeColor, int radiusDp) {
        button.setTextColor(textColor);
        button.setBackground(glassButtonBackground(backgroundColor, strokeColor, dp(radiusDp)));
        button.setElevation(dp(3));
    }

    private void styleRoleButton(
            Button button,
            int iconId,
            int iconColor,
            int textColor,
            int[] backgroundColors,
            int strokeColor
    ) {
        button.setTextColor(textColor);
        button.setTextSize(scaledSp(15));
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(8), 0, dp(8), 0);
        button.setBackground(gradient(
                backgroundColors,
                dp(22),
                strokeColor,
                dp(1),
                GradientDrawable.Orientation.TL_BR
        ));
        button.setElevation(dp(6));
        setButtonIcon(button, iconId, iconColor, 26);
    }

    private GradientDrawable rounded(int color, int radius, int strokeColor, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        if (strokeColor != 0 && strokeWidth > 0) {
            drawable.setStroke(strokeWidth, strokeColor);
        }
        return drawable;
    }

    private GradientDrawable appBackground() {
        return gradient(
                new int[]{
                        Color.rgb(6, 14, 18),
                        Color.rgb(8, 18, 23),
                        Color.rgb(4, 8, 10)
                },
                0,
                0,
                0,
                GradientDrawable.Orientation.TL_BR
        );
    }

    private GradientDrawable heroBackground() {
        return gradient(
                new int[]{
                        Color.rgb(12, 25, 31),
                        Color.rgb(15, 32, 39)
                },
                dp(24),
                Color.rgb(38, 60, 69),
                dp(1),
                GradientDrawable.Orientation.TL_BR
        );
    }

    private GradientDrawable cardBackground(int color, int radius) {
        return gradient(
                new int[]{
                        blendColor(color, Color.rgb(30, 48, 56), 0.22f),
                        color,
                        blendColor(color, Color.BLACK, 0.18f)
                },
                radius,
                Color.rgb(38, 60, 69),
                dp(1),
                GradientDrawable.Orientation.TL_BR
        );
    }

    private GradientDrawable rowBackground() {
        return gradient(
                new int[]{
                        Color.rgb(12, 25, 31),
                        Color.rgb(15, 32, 39)
                },
                dp(18),
                Color.rgb(38, 60, 69),
                dp(1),
                GradientDrawable.Orientation.LEFT_RIGHT
        );
    }

    private GradientDrawable glassButtonBackground(int color, int strokeColor, int radius) {
        int border = strokeColor == 0 ? blendColor(color, Color.WHITE, 0.18f) : strokeColor;
        return gradient(
                new int[]{
                        blendColor(color, Color.WHITE, 0.18f),
                        color,
                        blendColor(color, Color.BLACK, 0.14f)
                },
                radius,
                border,
                dp(1),
                GradientDrawable.Orientation.TOP_BOTTOM
        );
    }

    private GradientDrawable gradient(int[] colors, int radius, int strokeColor, int strokeWidth, GradientDrawable.Orientation orientation) {
        GradientDrawable drawable = new GradientDrawable(orientation, colors);
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setCornerRadius(radius);
        if (strokeColor != 0 && strokeWidth > 0) {
            drawable.setStroke(strokeWidth, strokeColor);
        }
        return drawable;
    }

    private int blendColor(int from, int to, float ratio) {
        float bounded = Math.max(0f, Math.min(1f, ratio));
        int alpha = Math.round(Color.alpha(from) + (Color.alpha(to) - Color.alpha(from)) * bounded);
        int red = Math.round(Color.red(from) + (Color.red(to) - Color.red(from)) * bounded);
        int green = Math.round(Color.green(from) + (Color.green(to) - Color.green(from)) * bounded);
        int blue = Math.round(Color.blue(from) + (Color.blue(to) - Color.blue(from)) * bounded);
        return Color.argb(alpha, red, green, blue);
    }

    private LinearLayout.LayoutParams fullWidthWithMargins(int left, int top, int right, int bottom) {
        return fullWidthWithMargins(left, top, right, bottom, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams fullWidthWithMargins(int left, int top, int right, int bottom, int height) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, height);
        params.setMargins(left, top, right, bottom);
        return params;
    }

    private LinearLayout.LayoutParams toolbarButtonParams(int left, int right) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1);
        params.setMargins(left, 0, right, 0);
        return params;
    }

    private LinearLayout.LayoutParams categoryTabParams(int left, int right) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1);
        params.setMargins(left, 0, right, 0);
        return params;
    }

    private int dp(int value) {
        if (value == 0) {
            return 0;
        }
        int pixels = Math.round(value * responsiveScale(MIN_AUTO_UI_SCALE, MAX_AUTO_UI_SCALE) * getResources().getDisplayMetrics().density);
        return value > 0 ? Math.max(1, pixels) : Math.min(-1, pixels);
    }

    private int rawDp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private float responsiveScale(float min, float max) {
        float density = Math.max(1f, getResources().getDisplayMetrics().density);
        Configuration configuration = getResources().getConfiguration();
        float widthDp = configuration.screenWidthDp > 0
                ? configuration.screenWidthDp
                : getResources().getDisplayMetrics().widthPixels / density;
        float heightDp = configuration.screenHeightDp > 0
                ? configuration.screenHeightDp
                : getResources().getDisplayMetrics().heightPixels / density;
        float shortestDp = Math.min(widthDp, heightDp);
        float longestDp = Math.max(widthDp, heightDp);
        float widthProgress = boundedProgress(shortestDp, 320f, 430f);
        float heightProgress = boundedProgress(longestDp, 600f, 860f);
        float progress = (widthProgress * 0.78f) + (heightProgress * 0.22f);
        if (isLandscape()) {
            progress *= 0.88f;
        }
        float scale = min + (max - min) * Math.max(0f, Math.min(1f, progress));
        return Math.max(min, Math.min(max, scale));
    }

    private float boundedProgress(float value, float min, float max) {
        if (max <= min) {
            return 0f;
        }
        return Math.max(0f, Math.min(1f, (value - min) / (max - min)));
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

    private static String formatSpeed(long bytesPerSecond) {
        return formatSize(Math.max(0, bytesPerSecond)) + "/s";
    }

    private static final class LanPeer {
        final String name;
        final String host;

        LanPeer(String name, String host) {
            this.name = name;
            this.host = host;
        }
    }

    private static final class FileFolderGroup {
        final String key;
        final String label;
        final List<SharedFile> files = new ArrayList<>();
        long totalBytes;

        FileFolderGroup(String key, String label) {
            this.key = key == null ? "" : key;
            this.label = TextUtils.isEmpty(label) ? "Selected files" : label;
        }
    }

    private static final class NearbyEndpoint {
        final String host;
        final int port;
        final boolean receiver;
        final String sessionToken;

        NearbyEndpoint(String host, int port, boolean receiver, String sessionToken) {
            this.host = host;
            this.port = port;
            this.receiver = receiver;
            this.sessionToken = sessionToken == null ? "" : sessionToken;
        }
    }

    private static final class NearbyTransferItem {
        final String fileId;
        final String name;
        long totalBytes;
        long transferredBytes;
        long bytesPerSecond;
        long lastSpeedSampleAt;
        long lastSpeedSampleBytes;
        long lastRenderAt;
        boolean cancelRequested;
        String remoteFileId = "";
        String status;
        String location = "";

        NearbyTransferItem(String name, long totalBytes, String status) {
            this("", name, totalBytes, status);
        }

        NearbyTransferItem(String fileId, String name, long totalBytes, String status) {
            this.fileId = fileId == null ? "" : fileId;
            this.name = TextUtils.isEmpty(name) ? "nearby-file" : name;
            this.totalBytes = totalBytes;
            this.status = status;
        }
    }

    private static final class NearbyIncomingFile {
        final String resumeId;
        final String name;
        final String mimeType;
        final long size;
        final NearbyTransferItem item;

        NearbyIncomingFile(String resumeId, String name, String mimeType, long size, NearbyTransferItem item) {
            this.resumeId = resumeId;
            this.name = name;
            this.mimeType = mimeType;
            this.size = size;
            this.item = item;
        }
    }

    private static final class ReceivedOutput {
        final OutputStream output;
        final String displayLocation;
        final Uri uri;

        ReceivedOutput(OutputStream output, String displayLocation, Uri uri) {
            this.output = output;
            this.displayLocation = displayLocation;
            this.uri = uri;
        }
    }
}
