package com.fluxfileshare;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import java.io.IOException;

public final class ShareService extends Service {
    static final String ACTION_START = "com.fluxfileshare.START";
    static final String ACTION_STOP = "com.fluxfileshare.STOP";
    static final String EXTRA_PORT = "port";
    static final String EXTRA_STARTED_PORT = "started_port";
    static final String BROADCAST_STATUS = "com.fluxfileshare.STATUS";
    static final int DEFAULT_PORT = 8080;

    private static final String CHANNEL_ID = "sharing";
    private static volatile int runningPort;
    private LocalHttpServer server;

    static int currentPort() {
        return runningPort;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopSharing();
            return START_NOT_STICKY;
        }

        int requestedPort = intent == null ? DEFAULT_PORT : intent.getIntExtra(EXTRA_PORT, DEFAULT_PORT);
        ensureChannel();
        startForeground(1001, notification("Starting local file share..."));
        try {
            if (server == null) {
                server = new LocalHttpServer(this, new ShareRepository(this));
            }
            int actualPort = server.start(requestedPort);
            runningPort = actualPort;
            startForeground(1001, notification("Sharing on port " + actualPort));
            broadcast(actualPort, null);
        } catch (IOException e) {
            broadcast(-1, e.getMessage());
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        stopSharing();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void stopSharing() {
        if (server != null) {
            server.stop();
            server = null;
        }
        runningPort = 0;
        broadcast(0, null);
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    private Notification notification(String text) {
        PendingIntent open = openWebShareIntent(1);
        Intent stopIntent = new Intent(this, MainActivity.class)
                .setAction(MainActivity.ACTION_CONFIRM_STOP_LOCAL_SHARE)
                .putExtra(MainActivity.EXTRA_OPEN_TAB, MainActivity.TAB_WEB)
                .putExtra(MainActivity.EXTRA_WEB_FLOW, MainActivity.WEB_FLOW_LOCAL)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent stop = PendingIntent.getActivity(
                this,
                2,
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        return new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("K-Share")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .setContentIntent(open)
                .addAction(android.R.drawable.ic_menu_view, "Open Web Share", openWebShareIntent(3))
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stop)
                .setOngoing(true)
                .build();
    }

    private PendingIntent openWebShareIntent(int requestCode) {
        Intent openIntent = new Intent(this, MainActivity.class)
                .setAction(MainActivity.ACTION_OPEN_WEB_SHARE)
                .putExtra(MainActivity.EXTRA_OPEN_TAB, MainActivity.TAB_WEB)
                .putExtra(MainActivity.EXTRA_WEB_FLOW, MainActivity.WEB_FLOW_LOCAL)
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
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "File sharing", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Keeps the local file sharing server running.");
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    private void broadcast(int port, String error) {
        Intent status = new Intent(BROADCAST_STATUS);
        status.setPackage(getPackageName());
        status.putExtra(EXTRA_STARTED_PORT, port);
        if (error != null) {
            status.putExtra("error", error);
        }
        sendBroadcast(status);
    }
}
