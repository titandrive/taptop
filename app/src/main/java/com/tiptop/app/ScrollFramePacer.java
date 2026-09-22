package com.tiptop.app;

/** Limits native requests to roughly 60 per second, always on display frames. */
final class ScrollFramePacer {
    private long minimumIntervalNanos = 16_000_000L;
    private long lastFrame = Long.MIN_VALUE;

    boolean shouldAdvance(long frameTimeNanos) {
        if (lastFrame != Long.MIN_VALUE && frameTimeNanos - lastFrame < minimumIntervalNanos)
            return false;
        // Anchor to this frame rather than catching up missed requests after a stall.
        lastFrame = frameTimeNanos;
        return true;
    }

    void reset() { lastFrame = Long.MIN_VALUE; }

    void resetForView(CharSequence className) {
        // Generic semantic views can restart their animation from rest on every
        // action. Give those animations time to advance instead of cancelling
        // their startup every frame. RecyclerView keeps its existing cadence.
        minimumIntervalNanos = "android.view.View".contentEquals(
                className == null ? "" : className) ? 96_000_000L : 16_000_000L;
        reset();
    }
}
