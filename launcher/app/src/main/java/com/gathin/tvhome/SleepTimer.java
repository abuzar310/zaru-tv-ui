package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.graphics.PixelFormat;
import android.media.AudioManager;
import android.os.Handler;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.TextView;

/**
 * The Control Center's sleep timer: Off, 15, 30, 60 or 90 minutes. A minute before the end a notice says so, and
 * any button pressed in that minute keeps the TV on. At the end it pauses what is playing and puts the TV to sleep,
 * as Turn off does. It lives in HomeKeyService, which outlives the panel, may draw over any app and sees the
 * remote's buttons. Turning the TV off any other way clears it, so it never fires into the next evening.
 */
final class SleepTimer {
    static final int[] MINUTES = {0, 15, 30, 60, 90};
    private static final long MINUTE_MS = 60_000;
    private static final long WARN_MS = 60_000;
    private static final long CANCELLED_MS = 1800;

    private final AccessibilityService service;
    private final Handler ui;
    private final WindowManager windows;
    private int minutes;
    private long endsAt;            // SystemClock.uptimeMillis() when the TV goes to sleep; 0 while the timer is off
    private int eatUp;              // the button that kept the TV on: its release is eaten too
    private View notice;
    private TextView noticeTitle, noticeHint;

    private final Runnable warn = this::showNotice;
    private final Runnable fire = this::fire;
    private final Runnable hide = this::hideNotice;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (endsAt == 0 || noticeTitle == null) return;
            long left = endsAt - SystemClock.uptimeMillis();
            int seconds = (int) Math.max(1, (left + 999) / 1000);
            noticeTitle.setText(service.getString(R.string.sleep_notice, seconds));
            // Next when the shown second runs out.
            ui.postDelayed(this, Math.max(50, left - (seconds - 1) * 1000L));
        }
    };

    SleepTimer(AccessibilityService service, Handler ui) {
        this.service = service;
        this.ui = ui;
        windows = (WindowManager) service.getSystemService(Context.WINDOW_SERVICE);
    }

    /** Off, then 15, 30, 60 and 90 minutes from now, then Off again. */
    void next() {
        int at = 0;
        for (int i = 0; i < MINUTES.length; i++) if (MINUTES[i] == minutes) at = i;
        set(MINUTES[(at + 1) % MINUTES.length]);
    }

    /** Starts the timer for this many minutes from now; 0 turns it off. */
    void set(int min) {
        ui.removeCallbacks(warn);
        ui.removeCallbacks(fire);
        ui.removeCallbacks(tick);
        hideNotice();
        minutes = Math.max(0, min);
        endsAt = minutes > 0 ? SystemClock.uptimeMillis() + minutes * MINUTE_MS : 0;
        if (endsAt == 0) return;
        ui.postAtTime(warn, endsAt - WARN_MS);
        ui.postAtTime(fire, endsAt);
    }

    /** Whole minutes left, rounded up; 0 while the timer is off. */
    int minutesLeft() {
        if (endsAt == 0) return 0;
        long left = endsAt - SystemClock.uptimeMillis();
        return (int) Math.max(1, (left + MINUTE_MS - 1) / MINUTE_MS);
    }

    /** From HomeKeyService.onKeyEvent: in the last minute any button keeps the TV on, and does nothing else. */
    boolean onKey(KeyEvent event) {
        if (eatUp != 0 && event.getKeyCode() == eatUp && event.getAction() == KeyEvent.ACTION_UP) {
            eatUp = 0;
            return true;
        }
        if (!warning() || event.getAction() != KeyEvent.ACTION_DOWN) return false;
        eatUp = event.getKeyCode();
        keepOn();
        return true;
    }

    /** A volume press, which the service only learns of: someone is awake, so the same as any other button. */
    void onActivity() {
        if (warning()) keepOn();
    }

    /** The TV went to sleep some other way: the timer is spent. */
    void cancel() {
        set(0);
    }

    void release() {
        set(0);
        ui.removeCallbacks(hide);
        try { if (notice != null) windows.removeView(notice); } catch (Throwable ignored) {}
        notice = null;
    }

    private boolean warning() {
        return endsAt != 0 && SystemClock.uptimeMillis() >= endsAt - WARN_MS;
    }

    private void keepOn() {
        set(0);
        if (!ensureNotice()) return;
        noticeTitle.setText(R.string.sleep_notice_cancelled);
        noticeHint.setText(R.string.sleep_notice_cancelled_hint);
        reveal();
        ui.postDelayed(hide, CANCELLED_MS);
    }

    private void fire() {
        ui.removeCallbacks(tick);
        minutes = 0;
        endsAt = 0;
        hideNotice();
        // Pause first, so the show waits where it was rather than playing on to a dark screen.
        try {
            AudioManager audio = (AudioManager) service.getSystemService(Context.AUDIO_SERVICE);
            audio.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE));
            audio.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE));
        } catch (Throwable ignored) {}
        ui.postDelayed(() -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN), 400);
    }

    private void showNotice() {
        if (endsAt == 0 || !ensureNotice()) return;
        noticeHint.setText(R.string.sleep_notice_hint);
        reveal();
        ui.removeCallbacks(tick);
        tick.run();
    }

    /** Fades the card in from just above; one already up (or fading out) comes back from where it is. */
    private void reveal() {
        ui.removeCallbacks(hide);
        notice.animate().cancel();
        if (notice.getVisibility() != View.VISIBLE) {
            notice.setVisibility(View.VISIBLE);
            notice.setAlpha(0f);
            notice.setTranslationY(-12 * service.getResources().getDisplayMetrics().density);
        }
        notice.animate().alpha(1f).translationY(0f).setDuration(320)
                .setInterpolator(new DecelerateInterpolator(1.6f)).start();
    }

    private void hideNotice() {
        ui.removeCallbacks(hide);
        if (notice == null || notice.getVisibility() != View.VISIBLE) return;
        notice.animate().cancel();
        notice.animate().alpha(0f).setDuration(260).withEndAction(() -> {
            if (notice != null) notice.setVisibility(View.GONE);
        }).start();
    }

    /** The notice card, added to the screen once and then shown and hidden. */
    private boolean ensureNotice() {
        if (notice != null) return true;
        try {
            View v = LayoutInflater.from(service).inflate(R.layout.sleep_notice, null);
            float d = service.getResources().getDisplayMetrics().density;
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.TOP | Gravity.END;
            lp.x = (int) (32 * d);
            lp.y = (int) (28 * d);
            v.setVisibility(View.GONE);
            windows.addView(v, lp);
            notice = v;
            noticeTitle = v.findViewById(R.id.sleepNoticeTitle);
            noticeHint = v.findViewById(R.id.sleepNoticeHint);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
