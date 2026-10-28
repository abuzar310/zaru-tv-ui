package com.zaru.tv;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * The glass widgets' weather card: the temperature now, the sky and today's high and low for one place, from
 * Open-Meteo (free, no key, no account). The place is fixed in config.xml (weather_lat, weather_lon, weather_city),
 * so the launcher needs no location permission for it. Read on a worker thread when Home shows and the last reading
 * is more than half an hour old; the last reading is kept, so the card fills in at once after a restart.
 */
final class Weather {
    private static final String TAG = "TvHome.Weather";
    private static final String PREFS = "weather";
    private static final long MAX_AGE_MS = 30 * 60 * 1000L;

    interface Listener {
        void onWeather(Reading reading);
    }

    static final class Reading {
        final double temp, high, low;
        final int code;
        final boolean day;

        Reading(double temp, double high, double low, int code, boolean day) {
            this.temp = temp;
            this.high = high;
            this.low = low;
            this.code = code;
            this.day = day;
        }
    }

    private final SharedPreferences prefs;
    private final String url;
    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean busy;

    Weather(Context context, String lat, String lon) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        url = "https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon
                + "&current=temperature_2m,weather_code,is_day&daily=temperature_2m_max,temperature_2m_min"
                + "&timezone=auto&forecast_days=1";
    }

    /** The last reading, or null before the first one. */
    Reading cached() {
        String json = prefs.getString("last", null);
        try {
            return json == null ? null : parse(json);
        } catch (JSONException e) {
            return null;
        }
    }

    /** Reads the weather again when the last reading is old; the listener runs on the main thread. */
    void refresh(Listener listener) {
        if (busy || System.currentTimeMillis() - prefs.getLong("at", 0) < MAX_AGE_MS) return;
        busy = true;
        new Thread(() -> {
            Reading reading = null;
            try {
                String json = get(url);
                reading = parse(json);
                prefs.edit().putString("last", json).putLong("at", System.currentTimeMillis()).apply();
            } catch (IOException | JSONException e) {
                Log.w(TAG, "no weather: " + e.getMessage());
            }
            final Reading result = reading;
            main.post(() -> {
                busy = false;
                if (result != null) listener.onWeather(result);
            });
        }, "weather").start();
    }

    private static Reading parse(String json) throws JSONException {
        JSONObject root = new JSONObject(json);
        JSONObject now = root.getJSONObject("current");
        JSONObject daily = root.getJSONObject("daily");
        return new Reading(now.getDouble("temperature_2m"), daily.getJSONArray("temperature_2m_max").getDouble(0),
                daily.getJSONArray("temperature_2m_min").getDouble(0), now.getInt("weather_code"),
                now.optInt("is_day", 1) == 1);
    }

    private static String get(String address) throws IOException {
        HttpURLConnection http = (HttpURLConnection) new URL(address).openConnection();
        http.setConnectTimeout(8000);
        http.setReadTimeout(10000);
        try (InputStream in = http.getInputStream()) {
            if (http.getResponseCode() != 200) throw new IOException("HTTP " + http.getResponseCode());
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toString(StandardCharsets.UTF_8.name());
        } finally {
            http.disconnect();
        }
    }

    /** The WMO weather code as one emoji, which the TV's colour emoji font draws. */
    static String icon(int code, boolean day) {
        if (code == 0) return day ? "☀️" : "🌙";
        if (code == 1) return day ? "🌤️" : "🌙";
        if (code == 2) return day ? "⛅" : "☁️";
        if (code == 3) return "☁️";
        if (code == 45 || code == 48) return "🌫️";
        if (code >= 51 && code <= 57) return "🌦️";
        if (code >= 61 && code <= 67) return "🌧️";
        if (code >= 71 && code <= 77) return "❄️";
        if (code >= 80 && code <= 82) return "🌦️";
        if (code == 85 || code == 86) return "🌨️";
        if (code >= 95) return "⛈️";
        return "🌡️";
    }

    /** The WMO weather code as one of the thin outline icons the vibe home uses (Lucide shapes, 1.6 stroke). */
    static int lineIcon(int code, boolean day) {
        if (code == 0) return day ? R.drawable.wx_sun : R.drawable.wx_moon;
        if (code == 1 || code == 2) return day ? R.drawable.wx_cloud_sun : R.drawable.wx_cloud_moon;
        if (code == 3) return R.drawable.wx_cloud;
        if (code == 45 || code == 48) return R.drawable.wx_fog;
        if (code >= 51 && code <= 57) return R.drawable.wx_drizzle;
        if ((code >= 61 && code <= 67) || (code >= 80 && code <= 82)) return R.drawable.wx_rain;
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return R.drawable.wx_snow;
        if (code >= 95) return R.drawable.wx_storm;
        return R.drawable.wx_cloud;
    }

    /** The WMO weather code in words. */
    static int condition(int code) {
        if (code == 0) return R.string.weather_clear;
        if (code == 1) return R.string.weather_mainly_clear;
        if (code == 2) return R.string.weather_partly_cloudy;
        if (code == 3) return R.string.weather_overcast;
        if (code == 45 || code == 48) return R.string.weather_fog;
        if (code >= 51 && code <= 57) return R.string.weather_drizzle;
        if (code >= 61 && code <= 67) return R.string.weather_rain;
        if (code >= 71 && code <= 77) return R.string.weather_snow;
        if (code >= 80 && code <= 82) return R.string.weather_showers;
        if (code == 85 || code == 86) return R.string.weather_snow;
        if (code >= 95) return R.string.weather_thunderstorm;
        return R.string.weather_unknown;
    }
}
