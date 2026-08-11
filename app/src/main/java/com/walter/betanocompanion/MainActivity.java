package com.walter.betanocompanion;

import android.app.Activity;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int REQ_OVERLAY = 1201;
    private static final int REQ_CAPTURE = 1202;
    private MediaProjectionManager projectionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        projectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(48, 56, 48, 48);
        root.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView title = new TextView(this);
        title.setText("Betano Companion 2.0");
        title.setTextSize(27f);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView info = new TextView(this);
        info.setText("Analizador de sesión\n\nLa app lee los importes visibles de la pantalla y calcula estadísticas de la sesión. El indicador de tendencia describe el historial reciente: no predice ni garantiza la próxima jugada.");
        info.setTextSize(16f);
        info.setPadding(0, 32, 0, 32);
        root.addView(info, new LinearLayout.LayoutParams(-1, -2));

        Button start = new Button(this);
        start.setText("INICIAR ANÁLISIS");
        start.setOnClickListener(v -> begin());
        root.addView(start, new LinearLayout.LayoutParams(-1, -2));

        Button bubbleOnly = new Button(this);
        bubbleOnly.setText("MOSTRAR SOLO BURBUJA");
        bubbleOnly.setOnClickListener(v -> ensureOverlayAndStartBubble());
        root.addView(bubbleOnly, new LinearLayout.LayoutParams(-1, -2));

        Button reset = new Button(this);
        reset.setText("REINICIAR SESIÓN");
        reset.setOnClickListener(v -> {
            SessionStore.get().reset();
            Toast.makeText(this, "Sesión reiniciada", Toast.LENGTH_SHORT).show();
        });
        root.addView(reset, new LinearLayout.LayoutParams(-1, -2));

        setContentView(root);
    }

    private void begin() {
        if (!Settings.canDrawOverlays(this)) {
            Intent permission = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(permission, REQ_OVERLAY);
            return;
        }
        startBubble();
        startActivityForResult(projectionManager.createScreenCaptureIntent(), REQ_CAPTURE);
    }

    private void ensureOverlayAndStartBubble() {
        if (!Settings.canDrawOverlays(this)) {
            Intent permission = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(permission, REQ_OVERLAY);
        } else {
            startBubble();
        }
    }

    private void startBubble() {
        Intent overlay = new Intent(this, OverlayService.class);
        startService(overlay);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_OVERLAY) {
            if (Settings.canDrawOverlays(this)) begin();
            else Toast.makeText(this, "La burbuja necesita permiso para mostrarse sobre otras apps", Toast.LENGTH_LONG).show();
            return;
        }
        if (requestCode == REQ_CAPTURE && resultCode == RESULT_OK && data != null) {
            Intent scan = new Intent(this, ScreenScanService.class);
            scan.putExtra(ScreenScanService.EXTRA_RESULT_CODE, resultCode);
            scan.putExtra(ScreenScanService.EXTRA_RESULT_DATA, data);
            startForegroundService(scan);
            Toast.makeText(this, "Análisis activo. Abrí el juego y dejá visible saldo/apuesta.", Toast.LENGTH_LONG).show();
            moveTaskToBack(true);
        }
    }
}
