package com.zaru.tvx;

import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

/**
 * The focus animation. On every focus change the newly-focused tile springs up a little and the one it
 * left settles back. Deliberately compositor-only (scale + Z) so it stays smooth on a 1 GB TV — no glare
 * sweep or 3D tilt, which measured as heavy jank here. Only tile-sized views are touched; the Control
 * Center and our own controls are left to their own focus styling.
 */
final class FocusFx {

    private FocusFx() {}

    static void onFocus(android.app.Activity a, View decor, View oldV, View newV) {
        if (oldV != null && isTile(decor, oldV)) settle(oldV);
        if (newV != null && isTile(decor, newV)) lift(a, newV);
    }

    private static boolean isTile(View decor, View v) {
        int w = v.getWidth(), h = v.getHeight();
        if (w <= 60 || h <= 40) return false;
        if (w > decor.getWidth() * 0.6f || h > decor.getHeight() * 0.6f) return false;
        if (excluded(v)) return false;
        return true;
    }

    /** Leave the Control Center (its own white-flip focus) and our own controls alone. */
    private static boolean excluded(View v) {
        try {
            int id = v.getId();
            if (id != View.NO_ID) {
                String n = v.getResources().getResourceEntryName(id);
                if (n != null && n.startsWith("tvx")) return true;
            }
        } catch (Throwable ignored) {}
        try {
            ViewParent p = v.getParent();
            for (int i = 0; i < 8 && p instanceof View; i++) {
                View pv = (View) p;
                int pid = pv.getId();
                if (pid != View.NO_ID) {
                    String n = pv.getResources().getResourceEntryName(pid);
                    if ("ccPanel".equals(n)) return true;
                }
                p = pv.getParent();
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private static float dp(View v, float d) {
        return v.getResources().getDisplayMetrics().density * d;
    }

    private static void settle(View v) {
        v.animate().cancel();
        v.animate().scaleX(1f).scaleY(1f).translationZ(0f)
                .setDuration(190).setInterpolator(new DecelerateInterpolator(2f)).start();
    }

    private static void lift(android.app.Activity a, View v) {
        unclip(v, 3);
        v.setPivotX(v.getWidth() / 2f);
        v.setPivotY(v.getHeight() / 2f);
        // Tint the lift's shadow with the app's brand colour (a soft white otherwise) so the focused
        // tile casts a glow in its own colour.
        try {
            if (android.os.Build.VERSION.SDK_INT >= 28) {
                int c = GlowFx.brandColorOf(a, v);
                int glow = (c != 0 ? c : 0xFFFFFF) | 0xFF000000;
                v.setOutlineSpotShadowColor(glow);
                v.setOutlineAmbientShadowColor(glow);
            }
        } catch (Throwable ignored) {}
        v.animate().cancel();
        v.animate()
                .scaleX(1.07f).scaleY(1.07f)
                .translationZ(dp(v, 10f))
                .setDuration(230)
                .setInterpolator(new OvershootInterpolator(1.7f))
                .start();
    }

    /** Stop a few levels of parents clipping the lifted tile. */
    private static void unclip(View v, int levels) {
        ViewParent p = v.getParent();
        for (int i = 0; i < levels && p instanceof ViewGroup; i++) {
            ViewGroup grp = (ViewGroup) p;
            try {
                grp.setClipChildren(false);
                grp.setClipToPadding(false);
            } catch (Throwable ignored) {}
            p = grp.getParent();
        }
    }
}
