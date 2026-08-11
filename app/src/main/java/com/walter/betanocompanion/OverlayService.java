package com.walter.betanocompanion;

import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

public class OverlayService extends Service {
    private WindowManager wm;
    private View bubble;
    private WindowManager.LayoutParams params;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView stats;
    private TextView trend;

    private final Runnable refresher = new Runnable() {
        @Override public void run() {
            render();
            handler.postDelayed(this, 700);
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return; }
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(20, 16, 20, 16);
        panel.setBackgroundColor(0xE6222222);

        TextView title = new TextView(this);
        title.setText("🎰 BC 2.4");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(16f);
        panel.addView(title);

        stats = new TextView(this);
        stats.setTextColor(0xFFFFFFFF);
        stats.setTextSize(13f);
        panel.addView(stats);

        trend = new TextView(this);
        trend.setTextColor(0xFFFFFFFF);
        trend.setTextSize(14f);
        trend.setPadding(0, 8, 0, 8);
        panel.addView(trend);

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        Button reset = new Button(this);
        reset.setText("↻");
        reset.setOnClickListener(v -> SessionStore.get().reset());
        controls.addView(reset);
        Button close = new Button(this);
        close.setText("×");
        close.setOnClickListener(v -> stopSelf());
        controls.addView(close);
        panel.addView(controls);

        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 20; params.y = 180;

        panel.setOnTouchListener(new View.OnTouchListener() {
            int startX, startY; float downX, downY;
            @Override public boolean onTouch(View v, MotionEvent e) {
                if (e.getAction() == MotionEvent.ACTION_DOWN) {
                    startX = params.x; startY = params.y; downX = e.getRawX(); downY = e.getRawY(); return true;
                }
                if (e.getAction() == MotionEvent.ACTION_MOVE) {
                    params.x = startX + (int)(e.getRawX() - downX);
                    params.y = startY + (int)(e.getRawY() - downY);
                    wm.updateViewLayout(bubble, params); return true;
                }
                return false;
            }
        });

        bubble = panel;
        wm.addView(bubble, params);
        handler.post(refresher);
    }

    private void render() {
        if (stats == null) return;
        SessionStore.Snapshot s = SessionStore.get().snapshot();
        stats.setText(String.format(Locale.US,
                "Tiradas: %d\nApostado: %.2f\nGanado: %.2f\nRTP sesión: %.1f%%\n" +
                "Sin premio (total): %d\nRacha actual sin premio: %d\nPremios detectados: %d\nFrecuencia de premio: %.1f%%",
                s.spins(), s.wagered(), s.won(), s.rtp(),
                s.totalNoWinSpins(), s.noWinStreak(), s.totalHits(), s.hitRate()));

        String windowText;
        if (s.windowMin() > 0) {
            if (s.windowMin() == s.windowMax()) {
                windowText = "Zona histórica observada: ~" + s.windowMin() + " tirada(s)";
            } else {
                windowText = "Zona histórica observada: " + s.windowMin() + "–" + s.windowMax() + " tiradas";
            }
        } else {
            windowText = "Zona histórica: faltan datos";
        }

        String avg = s.avgSpinsPerHit() > 0
                ? String.format(Locale.US, "Promedio: 1 premio cada %.1f tiradas", s.avgSpinsPerHit())
                : "Promedio: faltan premios detectados";

        trend.setText(s.trend() + "\n" + avg + "\n" + windowText +
                "\nIndicador: " + s.score() + "/100" +
                "\n(historial de esta sesión, no predice el próximo giro)");
    }

    @Override public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (wm != null && bubble != null) wm.removeView(bubble);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
