package com.tiptop.app;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class ScrollFramePacerTest {
    @Test public void genericSemanticViewCanAnimateBeforeNextRequest() {
        ScrollFramePacer pacer = new ScrollFramePacer();
        pacer.resetForView("android.view.View");
        assertTrue(pacer.shouldAdvance(0));
        for (int i = 1; i < 6; i++)
            assertFalse(pacer.shouldAdvance(Math.round(i * 1_000_000_000.0 / 60)));
        assertTrue(pacer.shouldAdvance(100_000_000));
    }

    @Test public void switchingBackToRecyclerViewRestoresFastCadence() {
        ScrollFramePacer pacer = new ScrollFramePacer();
        pacer.resetForView("android.view.View");
        assertTrue(pacer.shouldAdvance(0));
        pacer.resetForView("androidx.recyclerview.widget.RecyclerView");
        assertTrue(pacer.shouldAdvance(0));
        assertTrue(pacer.shouldAdvance(16_666_667));
    }

    @Test public void sixtyHertzAdvancesEveryFrame() {
        assertEquals(60, advancesAtRate(60).size());
    }

    @Test public void highRefreshDoesNotDoubleTheRequestRate() {
        List<Integer> frames = advancesAtRate(120);
        assertEquals(60, frames.size());
        for (int i = 0; i < frames.size(); i++) assertEquals(i * 2, (int) frames.get(i));
    }

    @Test public void ninetyHertzKeepsAnEvenFrameCadence() {
        List<Integer> frames = advancesAtRate(90);
        assertEquals(45, frames.size());
        for (int i = 0; i < frames.size(); i++) assertEquals(i * 2, (int) frames.get(i));
    }

    @Test public void slowFrameDoesNotCauseCatchUpBursts() {
        ScrollFramePacer pacer = new ScrollFramePacer();
        assertTrue(pacer.shouldAdvance(0));
        assertTrue(pacer.shouldAdvance(200_000_000));
        assertFalse(pacer.shouldAdvance(200_000_000));
        assertFalse(pacer.shouldAdvance(201_000_000));
        assertFalse(pacer.shouldAdvance(208_333_333));
        assertTrue(pacer.shouldAdvance(216_666_667));
    }

    @Test public void changingRefreshRateDoesNotLeaveAnOldTimerRunning() {
        ScrollFramePacer pacer = new ScrollFramePacer();
        assertTrue(pacer.shouldAdvance(0));
        assertTrue(pacer.shouldAdvance(16_666_667));
        assertFalse(pacer.shouldAdvance(25_000_000));
        assertTrue(pacer.shouldAdvance(33_333_333));
        assertTrue(pacer.shouldAdvance(50_000_000));
    }

    @Test public void restartingClearsPreviousRunTiming() {
        ScrollFramePacer pacer = new ScrollFramePacer();
        assertTrue(pacer.shouldAdvance(100_000_000));
        pacer.reset();
        assertTrue(pacer.shouldAdvance(101_000_000));
    }

    private List<Integer> advancesAtRate(int hz) {
        ScrollFramePacer pacer = new ScrollFramePacer();
        List<Integer> frames = new ArrayList<>();
        for (int i = 0; i < hz; i++) {
            if (pacer.shouldAdvance(Math.round(i * 1_000_000_000.0 / hz))) frames.add(i);
        }
        return frames;
    }
}
