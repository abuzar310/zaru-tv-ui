package com.gathin.tvhome;

import android.graphics.Color;
import android.media.MediaPlayer;
import android.net.Uri;
import android.service.dreams.DreamService;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.VideoView;

/**
 * An Apple-TV-style aerial screensaver: a muted, looping cinematic clip (res/raw/aerial_loop.mp4) filling
 * the screen. Set it as the active dream with:
 *   settings put secure screensaver_components com.gathin.tvhome/.AerialDreamService
 * The clip is a landscape 16:9 720p H.264 loop, gentle on a 1 GB device; the decoder is released when the
 * dream detaches so nothing is left running.
 */
public class AerialDreamService extends DreamService {

    private VideoView video;
    private final int[] clips = { R.raw.aerial_loop, R.raw.aerial_loop2, R.raw.aerial_loop3 };
    private int clipIdx;

    private Uri clipUri(int resId) {
        return Uri.parse("android.resource://" + getPackageName() + "/" + resId);
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);   // any remote/touch wakes and exits
        setFullscreen(true);
        setScreenBright(true);

        VideoView vv = new VideoView(this);
        video = vv;
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        root.addView(vv, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER));

        // A soft clock over the aerial, like Apple's — the StandBy clock you like, on top of the video.
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
        // Weather, read from the home's cached reading (no network in the dream) — like Apple's aerials.
        try {
            Weather.Reading wr = new Weather(this).cached();
            if (wr != null) {
                TextView weather = new TextView(this);
                weather.setText(Math.round(wr.temp) + "°  " + getString(Weather.condition(wr.code)));
                weather.setTextColor(0xCCFFFFFF);
                weather.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
                weather.setShadowLayer(12f, 0f, 1f, 0xB3000000);
                weather.setIncludeFontPadding(false);
                weather.setPadding(0, (int) (6 * d), 0, 0);
                try { weather.setTypeface(getResources().getFont(R.font.outfit_light)); } catch (Throwable ignored) {}
                clockBox.addView(weather);
            }
        } catch (Throwable ignored) {}
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.START);
        clp.setMargins((int) (72 * d), 0, 0, (int) (60 * d));
        root.addView(clockBox, clp);

        setContentView(root);

        // Shuffle the clips: start on a random one and advance to the next when each finishes, so the
        // screensaver cycles like Apple's aerials instead of looping one shot.
        clipIdx = new java.util.Random().nextInt(clips.length);
        vv.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override public void onPrepared(MediaPlayer mp) {
                try {
                    mp.setVolume(0f, 0f);
                    mp.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING);
                } catch (Throwable ignored) {}
                vv.start();
            }
        });
        vv.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override public void onCompletion(MediaPlayer mp) {
                try {
                    clipIdx = (clipIdx + 1) % clips.length;
                    vv.setVideoURI(clipUri(clips[clipIdx]));
                } catch (Throwable ignored) { finish(); }
            }
        });
        vv.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override public boolean onError(MediaPlayer mp, int what, int extra) {
                // Skip a bad clip to the next rather than ending the dream.
                try {
                    clipIdx = (clipIdx + 1) % clips.length;
                    vv.setVideoURI(clipUri(clips[clipIdx]));
                    return true;
                } catch (Throwable ignored) { finish(); return true; }
            }
        });
        vv.setVideoURI(clipUri(clips[clipIdx]));
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        if (video != null) video.start();
    }

    @Override
    public void onDreamingStopped() {
        super.onDreamingStopped();
        try { if (video != null) video.pause(); } catch (Throwable ignored) {}
    }

    @Override
    public void onDetachedFromWindow() {
        try { if (video != null) { video.stopPlayback(); } } catch (Throwable ignored) {}
        video = null;
        super.onDetachedFromWindow();
    }
}
