package com.nseai.analyst;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Locale;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

public class MainActivity extends Activity {
    private WebView webView;
    private static final String PREFS = "nse_ai_secure";
    private static final String KEY_ALIAS = "nse_ai_api_key";

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        webView = new WebView(this);
        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setAllowFileAccess(true);
        ws.setAllowContentAccess(true);
        ws.setBuiltInZoomControls(false);
        ws.setDisplayZoomControls(false);
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());
        webView.addJavascriptInterface(new NativeBridge(this), "Native");
        setContentView(webView);
        webView.loadUrl("file:///android_asset/index.html");
    }

    @Override public void onBackPressed() {
        if (webView.canGoBack()) webView.goBack(); else super.onBackPressed();
    }

    public static class NativeBridge {
        private final Context context;
        private final SharedPreferences prefs;
        NativeBridge(Context c) { context = c; prefs = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }

        @JavascriptInterface public String saveApiKey(String key) {
            try {
                if (key == null || key.trim().isEmpty()) { prefs.edit().remove("api").apply(); return "OK"; }
                prefs.edit().putString("api", encrypt(key.trim())).apply();
                return "OK";
            } catch (Exception e) { return "ERR:" + e.getMessage(); }
        }
        @JavascriptInterface public String hasApiKey() { return getApiKey().isEmpty() ? "0" : "1"; }
        @JavascriptInterface public String clearApiKey() { prefs.edit().remove("api").apply(); return "OK"; }

        @JavascriptInterface public String fetch(String rawUrl) {
            try {
                URL u = new URL(rawUrl);
                String host = u.getHost().toLowerCase(Locale.US);
                if (!(host.equals("query1.finance.yahoo.com") || host.equals("query2.finance.yahoo.com") || host.equals("news.google.com"))) return "{\"error\":\"Host not allowed\"}";
                return request("GET", rawUrl, null, null);
            } catch (Exception e) { return "{\"error\":\"" + esc(e.toString()) + "\"}"; }
        }

        @JavascriptInterface public String ai(String prompt) {
            String key = getApiKey();
            if (key.isEmpty()) return "{\"error\":\"OpenAI API key is not configured. Open Settings and paste your key.\"}";
            try {
                String body = "{\"model\":\"gpt-5.6-luna\",\"input\":\"" + esc(prompt) + "\",\"max_output_tokens\":1800}";
                return request("POST", "https://api.openai.com/v1/responses", body, key);
            } catch (Exception e) { return "{\"error\":\"" + esc(e.toString()) + "\"}"; }
        }

        private String request(String method, String urlString, String body, String bearer) throws Exception {
            HttpURLConnection c = (HttpURLConnection)new URL(urlString).openConnection();
            c.setRequestMethod(method);
            c.setConnectTimeout(12000);
            c.setReadTimeout(30000);
            c.setRequestProperty("Accept", "application/json, application/xml;q=0.9, text/plain;q=0.8");
            c.setRequestProperty("User-Agent", "NSE-AI-Analyst/1.0 (Android)");
            if (bearer != null) {
                c.setRequestProperty("Authorization", "Bearer " + bearer);
                c.setRequestProperty("Content-Type", "application/json");
            }
            if (body != null) {
                c.setDoOutput(true);
                try (OutputStream os = c.getOutputStream()) { os.write(body.getBytes(StandardCharsets.UTF_8)); }
            }
            int code = c.getResponseCode();
            BufferedReader br = new BufferedReader(new InputStreamReader(code >= 400 ? c.getErrorStream() : c.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(); String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();
            return sb.toString();
        }

        private String getApiKey() {
            String x = prefs.getString("api", "");
            if (x.isEmpty()) return "";
            try { return decrypt(x); } catch (Exception e) { return ""; }
        }

        private static SecretKey getKey() throws Exception {
            java.security.KeyStore keyStore = java.security.KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            if (!keyStore.containsAlias(KEY_ALIAS)) {
                KeyGenerator kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
                kg.init(new KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .build());
                return kg.generateKey();
            }
            return ((java.security.KeyStore.SecretKeyEntry) keyStore.getEntry(KEY_ALIAS, null)).getSecretKey();
        }
        private static String encrypt(String plain) throws Exception {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, getKey());
            byte[] iv = cipher.getIV(); byte[] ct = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] all = new byte[iv.length + ct.length]; System.arraycopy(iv,0,all,0,iv.length); System.arraycopy(ct,0,all,iv.length,ct.length);
            return Base64.encodeToString(all, Base64.NO_WRAP);
        }
        private static String decrypt(String encoded) throws Exception {
            byte[] all = Base64.decode(encoded, Base64.NO_WRAP); byte[] iv = new byte[12];
            System.arraycopy(all,0,iv,0,12); byte[] ct = new byte[all.length-12]; System.arraycopy(all,12,ct,0,ct.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.DECRYPT_MODE, getKey(), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(ct), StandardCharsets.UTF_8);
        }
        private static String esc(String s) { return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r"); }
    }
}
