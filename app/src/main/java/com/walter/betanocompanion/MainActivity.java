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
import android.widget.EditText;
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

        if (getIntent() != null && getIntent().getBooleanExtra("request_capture", false)) {
            getIntent().removeExtra("request_capture");
            requestScreenCapture();
        }
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(48, 80, 48, 48);

        TextView title = new TextView(this);
        title.setText("Betano Companion\nClean v2.1 + GPT");
        title.setTextSize(24);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        TextView info = new TextView(this);
        info.setText("LEER captura la pantalla, hace OCR y envía el texto a un endpoint GPT seguro.\n\nLa API key de OpenAI nunca se guarda dentro del APK.");
        info.setTextSize(16);
        info.setPadding(0, 28, 0, 18);
        root.addView(info);

        Button start = new Button(this);
        start.setText("ACTIVAR BURBUJA");
        start.setOnClickListener(v -> enableOverlay());
        root.addView(start);

        Button stop = new Button(this);
        stop.setText("OCULTAR BURBUJA");
        stop.setOnClickListener(v -> stopService(new Intent(this, OverlayService.class)));
        root.addView(stop);

        TextView endpointLabel = new TextView(this);
        endpointLabel.setText("Endpoint GPT seguro (proxy):");
        endpointLabel.setPadding(0, 32, 0, 8);
        root.addView(endpointLabel);

        EditText endpoint = new EditText(this);
        endpoint.setSingleLine(true);
        endpoint.setHint("https://tu-endpoint.example/analyze");
        endpoint.setText(getSharedPreferences("settings", MODE_PRIVATE).getString("gpt_endpoint", ""));
        root.addView(endpoint);

        Button save = new Button(this);
        save.setText("GUARDAR ENDPOINT");
        save.setOnClickListener(v -> {
            String url = endpoint.getText().toString().trim();
            getSharedPreferences("settings", MODE_PRIVATE).edit().putString("gpt_endpoint", url).apply();
            Toast.makeText(this, url.isEmpty() ? "Endpoint borrado" : "Endpoint guardado", Toast.LENGTH_SHORT).show();
        });
        root.addView(save);

        setContentView(root);
    }

    private void enableOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, OVERLAY_REQ);
        } else {
            startBubble();
        }
    }

    private void startBubble() {
        startService(new Intent(this, OverlayService.class));
        Toast.makeText(this, "Burbuja activada", Toast.LENGTH_SHORT).show();
    }

    private void requestScreenCapture() {
        if (projectionManager == null) {
            Toast.makeText(this, "No se pudo iniciar la captura", Toast.LENGTH_LONG).show();
            return;
        }
        startActivityForResult(projectionManager.createScreenCaptureIntent(), CAPTURE_REQ);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == OVERLAY_REQ) {
            if (Settings.canDrawOverlays(this)) startBubble();
            else Toast.makeText(this, "Necesitamos permiso para mostrar la burbuja", Toast.LENGTH_LONG).show();
            return;
        }

        if (requestCode == CAPTURE_REQ) {
            if (resultCode == RESULT_OK && data != null) {
                Intent service = new Intent(this, ScreenScanService.class);
                service.putExtra("resultCode", resultCode);
                service.putExtra("data", data);
                if (Build.VERSION.SDK_INT >= 26) startForegroundService(service); else startService(service);
                Toast.makeText(this, "Leyendo pantalla…", Toast.LENGTH_SHORT).show();
                moveTaskToBack(true);
            } else {
                Toast.makeText(this, "Captura cancelada", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
