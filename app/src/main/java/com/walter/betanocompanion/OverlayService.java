package com.walter.betanocompanion;

import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class OverlayService extends Service {
    private WindowManager wm;
    private View bubble;
    private WindowManager.LayoutParams params;
    private TextView label;

    @Override public IBinder onBind(Intent intent) { return null; }
    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density); }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (bubble != null) {
            refreshLabel();
            return START_STICKY;
        }

        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.HORIZONTAL);
        panel.setGravity(Gravity.CENTER_VERTICAL);
        panel.setPadding(dp(10), dp(6), dp(8), dp(6));
        panel.setBackgroundColor(Color.rgb(30, 73, 160));

        label = new TextView(this);
        label.setTextColor(Color.WHITE);
        label.setTextSize(13);
        label.setPadding(0, 0, dp(10), 0);
        panel.addView(label);
        refreshLabel();

        Button read = new Button(this);
        read.setText("LEER");
        read.setAllCaps(true);
        read.setOnClickListener(v -> {
            getSharedPreferences("session", MODE_PRIVATE).edit().putString("status", "Preparando lectura…").apply();
            refreshLabel();
            Intent i = new Intent(this, MainActivity.class);
            i.putExtra("request_capture", true);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(i);
        });
        panel.addView(read, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(44)));

        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.END;
        params.x = dp(12);
        params.y = dp(140);

        final float[] downX = new float[1];
        final float[] downY = new float[1];
        final int[] startX = new int[1];
        final int[] startY = new int[1];
        label.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    downX[0] = event.getRawX(); downY[0] = event.getRawY();
                    startX[0] = params.x; startY[0] = params.y; return true;
                case MotionEvent.ACTION_MOVE:
                    params.x = startX[0] - (int)(event.getRawX() - downX[0]);
                    params.y = startY[0] + (int)(event.getRawY() - downY[0]);
                    wm.updateViewLayout(bubble, params); return true;
            }
            return false;
        });

        bubble = panel;
        wm.addView(bubble, params);
        return START_STICKY;
    }

    private void refreshLabel() {
        if (label == null) return;
        String s = getSharedPreferences("session", MODE_PRIVATE).getString("status", "BC v2.1");
        if (s.length() > 90) s = s.substring(0, 90) + "…";
        label.setText(s);
    }

    @Override public void onDestroy() {
        if (bubble != null && wm != null) { wm.removeView(bubble); bubble = null; }
        super.onDestroy();
    }
}
