package com.taptop.app;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.Choreographer;
import android.view.Gravity;
import android.view.KeyCharacterMap;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public class TopService extends AccessibilityService {
    public static final String ACTION_UPDATE_APP_FILTER = "com.taptop.app.UPDATE_APP_FILTER";
    public static final String ACTION_UPDATE = "com.taptop.app.UPDATE";
    public static final String ACTION_REFRESH_APPEARANCE = "com.taptop.app.REFRESH_APPEARANCE";
    public static final String ACTION_SCROLL_TO_TOP = "com.taptop.app.SCROLL_TO_TOP";
    public static volatile boolean connected = false;
    private static final List<Runnable> connectionListeners = new ArrayList<>();

    // Service and activity lifecycle callbacks run on the main thread.
    static void addConnectionListener(Runnable listener) { connectionListeners.add(listener); }
    static void removeConnectionListener(Runnable listener) { connectionListeners.remove(listener); }
    private static void setConnected(boolean value) {
        connected = value;
        for (Runnable listener : new ArrayList<>(connectionListeners)) listener.run();
    }
    private WindowManager windows;
    private View bar;
    private Configuration overlayConfiguration;
    private SharedPreferences prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Choreographer choreographer;
    private final ScrollFramePacer framePacer = new ScrollFramePacer();
    private AccessibilityNodeInfo movingList;
    private AccessibilityNodeInfo recentScrollTarget;
    private ContinuousDragScroller dragScroller;
    private long lastDragCancellation;
    private View stopRegion;
    private boolean consumingStopTouch;
    private long scrollStarted;
    private long lastProgress;
    private int smallestDragScrollY = Integer.MAX_VALUE;
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
    private final AutomationReceiver automation = new AutomationReceiver();
    private final BroadcastReceiver update = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (ACTION_UPDATE_APP_FILTER.equals(intent.getAction())) {
                refreshAppFilter();
                return;
            }
            if (ACTION_SCROLL_TO_TOP.equals(intent.getAction())) {
                scrollToTop();
                return;
            }
            if (ACTION_REFRESH_APPEARANCE.equals(intent.getAction())) {
                if (bar != null) applyBarAppearance(bar);
                return;
            }
            if (dragScroller != null && !currentAppAllowed()) cancelDragOnTouch();
            stopNativeScroll("settings changed");
            showBar();
        }
    };

    @Override protected void onServiceConnected() {
        prefs = getSharedPreferences("settings", MODE_PRIVATE);
        windows = (WindowManager) getSystemService(WINDOW_SERVICE);
        overlayConfiguration = new Configuration(getResources().getConfiguration());
        choreographer = Choreographer.getInstance();
        IntentFilter filter = new IntentFilter(ACTION_UPDATE);
        filter.addAction(ACTION_REFRESH_APPEARANCE);
        filter.addAction(ACTION_SCROLL_TO_TOP);
        filter.addAction(ACTION_UPDATE_APP_FILTER);
        registerReceiver(update, filter, Context.RECEIVER_NOT_EXPORTED);
        IntentFilter automationFilter = new IntentFilter();
        automationFilter.addAction(ShortcutActions.ACTION_TOGGLE);
        automationFilter.addAction(ShortcutActions.ACTION_TURN_ON);
        automationFilter.addAction(ShortcutActions.ACTION_TURN_OFF);
        automationFilter.addAction(ShortcutActions.ACTION_SCROLL_TO_TOP);
        registerReceiver(automation, automationFilter, Context.RECEIVER_EXPORTED);
        showBar();
        setConnected(true);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                || event.getEventType() == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            recentScrollTarget = null;
            if (!AppFilter.ALL.equals(AppFilter.mode(prefs))) refreshAppFilter();
        }
        if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            AccessibilityNodeInfo source = event.getSource();
            if (source != null && isSubstantialScrollTarget(source)) recentScrollTarget = source;
        }
        if (movingList == null) return;
        if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            AccessibilityNodeInfo source = event.getSource();
            if (!movingList.equals(source)) return;
            if (dragScroller != null && event.getMaxScrollY() > 0)
                dragScroller.approachingTop(event.getScrollY());
            // Some browser engines retain ACTION_SCROLL_BACKWARD at the top.
            // A valid absolute range is stronger evidence than that action flag.
            if (dragScroller != null && event.getScrollY() == 0 && event.getMaxScrollY() > 0) {
                cancelDragOnTouch();
                return;
            }
            // Repeated/bouncing events are not progress toward the top.
            if (dragScroller == null || event.getMaxScrollY() <= 0 || event.getScrollY() < 0
                    || event.getScrollY() < smallestDragScrollY) {
                lastProgress = SystemClock.uptimeMillis();
                if (dragScroller != null && event.getMaxScrollY() > 0 && event.getScrollY() >= 0)
                    smallestDragScrollY = event.getScrollY();
            }
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
    @Override public void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
        Configuration previous = overlayConfiguration;
        overlayConfiguration = new Configuration(configuration);
        if (prefs == null || windows == null) return;
        boolean geometryChanged = previous == null
                || previous.screenWidthDp != configuration.screenWidthDp
                || previous.screenHeightDp != configuration.screenHeightDp
                || previous.densityDpi != configuration.densityDpi
                || previous.orientation != configuration.orientation;
        if (geometryChanged) {
            // A fold/display transition can change both the pixel size and the
            // cutout placement. Recreate the touch window with current metrics.
            if (dragScroller != null) cancelDragOnTouch();
            stopNativeScroll("display geometry changed");
            recentScrollTarget = null;
            showBar();
            Log.d("TapTopScroll", "tap zone rebuilt after display geometry change");
        } else if (bar != null) {
            // Theme-only changes do not need to recreate the touch window.
            applyBarAppearance(bar);
        }
    }
    @Override public boolean onUnbind(Intent intent) {
        setConnected(false);
        stopNativeScroll("service disconnected");
        return super.onUnbind(intent);
    }
    @Override public void onDestroy() {
        setConnected(false);
        consumingStopTouch = false;
        stopNativeScroll("service stopped");
        try { unregisterReceiver(update); } catch (IllegalArgumentException ignored) {}
        try { unregisterReceiver(automation); } catch (IllegalArgumentException ignored) {}
        if (bar != null) windows.removeView(bar);
        bar = null;
        super.onDestroy();
    }

    private void showBar() {
        if (windows == null) return;
        if (bar != null) { windows.removeView(bar); bar = null; }
        if (!prefs.getBoolean("taptop_enabled", true)
                || !prefs.getBoolean("tap_zone_enabled", true) || !currentAppAllowed()) return;
        View view = new View(this);
        applyBarAppearance(view);
        view.setContentDescription("Scroll to top");
        view.setOnClickListener(v -> scrollToTop());
        final int touchSlop = ViewConfiguration.get(this).getScaledTouchSlop();
        view.setOnTouchListener(new View.OnTouchListener() {
            private float downX, downY;
            private boolean tracking, moved, stoppedOnDown, shadeRequested;

            @Override public boolean onTouch(View v, MotionEvent event) {
                int action = event.getActionMasked();
                if (action == MotionEvent.ACTION_OUTSIDE) {
                    // Only physical touches interrupt our injected movement.
                    if (dragScroller != null
                            && event.getDeviceId() != KeyCharacterMap.VIRTUAL_KEYBOARD) {
                        cancelDragOnTouch();
                    }
                    return true;
                }
                if (action == MotionEvent.ACTION_DOWN) {
                    downX = event.getRawX();
                    downY = event.getRawY();
                    tracking = true;
                    moved = shadeRequested = false;
                    stoppedOnDown = dragScroller != null || movingList != null
                            || SystemClock.uptimeMillis() - lastDragCancellation < 150;
                    if (dragScroller != null) cancelDragOnTouch();
                    if (movingList != null) stopNativeScroll("tap zone touch");
                    return true;
                }
                if (action == MotionEvent.ACTION_CANCEL
                        || action == MotionEvent.ACTION_POINTER_DOWN) {
                    tracking = false;
                    return true;
                }
                if (!tracking) return true;
                if (action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_UP) {
                    float dx = event.getRawX() - downX;
                    float dy = event.getRawY() - downY;
                    if (dx * dx + dy * dy > touchSlop * touchSlop) moved = true;
                    // Include batched positions so leaving and returning to the
                    // start point never turns a swipe back into a tap.
                    float rawOffsetX = event.getRawX() - event.getX();
                    float rawOffsetY = event.getRawY() - event.getY();
                    for (int i = 0; i < event.getHistorySize(); i++) {
                        float hx = event.getHistoricalX(i) + rawOffsetX - downX;
                        float hy = event.getHistoricalY(i) + rawOffsetY - downY;
                        if (hx * hx + hy * hy > touchSlop * touchSlop) moved = true;
                    }
                    if (!stoppedOnDown && !shadeRequested
                            && dy > touchSlop && dy > Math.abs(dx)) {
                        shadeRequested = true;
                        performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS);
                    }
                    if (action == MotionEvent.ACTION_UP) {
                        tracking = false;
                        if (!stoppedOnDown && !moved && !shadeRequested) {
                            playTapFeedback(v);
                            v.performClick();
                        }
                    }
                }
                return true;
            }
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
        if (!prefs.getBoolean("taptop_enabled", true)) return;
        if (!currentAppAllowed()) {
            if (dragScroller != null) cancelDragOnTouch();
            stopNativeScroll("app excluded");
            return;
        }
        if (dragScroller != null) { stopNativeScroll("bar tap"); return; }
        if (movingList != null) { stopNativeScroll("bar tap"); return; }
        long lookupStarted = SystemClock.uptimeMillis();
        AccessibilityNodeInfo target = findScrollable();
        logScrollMode("selected target; lookupMs="
                + (SystemClock.uptimeMillis() - lookupStarted), target);
        int speed = ScrollFramePacer.clampSpeed(
                prefs.getInt("scroll_speed", ScrollFramePacer.DEFAULT_SPEED));
        boolean hasBackwardAction = target != null
                && supports(target, AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);
        boolean webContent = hasBackwardAction && isWebContent(target);
        if ((speed < ScrollFramePacer.DEFAULT_SPEED || webContent) && hasBackwardAction) {
            startContinuousDrag(target, speed, webContent);
            return;
        }
        // Item zero is not necessarily the visual top (e.g. reversed chats).
        // Only use absolute collection jumps when visible rows confirm order.
        boolean collectionStartsAtTop = target != null && collectionStartsAtTop(target);
        if (speed == ScrollFramePacer.DEFAULT_SPEED && hasBackwardAction
                && collectionStartsAtTop && supportsGranularScroll(target)) {
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
        if (target != null && collectionStartsAtTop
                && (speed == ScrollFramePacer.DEFAULT_SPEED || !hasBackwardAction)
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
            if (bar != null) applyBarAppearance(bar);
            choreographer.postFrameCallback(advanceScroll);
            handler.postDelayed(scrollWatchdog, 250);
            return;
        }
        // Keep the existing preference so an explicitly disabled fallback stays off.
        if (!prefs.getBoolean("legacy_swipes", true) || target == null
                || !supports(target, AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)) return;
        flingOnce(target);
    }

    private void startContinuousDrag(AccessibilityNodeInfo target, int speed, boolean webContent) {
        Rect bounds = new Rect();
        target.getBoundsInScreen(bounds);
        if (!bounds.intersect(0, 0, getResources().getDisplayMetrics().widthPixels,
                getResources().getDisplayMetrics().heightPixels)
                || bounds.height() < dp(180)) return;
        movingList = target;
        smallestDragScrollY = Integer.MAX_VALUE;
        scrollStarted = lastProgress = SystemClock.uptimeMillis();
        scrollEvents = scrollRequests = 0;
        scrollDistance = maxRequestGap = maxActionDuration = 0;
        dragScroller = new ContinuousDragScroller(this, bounds,
                getResources().getDisplayMetrics().density, speed, webContent, cancelled -> {
                    dragScroller = null;
                    if (cancelled) lastDragCancellation = SystemClock.uptimeMillis();
                    stopNativeScroll(cancelled ? "drag cancelled" : "drag finished");
                });
        dragScroller.setBoundaryReached(this::cancelDragOnTouch);
        logScrollMode("continuous drag speed=" + speed + "; web=" + webContent, target);
        if (bar != null) applyBarAppearance(bar);
        if (bar == null && !showDragTouchMonitor()) {
            stopNativeScroll("touch monitor unavailable");
            return;
        }
        handler.postDelayed(scrollWatchdog, 250);
        dragScroller.start();
    }

    private boolean isWebContent(AccessibilityNodeInfo node) {
        // Scrollable HTML elements are exposed as generic Views underneath the
        // WebView. They need the same single-pointer gestures as its root.
        for (AccessibilityNodeInfo current = node; current != null; current = current.getParent()) {
            if ("android.webkit.WebView".contentEquals(
                    current.getClassName() == null ? "" : current.getClassName())) return true;
        }
        return false;
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
        if (movingList != null) Log.d("TapTopScroll", "native stopped: " + reason
                + "; events=" + scrollEvents + "; distance=" + scrollDistance
                + "; elapsed=" + (SystemClock.uptimeMillis() - scrollStarted)
                + "; requests=" + scrollRequests + "; maxGapMs=" + maxRequestGap
                + "; maxActionMs=" + maxActionDuration);
        movingList = null;
        if (dragScroller != null) dragScroller.stop();
        if (!consumingStopTouch) removeStopRegion();
        if (bar != null) applyBarAppearance(bar);
    }

    private void applyBarAppearance(View view) {
        boolean dark = ThemeColors.isDark(prefs, getResources().getConfiguration());
        int color = movingList == null ? ThemeColors.bar(prefs, dark) : ThemeColors.scrolling(dark);
        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(dp(50));
        // Fill opacity is independent of the adjustment outline. A transparent
        // background keeps the same touch window active outside settings.
        int opacity = prefs.getBoolean("enabled", true)
                ? Math.round(255 * prefs.getInt("opacity", 70) / 100f) : 0;
        background.setColor((color & 0x00ffffff) | (opacity << 24));
        if (MainActivity.foreground && !prefs.getBoolean("enabled", true))
            background.setStroke(dp(2), ThemeColors.accent(dark), dp(2), dp(3));
        view.setBackground(background);
    }

    private void cancelDragOnTouch() {
        View cancellationTarget = bar != null ? bar : stopRegion;
        if (dragScroller == null || cancellationTarget == null) return;
        int[] location = new int[2];
        cancellationTarget.getLocationOnScreen(location);
        // A new gesture cancels pending injected movement immediately. Put its
        // stationary contact on our own bar, where the cancellation timestamp
        // suppresses the click, instead of tapping anything in the target app.
        boolean temporaryMonitor = cancellationTarget == stopRegion;
        if (temporaryMonitor) consumingStopTouch = true;
        dragScroller.cancelImmediately(location[0] + cancellationTarget.getWidth() / 2f,
                Math.max(0, location[1] + cancellationTarget.getHeight() / 2f),
                temporaryMonitor ? () -> {
                    consumingStopTouch = false;
                    if (stopRegion == cancellationTarget) removeStopRegion();
                } : null);
    }

    // Only exists during shortcut-driven drag scrolling when the tap zone is off.
    // A one-pixel corner window observes outside touches without covering content.
    private boolean showDragTouchMonitor() {
        removeStopRegion();
        View monitor = new View(this);
        monitor.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        monitor.setOnTouchListener((v, event) -> {
            if ((event.getActionMasked() == MotionEvent.ACTION_OUTSIDE
                    || event.getActionMasked() == MotionEvent.ACTION_DOWN)
                    && event.getDeviceId() != KeyCharacterMap.VIRTUAL_KEYBOARD) {
                cancelDragOnTouch();
            }
            return true;
        });
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(1, 1,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                android.graphics.PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.BOTTOM | Gravity.LEFT;
        try {
            windows.addView(monitor, params);
            stopRegion = monitor;
            return true;
        } catch (WindowManager.BadTokenException e) {
            return false;
        }
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
                Log.d("TapTopScroll", "fling released; app controls momentum");
            }
            @Override public void onCancelled(GestureDescription description) {
                Log.d("TapTopScroll", "fling cancelled");
            }
        }, null)) Log.d("TapTopScroll", "fling rejected");
    }

    private void playTapFeedback(View view) {
        if (!prefs.getBoolean("haptics", true)) return;
        Haptics.click(view);
    }

    private AccessibilityNodeInfo currentAppRoot() {
        List<AccessibilityWindowInfo> all = getWindows();
        if (all == null) return null;
        AccessibilityWindowInfo chosen = null;
        for (AccessibilityWindowInfo window : all) {
            if (window.getType() != AccessibilityWindowInfo.TYPE_APPLICATION) continue;
            AccessibilityNodeInfo root = window.getRoot();
            if (root == null) continue;
            if (window.isActive()) { chosen = window; break; }
            if (chosen == null || window.isFocused()) chosen = window;
        }
        if (chosen == null) return null;
        return chosen.getRoot();
    }

    private boolean currentAppAllowed() {
        if (AppFilter.ALL.equals(AppFilter.mode(prefs))) return true;
        AccessibilityNodeInfo root = currentAppRoot();
        return AppFilter.allows(prefs, root == null ? null : root.getPackageName());
    }

    private void refreshAppFilter() {
        // Switching back to All can restore a removed bar without recreating
        // an existing one or inspecting the foreground window.
        if (AppFilter.ALL.equals(AppFilter.mode(prefs))) {
            if (bar == null && prefs.getBoolean("taptop_enabled", true)) showBar();
            return;
        }
        AccessibilityNodeInfo root = currentAppRoot();
        CharSequence packageName = root == null ? null : root.getPackageName();
        boolean allowed = AppFilter.allows(prefs, packageName);
        if (!allowed || (movingList != null && (packageName == null
                || !packageName.equals(movingList.getPackageName())))) {
            if (dragScroller != null) cancelDragOnTouch();
            stopNativeScroll("app filter or foreground changed");
        }
        if (!allowed) {
            if (bar != null) { windows.removeView(bar); bar = null; }
        } else if (bar == null && prefs.getBoolean("taptop_enabled", true)) {
            showBar();
        }
    }

    private AccessibilityNodeInfo findScrollable() {
        AccessibilityNodeInfo root = currentAppRoot();
        // Check the chosen foreground app, never fall through to an allowed
        // background window. This also covers shortcuts and automation intents.
        if (root == null || !AppFilter.allows(prefs, root.getPackageName())) return null;
        // A recent scroll event identifies the list the user is interacting
        // with. Validate its live snapshot before avoiding a full tree walk.
        AccessibilityNodeInfo recent = recentScrollTarget;
        recentScrollTarget = null;
        if (recent != null && recent.getWindowId() == root.getWindowId()
                && recent.refresh() && recent.getWindowId() == root.getWindowId()
                && root.getPackageName() != null
                && root.getPackageName().equals(recent.getPackageName())
                && isSubstantialScrollTarget(recent)
                && supports(recent, AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)) {
            recentScrollTarget = recent;
            return recent;
        }
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
            if (android.os.Build.VERSION.SDK_INT >= 34 && node.isAccessibilityDataSensitive())
                Log.d("TapTopScroll", "accessibility-sensitive node: " + node.getClassName());
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
        recentScrollTarget = best;
        return best;
    }

    private boolean isSubstantialScrollTarget(AccessibilityNodeInfo node) {
        if (!node.isVisibleToUser() || (!node.isScrollable()
                && !supports(node, AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD))) return false;
        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);
        long screenArea = (long) getResources().getDisplayMetrics().widthPixels
                * getResources().getDisplayMetrics().heightPixels;
        return bounds.height() > bounds.width() / 2
                && (long) bounds.width() * bounds.height() >= screenArea / 6;
    }

    private boolean collectionStartsAtTop(AccessibilityNodeInfo node) {
        int firstRow = -1;
        int firstTop = 0;
        boolean ascending = false;
        Rect bounds = new Rect();
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null || !child.isVisibleToUser()) continue;
            AccessibilityNodeInfo.CollectionItemInfo item = child.getCollectionItemInfo();
            if (item == null || item.getRowIndex() < 0) continue;
            child.getBoundsInScreen(bounds);
            if (bounds.isEmpty()) continue;
            int row = item.getRowIndex();
            if (firstRow < 0) {
                firstRow = row;
                firstTop = bounds.top;
            } else if (row != firstRow && bounds.top != firstTop) {
                if ((row > firstRow) != (bounds.top > firstTop)) return false;
                ascending = true;
            }
        }
        // One visible item or missing row metadata cannot establish direction.
        // Relative backward scrolling remains available without assuming row 0.
        return ascending;
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
        Log.d("TapTopScroll", mode + (node == null ? ": no target" :
                ": " + node.getPackageName() + " / " + node.getClassName()));
    }
    private int dp(int n) { return (int)(n * getResources().getDisplayMetrics().density + .5f); }
}
