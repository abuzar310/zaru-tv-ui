package com.gathin.tvhome;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/**
 * The vibe home's ambient glow: a radial oval in the focused app's colour, 840dp across and stretched 1.7 times as
 * wide, fading from 24% through 6% to nothing. The colour eases over 420ms; away from the dock the whole glow drops
 * to 45% over 300ms. One gradient, redrawn only while it changes.
 */
public final class GlowView extends View {
    private static final float STRETCH = 1.7f;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float radius;
    private float cx, cy;
    private int color = 0xFF4F7BFF;
    private ValueAnimator colorAnim;

    public GlowView(Context context, AttributeSet attrs) {
        super(context, attrs);
        radius = 420 * getResources().getDisplayMetrics().density;
        setWillNotDraw(false);
    }

    /** The oval's centre, in this view's coordinates. */
    void setCenter(float x, float y) {
        cx = x;
        cy = y;
        rebuild();
    }

    /** Eases to the new colour over 420ms. */
    void setColor(int next) {
        if (colorAnim != null) colorAnim.cancel();
        if (next == color) return;
        colorAnim = ValueAnimator.ofObject(new ArgbEvaluator(), color, next);
        colorAnim.setDuration(420);
        colorAnim.setInterpolator(new DecelerateInterpolator(1.5f));
        colorAnim.addUpdateListener(a -> {
            color = (int) a.getAnimatedValue();
            rebuild();
        });
        colorAnim.start();
    }

    /** Full strength while the dock has focus, 45% elsewhere. */
    void setAway(boolean away) {
        animate().alpha(away ? 0.45f : 1f).setDuration(300).start();
    }

    private void rebuild() {
        if (radius <= 0) return;
        paint.setShader(new RadialGradient(cx, cy, radius,
                new int[]{withAlpha(0.24f), withAlpha(0.06f), Color.TRANSPARENT},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        invalidate();
    }

    private int withAlpha(float a) {
        return (Math.round(a * 255) << 24) | (color & 0xFFFFFF);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (paint.getShader() == null) return;
        canvas.save();
        canvas.scale(STRETCH, 1f, cx, cy);
        canvas.drawCircle(cx, cy, radius, paint);
        canvas.restore();
    }
}
