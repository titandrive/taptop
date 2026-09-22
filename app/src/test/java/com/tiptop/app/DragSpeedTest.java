package com.tiptop.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class DragSpeedTest {
    @Test public void slowerSettingsChangeVelocityNotPauses() {
        float previous = 0;
        for (int speed = 0; speed <= ScrollFramePacer.DEFAULT_SPEED; speed++) {
            float velocity = DragSpeed.pixelsPerSecond(speed, 1);
            assertTrue(velocity > previous);
            previous = velocity;
        }
    }

    @Test public void densityDoesNotChangeDpTravelPerSecond() {
        for (int speed = 0; speed <= ScrollFramePacer.DEFAULT_SPEED; speed++)
            assertEquals(DragSpeed.pixelsPerSecond(speed, 1),
                    DragSpeed.pixelsPerSecond(speed, 2.625f) / 2.625f, .01f);
    }

    @Test public void malformedSettingsCannotCreateZeroSpeed() {
        assertEquals(180, DragSpeed.pixelsPerSecond(-100, 1), 0);
        assertEquals(2160, DragSpeed.pixelsPerSecond(100, 1), 0);
    }
}
