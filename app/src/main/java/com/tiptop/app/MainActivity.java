package com.tiptop.app;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

public class MainActivity extends Activity {
    private static final String[] SPEED_LABELS = {"Gentle", "Smooth", "Balanced", "Fast", "Turbo"};
    private SharedPreferences prefs;
    private TextView status;
    private LinearLayout content;
    private final int ink = 0xff172033;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("settings", MODE_PRIVATE);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(28), dp(24), dp(24));
        content.setBackgroundColor(0xfff5f7fb);
        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.addView(content);
        setContentView(scroll);
        label("TipTop", 32, true);
        label("Tap the small bar near the top of any app to return to the start of a list.", 16, false);
        status = label("", 16, true);
        Button access = new Button(this);
        access.setText("Open accessibility settings");
        access.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        content.addView(access);
        label("TipTop needs accessibility access to find the current list and request scrolling. It does not collect content or connect to the internet.", 14, false);
        Switch enabled = new Switch(this);
        enabled.setText("Show tap bar");
        enabled.setTextColor(ink);
        enabled.setChecked(prefs.getBoolean("enabled", true));
        enabled.setOnCheckedChangeListener((button, checked) -> save("enabled", checked));
        content.addView(enabled);
        speedSlider();
        slider("Width", "width", 40, 240, 100);
        slider("Height", "height", 24, 80, 36);
        slider("Distance from screen top", "offset", 0, 80, 8);
        slider("Opacity", "opacity", 15, 100, 70);
        label("Position", 18, true);
        LinearLayout row = new LinearLayout(this);
        for (String value : new String[]{"Left", "Center", "Right"}) {
            Button button = new Button(this);
            button.setText(value);
            button.setOnClickListener(v -> save("position", value));
            row.addView(button, new LinearLayout.LayoutParams(0, dp(52), 1));
        }
        content.addView(row);
        label("Some apps do not expose scroll controls to Android accessibility. The tap bar will have no effect in those lists.", 14, false);
    }

    @Override protected void onResume() {
        super.onResume();
        String services = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        String name = new ComponentName(this, TopService.class).flattenToString();
        boolean enabledInSettings = services != null && services.contains(name);
        status.setText(TopService.connected ? "Service is running" :
            enabledInSettings ? "Service is enabled but not running. Turn TipTop off and on in Accessibility settings." :
                "Service is off — enable TipTop in Accessibility settings");
    }

    private TextView label(String text, int size, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(ink);
        if (bold) view.setTypeface(null, 1);
        view.setPadding(0, dp(8), 0, dp(8));
        content.addView(view);
        return view;
    }

    private void speedSlider() {
        TextView caption = label("", 18, true);
        SeekBar bar = new SeekBar(this);
        bar.setMax(SPEED_LABELS.length - 1);
        bar.setProgress(Math.max(0, Math.min(SPEED_LABELS.length - 1, prefs.getInt("speed", 3))));
        caption.setText("Scroll speed: " + SPEED_LABELS[bar.getProgress()]);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seek, int progress, boolean fromUser) {
                caption.setText("Scroll speed: " + SPEED_LABELS[progress]);
                if (fromUser) prefs.edit().putInt("speed", progress).apply();
            }
            public void onStartTrackingTouch(SeekBar seek) {}
            public void onStopTrackingTouch(SeekBar seek) {}
        });
        content.addView(bar);
    }

    private void slider(String title, String key, int min, int max, int initial) {
        TextView caption = label("", 16, true);
        SeekBar bar = new SeekBar(this);
        bar.setMax(max - min);
        bar.setProgress(prefs.getInt(key, initial) - min);
        caption.setText(title + ": " + (bar.getProgress() + min) + (key.equals("opacity") ? "%" : " dp"));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seek, int progress, boolean fromUser) {
                caption.setText(title + ": " + (progress + min) + (key.equals("opacity") ? "%" : " dp"));
                if (fromUser) save(key, progress + min);
            }
            public void onStartTrackingTouch(SeekBar seek) {}
            public void onStopTrackingTouch(SeekBar seek) {}
        });
        content.addView(bar);
    }

    private void save(String key, int value) { prefs.edit().putInt(key, value).apply(); notifyService(); }
    private void save(String key, String value) { prefs.edit().putString(key, value).apply(); notifyService(); }
    private void save(String key, boolean value) { prefs.edit().putBoolean(key, value).apply(); notifyService(); }
    private void notifyService() { sendBroadcast(new Intent(TopService.ACTION_UPDATE).setPackage(getPackageName())); }
    private int dp(int n) { return (int)(n * getResources().getDisplayMetrics().density + .5f); }
}
