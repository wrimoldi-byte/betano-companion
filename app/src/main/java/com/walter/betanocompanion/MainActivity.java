package com.walter.betanocompanion;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int OVERLAY_REQ = 1001;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(48, 80, 48, 48);

        TextView title = new TextView(this);
        title.setText("Betano Companion\nBase limpia v2");
        title.setTextSize(24);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        TextView info = new TextView(this);
        info.setText("Primera prueba: mostrar una burbuja flotante estable con botón LEER.\n\nTodavía no hace OCR ni búsquedas web.");
        info.setTextSize(16);
        info.setPadding(0, 36, 0, 36);
        root.addView(info);

        Button start = new Button(this);
        start.setText("ACTIVAR BURBUJA");
        start.setOnClickListener(v -> enableOverlay());
        root.addView(start);

        Button stop = new Button(this);
        stop.setText("OCULTAR BURBUJA");
        stop.setOnClickListener(v -> stopService(new Intent(this, OverlayService.class)));
        root.addView(stop);

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

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == OVERLAY_REQ) {
            if (Settings.canDrawOverlays(this)) startBubble();
            else Toast.makeText(this, "Necesitamos permiso para mostrar la burbuja", Toast.LENGTH_LONG).show();
        }
    }
}
