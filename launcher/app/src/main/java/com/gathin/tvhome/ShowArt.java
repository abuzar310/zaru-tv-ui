package com.gathin.tvhome;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Show artwork for the cinematic shelf: the films and shows apps publish as home screen cards (their channels and
 * Watch Next rows in the system TV provider), read with READ_TV_LISTINGS. Focusing a dock app fills the screen with
 * one of its cards, sharp, instead of its softened banner; an app with no cards leaves the shelf to the aerial or
 * colour behind it. The approach is Vibe Launcher's Backdrops (maydings/vibe-launcher, MIT): every row is read,
 * since filtering by package needs system permissions; what you're watching comes first; only wide art big enough
 * for the screen is used; downloaded files are kept on the TV. One worker thread, which runs only when Home asks.
 */
final class ShowArt {
    static final String PERMISSION = "android.permission.READ_TV_LISTINGS";
    private static final String TAG = "TvHome.ShowArt";
    private static final Uri PREVIEW = Uri.parse("content://android.media.tv/preview_program");
    private static final Uri WATCH_NEXT = Uri.parse("content://android.media.tv/watch_next_program");
    private static final String[] COLUMNS = {"package_name", "intent_uri", "title", "poster_art_uri", "thumbnail_uri"};
    private static final int FIRST_IMAGE = 3;   // COLUMNS from here on are image URIs
    private static final int MIN_WIDTH = 800;   // Netflix publishes 853x480
    private static final float MIN_ASPECT = 1.45f, MAX_ASPECT = 2.4f;
    private static final int MAX_BYTES = 6 << 20;
    /** Cards are read again after this long, so the shelf follows what the apps offer now. */
    private static final long REFRESH_MS = 6 * 60 * 60 * 1000L;
    /** Downloaded pictures kept on the TV; the oldest go first. */
    private static final int MAX_FILES = 60;
    /** Images tried per app before giving up until the next refresh. */
    private static final int MAX_TRIES = 8;

    /** One card ready to show: its title (null when the app gave none) and the picture cut to the shelf's size. */
    static final class Pick {
        final String title;
        final Bitmap bitmap;

        Pick(String title, Bitmap bitmap) {
            this.title = title;
            this.bitmap = bitmap;
        }
    }

    private final ContentResolver resolver;
    private final File root;
    private final int width, height;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    // Main thread only.
    private final Map<String, Pick> ready = new HashMap<>();
    private final Map<String, Long> checked = new HashMap<>();
    private final Set<String> loading = new HashSet<>();

    ShowArt(Context context, int width, int height) {
        resolver = context.getContentResolver();
        root = new File(context.getCacheDir(), "showart");
        this.width = width;
        this.height = height;
    }

    /** The card ready for this app, or null; never starts a read. */
    Pick peek(String pkg) {
        return pkg == null ? null : ready.get(pkg);
    }

    /**
     * The card ready for this app, or null. When none has been read yet, or the last read is old, a read starts
     * on the worker; onReady then runs on the main thread if it found a card.
     */
    Pick get(String pkg, Runnable onReady) {
        if (pkg == null) return null;
        Long at = checked.get(pkg);
        long now = System.currentTimeMillis();
        if ((at == null || now - at > REFRESH_MS) && loading.add(pkg)) {
            checked.put(pkg, now);
            worker.execute(() -> {
                Pick found = load(pkg);
                main.post(() -> {
                    loading.remove(pkg);
                    if (found == null) return;
                    ready.put(pkg, found);
                    if (onReady != null) onReady.run();
                });
            });
        }
        return ready.get(pkg);
    }

    void shutdown() {
        worker.shutdownNow();
    }

    /** One card for the browse deck: the app, the film or show, its picture and the link that opens it. */
    static final class Card {
        final String pkg, title, image, link;

        Card(String pkg, String title, String image, String link) {
            this.pkg = pkg;
            this.title = title;
            this.image = image;
            this.link = link;
        }
    }

    /**
     * Every card these apps publish (all apps when packages is null): what you're watching first, then the rest
     * shuffled, one per title. Read on the worker; done runs on the main thread.
     */
    void deck(Set<String> packages, java.util.function.Consumer<List<Card>> done) {
        worker.execute(() -> {
            List<Card> watching = new ArrayList<>(), channel = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            Map<String, Integer> counts = new HashMap<>();
            for (Uri table : new Uri[]{WATCH_NEXT, PREVIEW}) {
                try (Cursor c = resolver.query(table, COLUMNS, null, null, null)) {
                    while (c != null && c.moveToNext()) {
                        String pkg = c.getString(0), link = c.getString(1), title = c.getString(2);
                        if (pkg == null || (packages != null && !packages.contains(pkg)) || badged(link)) continue;
                        String image = c.getString(FIRST_IMAGE);
                        if (image == null || image.isEmpty()) image = c.getString(FIRST_IMAGE + 1);
                        if (image == null || image.isEmpty()) continue;
                        if (!seen.add(pkg + "|" + (title != null ? title : image))) continue;
                        (table == WATCH_NEXT ? watching : channel).add(new Card(pkg, title, image, link));
                        counts.merge(pkg, 1, Integer::sum);
                    }
                } catch (RuntimeException e) {
                    Log.w(TAG, "cannot read " + table + ": " + e.getMessage());
                }
            }
            Log.i(TAG, "deck: " + watching.size() + " watching, " + channel.size() + " more, by app " + counts);
            Collections.shuffle(channel);
            watching.addAll(channel);
            main.post(() -> done.accept(watching));
        });
    }

    /** A deck card's picture cut to the shelf's size, or null if it can't be had; done runs on the main thread. */
    void picture(String url, java.util.function.Consumer<Bitmap> done) {
        worker.execute(() -> {
            Bitmap bitmap = fetch(url);
            main.post(() -> done.accept(bitmap));
        });
    }

    // ---- worker thread ---------------------------------------------------------------------------

    private Pick load(String pkg) {
        List<String[]> watching = new ArrayList<>(), channel = new ArrayList<>();   // {title, image url}
        Set<String> seen = new HashSet<>();
        for (Uri table : new Uri[]{WATCH_NEXT, PREVIEW}) {
            try (Cursor c = resolver.query(table, COLUMNS, null, null, null)) {
                while (c != null && c.moveToNext()) {
                    if (!pkg.equals(c.getString(0)) || badged(c.getString(1))) continue;
                    for (int i = FIRST_IMAGE; i < COLUMNS.length; i++) {
                        String url = c.getString(i);
                        if (url == null || url.isEmpty() || !seen.add(url)) continue;
                        (table == WATCH_NEXT ? watching : channel).add(new String[]{c.getString(2), url});
                    }
                }
            } catch (RuntimeException e) {
                Log.w(TAG, "cannot read " + table + ": " + e.getMessage());   // no permission, or not a TV
            }
        }
        Log.i(TAG, pkg + ": " + watching.size() + " watching, " + channel.size() + " channel images");
        Collections.shuffle(channel);
        watching.addAll(channel);
        int tries = 0;
        for (String[] card : watching) {
            if (++tries > MAX_TRIES) break;
            Bitmap bitmap = fetch(card[1]);
            if (bitmap != null) return new Pick(card[0], bitmap);
        }
        return null;
    }

    /**
     * Netflix bakes stickers such as "Recently Added" and "Top 10" into the artwork of some cards and names them in
     * the card's link; those don't work as a full-screen picture.
     */
    private static boolean badged(String intentUri) {
        return intentUri != null && (intentUri.contains("badges%3D") || intentUri.contains("badges="));
    }

    /** The picture behind this URL cut to the shelf, from the TV's copy or downloaded first; null if unsuitable. */
    private Bitmap fetch(String url) {
        File file = new File(root, sha1(url) + ".img");
        byte[] data = null;
        if (file.exists()) {
            file.setLastModified(System.currentTimeMillis());
        } else {
            data = read(url);
            if (data == null || !suitable(data)) return null;
            save(file, data);   // a failed save only means it's downloaded again next time
        }
        Bitmap decoded = data != null ? decode(data) : decode(file);
        return decoded == null ? null : cover(decoded);
    }

    private byte[] read(String url) {
        try {
            if (url.startsWith("http://") || url.startsWith("https://")) {
                HttpURLConnection http = (HttpURLConnection) new URL(url).openConnection();
                http.setConnectTimeout(8000);
                http.setReadTimeout(15000);
                try (InputStream in = http.getInputStream()) {
                    return http.getResponseCode() == 200 ? readAll(in) : null;
                } finally {
                    http.disconnect();
                }
            }
            // content:// and android.resource:// art served by the app itself.
            try (InputStream in = resolver.openInputStream(Uri.parse(url))) {
                return in == null ? null : readAll(in);
            }
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
            if (out.size() > MAX_BYTES) return null;
        }
        return out.toByteArray();
    }

    private static boolean suitable(byte[] data) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, o);
        if (o.outWidth < MIN_WIDTH || o.outHeight <= 0) return false;
        float aspect = o.outWidth / (float) o.outHeight;
        return aspect >= MIN_ASPECT && aspect <= MAX_ASPECT;
    }

    private boolean save(File file, byte[] data) {
        root.mkdirs();
        trim();
        File tmp = new File(root, file.getName() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(data);
        } catch (IOException e) {
            tmp.delete();
            return false;
        }
        return tmp.renameTo(file);
    }

    /** Keeps the folder under MAX_FILES pictures, dropping the least recently shown. */
    private void trim() {
        File[] files = root.listFiles();
        if (files == null || files.length < MAX_FILES) return;
        Arrays.sort(files, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
        for (int i = 0; i <= files.length - MAX_FILES; i++) files[i].delete();
    }

    /** Decoded at the smallest power-of-two step that still covers the shelf, in full colour (no banding). */
    private Bitmap decode(byte[] data) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, o);
        o.inSampleSize = sampleSize(o.outWidth, o.outHeight);
        o.inJustDecodeBounds = false;
        return BitmapFactory.decodeByteArray(data, 0, data.length, o);
    }

    private Bitmap decode(File file) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getPath(), o);
        o.inSampleSize = sampleSize(o.outWidth, o.outHeight);
        o.inJustDecodeBounds = false;
        return BitmapFactory.decodeFile(file.getPath(), o);
    }

    private int sampleSize(int w, int h) {
        int sample = 1;
        while (w / (sample * 2) >= width && h / (sample * 2) >= height) sample *= 2;
        return sample;
    }

    /** Centre-cropped to exactly the shelf's size, so the view draws it without scaling work of its own. */
    private Bitmap cover(Bitmap src) {
        if (src.getWidth() == width && src.getHeight() == height) return src;
        float scale = Math.max(width / (float) src.getWidth(), height / (float) src.getHeight());
        int cw = Math.round(width / scale), ch = Math.round(height / scale);
        int x = (src.getWidth() - cw) / 2, y = (src.getHeight() - ch) / 2;
        Bitmap out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        new Canvas(out).drawBitmap(src, new Rect(x, y, x + cw, y + ch), new Rect(0, 0, width, height),
                new Paint(Paint.FILTER_BITMAP_FLAG));
        src.recycle();
        return out;
    }

    /** A file name for a URL: a 32-bit hash could give two images the same name and show the wrong one. */
    private static String sha1(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-1").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(s.hashCode());
        }
    }
}
