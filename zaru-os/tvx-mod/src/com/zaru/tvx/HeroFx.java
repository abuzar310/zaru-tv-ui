package com.zaru.tvx;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageView;

/**
 * Living hero: a slow, perpetual Ken-Burns drift on the two shelf backdrop ImageViews, so the featured
 * art gently breathes and pans instead of sitting still. His own cross-fade between shelf and shelf2 is
 * left untouched — this only transforms the views, whichever one is showing. Applied once per view via a
 * tag, so it re-applies if the activity is recreated (e.g. the day/night theme switch).
 */
final class HeroFx {
    private static final int TAG = 0x7e0d0001;

    private HeroFx() {}

    static void ensure(Activity a, View decor) {
        try {
            drift(find(a, decor, "shelf"), 0L);
            drift(find(a, decor, "shelf2"), 4000L);
        } catch (Throwable ignored) {}
    }

    private static ImageView find(Activity a, View decor, String name) {
        int id = a.getResources().getIdentifier(name, "id", a.getPackageName());
        if (id == 0) return null;
        View v = decor.findViewById(id);
        return v instanceof ImageView ? (ImageView) v : null;
    }

    private static void drift(final ImageView iv, long startDelay) {
        if (iv == null || iv.getTag(TAG) != null) return;
        iv.setTag(TAG, Boolean.TRUE);
        loop(iv, "scaleX", 1.02f, 1.12f, 18000, startDelay);
        loop(iv, "scaleY", 1.02f, 1.12f, 18000, startDelay);
        loop(iv, "translationX", -30f, 30f, 24000, startDelay);
        loop(iv, "translationY", -16f, 16f, 21000, startDelay + 2000L);
    }

    private static void loop(ImageView iv, String prop, float from, float to, long dur, long delay) {
        ObjectAnimator oa = ObjectAnimator.ofFloat(iv, prop, from, to);
        oa.setDuration(dur);
        oa.setStartDelay(delay);
        oa.setRepeatCount(ValueAnimator.INFINITE);
        oa.setRepeatMode(ValueAnimator.REVERSE);
        oa.setInterpolator(new AccelerateDecelerateInterpolator());
        oa.start();
    }
}
