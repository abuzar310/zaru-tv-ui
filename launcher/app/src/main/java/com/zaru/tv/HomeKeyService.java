package com.zaru.tv;

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

import java.io.BufferedReader;
import java.io.InputStreamReader;
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
 * And it runs the Control Center's sleep timer (SleepTimer), which has to outlast the panel and see the buttons.
 *
 * Enable over adb:
 *   settings put secure enabled_accessibility_services com.zaru.tv/.HomeKeyService
 *   settings put secure accessibility_enabled 1
 */
public class HomeKeyService extends AccessibilityService {
    private static final String TAG = "TvHome.Keys";
    /** Sent by the Control Center volume slider (tvx), so dragging it does not pop the HUD over the panel. */
    static final String ACTION_QUIET = "com.zaru.tv.VOLUME_HUD_QUIET";
    /** Android TV's own volume bar stays about 3 s after the last change; the HUD outlasts it to keep it covered. */
    private static final long HUD_MS = 3100;

    /** The running service, for the Control Center: its sleep timer, and Turn off when SleepService is not on. */
    private static HomeKeyService running;
    private SleepTimer sleepTimer;
    private final BroadcastReceiver screenOff = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            if (sleepTimer != null) sleepTimer.cancel();
        }
    };

    /** The sleep timer, or null while this service is not running. */
    static SleepTimer sleepTimer() {
        HomeKeyService s = running;
        return s == null ? null : s.sleepTimer;
    }

    /** Puts the TV to sleep with the lock-screen action, as SleepService does; false while this is not running. */
    static boolean sleep() {
        HomeKeyService s = running;
        return s != null && s.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
    }

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
    private long quietUntil;
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
            showVolume();
            scheduleHideVolume();
        }
    };

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
        sleepTimer = new SleepTimer(this, ui);
        try {
            registerReceiver(screenOff, new IntentFilter(Intent.ACTION_SCREEN_OFF));
        } catch (Throwable t) {
            Log.w(TAG, "no screen-off listener", t);
        }
        running = this;
        settingsScancodes = getResources().getIntArray(R.array.settings_key_scancodes);
        // Keycodes and the panel choice can also come from a theme; theme.sh restarts this service.
        Config config = Config.load(this);
        settingsKeycodes = config.settingsKeycodes;
        settingsPanel = config.settingsKeyPanel;
        appKeys = RemoteKeys.parseAppKeys(config.appKeys);
        Log.d(TAG, "connected, " + appKeys.size() + " app keys");
        followVolumeKeys();
    }

    /** A held volume button repeats after HOLD_DELAY_MS, one step every HOLD_STEP_MS, until it is let go. */
    private static final long HOLD_DELAY_MS = 400, HOLD_STEP_MS = 90, HOLD_MAX_MS = 15000;
    private int heldKey;               // KEYCODE_VOLUME_UP/DOWN while that button is down, else 0
    private long heldSince;
    private volatile boolean following;
    private volatile Process keyLog;
    private final Runnable holdStep = new Runnable() {
        @Override public void run() {
            if (heldKey == 0 || audio == null || ui == null) return;
            if (SystemClock.uptimeMillis() - heldSince > HOLD_MAX_MS) { heldKey = 0; return; }   // missed the release
            try {
                // Exactly one step each time, so the HUD counts 21, 22, 23 (a RAISE can move this TV by two).
                int max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                int cur = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
                int next = Math.max(0, Math.min(max, cur + (heldKey == KeyEvent.KEYCODE_VOLUME_UP ? 1 : -1)));
                if (next != cur) audio.setStreamVolume(AudioManager.STREAM_MUSIC, next, 0);
            } catch (Throwable ignored) {}
            ui.postDelayed(this, HOLD_STEP_MS);
        }
    };

    /**
     * Held volume buttons on a Bluetooth remote move one step only: the remote sends a press and a release, and
     * while any accessibility key filter runs (this service, his Button Remap) Android TV never makes its usual
     * repeats for volume keys. No service ever sees those keys, but the system logs each one (on this TCL,
     * "handleKeyBeforeQueueing ... KEYCODE_VOLUME_UP"), so follow that log and repeat while the button is down.
     * Needs READ_LOGS (pm grant); without it logcat shows only our own lines and nothing happens.
     */
    private void followVolumeKeys() {
        if (following) return;
        following = true;
        Thread t = new Thread(new Runnable() {
            @Override public void run() {
                while (following) {
                    try {
                        Process p = Runtime.getRuntime().exec(new String[] {"logcat", "-T", "1", "WindowManager:D", "*:S"});
                        keyLog = p;
                        BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()));
                        for (String line; following && (line = in.readLine()) != null; ) {
                            if (!line.contains("handleKeyBeforeQueueing")) continue;
                            final int key = line.contains("KEYCODE_VOLUME_UP,") ? KeyEvent.KEYCODE_VOLUME_UP
                                    : line.contains("KEYCODE_VOLUME_DOWN,") ? KeyEvent.KEYCODE_VOLUME_DOWN : 0;
                            if (key == 0) continue;
                            final boolean down = line.contains("action=ACTION_DOWN");
                            final boolean repeat = !line.contains("repeatCount=0");
                            if (ui != null) ui.post(new Runnable() {
                                @Override public void run() { onVolumeKey(key, down, repeat); }
                            });
                        }
                        p.destroy();
                    } catch (Throwable ignored) {}
                    try { Thread.sleep(2000); } catch (InterruptedException e) { return; }
                }
            }
        }, "volume-keys");
        t.setDaemon(true);
        t.start();
    }

    /** Starts the repeat on a press; a release, or the remote repeating by itself (IR), stops it. */
    private void onVolumeKey(int key, boolean down, boolean repeat) {
        if (ui == null) return;
        ui.removeCallbacks(holdStep);
        if (down && !repeat && sleepTimer != null) sleepTimer.onActivity();
        if (!down || repeat || heldKey == key) {
            heldKey = 0;
            return;
        }
        heldKey = key;
        heldSince = SystemClock.uptimeMillis();
        ui.postDelayed(holdStep, HOLD_DELAY_MS);
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
        // The sleep timer's last minute: any button keeps the TV on, and does only that.
        if (sleepTimer != null && sleepTimer.onKey(event)) return true;
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
        if (running == this) running = null;
        try { if (sleepTimer != null) sleepTimer.release(); } catch (Throwable ignored) {}
        try { unregisterReceiver(screenOff); } catch (Throwable ignored) {}
        following = false;
        heldKey = 0;
        try { if (keyLog != null) keyLog.destroy(); } catch (Throwable ignored) {}
        try { if (ui != null) ui.removeCallbacks(holdStep); } catch (Throwable ignored) {}
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
