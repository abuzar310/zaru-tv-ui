package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
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
 * Enable over adb:
 *   settings put secure enabled_accessibility_services com.gathin.tvhome/.HomeKeyService
 *   settings put secure accessibility_enabled 1
 */
public class HomeKeyService extends AccessibilityService {

    /** Remote buttons that should open Settings (config.xml, settings_key_scancodes and settings_key_keycodes). */
    private int[] settingsScancodes = new int[0];
    private int[] settingsKeycodes = new int[0];
    private boolean settingsPanel;
    /** app_keys, the same map InputKeyService reads, for setups that run only this service. */
    private Map<Integer, String> appKeys = Collections.emptyMap();

    /** tvOS-style volume overlay, shown while the volume keys are held. */
    private AudioManager audio;
    private WindowManager windows;
    private Handler ui;
    private VolumeHud hud;
    private final Runnable hideHud = new Runnable() {
        @Override public void run() {
            if (hud == null) return;
            hud.animate().alpha(0f).setDuration(260).withEndAction(new Runnable() {
                @Override public void run() { if (hud != null) hud.setVisibility(View.GONE); }
            }).start();
        }
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        settingsScancodes = getResources().getIntArray(R.array.settings_key_scancodes);
        // Keycodes and the panel choice can also come from a theme; theme.sh restarts this service.
        Config config = Config.load(this);
        settingsKeycodes = config.settingsKeycodes;
        settingsPanel = config.settingsKeyPanel;
        appKeys = RemoteKeys.parseAppKeys(config.appKeys);
        audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        windows = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        ui = new Handler(Looper.getMainLooper());
    }

    private boolean isSettingsKey(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_SETTINGS) return true;
        for (int code : settingsScancodes) if (event.getScanCode() == code) return true;
        for (int code : settingsKeycodes) if (event.getKeyCode() == code) return true;
        return false;
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        int kc = event.getKeyCode();
        // Volume: handle every DOWN (including auto-repeat while held) ourselves, so press-and-hold ramps
        // smoothly instead of one step per press, and show our own tvOS-style bar instead of the system UI.
        if (kc == KeyEvent.KEYCODE_VOLUME_UP || kc == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                try {
                    if (audio != null) audio.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                            kc == KeyEvent.KEYCODE_VOLUME_UP ? AudioManager.ADJUST_RAISE : AudioManager.ADJUST_LOWER, 0);
                } catch (Throwable ignored) {}
                showVolume();
            } else if (event.getAction() == KeyEvent.ACTION_UP) {
                scheduleHideVolume();
            }
            return true;
        }
        if (kc == KeyEvent.KEYCODE_VOLUME_MUTE) {
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                try {
                    if (audio != null) audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, 0);
                } catch (Throwable ignored) {}
                showVolume();
                scheduleHideVolume();
            }
            return true;
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
            if (ui != null) ui.removeCallbacks(hideHud);
            hud.animate().cancel();
            hud.setVisibility(View.VISIBLE);
            hud.setAlpha(1f);
            hud.set(level, muted);
        } catch (Throwable ignored) {}
    }

    private void scheduleHideVolume() {
        if (ui != null) {
            ui.removeCallbacks(hideHud);
            ui.postDelayed(hideHud, 1300);
        }
    }

    @Override
    public boolean onUnbind(Intent intent) {
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
