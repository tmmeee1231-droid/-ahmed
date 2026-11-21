package com.vanta.remote;

import android.app.Activity;
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
    private boolean peerReady;

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
        root.setPadding(24, 32, 24, 24);
        root.setBackgroundColor(0xFF10131A);

        TextView title = new TextView(this);
        title.setText("VANTA Remote");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(27);
        title.setGravity(Gravity.CENTER);
        root.addView(title, matchWrap());

        status = new TextView(this);
        status.setText("الحالة: غير متصل");
        status.setTextColor(0xFFFFFFFF);
        status.setTextSize(15);
        status.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams st = matchWrap(); st.topMargin = 14;
        root.addView(status, st);

        codeView = new TextView(this);
        codeView.setText("------");
        codeView.setTextColor(0xFF79D6FF);
        codeView.setTextSize(28);
        codeView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams cp = matchWrap(); cp.topMargin = 8;
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

        Button create = button("إنشاء رمز ربط", root, () -> connectWithCode(generateCode()));
        Button join = button("إدخال رمز جهاز آخر", root, this::showJoinDialog);
        button("مشاركة شاشتي (بموافقتي)", root, this::requestScreenCapture);
        button("تفعيل التحكم على هذا الجهاز", root, () -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        button("نسخ رمز الربط", root, this::copyCode);
        button("إيقاف الاتصال", root, this::disconnect);

        TextView note = new TextView(this);
        note.setText("مشاركة الشاشة لا تبدأ إلا بعد موافقة أندرويد. التحكم باللمس يتطلب تفعيل خدمة VANTA يدويًا من إعدادات إمكانية الوصول على الجهاز المستقبِل للتحكم.");
        note.setTextColor(0xFFB8C2D1);
        note.setTextSize(12);
        note.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams np = matchWrap(); np.topMargin = 12;
        root.addView(note, np);
        setContentView(root);
    }

    private Button button(String label, LinearLayout root, Runnable action) {
        Button b = new Button(this); b.setText(label); b.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams p = matchWrap(); p.topMargin = 7; root.addView(b, p); return b;
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
        if (resultCode != RESULT_OK || data == null) { setStatus("تم رفض إذن مشاركة الشاشة"); return; }
        try {
            screenCapturer = new ScreenCapturerAndroid(data, new MediaProjection.Callback() {
                @Override public void onStop() { runOnUiThread(() -> setStatus("توقفت مشاركة الشاشة")); }
            });
            SurfaceTextureHelper helper = SurfaceTextureHelper.create("VANTA-ScreenCapture", eglBase.getEglBaseContext());
            videoSource = factory.createVideoSource(true);
            screenCapturer.initialize(helper, getApplicationContext(), videoSource.getCapturerObserver());
            screenCapturer.startCapture(1280, 720, 15);
            localVideo = factory.createVideoTrack("vanta-screen", videoSource);
            peer.addTrack(localVideo, Arrays.asList("vanta-stream"));
            createOffer();
            setStatus("بدأت مشاركة الشاشة بعد موافقتك");
        } catch (Exception e) { setStatus("تعذر بدء مشاركة الشاشة: " + e.getMessage()); }
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
        peer.setRemoteDescription(new SimpleSdpObserver(), answer);
    }

    private void receiveIce(JSONObject p) {
        if (p == null || peer == null) return;
        peer.addIceCandidate(new IceCandidate(p.optString("sdpMid"), p.optInt("sdpMLineIndex"), p.optString("sdp")));
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
        if (screenCapturer != null) { try { screenCapturer.stopCapture(); screenCapturer.dispose(); } catch (Exception ignored) { } screenCapturer = null; }
        if (localVideo != null) { localVideo.dispose(); localVideo = null; }
        if (videoSource != null) { videoSource.dispose(); videoSource = null; }
        if (controlChannel != null) { controlChannel.close(); controlChannel.dispose(); controlChannel = null; }
        if (peer != null) { peer.close(); peer.dispose(); peer = null; }
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
