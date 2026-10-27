package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

/**
 * An iPhone-style volume overlay: a tall rounded capsule on the left edge that fills white from the
 * bottom as the media volume rises, with a speaker glyph in a dark circle below it. Shown by
 * HomeKeyService while the volume keys are pressed, then it fades. Drawn by hand so there's no layout to
 * inflate from an accessibility overlay window.
 */
public class VolumeHud extends View {

    private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final Path clip = new Path();
    private final float d;
    private Drawable speaker, mute;
    private float level = 0f;   // 0..1
    private boolean muted;

    public VolumeHud(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        bg.setColor(0xE6161618);
        fill.setColor(0xFFFFFFFF);
        try { speaker = c.getDrawable(R.drawable.hud_speaker); } catch (Throwable ignored) {}
        try { mute = c.getDrawable(R.drawable.hud_speaker_mute); } catch (Throwable ignored) {}
    }

    public void set(float level, boolean muted) {
        this.level = Math.max(0f, Math.min(1f, level));
        this.muted = muted;
        invalidate();
    }

    /** Overlay window params for an accessibility service: left edge, vertically centred, non-interactive. */
    public WindowManager.LayoutParams params() {
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.START | Gravity.CENTER_VERTICAL;
        lp.x = (int) (56 * d);
        return lp;
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension((int) (52 * d), (int) (256 * d));
    }

    @Override
    protected void onDraw(Canvas cv) {
        float capW = getWidth();
        float btn = capW;            // speaker button is the same width as the bar (iPhone squircle)
        float gap = 14 * d;
        float capH = getHeight() - gap - btn;

        // The track capsule.
        r.set(0f, 0f, capW, capH);
        cv.drawRoundRect(r, capW / 2f, capW / 2f, bg);

        // White fill rising from the bottom to the current level, clipped to the capsule.
        if (!muted && level > 0f) {
            float fillTop = capH * (1f - level);
            clip.reset();
            clip.addRoundRect(r, capW / 2f, capW / 2f, Path.Direction.CW);
            cv.save();
            cv.clipPath(clip);
            cv.drawRect(0f, fillTop, capW, capH, fill);
            cv.restore();
        }

        // Speaker glyph in a rounded-square button below the capsule, like iPhone's.
        float cx = capW / 2f;
        float top = capH + gap;
        float rad = 16 * d;
        r.set(0f, top, capW, top + btn);
        cv.drawRoundRect(r, rad, rad, bg);
        Drawable g = muted ? mute : speaker;
        if (g != null) {
            float ic = 24 * d;
            g.setBounds((int) (cx - ic / 2f), (int) (top + (btn - ic) / 2f),
                    (int) (cx + ic / 2f), (int) (top + (btn + ic) / 2f));
            g.draw(cv);
        }
    }
}
