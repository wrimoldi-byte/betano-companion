package com.walter.betanocompanion;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class GptRecommendationClient {
    public interface Callback { void done(String result); }

    public static void analyze(Context context, String screenText, Callback cb) {
        new Thread(() -> {
            String endpoint = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                    .getString("gpt_endpoint", "").trim();
            if (endpoint.isEmpty()) {
                finish(context, cb, "OCR listo. Falta configurar el endpoint GPT seguro.");
                return;
            }
            try {
                URL url = new URL(endpoint);
                HttpURLConnection c = (HttpURLConnection) url.openConnection();
                c.setConnectTimeout(12000);
                c.setReadTimeout(45000);
                c.setRequestMethod("POST");
                c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                c.setDoOutput(true);
                JSONObject body = new JSONObject();
                body.put("screen_text", screenText);
                try (OutputStream os = c.getOutputStream()) {
                    os.write(body.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
                int code = c.getResponseCode();
                BufferedReader br = new BufferedReader(new InputStreamReader(
                        code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream(),
                        java.nio.charset.StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                if (code < 200 || code >= 300) throw new RuntimeException("HTTP " + code);
                JSONObject out = new JSONObject(sb.toString());
                String result = out.optString("recommendation", "Sin recomendación");
                finish(context, cb, result);
            } catch (Exception e) {
                finish(context, cb, "GPT no disponible. OCR realizado correctamente.");
            }
        }).start();
    }

    private static void finish(Context context, Callback cb, String text) {
        SharedPreferences p = context.getSharedPreferences("session", Context.MODE_PRIVATE);
        p.edit().putString("status", text).apply();
        context.startService(new Intent(context, OverlayService.class).setAction("refresh"));
        if (cb != null) cb.done(text);
    }
}
