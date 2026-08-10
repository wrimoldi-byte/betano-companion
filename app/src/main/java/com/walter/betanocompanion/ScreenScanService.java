package com.walter.betanocompanion;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.graphics.*;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.*;
import android.view.WindowManager;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import java.nio.ByteBuffer;

public class ScreenScanService extends Service {
    private MediaProjection projection;
    private ImageReader reader;
    private VirtualDisplay display;
    private int w, h, density;

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || !intent.hasExtra("data")) { stopSelf(); return START_NOT_STICKY; }
        createNotification();
        MediaProjectionManager mgr = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        projection = mgr.getMediaProjection(intent.getIntExtra("resultCode", Activity.RESULT_CANCELED), (Intent) intent.getParcelableExtra("data"));
        if (projection == null) { setStatus("No se pudo iniciar lectura"); stopSelf(); return START_NOT_STICKY; }

        android.util.DisplayMetrics dm = new android.util.DisplayMetrics();
        ((WindowManager)getSystemService(WINDOW_SERVICE)).getDefaultDisplay().getRealMetrics(dm);
        w = dm.widthPixels; h = dm.heightPixels; density = dm.densityDpi;
        capture();
        return START_NOT_STICKY;
    }

    private void createNotification() {
        String id = "bc_scan";
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(new NotificationChannel(id, "Lectura de pantalla", NotificationManager.IMPORTANCE_LOW));
        Notification n = new Notification.Builder(this, id).setContentTitle("Betano Companion")
                .setContentText("Leyendo pantalla…").setSmallIcon(android.R.drawable.ic_menu_view).build();
        if (Build.VERSION.SDK_INT >= 29) startForeground(77, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION); else startForeground(77, n);
    }

    private void capture() {
        setStatus("Leyendo pantalla…");
        reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2);
        display = projection.createVirtualDisplay("BCScan", w, h, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader.getSurface(), null, null);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Image image = reader.acquireLatestImage();
            if (image == null) { setStatus("No se pudo capturar la pantalla"); cleanup(); return; }
            Bitmap bitmap = toBitmap(image);
            image.close();
            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                    .process(InputImage.fromBitmap(bitmap, 0))
                    .addOnSuccessListener(result -> {
                        String text = result.getText() == null ? "" : result.getText().trim();
                        getSharedPreferences("session", MODE_PRIVATE).edit().putString("last_scan", text).apply();
                        if (text.isEmpty()) { setStatus("No se detectó texto visible"); cleanup(); return; }
                        setStatus("OCR listo. Consultando GPT…");
                        GptRecommendationClient.analyze(this, text, r -> cleanup());
                    })
                    .addOnFailureListener(e -> { setStatus("Falló el OCR"); cleanup(); });
        }, 900);
    }

    private Bitmap toBitmap(Image image) {
        Image.Plane plane = image.getPlanes()[0];
        ByteBuffer buffer = plane.getBuffer();
        int pixelStride = plane.getPixelStride();
        int rowStride = plane.getRowStride();
        int rowPadding = rowStride - pixelStride * w;
        Bitmap padded = Bitmap.createBitmap(w + rowPadding / pixelStride, h, Bitmap.Config.ARGB_8888);
        padded.copyPixelsFromBuffer(buffer);
        Bitmap out = Bitmap.createBitmap(padded, 0, 0, w, h);
        if (padded != out) padded.recycle();
        return out;
    }

    private void setStatus(String s) {
        getSharedPreferences("session", MODE_PRIVATE).edit().putString("status", s).apply();
        startService(new Intent(this, OverlayService.class).setAction("refresh"));
    }

    private void cleanup() {
        try { if (display != null) display.release(); } catch (Exception ignored) {}
        try { if (reader != null) reader.close(); } catch (Exception ignored) {}
        try { if (projection != null) projection.stop(); } catch (Exception ignored) {}
        stopForeground(true);
        stopSelf();
    }
}
