package com.vanta.remote;

import android.app.Application;
import android.content.SharedPreferences;
import android.content.Context;
import android.util.Log;
import java.io.PrintWriter;
import java.io.StringWriter;

public final class VantaApplication extends Application {
    private static final String PREFS = "vanta_diagnostics";
    private static final String KEY_STAGE = "last_capture_stage";
    private static final String KEY_CRASH = "last_java_crash";

    @Override public void onCreate() {
        super.onCreate();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            try {
                StringWriter sw = new StringWriter();
                error.printStackTrace(new PrintWriter(sw));
                getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putString(KEY_CRASH, "Thread: " + thread.getName() + "\n" + sw).commit();
                Log.e("VANTA-CRASH", "Uncaught application exception", error);
            } catch (Throwable ignored) { }
            android.os.Process.killProcess(android.os.Process.myPid());
            System.exit(10);
        });
    }

    public static void mark(Context app, String stage) {
        try {
            app.getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_STAGE, stage).commit();
            Log.i("VANTA-DIAG", stage);
        } catch (Throwable ignored) { }
    }

    public static String previousReport(Context app) {
        SharedPreferences p = app.getSharedPreferences(PREFS, MODE_PRIVATE);
        String stage = p.getString(KEY_STAGE, "");
        String crash = p.getString(KEY_CRASH, "");
        boolean captureStage = stage != null && stage.startsWith("CAPTURE_") && !"CAPTURE_OK".equals(stage);
        p.edit().remove(KEY_STAGE).remove(KEY_CRASH).commit();
        if (!captureStage && (crash == null || crash.isEmpty())) return null;
        return "آخر مرحلة محفوظة: " + (stage == null || stage.isEmpty() ? "غير محددة" : stage)
            + ((crash == null || crash.isEmpty()) ? "" : "\n\nتفاصيل الخطأ:\n" + crash);
    }
}
