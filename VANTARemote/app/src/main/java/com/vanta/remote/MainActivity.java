package com.vanta.remote;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class MainActivity extends Activity {
    private TextView status;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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
        subtitle.setText("اتصال آمن بين أجهزتك");
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
        st.topMargin = 40;
        root.addView(status, st);

        Button pair = new Button(this);
        pair.setText("ربط جهاز");
        pair.setOnClickListener(v -> status.setText("ميزة الربط قيد التجهيز"));
        addButton(root, pair);

        Button screen = new Button(this);
        screen.setText("مشاركة الشاشة");
        screen.setOnClickListener(v -> status.setText("مشاركة الشاشة غير مفعلة في هذه النسخة"));
        addButton(root, screen);

        Button stop = new Button(this);
        stop.setText("إيقاف الاتصال");
        stop.setOnClickListener(v -> status.setText("الحالة: غير متصل"));
        addButton(root, stop);
        setContentView(root);
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private void addButton(LinearLayout root, Button button) {
        LinearLayout.LayoutParams p = matchWrap();
        p.topMargin = 16;
        root.addView(button, p);
    }
}
