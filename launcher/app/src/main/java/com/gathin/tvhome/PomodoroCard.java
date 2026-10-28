package com.gathin.tvhome;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * pomodoro on Home: a compact card with the dial (time left inside its ring) and three lines beside it, the phase,
 * the cycle and the day, the smallest at 13sp so they read from the sofa. OK starts or pauses it; holding OK opens
 * the timer screen. It redraws once a second only while a phase runs and Home is on screen.
 */
final class PomodoroCard extends LinearLayout {
    private static final int WHITE = 0xFFFFFFFF, SOFT = 0xCCFFFFFF, DIM = 0x8CFFFFFF;

    private final Pomodoro timer;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final float dp;
    private final boolean motion;
    private final PomodoroDial dial;
    private final View dot;
    private final TextView phase, cycle, day;
    private int shownPhase = -1;
    private boolean ticking;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            refresh();
            if (ticking && timer.running()) handler.postDelayed(this, 1000L - System.currentTimeMillis() % 1000L);
        }
    };

    PomodoroCard(Context c, Pomodoro timer, boolean motion) {
        super(c);
        this.timer = timer;
        this.motion = motion;
        dp = c.getResources().getDisplayMetrics().density;
        Typeface light = UiFont.get(c, R.string.font_light), medium = UiFont.get(c, R.string.font_medium);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setFocusable(true);
        setClickable(true);
        setPadding(px(12), px(12), px(20), px(12));
        setBackground(glass(c, true));
        setContentDescription("Pomodoro");

        dial = new PomodoroDial(c, 3.5f, 17, false);
        dial.setTypefaces(medium, medium);
        addView(dial, new LayoutParams(px(84), px(84)));

        LinearLayout text = new LinearLayout(c);
        text.setOrientation(VERTICAL);
        text.setPadding(px(16), 0, 0, 0);
        LinearLayout head = new LinearLayout(c);
        head.setGravity(Gravity.CENTER_VERTICAL);
        dot = new View(c);
        head.addView(dot, new LayoutParams(px(7), px(7)));
        phase = text(16, WHITE, medium);
        phase.setPadding(px(8), 0, 0, 0);
        head.addView(phase);
        text.addView(head);
        cycle = text(13, SOFT, medium);
        cycle.setPadding(0, px(7), 0, 0);
        text.addView(cycle);
        day = text(13, DIM, medium);
        day.setPadding(0, px(4), 0, 0);
        text.addView(day);
        addView(text, new LayoutParams(0, -2, 1));

        setOnClickListener(v -> {
            timer.toggle();
            start();
        });
        setOnLongClickListener(v -> {
            getContext().startActivity(new Intent(getContext(), PomodoroActivity.class));
            return true;
        });
        setOnFocusChangeListener((v, focused) -> {
            v.animate().scaleX(focused ? 1.04f : 1f).scaleY(focused ? 1.04f : 1f).setDuration(160).start();
            refresh();
        });
        refresh();
    }

    /**
     * The launcher's surface for a Home card: deep navy, a faint diagonal light, a hairline edge; focused, a clear
     * white edge (the same signal as the tiles' rim). 16dp corners, no drop shadow.
     */
    static StateListDrawable glass(Context c, boolean focusable) {
        float d = c.getResources().getDisplayMetrics().density;
        StateListDrawable s = new StateListDrawable();
        if (focusable) s.addState(new int[] {android.R.attr.state_focused}, surface(d, 2, 0xF2FFFFFF));
        s.addState(new int[] {}, surface(d, 1, 0x24FFFFFF));
        return s;
    }

    private static GradientDrawable surface(float d, int stroke, int edge) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[] {0xE6142048, 0xEB0A1230});
        g.setCornerRadius(16 * d);
        g.setStroke(Math.round(stroke * d), edge);
        return g;
    }

    /** Home is on screen, or the timer was started or paused. */
    void start() {
        ticking = true;
        handler.removeCallbacks(tick);
        if (timer.running()) handler.post(tick);
        else refresh();
    }

    /** Home is hidden. */
    void stop() {
        ticking = false;
        handler.removeCallbacks(tick);
    }

    void refresh() {
        timer.catchUp();
        int p = timer.phase();
        boolean onBreak = p != Pomodoro.FOCUS, running = timer.running();
        dial.setBreak(onBreak, motion && shownPhase >= 0 && shownPhase != p);
        shownPhase = p;
        dial.set(timer.progress(), Pomodoro.clock(timer.remaining()), "", "", !running && !timer.fresh());

        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(onBreak ? 0xFFE8B100 : running ? 0xFF2F7BEA : 0x66FFFFFF);
        dot.setBackground(d);
        phase.setText(!running && !timer.fresh() ? "En pause" : Pomodoro.phaseName(p));
        cycle.setText(isFocused() ? (running ? "OK  Pause" : timer.fresh() ? "OK  Démarrer" : "OK  Reprendre")
                : session(timer));
        day.setText(isFocused() ? "Maintenir OK  Plein écran"
                : timer.sessionsToday() + (timer.sessionsToday() == 1 ? " session · " : " sessions · ")
                + Pomodoro.duration(timer.focusToday()));
    }

    /** "Séance 2 sur 4": the session under way, or during a break the one just finished. */
    static String session(Pomodoro t) {
        int n = t.phase() == Pomodoro.FOCUS ? t.doneInCycle() + 1 : t.phase() == Pomodoro.LONG_BREAK ? t.cycle() : t.doneInCycle();
        return "Séance " + n + " sur " + t.cycle();
    }

    private TextView text(float sp, int colour, Typeface face) {
        TextView t = new TextView(getContext());
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(colour);
        t.setTypeface(face);
        t.setIncludeFontPadding(false);
        t.setSingleLine(true);
        return t;
    }

    private int px(float v) {
        return Math.round(v * dp);
    }
}
