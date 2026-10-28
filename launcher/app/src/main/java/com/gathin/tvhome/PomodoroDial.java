package com.gathin.tvhome;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.View;

/**
 * The pomodoro's dial: a thin track, the phase's progress as an arc in the phase's colours with a bright head, and
 * the time left in the middle. The digits sit in equal cells, so the time never shifts sideways as it counts. With
 * labels on (the timer screen) the phase's name goes above the digits and a short line below.
 *
 * It draws only when told something changed: once a second while a phase runs, and through the 700 ms blend when
 * the phase changes from focus to a break or back.
 */
final class PomodoroDial extends View {
    /** Focus: garnet into blue. A break: gold into a warm light. */
    private static final int[] FOCUS = {0xFFC0175F, 0xFF2F7BEA}, BREAK = {0xFFE8B100, 0xFFFFDE85};

    private final float stroke;
    private final boolean labels;
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint head = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint digits = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint top = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bottom = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF ring = new RectF();
    private final Rect bounds = new Rect();
    private final ArgbEvaluator argb = new ArgbEvaluator();

    private int colourA = FOCUS[0], colourB = FOCUS[1];
    private ValueAnimator blend;
    private float progress;
    private boolean paused;
    private String time = "25:00", label = "", line = "";
    private float cell, colon;

    PomodoroDial(Context c, float strokeDp, float digitsSp, boolean labels) {
        super(c);
        stroke = strokeDp * c.getResources().getDisplayMetrics().density;
        this.labels = labels;
        track.setStyle(Paint.Style.STROKE);
        track.setStrokeWidth(stroke);
        track.setColor(0x1AFFFFFF);
        arc.setStyle(Paint.Style.STROKE);
        arc.setStrokeWidth(stroke);
        arc.setStrokeCap(Paint.Cap.ROUND);
        digits.setColor(0xFFFFFFFF);
        digits.setTextAlign(Paint.Align.CENTER);
        digits.setTextSize(sp(digitsSp));
        top.setTextAlign(Paint.Align.CENTER);
        top.setTextSize(sp(Math.max(12, digitsSp * 0.16f)));
        top.setLetterSpacing(0.24f);
        bottom.setTextAlign(Paint.Align.CENTER);
        bottom.setTextSize(sp(Math.max(13, digitsSp * 0.18f)));
        bottom.setColor(0x99FFFFFF);
        measureCells();
    }

    void setTypefaces(Typeface light, Typeface medium) {
        digits.setTypeface(light);
        top.setTypeface(medium);
        bottom.setTypeface(medium);
        measureCells();
        invalidate();
    }

    /** The widest digit sets every digit's cell. */
    private void measureCells() {
        cell = 0;
        for (char d = '0'; d <= '9'; d++) cell = Math.max(cell, digits.measureText(String.valueOf(d)));
        colon = digits.measureText(":") * 0.9f;
    }

    /** Called once a second at most; a call that changes nothing draws nothing. */
    void set(float progress, String time, String label, String line, boolean paused) {
        progress = Math.max(0f, Math.min(1f, progress));
        if (progress == this.progress && time.equals(this.time) && label.equals(this.label) && line.equals(this.line)
                && paused == this.paused) return;
        this.progress = progress;
        this.time = time;
        this.label = label;
        this.line = line;
        this.paused = paused;
        invalidate();
    }

    /** Focus or a break: the colours blend over 700 ms, or switch at once with motion off. */
    void setBreak(boolean onBreak, boolean animate) {
        int[] to = onBreak ? BREAK : FOCUS;
        if (blend != null) blend.cancel();
        if (to[0] == colourA && to[1] == colourB) return;
        if (!animate) {
            colourA = to[0];
            colourB = to[1];
            shade();
            return;
        }
        int fromA = colourA, fromB = colourB;
        blend = ValueAnimator.ofFloat(0f, 1f);
        blend.setDuration(700);
        blend.addUpdateListener(a -> {
            float f = (float) a.getAnimatedValue();
            colourA = (int) argb.evaluate(f, fromA, to[0]);
            colourB = (int) argb.evaluate(f, fromB, to[1]);
            shade();
        });
        blend.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (blend != null) blend.cancel();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        shade();
    }

    /** The arc's gradient for the current colours, rotated so it runs clockwise from the top. */
    private void shade() {
        int w = getWidth(), h = getHeight();
        if (w > 0 && h > 0) {
            SweepGradient g = new SweepGradient(w / 2f, h / 2f, new int[] {colourA, colourB, colourB}, new float[] {0f, 0.75f, 1f});
            Matrix m = new Matrix();
            m.setRotate(-90 - 4, w / 2f, h / 2f);   // a little before the top, so the round cap starts in colourA
            g.setLocalMatrix(m);
            arc.setShader(g);
        }
        top.setColor(colourB | 0xFF000000);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight(), cx = w / 2f, cy = h / 2f;
        float r = Math.min(w, h) / 2f - stroke * 1.2f;
        ring.set(cx - r, cy - r, cx + r, cy + r);
        c.drawOval(ring, track);
        arc.setAlpha(paused ? 110 : 255);
        if (progress > 0f) {
            c.drawArc(ring, -90, Math.max(1f, 360f * progress), false, arc);
            double a = Math.toRadians(-90 + 360f * progress);
            float hx = cx + (float) Math.cos(a) * r, hy = cy + (float) Math.sin(a) * r;
            head.setColor(paused ? 0x66FFFFFF : 0x40FFFFFF);
            c.drawCircle(hx, hy, stroke * 1.15f, head);
            head.setColor(paused ? 0x99FFFFFF : 0xFFFFFFFF);
            c.drawCircle(hx, hy, stroke * 0.42f, head);
        }

        // The digits, each centred in its own cell so 11:11 and 08:08 are the same width.
        digits.getTextBounds("0", 0, 1, bounds);
        float capHeight = bounds.height();
        float baseline = cy + capHeight / 2f;
        float total = 4 * cell + colon, x = cx - total / 2f;
        digits.setAlpha(paused ? 170 : 255);
        for (int i = 0; i < time.length(); i++) {
            char ch = time.charAt(i);
            float width = ch == ':' ? colon : cell;
            c.drawText(String.valueOf(ch), x + width / 2f, baseline - (ch == ':' ? capHeight * 0.06f : 0), digits);
            x += width;
        }
        if (labels) {
            if (!label.isEmpty()) c.drawText(label, cx, cy - capHeight / 2f - top.getTextSize() * 1.6f, top);
            if (!line.isEmpty()) c.drawText(line, cx, cy + capHeight / 2f + bottom.getTextSize() * 2.3f, bottom);
        }
    }

    private float sp(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, getResources().getDisplayMetrics());
    }
}
