package com.zaru.tv;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.media.MediaPlayer;
import android.net.Uri;
import android.service.dreams.DreamService;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.VideoView;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * An Apple-TV-style screensaver built around your own photos. Any picture in the app's screensaver folder
 * (Android/data/com.zaru.tv/files/screensaver/, put there with adb push) is shown StandBy-style: the
 * photo with a slow push-in, and on its dark left side a big brass clock, the date, the weather and a month
 * calendar in the same champagne gold. Between photos one muted aerial clip plays (res/raw/aerial_loop*.mp4)
 * under a small white clock, and every change dissolves through black. With the folder empty it is the plain
 * aerial screensaver. Set it as the active dream with:
 *   settings put secure screensaver_components com.zaru.tv/.AerialDreamService
 * The clips are 720p H.264, gentle on a 1 GB device; the decoder is stopped while a photo shows and released
 * when the dream detaches, so nothing is left running.
 */
public class AerialDreamService extends DreamService {
    private static final long PHOTO_MS = 45000;

    private VideoView video;
    private ImageView photo;      // your pictures, between the video and the black layer
    private View fade;            // black layer above the picture, below the clocks, for dip-to-black changes
    private View aerialClock;     // small white clock, date and weather for the clips
    private View homePanel;       // the StandBy column for the photos
    private final int[] clips = { R.raw.aerial_loop, R.raw.aerial_loop2, R.raw.aerial_loop3 };
    /** One cycle: each photo (File) followed by a clip (Integer res id), clips in a shuffled order. */
    private final List<Object> scenes = new ArrayList<Object>();
    private int scene = -1;
    private int failures;
    private AnimatorSet motion;
    private Bitmap shown;
    private final Runnable nextScene = new Runnable() {
        @Override public void run() { next(); }
    };

    private Uri clipUri(int resId) {
        return Uri.parse("android.resource://" + getPackageName() + "/" + resId);
    }

    private void showWeather(TextView tv, Weather.Reading r, int iconTint) {
        if (tv == null || r == null) return;
        try {
            float d = getResources().getDisplayMetrics().density;
            tv.setText(Math.round(r.temp) + "°  " + getString(Weather.condition(r.code))
                    + "    H " + Math.round(r.high) + "°   L " + Math.round(r.low) + "°");
            tv.setCompoundDrawablesWithIntrinsicBounds(Weather.lineIcon(r.code, r.day), 0, 0, 0);
            tv.setCompoundDrawablePadding((int) (10 * d));
            tv.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(iconTint));
            tv.setVisibility(View.VISIBLE);
        } catch (Throwable ignored) {}
    }

    private TextView weatherLine(int color, float sizeSp, float topDp) {
        TextView tv = new TextView(this);
        tv.setTextColor(color);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        tv.setShadowLayer(12f, 0f, 1f, 0xB3000000);
        tv.setIncludeFontPadding(false);
        tv.setPadding(0, (int) (topDp * getResources().getDisplayMetrics().density), 0, 0);
        tv.setVisibility(View.GONE);
        try { tv.setTypeface(getResources().getFont(R.font.outfit_light)); } catch (Throwable ignored) {}
        return tv;
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);   // any remote/touch wakes and exits
        setFullscreen(true);
        setScreenBright(true);

        final VideoView vv = new VideoView(this);
        video = vv;
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        root.addView(vv, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER));

        photo = new ImageView(this);
        photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        photo.setVisibility(View.GONE);
        root.addView(photo, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // A black layer over the picture (under the clocks) that dips in and out when the scene changes, so
        // scenes dissolve through black like Apple's aerials instead of cutting hard.
        fade = new View(this);
        fade.setBackgroundColor(Color.BLACK);
        fade.setAlpha(0f);
        root.addView(fade, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // Clips: a soft white clock bottom-left, like Apple's aerials.
        float d = getResources().getDisplayMetrics().density;
        LinearLayout clockBox = new LinearLayout(this);
        clockBox.setOrientation(LinearLayout.VERTICAL);
        TextClock time = new TextClock(this);
        time.setFormat12Hour("h:mm");
        time.setFormat24Hour("H:mm");
        time.setTextColor(0xFFFFFFFF);
        time.setTextSize(TypedValue.COMPLEX_UNIT_SP, 76);
        time.setShadowLayer(18f, 0f, 2f, 0xB3000000);
        time.setIncludeFontPadding(false);
        try { time.setTypeface(getResources().getFont(R.font.outfit_extralight)); } catch (Throwable ignored) {}
        TextClock date = new TextClock(this);
        date.setFormat12Hour("EEEE, MMMM d");
        date.setFormat24Hour("EEEE, MMMM d");
        date.setTextColor(0xCCFFFFFF);
        date.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        date.setShadowLayer(12f, 0f, 1f, 0xB3000000);
        try { date.setTypeface(getResources().getFont(R.font.outfit_light)); } catch (Throwable ignored) {}
        clockBox.addView(time);
        clockBox.addView(date);
        final TextView weather = weatherLine(0xCCFFFFFF, 18, 6);
        clockBox.addView(weather);
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.START);
        clp.setMargins((int) (72 * d), 0, 0, (int) (60 * d));
        root.addView(clockBox, clp);
        aerialClock = clockBox;

        // Photos: a StandBy column on the dark left, in the champagne gold of the house's lights and brass.
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        final TextClock big = new TextClock(this);
        big.setFormat12Hour("h:mm");
        big.setFormat24Hour("H:mm");
        big.setTextColor(HomeCalendarView.CHAMPAGNE);
        big.setTextSize(TypedValue.COMPLEX_UNIT_SP, 100);
        big.setIncludeFontPadding(false);
        big.setShadowLayer(28f, 0f, 3f, 0x99000000);
        try { big.setTypeface(getResources().getFont(R.font.outfit_extralight)); } catch (Throwable ignored) {}
        big.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
                // Brushed brass: pale champagne at the top of the digits to warm gold at the foot.
                if (b - t > 0) big.getPaint().setShader(new LinearGradient(0, 0, 0, b - t,
                        0xFFFBEFD5, 0xFFDDAE62, Shader.TileMode.CLAMP));
            }
        });
        TextClock day = new TextClock(this);
        day.setFormat12Hour("EEEE, d MMMM");
        day.setFormat24Hour("EEEE, d MMMM");
        day.setTextColor(0xD9F5E1B8);
        day.setTextSize(TypedValue.COMPLEX_UNIT_SP, 21);
        day.setShadowLayer(14f, 0f, 1f, 0x99000000);
        try { day.setTypeface(getResources().getFont(R.font.outfit_light)); } catch (Throwable ignored) {}
        final TextView homeWeather = weatherLine(0xB3F5E1B8, 17, 8);
        panel.addView(big);
        panel.addView(day);
        panel.addView(homeWeather);
        LinearLayout.LayoutParams calp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        calp.topMargin = (int) (28 * d);
        panel.addView(new HomeCalendarView(this), calp);
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.START | Gravity.CENTER_VERTICAL);
        plp.setMargins((int) (80 * d), 0, 0, 0);
        panel.setVisibility(View.GONE);
        root.addView(panel, plp);
        homePanel = panel;

        // Weather for both: our launcher's cache is usually empty (his launcher is Home, not ours), so fetch
        // it; if there is a cached reading, show it first.
        try {
            String lat = getString(R.string.weather_lat), lon = getString(R.string.weather_lon);
            if (!lat.isEmpty() && !lon.isEmpty()) {
                Weather src = new Weather(this, lat, lon);
                showWeather(weather, src.cached(), 0xE6FFFFFF);
                showWeather(homeWeather, src.cached(), 0xCCF5E1B8);
                src.refresh(new Weather.Listener() {
                    @Override public void onWeather(final Weather.Reading reading) {
                        weather.post(new Runnable() {
                            @Override public void run() {
                                showWeather(weather, reading, 0xE6FFFFFF);
                                showWeather(homeWeather, reading, 0xCCF5E1B8);
                            }
                        });
                    }
                });
            }
        } catch (Throwable ignored) {}

        setContentView(root);

        // Each photo is followed by one clip, so the photos stay the main thing; clips shuffle each start.
        List<File> pics = photos();
        List<Integer> shuffled = new ArrayList<Integer>();
        for (int c : clips) shuffled.add(c);
        Collections.shuffle(shuffled);
        if (pics.isEmpty()) {
            scenes.addAll(shuffled);
        } else {
            for (int i = 0; i < Math.max(pics.size(), shuffled.size()); i++) {
                scenes.add(pics.get(i % pics.size()));
                if (i < shuffled.size()) scenes.add(shuffled.get(i));
            }
        }

        vv.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override public void onPrepared(MediaPlayer mp) {
                try {
                    mp.setVolume(0f, 0f);
                    mp.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING);
                } catch (Throwable ignored) {}
                vv.start();
                failures = 0;
                lift();   // the next clip is drawing
            }
        });
        vv.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override public void onCompletion(MediaPlayer mp) { next(); }
        });
        vv.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override public boolean onError(MediaPlayer mp, int what, int extra) {
                skip();   // a bad clip moves on rather than ending the dream
                return true;
            }
        });
        scene = 0;
        boolean photoFirst = scenes.get(0) instanceof File;
        homePanel.setVisibility(photoFirst ? View.VISIBLE : View.GONE);
        aerialClock.setVisibility(photoFirst ? View.GONE : View.VISIBLE);
        present(scenes.get(0));
    }

    /** Dip to black, then show the next scene; a clip lifts the black from onPrepared, a photo at once. */
    private void next() {
        if (fade == null || scenes.isEmpty()) return;
        fade.removeCallbacks(nextScene);
        fade.animate().cancel();
        fade.animate().alpha(1f).setDuration(280).withEndAction(new Runnable() {
            @Override public void run() {
                scene = (scene + 1) % scenes.size();
                present(scenes.get(scene));
            }
        }).start();
    }

    private void present(Object s) {
        try {
            if (s instanceof File) {
                Bitmap bm = decode((File) s);
                if (bm == null) { skip(); return; }
                if (video != null) video.stopPlayback();   // no decoder running behind a photo
                clearPhoto();
                shown = bm;
                photo.setImageBitmap(bm);
                photo.setVisibility(View.VISIBLE);
                chrome(homePanel, aerialClock);
                pushIn();
                lift();
                fade.postDelayed(nextScene, PHOTO_MS);
            } else {
                clearPhoto();
                chrome(aerialClock, homePanel);
                video.setVideoURI(clipUri((Integer) s));
            }
        } catch (Throwable t) { finish(); }
    }

    /** Cross-fade the overlays while the screen is black: the StandBy column for photos, the clock for clips. */
    private static void chrome(View in, final View out) {
        if (in == null || out == null) return;
        if (in.getVisibility() == View.VISIBLE && out.getVisibility() != View.VISIBLE) return;
        out.animate().cancel();
        out.animate().alpha(0f).setDuration(280).withEndAction(new Runnable() {
            @Override public void run() { out.setVisibility(View.GONE); }
        }).start();
        in.animate().cancel();
        in.setAlpha(0f);
        in.setVisibility(View.VISIBLE);
        in.animate().alpha(1f).setDuration(600).start();
    }

    /** Move past a scene that will not play; give up if nothing in the cycle works. */
    private void skip() {
        if (++failures > scenes.size()) finish();
        else next();
    }

    private void lift() {
        if (fade != null) fade.animate().alpha(0f).setDuration(600).start();
    }

    /** A slow Ken Burns push-in that drifts a little left, so a subject on the right eases toward the middle. */
    private void pushIn() {
        photo.setScaleX(1f);
        photo.setScaleY(1f);
        photo.setTranslationX(0f);
        motion = new AnimatorSet();
        motion.playTogether(
                ObjectAnimator.ofFloat(photo, "scaleX", 1f, 1.08f),
                ObjectAnimator.ofFloat(photo, "scaleY", 1f, 1.08f),
                ObjectAnimator.ofFloat(photo, "translationX", 0f, -28f));
        motion.setDuration(PHOTO_MS + 1000);
        motion.setInterpolator(new LinearInterpolator());
        motion.start();
    }

    private void clearPhoto() {
        if (motion != null) { motion.cancel(); motion = null; }
        if (photo != null) { photo.setVisibility(View.GONE); photo.setImageDrawable(null); }
        if (shown != null) { shown.recycle(); shown = null; }
    }

    /** Pictures in Android/data/com.zaru.tv/files/screensaver, in name order (01-..., 02-...). */
    private List<File> photos() {
        List<File> out = new ArrayList<File>();
        try {
            File dir = getExternalFilesDir("screensaver");
            File[] files = dir == null ? null : dir.listFiles();
            if (files == null) return out;
            Arrays.sort(files);
            for (File f : files) {
                String n = f.getName().toLowerCase(Locale.ROOT);
                if (f.isFile() && (n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp"))) {
                    out.add(f);
                }
            }
        } catch (Throwable ignored) {}
        return out;
    }

    /** Decoded no larger than the screen needs, so a big phone photo does not cost the 1 GB TV its memory. */
    private Bitmap decode(File f) {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int w = Math.max(dm.widthPixels, 1), h = Math.max(dm.heightPixels, 1);
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

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        boolean onClip = scene >= 0 && scene < scenes.size() && !(scenes.get(scene) instanceof File);
        if (video != null && onClip) video.start();
    }

    @Override
    public void onDreamingStopped() {
        super.onDreamingStopped();
        try { if (video != null) video.pause(); } catch (Throwable ignored) {}
    }

    @Override
    public void onDetachedFromWindow() {
        try { if (fade != null) fade.removeCallbacks(nextScene); } catch (Throwable ignored) {}
        try { if (video != null) { video.stopPlayback(); } } catch (Throwable ignored) {}
        try { clearPhoto(); } catch (Throwable ignored) {}
        video = null;
        super.onDetachedFromWindow();
    }
}
