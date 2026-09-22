package com.tiptop.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.net.Uri;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.ImageButton;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    static boolean foreground;
    private static final String[] SPEED_LABELS = {"Slowest", "Slow", "Medium", "Fast", "Fastest"};
    private SharedPreferences prefs;
    private TextView status, statusDetail, access;
    private LinearLayout content;
    private LinearLayout barAppearance;
    private ScrollView scroll;
    private BarPreview preview;
    private final List<Runnable> refreshBarSliders = new ArrayList<>();
    private final Runnable connectionChanged = this::updateStatus;
    private TextView masterButton;
    private android.graphics.drawable.Drawable masterIcon;
    private final SharedPreferences.OnSharedPreferenceChangeListener settingChanged = (preferences, key) -> {
        if ("tiptop_enabled".equals(key)) syncMasterButton();
    };
    private boolean dark;
    private int base, card, surface, ink, muted, accent, green;

    @Override public void onCreate(Bundle state) {
        prefs = getSharedPreferences("settings", MODE_PRIVATE);
        dark = ThemeColors.isDark(prefs, getResources().getConfiguration());
        setTheme(dark ? R.style.Theme_TipTop_Dark : R.style.Theme_TipTop_Light);
        super.onCreate(state);
        // Official Catppuccin Macchiato / Latte palette.
        base = dark ? 0xff24273a : 0xffeff1f5;
        card = dark ? 0xff1e2030 : 0xffe6e9ef;
        surface = dark ? 0xff363a4f : 0xffccd0da;
        ink = dark ? 0xffcad3f5 : 0xff4c4f69;
        muted = dark ? 0xffa5adcb : 0xff6c6f85;
        accent = ThemeColors.accent(dark);
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
        content.setPadding(side, dp(24), side, dp(12));
        scroll.addView(content);
        setContentView(scroll);
        buildHeader();
        buildStatus();

        section("SCROLLING");
        LinearLayout behavior = card();
        toggle(behavior, "Haptic feedback", "A gentle click when you tap the area.", "haptics", true);
        divider(behavior);
        slider(behavior, "Scroll speed", "scroll_speed", 0, 4, 4);
        addText(behavior, "Choose how fast to scroll back to the top.", 13, muted, false, 0, 4);

        buildAppFilter();

        section("TAP AREA");
        LinearLayout bar = card();
        addText(bar, "Make it yours", 19, ink, true, 0, 4);
        addText(bar, "Use the dotted outline on your screen to adjust the tap area. It stays active when the bar is hidden.", 13, muted, false, 0, 16);
        preview = new BarPreview();
        bar.addView(preview, new LinearLayout.LayoutParams(-1, dp(132)));
        addText(bar, "Position", 15, ink, true, 18, 10);
        choices(bar, "position", new String[]{"Left", "Center", "Right"}, "Center");
        slider(bar, "Width", "width", 40, 240, 100);
        slider(bar, "Height", "height", 24, 80, 36);
        slider(bar, "Top offset", "offset", 0, 80, 8);
        space(bar, 8);
        TextView resetSliders = resetButton(bar, "all tap area sliders", "defaults", this::resetBarSliders);
        resetSliders.setText("Reset all");
        resetSliders.setBackground(ripple(surface, 14));
        resetSliders.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        divider(bar);
        toggle(bar, "Show tap bar", "Show a visible marker over your tap area.", "enabled", true);
        barAppearance = column();
        bar.addView(barAppearance, new LinearLayout.LayoutParams(-1, -2));
        barAppearance.setVisibility(prefs.getBoolean("enabled", true) ? View.VISIBLE : View.GONE);
        colorPicker(barAppearance);
        slider(barAppearance, "Opacity", "opacity", 15, 100, 70);

        LinearLayout github = row();
        github.setGravity(Gravity.CENTER);
        github.setMinimumHeight(dp(48));
        ImageView githubLogo = new ImageView(this);
        githubLogo.setImageResource(R.drawable.ic_github);
        githubLogo.setImageTintList(ColorStateList.valueOf(accent));
        githubLogo.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(dp(20), dp(20));
        logoParams.setMarginEnd(dp(8));
        github.addView(githubLogo, logoParams);
        github.addView(text("GitHub", 14, accent, true));
        LinearLayout.LayoutParams githubParams = new LinearLayout.LayoutParams(-1, -2);
        githubParams.topMargin = dp(8);
        content.addView(github, githubParams);
        github.setBackground(ripple(base, 14));
        github.setContentDescription("Open TipTop on GitHub");
        github.setAccessibilityDelegate(buttonDelegate());
        github.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/titandrive/tiptop")));
            } catch (ActivityNotFoundException e) {
                Toast.makeText(this, "No app available to open GitHub", Toast.LENGTH_SHORT).show();
            }
        });
        TextView version = addText(content, "Version " + BuildConfig.VERSION_NAME, 12, muted, false, 0, 0);
        version.setGravity(Gravity.CENTER);
        if (state != null) scroll.post(() -> scroll.scrollTo(0, state.getInt("scroll_y")));
    }

    private void buildAppFilter() {
        section("APP FILTERING");
        LinearLayout panel = card();
        LinearLayout selector = column();
        panel.addView(selector, new LinearLayout.LayoutParams(-1, -2));
        TextView description = addText(panel, "", 13, muted, false, 10, 10);
        TextView manage = text("", 14, accent, true);
        manage.setGravity(Gravity.CENTER);
        manage.setMinHeight(dp(48));
        manage.setPadding(dp(12), dp(10), dp(12), dp(10));
        manage.setBackground(ripple(base, 14));
        manage.setAccessibilityDelegate(buttonDelegate());
        panel.addView(manage, new LinearLayout.LayoutParams(-1, -2));
        Runnable refresh = () -> {
            String mode = AppFilter.mode(prefs);
            boolean all = AppFilter.ALL.equals(mode);
            int count = prefs.getStringSet(AppFilter.listKey(mode), java.util.Collections.emptySet()).size();
            description.setText(all ? "TipTop works in all apps."
                    : AppFilter.BLACKLIST.equals(mode) ? "Works in all apps except those you select."
                    : "Works only in the apps you select.");
            manage.setVisibility(all ? View.INVISIBLE : View.VISIBLE);
            String action = AppFilter.WHITELIST.equals(mode) ? "Choose allowed apps" : "Choose blocked apps";
            String selection = count == 0 ? "No apps selected" : count + (count == 1 ? " app selected" : " apps selected");
            android.text.SpannableString label = new android.text.SpannableString(action + "\n" + selection);
            label.setSpan(new android.text.style.RelativeSizeSpan(.85f), action.length() + 1,
                    label.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            label.setSpan(new android.text.style.ForegroundColorSpan(muted), action.length() + 1,
                    label.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            manage.setText(label);
            manage.setContentDescription(action + ". " + selection + ".");
        };
        choices(selector, AppFilter.MODE,
                new String[]{AppFilter.ALL, AppFilter.BLACKLIST, AppFilter.WHITELIST},
                AppFilter.ALL, refresh);
        manage.setOnClickListener(v -> AppPickerDialog.show(this, prefs, AppFilter.mode(prefs),
                card, ink, muted, accent, () -> { refresh.run(); notifyAppFilter(); }));
        refresh.run();
    }

    private void buildHeader() {
        LinearLayout row = row();
        LinearLayout words = column();
        addText(words, "TipTop", 36, ink, true, 0, 2);
        row.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
        ImageButton theme = new ImageButton(this);
        theme.setImageResource(dark ? R.drawable.ic_sun : R.drawable.ic_moon);
        theme.setImageTintList(ColorStateList.valueOf(muted));
        theme.setBackground(ripple(base, 14));
        theme.setPadding(dp(12), dp(12), dp(12), dp(12));
        theme.setContentDescription(dark ? "Switch to light theme" : "Switch to dark theme");
        theme.setTooltipText("Change theme · Hold to follow system");
        theme.setOnClickListener(v -> {
            prefs.edit().putString("theme", dark ? "Light" : "Dark").apply();
            notifyBarAppearance();
            recreate();
        });
        theme.setOnLongClickListener(v -> {
            prefs.edit().putString("theme", "System").apply();
            notifyBarAppearance();
            Toast.makeText(this, "Theme follows system", Toast.LENGTH_SHORT).show();
            recreate();
            return true;
        });
        row.addView(theme, new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView icon = text("ⓘ", 24, muted, false);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(ripple(base, 14));
        icon.setContentDescription("TipTop help and info");
        icon.setAccessibilityDelegate(buttonDelegate());
        icon.setOnClickListener(v -> showShortcutInfo());
        row.addView(icon, new LinearLayout.LayoutParams(dp(48), dp(48)));
        content.addView(row);
        addText(content, "One tap. Back to the top.", 14, muted, false, 0, 0);
        space(content, 24);
    }

    private void showShortcutInfo() {
        LinearLayout body = column();
        body.setPadding(dp(24), dp(4), dp(24), dp(16));
        addText(body, "How to use TipTop", 17, ink, true, 0, 8);
        addText(body, "Enable TipTop and its accessibility service to get started.\n\nTap the tap area to scroll to the top. Touch anywhere on the screen to stop.\n\nAdjust the scroll speed and tap area in the app. The tap area still works when the bar is hidden.\n\nUse the sun/moon button to change themes. Hold it to follow your system theme.", 14, muted, false, 0, 16);
        addText(body, "App filtering", 17, ink, true, 0, 8);
        addText(body, "All: works in every app.\nBlacklist: works everywhere except selected apps.\nWhitelist: works only in selected apps. An empty whitelist disables it everywhere.", 14, muted, false, 0, 12);
        addText(body, "Tap Choose blocked apps or Choose allowed apps to edit. Search by name; selected apps appear at the top.\n\nSelect all selects every app, even those hidden by search. Clear deselects everything. Done saves; Cancel discards changes.", 14, muted, false, 0, 12);
        addText(body, "Each list is saved separately. Excluded apps have no tap area and cannot be scrolled by shortcuts or automation. New apps appear when you return to TipTop; select them to add them to a list.", 14, muted, false, 0, 16);
        addText(body, "Shortcuts", 17, ink, true, 0, 8);
        addText(body, "Long-press TipTop’s app icon to find Toggle TipTop and Scroll to top. You can also select them in apps that support app shortcuts, including launchers and gesture apps.", 14, muted, false, 0, 16);
        addText(body, "Quick Settings", 17, ink, true, 0, 8);
        addText(body, "Edit your Quick Settings panel and add the TipTop tile to toggle TipTop on or off.", 14, muted, false, 0, 16);
        addText(body, "Tasker & MacroDroid", 17, ink, true, 0, 8);
        addText(body, "Choose Send Intent. Set the target to Broadcast Receiver in Tasker, or Broadcast in MacroDroid. Use one action below with the package and class shown. Leave other fields empty.", 14, muted, false, 0, 8);
        addText(body, "Tap a field to copy it.", 13, accent, false, 0, 12);
        copyableInfo(body, "Toggle on/off action", ShortcutActions.ACTION_TOGGLE);
        copyableInfo(body, "Scroll to top action", ShortcutActions.ACTION_SCROLL_TO_TOP);
        copyableInfo(body, "Package", getPackageName());
        copyableInfo(body, "Class", AutomationReceiver.class.getName());
        addText(body, "Scrolling uses your selected speed and requires TipTop and its accessibility service to be enabled. Toggle changes the same master switch as the app and tile.", 13, muted, false, 12, 0);
        ScrollView details = new ScrollView(this);
        details.setBackgroundColor(card);
        if (android.os.Build.VERSION.SDK_INT >= 29) details.setForceDarkAllowed(false);
        details.addView(body);
        TextView title = text("TipTop guide", 20, ink, true);
        title.setPadding(dp(24), dp(24), dp(24), dp(16));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setCustomTitle(title)
                .setView(details)
                .setPositiveButton("Done", null)
                .create();
        dialog.show();
        dialog.getWindow().setBackgroundDrawable(shape(card, 24));
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(accent);
    }

    private void copyableInfo(LinearLayout parent, String label, String value) {
        LinearLayout field = column();
        field.setPadding(dp(12), dp(10), dp(12), dp(10));
        field.setMinimumHeight(dp(48));
        field.setBackground(ripple(base, 12));
        addText(field, label + "  ·  Copy", 13, accent, true, 0, 4);
        TextView detail = addText(field, value, 13, ink, false, 0, 0);
        detail.setTypeface(Typeface.MONOSPACE);
        field.setContentDescription("Copy " + label + ": " + value);
        field.setAccessibilityDelegate(buttonDelegate());
        field.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText(label, value));
            // Android 13+ provides its own clipboard confirmation.
            if (android.os.Build.VERSION.SDK_INT < 33)
                Toast.makeText(this, label + " copied", Toast.LENGTH_SHORT).show();
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(8);
        parent.addView(field, params);
    }

    private void buildStatus() {
        LinearLayout panel = card();
        masterButton = new TextView(this) {
            private final android.graphics.Rect inkBounds = new android.graphics.Rect();

            @Override protected void onDraw(Canvas canvas) {
                String label = getText().toString();
                Paint paint = getPaint();
                paint.setColor(getCurrentTextColor());
                paint.getTextBounds(label, 0, label.length(), inkBounds);
                float centerY = getPaddingTop()
                        + (getHeight() - getPaddingTop() - getPaddingBottom()) / 2f;
                int iconSize = dp(36);
                int gap = dp(12);
                float totalWidth = iconSize + gap + paint.measureText(label);
                float left = (getWidth() - totalWidth) / 2f;
                if (masterIcon != null) {
                    canvas.save();
                    canvas.translate(left, centerY - iconSize / 2f);
                    masterIcon.draw(canvas);
                    canvas.restore();
                }
                canvas.drawText(label, left + iconSize + gap,
                        centerY - inkBounds.exactCenterY() + dp(2), paint);
            }
        };
        masterButton.setTextSize(26);
        masterButton.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
        masterButton.setIncludeFontPadding(false);
        masterButton.setGravity(Gravity.CENTER);
        masterButton.setMinHeight(dp(84));
        masterButton.setPadding(dp(16), dp(16), dp(16), dp(16));
        masterButton.setAccessibilityDelegate(buttonDelegate());
        masterButton.setOnClickListener(v -> {
            if (prefs.getBoolean("haptics", true)) Haptics.click(v);
            prefs.edit().putBoolean("tiptop_enabled",
                    !prefs.getBoolean("tiptop_enabled", true)).apply();
            syncMasterButton();
            notifyService();
        });
        panel.addView(masterButton, new LinearLayout.LayoutParams(-1, -2));
        divider(panel);
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

    @Override protected void onStart() {
        super.onStart();
        TopService.addConnectionListener(connectionChanged);
        prefs.registerOnSharedPreferenceChangeListener(settingChanged);
        syncMasterButton();
    }

    @Override protected void onStop() {
        TopService.removeConnectionListener(connectionChanged);
        prefs.unregisterOnSharedPreferenceChangeListener(settingChanged);
        super.onStop();
    }

    @Override protected void onResume() {
        super.onResume();
        AppPickerDialog.preload(this, prefs);
        foreground = true;
        notifyBarAppearance();
        updateStatus();
    }

    @Override protected void onPause() {
        foreground = false;
        notifyBarAppearance();
        super.onPause();
    }

    private void notifyBarAppearance() {
        sendBroadcast(new Intent(TopService.ACTION_REFRESH_APPEARANCE).setPackage(getPackageName()));
    }

    private void updateStatus() {
        if (status == null) return;
        if (!prefs.getBoolean("tiptop_enabled", true)) {
            status.setText("●  TipTop is off");
            status.setTextColor(muted);
            statusDetail.setText("Turn TipTop on above when you're ready. Your settings are saved.");
            return;
        }
        String services = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        String name = new ComponentName(this, TopService.class).flattenToString();
        boolean enabledInSettings = services != null && services.contains(name);
        status.setText(TopService.connected ? "●  TipTop is active" : enabledInSettings ? "●  Waiting for accessibility" : "●  Let's get you set up");
        status.setTextColor(TopService.connected ? green : accent);
        statusDetail.setText(TopService.connected ? "Tap the tap area to scroll to the top."
                : enabledInSettings ? "Waiting for Android to connect. If this persists, turn TipTop off and on in accessibility settings."
                : "Enable TipTop in accessibility settings to start scrolling.");
    }

    private void syncMasterButton() {
        if (masterButton == null) return;
        boolean enabled = prefs.getBoolean("tiptop_enabled", true);
        String label = enabled ? "Stop TipTop" : "Start TipTop";
        android.graphics.drawable.Drawable icon = getDrawable(
                enabled ? R.drawable.ic_stop : R.drawable.ic_start).mutate();
        icon.setTint(enabled ? base : accent);
        icon.setBounds(0, 0, dp(36), dp(36));
        masterIcon = icon;
        masterButton.setText(label);
        masterButton.invalidate();
        masterButton.setTextColor(enabled ? base : accent);
        masterButton.setBackground(ripple(enabled ? accent : surface, 18));
        masterButton.setContentDescription(label);
        if (android.os.Build.VERSION.SDK_INT >= 30)
            masterButton.setStateDescription(enabled ? "On" : "Off");
        updateStatus();
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
            if (key.equals("haptics") && checked) Haptics.click(button);
            if (key.equals("enabled") && barAppearance != null)
                barAppearance.setVisibility(checked ? View.VISIBLE : View.GONE);
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
        SeekBar seek = speed ? new SeekBar(this) {
            private final Paint tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

            @Override protected synchronized void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                int steps = getMax();
                if (steps == 0) return;
                float trackWidth = getWidth() - getPaddingLeft() - getPaddingRight();
                for (int i = 0; i <= steps; i++) {
                    float fraction = (float) i / steps;
                    if (getLayoutDirection() == View.LAYOUT_DIRECTION_RTL) fraction = 1 - fraction;
                    tickPaint.setColor(i <= getProgress() ? accent : surface);
                    canvas.drawCircle(getPaddingLeft() + trackWidth * fraction,
                            getHeight() / 2f, dp(3), tickPaint);
                }
            }
        } : new SeekBar(this);
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
                    if (speed && prefs.getBoolean("haptics", true)) Haptics.click(control);
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
        if (speed) {
            LinearLayout speedRow = row();
            ImageView turtle = new ImageView(this);
            turtle.setImageResource(R.drawable.ic_turtle);
            turtle.setImageTintList(ColorStateList.valueOf(accent));
            turtle.setContentDescription("Slowest");
            speedRow.addView(turtle, new LinearLayout.LayoutParams(dp(24), dp(24)));
            speedRow.addView(seek, new LinearLayout.LayoutParams(0, dp(48), 1));
            ImageView rabbit = new ImageView(this);
            rabbit.setImageResource(R.drawable.ic_rabbit);
            rabbit.setImageTintList(ColorStateList.valueOf(accent));
            rabbit.setContentDescription("Fastest");
            speedRow.addView(rabbit, new LinearLayout.LayoutParams(dp(24), dp(24)));
            parent.addView(speedRow);
            space(parent, 10);
        } else {
            parent.addView(seek, new LinearLayout.LayoutParams(-1, dp(48)));
        }
    }

    private String format(String key, int value) {
        if (key.equals("scroll_speed")) return SPEED_LABELS[value];
        return value + (key.equals("opacity") ? "%" : " dp");
    }

    private void colorPicker(LinearLayout parent) {
        addText(parent, "Color", 15, ink, true, 18, 8);
        LinearLayout button = row();
        button.setPadding(dp(14), dp(10), dp(14), dp(10));
        button.setMinimumHeight(dp(52));
        button.setBackground(ripple(base, 14));
        button.setAccessibilityDelegate(buttonDelegate());
        View swatch = new View(this);
        button.addView(swatch, new LinearLayout.LayoutParams(dp(28), dp(28)));
        TextView value = text("", 14, ink, true);
        value.setPadding(dp(12), 0, dp(12), 0);
        button.addView(value, new LinearLayout.LayoutParams(0, -2, 1));
        button.addView(text("Edit", 13, accent, true));
        Runnable refresh = () -> {
            int color = ThemeColors.bar(prefs, dark);
            swatch.setBackground(shape(color, 14));
            String label = prefs.contains("bar_color") ? hex(color) : "Theme highlight";
            value.setText(label);
            button.setContentDescription("Tap bar color: " + label + ". Choose color.");
            if (preview != null) preview.invalidate();
        };
        refresh.run();
        button.setOnClickListener(v -> showColorPicker(refresh));
        parent.addView(button, new LinearLayout.LayoutParams(-1, -2));
    }

    private String hex(int color) {
        return String.format(java.util.Locale.ROOT, "#%06X", color & 0xffffff);
    }

    private void showColorPicker(Runnable refresh) {
        // Android-style swatch palette: selecting a color applies it directly.
        int[] colors = {
                0xfff44336, 0xffe91e63, 0xff9c27b0, ThemeColors.accent(dark),
                0xff673ab7, 0xff3f51b5, 0xff2196f3, 0xff03a9f4,
                0xff00bcd4, 0xff009688, 0xff4caf50, 0xff8bc34a,
                0xffcddc39, 0xffffeb3b, 0xffffc107, 0xffff9800,
                0xffff5722, 0xff795548, 0xff607d8b, 0xff9e9e9e,
                0xffeeeeee, 0xffbdbdbd, 0xff424242, 0xff000000
        };
        String[] names = {
                "Red", "Pink", "Purple", "Theme highlight",
                "Deep purple", "Indigo", "Blue", "Light blue",
                "Cyan", "Teal", "Green", "Light green",
                "Lime", "Yellow", "Amber", "Orange",
                "Deep orange", "Brown", "Blue grey", "Grey",
                "White", "Light grey", "Dark grey", "Black"
        };
        LinearLayout palette = column();
        palette.setPadding(dp(16), dp(8), dp(16), dp(8));
        ScrollView paletteScroll = new ScrollView(this);
        paletteScroll.addView(palette);
        paletteScroll.setBackgroundColor(card);
        TextView pickerTitle = text("Tap bar color", 20, ink, true);
        pickerTitle.setPadding(dp(24), dp(24), dp(24), dp(12));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setCustomTitle(pickerTitle)
                .setView(paletteScroll)
                .setNeutralButton("Default", (d, which) -> {
                    prefs.edit().remove("bar_color").apply();
                    refresh.run();
                    notifyBarAppearance();
                }).create();
        int selected = ThemeColors.bar(prefs, dark);
        for (int rowIndex = 0; rowIndex < colors.length / 4; rowIndex++) {
            LinearLayout row = row();
            for (int column = 0; column < 4; column++) {
                int index = rowIndex * 4 + column;
                int color = colors[index];
                FrameLayout cell = new FrameLayout(this);
                TextView swatch = text(selected == color ? "✓" : "", 24,
                        Color.red(color) * .299 + Color.green(color) * .587
                                + Color.blue(color) * .114 > 160 ? 0xff24273a : 0xffffffff, true);
                swatch.setGravity(Gravity.CENTER);
                swatch.setBackground(ripple(color, 24));
                swatch.setContentDescription(names[index] + ", " + hex(color));
                swatch.setSelected(selected == color);
                swatch.setAccessibilityDelegate(buttonDelegate());
                swatch.setOnClickListener(v -> {
                    prefs.edit().putInt("bar_color", color).apply();
                    refresh.run();
                    notifyBarAppearance();
                    dialog.dismiss();
                });
                cell.addView(swatch, new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.CENTER));
                row.addView(cell, new LinearLayout.LayoutParams(0, dp(60), 1));
            }
            palette.addView(row, new LinearLayout.LayoutParams(-1, -2));
        }
        dialog.show();
        dialog.getWindow().setBackgroundDrawable(shape(card, 24));
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setTextColor(accent);
        if (android.os.Build.VERSION.SDK_INT >= 29)
            dialog.getWindow().getDecorView().setForceDarkAllowed(false);
    }

    private LinearLayout choices(LinearLayout parent, String key, String[] options, String initial) {
        return choices(parent, key, options, initial, null);
    }

    private LinearLayout choices(LinearLayout parent, String key, String[] options, String initial,
            Runnable onChanged) {
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
                if (prefs.getBoolean("haptics", true)) Haptics.click(v);
                prefs.edit().putString(key, option).apply();
                if (key.equals("theme")) recreate();
                else {
                    updateChoices(group, option);
                    if (AppFilter.MODE.equals(key)) notifyAppFilter();
                    else notifyService();
                    if (preview != null) preview.invalidate();
                }
                if (onChanged != null) onChanged.run();
            });
        }
        updateChoices(group, AppFilter.MODE.equals(key) ? AppFilter.mode(prefs) : prefs.getString(key, initial));
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
    private void notifyAppFilter() { sendBroadcast(new Intent(TopService.ACTION_UPDATE_APP_FILTER).setPackage(getPackageName())); }
    private void notifyService() { sendBroadcast(new Intent(TopService.ACTION_UPDATE).setPackage(getPackageName())); }
    private int dp(float n) { return (int)(n * getResources().getDisplayMetrics().density + .5f); }

    private final class BarPreview extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        BarPreview() {
            super(MainActivity.this);
            setContentDescription("Solid tap bar color example. Size and position are illustrative.");
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
            // Keep this example stable; use the actual overlay to adjust geometry.
            float width = Math.min(dp(100), getWidth());
            float height = dp(28);
            float x = (getWidth() - width) / 2;
            float y = dp(12);
            paint.setColor(ThemeColors.bar(prefs, dark));
            canvas.drawRoundRect(x, y, x + width, y + height, height / 2, height / 2, paint);
        }
    }
}
