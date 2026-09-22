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
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.util.Log;
import android.view.Choreographer;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyCharacterMap;
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
    private SharedPreferences prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Choreographer choreographer;
    private final ScrollFramePacer framePacer = new ScrollFramePacer();
    private AccessibilityNodeInfo movingList;
    private ContinuousDragScroller dragScroller;
    private long lastDragCancellation;
    private View stopRegion;
    private boolean consumingStopTouch;
    private long scrollStarted;
    private long lastProgress;
    private int scrollEvents;
    private long scrollDistance;
    private int scrollRequests;
    private long lastRequestTime;
    private long maxRequestGap;
    private long maxActionDuration;
    private final Choreographer.FrameCallback advanceScroll = this::advanceNativeScroll;
    private final Runnable scrollWatchdog = this::checkScrollProgress;
    private static final int FLING_DURATION_MS = 90;
    // AndroidX publishes these separately from the platform's API 35 properties.
    private static final String COMPAT_BOOLEAN_PROPERTIES =
            "androidx.view.accessibility.AccessibilityNodeInfoCompat.BOOLEAN_PROPERTY_KEY";
    private static final int COMPAT_GRANULAR_SCROLLING = 1 << 26;
    private static final String COMPAT_SCROLL_AMOUNT =
            "androidx.core.view.accessibility.action.ARGUMENT_SCROLL_AMOUNT_FLOAT";
    private final BroadcastReceiver update = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            stopNativeScroll("settings changed");
            showBar();
        }
    };

    @Override protected void onServiceConnected() {
        connected = true;
        prefs = getSharedPreferences("settings", MODE_PRIVATE);
        windows = (WindowManager) getSystemService(WINDOW_SERVICE);
        choreographer = Choreographer.getInstance();
        registerReceiver(update, new IntentFilter(ACTION_UPDATE), Context.RECEIVER_NOT_EXPORTED);
        showBar();
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (movingList == null) return;
        if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            AccessibilityNodeInfo source = event.getSource();
            if (!movingList.equals(source)) return;
            lastProgress = SystemClock.uptimeMillis();
            scrollEvents++;
            if (android.os.Build.VERSION.SDK_INT >= 28 && event.getScrollDeltaY() != -1)
                scrollDistance += Math.abs((long) event.getScrollDeltaY());
            // Scroll events invalidate the accessibility cache. Use their source
            // snapshot instead of forcing another cross-process refresh mid-frame.
            movingList = source;
        } else if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && event.getPackageName() != null
                && !event.getPackageName().equals(movingList.getPackageName())
                && !event.getPackageName().equals(getPackageName())) {
            stopNativeScroll("app changed");
        }
    }
    @Override public void onInterrupt() { stopNativeScroll("interrupted"); }
    @Override public void onDestroy() {
        connected = false;
        consumingStopTouch = false;
        stopNativeScroll("service stopped");
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
            if (event.getActionMasked() == MotionEvent.ACTION_OUTSIDE) {
                // Accessibility gestures use the virtual device. Only a real
                // touch should interrupt our own ongoing injected movement.
                if (dragScroller != null
                        && event.getDeviceId() != KeyCharacterMap.VIRTUAL_KEYBOARD) {
                    Log.d("TipTopScroll", "physical screen touch: cancel drag immediately");
                    cancelDragOnTouch();
                }
                return true;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                stoppedOnDown[0] = dragScroller != null
                        || SystemClock.uptimeMillis() - lastDragCancellation < 150;
                if (!stoppedOnDown[0]) playTapFeedback(v);
                if (dragScroller != null) cancelDragOnTouch();
                return true;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                if (!stoppedOnDown[0]) v.performClick();
                return true;
            }
            return true;
        });
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
            dp(prefs.getInt("width", 100)), dp(prefs.getInt("height", 36)),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            android.graphics.PixelFormat.TRANSLUCENT);
        String position = prefs.getString("position", "Center");
        params.gravity = Gravity.TOP | (position.equals("Left") ? Gravity.LEFT : position.equals("Right") ? Gravity.RIGHT : Gravity.CENTER_HORIZONTAL);
        // Anchor to the physical screen, not the status-bar content inset.
        // Subtracting a guessed inset produces negative offsets that Android
        // clamps to zero, leaving the first part of the slider unresponsive.
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            params.setFitInsetsTypes(0);
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        } else if (android.os.Build.VERSION.SDK_INT >= 28)
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        params.y = dp(prefs.getInt("offset", 8));
        try { windows.addView(view, params); bar = view; } catch (WindowManager.BadTokenException ignored) {}
    }

    private void scrollToTop() {
        if (dragScroller != null) { stopNativeScroll("bar tap"); return; }
        if (movingList != null) { stopNativeScroll("bar tap"); return; }
        AccessibilityNodeInfo target = findScrollable();
        int speed = ScrollFramePacer.clampSpeed(
                prefs.getInt("scroll_speed", ScrollFramePacer.DEFAULT_SPEED));
        boolean hasBackwardAction = target != null
                && supports(target, AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);
        if (speed < ScrollFramePacer.DEFAULT_SPEED && hasBackwardAction) {
            startContinuousDrag(target, speed);
            return;
        }
        // Maximum retains the target app's existing native animation.
        // Lower speeds use a held drag with an explicit travel velocity.
        if (speed == ScrollFramePacer.DEFAULT_SPEED && hasBackwardAction
                && supportsGranularScroll(target)) {
            Bundle args = new Bundle();
            args.putFloat("android.view.accessibility.action.ARGUMENT_SCROLL_AMOUNT_FLOAT",
                    Float.POSITIVE_INFINITY);
            args.putFloat(COMPAT_SCROLL_AMOUNT, Float.POSITIVE_INFINITY);
            // RecyclerView handles this as one native smoothScrollToPosition(0).
            // Leave the screen touchable so a normal touch can cancel its animation.
            if (target.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD, args)) {
                logScrollMode("native smooth", target);
                return;
            }
        }
        if (target != null && (speed == ScrollFramePacer.DEFAULT_SPEED || !hasBackwardAction)
                && supports(target,
                AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_TO_POSITION.getId())) {
            Bundle args = new Bundle();
            args.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_ROW_INT, 0);
            args.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_COLUMN_INT, 0);
            if (target.performAction(
                    AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_TO_POSITION.getId(), args)) {
                logScrollMode("direct position", target);
                return;
            }
        }
        if (target != null && supports(target, AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
                && target.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)) {
            movingList = target;
            scrollStarted = lastProgress = lastRequestTime = SystemClock.uptimeMillis();
            scrollEvents = 0;
            scrollDistance = 0;
            scrollRequests = 1;
            maxRequestGap = maxActionDuration = 0;
            framePacer.resetForView(target.getClassName());
            framePacer.shouldAdvance(System.nanoTime());
            logScrollMode("paced native v2", target);
            showStopRegion();
            if (movingList == null) return;
            if (bar != null) bar.getBackground().setTint(Color.rgb(220, 64, 64));
            choreographer.postFrameCallback(advanceScroll);
            handler.postDelayed(scrollWatchdog, 250);
            return;
        }
        // Keep the existing preference so an explicitly disabled fallback stays off.
        if (!prefs.getBoolean("legacy_swipes", true) || target == null
                || !supports(target, AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)) return;
        flingOnce(target);
    }

    private void startContinuousDrag(AccessibilityNodeInfo target, int speed) {
        Rect bounds = new Rect();
        target.getBoundsInScreen(bounds);
        if (!bounds.intersect(0, 0, getResources().getDisplayMetrics().widthPixels,
                getResources().getDisplayMetrics().heightPixels)
                || bounds.height() < dp(180)) return;
        movingList = target;
        scrollStarted = lastProgress = SystemClock.uptimeMillis();
        scrollEvents = scrollRequests = 0;
        scrollDistance = maxRequestGap = maxActionDuration = 0;
        dragScroller = new ContinuousDragScroller(this, bounds,
                getResources().getDisplayMetrics().density, speed, cancelled -> {
                    dragScroller = null;
                    if (cancelled) lastDragCancellation = SystemClock.uptimeMillis();
                    stopNativeScroll(cancelled ? "drag cancelled" : "drag finished");
                });
        logScrollMode("continuous drag speed=" + speed, target);
        if (bar != null) bar.getBackground().setTint(Color.rgb(220, 64, 64));
        handler.postDelayed(scrollWatchdog, 250);
        dragScroller.start();
    }

    private void advanceNativeScroll(long frameTimeNanos) {
        if (movingList == null) return;
        if (!framePacer.shouldAdvance(frameTimeNanos)) {
            choreographer.postFrameCallback(advanceScroll);
            return;
        }
        long now = SystemClock.uptimeMillis();
        if (now - scrollStarted > 60000 || now - lastProgress > 1500) {
            stopNativeScroll("no progress or time limit");
            return;
        }
        if (!movingList.isVisibleToUser()
                || !supports(movingList, AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)) {
            stopNativeScroll("top or target unavailable");
            return;
        }
        // Renew the native animation before its easing curve slows to a stop.
        // No finger-down events interrupt the list, and no page-sized pauses occur.
        maxRequestGap = Math.max(maxRequestGap, now - lastRequestTime);
        lastRequestTime = now;
        scrollRequests++;
        boolean accepted = movingList.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);
        maxActionDuration = Math.max(maxActionDuration, SystemClock.uptimeMillis() - now);
        if (!accepted) {
            stopNativeScroll("native action finished");
            return;
        }
        choreographer.postFrameCallback(advanceScroll);
    }

    private void checkScrollProgress() {
        if (movingList == null) return;
        long now = SystemClock.uptimeMillis();
        if (dragScroller != null && (!movingList.isVisibleToUser()
                || !supports(movingList, AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD))) {
            stopNativeScroll("top or target unavailable");
            return;
        }
        // This still runs if display frame callbacks pause, e.g. when the screen locks.
        if (now - scrollStarted > 60000 || now - lastProgress > 1500)
            stopNativeScroll("no progress or time limit");
        else handler.postDelayed(scrollWatchdog, 250);
    }

    private void stopNativeScroll(String reason) {
        if (choreographer != null) choreographer.removeFrameCallback(advanceScroll);
        handler.removeCallbacks(scrollWatchdog);
        framePacer.reset();
        if (movingList != null) Log.d("TipTopScroll", "native stopped: " + reason
                + "; events=" + scrollEvents + "; distance=" + scrollDistance
                + "; elapsed=" + (SystemClock.uptimeMillis() - scrollStarted)
                + "; requests=" + scrollRequests + "; maxGapMs=" + maxRequestGap
                + "; maxActionMs=" + maxActionDuration);
        movingList = null;
        if (dragScroller != null) dragScroller.stop();
        if (!consumingStopTouch) removeStopRegion();
        if (bar != null) bar.getBackground().setTint(Color.rgb(39, 104, 244));
    }

    private void cancelDragOnTouch() {
        if (dragScroller == null || bar == null) return;
        int[] location = new int[2];
        bar.getLocationOnScreen(location);
        // A new gesture cancels pending injected movement immediately. Put its
        // stationary contact on our own bar, where the cancellation timestamp
        // suppresses the click, instead of tapping anything in the target app.
        dragScroller.cancelImmediately(location[0] + bar.getWidth() / 2f,
                Math.max(0, location[1] + bar.getHeight() / 2f));
    }

    private void showStopRegion() {
        removeStopRegion();
        View region = new View(this);
        region.setOnTouchListener((view, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                consumingStopTouch = true;
                stopNativeScroll("screen touch");
            } else if (event.getActionMasked() == MotionEvent.ACTION_UP
                    || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                consumingStopTouch = false;
                removeStopRegion();
            }
            return true;
        });
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                android.graphics.PixelFormat.TRANSLUCENT);
        try { windows.addView(region, params); stopRegion = region; }
        catch (WindowManager.BadTokenException ignored) { stopNativeScroll("overlay unavailable"); }
    }

    private void removeStopRegion() {
        if (stopRegion != null) { windows.removeView(stopRegion); stopRegion = null; }
    }

    private void flingOnce(AccessibilityNodeInfo target) {
        Rect bounds = new Rect();
        target.getBoundsInScreen(bounds);
        if (!bounds.intersect(0, 0, getResources().getDisplayMetrics().widthPixels,
                getResources().getDisplayMetrics().heightPixels)
                || bounds.height() < dp(180)) return;
        Path path = new Path();
        path.moveTo(bounds.centerX(), bounds.top + bounds.height() * .20f);
        path.lineTo(bounds.centerX(), bounds.top + bounds.height() * .80f);
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0,
                        FLING_DURATION_MS))
                .build();
        logScrollMode("single fling", target);
        // Dispatch exactly once. A subsequent down would interrupt the app's
        // momentum. Leave the list touchable so the user can stop it normally.
        if (!dispatchGesture(gesture, new GestureResultCallback() {
            @Override public void onCompleted(GestureDescription description) {
                Log.d("TipTopScroll", "fling released; app controls momentum");
            }
            @Override public void onCancelled(GestureDescription description) {
                Log.d("TipTopScroll", "fling cancelled");
            }
        }, null)) Log.d("TipTopScroll", "fling rejected");
    }

    private void playTapFeedback(View view) {
        if (!prefs.getBoolean("haptics", true)) return;
        Vibrator vibrator;
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            VibratorManager manager = (VibratorManager) getSystemService(VIBRATOR_MANAGER_SERVICE);
            vibrator = manager == null ? null : manager.getDefaultVibrator();
        } else vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (vibrator != null && vibrator.hasVibrator() && android.os.Build.VERSION.SDK_INT >= 29)
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK));
        else view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
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
    private boolean supportsGranularScroll(AccessibilityNodeInfo node) {
        if ((node.getExtras().getInt(COMPAT_BOOLEAN_PROPERTIES, 0)
                & COMPAT_GRANULAR_SCROLLING) != 0) return true;
        if (android.os.Build.VERSION.SDK_INT < 35) return false;
        try {
            return (Boolean) AccessibilityNodeInfo.class
                    .getMethod("isGranularScrollingSupported").invoke(node);
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }
    private void logScrollMode(String mode, AccessibilityNodeInfo node) {
        // Diagnostic metadata only; never log list text or screen content.
        Log.d("TipTopScroll", mode + (node == null ? ": no target" :
                ": " + node.getPackageName() + " / " + node.getClassName()));
    }
    private int dp(int n) { return (int)(n * getResources().getDisplayMetrics().density + .5f); }
}
