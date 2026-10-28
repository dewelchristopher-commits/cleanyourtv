package com.gathin.tvhome;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;

import java.util.ArrayList;
import java.util.List;

/**
 * floodlights (config.xml or a theme): soft stadium beams that sway slowly over the wallpaper. The beam is drawn
 * once into one small bitmap that every beam shares, and each beam is an ImageView whose rotation and alpha are
 * RenderNode properties: a frame of the sway re-records nothing and rasterises nothing on the CPU, which matters on
 * GPUs such as the RTD2851's (docs/devices/tcl-beyondtv2-v660.md). With animations off or reduced_motion the beams
 * stand still.
 */
final class FloodlightLayer {
    /** Beam centres across the screen, as fractions of its width. */
    private static final float[] X = {0.2f, 0.4f, 0.6f, 0.8f};
    /** Top of the beams, where the wallpaper's roof lights sit, as a fraction of the screen height. */
    private static final float TOP = 0.28f;
    private static final float SWAY_DEGREES = 7f;

    private final List<ImageView> beams = new ArrayList<>();
    private final List<Animator> running = new ArrayList<>();
    private final boolean moving;
    private Bitmap bitmap;

    FloodlightLayer(Context context, FrameLayout root, boolean moving) {
        this.moving = moving;
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        int w = metrics.widthPixels, h = metrics.heightPixels;
        int bw = Math.round(w * 0.24f), bh = Math.round(h * 0.66f);
        // Half resolution: the beam is a soft gradient, so scaling it up costs nothing visible.
        bitmap = drawBeam(Math.max(8, bw / 2), Math.max(8, bh / 2));
        for (int i = 0; i < X.length; i++) {
            ImageView beam = new ImageView(context);
            beam.setImageBitmap(bitmap);
            beam.setScaleType(ImageView.ScaleType.FIT_XY);
            beam.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            beam.setFocusable(false);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(bw, bh);
            lp.leftMargin = Math.round(X[i] * w - bw / 2f);
            lp.topMargin = Math.round(TOP * h);
            beam.setPivotX(bw / 2f);
            beam.setPivotY(0);
            // Each lamp leans toward the centre of the pitch.
            beam.setRotation(base(i));
            // Above the wallpaper (index 0), under everything else Home draws.
            root.addView(beam, Math.min(1 + i, root.getChildCount()), lp);
            beams.add(beam);
        }
    }

    private static float base(int i) {
        return (X[i] - 0.5f) * 40f;
    }

    /** A trapezoid of light, narrow at the lamp and fading toward the pitch, with softened edges. */
    private static Bitmap drawBeam(int w, int h) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        int layers = 6;
        for (int k = 0; k < layers; k++) {
            float spread = 1f - k / (float) layers;   // widest and faintest first, so the edges fade out
            float top = w * 0.04f * spread + w * 0.01f;
            float bottom = w * 0.5f * spread;
            Path path = new Path();
            path.moveTo(w / 2f - top, 0);
            path.lineTo(w / 2f + top, 0);
            path.lineTo(w / 2f + bottom, h);
            path.lineTo(w / 2f - bottom, h);
            path.close();
            p.setShader(new LinearGradient(0, 0, 0, h, 0x1CEFF4FF, 0x00EFF4FF, Shader.TileMode.CLAMP));
            c.drawPath(path, p);
        }
        return b;
    }

    /** Home is on screen: sway, each beam at its own pace so they never move in step. */
    void start() {
        if (!moving || !running.isEmpty()) return;
        for (int i = 0; i < beams.size(); i++) {
            ImageView beam = beams.get(i);
            float base = base(i);
            ObjectAnimator sway = ObjectAnimator.ofFloat(beam, View.ROTATION, base - SWAY_DEGREES, base + SWAY_DEGREES);
            sway.setDuration(6500L + i * 1700L);
            sway.setStartDelay(i * 900L);
            loop(sway);
            ObjectAnimator flicker = ObjectAnimator.ofFloat(beam, View.ALPHA, 1f, 0.6f);
            flicker.setDuration(2900L + i * 800L);
            loop(flicker);
        }
    }

    private void loop(ObjectAnimator a) {
        a.setRepeatCount(ValueAnimator.INFINITE);
        a.setRepeatMode(ValueAnimator.REVERSE);
        a.setInterpolator(new AccelerateDecelerateInterpolator());
        a.start();
        running.add(a);
    }

    /** Home is hidden: no frames for a screen nobody sees. */
    void stop() {
        for (Animator a : running) a.cancel();
        running.clear();
    }

    void close() {
        stop();
        for (ImageView beam : beams) beam.setImageDrawable(null);
        if (bitmap != null) bitmap.recycle();
        bitmap = null;
    }
}
