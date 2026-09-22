package com.tiptop.app;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.Rect;
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
        // panning. Finish each single-finger stroke before starting the next.
        this.singlePointer = singlePointer;
        float velocity = DragSpeed.pixelsPerSecond(speed, density);
        float distance = Math.min(bounds.height() * .60f, velocity * .8f);
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
        if (finished) return;
        // Latch completion before dispatch: cancellation/completion callbacks
        // from the old segment must never schedule another segment.
        finish(true);
        Path cancel = new Path();
        cancel.moveTo(cancelX, cancelY);
        service.dispatchGesture(new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(cancel, 0, 1))
                .build(), null, null);
    }

    private void dispatchNext() {
        if (finished) return;
        if (stopping) { release(); return; }
        GestureDescription.Builder builder = new GestureDescription.Builder();
        if (heldStroke != null) {
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
        long startTime = heldStroke == null ? 0 : 1;
        GestureDescription.StrokeDescription next =
                new GestureDescription.StrokeDescription(drag, startTime, duration, !singlePointer);
        builder.addStroke(next);
        inFlight = true;
        if (!service.dispatchGesture(builder.build(), new AccessibilityService.GestureResultCallback() {
            @Override public void onCompleted(GestureDescription gesture) {
                if (finished) return;
                inFlight = false;
                heldStroke = singlePointer ? null : next;
                completedSegments++;
                if (completedSegments <= 2 || completedSegments % 10 == 0)
                    Log.d("TipTopScroll", "drag segments completed=" + completedSegments);
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
