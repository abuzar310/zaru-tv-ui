package com.zaru.tv;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Date;

/**
 * Control Center: the panel the remote's Settings button opens when {@code settings_key_panel} is on
 * (HomeKeyService). A dark glass panel slides in from the right edge with the time, what is playing (Now Playing),
 * the network, Inputs, Sound, Display, the sleep timer, Turn off and wide rows for the TV's settings, over Home or
 * whatever app is playing. Translucent and in a task of its own, so the app behind keeps running and Back returns to
 * it. Pressing Settings again closes the panel. Nothing in it runs while it is closed. Ported from Matchday TV
 * (nahtheking/matchday-tv, MIT).
 */
public final class QuickSettingsActivity extends Activity {

    /** Sent to the TV's own Inputs panel (com.tcl.suspension on TCL Google TV). */
    private static final String ACTION_VIEW_INPUTS = "com.android.tv.action.VIEW_INPUTS";
    /** Android TV's sound panel (com.tcl.settings.SoundActivity on TCL); the phone action opens a full settings page. */
    private static final String ACTION_VIEW_SOUND = "com.android.tv.action.VIEW_SOUND_SETTINGS";

    private LinearLayout tiles;
    private LinearLayout pair;
    private NowPlayingCard nowPlaying;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_quick_settings);
        View scrim = findViewById(R.id.scrim);
        View panel = findViewById(R.id.panel);
        scrim.setOnClickListener(v -> finish());
        panel.setOnClickListener(v -> { });

        boolean is24 = DateFormat.is24HourFormat(this);
        TextClock clock = findViewById(R.id.ccTime);
        clock.setFormat12Hour(is24 ? "HH:mm" : "h:mm");
        clock.setFormat24Hour("HH:mm");
        ((TextView) findViewById(R.id.ccDate)).setText(DateFormat.format(getString(R.string.date_format), new Date()));

        tiles = findViewById(R.id.tiles);
        nowPlaying = new NowPlayingCard(this);
        addTile(R.drawable.ic_wifi, getString(R.string.quick_network), networkStatus(), new Intent(Settings.ACTION_WIFI_SETTINGS), false);
        addTile(R.drawable.ic_input, getString(R.string.inputs), null, new Intent(ACTION_VIEW_INPUTS), false);
        addTile(R.drawable.ic_sound, getString(R.string.quick_sound), volumeStatus(), firstAnswered(new Intent(ACTION_VIEW_SOUND),
                new Intent(Settings.ACTION_SOUND_SETTINGS)), false);
        addTile(R.drawable.ic_picture, getString(R.string.quick_picture), null, new Intent(Settings.ACTION_DISPLAY_SETTINGS), false);
        addSleepTile();
        addPowerTile();
        addTile(R.drawable.ic_settings, getString(R.string.tv_settings), null, new Intent(Settings.ACTION_SETTINGS), true);
        addTile(R.drawable.ic_apps, getString(R.string.quick_apps), getString(R.string.quick_apps_hint),
                new Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS), true);
        // Through Home, so it rebuilds with the new choices when Launcher settings closes.
        addTile(R.drawable.ic_home_settings, getString(R.string.launcher_settings), null,
                new Intent(this, MainActivity.class).setAction(MainActivity.ACTION_LAUNCHER_SETTINGS), true);
        tiles.getChildAt(0).requestFocus();

        // Slide in from the right edge; the scrim fades in with it. One translation of a cached layer.
        panel.setTranslationX(getResources().getDisplayMetrics().density * 320);
        panel.animate().translationX(0f).setDuration(260).setInterpolator(new DecelerateInterpolator(1.6f)).withLayer().start();
        scrim.setAlpha(0f);
        scrim.animate().alpha(1f).setDuration(200).start();
    }

    /** Settings pressed again while the panel is up. */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        if (nowPlaying != null) nowPlaying.release();
        super.onDestroy();
    }

    /** Anything else taking the screen (Home, an app, a screensaver) closes the panel. */
    @Override
    protected void onPause() {
        super.onPause();
        finish();
        overridePendingTransition(0, 0);
    }

    /** The first intent some activity answers, or the last one so the tile still reports "not available". */
    private Intent firstAnswered(Intent... intents) {
        for (Intent i : intents) {
            if (getPackageManager().resolveActivity(i, 0) != null) return i;
        }
        return intents[intents.length - 1];
    }

    /** The network name when Wi-Fi is up, "Ethernet", or offline. Read once, when the panel opens. */
    private String networkStatus() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkCapabilities caps = cm == null ? null : cm.getNetworkCapabilities(cm.getActiveNetwork());
        if (caps == null) return getString(R.string.offline);
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) return getString(R.string.ethernet);
        WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        WifiInfo info = wm == null ? null : wm.getConnectionInfo();
        String ssid = info == null ? null : info.getSSID();
        if (ssid == null || ssid.isEmpty() || "<unknown ssid>".equals(ssid)) return getString(R.string.connected);
        return ssid.startsWith("\"") && ssid.endsWith("\"") && ssid.length() > 1 ? ssid.substring(1, ssid.length() - 1) : ssid;
    }

    /** "Volume 12" for the media stream, read once when the panel opens; null when the TV does not say. */
    private String volumeStatus() {
        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (am == null) return null;
        if (am.isStreamMute(AudioManager.STREAM_MUSIC)) return getString(R.string.off);
        return getString(R.string.quick_volume, am.getStreamVolume(AudioManager.STREAM_MUSIC));
    }

    private View addTile(int icon, String label, String sub, Intent target, boolean wide) {
        View tile = newTile(icon, label, sub, wide);
        tile.setOnClickListener(v -> {
            try {
                startActivity(target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (ActivityNotFoundException e) {
                Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
            }
            finish();
        });
        return tile;
    }

    /** Turn off: an accessibility service puts the TV to sleep, as the remote's power button does: the Sleep row's
     *  own, or HomeKeyService, which is on wherever this panel opens from the remote. */
    private void addPowerTile() {
        View tile = newTile(R.drawable.ic_power, getString(R.string.quick_power), null, false);
        tile.setOnClickListener(v -> {
            finish();
            if (!SleepService.sleep() && !HomeKeyService.sleep()) {
                Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
            }
        });
    }

    /** Sleep timer: each OK steps Off, 15, 30, 60, 90 minutes. The panel stays open to show the choice. */
    private void addSleepTile() {
        View tile = newTile(R.drawable.ic_timer, getString(R.string.sleep_timer), sleepStatus(), false);
        TextView sub = tile.findViewById(R.id.ccSub);
        tile.setOnClickListener(v -> {
            SleepTimer timer = HomeKeyService.sleepTimer();
            if (timer == null) {
                Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
                return;
            }
            timer.next();
            sub.setText(sleepStatus());
        });
    }

    /** "Off", or the minutes left. */
    private String sleepStatus() {
        SleepTimer timer = HomeKeyService.sleepTimer();
        int left = timer == null ? 0 : timer.minutesLeft();
        return left == 0 ? getString(R.string.off) : getString(R.string.sleep_timer_minutes, left);
    }

    /** Square tiles go two to a row; a wide one takes a row of its own. */
    private View newTile(int icon, String label, String sub, boolean wide) {
        float density = getResources().getDisplayMetrics().density;
        int gap = Math.round(10 * density);
        LinearLayout row;
        if (!wide && pair != null && pair.getChildCount() == 1) {
            row = pair;
        } else {
            row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            if (tiles.getChildCount() > 0) lp.topMargin = gap;
            tiles.addView(row, lp);
            pair = wide ? null : row;
        }
        LinearLayout tile = (LinearLayout) getLayoutInflater().inflate(R.layout.item_cc_tile, row, false);
        ((ImageView) tile.findViewById(R.id.ccIcon)).setImageResource(icon);
        ((TextView) tile.findViewById(R.id.ccLabel)).setText(label);
        TextView subView = tile.findViewById(R.id.ccSub);
        if (sub != null) subView.setText(sub);
        else subView.setVisibility(View.GONE);
        if (wide) {
            tile.setOrientation(LinearLayout.HORIZONTAL);
            tile.setGravity(Gravity.CENTER_VERTICAL);
            tile.setMinimumHeight(Math.round(48 * density));
            LinearLayout.LayoutParams textLp = (LinearLayout.LayoutParams) tile.findViewById(R.id.ccText).getLayoutParams();
            textLp.topMargin = 0;
            textLp.setMarginStart(Math.round(14 * density));
        } else {
            // The two squares in a row share one height, icons level at the top.
            tile.setGravity(Gravity.TOP);
            tile.getLayoutParams().height = LinearLayout.LayoutParams.MATCH_PARENT;
        }
        if (row.getChildCount() == 1) ((LinearLayout.LayoutParams) tile.getLayoutParams()).setMarginStart(gap);
        row.addView(tile);
        return tile;
    }
}
