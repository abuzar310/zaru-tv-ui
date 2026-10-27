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
    private static boolean installed;

    private TvxHooks() {}

    public static void install(final Activity a) {
        try {
            if (installed || a == null) return;
            installed = true;
            final View decor = a.getWindow().getDecorView();

            seedSettings(a);

            // Apple-TV focus feel + Control Center wiring, both driven off the one global focus listener.
            decor.getViewTreeObserver().addOnGlobalFocusChangeListener(
                new ViewTreeObserver.OnGlobalFocusChangeListener() {
                    @Override public void onGlobalFocusChanged(View oldV, View newV) {
                        try { FocusFx.onFocus(decor, oldV, newV); } catch (Throwable ignored) {}
                        try { GlowFx.onFocus(a, newV); } catch (Throwable ignored) {}
                        try { CcExtras.ensure(a, decor); } catch (Throwable ignored) {}
                        try { SleepTimer.ensure(a, decor); } catch (Throwable ignored) {}
                        try { HeroFx.ensure(a, decor); } catch (Throwable ignored) {}
                    }
                });

            // First wire-up once the tree has settled: start the hero drift and any present Control Center.
            decor.postDelayed(new Runnable() {
                @Override public void run() {
                    try { HeroFx.ensure(a, decor); } catch (Throwable ignored) {}
                    try { GlowFx.ensure(a, decor); } catch (Throwable ignored) {}
                    try { CcExtras.ensure(a, decor); } catch (Throwable ignored) {}
                    try { SleepTimer.ensure(a, decor); } catch (Throwable ignored) {}
                }
            }, 1200);
        } catch (Throwable ignored) {}
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
