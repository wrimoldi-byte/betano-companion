package com.walter.betanocompanion;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int OVERLAY_REQ = 1001;
    private static final int CAPTURE_REQ = 1002;
    private MediaProjectionManager projectionManager;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        projectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        buildUi();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(48, 80, 48, 48);

        TextView title = new TextView(this);
        title.setText("Betano Companion\nv2.4");
        title.setTextSize(26);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        TextView info = new TextView(this);
        info.setText("1. Tocá ACTIVAR LECTURA.\n2. Aceptá la burbuja y compartir pantalla una sola vez.\n3. Abrí Betano.\n4. Desde Betano tocá LEER.\n5. La lectura se hará sin volver a esta app.");
        info.setTextSize(17);
        info.setPadding(0, 30, 0, 30);
        root.addView(info);

        Button start = new Button(this);
        start.setText("ACTIVAR LECTURA");
        start.setOnClickListener(v -> beginSetup());
        root.addView(start);

        Button stop = new Button(this);
        stop.setText("DETENER");
        stop.setOnClickListener(v -> {
            stopService(new Intent(this, OverlayService.class));
            stopService(new Intent(this, ScreenScanService.class));
            Toast.makeText(this, "Lectura detenida", Toast.LENGTH_SHORT).show();
        });
        root.addView(stop);

        TextView note = new TextView(this);
        note.setText("La IA compara información pública como RTP publicado y volatilidad. No predice el próximo giro ni garantiza ganancias.");
        note.setTextSize(14);
        note.setPadding(0, 30, 0, 0);
        root.addView(note);

        setContentView(root);
    }

    private void beginSetup() {
        if (!Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, OVERLAY_REQ);
        } else {
            requestScreenCapture();
        }
    }

    private void requestScreenCapture() {
        if (projectionManager == null) {
            Toast.makeText(this, "No se pudo iniciar la captura", Toast.LENGTH_LONG).show();
            return;
        }
        startActivityForResult(projectionManager.createScreenCaptureIntent(), CAPTURE_REQ);
    }

    private void startBubble() {
        startService(new Intent(this, OverlayService.class));
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == OVERLAY_REQ) {
            if (Settings.canDrawOverlays(this)) requestScreenCapture();
            else Toast.makeText(this, "Necesitamos permiso para mostrar la burbuja", Toast.LENGTH_LONG).show();
            return;
        }

        if (requestCode == CAPTURE_REQ) {
            if (resultCode == RESULT_OK && data != null) {
                Intent service = new Intent(this, ScreenScanService.class).setAction(ScreenScanService.ACTION_START);
                service.putExtra("resultCode", resultCode);
                service.putExtra("data", data);
                if (Build.VERSION.SDK_INT >= 26) startForegroundService(service); else startService(service);
                getSharedPreferences("session", MODE_PRIVATE).edit().putString("status", "Listo. Abrí Betano y tocá LEER.").apply();
                startBubble();
                Toast.makeText(this, "Listo. Ahora abrí Betano y usá LEER.", Toast.LENGTH_LONG).show();
                moveTaskToBack(true);
            } else {
                Toast.makeText(this, "Captura cancelada", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
