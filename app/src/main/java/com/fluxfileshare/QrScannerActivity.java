package com.fluxfileshare;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.google.zxing.ResultPoint;
import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.BarcodeResult;
import com.journeyapps.barcodescanner.DecoratedBarcodeView;

import java.util.List;

public final class QrScannerActivity extends Activity {
    static final String EXTRA_QR_TEXT = "qr_text";
    static final String EXTRA_SCAN_TITLE = "scan_title";
    static final String EXTRA_SCAN_HINT = "scan_hint";
    static final String EXTRA_SCAN_STATUS = "scan_status";

    private DecoratedBarcodeView barcodeView;
    private boolean resultDelivered;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            setResult(RESULT_CANCELED);
            finish();
            return;
        }

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        String scanTitle = getIntent().getStringExtra(EXTRA_SCAN_TITLE);
        String scanHint = getIntent().getStringExtra(EXTRA_SCAN_HINT);
        String scanStatus = getIntent().getStringExtra(EXTRA_SCAN_STATUS);

        barcodeView = new DecoratedBarcodeView(this);
        barcodeView.setStatusText(TextUtils.isEmpty(scanStatus)
                ? "Point camera at the K-Share QR"
                : scanStatus);
        root.addView(barcodeView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        FrameLayout topBar = new FrameLayout(this);
        topBar.setBackgroundColor(Color.argb(145, 0, 0, 0));
        topBar.setPadding(dp(12), dp(18), dp(12), dp(8));
        FrameLayout.LayoutParams topParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP
        );
        root.addView(topBar, topParams);

        TextView back = new TextView(this);
        back.setText("<");
        back.setTextColor(Color.WHITE);
        back.setTextSize(34);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });
        FrameLayout.LayoutParams backParams = new FrameLayout.LayoutParams(dp(44), dp(44), Gravity.START | Gravity.CENTER_VERTICAL);
        topBar.addView(back, backParams);

        TextView title = new TextView(this);
        title.setText(TextUtils.isEmpty(scanTitle) ? "Scan QR Code" : scanTitle);
        title.setTextColor(Color.WHITE);
        title.setTextSize(17);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        topBar.addView(title, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dp(44),
                Gravity.CENTER
        ));

        FrameLayout scanFrame = new FrameLayout(this);
        FrameLayout.LayoutParams frameParams = new FrameLayout.LayoutParams(dp(290), dp(290), Gravity.CENTER);
        root.addView(scanFrame, frameParams);
        addCorner(scanFrame, Gravity.TOP | Gravity.START, true, true);
        addCorner(scanFrame, Gravity.TOP | Gravity.END, false, true);
        addCorner(scanFrame, Gravity.BOTTOM | Gravity.START, true, false);
        addCorner(scanFrame, Gravity.BOTTOM | Gravity.END, false, false);

        View scanLine = new View(this);
        scanLine.setBackgroundColor(Color.rgb(37, 99, 235));
        FrameLayout.LayoutParams lineParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dp(2),
                Gravity.CENTER
        );
        lineParams.setMargins(dp(20), 0, dp(20), 0);
        scanFrame.addView(scanLine, lineParams);

        TextView hint = new TextView(this);
        hint.setText(TextUtils.isEmpty(scanHint) ? "Scan the K-Share QR code" : scanHint);
        hint.setTextColor(Color.WHITE);
        hint.setTextSize(14);
        hint.setGravity(Gravity.CENTER);
        hint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        hint.setBackgroundColor(Color.argb(130, 0, 0, 0));
        hint.setPadding(dp(18), dp(10), dp(18), dp(10));
        FrameLayout.LayoutParams hintParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
        );
        hintParams.setMargins(dp(24), 0, dp(24), dp(30));
        root.addView(hint, hintParams);

        setContentView(root);
        barcodeView.decodeSingle(new BarcodeCallback() {
            @Override
            public void barcodeResult(BarcodeResult result) {
                if (resultDelivered || result == null || result.getText() == null) {
                    return;
                }
                resultDelivered = true;
                Intent data = new Intent();
                data.putExtra(EXTRA_QR_TEXT, result.getText());
                setResult(RESULT_OK, data);
                finish();
            }

            @Override
            public void possibleResultPoints(List<ResultPoint> resultPoints) {
            }
        });
    }

    private void addCorner(FrameLayout parent, int gravity, boolean left, boolean top) {
        int blue = Color.rgb(37, 99, 235);
        View horizontal = new View(this);
        horizontal.setBackgroundColor(blue);
        FrameLayout.LayoutParams horizontalParams = new FrameLayout.LayoutParams(dp(46), dp(4), gravity);
        parent.addView(horizontal, horizontalParams);

        View vertical = new View(this);
        vertical.setBackgroundColor(blue);
        FrameLayout.LayoutParams verticalParams = new FrameLayout.LayoutParams(dp(4), dp(46), gravity);
        parent.addView(vertical, verticalParams);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (barcodeView != null) {
            barcodeView.resume();
        }
    }

    @Override
    protected void onPause() {
        if (barcodeView != null) {
            barcodeView.pause();
        }
        super.onPause();
    }
}
