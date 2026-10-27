package com.zaru.tvx;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.List;

/**
 * Control Center additions, found in control_center.xml by id and wired defensively:
 *   - a volume slider bound to the media stream (D-pad left/right, re-synced when the panel opens),
 *   - a Now Playing strip that appears only while something is playing.
 */
final class CcExtras {
    private static int volId, pctId, nowRowId, nowTitleId, nowArtistId;
    private static boolean wired;

    static void ensure(Activity a, View decor) {
        volume(a, decor);
        nowPlaying(a, decor);
    }

    private static int id(Activity a, String name) {
        return a.getResources().getIdentifier(name, "id", a.getPackageName());
    }

    private static void volume(Activity a, View decor) {
        try {
            if (volId == 0) volId = id(a, "tvxVol");
            if (pctId == 0) pctId = id(a, "tvxVolPct");
            if (volId == 0) return;
            View v = decor.findViewById(volId);
            if (!(v instanceof SeekBar)) { wired = false; return; }
            final SeekBar sb = (SeekBar) v;
            final AudioManager am = (AudioManager) a.getSystemService(Context.AUDIO_SERVICE);
            if (am == null) return;
            final int max = Math.max(1, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
            final TextView pct = pctId != 0 ? (TextView) decor.findViewById(pctId) : null;

            if (!wired) {
                sb.setMax(max);
                sb.setKeyProgressIncrement(Math.max(1, max / 25));
                sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                    @Override public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                        if (fromUser) {
                            // The launcher's volume HUD follows every volume change; tell it this one is the
                            // slider, so it does not pop up over the Control Center while you drag.
                            try {
                                s.getContext().sendBroadcast(new Intent("com.gathin.tvhome.VOLUME_HUD_QUIET")
                                        .setPackage("com.gathin.tvhome"));
                            } catch (Throwable ignored) {}
                            try { am.setStreamVolume(AudioManager.STREAM_MUSIC, Math.max(0, Math.min(max, p)), 0); }
                            catch (Throwable ignored) {}
                        }
                        if (pct != null) pct.setText(Math.round(p * 100f / max) + "%");
                    }
                    @Override public void onStartTrackingTouch(SeekBar s) {}
                    @Override public void onStopTrackingTouch(SeekBar s) {}
                });
                wired = true;
            }

            if (!sb.isFocused()) {
                int cur = Math.max(0, Math.min(max, am.getStreamVolume(AudioManager.STREAM_MUSIC)));
                sb.setProgress(cur);
                if (pct != null) pct.setText(Math.round(cur * 100f / max) + "%");
            }
        } catch (Throwable ignored) {}
    }

    private static void nowPlaying(Activity a, View decor) {
        try {
            if (nowRowId == 0) nowRowId = id(a, "tvxNowRow");
            if (nowRowId == 0) return;
            View row = decor.findViewById(nowRowId);
            if (row == null) return;
            if (nowTitleId == 0) nowTitleId = id(a, "tvxNowTitle");
            if (nowArtistId == 0) nowArtistId = id(a, "tvxNowArtist");

            MediaController best = null;
            try {
                MediaSessionManager msm = (MediaSessionManager) a.getSystemService(Context.MEDIA_SESSION_SERVICE);
                List<MediaController> cs = msm.getActiveSessions(new ComponentName(a, "com.sid.tvos.NotifListener"));
                for (MediaController c : cs) {
                    if (c == null || c.getMetadata() == null) continue;
                    PlaybackState ps = c.getPlaybackState();
                    if (ps != null && ps.getState() == PlaybackState.STATE_PLAYING) { best = c; break; }
                    if (best == null) best = c; // fall back to any session with metadata
                }
            } catch (Throwable ignored) {}

            if (best == null || best.getMetadata() == null) { row.setVisibility(View.GONE); return; }
            MediaMetadata m = best.getMetadata();
            String title = m.getString(MediaMetadata.METADATA_KEY_TITLE);
            String sub = m.getString(MediaMetadata.METADATA_KEY_ARTIST);
            if (sub == null || sub.isEmpty()) sub = m.getString(MediaMetadata.METADATA_KEY_ALBUM);
            if (sub == null || sub.isEmpty()) sub = best.getPackageName();
            if (title == null || title.isEmpty()) { row.setVisibility(View.GONE); return; }

            TextView t = nowTitleId != 0 ? (TextView) decor.findViewById(nowTitleId) : null;
            TextView s = nowArtistId != 0 ? (TextView) decor.findViewById(nowArtistId) : null;
            if (t != null) t.setText(title);
            if (s != null) s.setText(sub);
            row.setVisibility(View.VISIBLE);
        } catch (Throwable ignored) {
            try {
                View row = decor.findViewById(nowRowId);
                if (row != null) row.setVisibility(View.GONE);
            } catch (Throwable ignored2) {}
        }
    }
}
