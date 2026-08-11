package com.walter.betanocompanion;

import android.app.Activity;
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
        int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED);
        Intent data;
        if (Build.VERSION.SDK_INT >= 33) data = intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent.class);
        else data = intent.getParcelableExtra(EXTRA_RESULT_DATA);

        if (resultCode != Activity.RESULT_OK || data == null) {
            stopSelf();
            return START_NOT_STICKY;
        }

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
        if (now - lastProcess < 700) { image.close(); return; }
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

        boolean bonusMode = containsAny(text,
                "TIRADAS GRATIS", "FREE SPINS", "FREESPINS",
                "JOGADAS GRATIS", "RODADAS GRATIS", "BONUS ACTIVE");

        Double bet = findAmount(text, "APUESTA", "BET", "STAKE");
        Double balance = findAmount(text, "CRÉDITO", "CREDITO", "SALDO", "BALANCE", "CREDIT");
        if (bet != null && bet > 0) lastBet = bet;
        if (balance == null || lastBet <= 0) return;

        if (lastBalance == null) {
            lastBalance = balance;
            return;
        }

        double delta = balance - lastBalance;
        long now = System.currentTimeMillis();
        if (Math.abs(delta) < minimumMovement(lastBet)) return;
        if (now - lastSpinAt < 600) return;

        if (bonusMode) {
            lastBalance = balance;
            lastSpinAt = now;
            return;
        }

        Double prize = classifyPrize(delta, lastBet);
        if (prize == null) {
            lastBalance = balance;
            return;
        }

        SessionStore.get().addSpin(lastBet, prize);
        lastSpinAt = now;
        lastBalance = balance;
    }

    private boolean containsAny(String text, String... probes) {
        for (String probe : probes) if (text.contains(probe)) return true;
        return false;
    }

    private double minimumMovement(double bet) {
        return Math.max(0.50, bet * 0.08);
    }

    private Double classifyPrize(double delta, double bet) {
        double tolerance = Math.max(1.0, bet * 0.12);
        double expectedLoss = -bet;

        // Una caída cercana al valor de la apuesta es una tirada sin premio.
        if (Math.abs(delta - expectedLoss) <= tolerance) {
            return 0.0;
        }

        // Saltos negativos imposibles o lecturas muy desviadas del OCR se ignoran.
        if (delta < expectedLoss - (tolerance * 2.5)) {
            return null;
        }

        double prize = delta + bet;
        if (prize <= tolerance) {
            return 0.0;
        }

        // Si el saldo igualmente terminó bajando y el supuesto premio es mínimo,
        // preferimos clasificarlo como pérdida para evitar falsos positivos del OCR.
        if (delta < 0 && prize < Math.max(tolerance * 2.0, bet * 0.25)) {
            return 0.0;
        }

        return Math.max(0.0, prize);
    }

    private Double findAmount(String text, String... labels) {
        for (String label : labels) {
            int idx = text.indexOf(label);
            if (idx < 0) continue;
            String tail = text.substring(idx + label.length(), Math.min(text.length(), idx + label.length() + 45));
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
                .setContentText("Lector de pantalla activo")
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
