package com.taptop.app;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.SystemClock;
import android.util.Log;

/** Uses held pointer handovers for native lists, single-finger strokes for web content. */
final class ContinuousDragScroller {
    interface Listener { void onStopped(boolean cancelled); }

    private final AccessibilityService service;
    private final Listener listener;
    private final float x, startY, endY;
    private final long duration;
    private final boolean singlePointer;
    private GestureDescription.StrokeDescription heldStroke;
    private boolean stopping;
    private boolean finished;
    private boolean inFlight;
    private int completedSegments;

    ContinuousDragScroller(AccessibilityService service, Rect bounds, float density,
            int speed, Listener listener) {
        this(service, bounds, density, speed, false, listener);
    }

    ContinuousDragScroller(AccessibilityService service, Rect bounds, float density,
            int speed, boolean singlePointer, Listener listener) {
        this.service = service;
        this.listener = listener;
        // Web content can interpret overlapping pointers as a pinch and stop
        // panning. Replace its held pointer with a fresh gesture instead.
        this.singlePointer = singlePointer;
        float velocity = DragSpeed.pixelsPerSecond(speed, density);
        // WebViews need a pointer reset between strokes. Use more of the viewport and
        // allow longer slow strokes to reduce those disruptive handoffs without
        // changing velocity. Physical touches still cancel the in-flight stroke.
        float distance = singlePointer
                ? Math.min(bounds.height() * .80f, velocity * 1.6f)
                : Math.min(bounds.height() * .60f, velocity * .8f);
        x = bounds.centerX();
        startY = bounds.centerY() - distance / 2;
        endY = startY + distance;
        duration = Math.max(1, Math.round(distance * 1000 / velocity));
    }

    void start() { dispatchNext(); }

    void stop() {
        stopping = true;
        if (!inFlight && !finished) release();
    }

    void cancelImmediately(float cancelX, float cancelY) {
        cancelImmediately(cancelX, cancelY, null);
    }

    void cancelImmediately(float cancelX, float cancelY, Runnable delivered) {
        if (finished) {
            if (delivered != null) delivered.run();
            return;
        }
        // Latch completion before dispatch: cancellation/completion callbacks
        // from the old segment must never schedule another segment.
        finish(true);
        Path cancel = new Path();
        cancel.moveTo(cancelX, cancelY);
        AccessibilityService.GestureResultCallback callback = delivered == null ? null
                : new AccessibilityService.GestureResultCallback() {
                    @Override public void onCompleted(GestureDescription gesture) { delivered.run(); }
                    @Override public void onCancelled(GestureDescription gesture) { delivered.run(); }
                };
        boolean accepted = service.dispatchGesture(new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(cancel, 0, 1))
                .build(), callback, null);
        if (!accepted && delivered != null) delivered.run();
    }

    private void dispatchNext() {
        if (finished) return;
        if (stopping) { release(); return; }
        GestureDescription.Builder builder = new GestureDescription.Builder();
        if (heldStroke != null && !singlePointer) {
            // The replacement pointer goes down before the old one goes up.
            // A list already being dragged can switch pointers without a new
            // touch-slop threshold or a fling at the end of every screenful.
            builder.addStroke(heldStroke.continueStroke(point(endY), 0, 2, false));
        }
        Path drag = point(startY);
        drag.lineTo(x, endY);
        // MotionEventInjector validates continuation using the first time step.
        // It must contain ONLY the old pointer. Introduce the new one at t=1,
        // then lift the old one at t=2, keeping a pointer down throughout.
        // A fresh WebView gesture cancels the previous held pointer before its
        // DOWN. Unlike UP, CANCEL does not start a fling between strokes.
        long startTime = heldStroke == null || singlePointer ? 0 : 1;
        GestureDescription.StrokeDescription next =
                new GestureDescription.StrokeDescription(drag, startTime, duration, true);
        builder.addStroke(next);
        inFlight = true;
        final long dispatchedAt = SystemClock.uptimeMillis();
        if (!service.dispatchGesture(builder.build(), new AccessibilityService.GestureResultCallback() {
            @Override public void onCompleted(GestureDescription gesture) {
                if (finished) return;
                inFlight = false;
                heldStroke = next;
                completedSegments++;
                if (completedSegments <= 2 || completedSegments % 10 == 0)
                    Log.d("TapTopScroll", "drag segments completed=" + completedSegments
                            + "; durationMs=" + duration + "; callbackOverheadMs="
                            + (SystemClock.uptimeMillis() - dispatchedAt - duration - startTime));
                dispatchNext();
            }
            @Override public void onCancelled(GestureDescription gesture) {
                inFlight = false;
                finish(true);
            }
        }, null)) {
            inFlight = false;
            stopping = true;
            release();
        }
    }

    private void release() {
        if (finished) return;
        if (heldStroke == null) { finish(false); return; }
        // Briefly hold still before lifting so stopping does not launch a fling.
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(heldStroke.continueStroke(point(endY), 0, 120, false)).build();
        heldStroke = null;
        inFlight = true;
        if (!service.dispatchGesture(gesture, new AccessibilityService.GestureResultCallback() {
            @Override public void onCompleted(GestureDescription ignored) { finish(false); }
            @Override public void onCancelled(GestureDescription ignored) { finish(true); }
        }, null)) finish(true);
    }

    private Path point(float y) {
        Path path = new Path();
        path.moveTo(x, y);
        return path;
    }

    private void finish(boolean cancelled) {
        if (finished) return;
        finished = true;
        inFlight = false;
        heldStroke = null;
        listener.onStopped(cancelled);
    }
}
