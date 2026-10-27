package com.zaru.tvx;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.view.View;
import android.widget.TextView;

/**
 * Control Center sleep timer. The tile cycles Off -> 15 -> 30 -> 60 -> 90 min on OK. When it fires it
 * launches the host's own StandBy screen (com.sid.tvos.StandByActivity) — exactly what his StandBy tile
 * does — so there is nothing to add to any other app. An exact AlarmManager alarm (targetSdk 29 allows
 * it) survives the launcher being killed, and the host holds SYSTEM_ALERT_WINDOW so the alarm may start
 * the StandBy activity even from the background.
 */
final class SleepTimer {
    /** >0 overrides the chosen minutes with this many seconds, only to test the fire path. 0 = normal. */
    private static final int DEBUG_FIRE_SECONDS = 0;

    private static final int[] STEPS = {0, 15, 30, 60, 90};
    private static final int REQ = 47104;
    private static int rowId, valId;

    private SleepTimer() {}

    static void ensure(final Activity a, final View decor) {
        try {
            if (rowId == 0) rowId = a.getResources().getIdentifier("tvxSleepRow", "id", a.getPackageName());
            if (valId == 0) valId = a.getResources().getIdentifier("tvxSleepValue", "id", a.getPackageName());
            if (rowId == 0) return;
            final View row = decor.findViewById(rowId);
            if (row == null) return;
            if (row.getTag(rowId) == null) {
                row.setTag(rowId, Boolean.TRUE);
                row.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        try { cycle(a); refresh(a, decor); } catch (Throwable ignored) {}
                    }
                });
            }
            refresh(a, decor);
        } catch (Throwable ignored) {}
    }

    private static SharedPreferences prefs(Activity a) {
        return a.getSharedPreferences("appearance", 0);
    }

    private static void cycle(Activity a) {
        int cur = prefs(a).getInt("tvx_sleep_min", 0);
        int idx = 0;
        for (int i = 0; i < STEPS.length; i++) if (STEPS[i] == cur) idx = i;
        schedule(a, STEPS[(idx + 1) % STEPS.length]);
    }

    private static void schedule(Activity a, int minutes) {
        try {
            AlarmManager am = (AlarmManager) a.getSystemService(Context.ALARM_SERVICE);
            PendingIntent pi = pendingIntent(a);
            if (am != null) am.cancel(pi);
            SharedPreferences.Editor e = prefs(a).edit();
            if (minutes <= 0 || am == null) {
                e.putInt("tvx_sleep_min", 0).putLong("tvx_sleep_at", 0L).apply();
                return;
            }
            long delay = DEBUG_FIRE_SECONDS > 0 ? DEBUG_FIRE_SECONDS * 1000L : minutes * 60000L;
            long at = System.currentTimeMillis() + delay;
            e.putInt("tvx_sleep_min", minutes).putLong("tvx_sleep_at", at).apply();
            try {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            } catch (Throwable t) {
                am.set(AlarmManager.RTC_WAKEUP, at, pi);
            }
        } catch (Throwable ignored) {}
    }

    private static PendingIntent pendingIntent(Activity a) {
        Intent i = new Intent()
                .setClassName("com.sid.tvos", "com.sid.tvos.StandByActivity")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT
                | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        return PendingIntent.getActivity(a.getApplicationContext(), REQ, i, flags);
    }

    static void refresh(Activity a, View decor) {
        try {
            SharedPreferences p = prefs(a);
            long at = p.getLong("tvx_sleep_at", 0L);
            int min = p.getInt("tvx_sleep_min", 0);
            String label;
            if (at > 0 && at > System.currentTimeMillis()) {
                long rem = (at - System.currentTimeMillis() + 59999L) / 60000L;
                label = rem <= 0 ? "1 min" : rem + " min";
            } else {
                if (min != 0 || at != 0) p.edit().putInt("tvx_sleep_min", 0).putLong("tvx_sleep_at", 0L).apply();
                label = "Off";
            }
            TextView val = valId != 0 ? (TextView) decor.findViewById(valId) : null;
            if (val != null) val.setText(label);
        } catch (Throwable ignored) {}
    }
}
