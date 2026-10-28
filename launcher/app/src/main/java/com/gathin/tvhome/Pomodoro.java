package com.gathin.tvhome;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import java.time.LocalDate;

/**
 * pomodoro (config.xml or a theme): a study timer, 25 minutes of work, then 5 of rest, and 15 after every fourth
 * session. The state lives in preferences so Home's pill and the timer screen agree, and the end of each phase is
 * an alarm that brings the timer screen up over whatever is playing. A break starts by itself; the next session
 * waits for OK, so a TV left alone does not cycle all night.
 */
final class Pomodoro {
    static final int STUDY = 0, SHORT_BREAK = 1, LONG_BREAK = 2;
    static final int SESSIONS_PER_CYCLE = 4;
    static final String EXTRA_TIME_UP = "com.gathin.tvhome.POMODORO_TIME_UP";
    private static final long MINUTE = 60_000L;

    private final Context ctx;
    private final SharedPreferences prefs;

    Pomodoro(Context ctx) {
        this.ctx = ctx.getApplicationContext();
        prefs = this.ctx.getSharedPreferences("pomodoro", Context.MODE_PRIVATE);
    }

    static long length(int phase) {
        return (phase == STUDY ? 25 : phase == SHORT_BREAK ? 5 : 15) * MINUTE;
    }

    int phase() {
        return prefs.getInt("phase", STUDY);
    }

    boolean running() {
        return prefs.getBoolean("running", false);
    }

    /** Sessions finished in the current cycle of four, 0 to 3. */
    int doneInCycle() {
        return prefs.getInt("cycle", 0);
    }

    /** Sessions finished today. */
    int doneToday() {
        return LocalDate.now().toString().equals(prefs.getString("day", "")) ? prefs.getInt("today", 0) : 0;
    }

    long remaining(long now) {
        long left = running() ? prefs.getLong("end", now) - now : prefs.getLong("left", length(phase()));
        return Math.max(0, left);
    }

    /** True while a session or a break is under way or paused part-way, so Home shows the pill's time. */
    boolean active() {
        return running() || prefs.getLong("left", length(phase())) < length(phase());
    }

    void start(long now) {
        if (running()) return;
        long end = now + remaining(now);
        prefs.edit().putBoolean("running", true).putLong("end", end).apply();
        schedule(end);
    }

    void pause(long now) {
        if (!running()) return;
        prefs.edit().putBoolean("running", false).putLong("left", remaining(now)).apply();
        cancel();
    }

    void reset() {
        cancel();
        prefs.edit().putInt("phase", STUDY).putBoolean("running", false).putLong("left", length(STUDY))
                .putInt("cycle", 0).apply();
    }

    /** Ends the phase on screen now (the alarm, or Skip): a finished session counts, and the next phase is set. */
    void finish(long now, boolean counted) {
        cancel();
        int phase = phase();
        SharedPreferences.Editor e = prefs.edit();
        if (phase == STUDY) {
            int cycle = doneInCycle() + (counted ? 1 : 0);
            if (counted) {
                e.putString("day", LocalDate.now().toString()).putInt("today", doneToday() + 1);
            }
            int next = cycle >= SESSIONS_PER_CYCLE ? LONG_BREAK : SHORT_BREAK;
            e.putInt("cycle", cycle % SESSIONS_PER_CYCLE).putInt("phase", next);
            // The break starts by itself.
            long end = now + length(next);
            e.putBoolean("running", true).putLong("end", end).putLong("left", length(next)).apply();
            schedule(end);
        } else {
            e.putInt("phase", STUDY).putBoolean("running", false).putLong("left", length(STUDY)).apply();
        }
    }

    /** The alarm went off, or the screen opened after it should have: finish the phase once. */
    boolean catchUp(long now) {
        if (running() && prefs.getLong("end", Long.MAX_VALUE) <= now) {
            finish(now, true);
            return true;
        }
        return false;
    }

    private PendingIntent alarmIntent() {
        Intent i = new Intent(ctx, PomodoroActivity.class).putExtra(EXTRA_TIME_UP, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(ctx, 7, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private void schedule(long at) {
        AlarmManager am = ctx.getSystemService(AlarmManager.class);
        if (am == null) return;
        // Exact where the system allows it without asking (every TV before Android 12, and 12+ once granted).
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, alarmIntent());
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, alarmIntent());
        }
    }

    private void cancel() {
        AlarmManager am = ctx.getSystemService(AlarmManager.class);
        if (am != null) am.cancel(alarmIntent());
    }

    /** "18:42". */
    static String clock(long ms) {
        long s = (ms + 999) / 1000;
        return String.format(java.util.Locale.ROOT, "%02d:%02d", s / 60, s % 60);
    }
}
