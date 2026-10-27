package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

import java.util.Collections;
import java.util.Map;

/**
 * Sends the remote's Home button to this launcher on remotes that emit KEYCODE_ALL_APPS for Home
 * (Xiaomi sticks and boxes). Android TV 9 routes that key only to the system-image launcher, so once
 * the stock launcher is disabled the button does nothing. Every other key passes through untouched.
 *
 * It also draws the iPhone-style volume HUD. Android TV hands the remote's volume keys straight to the
 * system (PhoneWindowManager routes them on TVs, and the accessibility key filter asks it first), so no
 * service ever receives them; instead this follows every volume change and lays VolumeHud over the
 * system's own volume bar until that bar has gone.
 *
 * Enable over adb:
 *   settings put secure enabled_accessibility_services com.gathin.tvhome/.HomeKeyService
 *   settings put secure accessibility_enabled 1
 */
public class HomeKeyService extends AccessibilityService {
    private static final String TAG = "TvHome.Keys";
    /** Sent by the Control Center volume slider (tvx), so dragging it does not pop the HUD over the panel. */
    static final String ACTION_QUIET = "com.gathin.tvhome.VOLUME_HUD_QUIET";
    /** Android TV's own volume bar stays about 3 s after the last change; the HUD outlasts it to keep it covered. */
    private static final long HUD_MS = 3100;
    /**
     * The TV moves its 0..100 media volume by 1 per press, which is too fine to hear. A press is topped up to
     * STEP_TAP and each repeat of a held button to STEP_HELD; a step within HELD_GAP_MS of the last is a repeat.
     */
    private static final int STEP_TAP = 5, STEP_HELD = 2;
    private static final long HELD_GAP_MS = 220;

    /** Remote buttons that should open Settings (config.xml, settings_key_scancodes and settings_key_keycodes). */
    private int[] settingsScancodes = new int[0];
    private int[] settingsKeycodes = new int[0];
    private boolean settingsPanel;
    /** app_keys, the same map InputKeyService reads, for setups that run only this service. */
    private Map<Integer, String> appKeys = Collections.emptyMap();

    /** iPhone-style volume overlay, shown on every volume change. */
    private AudioManager audio;
    private WindowManager windows;
    private Handler ui;
    private VolumeHud hud;
    private long quietUntil, lastStepAt;
    private int ownTarget = -1;   // the volume we set ourselves, so its broadcast is not topped up again
    private final Runnable hideHud = new Runnable() {
        @Override public void run() {
            if (hud == null) return;
            hud.animate().alpha(0f).setDuration(260).withEndAction(new Runnable() {
                @Override public void run() { if (hud != null) hud.setVisibility(View.GONE); }
            }).start();
        }
    };
    private final BroadcastReceiver volumeWatch = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            if (ACTION_QUIET.equals(i.getAction())) {
                quietUntil = SystemClock.uptimeMillis() + 800;
                return;
            }
            int stream = i.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", AudioManager.STREAM_MUSIC);
            if (stream != AudioManager.STREAM_MUSIC || SystemClock.uptimeMillis() < quietUntil) return;
            if ("android.media.VOLUME_CHANGED_ACTION".equals(i.getAction())) {
                topUp(i.getIntExtra("android.media.EXTRA_PREV_VOLUME_STREAM_VALUE", -1),
                        i.getIntExtra("android.media.EXTRA_VOLUME_STREAM_VALUE", -1));
            }
            showVolume();
            scheduleHideVolume();
        }
    };

    /** Turns the TV's own one-step change (a press or a held repeat) into a bigger one; leaves anything else. */
    private void topUp(int prev, int cur) {
        try {
            if (cur == ownTarget) { ownTarget = -1; return; }   // our own change coming back
            if (audio == null || prev < 0 || cur < 0 || Math.abs(cur - prev) != 1) return;
            long now = SystemClock.uptimeMillis();
            int step = now - lastStepAt < HELD_GAP_MS ? STEP_HELD : STEP_TAP;
            lastStepAt = now;
            int max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            int target = Math.max(0, Math.min(max, prev + (cur > prev ? step : -step)));
            if (target == cur) return;
            ownTarget = target;
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0);
        } catch (Throwable ignored) {}
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        // The HUD first, so it works even if a theme's config fails to load below.
        audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        windows = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        ui = new Handler(Looper.getMainLooper());
        try {
            IntentFilter volume = new IntentFilter("android.media.VOLUME_CHANGED_ACTION");
            volume.addAction("android.media.STREAM_MUTE_CHANGED_ACTION");
            volume.addAction(ACTION_QUIET);
            registerReceiver(volumeWatch, volume);
        } catch (Throwable t) {
            Log.w(TAG, "no volume listener", t);
        }
        settingsScancodes = getResources().getIntArray(R.array.settings_key_scancodes);
        // Keycodes and the panel choice can also come from a theme; theme.sh restarts this service.
        Config config = Config.load(this);
        settingsKeycodes = config.settingsKeycodes;
        settingsPanel = config.settingsKeyPanel;
        appKeys = RemoteKeys.parseAppKeys(config.appKeys);
        Log.d(TAG, "connected, " + appKeys.size() + " app keys");
    }

    private boolean isSettingsKey(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_SETTINGS) return true;
        for (int code : settingsScancodes) if (event.getScanCode() == code) return true;
        for (int code : settingsKeycodes) if (event.getKeyCode() == code) return true;
        return false;
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        // Finds a button's code for app_keys: adb logcat -s TvHome.Keys, then press it on the remote.
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            Log.d(TAG, "key " + event.getKeyCode() + " scan " + event.getScanCode());
        }
        // The stock launcher handled the remote's Settings button; with it disabled the button does nothing.
        if (isSettingsKey(event)) {
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                Intent open = settingsPanel ? new Intent(this, QuickSettingsActivity.class)
                        : new Intent(Settings.ACTION_SETTINGS);
                startActivity(open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            }
            return true;
        }
        String app = appKeys.get(event.getKeyCode());
        if (app != null) {
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) RemoteKeys.launch(this, app);
            return true;
        }
        if (event.getKeyCode() != KeyEvent.KEYCODE_ALL_APPS) return false;
        if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
            Intent home = new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .setPackage(getPackageName())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivity(home);
        }
        return true;
    }

    private void showVolume() {
        try {
            if (audio == null || windows == null) return;
            int max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            int cur = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
            boolean muted = cur == 0 || audio.isStreamMute(AudioManager.STREAM_MUSIC);
            float level = max > 0 ? (float) cur / max : 0f;
            if (hud == null) {
                hud = new VolumeHud(this);
                try { windows.addView(hud, hud.params()); } catch (Throwable t) { hud = null; return; }
            }
            boolean up = hud.getVisibility() == View.VISIBLE && hud.getAlpha() > 0.5f;
            if (ui != null) ui.removeCallbacks(hideHud);
            hud.animate().cancel();
            hud.setVisibility(View.VISIBLE);
            hud.setAlpha(1f);
            hud.set(level, cur, muted, up);
        } catch (Throwable ignored) {}
    }

    private void scheduleHideVolume() {
        if (ui != null) {
            ui.removeCallbacks(hideHud);
            ui.postDelayed(hideHud, HUD_MS);
        }
    }

    @Override
    public boolean onUnbind(Intent intent) {
        try { unregisterReceiver(volumeWatch); } catch (Throwable ignored) {}
        try { if (ui != null) ui.removeCallbacks(hideHud); } catch (Throwable ignored) {}
        try { if (windows != null && hud != null) windows.removeView(hud); } catch (Throwable ignored) {}
        hud = null;
        return super.onUnbind(intent);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }
}
