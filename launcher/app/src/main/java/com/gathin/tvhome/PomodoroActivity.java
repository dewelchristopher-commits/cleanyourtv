package com.gathin.tvhome;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The pomodoro timer screen, in a pharmacy look: a green cross, the time in large type and a capsule that fills as
 * the phase runs. Plain views and one small custom view redrawn once a second, so the TV's GPU idles in between.
 * The words are French only: this screen is for the owner of the barca theme (see Pomodoro for the timer itself).
 */
public final class PomodoroActivity extends Activity {
    private static final int GREEN = 0xFF00A86B, MINT = 0xFF7EE2B8, CROSS = 0xFF00C46A;
    private static final int TEXT = 0xFFFFFFFF, DIM = 0xB3FFFFFF, FAINT = 0x66FFFFFF;
    private static final String[] STUDY_TIPS = {
            "Une seule matière à la fois. Téléphone hors de portée.",
            "Reformule le cours à voix haute, comme si tu l'expliquais.",
            "Fiches : nom, classe, mécanisme, indications, effets indésirables.",
            "Interactions : note le mécanisme, pas seulement la paire.",
    };
    private static final String[] BREAK_TIPS = {
            "Lève-toi, étire-toi et bois un verre d'eau.",
            "Regarde au loin quelques instants : tes yeux te remercieront.",
            "Pas d'écran pendant la pause, si possible.",
    };

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Pomodoro timer;
    private float dp;
    private TextView phaseLabel, time, tip, sessions, startButton;
    private Capsule capsule;
    private LinearLayout dots;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (timer.catchUp(System.currentTimeMillis())) chime();
            refresh();
            handler.postDelayed(this, 1000L - System.currentTimeMillis() % 1000L);
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        dp = getResources().getDisplayMetrics().density;
        timer = new Pomodoro(this);
        setContentView(build());
        handle(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handle(intent);
    }

    /** Opened by the end-of-phase alarm: move on and sound once. */
    private void handle(Intent intent) {
        if (intent != null && intent.getBooleanExtra(Pomodoro.EXTRA_TIME_UP, false)) {
            intent.removeExtra(Pomodoro.EXTRA_TIME_UP);
            if (timer.catchUp(System.currentTimeMillis())) chime();
        }
        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(tick);
        startButton.requestFocus();
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(tick);
    }

    private void chime() {
        try {
            Ringtone r = RingtoneManager.getRingtone(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));
            if (r != null) r.play();
        } catch (RuntimeException ignored) {
            // No sound on this TV: the screen coming up is the signal.
        }
    }

    private View build() {
        FrameLayout root = new FrameLayout(this);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[] {0xFF03211C, 0xFF06352E, 0xFF0A2A3A}));

        LinearLayout page = vertical();
        page.setGravity(Gravity.CENTER_HORIZONTAL);
        page.setPadding(px(56), px(36), px(56), px(30));
        root.addView(page, new FrameLayout.LayoutParams(-1, -1));

        // Header: the pharmacy cross, the name, and today's count on the right.
        LinearLayout header = horizontal();
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(new Cross(this), new LinearLayout.LayoutParams(px(34), px(34)));
        LinearLayout titles = vertical();
        titles.setPadding(px(14), 0, 0, 0);
        titles.addView(text("POMODORO", 11, MINT, true, 0.3f));
        titles.addView(text("Révisions de pharmacie", 20, TEXT, true, 0));
        header.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));
        sessions = text("", 13, DIM, false, 0.02f);
        header.addView(sessions);
        page.addView(header, new LinearLayout.LayoutParams(-1, -2));

        // Centre: the phase, the time, the capsule and the cycle's four dots.
        LinearLayout centre = vertical();
        centre.setGravity(Gravity.CENTER);
        phaseLabel = text("", 14, MINT, true, 0.25f);
        phaseLabel.setGravity(Gravity.CENTER);
        centre.addView(phaseLabel);
        time = text("25:00", 104, TEXT, false, -0.02f);
        time.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        time.setGravity(Gravity.CENTER);
        centre.addView(time, lp(-2, -2, 0, px(6), 0, px(16)));
        capsule = new Capsule(this);
        centre.addView(capsule, new LinearLayout.LayoutParams(px(440), px(54)));
        dots = horizontal();
        dots.setGravity(Gravity.CENTER);
        centre.addView(dots, lp(-2, -2, 0, px(18), 0, 0));
        tip = text("", 14, DIM, false, 0);
        tip.setGravity(Gravity.CENTER);
        centre.addView(tip, lp(-2, -2, 0, px(14), 0, 0));
        page.addView(centre, new LinearLayout.LayoutParams(-1, 0, 1));

        // Buttons: the remote's OK on the focused one; Back returns Home with the timer still running.
        LinearLayout buttons = horizontal();
        buttons.setGravity(Gravity.CENTER);
        startButton = button("Démarrer", v -> {
            long now = System.currentTimeMillis();
            if (timer.running()) timer.pause(now);
            else timer.start(now);
            refresh();
        });
        buttons.addView(startButton, lp(px(180), px(46), 0, 0, px(14), 0));
        buttons.addView(button("Passer", v -> {
            timer.finish(System.currentTimeMillis(), false);
            refresh();
        }), lp(px(140), px(46), 0, 0, px(14), 0));
        buttons.addView(button("Réinitialiser", v -> {
            timer.reset();
            refresh();
        }), lp(px(160), px(46), 0, 0, 0, 0));
        page.addView(buttons, new LinearLayout.LayoutParams(-1, -2));
        return root;
    }

    private void refresh() {
        long now = System.currentTimeMillis();
        int phase = timer.phase();
        long left = timer.remaining(now);
        long total = Pomodoro.length(phase);
        phaseLabel.setText(phase == Pomodoro.STUDY ? "ÉTUDE" : phase == Pomodoro.SHORT_BREAK ? "PETITE PAUSE" : "GRANDE PAUSE");
        time.setText(Pomodoro.clock(left));
        capsule.setProgress(1f - left / (float) total, phase == Pomodoro.STUDY);
        startButton.setText(timer.running() ? "Pause" : left < total ? "Reprendre" : "Démarrer");
        String[] tips = phase == Pomodoro.STUDY ? STUDY_TIPS : BREAK_TIPS;
        tip.setText(tips[(int) ((now / (10 * 60_000L)) % tips.length)]);
        int today = timer.doneToday();
        sessions.setText(today == 0 ? "Aucune séance aujourd'hui" : today + (today == 1 ? " séance" : " séances") + " aujourd'hui");
        int done = timer.doneInCycle();
        if (dots.getChildCount() != Pomodoro.SESSIONS_PER_CYCLE) {
            dots.removeAllViews();
            for (int i = 0; i < Pomodoro.SESSIONS_PER_CYCLE; i++) dots.addView(new View(this), lp(px(22), px(10), px(5), 0, px(5), 0));
        }
        for (int i = 0; i < Pomodoro.SESSIONS_PER_CYCLE; i++) {
            boolean current = phase == Pomodoro.STUDY && i == done;
            dots.getChildAt(i).setBackground(rounded(i < done ? GREEN : current ? 0x99FFFFFF : 0x33FFFFFF, px(5)));
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Play/pause on the remote toggles the timer from anywhere on the screen.
        if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
            startButton.performClick();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private TextView button(String label, View.OnClickListener click) {
        TextView b = text(label, 15, TEXT, true, 0.02f);
        b.setGravity(Gravity.CENTER);
        b.setFocusable(true);
        b.setClickable(true);
        b.setOnClickListener(click);
        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[] {android.R.attr.state_focused}, rounded(GREEN, px(23)));
        bg.addState(new int[] {}, rounded(0x1FFFFFFF, px(23)));
        b.setBackground(bg);
        b.setOnFocusChangeListener((v, focused) -> v.animate().scaleX(focused ? 1.06f : 1f).scaleY(focused ? 1.06f : 1f)
                .setDuration(140).start());
        return b;
    }

    private TextView text(String s, float sp, int colour, boolean bold, float spacing) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(colour);
        t.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        if (spacing != 0) t.setLetterSpacing(spacing);
        t.setIncludeFontPadding(false);
        return t;
    }

    private GradientDrawable rounded(int colour, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(colour);
        d.setCornerRadius(radius);
        return d;
    }

    private LinearLayout vertical() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private LinearLayout horizontal() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        return l;
    }

    private LinearLayout.LayoutParams lp(int w, int h, int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(left, top, right, bottom);
        return p;
    }

    private int px(float v) {
        return Math.round(v * dp);
    }

    /** A gélule: two halves, the left one white and the right one green, filled left to right as the phase runs. */
    private static final class Capsule extends View {
        private final Paint shell = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint seam = new Paint();
        private final RectF r = new RectF();
        private Shader studyShader, breakShader;
        private float progress;
        private boolean study = true;

        Capsule(Context c) {
            super(c);
            shell.setColor(0x26FFFFFF);
            seam.setColor(0x4D000000);
        }

        void setProgress(float p, boolean study) {
            p = Math.max(0f, Math.min(1f, p));
            if (p == progress && study == this.study) return;
            progress = p;
            this.study = study;
            invalidate();
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            // Built once per size, not per frame: a hard stop at the seam makes the two halves.
            float[] stops = {0f, 0.5f, 0.5f, 1f};
            studyShader = new LinearGradient(0, 0, w, 0, new int[] {0xFFF2FFF8, 0xFFF2FFF8, GREEN, GREEN}, stops, Shader.TileMode.CLAMP);
            breakShader = new LinearGradient(0, 0, w, 0, new int[] {MINT, MINT, 0xFF3CCB95, 0xFF3CCB95}, stops, Shader.TileMode.CLAMP);
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), rad = h / 2f, inset = h * 0.12f, ir = rad - inset;
            r.set(0, 0, w, h);
            c.drawRoundRect(r, rad, rad, shell);
            if (progress > 0f && studyShader != null) {
                fill.setShader(study ? studyShader : breakShader);
                r.set(inset, inset, Math.max(inset + 2 * ir, inset + (w - 2 * inset) * progress), h - inset);
                c.drawRoundRect(r, ir, ir, fill);
            }
            float s = getResources().getDisplayMetrics().density;
            c.drawRect(w / 2f - s, 0, w / 2f + s, h, seam);
        }
    }

    /** The pharmacy cross: a green plus with rounded arms and a soft halo. */
    private static final class Cross extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();

        Cross(Context c) {
            super(c);
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), arm = w * 0.34f, rad = arm * 0.22f;
            paint.setColor(0x3300C46A);
            c.drawCircle(w / 2f, h / 2f, w / 2f, paint);
            paint.setColor(CROSS);
            r.set((w - arm) / 2f, h * 0.12f, (w + arm) / 2f, h * 0.88f);
            c.drawRoundRect(r, rad, rad, paint);
            r.set(w * 0.12f, (h - arm) / 2f, w * 0.88f, (h + arm) / 2f);
            c.drawRoundRect(r, rad, rad, paint);
        }
    }
}
