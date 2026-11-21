package com.vanta.remote;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.view.accessibility.AccessibilityEvent;

import org.json.JSONObject;

public final class VantaAccessibilityService extends AccessibilityService {
    private static volatile VantaAccessibilityService instance;

    public static VantaAccessibilityService getInstance() { return instance; }

    @Override public void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }
    @Override public void onInterrupt() { }

    @Override public void onDestroy() {
        if (instance == this) instance = null;
        super.onDestroy();
    }

    public void performRemoteTap(float normalizedX, float normalizedY) {
        float x = Math.max(0f, Math.min(1f, normalizedX)) * getResources().getDisplayMetrics().widthPixels;
        float y = Math.max(0f, Math.min(1f, normalizedY)) * getResources().getDisplayMetrics().heightPixels;
        Path path = new Path();
        path.moveTo(x, y);
        GestureDescription gesture = new GestureDescription.Builder()
            .addStroke(new GestureDescription.StrokeDescription(path, 0, 90))
            .build();
        dispatchGesture(gesture, null, null);
    }

    public void handleControlMessage(String raw) {
        try {
            JSONObject data = new JSONObject(raw);
            if ("tap".equals(data.optString("action"))) {
                performRemoteTap((float) data.optDouble("x"), (float) data.optDouble("y"));
            }
        } catch (Exception ignored) { }
    }
}
