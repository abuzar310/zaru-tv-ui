package com.gathin.tvhome;

import android.animation.ValueAnimator;
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
 * An iPhone-style volume overlay: one tall capsule that fills white from the bottom as the media volume
 * rises, with the speaker glyph inside near the foot, dark on the fill and light on the track, like iOS.
 * It sits on the right edge exactly over Android TV's own volume bar, which no app can switch off: an
 * accessibility overlay is layered above the volume dialog, so this opaque capsule hides it while it is
 * up. Shown by HomeKeyService on every volume change, then it fades. Drawn by hand, so there is no
 * layout to inflate from an accessibility overlay window.
 */
public class VolumeHud extends View {

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final Path clip = new Path();
    private final float d;
    private Drawable speaker, mute;
    private float shown;   // the drawn level, 0..1, easing toward the latest one
    private int value;     // the volume step the TV reports, shown as a number at the top
    private boolean muted;
    private ValueAnimator ease;

    public VolumeHud(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        track.setColor(0xFF3A3A3C);   // opaque, so the system bar underneath never shows through
        fill.setColor(0xFFFFFFFF);
        label.setTextAlign(Paint.Align.CENTER);
        label.setTextSize(20 * c.getResources().getDisplayMetrics().scaledDensity);
        try { label.setTypeface(c.getResources().getFont(R.font.outfit_medium)); } catch (Throwable ignored) {}
        try { speaker = c.getDrawable(R.drawable.hud_speaker).mutate(); } catch (Throwable ignored) {}
        try { mute = c.getDrawable(R.drawable.hud_speaker_mute).mutate(); } catch (Throwable ignored) {}
    }

    /** The new level and step; eased when the HUD is already up (a held key ramps smoothly), set at once otherwise. */
    public void set(float level, int value, boolean muted, boolean animate) {
        final float target = Math.max(0f, Math.min(1f, level));
        this.value = value;
        this.muted = muted;
        if (ease != null) ease.cancel();
        if (!animate) {
            shown = target;
            invalidate();
            return;
        }
        ease = ValueAnimator.ofFloat(shown, target);
        ease.setDuration(140);
        ease.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) {
                shown = (float) a.getAnimatedValue();
                invalidate();
            }
        });
        ease.start();
    }

    /** Overlay window params for an accessibility service: right edge, centred, over the system volume bar. */
    public WindowManager.LayoutParams params() {
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
        lp.x = (int) (35 * d);
        return lp;
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        // Tall enough that the rounded ends still cover the corners of the system bar (88x400 px at 1080p).
        setMeasuredDimension((int) (56 * d), (int) (232 * d));
    }

    @Override
    protected void onDraw(Canvas cv) {
        float w = getWidth(), h = getHeight(), rad = w / 2f;
        r.set(0f, 0f, w, h);
        cv.drawRoundRect(r, rad, rad, track);

        // White fill rising from the bottom to the current level, clipped to the capsule.
        float fillTop = h * (1f - shown);
        if (!muted && shown > 0f) {
            clip.reset();
            clip.addRoundRect(r, rad, rad, Path.Direction.CW);
            cv.save();
            cv.clipPath(clip);
            cv.drawRect(0f, fillTop, w, h, fill);
            cv.restore();
        }

        // The step number in the top of the capsule, where the system bar shows its own; dark once the fill reaches it.
        Paint.FontMetrics fm = label.getFontMetrics();
        float ny = rad - (fm.ascent + fm.descent) / 2f;
        label.setColor(muted ? 0xFF8E8E93 : (fillTop <= rad + fm.ascent / 2f ? 0xFF3A3A3C : 0xFFFFFFFF));
        cv.drawText(Integer.toString(value), w / 2f, ny, label);

        // The glyph inside the foot of the capsule: dark grey on the white fill, light on the grey track.
        Drawable g = muted ? mute : speaker;
        if (g != null) {
            float ic = 26 * d, cx = w / 2f, cy = h - rad;
            boolean onFill = !muted && fillTop <= cy - ic / 2f;
            g.setTint(onFill ? 0xFF636366 : 0xFFE5E5EA);
            g.setBounds((int) (cx - ic / 2f), (int) (cy - ic / 2f), (int) (cx + ic / 2f), (int) (cy + ic / 2f));
            g.draw(cv);
        }
    }
}
