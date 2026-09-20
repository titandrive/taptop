package com.tiptop.app;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import java.util.ArrayDeque;
import java.util.List;

public class TopService extends AccessibilityService {
    public static final String ACTION_UPDATE = "com.tiptop.app.UPDATE";
    public static volatile boolean connected = false;
    private WindowManager windows;
    private View bar;
    private SharedPreferences prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean scrolling;
    private int steps;
    private final BroadcastReceiver update = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { showBar(); }
    };

    @Override protected void onServiceConnected() {
        connected = true;
        prefs = getSharedPreferences("settings", MODE_PRIVATE);
        windows = (WindowManager) getSystemService(WINDOW_SERVICE);
        registerReceiver(update, new IntentFilter(ACTION_UPDATE), Context.RECEIVER_NOT_EXPORTED);
        showBar();
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {}
    @Override public void onInterrupt() { scrolling = false; handler.removeCallbacksAndMessages(null); }
    @Override public void onDestroy() {
        connected = false;
        handler.removeCallbacksAndMessages(null);
        try { unregisterReceiver(update); } catch (IllegalArgumentException ignored) {}
        if (bar != null) windows.removeView(bar);
        bar = null;
        super.onDestroy();
    }

    private void showBar() {
        if (windows == null) return;
        if (bar != null) { windows.removeView(bar); bar = null; }
        if (!prefs.getBoolean("enabled", true)) return;
        View view = new View(this);
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.rgb(39, 104, 244));
        background.setCornerRadius(dp(50));
        view.setBackground(background);
        view.setAlpha(prefs.getInt("opacity", 70) / 100f);
        view.setContentDescription("Scroll to top");
        view.setOnClickListener(v -> { v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); scrollToTop(); });
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
            dp(prefs.getInt("width", 100)), dp(prefs.getInt("height", 36)),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            android.graphics.PixelFormat.TRANSLUCENT);
        String position = prefs.getString("position", "Center");
        params.gravity = Gravity.TOP | (position.equals("Left") ? Gravity.LEFT : position.equals("Right") ? Gravity.RIGHT : Gravity.CENTER_HORIZONTAL);
        // WindowManager starts this window below the status bar on some devices.
        // Subtract that inset so zero means the physical top for both drawing and touch.
        if (android.os.Build.VERSION.SDK_INT >= 28)
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        params.y = dp(prefs.getInt("offset", 8)) - statusBarHeight();
        try { windows.addView(view, params); bar = view; } catch (WindowManager.BadTokenException ignored) {}
    }

    private void scrollToTop() {
        if (scrolling) return;
        scrolling = true;
        steps = 0;
        scrollStep();
    }

    private void scrollStep() {
        if (!scrolling) return;
        AccessibilityNodeInfo target = findScrollable();
        if (target == null) { scrolling = false; return; }
        if (steps == 0 && supports(target, AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_TO_POSITION.getId())) {
            Bundle args = new Bundle();
            args.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_ROW_INT, 0);
            args.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_COLUMN_INT, 0);
            if (target.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_TO_POSITION.getId(), args)) {
                scrolling = false;
                return;
            }
        }
        if (steps++ >= 40 || !target.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)) {
            scrolling = false;
            return;
        }
        handler.postDelayed(this::scrollStep, 75);
    }

    private AccessibilityNodeInfo findScrollable() {
        List<AccessibilityWindowInfo> all = getWindows();
        if (all == null) return null;
        for (AccessibilityWindowInfo window : all) {
            if (window.getType() != AccessibilityWindowInfo.TYPE_APPLICATION) continue;
            AccessibilityNodeInfo root = window.getRoot();
            if (root == null || getPackageName().contentEquals(root.getPackageName())) continue;
            ArrayDeque<AccessibilityNodeInfo> queue = new ArrayDeque<>();
            queue.add(root);
            AccessibilityNodeInfo best = null;
            while (!queue.isEmpty()) {
                AccessibilityNodeInfo node = queue.removeFirst();
                if (node.isVisibleToUser() && (node.isScrollable() || supports(node, AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD))) best = node;
                for (int i = 0; i < node.getChildCount(); i++) {
                    AccessibilityNodeInfo child = node.getChild(i);
                    if (child != null) queue.addLast(child);
                }
            }
            if (best != null) return best;
        }
        return null;
    }

    private boolean supports(AccessibilityNodeInfo node, int id) {
        for (AccessibilityNodeInfo.AccessibilityAction action : node.getActionList()) if (action.getId() == id) return true;
        return false;
    }
    private int statusBarHeight() {
        int id = getResources().getIdentifier("status_bar_height", "dimen", "android");
        return id > 0 ? getResources().getDimensionPixelSize(id) : dp(24);
    }
    private int dp(int n) { return (int)(n * getResources().getDisplayMetrics().density + .5f); }
}
