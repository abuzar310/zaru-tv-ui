package com.gathin.tvhome;

import android.graphics.Color;
import android.media.MediaPlayer;
import android.net.Uri;
import android.service.dreams.DreamService;
import android.view.Gravity;
import android.widget.FrameLayout;
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
        setContentView(root);

        vv.setVideoURI(Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.aerial_loop));
        vv.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override public void onPrepared(MediaPlayer mp) {
                try {
                    mp.setLooping(true);
                    mp.setVolume(0f, 0f);
                    mp.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING);
                } catch (Throwable ignored) {}
                vv.start();
            }
        });
        vv.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override public boolean onError(MediaPlayer mp, int what, int extra) {
                finish();
                return true;
            }
        });
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
