package com.gathin.tvhome;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * "Don't have time to browse?": the page under the vibe home's dock, after the reel's swipe deck. One film or show at
 * a time from the cards the dock's apps publish (ShowArt.deck: what you're watching first, then the rest shuffled), on
 * top of a little deck, over its own picture in black and white with a glow in its app's colour. Left is nope (the
 * next one), Right or OK is yes (it opens in its app), Up or Back returns to the dock, Down opens every app.
 */
public final class BrowseDeckActivity extends Activity {
    private static final String TAG = "TvHome.Deck";
    private static final int GRANTS = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION;

    private final List<ShowArt.Card> cards = new ArrayList<>();
    private final Map<Integer, Bitmap> pictures = new HashMap<>();
    private final DecelerateInterpolator decelerate = new DecelerateInterpolator(2f);
    private ShowArt showArt;
    private ImageView card, bg;
    private TextView title, appName, nope, yes;
    private GlowView glow;
    private float density;
    /** The card on top, -1 before the first. */
    private int index = -1;
    /** Cards in a row whose picture could not be had: stops the deck spinning when none can. */
    private int misses;
    /** A card is flying off: keys wait. */
    private boolean busy;
    /** The top card left for its app: bring the next one in when Home comes back here. */
    private boolean away;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_browse_deck);
        density = getResources().getDisplayMetrics().density;
        card = findViewById(R.id.deckCard);
        bg = findViewById(R.id.deckBg);
        title = findViewById(R.id.deckTitle);
        appName = findViewById(R.id.deckApp);
        nope = findViewById(R.id.deckNope);
        yes = findViewById(R.id.deckYes);
        glow = findViewById(R.id.deckGlow);
        ColorMatrix grey = new ColorMatrix();
        grey.setSaturation(0f);
        bg.setColorFilter(new ColorMatrixColorFilter(grey));
        card.setClipToOutline(true);
        card.setAlpha(0f);
        View root = findViewById(android.R.id.content);
        root.post(() -> glow.setCenter(root.getWidth() / 2f, root.getHeight() / 2f + 20 * density));

        if (checkSelfPermission(ShowArt.PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            showEmpty();
            return;
        }
        showArt = new ShowArt(this, 1280, 720);
        // Only the dock's apps: other apps publish cards too (TCL's browser offered Facebook and web links).
        java.util.Set<String> dockApps = new java.util.HashSet<>(
                java.util.Arrays.asList(getResources().getStringArray(R.array.dock_apps)));
        showArt.deck(dockApps, list -> {
            cards.addAll(list);
            if (cards.isEmpty()) showEmpty();
            else advance();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (away) {
            away = false;
            advance();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (showArt != null) showArt.shutdown();
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.page_down_in, R.anim.page_down_out);
    }

    @Override
    public boolean onKeyDown(int code, KeyEvent event) {
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                nope();
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                yes();
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                finish();
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (event.getRepeatCount() == 0) {
                    open(new Intent(this, MainActivity.class).putExtra(VibeHomeActivity.EXTRA_ALL_APPS, true), null);
                }
                return true;
            default:
                return super.onKeyDown(code, event);
        }
    }

    // ---- the deck --------------------------------------------------------------------------------

    /** Brings the next card with a picture to the top, wrapping round; prefetches the one after it. */
    private void advance() {
        if (cards.isEmpty()) return;
        if (misses >= cards.size()) {
            showEmpty();
            return;
        }
        index = (index + 1) % cards.size();
        final int at = index;
        picture(at, bitmap -> {
            if (at != index) return;
            if (bitmap == null) {
                misses++;
                advance();
                return;
            }
            misses = 0;
            present(at, bitmap);
            picture((at + 1) % cards.size(), next -> { });
        });
    }

    private void present(int at, Bitmap bitmap) {
        ShowArt.Card c = cards.get(at);
        card.animate().cancel();
        card.setImageBitmap(bitmap);
        card.setTranslationX(0f);
        card.setRotation(0f);
        card.setScaleX(0.94f);
        card.setScaleY(0.94f);
        card.setAlpha(0f);
        card.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(240).setInterpolator(decelerate).start();
        bg.animate().cancel();
        bg.setImageBitmap(bitmap);
        bg.setAlpha(0f);
        bg.animate().alpha(1f).setDuration(420).start();
        String label = label(c.pkg);
        title.setText(c.title != null && !c.title.trim().isEmpty() ? c.title.trim() : label);
        appName.setText(getString(R.string.deck_on, label));
        glow.setColor(VibeHomeActivity.brandColor(c.pkg));
        // Keep only the pictures around the top card.
        pictures.keySet().removeIf(k -> k != at && k != (at + 1) % cards.size());
    }

    private void nope() {
        if (busy || index < 0 || card.getAlpha() == 0f) return;
        busy = true;
        flash(nope);
        card.animate().translationX(-560 * density).rotation(-14f).alpha(0f).setDuration(260).withEndAction(() -> {
            busy = false;
            advance();
        }).start();
    }

    private void yes() {
        if (busy || index < 0 || card.getAlpha() == 0f) return;
        busy = true;
        flash(yes);
        final ShowArt.Card c = cards.get(index);
        card.animate().translationX(560 * density).rotation(14f).alpha(0f).setDuration(260).withEndAction(() -> {
            busy = false;
            away = true;
            open(link(c), c.pkg);
        }).start();
    }

    private void flash(TextView label) {
        label.animate().cancel();
        label.setAlpha(1f);
        label.setScaleX(1.15f);
        label.setScaleY(1.15f);
        label.animate().alpha(0.6f).scaleX(1f).scaleY(1f).setDuration(320).start();
    }

    private void picture(int at, java.util.function.Consumer<Bitmap> done) {
        if (pictures.containsKey(at)) {
            done.accept(pictures.get(at));
            return;
        }
        showArt.picture(cards.get(at).image, bitmap -> {
            if (bitmap != null) pictures.put(at, bitmap);
            done.accept(bitmap);
        });
    }

    private void showEmpty() {
        findViewById(R.id.deckStack).setVisibility(View.GONE);
        nope.setVisibility(View.GONE);
        yes.setVisibility(View.GONE);
        findViewById(R.id.deckEmpty).setVisibility(View.VISIBLE);
    }

    // ---- opening ---------------------------------------------------------------------------------

    /**
     * The card's own link, if it opens the card's own app: a card is another app's data, so its link may start that
     * app and nothing else, and never hands out access to anything. Null means: just open the app.
     */
    private static Intent link(ShowArt.Card c) {
        if (c.link == null || c.link.isEmpty()) return null;
        Intent intent;
        try {
            intent = Intent.parseUri(c.link, Intent.URI_INTENT_SCHEME);
        } catch (URISyntaxException e) {
            return null;
        }
        intent.setSelector(null);
        intent.removeFlags(GRANTS);
        if (intent.getComponent() != null && !c.pkg.equals(intent.getComponent().getPackageName())) return null;
        if (intent.getPackage() != null && !c.pkg.equals(intent.getPackage())) return null;
        if (intent.getComponent() == null) intent.setPackage(c.pkg);
        return intent;
    }

    /** Starts the intent, or the app's own start screen when there is none or it fails. */
    private void open(Intent intent, String pkg) {
        if (intent != null) {
            try {
                startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return;
            } catch (ActivityNotFoundException | SecurityException e) {
                Log.w(TAG, "card link failed for " + pkg + ": " + e.getMessage());
            }
        }
        if (pkg == null) return;
        PackageManager pm = getPackageManager();
        Intent launch = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (launch == null) launch = pm.getLaunchIntentForPackage(pkg);
        if (launch == null) return;
        try {
            startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException | SecurityException e) {
            Log.w(TAG, "cannot open " + pkg, e);
        }
    }

    private String label(String pkg) {
        try {
            PackageManager pm = getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }
}
