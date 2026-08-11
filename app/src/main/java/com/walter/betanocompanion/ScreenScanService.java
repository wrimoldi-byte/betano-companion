package com.walter.betanocompanion;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.DisplayMetrics;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ScreenScanService extends Service {
    public static final String EXTRA_RESULT_CODE = "result_code";
    public static final String EXTRA_RESULT_DATA = "result_data";
    private static final int NOTIF_ID = 221;
    private static final String CHANNEL = "betano_scan";

    private MediaProjection projection;
    private ImageReader imageReader;
    private HandlerThread workerThread;
    private Handler worker;
    private TextRecognizer recognizer;
    private long lastProcess;
    private Double lastBalance;
    private double lastBet;
    private long lastSpinAt;

    private static final Pattern MONEY = Pattern.compile("(-?[0-9][0-9.,]*)");

    @Override public void onCreate() {
        super.onCreate();
        recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        workerThread = new HandlerThread("screen-ocr");
        workerThread.start();
        worker = new Handler(workerThread.getLooper());
        startForeground(NOTIF_ID, notification());
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;
        int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, -1);
        Intent data;
        if (Build.VERSION.SDK_INT >= 33) data = intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent.class);
        else data = intent.getParcelableExtra(EXTRA_RESULT_DATA);
        if (resultCode == -1 || data == null) { stopSelf(); return START_NOT_STICKY; }

        MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        projection = mpm.getMediaProjection(resultCode, data);
        if (projection == null) { stopSelf(); return START_NOT_STICKY; }

        projection.registerCallback(new MediaProjection.Callback() {
            @Override public void onStop() { stopSelf(); }
        }, worker);

        DisplayMetrics dm = getResources().getDisplayMetrics();
        int width = dm.widthPixels;
        int height = dm.heightPixels;
        int dpi = dm.densityDpi;
        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2);
        imageReader.setOnImageAvailableListener(this::onImage, worker);
        projection.createVirtualDisplay("BetanoCompanionScan", width, height, dpi, 0,
                imageReader.getSurface(), null, worker);
        return START_NOT_STICKY;
    }

    private void onImage(ImageReader reader) {
        Image image = reader.acquireLatestImage();
        if (image == null) return;
        long now = System.currentTimeMillis();
        if (now - lastProcess < 900) { image.close(); return; }
        lastProcess = now;
        Bitmap bmp = null;
        try {
            Image.Plane plane = image.getPlanes()[0];
            ByteBuffer buffer = plane.getBuffer();
            int pixelStride = plane.getPixelStride();
            int rowStride = plane.getRowStride();
            int rowPadding = rowStride - pixelStride * image.getWidth();
            int paddedWidth = image.getWidth() + rowPadding / pixelStride;
            Bitmap padded = Bitmap.createBitmap(paddedWidth, image.getHeight(), Bitmap.Config.ARGB_8888);
            padded.copyPixelsFromBuffer(buffer);
            bmp = Bitmap.createBitmap(padded, 0, 0, image.getWidth(), image.getHeight());
            padded.recycle();
        } catch (Exception ignored) {
        } finally {
            image.close();
        }
        if (bmp == null) return;
        final Bitmap frame = bmp;
        recognizer.process(InputImage.fromBitmap(frame, 0))
                .addOnSuccessListener(result -> worker.post(() -> handleText(result)))
                .addOnCompleteListener(task -> frame.recycle());
    }

    private void handleText(Text result) {
        String raw = result.getText();
        if (raw == null || raw.isBlank()) return;
        String text = raw.toUpperCase(Locale.ROOT).replace('\u00A0', ' ');

        Double bet = findAmount(text, "APUESTA", "BET", "STAKE");
        Double balance = findAmount(text, "SALDO", "BALANCE", "CREDIT", "CRÉDITO", "CREDITO");
        if (bet != null && bet > 0) lastBet = bet;
        if (balance == null || lastBet <= 0) return;

        if (lastBalance == null) {
            lastBalance = balance;
            return;
        }

        double delta = balance - lastBalance;
        long now = System.currentTimeMillis();
        if (Math.abs(delta) >= 0.005 && now - lastSpinAt >= 700) {
            double prize = Math.max(0, delta + lastBet);
            SessionStore.get().addSpin(lastBet, prize);
            lastSpinAt = now;
            lastBalance = balance;
        }
    }

    private Double findAmount(String text, String... labels) {
        for (String label : labels) {
            int idx = text.indexOf(label);
            if (idx < 0) continue;
            String tail = text.substring(idx + label.length(), Math.min(text.length(), idx + label.length() + 35));
            Matcher m = MONEY.matcher(tail);
            if (m.find()) {
                Double value = parseMoney(m.group(1));
                if (value != null) return value;
            }
        }
        return null;
    }

    private Double parseMoney(String token) {
        try {
            String s = token.replace(" ", "");
            int lastComma = s.lastIndexOf(',');
            int lastDot = s.lastIndexOf('.');
            if (lastComma > lastDot) s = s.replace(".", "").replace(',', '.');
            else if (lastDot > lastComma) s = s.replace(",", "");
            return Double.parseDouble(s);
        } catch (Exception e) {
            return null;
        }
    }

    private Notification notification() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Análisis de sesión", NotificationManager.IMPORTANCE_LOW));
        }
        return new Notification.Builder(this, CHANNEL)
                .setContentTitle("Betano Companion")
                .setContentText("Analizando sesión en pantalla")
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setOngoing(true)
                .build();
    }

    @Override public void onDestroy() {
        if (imageReader != null) imageReader.close();
        if (projection != null) projection.stop();
        if (recognizer != null) recognizer.close();
        if (workerThread != null) workerThread.quitSafely();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
