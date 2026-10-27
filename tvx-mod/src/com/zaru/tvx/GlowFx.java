package com.zaru.tvx;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Ambient glow: samples the hero backdrop's dominant colour every couple of seconds and feeds it to a
 * GlowView laid over the whole screen, so the home glows with whatever is featured and shifts as the
 * shelf changes. Added once; the sampling loop is self-cancelling-safe (it just re-posts).
 */
final class GlowFx {
    private static GlowView glow;
    private static boolean looping;
    private static int shelfId, shelf2Id, nameId;
    /** While a tile is focused, glow its brand colour and hold off the hero sampling until this time. */
    private static long brandUntil;

    /** App label (lower-case, substring-matched) -> a vivid brand colour for the ambient glow. */
    private static final Map<String, Integer> BRANDS = new LinkedHashMap<String, Integer>();
    static {
        BRANDS.put("netflix", 0xE50914);
        BRANDS.put("prime", 0x00A8E1);
        BRANDS.put("hotstar", 0x7B2FF7);
        BRANDS.put("jiohotstar", 0x7B2FF7);
        BRANDS.put("sony", 0x6C2BD9);
        BRANDS.put("netmirror", 0xE23744);
        BRANDS.put("youtube", 0xFF0033);
        BRANDS.put("spotify", 0x1DB954);
        BRANDS.put("mx player", 0x2196F3);
        BRANDS.put("play store", 0x34A853);
        BRANDS.put("play games", 0x5B8DEF);
        BRANDS.put("play movies", 0xE8402E);
        BRANDS.put("alexa", 0x00CAFF);
        BRANDS.put("kodi", 0x17B2E7);
        BRANDS.put("disney", 0x1A48C4);
        BRANDS.put("zee", 0x6E34C9);
        BRANDS.put("music", 0xF23A5C);
    }

    private GlowFx() {}

    /** Focus moved: if it landed on an app tile we know, glow that app's colour for a few seconds. */
    static void onFocus(Activity a, View newV) {
        try {
            int c = brandOf(a, newV);
            if (c != 0 && glow != null) {
                glow.setGlow(c);
                brandUntil = System.currentTimeMillis() + 5000L;
            }
        } catch (Throwable ignored) {}
    }

    private static int brandOf(Activity a, View v) {
        if (v == null) return 0;
        if (nameId == 0) nameId = a.getResources().getIdentifier("name", "id", a.getPackageName());
        CharSequence label = null;
        try {
            if (nameId != 0) {
                View n = v.findViewById(nameId);
                if (n instanceof TextView) label = ((TextView) n).getText();
            }
        } catch (Throwable ignored) {}
        if (label == null || label.length() == 0) return 0;
        String s = label.toString().trim().toLowerCase();
        for (Map.Entry<String, Integer> e : BRANDS.entrySet()) {
            if (s.contains(e.getKey())) return e.getValue();
        }
        return 0;
    }

    static void ensure(final Activity a, final View decor) {
        try {
            if (glow == null) {
                ViewGroup content = (ViewGroup) decor.findViewById(android.R.id.content);
                if (content == null) return;
                glow = new GlowView(a);
                content.addView(glow, 0, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                // index 0 keeps it behind the app's own views where possible; it is transparent anyway.
            }
            if (!looping) {
                looping = true;
                loop(a, decor);
            }
        } catch (Throwable ignored) {}
    }

    private static void loop(final Activity a, final View decor) {
        try {
            // Leave a focused app's brand glow alone until it expires, then drift back to the hero colour.
            if (System.currentTimeMillis() >= brandUntil) {
                int c = dominant(image(a, decor, "shelf"));
                if (c == 0) c = dominant(image(a, decor, "shelf2"));
                if (glow != null && c != 0) glow.setGlow(c);
            }
        } catch (Throwable ignored) {}
        decor.postDelayed(new Runnable() {
            @Override public void run() { loop(a, decor); }
        }, 2300);
    }

    private static ImageView image(Activity a, View decor, String name) {
        int id = "shelf".equals(name)
                ? (shelfId != 0 ? shelfId : (shelfId = a.getResources().getIdentifier("shelf", "id", a.getPackageName())))
                : (shelf2Id != 0 ? shelf2Id : (shelf2Id = a.getResources().getIdentifier("shelf2", "id", a.getPackageName())));
        if (id == 0) return null;
        View v = decor.findViewById(id);
        return v instanceof ImageView ? (ImageView) v : null;
    }

    /** A vivid representative colour of the image: the most saturated of a downscaled grid, else the mean. */
    private static int dominant(ImageView iv) {
        try {
            if (iv == null || iv.getAlpha() < 0.2f) return 0;
            Drawable d = iv.getDrawable();
            if (!(d instanceof BitmapDrawable)) return 0;
            Bitmap bm = ((BitmapDrawable) d).getBitmap();
            if (bm == null || bm.isRecycled()) return 0;
            Bitmap s = Bitmap.createScaledBitmap(bm, 14, 8, true);
            long r = 0, g = 0, b = 0; int n = 0;
            float bestSat = 0f; int best = 0;
            for (int y = 0; y < s.getHeight(); y++) {
                for (int x = 0; x < s.getWidth(); x++) {
                    int p = s.getPixel(x, y);
                    int rr = (p >> 16) & 255, gg = (p >> 8) & 255, bb = p & 255;
                    int mx = Math.max(rr, Math.max(gg, bb)), mn = Math.min(rr, Math.min(gg, bb));
                    float sat = mx == 0 ? 0 : (float) (mx - mn) / mx;
                    r += rr; g += gg; b += bb; n++;
                    if (sat > bestSat && mx > 70 && mx < 250) { bestSat = sat; best = p; }
                }
            }
            s.recycle();
            if (best != 0 && bestSat > 0.3f) return best & 0xFFFFFF;
            if (n == 0) return 0;
            int mr = (int) (r / n), mg = (int) (g / n), mb = (int) (b / n);
            if (mr + mg + mb < 60) return 0;  // nearly black -> no glow
            return (mr << 16) | (mg << 8) | mb;
        } catch (Throwable t) {
            return 0;
        }
    }
}
