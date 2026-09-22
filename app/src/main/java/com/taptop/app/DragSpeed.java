package com.taptop.app;

final class DragSpeed {
    private static final int[] DP_PER_SECOND = {180, 360, 720, 1440, 2160};

    static float pixelsPerSecond(int speed, float density) {
        return DP_PER_SECOND[Math.max(0, Math.min(DP_PER_SECOND.length - 1, speed))] * density;
    }
}
