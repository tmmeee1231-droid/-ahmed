package com.vanta.remote;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.security.SecureRandom;
import java.util.Locale;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public final class MainActivity extends Activity {
    private static final String SIGNAL_URL = "wss://vanta-remote-server-production.up.railway.app/ws";
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final OkHttpClient http = new OkHttpClient();
    private WebSocket socket;
    private TextView status;
    private TextView codeView;
    private String activeCode;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(32, 48, 32, 32);
        root.setBackgroundColor(0xFF10131A);

        TextView title = new TextView(this);
        title.setText("VANTA Remote");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(28);
        title.setGravity(Gravity.CENTER);
        root.addView(title, matchWrap());

        TextView subtitle = new TextView(this);
        subtitle.setText("اتصال بين جهازين عبر خادم VANTA");
        subtitle.setTextColor(0xFFB8C2D1);
        subtitle.setTextSize(16);
        subtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams sub = matchWrap();
        sub.topMargin = 12;
        root.addView(subtitle, sub);

        status = new TextView(this);
        status.setText("الحالة: غير متصل");
        status.setTextColor(0xFFFFFFFF);
        status.setTextSize(16);
        status.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams st = matchWrap();
        st.topMargin = 36;
        root.addView(status, st);

        codeView = new TextView(this);
        codeView.setText("------");
        codeView.setTextColor(0xFF79D6FF);
        codeView.setTextSize(30);
        codeView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams cp = matchWrap();
        cp.topMargin = 18;
        root.addView(codeView, cp);

        Button create = new Button(this);
        create.setText("إنشاء رمز ربط");
        create.setOnClickListener(v -> connectWithCode(generateCode()));
        addButton(root, create);

        Button join = new Button(this);
        join.setText("إدخال رمز جهاز آخر");
        join.setOnClickListener(v -> showJoinDialog());
        addButton(root, join);

        Button copy = new Button(this);
        copy.setText("نسخ رمز الربط");
        copy.setOnClickListener(v -> copyCode());
        addButton(root, copy);

        Button stop = new Button(this);
        stop.setText("إيقاف الاتصال");
        stop.setOnClickListener(v -> disconnect());
        addButton(root, stop);

        TextView note = new TextView(this);
        note.setText("هذه النسخة تختبر ربط الجهازين فقط. مشاركة الشاشة والتحكم لم يتم تفعيلهما بعد.");
        note.setTextColor(0xFFB8C2D1);
        note.setTextSize(13);
        note.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams np = matchWrap();
        np.topMargin = 28;
        root.addView(note, np);

        setContentView(root);
    }

    private void showJoinDialog() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("مثال: A2BC45");
        input.setTextColor(0xFF10131A);
        input.setHintTextColor(0xFF667085);
        new AlertDialog.Builder(this)
            .setTitle("رمز الجهاز الآخر")
            .setView(input)
            .setNegativeButton("إلغاء", null)
            .setPositiveButton("اتصال", (dialog, which) -> {
                String code = input.getText().toString().trim().toUpperCase(Locale.US);
                if (code.matches("[A-Z0-9]{6}")) connectWithCode(code);
                else setStatus("رمز غير صحيح: أدخل 6 أحرف أو أرقام");
            }).show();
    }

    private void connectWithCode(String code) {
        disconnect();
        activeCode = code;
        codeView.setText(code);
        setStatus("جارٍ الاتصال بالخادم...");
        Request request = new Request.Builder().url(SIGNAL_URL).build();
        socket = http.newWebSocket(request, new WebSocketListener() {
            @Override public void onOpen(WebSocket webSocket, Response response) {
                try {
                    JSONObject message = new JSONObject();
                    message.put("type", "join");
                    message.put("code", code);
                    webSocket.send(message.toString());
                } catch (Exception e) {
                    setStatus("تعذر تجهيز طلب الربط");
                }
            }

            @Override public void onMessage(WebSocket webSocket, String text) {
                runOnUiThread(() -> handleServerMessage(text));
            }

            @Override public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                runOnUiThread(() -> setStatus("فشل الاتصال بالخادم. تحقق من الإنترنت وحاول مجددًا."));
            }

            @Override public void onClosed(WebSocket webSocket, int code, String reason) {
                runOnUiThread(() -> setStatus("تم إغلاق الاتصال"));
            }
        });
    }

    private void handleServerMessage(String raw) {
        try {
            JSONObject message = new JSONObject(raw);
            String type = message.optString("type");
            switch (type) {
                case "connected":
                    setStatus("الخادم متصل، جارٍ تسجيل رمز الربط...");
                    break;
                case "joined":
                    setStatus("تم تسجيل الرمز. افتح التطبيق على الجهاز الآخر وأدخل الرمز.");
                    break;
                case "peer_ready":
                    setStatus("تم ربط الجهازين بنجاح");
                    break;
                case "peer_left":
                    setStatus("الجهاز الآخر قطع الاتصال");
                    break;
                case "error":
                    setStatus("خطأ في الربط: " + message.optString("code"));
                    break;
                case "left":
                    setStatus("الحالة: غير متصل");
                    break;
                default:
                    break;
            }
        } catch (Exception ignored) {
            setStatus("وصل رد غير مفهوم من الخادم");
        }
    }

    private String generateCode() {
        SecureRandom random = new SecureRandom();
        StringBuilder result = new StringBuilder(6);
        for (int i = 0; i < 6; i++) result.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        return result.toString();
    }

    private void copyCode() {
        if (activeCode == null) {
            setStatus("أنشئ رمز ربط أولًا");
            return;
        }
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("VANTA pairing code", activeCode));
        setStatus("تم نسخ رمز الربط");
    }

    private void disconnect() {
        if (socket != null) {
            socket.send("{\"type\":\"leave\"}");
            socket.close(1000, "User disconnected");
            socket = null;
        }
        activeCode = null;
        if (codeView != null) codeView.setText("------");
        if (status != null) setStatus("الحالة: غير متصل");
    }

    private void setStatus(String message) {
        runOnUiThread(() -> status.setText("الحالة: " + message));
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private void addButton(LinearLayout root, Button button) {
        LinearLayout.LayoutParams p = matchWrap();
        p.topMargin = 12;
        root.addView(button, p);
    }

    @Override protected void onDestroy() {
        if (socket != null) socket.close(1000, "Activity destroyed");
        http.dispatcher().executorService().shutdown();
        super.onDestroy();
    }
}
