package com.gathin.tvhome;

import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
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
 * pomodoro on Home: the timer as a glass card beside the game_day panel. OK starts or pauses it, holding OK opens
 * the full timer screen. Its text changes once a second only while a phase runs and Home is on screen; otherwise
 * it is redrawn once a minute with the clock.
 */
final class PomodoroCard extends LinearLayout {
    static final int MINT = 0xFF7EE2B8, GREEN = 0xFF00A86B;
    private static final int DIM = 0xB3FFFFFF, FAINT = 0x73FFFFFF;

    private final Pomodoro timer;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final float dp;
    private final TextView phase, time, round, hint;
    private final Bar bar;
    private boolean ticking;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            refresh();
            if (ticking && timer.running()) handler.postDelayed(this, 1000L - System.currentTimeMillis() % 1000L);
        }
    };

    PomodoroCard(Context c, Pomodoro timer) {
        super(c);
        this.timer = timer;
        dp = c.getResources().getDisplayMetrics().density;
        setOrientation(VERTICAL);
        setFocusable(true);
        setClickable(true);
        setPadding(px(18), px(15), px(18), px(15));
        setBackground(glass(c, true));
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        setContentDescription("Pomodoro");

        LinearLayout top = row();
        top.addView(text("POMODORO", 10, MINT, true, 0.28f), new LayoutParams(0, -2, 1));
        phase = text("", 10, 0xFFFFFFFF, true, 0.2f);
        phase.setPadding(px(8), px(3), px(8), px(3));
        top.addView(phase);
        addView(top);

        LinearLayout middle = row();
        middle.setGravity(Gravity.BOTTOM);
        middle.setPadding(0, px(6), 0, 0);
        time = text("25:00", 30, 0xFFFFFFFF, false, -0.01f);
        time.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        middle.addView(time, new LayoutParams(0, -2, 1));
        round = text("", 11, DIM, false, 0.02f);
        round.setPadding(0, 0, 0, px(4));
        middle.addView(round);
        addView(middle);

        bar = new Bar(c);
        LayoutParams bl = new LayoutParams(-1, px(5));
        bl.topMargin = px(8);
        addView(bar, bl);

        hint = text("", 10, FAINT, false, 0.02f);
        LayoutParams hl = new LayoutParams(-2, -2);
        hl.topMargin = px(8);
        addView(hint, hl);

        setOnClickListener(v -> {
            long now = System.currentTimeMillis();
            if (timer.running()) timer.pause(now);
            else timer.start(now);
            refresh();
            startTicking();
        });
        setOnLongClickListener(v -> {
            getContext().startActivity(new Intent(getContext(), PomodoroActivity.class));
            return true;
        });
        setOnFocusChangeListener((v, focused) -> {
            v.animate().scaleX(focused ? 1.03f : 1f).scaleY(focused ? 1.03f : 1f).setDuration(150).start();
            refresh();
        });
        refresh();
    }

    /** The launcher's glass for a Home card: dark, translucent, a hairline rim; a mint rim while focused. */
    static StateListDrawable glass(Context c, boolean focusable) {
        float d = c.getResources().getDisplayMetrics().density;
        StateListDrawable s = new StateListDrawable();
        if (focusable) s.addState(new int[] {android.R.attr.state_focused}, card(d, 0xB30A1E1C, 2, MINT));
        s.addState(new int[] {}, card(d, 0x8C080E1A, 1, 0x29FFFFFF));
        return s;
    }

    private static GradientDrawable card(float d, int fill, int stroke, int strokeColour) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(18 * d);
        g.setStroke(Math.round(stroke * d), strokeColour);
        return g;
    }

    /** Home is on screen. */
    void start() {
        refresh();
        startTicking();
    }

    private void startTicking() {
        ticking = true;
        handler.removeCallbacks(tick);
        if (timer.running()) handler.post(tick);
    }

    /** Home is hidden. */
    void stop() {
        ticking = false;
        handler.removeCallbacks(tick);
    }

    void refresh() {
        long now = System.currentTimeMillis();
        timer.catchUp(now);
        int p = timer.phase();
        long left = timer.remaining(now), total = Pomodoro.length(p);
        boolean study = p == Pomodoro.STUDY;
        phase.setText(study ? "ÉTUDE" : p == Pomodoro.SHORT_BREAK ? "PAUSE" : "GRANDE PAUSE");
        GradientDrawable chip = new GradientDrawable();
        chip.setCornerRadius(px(9));
        chip.setColor(study ? 0x3300A86B : 0x33EDBB00);
        phase.setBackground(chip);
        time.setText(Pomodoro.clock(left));
        round.setText(study ? "Séance " + (timer.doneInCycle() + 1) + "/" + Pomodoro.SESSIONS_PER_CYCLE
                : timer.doneToday() + " aujourd'hui");
        bar.set(1f - left / (float) total, study);
        String ok = timer.running() ? "OK : pause" : left < total ? "OK : reprendre" : "OK : démarrer";
        hint.setText(isFocused() ? ok + "  ·  maintenir : plein écran" : timer.running() ? "En cours" : "Prêt");
    }

    private LinearLayout row() {
        LinearLayout l = new LinearLayout(getContext());
        l.setOrientation(HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    private TextView text(String s, float sp, int colour, boolean bold, float spacing) {
        TextView t = new TextView(getContext());
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(colour);
        t.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        if (spacing != 0) t.setLetterSpacing(spacing);
        t.setIncludeFontPadding(false);
        t.setSingleLine(true);
        return t;
    }

    private int px(float v) {
        return Math.round(v * dp);
    }

    /** A thin capsule track, filled white then green across the seam, as the timer screen's gélule. */
    private static final class Bar extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private float progress;
        private boolean study = true;

        Bar(Context c) {
            super(c);
        }

        void set(float p, boolean study) {
            p = Math.max(0f, Math.min(1f, p));
            if (p == progress && study == this.study) return;
            progress = p;
            this.study = study;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), rad = h / 2f;
            paint.setColor(0x26FFFFFF);
            r.set(0, 0, w, h);
            c.drawRoundRect(r, rad, rad, paint);
            if (progress <= 0f) return;
            float end = Math.max(h, w * progress);
            paint.setColor(study ? 0xFFF2FFF8 : MINT);
            r.set(0, 0, Math.min(end, w / 2f + rad), h);
            c.drawRoundRect(r, rad, rad, paint);
            if (end > w / 2f) {
                paint.setColor(study ? GREEN : 0xFF3CCB95);
                r.set(w / 2f, 0, end, h);
                c.drawRoundRect(r, rad, rad, paint);
                c.drawRect(w / 2f, 0, Math.min(w / 2f + rad, end), h, paint);
            }
        }
    }
}
