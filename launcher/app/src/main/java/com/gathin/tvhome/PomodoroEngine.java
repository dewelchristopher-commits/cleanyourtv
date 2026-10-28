package com.gathin.tvhome;

/**
 * The pomodoro's rules, with no Android in them so they can be tested on the JVM (PomodoroEngineTest). Every time
 * is a reading of one monotonic clock that the caller passes in (Pomodoro uses elapsedRealtime, which keeps counting
 * through standby and never jumps when the TV sets its time), so the countdown is an end time, not a sum of ticks:
 * a frame that never drew, a paused process or a sleeping TV cannot make it drift.
 *
 * A focus session that ends counts and starts its break by itself; a break that ends leaves the next session
 * waiting for OK, so a TV left alone does not cycle all night.
 */
final class PomodoroEngine {
    static final int FOCUS = 0, SHORT_BREAK = 1, LONG_BREAK = 2;
    private static final long MINUTE = 60_000L;

    /** The lengths of one format, and how many sessions make a cycle before the long break. */
    static final class Durations {
        final long focus, shortBreak, longBreak;
        final int cycle;

        Durations(long focusMinutes, long shortMinutes, long longMinutes, int cycle) {
            this.focus = focusMinutes * MINUTE;
            this.shortBreak = shortMinutes * MINUTE;
            this.longBreak = longMinutes * MINUTE;
            this.cycle = cycle;
        }
    }

    /** 25 minutes of focus, then 5 of rest, and 15 after the fourth. */
    static final Durations CLASSIC = new Durations(25, 5, 15, 4);
    /** 50 minutes of focus, then 10 of rest, and 30 after the fourth. */
    static final Durations DEEP = new Durations(50, 10, 30, 4);

    Durations durations = CLASSIC;
    int phase = FOCUS;
    boolean running;
    /** While running: when the phase ends. */
    long endAt;
    /** While not running: what is left of the phase. */
    long left = CLASSIC.focus;
    /** While running: since when, for the day's focus time. */
    long runFrom;
    /** Focus sessions finished in the current cycle, 0 to cycle - 1. */
    int doneInCycle;
    int sessionsToday;
    /** Focus time counted today, the run under way not included (focusToday(now) adds it). */
    long focusToday;

    long length(int p) {
        return p == FOCUS ? durations.focus : p == SHORT_BREAK ? durations.shortBreak : durations.longBreak;
    }

    long remaining(long now) {
        return Math.max(0, running ? endAt - now : left);
    }

    /** 0 at the start of the phase, 1 at its end. */
    float progress(long now) {
        long total = length(phase);
        return total <= 0 ? 0f : Math.max(0f, Math.min(1f, 1f - remaining(now) / (float) total));
    }

    /** Not started, or reset: the whole phase is still ahead. */
    boolean fresh() {
        return !running && left >= length(phase);
    }

    long focusToday(long now) {
        return focusToday + (running && phase == FOCUS ? Math.max(0, Math.min(now, endAt) - runFrom) : 0);
    }

    void start(long now) {
        if (running) return;
        if (left <= 0) left = length(phase);
        running = true;
        endAt = now + left;
        runFrom = now;
    }

    void pause(long now) {
        if (!running) return;
        credit(now);
        left = Math.max(0, endAt - now);
        running = false;
    }

    /** Back to a fresh focus session and a fresh cycle; the day's counts stay. */
    void reset(long now) {
        if (running) credit(now);
        running = false;
        phase = FOCUS;
        left = length(FOCUS);
        doneInCycle = 0;
    }

    /** Ends the phase now without counting it: a skipped session leads to a short break, a skipped break to focus. */
    void skip(long now) {
        finish(now, false);
    }

    /**
     * Finishes every phase whose end has passed, at the moment it ended rather than now: after a long standby a break
     * that also ran out is finished too. True when the phase changed, for the chime and the screen.
     */
    boolean advance(long now) {
        boolean changed = false;
        while (running && endAt <= now) {
            finish(endAt, true);
            changed = true;
        }
        return changed;
    }

    /** Takes a new format. A phase under way keeps going; one not running starts over with the new length. */
    void setDurations(Durations d) {
        durations = d;
        if (!running) left = length(phase);
        if (doneInCycle >= d.cycle) doneInCycle = 0;
    }

    /** A new day: the day's counts start again. A focus run under way counts from now. */
    void newDay(long now) {
        sessionsToday = 0;
        focusToday = 0;
        if (running) runFrom = now;
    }

    private void finish(long at, boolean counted) {
        if (phase == FOCUS) {
            if (running) credit(at);
            int next = SHORT_BREAK;
            if (counted) {
                sessionsToday++;
                doneInCycle++;
                if (doneInCycle >= durations.cycle) {
                    next = LONG_BREAK;
                    doneInCycle = 0;
                }
            }
            phase = next;
            running = true;
            runFrom = at;
            endAt = at + length(next);
            left = length(next);
        } else {
            phase = FOCUS;
            running = false;
            left = length(FOCUS);
        }
    }

    /** Adds the focus run up to {@code at} to the day's time, and starts the next stretch there. */
    private void credit(long at) {
        if (phase == FOCUS) focusToday += Math.max(0, Math.min(at, endAt) - runFrom);
        runFrom = at;
    }
}
