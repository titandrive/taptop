package com.tiptop.app;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private static final String[] SPEED_LABELS = {"Slowest", "Slow", "Medium", "Fast", "Maximum"};
    private SharedPreferences prefs;
    private TextView status, statusDetail, access;
    private LinearLayout content;
    private LinearLayout barControls;
    private ScrollView scroll;
    private BarPreview preview;
    private final List<Runnable> refreshBarSliders = new ArrayList<>();
    private boolean dark;
    private int base, card, surface, ink, muted, accent, green;

    @Override public void onCreate(Bundle state) {
        prefs = getSharedPreferences("settings", MODE_PRIVATE);
        String theme = prefs.getString("theme", "System");
        dark = theme.equals("Dark") || (theme.equals("System")
                && (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES);
        setTheme(dark ? R.style.Theme_TipTop_Dark : R.style.Theme_TipTop_Light);
        super.onCreate(state);
        // Official Catppuccin Macchiato / Latte palette.
        base = dark ? 0xff24273a : 0xffeff1f5;
        card = dark ? 0xff1e2030 : 0xffe6e9ef;
        surface = dark ? 0xff363a4f : 0xffccd0da;
        ink = dark ? 0xffcad3f5 : 0xff4c4f69;
        muted = dark ? 0xffa5adcb : 0xff6c6f85;
        accent = dark ? 0xffc6a0f6 : 0xff8839ef;
        green = dark ? 0xffa6da95 : 0xff40a02b;
        getWindow().setStatusBarColor(base);
        getWindow().setNavigationBarColor(base);
        int systemBars = dark ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if (!dark && android.os.Build.VERSION.SDK_INT >= 27)
            systemBars |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        getWindow().getDecorView().setSystemUiVisibility(systemBars);
        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(base);
        if (android.os.Build.VERSION.SDK_INT >= 29) scroll.setForceDarkAllowed(false);
        scroll.setClipToPadding(false);
        content = column();
        int side = Math.max(dp(20), (getResources().getDisplayMetrics().widthPixels - dp(600)) / 2);
        content.setPadding(side, dp(24), side, dp(32));
        scroll.addView(content);
        setContentView(scroll);
        buildHeader();
        buildStatus();

        section("SCROLLING");
        LinearLayout behavior = card();
        toggle(behavior, "Haptic feedback", "A gentle click when you tap the bar.", "haptics", true);
        divider(behavior);
        slider(behavior, "Scroll speed", "scroll_speed", 0, 4, 4);
        addText(behavior, "Touch the screen to stop scrolling. Speed changes apply on your next tap.", 13, muted, false, 0, 4);

        section("APPEARANCE");
        LinearLayout appearance = card();
        addText(appearance, "Color theme", 17, ink, true, 0, 4);
        addText(appearance, "Latte by day. Macchiato by night.", 13, muted, false, 0, 14);
        choices(appearance, "theme", new String[]{"System", "Light", "Dark"}, "System");

        section("TAP BAR");
        LinearLayout barCard = card();
        toggle(barCard, "Show tap bar", "One tap to head back to the top.", "enabled", true);
        LinearLayout bar = column();
        barControls = bar;
        barCard.addView(bar, new LinearLayout.LayoutParams(-1, -2));
        bar.setVisibility(prefs.getBoolean("enabled", true) ? View.VISIBLE : View.GONE);
        divider(bar);
        addText(bar, "Make it yours", 19, ink, true, 0, 4);
        addText(bar, "Adjust the size and placement to suit your thumb.", 13, muted, false, 0, 16);
        preview = new BarPreview();
        bar.addView(preview, new LinearLayout.LayoutParams(-1, dp(132)));
        addText(bar, "Position", 15, ink, true, 18, 10);
        choices(bar, "position", new String[]{"Left", "Center", "Right"}, "Center");
        slider(bar, "Width", "width", 40, 240, 100);
        slider(bar, "Height", "height", 24, 80, 36);
        slider(bar, "Top offset", "offset", 0, 80, 8);
        slider(bar, "Opacity", "opacity", 15, 100, 70);
        space(bar, 8);
        TextView resetSliders = resetButton(bar, "all tap bar sliders", "defaults", this::resetBarSliders);
        resetSliders.setText("Reset all");
        resetSliders.setBackground(ripple(surface, 14));
        resetSliders.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));

        section("COMPATIBILITY");
        LinearLayout compatibility = card();
        toggle(compatibility, "Fallback fling", "Try a single swipe if an app won't scroll. It may stop before the top.", "legacy_swipes", true);
        TextView footer = addText(content, "Made for a little less scrolling.\nNo internet access. No saved screen content.", 12, muted, false, 24, 0);
        footer.setGravity(Gravity.CENTER);
        if (state != null) scroll.post(() -> scroll.scrollTo(0, state.getInt("scroll_y")));
    }

    private void buildHeader() {
        LinearLayout row = row();
        LinearLayout words = column();
        addText(words, "TipTop", 36, ink, true, 0, 2);
        addText(words, "Back to the top. Just like that.", 14, muted, false, 0, 0);
        row.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
        TextView icon = text("↑", 32, accent, true);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(shape(surface, 20));
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.addView(icon, new LinearLayout.LayoutParams(dp(60), dp(60)));
        content.addView(row);
        space(content, 24);
    }

    private void buildStatus() {
        LinearLayout panel = card();
        status = addText(panel, "", 17, ink, true, 0, 4);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        statusDetail = addText(panel, "", 13, muted, false, 0, 14);
        access = text("Accessibility settings  ↗", 14, accent, true);
        access.setGravity(Gravity.CENTER);
        access.setMinHeight(dp(48));
        access.setBackground(ripple(surface, 14));
        access.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        access.setAccessibilityDelegate(buttonDelegate());
        panel.addView(access, new LinearLayout.LayoutParams(-1, -2));
    }

    @Override protected void onResume() {
        super.onResume();
        String services = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        String name = new ComponentName(this, TopService.class).flattenToString();
        boolean enabledInSettings = services != null && services.contains(name);
        status.setText(TopService.connected ? "●  Ready when you are" : enabledInSettings ? "●  Needs a restart" : "●  Let's get you set up");
        status.setTextColor(TopService.connected ? green : accent);
        statusDetail.setText(TopService.connected ? "Open an app, find your list, and tap the bar."
                : enabledInSettings ? "Turn TipTop off and on in accessibility settings."
                : "Enable TipTop in accessibility settings to start scrolling.");
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putInt("scroll_y", scroll.getScrollY());
        super.onSaveInstanceState(state);
    }

    private void toggle(LinearLayout parent, String title, String description, String key, boolean initial) {
        LinearLayout row = row();
        row.setMinimumHeight(dp(68));
        LinearLayout words = column();
        addText(words, title, 16, ink, true, 4, 3);
        addText(words, description, 13, muted, false, 0, 4);
        words.setPadding(0, 0, dp(16), 0);
        row.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
        Switch control = new Switch(this);
        control.setMinHeight(dp(48));
        control.setMinWidth(dp(48));
        control.setContentDescription(title + ". " + description);
        control.setThumbTintList(new ColorStateList(new int[][]{{android.R.attr.state_checked}, {}}, new int[]{accent, muted}));
        control.setTrackTintList(new ColorStateList(new int[][]{{android.R.attr.state_checked}, {}}, new int[]{alpha(accent, 90), surface}));
        control.setChecked(prefs.getBoolean(key, initial));
        control.setOnCheckedChangeListener((button, checked) -> {
            prefs.edit().putBoolean(key, checked).apply();
            if (key.equals("enabled") && barControls != null)
                barControls.setVisibility(checked ? View.VISIBLE : View.GONE);
            if (!key.equals("haptics")) notifyService();
            if (preview != null) preview.invalidate();
        });
        row.addView(control);
        row.setOnClickListener(v -> control.setChecked(!control.isChecked()));
        parent.addView(row);
    }

    private void slider(LinearLayout parent, String title, String key, int min, int max, int initial) {
        boolean speed = key.equals("scroll_speed");
        int value = Math.max(min, Math.min(max, prefs.getInt(key, initial)));
        LinearLayout heading = row();
        heading.setPadding(0, dp(18), 0, dp(2));
        heading.addView(text(title, 15, ink, true), new LinearLayout.LayoutParams(0, -2, 1));
        TextView caption = text(format(key, value), 13, accent, true);
        caption.setPadding(dp(10), dp(5), dp(10), dp(5));
        caption.setBackground(shape(alpha(accent, dark ? 25 : 18), 8));
        heading.addView(caption);
        parent.addView(heading);
        SeekBar seek = new SeekBar(this);
        seek.setMax(max - min);
        seek.setProgress(value - min);
        seek.setContentDescription(title + ": " + format(key, value));
        seek.setProgressTintList(ColorStateList.valueOf(accent));
        seek.setProgressBackgroundTintList(ColorStateList.valueOf(surface));
        seek.setThumbTintList(ColorStateList.valueOf(accent));
        seek.setSplitTrack(false);
        seek.setPadding(dp(10), 0, dp(10), 0);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar control, int progress, boolean fromUser) {
                caption.setText(format(key, progress + min));
                control.setContentDescription(title + ": " + format(key, progress + min));
                if (fromUser) {
                    prefs.edit().putInt(key, progress + min).apply();
                    if (!speed) notifyService();
                    if (preview != null) preview.invalidate();
                }
            }
            public void onStartTrackingTouch(SeekBar control) {}
            public void onStopTrackingTouch(SeekBar control) {}
        });
        if (!speed) {
            refreshBarSliders.add(() -> seek.setProgress(prefs.getInt(key, initial) - min));
            resetButton(heading, title, format(key, initial), () -> {
                seek.setProgress(initial - min);
                prefs.edit().putInt(key, initial).apply();
                notifyService();
                if (preview != null) preview.invalidate();
            });
        }
        parent.addView(seek, new LinearLayout.LayoutParams(-1, dp(48)));
        if (speed) {
            LinearLayout limits = row();
            limits.addView(text("Slowest", 11, muted, false), new LinearLayout.LayoutParams(0, -2, 1));
            limits.addView(text("Maximum", 11, muted, false));
            parent.addView(limits);
            space(parent, 10);
        }
    }

    private String format(String key, int value) {
        if (key.equals("scroll_speed")) return SPEED_LABELS[value];
        return value + (key.equals("opacity") ? "%" : " dp");
    }

    private LinearLayout choices(LinearLayout parent, String key, String[] options, String initial) {
        LinearLayout group = row();
        group.setPadding(dp(4), dp(4), dp(4), dp(4));
        group.setBackground(shape(base, 16));
        for (String option : options) {
            TextView choice = text(option, 14, muted, true);
            choice.setGravity(Gravity.CENTER);
            choice.setMinHeight(dp(48));
            choice.setAccessibilityDelegate(buttonDelegate());
            choice.setTag(option);
            group.addView(choice, new LinearLayout.LayoutParams(0, -2, 1));
            choice.setOnClickListener(v -> {
                if (option.equals(prefs.getString(key, initial))) return;
                prefs.edit().putString(key, option).apply();
                if (key.equals("theme")) recreate();
                else {
                    updateChoices(group, option);
                    notifyService();
                    if (preview != null) preview.invalidate();
                }
            });
        }
        updateChoices(group, prefs.getString(key, initial));
        parent.addView(group, new LinearLayout.LayoutParams(-1, -2));
        return group;
    }

    private void resetBarSliders() {
        prefs.edit()
                .putInt("width", 100)
                .putInt("height", 36)
                .putInt("offset", 8)
                .putInt("opacity", 70)
                .apply();
        // Existing listeners update each value label without saving again.
        for (Runnable refresh : refreshBarSliders) refresh.run();
        if (preview != null) preview.invalidate();
        notifyService();
    }

    private TextView resetButton(LinearLayout parent, String title, String defaultValue, Runnable reset) {
        TextView button = text("Reset", 13, accent, true);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(dp(48));
        button.setMinWidth(dp(56));
        button.setPadding(dp(8), 0, dp(8), 0);
        button.setBackground(ripple(card, 12));
        button.setContentDescription("Reset " + title + " to " + defaultValue);
        button.setAccessibilityDelegate(buttonDelegate());
        button.setOnClickListener(v -> reset.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
        params.leftMargin = dp(6);
        parent.addView(button, params);
        return button;
    }

    private void updateChoices(LinearLayout group, String selected) {
        for (int i = 0; i < group.getChildCount(); i++) {
            TextView choice = (TextView) group.getChildAt(i);
            boolean active = selected.equals(choice.getTag());
            choice.setSelected(active);
            choice.setTextColor(active ? (dark ? base : 0xffeff1f5) : muted);
            choice.setBackground(ripple(active ? accent : base, 12));
        }
    }

    private View.AccessibilityDelegate buttonDelegate() {
        return new View.AccessibilityDelegate() {
            @Override public void onInitializeAccessibilityNodeInfo(View host,
                    android.view.accessibility.AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setClassName("android.widget.Button");
            }
        };
    }

    private LinearLayout card() {
        LinearLayout panel = column();
        panel.setPadding(dp(18), dp(14), dp(18), dp(18));
        panel.setBackground(shape(card, 24));
        content.addView(panel, new LinearLayout.LayoutParams(-1, -2));
        return panel;
    }

    private void section(String title) {
        TextView label = addText(content, title, 11, muted, true, 26, 10);
        label.setLetterSpacing(.14f);
    }

    private void divider(LinearLayout parent) {
        View line = new View(this);
        line.setBackgroundColor(surface);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(1));
        params.topMargin = params.bottomMargin = dp(8);
        parent.addView(line, params);
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.create(bold ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        view.setIncludeFontPadding(false);
        view.setLineSpacing(dp(3), 1);
        return view;
    }

    private TextView addText(LinearLayout parent, String value, int size, int color, boolean bold, int top, int bottom) {
        TextView view = text(value, size, color, bold);
        view.setPadding(0, dp(top), 0, dp(bottom));
        parent.addView(view);
        return view;
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private LinearLayout row() {
        LinearLayout layout = new LinearLayout(this);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }

    private GradientDrawable shape(int color, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(radius));
        return shape;
    }

    private RippleDrawable ripple(int color, int radius) {
        return new RippleDrawable(ColorStateList.valueOf(alpha(ink, 30)), shape(color, radius), shape(0xffffffff, radius));
    }

    private void space(LinearLayout parent, int height) { parent.addView(new View(this), new LinearLayout.LayoutParams(1, dp(height))); }
    private int alpha(int color, int opacity) { return (color & 0x00ffffff) | (opacity << 24); }
    private void notifyService() { sendBroadcast(new Intent(TopService.ACTION_UPDATE).setPackage(getPackageName())); }
    private int dp(float n) { return (int)(n * getResources().getDisplayMetrics().density + .5f); }

    private final class BarPreview extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        BarPreview() {
            super(MainActivity.this);
            setContentDescription("Tap bar preview. Size, position, offset and opacity update as you adjust the controls.");
        }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            paint.setColor(base);
            canvas.drawRoundRect(0, 0, getWidth(), getHeight(), dp(16), dp(16), paint);
            paint.setColor(surface);
            for (int i = 0; i < 3; i++) {
                float y = dp(57 + i * 24);
                canvas.drawRoundRect(dp(18), y, dp(34), y + dp(14), dp(4), dp(4), paint);
                canvas.drawRoundRect(dp(44), y + dp(2), getWidth() * (i == 1 ? .65f : .83f), y + dp(6), dp(2), dp(2), paint);
                canvas.drawRoundRect(dp(44), y + dp(10), getWidth() * .5f, y + dp(13), dp(2), dp(2), paint);
            }
            if (!prefs.getBoolean("enabled", true)) return;
            float scale = getWidth() / (float) getResources().getDisplayMetrics().widthPixels;
            float width = dp(prefs.getInt("width", 100)) * scale;
            float height = dp(prefs.getInt("height", 36)) * scale;
            float offset = dp(prefs.getInt("offset", 8)) * scale;
            String position = prefs.getString("position", "Center");
            float x = position.equals("Left") ? 0 : position.equals("Right") ? getWidth() - width : (getWidth() - width) / 2;
            paint.setColor(alpha(0xff2768f4, Math.round(255 * prefs.getInt("opacity", 70) / 100f)));
            canvas.drawRoundRect(x, offset, x + width, offset + height, height / 2, height / 2, paint);
        }
    }
}
