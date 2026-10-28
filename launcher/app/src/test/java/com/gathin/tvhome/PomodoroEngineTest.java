package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PomodoroEngineTest {
    private static final long MIN = 60_000L;

    @Test
    public void aFreshTimerWaitsForAFullFocusSession() {
        PomodoroEngine e = new PomodoroEngine();
        assertTrue(e.fresh());
        assertEquals(25 * MIN, e.remaining(1_000));
        assertEquals(0f, e.progress(1_000), 0f);
    }

    @Test
    public void theCountdownIsAnEndTimeSoMissedTicksDoNotDrift() {
        PomodoroEngine e = new PomodoroEngine();
        e.start(0);
        // Nothing is called for ten minutes (no frames, a paused process): the time left is still exact.
        assertEquals(15 * MIN, e.remaining(10 * MIN));
        assertEquals(0.4f, e.progress(10 * MIN), 1e-6);
    }

    @Test
    public void pauseAndResumeKeepTheTimeLeftAndCountOnlyTheRunning() {
        PomodoroEngine e = new PomodoroEngine();
        e.start(0);
        e.pause(10 * MIN);
        assertFalse(e.running);
        assertFalse(e.fresh());
        assertEquals(15 * MIN, e.remaining(99 * MIN));       // paused: the clock does not matter
        assertEquals(10 * MIN, e.focusToday(99 * MIN));
        e.start(100 * MIN);
        assertEquals(15 * MIN, e.remaining(100 * MIN));
        assertEquals(15 * MIN, e.focusToday(105 * MIN));     // the run under way counts too
    }

    @Test
    public void aFinishedSessionCountsAndItsBreakStartsByItself() {
        PomodoroEngine e = new PomodoroEngine();
        e.start(0);
        assertTrue(e.advance(25 * MIN + 500));
        assertEquals(PomodoroEngine.SHORT_BREAK, e.phase);
        assertTrue(e.running);
        assertEquals(1, e.sessionsToday);
        assertEquals(1, e.doneInCycle);
        assertEquals(25 * MIN, e.focusToday(26 * MIN));
        // The break started when the session ended, not when the alarm was read.
        assertEquals(5 * MIN - 500, e.remaining(25 * MIN + 500));
    }

    @Test
    public void aBreakThatEndsLeavesTheNextSessionWaiting() {
        PomodoroEngine e = new PomodoroEngine();
        e.start(0);
        e.advance(25 * MIN);
        assertTrue(e.advance(30 * MIN));
        assertEquals(PomodoroEngine.FOCUS, e.phase);
        assertFalse(e.running);
        assertTrue(e.fresh());
    }

    @Test
    public void aLongStandbyFinishesTheSessionAndTheBreakThatBothRanOut() {
        PomodoroEngine e = new PomodoroEngine();
        e.start(0);
        assertTrue(e.advance(3 * 60 * MIN));
        assertEquals(PomodoroEngine.FOCUS, e.phase);
        assertFalse(e.running);
        assertEquals(1, e.sessionsToday);
        assertEquals(25 * MIN, e.focusToday(3 * 60 * MIN));
    }

    @Test
    public void theFourthSessionEarnsTheLongBreakAndStartsANewCycle() {
        PomodoroEngine e = new PomodoroEngine();
        long t = 0;
        for (int i = 0; i < 4; i++) {
            e.start(t);
            t += 25 * MIN;
            e.advance(t);
            if (i < 3) {
                assertEquals(PomodoroEngine.SHORT_BREAK, e.phase);
                t += 5 * MIN;
                e.advance(t);
            }
        }
        assertEquals(PomodoroEngine.LONG_BREAK, e.phase);
        assertEquals(15 * MIN, e.remaining(t));
        assertEquals(0, e.doneInCycle);
        assertEquals(4, e.sessionsToday);
    }

    @Test
    public void skippingASessionDoesNotCountIt() {
        PomodoroEngine e = new PomodoroEngine();
        e.start(0);
        e.skip(5 * MIN);
        assertEquals(PomodoroEngine.SHORT_BREAK, e.phase);
        assertEquals(0, e.sessionsToday);
        assertEquals(0, e.doneInCycle);
        assertEquals(5 * MIN, e.focusToday(6 * MIN));         // the five minutes worked still count
    }

    @Test
    public void resetGoesBackToAFreshCycleAndKeepsTheDay() {
        PomodoroEngine e = new PomodoroEngine();
        e.start(0);
        e.advance(25 * MIN);
        e.reset(26 * MIN);
        assertEquals(PomodoroEngine.FOCUS, e.phase);
        assertTrue(e.fresh());
        assertEquals(0, e.doneInCycle);
        assertEquals(1, e.sessionsToday);
    }

    @Test
    public void theDeepFormatChangesTheLengthsOnlyWhenStopped() {
        PomodoroEngine e = new PomodoroEngine();
        e.setDurations(PomodoroEngine.DEEP);
        assertEquals(50 * MIN, e.remaining(0));
        e.start(0);
        e.advance(50 * MIN);
        assertEquals(10 * MIN, e.remaining(50 * MIN));
    }

    @Test
    public void aNewDayStartsTheCountsAgain() {
        PomodoroEngine e = new PomodoroEngine();
        e.start(0);
        e.advance(25 * MIN);
        e.newDay(26 * MIN);
        assertEquals(0, e.sessionsToday);
        assertEquals(0, e.focusToday(27 * MIN));
    }
}
