package com.gathin.tvhome;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * pomodoro on Home: the timer as a glass card. A ring fills around a pharmacy cross as the phase runs; while it runs
 * the cross beats like a pulse (lub-dub, once a second and a bit) and a mint halo breathes behind the card. OK starts
 * or pauses it, holding OK opens the full timer screen. The beat and the halo are view properties, so a frame redraws
 * no pixels of the card; the ring and the text change once a second only while a phase runs and Home is on screen.
 */
final class PomodoroCard extends LinearLayout {
    static final int MINT = 0xFF7EE2B8, GREEN = 0xFF00A86B, AMBER = 0xFFEDBB00;
    private static final int DIM = 0xB3FFFFFF, FAINT = 0x73FFFFFF;

    private final Pomodoro timer;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final float dp;
    private final boolean motion;
    private final TextView phase, time, round, hint;
    private final Ring ring;
    private final View cross;
    /** The breathing light behind the card, sized and placed by the caller (halo()). */
    private final View halo;
    private Animator beat, breath;
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
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setFocusable(true);
        setClickable(true);
        setPadding(px(14), px(14), px(18), px(14));
        setBackground(glass(c, true));
        setContentDescription("Pomodoro");

        // Left: the ring with the cross in its middle.
        FrameLayout dial = new FrameLayout(c);
        ring = new Ring(c);
        dial.addView(ring, new FrameLayout.LayoutParams(-1, -1));
        cross = new Cross(c);
        dial.addView(cross, new FrameLayout.LayoutParams(px(26), px(26), Gravity.CENTER));
        addView(dial, new LayoutParams(px(68), px(68)));

        // Right: name and phase, the time, then the session or the hint.
        LinearLayout text = new LinearLayout(c);
        text.setOrientation(VERTICAL);
        text.setPadding(px(14), 0, 0, 0);
        LinearLayout top = new LinearLayout(c);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(text("POMODORO", 9, MINT, true, 0.3f), new LayoutParams(0, -2, 1));
        phase = text("", 9, 0xFFFFFFFF, true, 0.18f);
        phase.setPadding(px(7), px(3), px(7), px(3));
        top.addView(phase);
        text.addView(top);
        time = text("25:00", 30, 0xFFFFFFFF, false, -0.01f);
        time.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        time.setPadding(0, px(5), 0, px(5));
        text.addView(time);
        round = text("", 10, DIM, false, 0.03f);
        text.addView(round);
        hint = text("", 9, FAINT, false, 0.02f);
        hint.setPadding(0, px(3), 0, 0);
        text.addView(hint);
        addView(text, new LayoutParams(0, -2, 1));

        halo = new View(c);
        GradientDrawable glow = new GradientDrawable();
        glow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        glow.setColors(new int[] {0x5500C46A, 0x2200A86B, 0x00000000});
        glow.setGradientRadius(170 * dp);
        halo.setBackground(glow);
        halo.setAlpha(0f);
        halo.setFocusable(false);
        halo.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);

        setOnClickListener(v -> {
            long now = System.currentTimeMillis();
            if (timer.running()) timer.pause(now);
            else timer.start(now);
            start();
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

    /** The light behind the card: add it to the same parent, just under the card, a little larger. */
    View halo() {
        return halo;
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

    /** Home is on screen, or the timer was started or paused: tick and animate only while it runs. */
    void start() {
        ticking = true;
        refresh();
        handler.removeCallbacks(tick);
        if (timer.running()) {
            handler.post(tick);
            animateAlive(true);
        } else {
            animateAlive(false);
        }
    }

    /** Home is hidden. */
    void stop() {
        ticking = false;
        handler.removeCallbacks(tick);
        animateAlive(false);
    }

    private void animateAlive(boolean alive) {
        if (!alive || !motion) {
            if (beat != null) beat.cancel();
            if (breath != null) breath.cancel();
            beat = breath = null;
            cross.setScaleX(1f);
            cross.setScaleY(1f);
            halo.animate().alpha(alive ? 0.6f : 0f).setDuration(300).start();
            return;
        }
        if (beat != null) return;
        // Lub-dub: two quick swells, then a rest; 1.1 s, about a resting heart.
        ObjectAnimator lub = pulse(1.16f, 110), dub = pulse(1.1f, 110);
        dub.setStartDelay(60);
        AnimatorSet set = new AnimatorSet();
        set.playSequentially(lub, dub);
        set.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator a) {
                if (beat == set) handler.postDelayed(() -> { if (beat == set) set.start(); }, 650);
            }
        });
        beat = set;
        set.start();
        ObjectAnimator b = ObjectAnimator.ofFloat(halo, View.ALPHA, 0.25f, 0.85f);
        b.setDuration(2400);
        b.setRepeatCount(ValueAnimator.INFINITE);
        b.setRepeatMode(ValueAnimator.REVERSE);
        breath = b;
        b.start();
    }

    private ObjectAnimator pulse(float scale, long ms) {
        ObjectAnimator a = ObjectAnimator.ofPropertyValuesHolder(cross,
                PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, scale, 1f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, scale, 1f));
        a.setDuration(ms * 2);
        return a;
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
        round.setText(study ? "Séance " + (timer.doneInCycle() + 1) + " sur " + Pomodoro.SESSIONS_PER_CYCLE
                : timer.doneToday() + (timer.doneToday() == 1 ? " séance" : " séances") + " aujourd'hui");
        ring.set(1f - left / (float) total, study, timer.doneInCycle());
        String ok = timer.running() ? "OK pause" : left < total ? "OK reprendre" : "OK démarrer";
        hint.setText(isFocused() ? ok + " · maintenir : plein écran" : timer.running() ? "● en cours" : "prêt");
        hint.setTextColor(timer.running() && !isFocused() ? MINT : FAINT);
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

    /**
     * The dial: a faint track, the phase's progress as an arc from white to green (amber in a break), and four
     * small ticks round the outside for the cycle's sessions, lit as they are done.
     */
    private static final class Ring extends View {
        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private Shader studyShader, breakShader;
        private float progress;
        private boolean study = true;
        private int done;

        Ring(Context c) {
            super(c);
            float d = c.getResources().getDisplayMetrics().density;
            track.setStyle(Paint.Style.STROKE);
            track.setStrokeWidth(5 * d);
            track.setColor(0x1FFFFFFF);
            arc.setStyle(Paint.Style.STROKE);
            arc.setStrokeWidth(5 * d);
            arc.setStrokeCap(Paint.Cap.ROUND);
        }

        void set(float p, boolean study, int done) {
            p = Math.max(0f, Math.min(1f, p));
            if (p == progress && study == this.study && done == this.done) return;
            progress = p;
            this.study = study;
            this.done = done;
            invalidate();
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            // Built once per size; rotated so the colour runs from the top, clockwise.
            studyShader = rotated(new SweepGradient(w / 2f, h / 2f, new int[] {0xFFF2FFF8, MINT, GREEN, GREEN}, null), w, h);
            breakShader = rotated(new SweepGradient(w / 2f, h / 2f, new int[] {0xFFFFF2C4, AMBER, AMBER}, null), w, h);
        }

        private static Shader rotated(SweepGradient g, int w, int h) {
            android.graphics.Matrix m = new android.graphics.Matrix();
            m.setRotate(-90, w / 2f, h / 2f);
            g.setLocalMatrix(m);
            return g;
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), d = getResources().getDisplayMetrics().density;
            float inset = 9 * d;
            r.set(inset, inset, w - inset, h - inset);
            c.drawOval(r, track);
            if (progress > 0f && studyShader != null) {
                arc.setShader(study ? studyShader : breakShader);
                c.drawArc(r, -90, 360 * progress, false, arc);
            }
            // The cycle's four sessions as ticks on a slightly larger circle: top, right, bottom, left.
            float cx = w / 2f, cy = h / 2f, rad = w / 2f - 2.5f * d;
            for (int i = 0; i < Pomodoro.SESSIONS_PER_CYCLE; i++) {
                double a = Math.toRadians(-90 + i * 90);
                dot.setColor(i < done ? GREEN : 0x40FFFFFF);
                c.drawCircle(cx + (float) Math.cos(a) * rad, cy + (float) Math.sin(a) * rad, 2.2f * d, dot);
            }
        }
    }

    /** The pharmacy cross, green with rounded arms. */
    private static final class Cross extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();

        Cross(Context c) {
            super(c);
            paint.setColor(0xFF00C46A);
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), arm = w * 0.36f, rad = arm * 0.25f;
            r.set((w - arm) / 2f, 0, (w + arm) / 2f, h);
            c.drawRoundRect(r, rad, rad, paint);
            r.set(0, (h - arm) / 2f, w, (h + arm) / 2f);
            c.drawRoundRect(r, rad, rad, paint);
        }
    }
}
