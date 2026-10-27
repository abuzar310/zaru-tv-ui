package com.zaru.tvx;

import android.app.Activity;
import android.content.SharedPreferences;
import android.view.View;
import android.view.ViewTreeObserver;

/**
 * Personal enhancements layered onto the tvOS Launcher by Siddhant (com.sid.tvos), with the author's
 * permission, for this one TV only. Nothing here is redistributed. Every entry point is wrapped so a
 * failure can never crash the host launcher: the worst case is that an effect quietly does not appear.
 *
 * install() is called once from MainActivity.onCreate, after setContentView.
 */
public final class TvxHooks {
    /** Marks a window as wired. Per window, not per process: he recreate()s MainActivity when the icon style
     *  changes (the dark-mode flip at sunset and sunrise), and the new window needs the hooks again. */
    private static final int TAG_INSTALLED = 0x7e0d0002;

    private TvxHooks() {}

    public static void install(final Activity a) {
        try {
            if (a == null) return;
            final View decor = a.getWindow().getDecorView();
            if (decor.getTag(TAG_INSTALLED) != null) return;
            decor.setTag(TAG_INSTALLED, Boolean.TRUE);

            seedSettings(a);
            // Have the system create Android/data/<pkg>/files with our ownership, so a StandBy photo can be
            // pushed into it over adb (StandByHouse reads standby.jpg from there).
            try { a.getExternalFilesDir(null); } catch (Throwable ignored) {}

            // Apple-TV focus feel + Control Center wiring, both driven off the one global focus listener.
            decor.getViewTreeObserver().addOnGlobalFocusChangeListener(
                new ViewTreeObserver.OnGlobalFocusChangeListener() {
                    @Override public void onGlobalFocusChanged(View oldV, View newV) {
                        try { FocusFx.onFocus(a, decor, oldV, newV); } catch (Throwable ignored) {}
                        try { GlowFx.onFocus(a, newV); } catch (Throwable ignored) {}
                        try { CcExtras.ensure(a, decor); } catch (Throwable ignored) {}
                        try { SleepTimer.ensure(a, decor); } catch (Throwable ignored) {}
                        try { HeroFx.ensure(a, decor); } catch (Throwable ignored) {}
                        try { HomeWallpaper.ensure(a, decor); } catch (Throwable ignored) {}
                    }
                });

            // First wire-up once the tree has settled: start the hero drift and any present Control Center.
            decor.postDelayed(new Runnable() {
                @Override public void run() {
                    try { HeroFx.ensure(a, decor); } catch (Throwable ignored) {}
                    try { GlowFx.ensure(a, decor); } catch (Throwable ignored) {}
                    try { CcExtras.ensure(a, decor); } catch (Throwable ignored) {}
                    try { SleepTimer.ensure(a, decor); } catch (Throwable ignored) {}
                    try { HomeWallpaper.ensure(a, decor); } catch (Throwable ignored) {}
                }
            }, 1200);
        } catch (Throwable ignored) {}
    }

    /** StandBy in the house design; called at the end of StandByActivity.onCreate. */
    public static void standby(Activity a) {
        try { StandByHouse.show(a); } catch (Throwable ignored) {}
    }

    /** The same for ClockDream, his StandBy-as-screensaver; called at the end of its onAttachedToWindow. */
    public static void standbyDream(android.service.dreams.DreamService d) {
        try { StandByHouse.show(d); } catch (Throwable ignored) {}
    }

    /** Apply the preferences the owner asked for, once, without fighting later manual changes. */
    private static void seedSettings(Activity a) {
        try {
            SharedPreferences ap = a.getSharedPreferences("appearance", 0);
            if (!ap.getBoolean("tvx_seed_v1", false)) {
                // dark_schedule: 1 = Sunset (dark automatically through the night).
                ap.edit().putInt("dark_schedule", 1).putBoolean("tvx_seed_v1", true).apply();
            }
        } catch (Throwable ignored) {}
    }
}
