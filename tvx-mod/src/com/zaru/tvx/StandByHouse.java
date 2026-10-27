package com.zaru.tvx;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.service.dreams.DreamService;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

import java.io.File;

/**
 * His StandBy in the house design (StandByActivity, and ClockDream when StandBy is the screensaver): a photo
 * (standby.jpg pushed to Android/data/com.sid.tvos/files, else the Background picture his settings save)
 * drifting slowly, and on its dark left side a big brushed-brass clock, the date, the weather from his own
 * cache and a month calendar, in the champagne gold of the house's lights. It matches the house scene of
 * the screensaver. With no picture saved his StandBy stays as it is.
 */
final class StandByHouse {
    private StandByHouse() {}

    static void show(Activity a) {
        View v = build(a);
        if (v != null) a.setContentView(v);
    }

    static void show(DreamService d) {
        View v = build(d);
        if (v != null) d.setContentView(v);
    }

    static Typeface font(Context c, String name) {
        try {
            int id = c.getResources().getIdentifier(name, "font", c.getPackageName());
            return id == 0 ? null : c.getResources().getFont(id);
        } catch (Throwable t) {
            return null;
        }
    }

    /** The photo: a standby.jpg pushed into his external files folder (Android/data/com.sid.tvos/files) wins,
     *  then the Background picture his own settings save. */
    private static File picture(Context c) {
        try {
            File ext = c.getExternalFilesDir(null);
            if (ext != null && new File(ext, "standby.jpg").isFile()) return new File(ext, "standby.jpg");
        } catch (Throwable ignored) {}
        File saved = new File(c.getFilesDir(), "cw_background.jpg");
        return saved.isFile() ? saved : null;
    }

    private static View build(Context c) {
        File pic = picture(c);
        if (pic == null) return null;
        DisplayMetrics dm = c.getResources().getDisplayMetrics();
        Bitmap bm = decode(pic, dm.widthPixels, dm.heightPixels);
        if (bm == null) return null;
        float d = dm.density;
        int match = ViewGroup.LayoutParams.MATCH_PARENT, wrap = ViewGroup.LayoutParams.WRAP_CONTENT;

        FrameLayout root = new FrameLayout(c);
        root.setBackgroundColor(Color.BLACK);
        ImageView photo = new ImageView(c);
        photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        photo.setImageBitmap(bm);
        root.addView(photo, new FrameLayout.LayoutParams(match, match));
        drift(photo);

        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        final TextClock big = new TextClock(c);
        big.setFormat12Hour("h:mm");
        big.setFormat24Hour("H:mm");
        big.setTextColor(StandByCalendar.CHAMPAGNE);
        big.setTextSize(TypedValue.COMPLEX_UNIT_SP, 100);
        big.setIncludeFontPadding(false);
        big.setShadowLayer(28f, 0f, 3f, 0x99000000);
        big.setTypeface(font(c, "tvx_outfit_extralight"));
        big.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
                // Brushed brass: pale champagne at the top of the digits to warm gold at the foot.
                if (b - t > 0) big.getPaint().setShader(new LinearGradient(0, 0, 0, b - t,
                        0xFFFBEFD5, 0xFFDDAE62, Shader.TileMode.CLAMP));
            }
        });
        TextClock day = new TextClock(c);
        day.setFormat12Hour("EEEE, d MMMM");
        day.setFormat24Hour("EEEE, d MMMM");
        day.setTextColor(0xD9F5E1B8);
        day.setTextSize(TypedValue.COMPLEX_UNIT_SP, 21);
        day.setShadowLayer(14f, 0f, 1f, 0x99000000);
        day.setTypeface(font(c, "tvx_outfit_light"));
        col.addView(big);
        col.addView(day);
        TextView weather = weather(c, d);
        if (weather != null) col.addView(weather);
        LinearLayout.LayoutParams calp = new LinearLayout.LayoutParams(wrap, wrap);
        calp.topMargin = (int) (28 * d);
        col.addView(new StandByCalendar(c), calp);
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(wrap, wrap, Gravity.START | Gravity.CENTER_VERTICAL);
        plp.setMargins((int) (80 * d), 0, 0, 0);
        root.addView(col, plp);

        root.setAlpha(0f);
        root.animate().alpha(1f).setDuration(600).start();
        return root;
    }

    /** His weather cache (prefs "weather", Open-Meteo WMO codes): temperature, sky, today's high and low. */
    private static TextView weather(Context c, float d) {
        SharedPreferences w = c.getSharedPreferences("weather", 0);
        if (!w.contains("temp")) return null;
        int code = w.getInt("code", -1), high = w.getInt("high", 0), low = w.getInt("low", 0);
        String deg = "°";
        TextView tv = new TextView(c);
        // His cache only carries the day's high and low after a full forecast; leave them out until it does.
        tv.setText(w.getInt("temp", 0) + deg + "  " + condition(code)
                + (high == 0 && low == 0 ? "" : "    H " + high + deg + "   L " + low + deg));
        tv.setTextColor(0xB3F5E1B8);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        tv.setShadowLayer(12f, 0f, 1f, 0xB3000000);
        tv.setIncludeFontPadding(false);
        tv.setPadding(0, (int) (8 * d), 0, 0);
        tv.setTypeface(font(c, "tvx_outfit_light"));
        int icon = c.getResources().getIdentifier(icon(code, w.getBoolean("day", true)), "drawable", c.getPackageName());
        if (icon != 0) {
            tv.setCompoundDrawablesWithIntrinsicBounds(icon, 0, 0, 0);
            tv.setCompoundDrawablePadding((int) (10 * d));
            tv.setCompoundDrawableTintList(ColorStateList.valueOf(0xCCF5E1B8));
        }
        return tv;
    }

    private static String condition(int code) {
        if (code == 0) return "Clear";
        if (code == 1) return "Mainly clear";
        if (code == 2) return "Partly cloudy";
        if (code == 3) return "Overcast";
        if (code == 45 || code == 48) return "Fog";
        if (code >= 51 && code <= 57) return "Drizzle";
        if (code >= 61 && code <= 67) return "Rain";
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return "Snow";
        if (code >= 80 && code <= 82) return "Showers";
        if (code >= 95) return "Thunderstorm";
        return "";
    }

    /** The same thin outline icons the screensaver uses (tvx_wx_*, Lucide shapes). */
    private static String icon(int code, boolean day) {
        if (code == 0) return day ? "tvx_wx_sun" : "tvx_wx_moon";
        if (code == 1 || code == 2) return day ? "tvx_wx_cloud_sun" : "tvx_wx_cloud_moon";
        if (code == 45 || code == 48) return "tvx_wx_fog";
        if (code >= 51 && code <= 57) return "tvx_wx_drizzle";
        if ((code >= 61 && code <= 67) || (code >= 80 && code <= 82)) return "tvx_wx_rain";
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return "tvx_wx_snow";
        if (code >= 95) return "tvx_wx_storm";
        return "tvx_wx_cloud";
    }

    /** Decoded no larger than the screen needs, the way his upload stores it. */
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

    /** StandBy can stay up for hours, so a slow drift that never stops, gentle enough not to distract. */
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
