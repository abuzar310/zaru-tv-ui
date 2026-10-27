package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

/**
 * A tvOS-style volume overlay: a rounded translucent pill, a speaker glyph, and a white fill bar that
 * tracks the media volume. Shown by HomeKeyService while the volume keys are pressed, then it fades.
 * Drawn by hand so there is no layout to inflate from an accessibility overlay window.
 */
public class VolumeHud extends View {

    private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final float d;
    private Drawable speaker, mute;
    private float level = 0f;   // 0..1
    private boolean muted;

    public VolumeHud(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        bg.setColor(0xE6161618);
        track.setColor(0x3DFFFFFF);
        fill.setColor(0xFFFFFFFF);
        try { speaker = c.getDrawable(R.drawable.hud_speaker); } catch (Throwable ignored) {}
        try { mute = c.getDrawable(R.drawable.hud_speaker_mute); } catch (Throwable ignored) {}
    }

    public void set(float level, boolean muted) {
        this.level = Math.max(0f, Math.min(1f, level));
        this.muted = muted;
        invalidate();
    }

    /** Overlay window params for an accessibility service: top-centre, non-interactive. */
    public WindowManager.LayoutParams params() {
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        lp.y = (int) (46 * d);
        return lp;
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension((int) (440 * d), (int) (54 * d));
    }

    @Override
    protected void onDraw(Canvas cv) {
        float w = getWidth(), h = getHeight();
        r.set(0, 0, w, h);
        cv.drawRoundRect(r, h / 2f, h / 2f, bg);

        float pad = 22 * d;
        float ic = 26 * d;
        float iy = (h - ic) / 2f;
        Drawable g = muted ? mute : speaker;
        if (g != null) {
            g.setBounds((int) pad, (int) iy, (int) (pad + ic), (int) (iy + ic));
            g.draw(cv);
        }

        float barL = pad + ic + 16 * d;
        float barR = w - pad;
        float barH = 8 * d;
        float barT = (h - barH) / 2f;
        r.set(barL, barT, barR, barT + barH);
        cv.drawRoundRect(r, barH / 2f, barH / 2f, track);

        if (!muted && level > 0f) {
            float end = barL + (barR - barL) * level;
            if (end < barL + barH) end = barL + barH;   // keep a visible rounded nub at low levels
            r.set(barL, barT, end, barT + barH);
            cv.drawRoundRect(r, barH / 2f, barH / 2f, fill);
        }
    }
}
