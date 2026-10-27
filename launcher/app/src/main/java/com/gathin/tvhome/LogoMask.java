package com.gathin.tvhome;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;

/**
 * A white logo for an app the vibe dock has no drawn mark for, cut from its own banner: whatever differs from the
 * banner's background colour (read along its edges) becomes white, the rest transparent, cropped to the mark. Works
 * for the usual banner, a wordmark on a flat colour; returns null when the result is empty or covers most of the
 * banner (a photo, a gradient), and the dock shows the app's name instead.
 */
final class LogoMask {
    private static final int W = 320, H = 180;

    private LogoMask() {
    }

    /** The silhouette, plus its average colour (the brand colour for the glow) in {@code colorOut[0]}. */
    static Bitmap from(Drawable banner, int[] colorOut) {
        if (banner == null) return null;
        Bitmap src = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Drawable d = banner.mutate();
        d.setBounds(0, 0, W, H);
        d.draw(new Canvas(src));
        int[] px = new int[W * H];
        src.getPixels(px, 0, W, 0, 0, W, H);
        src.recycle();

        // Background: the average opaque colour along the four edges.
        long r = 0, g = 0, b = 0, n = 0;
        for (int x = 0; x < W; x++) {
            for (int y : new int[]{0, 1, H - 2, H - 1}) {
                int c = px[y * W + x];
                if ((c >>> 24) < 128) continue;
                r += (c >> 16) & 0xFF; g += (c >> 8) & 0xFF; b += c & 0xFF; n++;
            }
        }
        for (int y = 0; y < H; y++) {
            for (int x : new int[]{0, 1, W - 2, W - 1}) {
                int c = px[y * W + x];
                if ((c >>> 24) < 128) continue;
                r += (c >> 16) & 0xFF; g += (c >> 8) & 0xFF; b += c & 0xFF; n++;
            }
        }
        if (n == 0) return null;
        int br = (int) (r / n), bg = (int) (g / n), bb = (int) (b / n);

        int[] out = new int[W * H];
        int minX = W, minY = H, maxX = -1, maxY = -1, covered = 0;
        long fr = 0, fg = 0, fb = 0, fw = 0;
        for (int i = 0; i < px.length; i++) {
            int c = px[i];
            int alpha = c >>> 24;
            int cr = (c >> 16) & 0xFF, cg = (c >> 8) & 0xFF, cb = c & 0xFF;
            int dist = Math.max(Math.abs(cr - br), Math.max(Math.abs(cg - bg), Math.abs(cb - bb)));
            float a = Math.max(0f, Math.min(1f, (dist / 255f - 0.18f) / 0.22f)) * (alpha / 255f);
            if (a <= 0f) continue;
            out[i] = (Math.round(a * 255) << 24) | 0xFFFFFF;
            if (a > 0.15f) {
                int x = i % W, y = i / W;
                minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                minY = Math.min(minY, y); maxY = Math.max(maxY, y);
                covered++;
                // Saturated pixels say the most about the brand's colour.
                int sat = Math.max(cr, Math.max(cg, cb)) - Math.min(cr, Math.min(cg, cb));
                fr += (long) cr * sat; fg += (long) cg * sat; fb += (long) cb * sat; fw += sat;
            }
        }
        // Too little is noise; too much is a picture or a gradient, not a mark (a boxed logo on a gradient came out
        // as a white slab at 40%+).
        if (maxX < 0 || covered < 200 || covered > W * H * 0.35f) return null;
        if (colorOut != null && colorOut.length > 0 && fw > 0) {
            colorOut[0] = 0xFF000000 | ((int) (fr / fw) << 16) | ((int) (fg / fw) << 8) | (int) (fb / fw);
        }
        int pad = 2;
        minX = Math.max(0, minX - pad); minY = Math.max(0, minY - pad);
        maxX = Math.min(W - 1, maxX + pad); maxY = Math.min(H - 1, maxY + pad);
        int cw = maxX - minX + 1, ch = maxY - minY + 1;
        Bitmap mask = Bitmap.createBitmap(cw, ch, Bitmap.Config.ARGB_8888);
        mask.setPixels(out, minY * W + minX, W, 0, 0, cw, ch);
        return mask;
    }
}
