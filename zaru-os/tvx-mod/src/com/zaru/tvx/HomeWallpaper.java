package com.zaru.tvx;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageView;

import java.io.File;

/**
 * Finishes his "Background: Picture" option. His settings already save the chosen picture (Appearance >
 * Continue Watching > Background > Picture, or Send from Phone) as files/cw_background.jpg, but 2.0.1 never
 * draws it. With the shelf Off and Background on Picture, this lays that picture full-screen just above his
 * backdrop group (shelfGroup), under his scrim, dock and status bar, with a slow drift like the living hero.
 * Any other setting removes it, and a new upload is picked up from the file's timestamp.
 */
final class HomeWallpaper {
    private static final String TAG = "tvxWall";
    private static long shownStamp;

    private HomeWallpaper() {}

    static void ensure(Activity a, View decor) {
        try {
            View wall = decor.findViewWithTag(TAG);
            File pic = new File(a.getFilesDir(), "cw_background.jpg");
            if (!wanted(a) || !pic.isFile()) {
                if (wall != null) ((ViewGroup) wall.getParent()).removeView(wall);
                return;
            }
            // Sit just above his shelfGroup, not inside it: he cross-fades shelf and shelf2 by bringing the
            // incoming one to the front of that group, which would bury anything placed in there.
            View group = find(a, decor, "shelfGroup");
            if (group == null) {
                View shelf = find(a, decor, "shelf");
                if (shelf != null && shelf.getParent() instanceof View) group = (View) shelf.getParent();
            }
            if (group == null || !(group.getParent() instanceof ViewGroup)) return;
            ViewGroup parent = (ViewGroup) group.getParent();

            ImageView iv;
            if (wall instanceof ImageView && wall.getParent() == parent) {
                iv = (ImageView) wall;
            } else {
                if (wall != null) ((ViewGroup) wall.getParent()).removeView(wall);
                iv = new ImageView(a);
                iv.setTag(TAG);
                iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                iv.setFocusable(false);
                iv.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                parent.addView(iv, parent.indexOfChild(group) + 1, copyParams(group.getLayoutParams()));
                drift(iv);
                shownStamp = 0;
            }
            long stamp = pic.lastModified() * 31 + pic.length();
            if (stamp != shownStamp) {
                Bitmap bm = decode(pic, decor.getWidth(), decor.getHeight());
                if (bm != null) {
                    iv.setImageBitmap(bm);
                    shownStamp = stamp;
                }
            }
        } catch (Throwable ignored) {}
    }

    /** His own settings decide: shelf content Off and Background = Picture (cw_bg 3). */
    private static boolean wanted(Activity a) {
        SharedPreferences ap = a.getSharedPreferences("appearance", 0);
        String mode = ap.getString("shelf_content_mode", null);
        if (mode == null) mode = ap.getBoolean("cw_on", true) ? "app_cw" : "off";
        return "off".equals(mode) && ap.getInt("cw_bg", 0) == 3;
    }

    private static View find(Activity a, View decor, String name) {
        int id = a.getResources().getIdentifier(name, "id", a.getPackageName());
        return id == 0 ? null : decor.findViewById(id);
    }

    /** The same kind of LayoutParams as his backdrop (FrameLayout, ConstraintLayout, ...), so it fills the same box. */
    private static ViewGroup.LayoutParams copyParams(ViewGroup.LayoutParams lp) {
        try {
            return lp.getClass().getConstructor(lp.getClass()).newInstance(lp);
        } catch (Throwable t) {
            return new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
    }

    /** Decoded no larger than needed for the screen, the same way his upload stores it. */
    private static Bitmap decode(File f, int w, int h) {
        if (w <= 0 || h <= 0) { w = 1920; h = 1080; }
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getPath(), bounds);
        if (bounds.outWidth <= 0) return null;
        int sample = 1;
        while (bounds.outWidth / (sample * 2) >= w && bounds.outHeight / (sample * 2) >= h) sample *= 2;
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        return BitmapFactory.decodeFile(f.getPath(), opts);
    }

    /** A gentler version of the living-hero drift; scale stays above 1.03 so no edge ever shows. */
    private static void drift(ImageView iv) {
        loop(iv, "scaleX", 1.03f, 1.07f, 26000, 0);
        loop(iv, "scaleY", 1.03f, 1.07f, 26000, 0);
        loop(iv, "translationX", -14f, 14f, 32000, 0);
        loop(iv, "translationY", -8f, 8f, 29000, 3000);
    }

    private static void loop(View v, String prop, float from, float to, long dur, long delay) {
        ObjectAnimator oa = ObjectAnimator.ofFloat(v, prop, from, to);
        oa.setDuration(dur);
        oa.setStartDelay(delay);
        oa.setRepeatCount(ValueAnimator.INFINITE);
        oa.setRepeatMode(ValueAnimator.REVERSE);
        oa.setInterpolator(new AccelerateDecelerateInterpolator());
        oa.start();
    }
}
