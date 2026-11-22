package com.vanta.remote;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjection;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;
import org.webrtc.*;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;
import java.util.ArrayList;
import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public final class MainActivity extends Activity {
    private static final int CAPTURE_REQUEST = 7401;
    private static final String SIGNAL_URL = "wss://vanta-remote-server-production.up.railway.app/ws";
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private final OkHttpClient http = new OkHttpClient();
    private WebSocket socket;
    private TextView status, codeView;
    private SurfaceViewRenderer remoteView;
    private String activeCode;
    private boolean isRoomCreator;
    private EglBase eglBase;
    private PeerConnectionFactory factory;
    private PeerConnection peer;
    private DataChannel controlChannel;
    private ScreenCapturerAndroid screenCapturer;
    private VideoSource videoSource;
    private VideoTrack localVideo;
    private SurfaceTextureHelper captureHelper;
    private boolean peerReady;
    private final List<IceCandidate> pendingIceCandidates = new ArrayList<>();

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        initializeWebRtc();
        buildUi();
    }

    private void initializeWebRtc() {
        PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(this).createInitializationOptions());
        eglBase = EglBase.create();
        factory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(new DefaultVideoEncoderFactory(eglBase.getEglBaseContext(), true, true))
            .setVideoDecoderFactory(new DefaultVideoDecoderFactory(eglBase.getEglBaseContext()))
            .createPeerConnectionFactory();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(22, 26, 22, 22);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[]{0xFF050507, 0xFF18070D, 0xFF08090D}));

        TextView sigil = new TextView(this);
        sigil.setText("☠");
        sigil.setTextColor(0xFFFF243F);
        sigil.setTextSize(34);
        sigil.setGravity(Gravity.CENTER);
        root.addView(sigil, matchWrap());

        TextView title = new TextView(this);
        title.setText("MANW 😈💪");
        title.setTextColor(0xFFFF2948);
        title.setTextSize(30);
        title.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
        title.setLetterSpacing(0.12f);
        title.setGravity(Gravity.CENTER);
        root.addView(title, matchWrap());

        TextView subtitle = new TextView(this);
        subtitle.setText("V A N T A   //   REMOTE ACCESS");
        subtitle.setTextColor(0xFFB8A0A8);
        subtitle.setTextSize(11);
        subtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams sub = matchWrap(); sub.topMargin = 3;
        root.addView(subtitle, sub);

        status = new TextView(this);
        status.setText("●  غير متصل");
        status.setTextColor(0xFFFFA4B2);
        status.setTextSize(14);
        status.setGravity(Gravity.CENTER);
        status.setPadding(12, 12, 12, 12);
        status.setBackground(roundBg(0xFF211018, 0xFF6D1B32, 18));
        LinearLayout.LayoutParams st = matchWrap(); st.topMargin = 20;
        root.addView(status, st);

        TextView codeLabel = new TextView(this);
        codeLabel.setText("رمز الربط");
        codeLabel.setTextColor(0xFFD4AAB3);
        codeLabel.setTextSize(12);
        codeLabel.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams cl = matchWrap(); cl.topMargin = 16;
        root.addView(codeLabel, cl);

        codeView = new TextView(this);
        codeView.setText("------");
        codeView.setTextColor(0xFFFF3655);
        codeView.setTextSize(31);
        codeView.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        codeView.setGravity(Gravity.CENTER);
        codeView.setPadding(12, 8, 12, 8);
        codeView.setBackground(roundBg(0xFF100B10, 0xFF9B203B, 14));
        LinearLayout.LayoutParams cp = matchWrap(); cp.topMargin = 6;
        root.addView(codeView, cp);

        remoteView = new SurfaceViewRenderer(this);
        remoteView.init(eglBase.getEglBaseContext(), null);
        remoteView.setEnableHardwareScaler(true);
        remoteView.setMirror(false);
        remoteView.setVisibility(View.GONE);
        LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        vp.topMargin = 12; vp.bottomMargin = 12;
        root.addView(remoteView, vp);
        remoteView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                float x = Math.max(0f, Math.min(1f, event.getX() / Math.max(1f, v.getWidth())));
                float y = Math.max(0f, Math.min(1f, event.getY() / Math.max(1f, v.getHeight())));
                sendRemoteTap(x, y);
            }
            return true;
        });

        button("☠  إنشاء رمز ربط", root, () -> connectWithCode(generateCode()));
        button("⌁  إدخال رمز الجهاز الآخر", root, this::showJoinDialog);
        button("◉  مشاركة شاشتي بموافقتي", root, this::requestScreenCapture);
        button("⚙  تفعيل التحكم على هذا الجهاز", root, this::ensureAccessibilityEnabled);
        button("نسخ رمز الربط", root, this::copyCode);
        button("✕  إيقاف الاتصال", root, this::disconnect);

        TextView note = new TextView(this);
        note.setText("مشاركة الشاشة تتطلب موافقتك من أندرويد. التحكم يتطلب تفعيل خدمة VANTA يدويًا من إعدادات إمكانية الوصول.");
        note.setTextColor(0xFFBFAAB0);
        note.setTextSize(11);
        note.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams np = matchWrap(); np.topMargin = 14;
        root.addView(note, np);
        setContentView(root);
    }

    private GradientDrawable roundBg(int fill, int stroke, int radius) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(fill); bg.setCornerRadius(radius);
        bg.setStroke(1, stroke);
        return bg;
    }

    private Button button(String label, LinearLayout root, Runnable action) {
        Button b = new Button(this); b.setText(label); b.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams p = matchWrap(); p.topMargin = 7; root.addView(b, p); return b;
    }

    private boolean isVantaAccessibilityEnabled() {
        String enabled = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabled == null) return false;
        String expected = new android.content.ComponentName(this, VantaAccessibilityService.class).flattenToString();
        for (String item : enabled.split(":")) {
            if (expected.equalsIgnoreCase(item)) return true;
        }
        return false;
    }

    private void ensureAccessibilityEnabled() {
        if (isVantaAccessibilityEnabled() && VantaAccessibilityService.getInstance() != null) {
            new AlertDialog.Builder(this)
                .setTitle("التحكم مفعّل")
                .setMessage("خدمة VANTA شغالة بالفعل على هذا الجهاز. ارجع للتطبيق واستخدم الجهاز الآخر للتحكم.")
                .setPositiveButton("تمام", null)
                .show();
            return;
        }
        if (isVantaAccessibilityEnabled()) {
            new AlertDialog.Builder(this)
                .setTitle("الخدمة بتتصل")
                .setMessage("أندرويد مفعّل الخدمة، لكن لسه ما ربطهاش بالتطبيق. اقفل وافتح التطبيق مرة واحدة. لو استمرت المشكلة افتح إعدادات إمكانية الوصول وتأكد أن VANTA ما زالت مفعّلة.")
                .setPositiveButton("فتح الإعدادات", (d, w) -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)))
                .setNegativeButton("إلغاء", null)
                .show();
            return;
        }
        startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
    }

    private void showJoinDialog() {
        EditText input = new EditText(this); input.setSingleLine(true); input.setHint("مثال: A2BC45");
        new AlertDialog.Builder(this).setTitle("رمز الجهاز الآخر").setView(input)
            .setNegativeButton("إلغاء", null)
            .setPositiveButton("اتصال", (d, w) -> {
                String code = input.getText().toString().trim().toUpperCase(Locale.US);
                if (code.matches("[A-Z0-9]{6}")) connectWithCode(code);
                else setStatus("أدخل رمزًا صحيحًا من 6 خانات");
            }).show();
    }

    private void connectWithCode(String code) {
        disconnect();
        activeCode = code; codeView.setText(code); setStatus("جارٍ الاتصال بالخادم...");
        Request request = new Request.Builder().url(SIGNAL_URL).build();
        socket = http.newWebSocket(request, new WebSocketListener() {
            @Override public void onOpen(WebSocket ws, Response response) {
                try { JSONObject m = new JSONObject(); m.put("type", "join"); m.put("code", code); ws.send(m.toString()); }
                catch (Exception e) { setStatus("تعذر إرسال رمز الربط"); }
            }
            @Override public void onMessage(WebSocket ws, String text) { runOnUiThread(() -> handleServerMessage(text)); }
            @Override public void onFailure(WebSocket ws, Throwable t, Response response) {
                runOnUiThread(() -> setStatus("فشل الاتصال بالخادم. تحقق من الإنترنت."));
            }
            @Override public void onClosed(WebSocket ws, int c, String reason) {
                runOnUiThread(() -> setStatus("تم إغلاق الاتصال"));
            }
        });
    }

    private void handleServerMessage(String raw) {
        try {
            JSONObject m = new JSONObject(raw);
            String type = m.optString("type");
            switch (type) {
                case "connected": setStatus("متصل بالخادم"); break;
                case "joined":
                    isRoomCreator = m.optInt("peers", 0) == 1;
                    setStatus("تم تسجيل الرمز. انتظر الجهاز الآخر."); break;
                case "peer_ready":
                    peerReady = true; createPeerConnection();
                    setStatus(isRoomCreator ? "الجهاز الآخر اتصل. اضغط مشاركة شاشتي." : "تم الاتصال. في انتظار مشاركة الشاشة.");
                    break;
                case "offer": receiveOffer(m.optJSONObject("payload")); break;
                case "answer": receiveAnswer(m.optJSONObject("payload")); break;
                case "ice": receiveIce(m.optJSONObject("payload")); break;
                case "peer_left": peerReady = false; closePeer(); setStatus("الجهاز الآخر قطع الاتصال"); break;
                case "error": setStatus("خطأ: " + m.optString("code")); break;
                case "left": setStatus("غير متصل"); break;
            }
        } catch (Exception e) { setStatus("رد غير مفهوم من الخادم"); }
    }

    private void createPeerConnection() {
        closePeer();
        pendingIceCandidates.clear();
        PeerConnection.RTCConfiguration config = new PeerConnection.RTCConfiguration(Collections.singletonList(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()));
        config.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;
        peer = factory.createPeerConnection(config, new PeerConnection.Observer() {
            @Override public void onSignalingChange(PeerConnection.SignalingState s) { }
            @Override public void onIceConnectionChange(PeerConnection.IceConnectionState s) {
                runOnUiThread(() -> setStatus("اتصال الفيديو: " + s.toString()));
            }
            @Override public void onStandardizedIceConnectionChange(PeerConnection.IceConnectionState s) { }
            @Override public void onConnectionChange(PeerConnection.PeerConnectionState s) { }
            @Override public void onIceConnectionReceivingChange(boolean b) { }
            @Override public void onIceGatheringChange(PeerConnection.IceGatheringState s) { }
            @Override public void onIceCandidate(IceCandidate c) {
                try { JSONObject p = new JSONObject(); p.put("sdpMid", c.sdpMid); p.put("sdpMLineIndex", c.sdpMLineIndex); p.put("sdp", c.sdp); sendSignal("ice", p); }
                catch (Exception ignored) { }
            }
            @Override public void onIceCandidatesRemoved(IceCandidate[] c) { }
            @Override public void onAddStream(MediaStream s) { }
            @Override public void onRemoveStream(MediaStream s) { }
            @Override public void onDataChannel(DataChannel dc) { controlChannel = dc; observeControlChannel(dc); }
            @Override public void onRenegotiationNeeded() { }
            @Override public void onAddTrack(RtpReceiver r, MediaStream[] s) { }
            @Override public void onTrack(RtpTransceiver transceiver) {
                MediaStreamTrack track = transceiver.getReceiver().track();
                if (track instanceof VideoTrack) {
                    ((VideoTrack) track).addSink(remoteView);
                    runOnUiThread(() -> remoteView.setVisibility(View.VISIBLE));
                }
            }
        });
        if (peer != null && isRoomCreator) {
            DataChannel.Init init = new DataChannel.Init();
            controlChannel = peer.createDataChannel("vanta-control", init);
            observeControlChannel(controlChannel);
        }
    }

    private void requestScreenCapture() {
        if (!peerReady || peer == null) { setStatus("اربط الجهازين أولًا"); return; }
        if (!isRoomCreator) { setStatus("ابدأ مشاركة الشاشة من الجهاز الذي أنشأ رمز الربط"); return; }
        startActivityForResult(((android.media.projection.MediaProjectionManager)getSystemService(Context.MEDIA_PROJECTION_SERVICE))
            .createScreenCaptureIntent(), CAPTURE_REQUEST);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != CAPTURE_REQUEST) return;
        if (resultCode != RESULT_OK || data == null) {
            setStatus("تم رفض إذن مشاركة الشاشة");
            return;
        }
        if (peer == null || !peerReady) {
            setStatus("الاتصال بالجهاز الآخر انقطع. أعد الاتصال ثم جرّب مشاركة الشاشة.");
            return;
        }
        try {
            Intent projectionService = new Intent(this, VantaProjectionService.class);
            if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(projectionService);
            else startService(projectionService);

            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (isFinishing() || isDestroyed() || peer == null || !peerReady) {
                    setStatus("الاتصال انتهى قبل بدء مشاركة الشاشة");
                    stopService(new Intent(this, VantaProjectionService.class));
                    return;
                }
                try {
                    screenCapturer = new ScreenCapturerAndroid(data, new MediaProjection.Callback() {
                        @Override public void onStop() {
                            runOnUiThread(() -> {
                                setStatus("توقفت مشاركة الشاشة");
                                stopService(new Intent(MainActivity.this, VantaProjectionService.class));
                            });
                        }
                    });
                    captureHelper = SurfaceTextureHelper.create("VANTA-ScreenCapture", eglBase.getEglBaseContext());
                    videoSource = factory.createVideoSource(true);
                    screenCapturer.initialize(captureHelper, getApplicationContext(), videoSource.getCapturerObserver());
                    screenCapturer.startCapture(1280, 720, 15);
                    localVideo = factory.createVideoTrack("vanta-screen", videoSource);
                    peer.addTrack(localVideo, Arrays.asList("vanta-stream"));
                    createOffer();
                    setStatus("مشاركة الشاشة بدأت");
                } catch (Throwable captureError) {
                    android.util.Log.e("VANTA-CAPTURE", "Screen capture startup failed", captureError);
                    setStatus("فشل بدء مشاركة الشاشة: " + captureError.getClass().getSimpleName()
                        + " — " + String.valueOf(captureError.getMessage()));
                    closeScreenCapture();
                    new AlertDialog.Builder(MainActivity.this)
                        .setTitle("تعذر بدء مشاركة الشاشة")
                        .setMessage(captureError.getClass().getSimpleName() + ": " + String.valueOf(captureError.getMessage()))
                        .setPositiveButton("حسنًا", null)
                        .show();
                }
            }, 800);
        } catch (Throwable e) {
            android.util.Log.e("VANTA-CAPTURE", "Could not start projection foreground service", e);
            setStatus("تعذر تشغيل خدمة مشاركة الشاشة: " + e.getClass().getSimpleName()
                + " — " + String.valueOf(e.getMessage()));
            closeScreenCapture();
            new AlertDialog.Builder(this)
                .setTitle("تعذر تشغيل مشاركة الشاشة")
                .setMessage(e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage()))
                .setPositiveButton("حسنًا", null)
                .show();
        }
    }

    private void closeScreenCapture() {
        if (screenCapturer != null) {
            try { screenCapturer.stopCapture(); } catch (Exception ignored) { }
            try { screenCapturer.dispose(); } catch (Exception ignored) { }
            screenCapturer = null;
        }
        if (captureHelper != null) { captureHelper.dispose(); captureHelper = null; }
        if (localVideo != null) { localVideo.dispose(); localVideo = null; }
        if (videoSource != null) { videoSource.dispose(); videoSource = null; }
        stopService(new Intent(this, VantaProjectionService.class));
    }

    private void createOffer() {
        peer.createOffer(new SimpleSdpObserver() {
            @Override public void onCreateSuccess(SessionDescription sdp) {
                peer.setLocalDescription(new SimpleSdpObserver() {
                    @Override public void onSetSuccess() {
                        try { JSONObject p = new JSONObject(); p.put("type", sdp.type.canonicalForm()); p.put("sdp", sdp.description); sendSignal("offer", p); }
                        catch (Exception ignored) { }
                    }
                }, sdp);
            }
        }, new MediaConstraints());
    }

    private void receiveOffer(JSONObject p) {
        if (p == null || peer == null) return;
        SessionDescription offer = new SessionDescription(SessionDescription.Type.fromCanonicalForm(p.optString("type", "offer")), p.optString("sdp"));
        peer.setRemoteDescription(new SimpleSdpObserver() {
            @Override public void onSetSuccess() {
                flushPendingIceCandidates();
                peer.createAnswer(new SimpleSdpObserver() {
                    @Override public void onCreateSuccess(SessionDescription answer) {
                        peer.setLocalDescription(new SimpleSdpObserver() {
                            @Override public void onSetSuccess() {
                                try { JSONObject a = new JSONObject(); a.put("type", answer.type.canonicalForm()); a.put("sdp", answer.description); sendSignal("answer", a); }
                                catch (Exception ignored) { }
                            }
                        }, answer);
                    }
                }, new MediaConstraints());
            }
        }, offer);
    }

    private void receiveAnswer(JSONObject p) {
        if (p == null || peer == null) return;
        SessionDescription answer = new SessionDescription(SessionDescription.Type.fromCanonicalForm(p.optString("type", "answer")), p.optString("sdp"));
        peer.setRemoteDescription(new SimpleSdpObserver() { @Override public void onSetSuccess() { flushPendingIceCandidates(); } }, answer);
    }

    private void receiveIce(JSONObject p) {
        if (p == null || peer == null) return;
        IceCandidate candidate = new IceCandidate(p.optString("sdpMid"), p.optInt("sdpMLineIndex"), p.optString("sdp"));
        if (peer.getRemoteDescription() == null) pendingIceCandidates.add(candidate);
        else peer.addIceCandidate(candidate);
    }

    private void flushPendingIceCandidates() {
        if (peer == null || peer.getRemoteDescription() == null) return;
        for (IceCandidate candidate : pendingIceCandidates) peer.addIceCandidate(candidate);
        pendingIceCandidates.clear();
    }

    private void sendSignal(String type, JSONObject payload) {
        try { if (socket != null) { JSONObject m = new JSONObject(); m.put("type", type); m.put("payload", payload); socket.send(m.toString()); } }
        catch (Exception ignored) { }
    }

    private void observeControlChannel(DataChannel dc) {
        if (dc == null) return;
        dc.registerObserver(new DataChannel.Observer() {
            @Override public void onBufferedAmountChange(long amount) { }
            @Override public void onStateChange() { }
            @Override public void onMessage(DataChannel.Buffer buffer) {
                if (!buffer.binary) {
                    byte[] bytes = new byte[buffer.data.remaining()]; buffer.data.get(bytes);
                    VantaAccessibilityService service = VantaAccessibilityService.getInstance();
                    if (service != null) service.handleControlMessage(new String(bytes, StandardCharsets.UTF_8));
                }
            }
        });
    }

    private void sendRemoteTap(float x, float y) {
        if (isRoomCreator || controlChannel == null || controlChannel.state() != DataChannel.State.OPEN) return;
        try {
            JSONObject m = new JSONObject(); m.put("action", "tap"); m.put("x", x); m.put("y", y);
            ByteBuffer bytes = ByteBuffer.wrap(m.toString().getBytes(StandardCharsets.UTF_8));
            controlChannel.send(new DataChannel.Buffer(bytes, false));
        } catch (Exception ignored) { }
    }

    private void copyCode() {
        if (activeCode == null) { setStatus("أنشئ رمز ربط أولًا"); return; }
        ClipboardManager cm = (ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("VANTA pairing code", activeCode)); setStatus("تم نسخ الرمز");
    }

    private String generateCode() {
        SecureRandom r = new SecureRandom(); StringBuilder b = new StringBuilder(6);
        for (int i=0;i<6;i++) b.append(ALPHABET.charAt(r.nextInt(ALPHABET.length())));
        return b.toString();
    }

    private void disconnect() {
        if (socket != null) { socket.send("{\"type\":\"session_end\"}"); socket.close(1000, "User disconnected"); socket = null; }
        peerReady = false; closePeer(); activeCode = null;
        if (codeView != null) codeView.setText("------");
        if (remoteView != null) remoteView.setVisibility(View.GONE);
        if (status != null) setStatus("غير متصل");
    }

    private void closePeer() {
        if (screenCapturer != null) { try { screenCapturer.stopCapture(); } catch (Exception ignored) { } try { screenCapturer.dispose(); } catch (Exception ignored) { } screenCapturer = null; }
        if (captureHelper != null) { captureHelper.dispose(); captureHelper = null; }
        if (localVideo != null) { localVideo.dispose(); localVideo = null; }
        if (videoSource != null) { videoSource.dispose(); videoSource = null; }
        if (controlChannel != null) { controlChannel.close(); controlChannel.dispose(); controlChannel = null; }
        if (peer != null) { peer.close(); peer.dispose(); peer = null; }
        pendingIceCandidates.clear();
        stopService(new Intent(this, VantaProjectionService.class));
    }

    private void setStatus(String s) { runOnUiThread(() -> { if (status != null) status.setText("الحالة: " + s); }); }
    private LinearLayout.LayoutParams matchWrap() { return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); }

    @Override protected void onDestroy() {
        closePeer();
        if (socket != null) socket.close(1000, "Activity destroyed");
        if (remoteView != null) remoteView.release();
        if (factory != null) factory.dispose();
        if (eglBase != null) eglBase.release();
        http.dispatcher().executorService().shutdown();
        super.onDestroy();
    }

    private static class SimpleSdpObserver implements SdpObserver {
        @Override public void onCreateSuccess(SessionDescription s) { }
        @Override public void onSetSuccess() { }
        @Override public void onCreateFailure(String error) { }
        @Override public void onSetFailure(String error) { }
    }
}
