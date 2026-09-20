package com.tiptop.app;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
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
    private View leftStopRegion;
    private View rightStopRegion;
    private SharedPreferences prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean scrolling;
    private int steps;
    private int runId;
    private boolean consumingStopTouch;
    private String scrollPackage;
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
    @Override public void onInterrupt() { stopScroll(); }
    @Override public void onDestroy() {
        connected = false;
        stopScroll();
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
        final boolean[] stoppedOnDown = {false};
        view.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                stoppedOnDown[0] = scrolling;
                if (stoppedOnDown[0]) {
                    v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    stopScroll();
                }
                return true;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                if (!stoppedOnDown[0]) v.performClick();
                stoppedOnDown[0] = false;
                return true;
            }
            return true;
        });
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
        if (scrolling) { stopScroll(); return; }
        stopScroll();
        scrolling = true;
        if (bar != null) bar.getBackground().setTint(Color.rgb(220, 64, 64));
        steps = 0;
        scrollPackage = null;
        swipeStep(runId);
    }

    private void stopScroll() {
        scrolling = false;
        runId++;
        handler.removeCallbacksAndMessages(null);
        if (!consumingStopTouch) removeStopRegions();
        if (bar != null) bar.getBackground().setTint(Color.rgb(39, 104, 244));
    }

    private void swipeStep(int id) {
        if (!scrolling || id != runId) return;
        AccessibilityNodeInfo target = findScrollable();
        if (target == null || steps >= 60) { stopScroll(); return; }
        String targetPackage = String.valueOf(target.getPackageName());
        if (scrollPackage != null && !scrollPackage.equals(targetPackage)) { stopScroll(); return; }
        scrollPackage = targetPackage;
        if (!supports(target, AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)) { stopScroll(); return; }
        Rect bounds = new Rect();
        target.getBoundsInScreen(bounds);
        if (bounds.height() < dp(180)) { stopScroll(); return; }
        float x = bounds.centerX();
        float startY = bounds.top + bounds.height() * .22f;
        float endY = bounds.bottom - bounds.height() * .12f;
        if (leftStopRegion == null) showStopRegions(x);
        Path path = new Path();
        path.moveTo(x, startY);
        path.lineTo(x, endY);
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, 520))
                .build();
        steps++;
        if (!dispatchGesture(gesture, new GestureResultCallback() {
            @Override public void onCompleted(GestureDescription description) {
                if (id != runId) return;
                        handler.postDelayed(() -> swipeStep(id), 60);
            }
            @Override public void onCancelled(GestureDescription description) {
                if (id == runId) { stopScroll(); }
            }
        }, handler)) { stopScroll(); }
    }

    private void showStopRegions(float swipeX) {
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int gap = Math.max(2, dp(2));
        int leftWidth = Math.max(0, Math.round(swipeX) - gap / 2);
        int rightWidth = Math.max(0, screenWidth - Math.round(swipeX) - gap / 2);
        leftStopRegion = addStopRegion(leftWidth, Gravity.LEFT | Gravity.TOP);
        rightStopRegion = addStopRegion(rightWidth, Gravity.RIGHT | Gravity.TOP);
    }

    private View addStopRegion(int width, int gravity) {
        if (width == 0) return null;
        View region = new View(this);
        region.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                consumingStopTouch = true;
                stopScroll();
            } else if (event.getActionMasked() == MotionEvent.ACTION_UP
                    || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                consumingStopTouch = false;
                handler.postDelayed(this::removeStopRegions, 80);
            }
            return true;
        });
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                width, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                android.graphics.PixelFormat.TRANSLUCENT);
        params.gravity = gravity;
        try { windows.addView(region, params); return region; }
        catch (WindowManager.BadTokenException ignored) { return null; }
    }

    private void removeStopRegions() {
        if (windows == null) return;
        if (leftStopRegion != null) { windows.removeView(leftStopRegion); leftStopRegion = null; }
        if (rightStopRegion != null) { windows.removeView(rightStopRegion); rightStopRegion = null; }
    }

    private AccessibilityNodeInfo findScrollable() {
        List<AccessibilityWindowInfo> all = getWindows();
        if (all == null) return null;
        AccessibilityWindowInfo chosen = null;
        for (AccessibilityWindowInfo window : all) {
            if (window.getType() != AccessibilityWindowInfo.TYPE_APPLICATION) continue;
            AccessibilityNodeInfo root = window.getRoot();
            if (root == null || getPackageName().contentEquals(root.getPackageName())) continue;
            if (window.isActive()) { chosen = window; break; }
            if (chosen == null || window.isFocused()) chosen = window;
        }
        if (chosen == null) return null;
        AccessibilityNodeInfo root = chosen.getRoot();
        if (root == null) return null;
        ArrayDeque<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        ArrayDeque<Integer> depths = new ArrayDeque<>();
        queue.add(root);
        depths.add(0);
        AccessibilityNodeInfo best = null;
        int bestDepth = -1;
        long bestArea = -1;
        boolean bestHasBackwardAction = false;
        Rect bounds = new Rect();
        long screenArea = (long) getResources().getDisplayMetrics().widthPixels
                * getResources().getDisplayMetrics().heightPixels;
        while (!queue.isEmpty()) {
            AccessibilityNodeInfo node = queue.removeFirst();
            int depth = depths.removeFirst();
            if (!node.isVisibleToUser()) continue;
            node.getBoundsInScreen(bounds);
            long area = (long) bounds.width() * bounds.height();
            // Prefer the substantial inner list over a parent that only moves
            // a toolbar or header. Ignore small controls such as spinners.
            boolean hasBackwardAction = supports(node, AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);
            if ((node.isScrollable() || hasBackwardAction)
                    && bounds.height() > bounds.width() / 2 && area >= screenArea / 6
                    && ((!bestHasBackwardAction && hasBackwardAction)
                        || (bestHasBackwardAction == hasBackwardAction
                            && (depth > bestDepth || (depth == bestDepth && area > bestArea))))) {
                best = node;
                bestDepth = depth;
                bestArea = area;
                bestHasBackwardAction = hasBackwardAction;
            }
            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) {
                    queue.addLast(child);
                    depths.addLast(depth + 1);
                }
            }
        }
        return best;
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
