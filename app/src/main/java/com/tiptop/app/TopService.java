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
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
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
    private static final int[] SWIPE_DURATION_MS = {700, 520, 380, 240, 170};
    private static final int[] PAUSE_MS = {70, 60, 40, 20, 10};
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean scrolling;
    private int steps;
    private int missingActionChecks;
    private int runId;
    private boolean consumingStopTouch;
    private String scrollPackage;
    private String scrollClass;
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
        view.setOnClickListener(v -> scrollToTop());
        final boolean[] stoppedOnDown = {false};
        view.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                playTapFeedback(v);
                stoppedOnDown[0] = scrolling;
                if (stoppedOnDown[0]) stopScroll();
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
        AccessibilityNodeInfo target = findScrollable();
        if (target != null && supports(target, AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
                && supportsGranularScroll(target)) {
            Bundle args = new Bundle();
            args.putFloat("android.view.accessibility.action.ARGUMENT_SCROLL_AMOUNT_FLOAT",
                    Float.POSITIVE_INFINITY);
            // RecyclerView handles this as one native smoothScrollToPosition(0).
            // Leave the screen touchable so a normal touch can cancel its animation.
            if (target.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD, args)) return;
        }
        if (target != null && supports(target,
                AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_TO_POSITION.getId())) {
            Bundle args = new Bundle();
            args.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_ROW_INT, 0);
            args.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_COLUMN_INT, 0);
            if (target.performAction(
                    AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_TO_POSITION.getId(), args)) return;
        }
        if (!prefs.getBoolean("legacy_swipes", true)) return;
        scrolling = true;
        if (bar != null) bar.getBackground().setTint(Color.rgb(220, 64, 64));
        steps = 0;
        missingActionChecks = 0;
        scrollPackage = null;
        scrollClass = null;
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
        if (steps >= 200) { stopScroll(); return; }
        AccessibilityNodeInfo target = findScrollable();
        if (target == null) {
            if (scrollClass == null) stopScroll(); else retryMissingAction(id);
            return;
        }
        String targetPackage = String.valueOf(target.getPackageName());
        if (scrollPackage != null && !scrollPackage.equals(targetPackage)) { stopScroll(); return; }
        scrollPackage = targetPackage;
        if (!supports(target, AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)) {
            retryMissingAction(id);
            return;
        }
        missingActionChecks = 0;
        scrollClass = String.valueOf(target.getClassName());
        Rect bounds = new Rect();
        target.getBoundsInScreen(bounds);
        if (bounds.height() < dp(180)) { stopScroll(); return; }
        float x = bounds.centerX();
        float startY = bounds.top + bounds.height() * .12f;
        float endY = bounds.bottom - bounds.height() * .08f;
        if (leftStopRegion == null) showStopRegions(x);
        Path path = new Path();
        path.moveTo(x, startY);
        path.lineTo(x, endY);
        int speed = Math.max(0, Math.min(SWIPE_DURATION_MS.length - 1, prefs.getInt("speed", 3)));
        int pause = PAUSE_MS[speed];
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, SWIPE_DURATION_MS[speed]))
                .build();
        steps++;
        if (!dispatchGesture(gesture, new GestureResultCallback() {
            @Override public void onCompleted(GestureDescription description) {
                if (id != runId) return;
                handler.postDelayed(() -> swipeStep(id), pause);
            }
            @Override public void onCancelled(GestureDescription description) {
                if (id == runId) { stopScroll(); }
            }
        }, handler)) { stopScroll(); }
    }

    private void retryMissingAction(int id) {
        if (++missingActionChecks >= 8) stopScroll();
        else handler.postDelayed(() -> swipeStep(id), 120);
    }

    private void playTapFeedback(View view) {
        Vibrator vibrator;
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            VibratorManager manager = (VibratorManager) getSystemService(VIBRATOR_MANAGER_SERVICE);
            vibrator = manager == null ? null : manager.getDefaultVibrator();
        } else vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (vibrator != null && vibrator.hasVibrator() && android.os.Build.VERSION.SDK_INT >= 29)
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK));
        else view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
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
            boolean matchesCurrentList = scrollClass == null
                    || scrollClass.equals(String.valueOf(node.getClassName()));
            node.getBoundsInScreen(bounds);
            long area = (long) bounds.width() * bounds.height();
            // Prefer the substantial inner list over a parent that only moves
            // a toolbar or header. Ignore small controls such as spinners.
            boolean hasBackwardAction = supports(node, AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);
            if (matchesCurrentList && (node.isScrollable() || hasBackwardAction)
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
    private boolean supportsGranularScroll(AccessibilityNodeInfo node) {
        if (android.os.Build.VERSION.SDK_INT < 35) return false;
        try {
            return (Boolean) AccessibilityNodeInfo.class
                    .getMethod("isGranularScrollingSupported").invoke(node);
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }
    private int statusBarHeight() {
        int id = getResources().getIdentifier("status_bar_height", "dimen", "android");
        return id > 0 ? getResources().getDimensionPixelSize(id) : dp(24);
    }
    private int dp(int n) { return (int)(n * getResources().getDisplayMetrics().density + .5f); }
}
