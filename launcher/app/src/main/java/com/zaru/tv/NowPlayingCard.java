package com.zaru.tv;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Outline;
import android.graphics.drawable.Drawable;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;

/**
 * The Control Center's Now Playing card: what any app is playing, from its media session, with its artwork and a
 * progress line. OK plays or pauses it; Left and Right skip back or ahead 10 seconds where the app allows seeking.
 * Hidden while nothing plays. Android lists other apps' sessions only to an allowed notification listener, so
 * without MediaListener's pass the card simply stays hidden.
 */
final class NowPlayingCard {
    private static final long SKIP_MS = 10_000;
    private static final long FLASH_MS = 1200;

    private final Activity activity;
    private final View card, bar, fill;
    private final ImageView art, glyph;
    private final TextView title, sub;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ComponentName pass;
    private MediaSessionManager sessions;
    private MediaController controller;
    private MediaMetadata meta;
    private PlaybackState playback;
    private String subText;
    private boolean flashing;

    private final MediaController.Callback watch = new MediaController.Callback() {
        @Override public void onPlaybackStateChanged(PlaybackState state) {
            playback = state;
            showState();
        }

        @Override public void onMetadataChanged(MediaMetadata metadata) {
            meta = metadata;
            showSession();
        }

        @Override public void onSessionDestroyed() {
            choose(null);
        }
    };
    private final MediaSessionManager.OnActiveSessionsChangedListener changed = this::choose;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            showProgress();
            if (isPlaying(playback)) ui.postDelayed(this, 1000);
        }
    };
    private final Runnable unflash = () -> {
        flashing = false;
        showState();
    };

    NowPlayingCard(Activity activity) {
        this.activity = activity;
        card = activity.findViewById(R.id.ccNow);
        art = activity.findViewById(R.id.ccNowArt);
        glyph = activity.findViewById(R.id.ccNowState);
        title = activity.findViewById(R.id.ccNowTitle);
        sub = activity.findViewById(R.id.ccNowSub);
        bar = activity.findViewById(R.id.ccNowBar);
        fill = activity.findViewById(R.id.ccNowFill);
        pass = new ComponentName(activity, MediaListener.class);

        final float radius = 8 * activity.getResources().getDisplayMetrics().density;
        art.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline outline) {
                outline.setRoundRect(0, 0, v.getWidth(), v.getHeight(), radius);
            }
        });
        art.setClipToOutline(true);
        fill.setPivotX(0f);

        card.setOnClickListener(v -> playPause());
        card.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode != KeyEvent.KEYCODE_DPAD_LEFT && keyCode != KeyEvent.KEYCODE_DPAD_RIGHT) return false;
            if (event.getAction() == KeyEvent.ACTION_DOWN) skip(keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ? SKIP_MS : -SKIP_MS);
            return true;
        });

        try {
            sessions = (MediaSessionManager) activity.getSystemService(Context.MEDIA_SESSION_SERVICE);
            sessions.addOnActiveSessionsChangedListener(changed, pass, ui);
            choose(sessions.getActiveSessions(pass));
        } catch (Throwable notAllowed) {
            sessions = null;
            card.setVisibility(View.GONE);
        }
    }

    /** Stops listening; the panel calls this as it closes. */
    void release() {
        ui.removeCallbacksAndMessages(null);
        try { if (sessions != null) sessions.removeOnActiveSessionsChangedListener(changed); } catch (Throwable ignored) {}
        try { if (controller != null) controller.unregisterCallback(watch); } catch (Throwable ignored) {}
        controller = null;
    }

    /** The session to show: the first one playing, else one paused that has a title to show. */
    private void choose(List<MediaController> list) {
        MediaController pick = null;
        try {
            if (list == null && sessions != null) list = sessions.getActiveSessions(pass);
            if (list != null) {
                for (MediaController c : list) {
                    PlaybackState s = c.getPlaybackState();
                    if (isPlaying(s)) { pick = c; break; }
                    if (pick == null && s != null && s.getState() == PlaybackState.STATE_PAUSED
                            && titleOf(c.getMetadata()) != null) pick = c;
                }
            }
        } catch (Throwable ignored) {}
        if (pick != null && controller != null && pick.getSessionToken().equals(controller.getSessionToken())) return;
        try { if (controller != null) controller.unregisterCallback(watch); } catch (Throwable ignored) {}
        controller = pick;
        meta = null;
        playback = null;
        if (pick != null) {
            try {
                meta = pick.getMetadata();
                playback = pick.getPlaybackState();
                pick.registerCallback(watch, ui);
            } catch (Throwable ignored) {}
        }
        showSession();
    }

    /** Title, the line under it and the artwork; then the play state. */
    private void showSession() {
        MediaController c = controller;
        if (c == null) {
            showState();
            return;
        }
        String app = appLabel(c.getPackageName());
        String name = titleOf(meta);
        String line = text(meta, MediaMetadata.METADATA_KEY_ARTIST, MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE,
                MediaMetadata.METADATA_KEY_ALBUM);
        if (name == null) {
            name = app;
            line = null;
        }
        title.setText(name);
        subText = line != null ? line : !name.equals(app) ? app : null;

        Bitmap picture = meta == null ? null : firstBitmap(meta, MediaMetadata.METADATA_KEY_ART,
                MediaMetadata.METADATA_KEY_ALBUM_ART, MediaMetadata.METADATA_KEY_DISPLAY_ICON);
        if (picture != null) {
            art.setImageTintList(null);
            art.setScaleType(ImageView.ScaleType.CENTER_CROP);
            art.setImageBitmap(picture);
        } else {
            Drawable icon = appIcon(c.getPackageName());
            art.setScaleType(ImageView.ScaleType.FIT_CENTER);
            if (icon != null) {
                art.setImageTintList(null);
                art.setImageDrawable(icon);
            } else {
                art.setImageTintList(activity.getColorStateList(R.color.cc_tile_text));
                art.setImageResource(R.drawable.ic_note);
            }
        }
        showState();
    }

    /** Shown while its app plays or is paused; the glyph is what OK will do. */
    private void showState() {
        int s = playback == null ? PlaybackState.STATE_NONE : playback.getState();
        boolean playing = isPlaying(playback);
        if (controller == null || !(playing || s == PlaybackState.STATE_PAUSED)) {
            ui.removeCallbacks(tick);
            card.setVisibility(View.GONE);
            return;
        }
        glyph.setImageResource(playing ? R.drawable.ic_pause : R.drawable.ic_play);
        if (!flashing) {
            sub.setText(subText != null ? subText : activity.getString(playing ? R.string.now_playing : R.string.now_paused));
        }
        card.setVisibility(View.VISIBLE);
        ui.removeCallbacks(tick);
        tick.run();
    }

    /** The thin line under the title: how far in, when the app says how long it is. */
    private void showProgress() {
        long length = duration();
        long at = position();
        if (length <= 0 || at < 0) {
            bar.setVisibility(View.GONE);
            return;
        }
        bar.setVisibility(View.VISIBLE);
        fill.setScaleX(Math.max(0f, Math.min(1f, at / (float) length)));
    }

    private void playPause() {
        MediaController c = controller;
        if (c == null) return;
        boolean playing = isPlaying(playback);
        try {
            if (playing) c.getTransportControls().pause();
            else c.getTransportControls().play();
        } catch (Throwable ignored) {}
        glyph.setImageResource(playing ? R.drawable.ic_play : R.drawable.ic_pause);   // until the app confirms
    }

    private void skip(long delta) {
        MediaController c = controller;
        long at = position();
        if (c == null || at < 0 || playback == null || (playback.getActions() & PlaybackState.ACTION_SEEK_TO) == 0) return;
        long to = Math.max(0, at + delta);
        long length = duration();
        if (length > 0) to = Math.min(length, to);
        try { c.getTransportControls().seekTo(to); } catch (Throwable ignored) { return; }
        flashing = true;
        sub.setText(delta > 0 ? R.string.now_playing_ahead : R.string.now_playing_back);
        ui.removeCallbacks(unflash);
        ui.postDelayed(unflash, FLASH_MS);
    }

    private long duration() {
        return meta == null ? 0 : meta.getLong(MediaMetadata.METADATA_KEY_DURATION);
    }

    /** Where playback is now: the app's last report moved on by the time since, at its speed. */
    private long position() {
        PlaybackState s = playback;
        if (s == null || s.getPosition() < 0) return -1;
        long at = s.getPosition();
        if (s.getState() == PlaybackState.STATE_PLAYING && s.getLastPositionUpdateTime() > 0) {
            at += (long) ((SystemClock.elapsedRealtime() - s.getLastPositionUpdateTime()) * s.getPlaybackSpeed());
        }
        return at;
    }

    private static boolean isPlaying(PlaybackState s) {
        if (s == null) return false;
        int state = s.getState();
        return state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING;
    }

    private static String titleOf(MediaMetadata m) {
        return text(m, MediaMetadata.METADATA_KEY_TITLE, MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
    }

    private static String text(MediaMetadata m, String... keys) {
        if (m == null) return null;
        for (String key : keys) {
            CharSequence value = m.getText(key);
            if (value != null && value.toString().trim().length() > 0) return value.toString().trim();
        }
        return null;
    }

    private static Bitmap firstBitmap(MediaMetadata m, String... keys) {
        for (String key : keys) {
            Bitmap b = m.getBitmap(key);
            if (b != null) return b;
        }
        return null;
    }

    private String appLabel(String pkg) {
        try {
            PackageManager pm = activity.getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (Throwable e) {
            return pkg;
        }
    }

    private Drawable appIcon(String pkg) {
        try {
            return activity.getPackageManager().getApplicationIcon(pkg);
        } catch (Throwable e) {
            return null;
        }
    }
}
