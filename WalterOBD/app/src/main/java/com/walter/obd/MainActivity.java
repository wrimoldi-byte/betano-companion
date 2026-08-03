package com.walter.obd;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int REQ_BT = 1001;
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private BluetoothSocket socket;
    private InputStream input;
    private OutputStream output;
    private volatile boolean reading;

    private TextView status;
    private TextView rpm;
    private TextView speed;
    private TextView coolant;
    private TextView voltage;
    private TextView raw;
    private Button connectButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildUi());
        connectButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { ensurePermissionAndConnect(); }
        });
    }

    private View buildUi() {
        int pad = dp(18);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        root.setBackgroundColor(Color.rgb(238, 243, 248));

        TextView title = new TextView(this);
        title.setText("WALTER OBD");
        title.setTextSize(28);
        title.setTextColor(Color.rgb(13, 27, 42));
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        title.setPadding(0, 0, 0, dp(12));
        root.addView(title, fullWidth());

        status = text("Estado: desconectado", 17, Color.DKGRAY);
        status.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(status, fullWidth());

        connectButton = new Button(this);
        connectButton.setText("CONECTAR A OBDII");
        LinearLayout.LayoutParams buttonParams = fullWidth();
        buttonParams.setMargins(0, dp(12), 0, dp(14));
        root.addView(connectButton, buttonParams);

        rpm = addCard(root, "RPM", "—");
        speed = addCard(root, "VELOCIDAD", "— km/h");
        coolant = addCard(root, "TEMPERATURA MOTOR", "— °C");
        voltage = addCard(root, "VOLTAJE ELM", "— V");

        TextView rawTitle = text("Última respuesta", 14, Color.DKGRAY);
        rawTitle.setPadding(0, dp(10), 0, dp(4));
        root.addView(rawTitle, fullWidth());
        raw = text("Sin datos", 12, Color.rgb(60, 60, 60));
        raw.setBackgroundColor(Color.WHITE);
        raw.setPadding(dp(10), dp(10), dp(10), dp(10));
        root.addView(raw, fullWidth());

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        return scroll;
    }

    private TextView addCard(LinearLayout root, String label, String initial) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(12), dp(16), dp(12));
        card.setBackgroundColor(Color.WHITE);

        TextView name = text(label, 13, Color.GRAY);
        TextView value = text(initial, 30, Color.rgb(21, 101, 192));
        card.addView(name, fullWidth());
        card.addView(value, fullWidth());

        LinearLayout.LayoutParams lp = fullWidth();
        lp.setMargins(0, dp(6), 0, dp(6));
        root.addView(card, lp);
        return value;
    }

    private TextView text(String value, int size, int color) {
        TextView tv = new TextView(this);
        tv.setText(value);
        tv.setTextSize(size);
        tv.setTextColor(color);
        return tv;
    }

    private LinearLayout.LayoutParams fullWidth() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void ensurePermissionAndConnect() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, REQ_BT);
            return;
        }
        connect();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_BT && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            connect();
        } else if (requestCode == REQ_BT) {
            Toast.makeText(this, "Se necesita permiso para conectar con OBDII", Toast.LENGTH_LONG).show();
        }
    }

    private void connect() {
        setStatus("Buscando dispositivo OBDII vinculado…");
        connectButton.setEnabled(false);
        executor.execute(new Runnable() {
            @Override public void run() {
                try {
                    closeConnection();
                    BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
                    if (adapter == null) throw new IOException("Este teléfono no tiene Bluetooth");
                    if (!adapter.isEnabled()) throw new IOException("Activá Bluetooth primero");

                    BluetoothDevice target = findObdDevice(adapter.getBondedDevices());
                    if (target == null) throw new IOException("No encontré un dispositivo vinculado llamado OBDII");

                    socket = target.createRfcommSocketToServiceRecord(SPP_UUID);
                    adapter.cancelDiscovery();
                    socket.connect();
                    input = socket.getInputStream();
                    output = socket.getOutputStream();

                    initializeElm();
                    reading = true;
                    setStatus("Conectado a " + target.getName());
                    main.post(new Runnable() {
                        @Override public void run() { connectButton.setText("RECONECTAR"); connectButton.setEnabled(true); }
                    });
                    pollLoop();
                } catch (Exception e) {
                    reading = false;
                    setStatus("Error: " + e.getMessage());
                    main.post(new Runnable() {
                        @Override public void run() { connectButton.setEnabled(true); }
                    });
                    closeConnection();
                }
            }
        });
    }

    private BluetoothDevice findObdDevice(Set<BluetoothDevice> devices) {
        BluetoothDevice fallback = null;
        for (BluetoothDevice d : devices) {
            String name = d.getName();
            if (name == null) continue;
            String n = name.toUpperCase(Locale.ROOT);
            if (n.equals("OBDII")) return d;
            if (n.contains("OBD") || n.contains("ELM")) fallback = d;
        }
        return fallback;
    }

    private void initializeElm() throws IOException, InterruptedException {
        command("ATZ", 1800);
        command("ATE0", 700);
        command("ATL0", 500);
        command("ATS0", 500);
        command("ATH0", 500);
        command("ATSP0", 1200);
    }

    private void pollLoop() throws IOException, InterruptedException {
        while (reading && socket != null && socket.isConnected()) {
            String rRpm = command("010C", 900);
            updateRaw(rRpm);
            Integer rpmValue = parseRpm(rRpm);
            if (rpmValue != null) update(rpm, rpmValue + "");

            String rSpeed = command("010D", 900);
            updateRaw(rSpeed);
            Integer speedValue = parseSingleBytePid(rSpeed, "410D");
            if (speedValue != null) update(speed, speedValue + " km/h");

            String rTemp = command("0105", 900);
            updateRaw(rTemp);
            Integer tempValue = parseSingleBytePid(rTemp, "4105");
            if (tempValue != null) update(coolant, (tempValue - 40) + " °C");

            String rVoltage = command("ATRV", 900);
            updateRaw(rVoltage);
            String volts = parseVoltage(rVoltage);
            if (volts != null) update(voltage, volts + " V");

            Thread.sleep(250);
        }
    }

    private synchronized String command(String command, long timeoutMs) throws IOException, InterruptedException {
        while (input.available() > 0) input.read();
        output.write((command + "\r").getBytes(StandardCharsets.US_ASCII));
        output.flush();

        StringBuilder response = new StringBuilder();
        long end = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < end) {
            while (input.available() > 0) {
                int b = input.read();
                if (b < 0) throw new IOException("Conexión cerrada");
                char c = (char) b;
                if (c == '>') return response.toString();
                response.append(c);
            }
            Thread.sleep(20);
        }
        return response.toString();
    }

    private String clean(String response) {
        return response.toUpperCase(Locale.ROOT)
                .replace("\r", "")
                .replace("\n", "")
                .replace(" ", "")
                .replace(">", "");
    }

    private Integer parseRpm(String response) {
        String c = clean(response);
        int i = c.indexOf("410C");
        if (i < 0 || c.length() < i + 8) return null;
        try {
            int a = Integer.parseInt(c.substring(i + 4, i + 6), 16);
            int b = Integer.parseInt(c.substring(i + 6, i + 8), 16);
            return ((a * 256) + b) / 4;
        } catch (Exception ignored) { return null; }
    }

    private Integer parseSingleBytePid(String response, String marker) {
        String c = clean(response);
        int i = c.indexOf(marker);
        if (i < 0 || c.length() < i + 6) return null;
        try { return Integer.parseInt(c.substring(i + 4, i + 6), 16); }
        catch (Exception ignored) { return null; }
    }

    private String parseVoltage(String response) {
        String normalized = response.toUpperCase(Locale.ROOT).replace("\r", " ").replace("\n", " ").trim();
        for (String part : normalized.split("\\s+")) {
            if (part.endsWith("V")) {
                try {
                    double v = Double.parseDouble(part.substring(0, part.length() - 1));
                    return String.format(Locale.getDefault(), "%.1f", v);
                } catch (Exception ignored) { }
            }
        }
        return null;
    }

    private void update(final TextView view, final String value) {
        main.post(new Runnable() { @Override public void run() { view.setText(value); } });
    }

    private void updateRaw(final String value) {
        final String shown = value.replace("\r", " ").replace("\n", " ").trim();
        if (!shown.isEmpty()) update(raw, shown);
    }

    private void setStatus(final String value) {
        main.post(new Runnable() { @Override public void run() { status.setText("Estado: " + value); } });
    }

    private void closeConnection() {
        reading = false;
        try { if (input != null) input.close(); } catch (Exception ignored) { }
        try { if (output != null) output.close(); } catch (Exception ignored) { }
        try { if (socket != null) socket.close(); } catch (Exception ignored) { }
        input = null;
        output = null;
        socket = null;
    }

    @Override
    protected void onDestroy() {
        closeConnection();
        executor.shutdownNow();
        super.onDestroy();
    }
}
