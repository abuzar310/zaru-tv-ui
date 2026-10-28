package com.zaru.tv;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * The vibe home, built to the "can't stop vibecoding my TV" breakdown: a full-bleed black-and-white picture from the
 * shows on the TV's own apps, a thin clock with the weather, three outline icons, one line of text for the focused
 * app, and a dock of white logos with a glow in that app's colour. Outfit throughout; no tiles, no band, no blur,
 * no video. Down from the dock opens every app (MainActivity, which Back then closes); Settings opens the Control
 * Center. MainActivity stays a Home of its own, so `cmd package set-home-activity` switches between the two looks.
 */
public final class VibeHomeActivity extends Activity {
    static final String EXTRA_ALL_APPS = "com.zaru.tv.extra.ALL_APPS";
    private static final int MAX_DOCK = 6;
    /** The TV's own input switcher (com.tcl.suspension on TCL Google TV). */
    private static final String ACTION_VIEW_INPUTS = "com.android.tv.action.VIEW_INPUTS";

    /** A drawn white mark: the drawable, its square box in dp (sized by visual weight, never stretched), the colour. */
    private static final class Mark {
        final int drawable, boxDp, color;

        Mark(int drawable, int boxDp, int color) {
            this.drawable = drawable;
            this.boxDp = boxDp;
            this.color = color;
        }
    }

    private static final Map<String, Mark> MARKS = new HashMap<>();
    /** Brand colours for apps whose mark is cut from their banner (LogoMask) instead. */
    private static final Map<String, Integer> COLORS = new HashMap<>();
    /** Apps whose banner does not cut out cleanly (a boxed logo on a gradient): their name in Outfit instead. */
    private static final Map<String, String> WORDMARKS = new HashMap<>();
    /**
     * Apps whose cards bake their own logo and buttons into the picture (Netflix: the N top left, "New Episode" and
     * "Watch Now" along the bottom), which clash with the clock as a wallpaper. Their cards still feed the taglines.
     */
    private static final java.util.Set<String> NOT_WALLPAPER = new java.util.HashSet<>(
            java.util.Collections.singletonList("com.netflix.ninja"));

    static {
        MARKS.put("com.netflix.ninja", new Mark(R.drawable.logo_netflix, 54, 0xFFE50914));
        MARKS.put("com.amazon.amazonvideo.livingroom", new Mark(R.drawable.logo_primevideo, 116, 0xFF1FB6FF));
        MARKS.put("in.startv.hotstar", new Mark(R.drawable.logo_star, 60, 0xFF4F7BFF));
        MARKS.put("com.google.android.youtube.tv", new Mark(R.drawable.logo_youtube, 62, 0xFFFF2D2D));
        MARKS.put("com.google.android.youtube.tvmusic", new Mark(R.drawable.logo_youtube, 62, 0xFFFF2D2D));
        MARKS.put("com.spotify.tv.android", new Mark(R.drawable.logo_spotify, 50, 0xFF1ED760));
        MARKS.put("com.apple.atve.androidtv.appletv", new Mark(R.drawable.logo_appletv, 70, 0xFFE8E8ED));
        COLORS.put("com.sonyliv", 0xFFE8A33C);
        COLORS.put("app.netmirror.newtv", 0xFFE53935);
        COLORS.put("com.graymatrix.did", 0xFF8230C6);
        COLORS.put("com.mxtech.videoplayer.television", 0xFF2D7CF6);
        WORDMARKS.put("com.sonyliv", "SonyLIV");
    }

    /** The app's colour for a glow: its drawn mark's, a known brand's, or a neutral grey. */
    static int brandColor(String pkg) {
        Mark mark = MARKS.get(pkg);
        if (mark != null) return mark.color;
        Integer known = COLORS.get(pkg);
        return known != null ? known : 0xFF8A8F98;
    }

    private static final class Cell {
        String pkg, label, line;
        Intent launch;
        View root, box;
        TextView name;
        int color;
        final List<String> lines = new ArrayList<>();
    }

    private final List<Cell> cells = new ArrayList<>();
    private final Random random = new Random();
    private final DecelerateInterpolator decelerate = new DecelerateInterpolator(2f);
    private float density;
    private GlowView glow;
    private TextView tagline;
    private ImageView wallA, wallB, wallFront;
    private Bitmap wallShown;
    private View[] topIcons;
    private ShowArt showArt;
    private Weather weather;
    private int focusedCell = -1;
    private boolean dockFocused = true;
    private boolean resumedOnce;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_vibe_home);
        density = getResources().getDisplayMetrics().density;
        glow = findViewById(R.id.vibeGlow);
        tagline = findViewById(R.id.vibeTagline);
        wallA = findViewById(R.id.vibeWallA);
        wallB = findViewById(R.id.vibeWallB);
        ColorMatrix grey = new ColorMatrix();
        grey.setSaturation(0f);
        wallA.setColorFilter(new ColorMatrixColorFilter(grey));
        wallB.setColorFilter(new ColorMatrixColorFilter(grey));
        findViewById(R.id.vibeShade).setBackground(shade());

        if (checkSelfPermission(ShowArt.PERMISSION) == PackageManager.PERMISSION_GRANTED) {
            showArt = new ShowArt(this, 1280, 720);
            ChannelSetup.run(this);
        }
        String lat = getString(R.string.weather_lat), lon = getString(R.string.weather_lon);
        if (!lat.isEmpty() && !lon.isEmpty()) {
            weather = new Weather(this, lat, lon);
            Weather.Reading last = weather.cached();
            if (last != null) showWeather(last);
        }
        setUpTopIcons();
        buildDock();
        View dock = findViewById(R.id.vibeDock);
        dock.post(() -> {
            // E: the glow's centre sits 250dp in and 30dp below the top of the dock.
            int[] at = new int[2], origin = new int[2];
            dock.getLocationInWindow(at);
            glow.getLocationInWindow(origin);
            glow.setCenter(250 * density, at[1] - origin[1] + 30 * density);
        });
        if (!cells.isEmpty()) cells.get(0).root.requestFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (weather != null) weather.refresh(this::showWeather);
        if (resumedOnce) {
            // D: a fresh line for every app on each visit.
            for (Cell c : cells) c.line = null;
            if (focusedCell >= 0) showLine(cells.get(focusedCell));
        }
        resumedOnce = true;
        pickWallpaper();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Home pressed on Home: back to the first app in the dock.
        if (!cells.isEmpty()) cells.get(0).root.requestFocus();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (showArt != null) showArt.shutdown();
    }

    /** Back on Home starts the screensaver, as on the other look. */
    @Override
    public void onBackPressed() {
        try {
            startActivity(new Intent(Intent.ACTION_MAIN)
                    .setClassName("com.android.systemui", "com.android.systemui.Somnambulator"));
        } catch (ActivityNotFoundException | SecurityException e) {
            android.util.Log.w("TvHome", "no screensaver starter on this TV", e);
        }
    }

    // ---- dock ------------------------------------------------------------------------------------

    private void buildDock() {
        LinearLayout dock = findViewById(R.id.vibeDock);
        PackageManager pm = getPackageManager();
        LayoutInflater inflater = getLayoutInflater();
        Map<String, List<String>> lines = taglines();
        for (String pkg : getResources().getStringArray(R.array.dock_apps)) {
            if (cells.size() >= MAX_DOCK) break;
            Intent launch = pm.getLeanbackLaunchIntentForPackage(pkg);
            if (launch == null) launch = pm.getLaunchIntentForPackage(pkg);
            if (launch == null) continue;
            Cell cell = new Cell();
            cell.pkg = pkg;
            cell.launch = launch;
            cell.label = label(pm, pkg);
            View v = inflater.inflate(R.layout.item_vibe_dock, dock, false);
            cell.root = v;
            cell.box = v.findViewById(R.id.vibeLogoBox);
            cell.name = v.findViewById(R.id.vibeLabel);
            cell.name.setText(cell.label);
            int[] color = {0xFF8A8F98};
            ImageView logo = v.findViewById(R.id.vibeLogo);
            Mark mark = MARKS.get(pkg);
            if (mark != null) {
                logo.setImageResource(mark.drawable);
                ViewGroup.LayoutParams lp = logo.getLayoutParams();
                lp.width = lp.height = Math.round(mark.boxDp * density);
                logo.setLayoutParams(lp);
                color[0] = mark.color;
            } else {
                String wordmark = WORDMARKS.get(pkg);
                Bitmap mask = wordmark == null ? LogoMask.from(banner(pm, pkg, launch), color) : null;
                if (mask != null) {
                    logo.setImageBitmap(mask);
                    fit(logo, mask, 118, 44);
                } else {
                    logo.setVisibility(View.GONE);
                    TextView word = v.findViewById(R.id.vibeWordmark);
                    word.setText(wordmark != null ? wordmark : cell.label);
                    word.setVisibility(View.VISIBLE);
                }
            }
            Integer known = COLORS.get(pkg);
            cell.color = known != null ? known : color[0];
            List<String> own = lines.get(pkg);
            if (own != null) cell.lines.addAll(own);
            cell.box.setAlpha(0.8f);
            final int index = cells.size();
            v.setId(View.generateViewId());
            v.setOnFocusChangeListener((view, has) -> onCellFocus(index, has));
            v.setOnClickListener(view -> open(cell.launch));
            v.setOnKeyListener((view, code, event) -> {
                if (code != KeyEvent.KEYCODE_DPAD_DOWN) return false;
                if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) openDeck();
                return true;
            });
            cells.add(cell);
            dock.addView(v);
        }
        if (cells.isEmpty()) return;
        // The ends of the dock don't fall through to anything.
        View first = cells.get(0).root, last = cells.get(cells.size() - 1).root;
        first.setNextFocusLeftId(first.getId());
        last.setNextFocusRightId(last.getId());
        for (View icon : topIcons) icon.setNextFocusDownId(first.getId());
    }

    private void onCellFocus(int index, boolean has) {
        Cell cell = cells.get(index);
        if (has) {
            focusedCell = index;
            cell.box.animate().scaleX(1.16f).scaleY(1.16f).alpha(1f).setDuration(240).setInterpolator(decelerate).start();
            // G: the name rises 6px into place over 220ms.
            cell.name.animate().cancel();
            cell.name.setTranslationY(6 * density);
            cell.name.animate().alpha(1f).translationY(0f).setDuration(220).setInterpolator(decelerate).start();
            glow.setColor(cell.color);
            setDockFocused(true);
            showLine(cell);
            for (View icon : topIcons) icon.setNextFocusDownId(cell.root.getId());
        } else {
            cell.box.animate().scaleX(1f).scaleY(1f).alpha(0.8f).setDuration(240).setInterpolator(decelerate).start();
            cell.name.animate().cancel();
            cell.name.animate().alpha(0f).setDuration(120).start();
        }
    }

    /** D at 100% while the dock has focus, 35% elsewhere (200ms); E at full strength, or 45% away. */
    private void setDockFocused(boolean focused) {
        if (dockFocused == focused) return;
        dockFocused = focused;
        tagline.animate().alpha(focused ? 1f : 0.35f).setDuration(200).start();
        glow.setAway(!focused);
    }

    /** Swaps the line above the dock: out over 120ms, then the new one rises in over 220ms. */
    private void showLine(Cell cell) {
        if (cell.line == null) cell.line = pickLine(cell);
        final String line = cell.line;
        if (line.contentEquals(tagline.getText())) return;
        tagline.animate().cancel();
        tagline.animate().alpha(0f).setDuration(120).withEndAction(() -> {
            tagline.setText(line);
            tagline.setTranslationY(6 * density);
            tagline.animate().alpha(dockFocused ? 1f : 0.35f).translationY(0f).setDuration(220)
                    .setInterpolator(decelerate).start();
        }).start();
    }

    private String pickLine(Cell cell) {
        List<String> pool = new ArrayList<>(cell.lines);
        ShowArt.Pick pick = showArt == null ? null : showArt.peek(cell.pkg);
        if (pick != null && pick.title != null && !pick.title.trim().isEmpty()) {
            pool.add(getString(R.string.vibe_show, pick.title.trim()));
        }
        if (pool.isEmpty()) return getString(R.string.vibe_ready, cell.label);
        return pool.get(random.nextInt(pool.size()));
    }

    private Map<String, List<String>> taglines() {
        Map<String, List<String>> map = new HashMap<>();
        for (String item : getResources().getStringArray(R.array.vibe_taglines)) {
            int bar = item.indexOf('|');
            if (bar <= 0 || bar == item.length() - 1) continue;
            map.computeIfAbsent(item.substring(0, bar), k -> new ArrayList<>()).add(item.substring(bar + 1));
        }
        return map;
    }

    private static String label(PackageManager pm, String pkg) {
        try {
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }

    private static Drawable banner(PackageManager pm, String pkg, Intent launch) {
        try {
            Drawable d = pm.getActivityBanner(launch);
            if (d == null) d = pm.getApplicationBanner(pkg);
            if (d == null) d = pm.getActivityIcon(launch);
            return d;
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    /** Fits a cut-out mark inside maxW x maxH dp, keeping its shape. */
    private void fit(ImageView view, Bitmap mark, int maxWdp, int maxHdp) {
        float scale = Math.min(maxWdp * density / mark.getWidth(), maxHdp * density / mark.getHeight());
        ViewGroup.LayoutParams lp = view.getLayoutParams();
        lp.width = Math.round(mark.getWidth() * scale);
        lp.height = Math.round(mark.getHeight() * scale);
        view.setLayoutParams(lp);
    }

    // ---- top icons, wallpaper, weather -----------------------------------------------------------

    private void setUpTopIcons() {
        View wifi = findViewById(R.id.vibeWifi), inputs = findViewById(R.id.vibeInputs), settings = findViewById(R.id.vibeSettings);
        topIcons = new View[]{wifi, inputs, settings};
        for (View icon : topIcons) {
            icon.setNextFocusUpId(icon.getId());
            icon.setOnFocusChangeListener((v, has) -> {
                // C: focus grows the icon 1.32x over 220ms.
                v.animate().scaleX(has ? 1.32f : 1f).scaleY(has ? 1.32f : 1f).alpha(has ? 1f : 0.85f)
                        .setDuration(220).setInterpolator(decelerate).start();
                if (has) setDockFocused(false);
            });
        }
        wifi.setNextFocusLeftId(wifi.getId());
        settings.setNextFocusRightId(settings.getId());
        wifi.setOnClickListener(v -> open(new Intent(Settings.ACTION_WIFI_SETTINGS)));
        inputs.setOnClickListener(v -> {
            Intent panel = new Intent(ACTION_VIEW_INPUTS);
            open(getPackageManager().resolveActivity(panel, 0) != null ? panel : new Intent(Settings.ACTION_SETTINGS));
        });
        settings.setOnClickListener(v -> open(new Intent(this, QuickSettingsActivity.class)));
    }

    /** H: one black-and-white picture per visit, from the shows the dock's apps publish; it fades over the last. */
    private void pickWallpaper() {
        if (showArt == null || cells.isEmpty()) return;
        List<Bitmap> ready = new ArrayList<>();
        for (Cell c : cells) {
            ShowArt.Pick p = showArt.get(c.pkg, () -> {
                if (wallShown == null) pickWallpaper();
            });
            if (p != null && !NOT_WALLPAPER.contains(c.pkg)) ready.add(p.bitmap);
        }
        if (ready.isEmpty()) return;
        if (ready.size() > 1) ready.remove(wallShown);
        setWall(ready.get(random.nextInt(ready.size())));
    }

    private void setWall(Bitmap bitmap) {
        if (bitmap == wallShown) return;
        wallShown = bitmap;
        ImageView next = wallFront == wallA ? wallB : wallA;
        ImageView old = wallFront;
        wallFront = next;
        next.animate().cancel();
        next.setImageBitmap(bitmap);
        next.setAlpha(0f);
        next.animate().alpha(1f).setDuration(600).start();
        if (old != null) old.animate().alpha(0f).setDuration(600).start();
    }

    /** Over the picture: a light shade under the clock, and from 45% of the height 0 → 35% → 70% → 85% black. */
    private Drawable shade() {
        int h = 270;
        Bitmap bmp = Bitmap.createBitmap(1, h, Bitmap.Config.ARGB_8888);
        Paint paint = new Paint();
        paint.setShader(new LinearGradient(0, 0, 0, h,
                new int[]{0x66000000, 0x00000000, 0x00000000, 0x59000000, 0xB3000000, 0xD9000000},
                new float[]{0f, 0.24f, 0.45f, 0.63f, 0.82f, 1f}, Shader.TileMode.CLAMP));
        new Canvas(bmp).drawRect(0, 0, 1, h, paint);
        BitmapDrawable shade = new BitmapDrawable(getResources(), bmp);
        shade.setFilterBitmap(true);
        return shade;
    }

    private void showWeather(Weather.Reading r) {
        ImageView icon = findViewById(R.id.vibeWxIcon);
        icon.setImageResource(Weather.lineIcon(r.code, r.day));
        icon.setVisibility(View.VISIBLE);
        ((TextView) findViewById(R.id.vibeWxTemp)).setText(Math.round(r.temp) + "°C");
    }

    // ---- launching -------------------------------------------------------------------------------

    /** Down from the dock: the next page, "Don't have time to browse?", sliding up over 380ms. */
    private void openDeck() {
        startActivity(new Intent(this, BrowseDeckActivity.class));
        overridePendingTransition(R.anim.page_up_in, R.anim.page_up_out);
    }

    private void open(Intent intent) {
        try {
            startActivity(new Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
        }
    }
}
