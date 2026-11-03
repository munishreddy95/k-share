package com.fluxfileshare;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

public final class NearbyTransferService extends Service {
    static final String ACTION_START = "com.fluxfileshare.NEARBY_START";
    static final String ACTION_STOP = "com.fluxfileshare.NEARBY_STOP";
    static final String ACTION_PAUSE = "com.fluxfileshare.NEARBY_PAUSE";
    static final String ACTION_RESUME = "com.fluxfileshare.NEARBY_RESUME";
    static final String ACTION_CANCEL = "com.fluxfileshare.NEARBY_CANCEL";
    static final String BROADCAST_CONTROL = "com.fluxfileshare.NEARBY_CONTROL";
    static final String EXTRA_COMMAND = "command";
    static final String EXTRA_TEXT = "text";
    static final String EXTRA_TRANSFER_ACTIVE = "transfer_active";
    static final String EXTRA_CAN_PAUSE = "can_pause";
    static final String EXTRA_CAN_CANCEL = "can_cancel";
    static final String EXTRA_PAUSED = "paused";
    static final String COMMAND_PAUSE = "pause";
    static final String COMMAND_RESUME = "resume";
    static final String COMMAND_CANCEL = "cancel";

    private static final String TAG = "KShareNearbyService";
    private static final String CHANNEL_ID = "nearby_transfer";
    private static final int NOTIFICATION_ID = 1002;

    private WifiManager.WifiLock wifiLock;
    private PowerManager.WakeLock wakeLock;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopKeepAlive();
            return START_NOT_STICKY;
        }
        if (ACTION_PAUSE.equals(action)) {
            broadcastControl(COMMAND_PAUSE);
            return START_STICKY;
        }
        if (ACTION_RESUME.equals(action)) {
            broadcastControl(COMMAND_RESUME);
            return START_STICKY;
        }
        if (ACTION_CANCEL.equals(action)) {
            broadcastControl(COMMAND_CANCEL);
            return START_STICKY;
        }

        ensureChannel();
        acquireLocks();
        String text = intent == null ? "" : intent.getStringExtra(EXTRA_TEXT);
        if (text == null || text.trim().isEmpty()) {
            text = "Keeping nearby transfer active.";
        }
        boolean transferActive = intent != null && intent.getBooleanExtra(EXTRA_TRANSFER_ACTIVE, false);
        boolean canPause = intent != null && intent.getBooleanExtra(EXTRA_CAN_PAUSE, transferActive);
        boolean canCancel = intent != null && intent.getBooleanExtra(EXTRA_CAN_CANCEL, transferActive);
        boolean paused = intent != null && intent.getBooleanExtra(EXTRA_PAUSED, false);
        startForeground(NOTIFICATION_ID, notification(text, canPause, canCancel, paused));
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        releaseLocks();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @SuppressLint("WakelockTimeout")
    private void acquireLocks() {
        try {
            if (wifiLock == null) {
                WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
                if (wifiManager != null) {
                    wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "FluxFileShare:NearbyWifi");
                    wifiLock.setReferenceCounted(false);
                }
            }
            if (wifiLock != null && !wifiLock.isHeld()) {
                wifiLock.acquire();
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not acquire Wi-Fi lock.", e);
        }

        try {
            if (wakeLock == null) {
                PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
                if (powerManager != null) {
                    wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "FluxFileShare:NearbyTransfer");
                    wakeLock.setReferenceCounted(false);
                }
            }
            if (wakeLock != null && !wakeLock.isHeld()) {
                wakeLock.acquire();
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not acquire nearby transfer wake lock.", e);
        }
    }

    private void releaseLocks() {
        try {
            if (wifiLock != null && wifiLock.isHeld()) {
                wifiLock.release();
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not release Wi-Fi lock.", e);
        }
        try {
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not release wake lock.", e);
        }
    }

    private void stopKeepAlive() {
        releaseLocks();
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    private void broadcastControl(String command) {
        Intent intent = new Intent(BROADCAST_CONTROL)
                .setPackage(getPackageName())
                .putExtra(EXTRA_COMMAND, command);
        sendBroadcast(intent);
    }

    private Notification notification(String text, boolean canPause, boolean canCancel, boolean paused) {
        Intent openIntent = new Intent(this, MainActivity.class);
        PendingIntent open = PendingIntent.getActivity(
                this,
                3,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        Notification.Builder builder = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("K-Share Nearby")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .setContentIntent(open)
                .setOngoing(true)
                .setStyle(new Notification.BigTextStyle().bigText(text));
        if (canPause) {
            if (paused) {
                builder.addAction(
                        android.R.drawable.ic_media_play,
                        "Resume",
                        serviceAction(ACTION_RESUME, 4)
                );
            } else {
                builder.addAction(
                        android.R.drawable.ic_media_pause,
                        "Pause",
                        serviceAction(ACTION_PAUSE, 5)
                );
            }
        }
        if (canCancel) {
            builder.addAction(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    "Cancel",
                    serviceAction(ACTION_CANCEL, 6)
            );
        }
        return builder.build();
    }

    private PendingIntent serviceAction(String action, int requestCode) {
        Intent intent = new Intent(this, NearbyTransferService.class).setAction(action);
        return PendingIntent.getService(
                this,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private void ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Nearby transfer",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Keeps nearby file transfer active in the background.");
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }
}
