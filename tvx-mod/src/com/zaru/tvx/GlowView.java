package com.zaru.tvx;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;

/**
 * A full-screen overlay that washes the edges of the screen with a colour, transparent through the
 * middle so it never hides content. GlowFx feeds it the hero's dominant colour and it cross-fades there,
 * so the whole home glows with whatever is on the shelf.
 */
class GlowView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ArgbEvaluator argb = new ArgbEvaluator();
    private int color = 0;          // current rgb (no alpha)
    private RadialGradient grad;
    private int builtW, builtH, builtColor = -1;
    private ValueAnimator anim;

    GlowView(Context c) {
        super(c);
        setClickable(false);
        setFocusable(false);
    }

    void setGlow(int targetRgb) {
        targetRgb &= 0xFFFFFF;
        if (targetRgb == color) return;
        if (anim != null) anim.cancel();
        anim = ValueAnimator.ofObject(argb, color | 0xFF000000, targetRgb | 0xFF000000);
        anim.setDuration(1100);
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) {
                color = ((Integer) a.getAnimatedValue()) & 0xFFFFFF;
                invalidate();
            }
        });
        anim.start();
    }

    private void rebuild() {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        int edge = color | 0x66000000;   // ~40% alpha at the rim
        int soft = color | 0x24000000;   // ~14% midway
        grad = new RadialGradient(w / 2f, h * 0.60f, Math.max(w, h) * 0.80f,
                new int[]{0x00000000, soft, edge},
                new float[]{0f, 0.48f, 1f}, Shader.TileMode.CLAMP);
        paint.setShader(grad);
        builtW = w; builtH = h; builtColor = color;
    }

    @Override
    protected void onDraw(Canvas cv) {
        if (color == 0) return;
        if (grad == null || builtW != getWidth() || builtH != getHeight() || builtColor != color) rebuild();
        if (grad != null) cv.drawRect(0, 0, getWidth(), getHeight(), paint);
    }
}
