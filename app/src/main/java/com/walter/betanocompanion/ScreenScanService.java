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
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public class ScreenScanService extends Service {
    public static final String ACTION_START = "com.walter.betanocompanion.START_CAPTURE_SESSION";
    public static final String ACTION_CAPTURE = "com.walter.betanocompanion.CAPTURE_NOW";

    private static final int TOTAL_SAMPLES = 7;
    private static final long BETWEEN_SAMPLES_MS = 900;

    private MediaProjection projection;
    private ImageReader reader;
    private VirtualDisplay display;
    private int w, h, density;

    private final AtomicBoolean pendingCapture = new AtomicBoolean(false);
    private final AtomicBoolean ocrBusy = new AtomicBoolean(false);
    private final AtomicBoolean scanRunning = new AtomicBoolean(false);
    private final Set<String> collectedLines = new LinkedHashSet<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private int sampleNumber = 0;

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_STICKY;
        String action = intent.getAction();

        if (ACTION_START.equals(action)) {
            startProjection(intent);
        } else if (ACTION_CAPTURE.equals(action)) {
            if (projection == null || reader == null) {
                setStatus("Lectura inactiva. Abrí Betano Companion y tocá ACTIVAR LECTURA.");
            } else if (scanRunning.get()) {
                setStatus("Ya estoy leyendo. Seguí bajando por la pantalla…");
            } else {
                startScrollingScan();
            }
        }
        return START_STICKY;
    }

    private void startProjection(Intent intent) {
        createNotification();
        if (projection != null) return;

        MediaProjectionManager mgr = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        projection = mgr.getMediaProjection(intent.getIntExtra("resultCode", Activity.RESULT_CANCELED), (Intent) intent.getParcelableExtra("data"));
        if (projection == null) {
            setStatus("No se pudo activar la lectura");
            stopSelf();
            return;
        }

        projection.registerCallback(new MediaProjection.Callback() {
            @Override public void onStop() {
                setStatus("Lectura detenida. Volvé a activar desde la app.");
                cleanup();
            }
        }, mainHandler);

        android.util.DisplayMetrics dm = new android.util.DisplayMetrics();
        ((WindowManager)getSystemService(WINDOW_SERVICE)).getDefaultDisplay().getRealMetrics(dm);
        w = dm.widthPixels;
        h = dm.heightPixels;
        density = dm.densityDpi;

        reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2);
        reader.setOnImageAvailableListener(r -> {
            Image image = null;
            try {
                image = r.acquireLatestImage();
                if (image == null) return;
                if (!pendingCapture.compareAndSet(true, false)) return;
                if (!ocrBusy.compareAndSet(false, true)) return;

                Bitmap bitmap = toBitmap(image);
                runOcrSample(bitmap);
            } catch (Exception e) {
                ocrBusy.set(false);
                setStatus("No se pudo leer la pantalla");
                scanRunning.set(false);
            } finally {
                if (image != null) image.close();
            }
        }, mainHandler);

        display = projection.createVirtualDisplay(
                "BCPersistentScan",
                w,
                h,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.getSurface(),
                null,
                null
        );

        setStatus("Listo. Abrí Betano y tocá LEER.");
    }

    private void startScrollingScan() {
        collectedLines.clear();
        sampleNumber = 0;
        scanRunning.set(true);
        ocrBusy.set(false);
        setStatus("Deslizá hacia abajo… iniciando lectura");
        requestNextSample(250);
    }

    private void requestNextSample(long delayMs) {
        mainHandler.postDelayed(() -> {
            if (!scanRunning.get()) return;
            pendingCapture.set(true);
            setStatus("Deslizá hacia abajo… " + (sampleNumber + 1) + "/" + TOTAL_SAMPLES);
        }, delayMs);
    }

    private void runOcrSample(Bitmap bitmap) {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                .process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener(result -> {
                    String text = result.getText() == null ? "" : result.getText().trim();
                    bitmap.recycle();
                    addUniqueLines(text);
                    sampleNumber++;
                    ocrBusy.set(false);

                    if (sampleNumber >= TOTAL_SAMPLES) {
                        finishScrollingScan();
                    } else {
                        requestNextSample(BETWEEN_SAMPLES_MS);
                    }
                })
                .addOnFailureListener(e -> {
                    bitmap.recycle();
                    sampleNumber++;
                    ocrBusy.set(false);
                    if (sampleNumber >= TOTAL_SAMPLES) finishScrollingScan();
                    else requestNextSample(BETWEEN_SAMPLES_MS);
                });
    }

    private void addUniqueLines(String text) {
        if (text == null || text.trim().isEmpty()) return;
        for (String raw : text.split("\\r?\\n")) {
            String line = raw.trim();
            if (line.length() >= 2) collectedLines.add(line);
            if (collectedLines.size() >= 350) break;
        }
    }

    private void finishScrollingScan() {
        scanRunning.set(false);
        pendingCapture.set(false);
        ocrBusy.set(false);

        StringBuilder all = new StringBuilder();
        for (String line : collectedLines) {
            if (all.length() > 0) all.append('\n');
            all.append(line);
            if (all.length() > 11000) break;
        }
        String text = all.toString().trim();
        getSharedPreferences("session", MODE_PRIVATE).edit().putString("last_scan", text).apply();

        if (text.isEmpty()) {
            setStatus("No se detectó texto durante el recorrido");
            return;
        }

        setStatus("Lectura completa. Analizando con IA…");
        GptRecommendationClient.analyze(this, text, r -> {});
    }

    private void createNotification() {
        String id = "bc_scan";
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(new NotificationChannel(id, "Lectura de pantalla", NotificationManager.IMPORTANCE_LOW));
        }
        Notification n = new Notification.Builder(this, id)
                .setContentTitle("Betano Companion")
                .setContentText("Lectura de pantalla activa")
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setOngoing(true)
                .build();
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(77, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else {
            startForeground(77, n);
        }
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
        display = null;
        try { if (reader != null) reader.close(); } catch (Exception ignored) {}
        reader = null;
        projection = null;
        pendingCapture.set(false);
        ocrBusy.set(false);
        scanRunning.set(false);
        stopForeground(true);
        stopSelf();
    }

    @Override public void onDestroy() {
        try { if (display != null) display.release(); } catch (Exception ignored) {}
        try { if (reader != null) reader.close(); } catch (Exception ignored) {}
        try { if (projection != null) projection.stop(); } catch (Exception ignored) {}
        display = null;
        reader = null;
        projection = null;
        super.onDestroy();
    }
}
