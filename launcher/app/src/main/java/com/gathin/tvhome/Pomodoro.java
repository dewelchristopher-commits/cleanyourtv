package com.gathin.tvhome;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.SystemClock;
import android.provider.Settings;

import java.time.LocalDate;

/**
 * pomodoro (config.xml or a theme): the study timer that Home's card and the timer screen share, one per process.
 * PomodoroEngine holds the rules; this keeps them on the monotonic clock, saves them after every change, and sets an
 * alarm for the end of each phase that brings the timer screen up over whatever is playing.
 *
 * elapsedRealtime restarts at a reboot, so the end is also saved as a wall-clock time with the boot count: after a
 * reboot a phase under way is carried over by the wall clock, the one reference that survived.
 */
final class Pomodoro {
    static final int FOCUS = PomodoroEngine.FOCUS, SHORT_BREAK = PomodoroEngine.SHORT_BREAK,
            LONG_BREAK = PomodoroEngine.LONG_BREAK;
    static final String EXTRA_TIME_UP = "com.gathin.tvhome.POMODORO_TIME_UP";

    private static Pomodoro instance;

    private final Context ctx;
    private final SharedPreferences prefs;
    private final PomodoroEngine e = new PomodoroEngine();
    private String day;

    static synchronized Pomodoro get(Context c) {
        if (instance == null) instance = new Pomodoro(c.getApplicationContext());
        return instance;
    }

    private Pomodoro(Context ctx) {
        this.ctx = ctx;
        prefs = ctx.getSharedPreferences("pomodoro2", Context.MODE_PRIVATE);
        load();
    }

    static long now() {
        return SystemClock.elapsedRealtime();
    }

    // ---- reading ---------------------------------------------------------------------------------

    int phase() { return e.phase; }
    boolean running() { return e.running; }
    boolean fresh() { return e.fresh(); }
    boolean deep() { return e.durations == PomodoroEngine.DEEP; }
    long length(int phase) { return e.length(phase); }
    long remaining() { return e.remaining(now()); }
    float progress() { return e.progress(now()); }
    int doneInCycle() { return e.doneInCycle; }
    int cycle() { return e.durations.cycle; }

    int sessionsToday() {
        rollDay();
        return e.sessionsToday;
    }

    long focusToday() {
        rollDay();
        return e.focusToday(now());
    }

    /** The wall-clock time the phase under way ends, for "Fin à 21:45". */
    long endsAtWall() {
        return System.currentTimeMillis() + e.remaining(now());
    }

    // ---- changing --------------------------------------------------------------------------------

    void start() { e.start(now()); changed(); }
    void pause() { e.pause(now()); changed(); }
    void reset() { e.reset(now()); changed(); }
    void skip() { e.skip(now()); changed(); }

    void toggle() {
        if (e.running) pause();
        else start();
    }

    /** 50-minute sessions instead of 25; only while nothing runs. */
    void setDeep(boolean deep) {
        if (e.running) return;
        e.setDurations(deep ? PomodoroEngine.DEEP : PomodoroEngine.CLASSIC);
        changed();
    }

    /** Finishes the phases that ran out (the alarm, or a screen opened late). True when the phase changed. */
    boolean catchUp() {
        rollDay();
        if (!e.advance(now())) return false;
        changed();
        return true;
    }

    private void rollDay() {
        String today = LocalDate.now().toString();
        if (!today.equals(day)) {
            if (day != null) e.newDay(now());
            day = today;
            save();
        }
    }

    private void changed() {
        save();
        schedule();
    }

    // ---- saving ----------------------------------------------------------------------------------

    private int bootCount() {
        return Settings.Global.getInt(ctx.getContentResolver(), Settings.Global.BOOT_COUNT, -1);
    }

    private void save() {
        long now = now();
        prefs.edit()
                .putBoolean("deep", deep())
                .putInt("phase", e.phase)
                .putBoolean("running", e.running)
                .putLong("end", e.endAt)
                .putLong("end_wall", System.currentTimeMillis() + (e.endAt - now))
                .putLong("left", e.left)
                .putLong("run_from", e.runFrom)
                .putInt("cycle_done", e.doneInCycle)
                .putInt("sessions", e.sessionsToday)
                .putLong("focus", e.focusToday)
                .putString("day", day)
                .putInt("boot", bootCount())
                .apply();
    }

    private void load() {
        e.setDurations(prefs.getBoolean("deep", false) ? PomodoroEngine.DEEP : PomodoroEngine.CLASSIC);
        e.phase = prefs.getInt("phase", FOCUS);
        e.running = prefs.getBoolean("running", false);
        e.left = prefs.getLong("left", e.length(e.phase));
        e.endAt = prefs.getLong("end", 0);
        e.runFrom = prefs.getLong("run_from", 0);
        e.doneInCycle = prefs.getInt("cycle_done", 0);
        e.sessionsToday = prefs.getInt("sessions", 0);
        e.focusToday = prefs.getLong("focus", 0);
        day = prefs.getString("day", null);
        if (e.running && prefs.getInt("boot", -1) != bootCount()) {
            // A reboot reset the monotonic clock: place the end again by the wall clock.
            long now = now();
            e.endAt = now + Math.max(0, prefs.getLong("end_wall", 0) - System.currentTimeMillis());
            e.runFrom = now;
        }
        rollDay();
        // After a reboot the alarm is gone with the rest; set it again for the phase that carried over.
        if (!catchUp()) schedule();
    }

    // ---- the end-of-phase alarm ------------------------------------------------------------------

    private PendingIntent alarmIntent() {
        Intent i = new Intent(ctx, PomodoroActivity.class).putExtra(EXTRA_TIME_UP, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(ctx, 7, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /**
     * One alarm for the end of the phase under way, on the same monotonic clock, and not a wake-up one: a TV in
     * standby is left asleep, and the screen catches up when it is switched on.
     */
    private void schedule() {
        AlarmManager am = ctx.getSystemService(AlarmManager.class);
        if (am == null) return;
        am.cancel(alarmIntent());
        if (!e.running) return;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME, e.endAt, alarmIntent());
        } else {
            am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME, e.endAt, alarmIntent());
        }
    }

    // ---- words -----------------------------------------------------------------------------------

    /** "18:42". */
    static String clock(long ms) {
        long s = (ms + 999) / 1000;
        return String.format(java.util.Locale.ROOT, "%02d:%02d", s / 60, s % 60);
    }

    /** "1 h 15 min", "40 min", "0 min". */
    static String duration(long ms) {
        long m = ms / 60_000L;
        return m >= 60 ? (m / 60) + " h " + String.format(java.util.Locale.ROOT, "%02d", m % 60) : m + " min";
    }

    static String phaseName(int phase) {
        return phase == FOCUS ? "Concentration" : phase == SHORT_BREAK ? "Pause courte" : "Pause longue";
    }
}
