package com.gathin.tvhome;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
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

import java.text.DateFormat;
import java.util.Date;

/**
 * The pomodoro's own screen. The dial on the left carries the phase and the one number that matters, the time
 * left; the column on the right reads top to bottom as the session, what comes next, the cycle, the day, then the
 * controls and the format. Deep navy
 * with one soft light in the phase's colours; garnet and blue while focusing, gold during a break, blended over
 * 700 ms when the phase changes. Everything is reached with the arrows and OK, and the focused control is the only
 * white thing on the screen.
 *
 * Built from plain views; the dial redraws once a second, the rest only when something changes. The words are
 * French only: this screen belongs to the barca theme's owner.
 */
public final class PomodoroActivity extends Activity {
    private static final int NAVY = 0xFF0A1230, WHITE = 0xFFFFFFFF, SOFT = 0xB3FFFFFF, DIM = 0x8CFFFFFF;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Pomodoro timer;
    private float dp;
    private boolean motion;
    private Typeface light, medium;
    private PomodoroDial dial;
    private View focusLight, breakLight;
    private TextView title, session, sessions, focusTime, hint, primary, reset, skip, classic, deep, formatNote;
    private View[] segments;
    private int shownPhase = -1;
    /** A short message in place of the session line after a phase ends, until this time. */
    private long noteUntil;
    private String note = "";

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (timer.catchUp()) celebrate();
            refresh();
            handler.postDelayed(this, 1000L - System.currentTimeMillis() % 1000L);
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        dp = getResources().getDisplayMetrics().density;
        timer = Pomodoro.get(this);
        light = UiFont.get(this, R.string.font_light);
        medium = UiFont.get(this, R.string.font_medium);
        Config cfg = Config.load(this);
        motion = cfg.animations && !cfg.reducedMotion;
        setContentView(build());
        handle(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handle(intent);
    }

    /** Opened by the end-of-phase alarm: move on, and say so. */
    private void handle(Intent intent) {
        if (intent != null && intent.getBooleanExtra(Pomodoro.EXTRA_TIME_UP, false)) {
            intent.removeExtra(Pomodoro.EXTRA_TIME_UP);
            if (timer.catchUp()) celebrate();
        }
        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(tick);
        if (getCurrentFocus() == null) primary.requestFocus();
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(tick);
    }

    /** A phase just ended: one chime, and a line that says what comes next. */
    private void celebrate() {
        int p = timer.phase();
        note = p == Pomodoro.FOCUS ? "Pause terminée · prêt pour la suite"
                : p == Pomodoro.LONG_BREAK ? "Cycle terminé · bravo" : "Séance terminée · bien joué";
        noteUntil = System.currentTimeMillis() + 10_000L;
        try {
            Ringtone r = RingtoneManager.getRingtone(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));
            if (r != null) r.play();
        } catch (RuntimeException ignored) {
            // No sound on this TV: the screen coming up is the signal.
        }
    }

    // ---- composition -----------------------------------------------------------------------------

    private View build() {
        FrameLayout root = new FrameLayout(this);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[] {0xFF070C1F, 0xFF0A1430}));
        // One soft light behind the dial, in the phase's colours: two layers, crossfaded when the phase changes.
        focusLight = light(new int[] {0x47A50044, 0x1F004D98, 0x00000000});
        breakLight = light(new int[] {0x40E8B100, 0x14E8B100, 0x00000000});
        breakLight.setAlpha(0f);
        FrameLayout.LayoutParams glow = new FrameLayout.LayoutParams(px(720), px(720), Gravity.CENTER_VERTICAL | Gravity.START);
        glow.leftMargin = -px(80);
        root.addView(focusLight, glow);
        root.addView(breakLight, new FrameLayout.LayoutParams(glow));

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.HORIZONTAL);
        page.setGravity(Gravity.CENTER_VERTICAL);
        page.setPadding(px(72), px(48), px(72), px(48));
        root.addView(page, new FrameLayout.LayoutParams(-1, -1));

        FrameLayout left = new FrameLayout(this);
        dial = new PomodoroDial(this, 9, 84, true);
        dial.setTypefaces(light, medium);
        left.addView(dial, new FrameLayout.LayoutParams(px(380), px(380), Gravity.CENTER));
        page.addView(left, new LinearLayout.LayoutParams(0, -1, 1));

        page.addView(column(), new LinearLayout.LayoutParams(px(400), -2));
        return root;
    }

    private View light(int[] colours) {
        GradientDrawable g = new GradientDrawable();
        g.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        g.setColors(colours);
        g.setGradientRadius(px(360));
        View v = new View(this);
        v.setBackground(g);
        return v;
    }

    private View column() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);

        c.addView(text("POMODORO", 13, DIM, medium, 0.3f));
        title = text("", 38, WHITE, light, -0.01f);
        c.addView(title, margins(-2, -2, 0, 10, 0, 0));
        session = text("", 17, SOFT, medium, 0.01f);
        c.addView(session, margins(-2, -2, 0, 8, 0, 0));

        LinearLayout bars = new LinearLayout(this);
        segments = new View[PomodoroEngine.CLASSIC.cycle];
        for (int i = 0; i < segments.length; i++) {
            segments[i] = new View(this);
            bars.addView(segments[i], margins(px(64), px(6), 0, 0, 10, 0));
        }
        c.addView(bars, margins(-2, -2, 0, 20, 0, 0));

        LinearLayout stats = new LinearLayout(this);
        sessions = text("0", 32, WHITE, light, 0);
        focusTime = text("0 min", 32, WHITE, light, 0);
        stats.addView(stat(sessions, "SESSIONS AUJOURD'HUI"), new LinearLayout.LayoutParams(0, -2, 1));
        stats.addView(stat(focusTime, "CONCENTRATION"), new LinearLayout.LayoutParams(0, -2, 1));
        c.addView(stats, margins(-1, -2, 0, 34, 0, 0));

        // Controls: one wide primary action, and two round ones whose name shows under the row while focused.
        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        primary = button("", R.drawable.ic_pomo_play, true);
        primary.setOnClickListener(v -> { timer.toggle(); refresh(); });
        controls.addView(primary, margins(px(208), px(56), 0, 0, 14, 0));
        reset = button(null, R.drawable.ic_pomo_reset, false);
        reset.setContentDescription("Réinitialiser");
        reset.setOnClickListener(v -> { timer.reset(); refresh(); });
        controls.addView(reset, margins(px(56), px(56), 0, 0, 14, 0));
        skip = button(null, R.drawable.ic_pomo_skip, false);
        skip.setContentDescription("Passer à l'étape suivante");
        skip.setOnClickListener(v -> { timer.skip(); refresh(); });
        controls.addView(skip, margins(px(56), px(56), 0, 0, 0, 0));
        c.addView(controls, margins(-2, -2, 0, 36, 0, 0));
        hint = text("", 13, DIM, medium, 0.02f);
        c.addView(hint, margins(-2, -2, 0, 12, 0, 0));

        // The format: 25 or 50 minutes of focus, chosen while nothing runs.
        c.addView(text("DURÉE DE CONCENTRATION", 12, DIM, medium, 0.22f), margins(-2, -2, 0, 26, 0, 0));
        LinearLayout format = new LinearLayout(this);
        classic = segment("25 min");
        classic.setOnClickListener(v -> { timer.setDeep(false); refresh(); });
        deep = segment("50 min");
        deep.setOnClickListener(v -> { timer.setDeep(true); refresh(); });
        format.addView(classic, margins(px(112), px(44), 0, 0, 10, 0));
        format.addView(deep, margins(px(112), px(44), 0, 0, 0, 0));
        formatNote = text("", 12, DIM, medium, 0.02f);
        formatNote.setGravity(Gravity.CENTER_VERTICAL);
        format.addView(formatNote, margins(-2, px(44), 16, 0, 0, 0));
        c.addView(format, margins(-2, -2, 0, 10, 0, 0));

        // The arrows: along the controls, then down to the format and back up to the primary action.
        for (View v : new View[] {primary, reset, skip, classic, deep}) v.setId(View.generateViewId());
        for (View v : new View[] {primary, reset, skip}) v.setNextFocusDownId(classic.getId());
        classic.setNextFocusUpId(primary.getId());
        deep.setNextFocusUpId(primary.getId());
        View.OnFocusChangeListener hints = (v, focused) -> {
            if (motion) v.animate().scaleX(focused ? 1.06f : 1f).scaleY(focused ? 1.06f : 1f).setDuration(140).start();
            refreshHint();
        };
        for (View v : new View[] {primary, reset, skip, classic, deep}) v.setOnFocusChangeListener(hints);
        return c;
    }

    private View stat(TextView value, String caption) {
        LinearLayout s = new LinearLayout(this);
        s.setOrientation(LinearLayout.VERTICAL);
        s.addView(value);
        s.addView(text(caption, 11, DIM, medium, 0.2f), margins(-2, -2, 0, 8, 0, 0));
        return s;
    }

    // ---- state -----------------------------------------------------------------------------------

    private void refresh() {
        int p = timer.phase();
        boolean onBreak = p != Pomodoro.FOCUS, running = timer.running(), paused = !running && !timer.fresh();
        boolean changed = shownPhase >= 0 && shownPhase != p;
        dial.setBreak(onBreak, motion && changed);
        if (changed && motion) {
            focusLight.animate().alpha(onBreak ? 0f : 1f).setDuration(700).start();
            breakLight.animate().alpha(onBreak ? 1f : 0f).setDuration(700).start();
        } else if (shownPhase != p) {
            focusLight.setAlpha(onBreak ? 0f : 1f);
            breakLight.setAlpha(onBreak ? 1f : 0f);
        }
        shownPhase = p;

        String line = running ? "Fin à " + DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(timer.endsAtWall()))
                : paused ? "En pause" : "Prêt";
        dial.set(timer.progress(), Pomodoro.clock(timer.remaining()), Pomodoro.phaseName(p).toUpperCase(java.util.Locale.FRENCH),
                line, paused);
        String name = PomodoroCard.session(timer);
        if (!name.contentEquals(title.getText())) {
            if (changed && motion) {
                title.animate().alpha(0f).setDuration(160).withEndAction(() -> {
                    title.setText(name);
                    title.animate().alpha(1f).setDuration(240).start();
                }).start();
            } else {
                title.setText(name);
            }
        }
        boolean noting = System.currentTimeMillis() < noteUntil;
        session.setText(noting ? note : next(p));
        session.setTextColor(noting ? (onBreak ? 0xFFFFDE85 : 0xFF8FB8FF) : SOFT);

        int done = timer.doneInCycle();
        if (p == Pomodoro.LONG_BREAK) done = timer.cycle();
        for (int i = 0; i < segments.length; i++) {
            boolean current = p == Pomodoro.FOCUS && i == done;
            GradientDrawable g = i < done
                    ? new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, onBreak ? new int[] {0xFFE8B100, 0xFFFFDE85}
                            : new int[] {0xFFC0175F, 0xFF2F7BEA})
                    : new GradientDrawable();
            if (i >= done) g.setColor(current ? 0x8CFFFFFF : 0x1FFFFFFF);
            g.setCornerRadius(px(3));
            segments[i].setBackground(g);
        }

        sessions.setText(String.valueOf(timer.sessionsToday()));
        focusTime.setText(Pomodoro.duration(timer.focusToday()));

        primary.setText(running ? "Pause" : paused ? "Reprendre" : "Démarrer");
        setIcon(primary, running ? R.drawable.ic_pomo_pause : R.drawable.ic_pomo_play);
        classic.setSelected(!timer.deep());
        deep.setSelected(timer.deep());
        // The format waits for a stop: a session's length does not change under it.
        for (TextView s : new TextView[] {classic, deep}) {
            s.setFocusable(!running);
            s.setAlpha(running ? 0.4f : 1f);
        }
        formatNote.setText(running ? "modifiable à l'arrêt" : "");
        refreshHint();
    }

    /** What follows the phase on screen, under the session's number. */
    private String next(int p) {
        if (p == Pomodoro.FOCUS) {
            boolean last = timer.doneInCycle() + 1 >= timer.cycle();
            return "Ensuite : pause " + (last ? "longue de " + Pomodoro.duration(timer.length(Pomodoro.LONG_BREAK))
                    : "de " + Pomodoro.duration(timer.length(Pomodoro.SHORT_BREAK)));
        }
        return p == Pomodoro.LONG_BREAK ? "Ensuite : un nouveau cycle" : "Ensuite : la séance " + (timer.doneInCycle() + 1);
    }

    private void refreshHint() {
        View f = getCurrentFocus();
        String h = f == primary ? (timer.running() ? "Mettre en pause" : timer.fresh() ? "Lancer la séance" : "Reprendre la séance")
                : f == reset ? "Réinitialiser le cycle"
                : f == skip ? (timer.phase() == Pomodoro.FOCUS ? "Passer à la pause" : "Passer à la séance suivante")
                : f == classic ? "25 min · pause de 5, puis de 15 toutes les 4 séances"
                : f == deep ? "50 min · pause de 10, puis de 30 toutes les 4 séances" : "";
        hint.setText(h);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Play/pause on the remote works from anywhere on the screen.
        if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY
                || keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE) {
            primary.performClick();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    // ---- controls ----------------------------------------------------------------------------------

    /** Focused: white with navy ink, the screen's one bright surface. Otherwise a faint fill and a hairline. */
    private TextView button(String label, int icon, boolean wide) {
        TextView b = text(label == null ? "" : label, 17, WHITE, medium, 0.02f);
        b.setGravity(Gravity.CENTER);
        b.setFocusable(true);
        b.setClickable(true);
        b.setBackground(states(px(28), false));
        b.setTextColor(ink());
        if (wide) {
            b.setCompoundDrawablePadding(px(10));
            b.setPadding(px(20), 0, px(24), 0);
        } else {
            b.setPadding(px(17), 0, px(17), 0);
        }
        setIcon(b, icon);
        return b;
    }

    private void setIcon(TextView b, int icon) {
        Drawable d = getDrawable(icon);
        b.setCompoundDrawablesRelativeWithIntrinsicBounds(d, null, null, null);
        b.setCompoundDrawableTintList(ink());
    }

    private TextView segment(String label) {
        TextView s = text(label, 16, WHITE, medium, 0.02f);
        s.setGravity(Gravity.CENTER);
        s.setFocusable(true);
        s.setClickable(true);
        s.setBackground(states(px(22), true));
        s.setTextColor(ink());
        return s;
    }

    private ColorStateList ink() {
        return new ColorStateList(new int[][] {{android.R.attr.state_focused}, {}}, new int[] {NAVY, WHITE});
    }

    private StateListDrawable states(int radius, boolean selectable) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[] {android.R.attr.state_focused}, fill(0xFFFFFFFF, 0, 0, radius));
        if (selectable) s.addState(new int[] {android.R.attr.state_selected}, fill(0x33FFFFFF, 1, 0x66FFFFFF, radius));
        s.addState(new int[] {}, fill(0x14FFFFFF, 1, 0x2EFFFFFF, radius));
        return s;
    }

    private GradientDrawable fill(int colour, int stroke, int edge, int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(colour);
        g.setCornerRadius(radius);
        if (stroke > 0) g.setStroke(px(stroke), edge);
        return g;
    }

    private TextView text(String s, float sp, int colour, Typeface face, float spacing) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(colour);
        t.setTypeface(face);
        if (spacing != 0) t.setLetterSpacing(spacing);
        t.setIncludeFontPadding(false);
        t.setSingleLine(true);
        return t;
    }

    private LinearLayout.LayoutParams margins(int w, int h, int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(px(left), px(top), px(right), px(bottom));
        return p;
    }

    private int px(float v) {
        return Math.round(v * dp);
    }
}
