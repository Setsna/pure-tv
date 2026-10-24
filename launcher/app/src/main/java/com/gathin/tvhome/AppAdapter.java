package com.gathin.tvhome;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * App tiles. The first {@link MainActivity#COLUMNS} positions are the pinned dock (favourites, fixed order),
 * then the module cards the owner chose to show (by label), the rest is alphabetical.
 */
public class AppAdapter extends RecyclerView.Adapter<AppAdapter.Holder> {

    interface TileListener {
        void onTileFocused(int position, View card);

        /** Called for a D-pad direction key on a tile. Return true when handled (focus stays put). */
        boolean onTileVerticalKey(int position, int keyCode);
    }

    static final class Entry {
        static final int APP = 0, HOME_SETTINGS = 1, CARD = 2;

        final int kind;
        final String label;
        final ComponentName component;
        final String pkg;
        final Drawable banner;
        final Drawable icon;
        /** Text under the tile when it is focused in the grid: the label, plus the caption for a card. */
        final String name;
        /** Ambient glow colour; 0 until sampled from the banner, or the card's accent from the start. */
        int ambient;

        Entry(String label, ComponentName component, Drawable banner, Drawable icon) {
            this(APP, label, component, banner, icon, null, 0);
        }

        Entry(int kind, String label, ComponentName component, Drawable banner, Drawable icon, String caption, int accent) {
            this.kind = kind;
            this.label = label;
            this.component = component;
            this.pkg = component.getPackageName();
            this.banner = banner;
            this.icon = icon;
            this.name = caption == null ? label : label + " \u00B7 " + caption;
            this.ambient = accent;
        }
    }

    static final float FOCUS_SCALE = 1.04f;
    /** Design size of a tile (dp); tiles shrink from here when the columns don't fit, see tileWidthPx. */
    static final float DESIGN_W_DP = 180f, DESIGN_H_DP = 101f;

    /**
     * Tile width for a grid of this inner width: the cell minus room for the focus zoom and the glass
     * highlight, capped at the design size so 4 columns on a 1080p TV look exactly as before.
     */
    static int tileWidthPx(int innerWidthPx, int columns, float density) {
        int design = Math.round(DESIGN_W_DP * density);
        if (innerWidthPx <= 0 || columns <= 0) return design;
        int cell = innerWidthPx / columns;
        int fit = (int) ((cell - 12 * density) / FOCUS_SCALE);
        return Math.max(Math.round(80 * density), Math.min(design, fit));
    }

    static int tileHeightPx(int tileWidthPx) {
        return Math.round(tileWidthPx * DESIGN_H_DP / DESIGN_W_DP);
    }

    /** Height of one grid item: the tile, the name label under it and the item's own padding. */
    static int itemHeightPx(int tileHeightPx, float density) {
        return tileHeightPx + Math.round(45 * density);
    }

    private final Context ctx;
    private final TileListener listener;
    private final int dockSize;
    /** Dock order (config.xml dock_apps, or a theme's config.json). */
    private final List<String> favourites;
    /** Never shown as tiles (res/values/config.xml, hidden_apps). */
    private final Set<String> hidden;
    private final boolean showHomeSettings;
    private final List<Entry> items = new ArrayList<>();

    AppAdapter(Context ctx, TileListener listener, int dockSize, Config cfg) {
        this.ctx = ctx;
        this.listener = listener;
        this.dockSize = dockSize;
        this.hidden = new HashSet<>(cfg.hiddenApps);
        this.favourites = new ArrayList<>(DockPrefs.loadDock(ctx));
        this.showHomeSettings = cfg.showHomeSettingsShortcut;
        // Tiles keep their identity across reload(), so a focused tile stays on its app instead of
        // its view being rebound to whatever app now sits in that slot.
        setHasStableIds(true);
    }

    /**
     * Rebuilds the tiles using universal dynamic app discovery from DockPrefs and AppDiscovery.
     */
    void reload(List<Cards.Card> cards) {
        this.favourites.clear();
        this.favourites.addAll(DockPrefs.loadDock(ctx));

        PackageManager pm = ctx.getPackageManager();
        Intent probe = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> found = pm.queryIntentActivities(probe, PackageManager.MATCH_DISABLED_COMPONENTS);

        // Also query launch intents for configured dock apps (favourites) in case they declare standard launcher category
        Set<String> resolvedPkgs = new HashSet<>();
        for (ResolveInfo ri : found) {
            if (ri.activityInfo != null) resolvedPkgs.add(ri.activityInfo.packageName);
        }
        for (String favPkg : favourites) {
            if (!resolvedPkgs.contains(favPkg) && !hidden.contains(favPkg)) {
                if ("com.mitv.tvhome.mitvplus".equals(favPkg)) {
                    Intent mitvIntent = new Intent(Intent.ACTION_MAIN);
                    mitvIntent.setComponent(new ComponentName("com.mitv.tvhome.mitvplus", "com.mitv.tvhome.mitvplus.app.BannerLogoFree"));
                    ResolveInfo ri = pm.resolveActivity(mitvIntent, PackageManager.MATCH_DISABLED_COMPONENTS);
                    if (ri != null && ri.activityInfo != null) {
                        found.add(ri);
                        resolvedPkgs.add(favPkg);
                        continue;
                    }
                }
                Intent li = pm.getLeanbackLaunchIntentForPackage(favPkg);
                if (li == null) li = pm.getLaunchIntentForPackage(favPkg);
                if (li != null) {
                    ResolveInfo ri = pm.resolveActivity(li, 0);
                    if (ri != null && ri.activityInfo != null) {
                        found.add(ri);
                        resolvedPkgs.add(favPkg);
                    }
                }
            }
        }

        Map<String, Cards.Card> cardByPkg = new HashMap<>();
        for (Cards.Card c : cards) cardByPkg.put(c.pkg, c);
        List<Entry> fresh = new ArrayList<>();
        for (ResolveInfo ri : found) {
            if (ri.activityInfo == null) continue;
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(ctx.getPackageName()) || hidden.contains(pkg)) continue;
            if (!favourites.isEmpty() && !favourites.contains(pkg)) continue; // STRICT CORE APPS ONLY
            ComponentName component = new ComponentName(pkg, ri.activityInfo.name);
            Cards.Card card = cardByPkg.get(pkg);
            if (card != null && !card.component.equals(component)) continue;   // the card stands in for this tile
            Drawable banner = ri.activityInfo.loadBanner(pm);
            if (banner == null) banner = ri.activityInfo.applicationInfo.loadBanner(pm);
            Drawable icon = ri.loadIcon(pm);
            String label = String.valueOf(ri.loadLabel(pm));
            if ("com.mitv.tvhome.mitvplus".equals(pkg)) {
                label = "Xiaomi TV+";
                component = new ComponentName("com.mitv.tvhome.mitvplus", "com.mitv.tvhome.mitvplus.app.BannerLogoFree");
            }
            if (card != null) {
                fresh.add(new Entry(Entry.CARD, card.label, component, banner, icon, card.caption, card.accent));
            } else {
                fresh.add(new Entry(label, component, banner, icon));
            }
        }

        Collections.sort(fresh, (a, b) -> {
            int fa = favourites.indexOf(a.pkg), fb = favourites.indexOf(b.pkg);
            if (fa >= 0 || fb >= 0) {
                if (fa < 0) return 1;
                if (fb < 0) return -1;
                return Integer.compare(fa, fb);
            }
            // Cards come right after the configured dock apps, before the alphabetical grid.
            boolean ca = a.kind == Entry.CARD, cb = b.kind == Entry.CARD;
            if (ca != cb) return ca ? -1 : 1;
            return a.label.compareToIgnoreCase(b.label);
        });
        // Optional "Default home" tile (config.xml), only when this TV actually has a home-screen picker.
        if (showHomeSettings
                && pm.resolveActivity(new Intent(Settings.ACTION_HOME_SETTINGS), 0) != null) {
            fresh.add(new Entry(Entry.HOME_SETTINGS, ctx.getString(R.string.home_settings),
                    new ComponentName(ctx, HomeSettingsActivity.class), null,
                    ctx.getDrawable(R.drawable.ic_home_settings), null, 0));
        }
        items.clear();
        items.addAll(fresh);
        notifyDataSetChanged();
    }

    private final Map<String, Drawable> artCache = new HashMap<>();

    private void bindTile(@NonNull Holder h, @NonNull Entry e) {
        String pkg = e.pkg;
        Drawable art = (pkg != null) ? artCache.get(pkg) : null;
        CharSequence label = e.label;

        if (art == null && pkg != null) {
            try {
                PackageManager pm = ctx.getPackageManager();
                if ("com.mitv.tvhome.mitvplus".equals(pkg)) {
                    label = "Xiaomi TV+";
                    Intent mitvIntent = new Intent(Intent.ACTION_MAIN)
                            .setComponent(new ComponentName("com.mitv.tvhome.mitvplus", "com.mitv.tvhome.mitvplus.app.BannerLogoFree"));
                    ResolveInfo ri = pm.resolveActivity(mitvIntent, PackageManager.MATCH_DISABLED_COMPONENTS);
                    if (ri != null && ri.activityInfo != null) {
                        art = ri.activityInfo.loadBanner(pm);
                    }
                }
                ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
                if (label == null || label.length() == 0) {
                    label = ai.loadLabel(pm);
                }
                if (art == null) {
                    art = ai.loadBanner(pm); // 16:9 TV artwork, preferred
                }
                if (art == null && e.banner != null) {
                    art = e.banner;
                }
                if (art == null) {
                    art = ai.loadIcon(pm); // fallback: square app icon
                    if (art == null && e.icon != null) {
                        art = e.icon;
                    }
                }
                if (art != null) {
                    art.setBounds(0, 0, art.getIntrinsicWidth(), art.getIntrinsicHeight());
                    artCache.put(pkg, art);
                }
            } catch (PackageManager.NameNotFoundException ignored) {
                if (e.banner != null) art = e.banner;
                else if (e.icon != null) art = e.icon;
            }
        } else if (art == null) {
            if (e.banner != null) art = e.banner;
            else if (e.icon != null) art = e.icon;
        }

        if ("com.mitv.tvhome.mitvplus".equals(pkg)) {
            label = "Xiaomi TV+";
        }

        boolean hasBanner = (art != null && art.getIntrinsicWidth() > art.getIntrinsicHeight() * 1.3f);
        if (hasBanner) {
            h.banner.setScaleType(ImageView.ScaleType.CENTER_CROP);
            h.banner.setPadding(0, 0, 0, 0);
            h.label.setVisibility(View.GONE);
        } else {
            h.banner.setScaleType(ImageView.ScaleType.FIT_CENTER);
            float d = h.itemView.getResources().getDisplayMetrics().density;
            int padH = Math.round(36 * d);
            int padT = Math.round(20 * d);
            int padB = Math.round(36 * d);
            h.banner.setPadding(padH, padT, padH, padB);
            h.label.setText(label);
            h.label.setVisibility(View.VISIBLE);
        }
        h.banner.setImageDrawable(art);
    }

    private int cachedCellWidth = 0;

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app, parent, false);
        float density = parent.getResources().getDisplayMetrics().density;
        int width = parent.getWidth() > 0 ? parent.getWidth() : parent.getMeasuredWidth();
        int inner = width - parent.getPaddingLeft() - parent.getPaddingRight();
        if (inner > 0) {
            cachedCellWidth = inner / dockSize;
        }
        int w = tileWidthPx(inner, dockSize, density), h = tileHeightPx(w);
        View card = v.findViewById(R.id.card);
        card.setStateListAnimator(android.animation.AnimatorInflater.loadStateListAnimator(parent.getContext(), R.animator.tile_focus_animator));
        ViewGroup.LayoutParams cardLp = card.getLayoutParams();
        cardLp.width = w;
        cardLp.height = h;
        card.setLayoutParams(cardLp);
        View name = v.findViewById(R.id.name);
        ViewGroup.MarginLayoutParams nameLp = (ViewGroup.MarginLayoutParams) name.getLayoutParams();
        nameLp.width = w;
        nameLp.topMargin = h + Math.round(7 * density);
        name.setLayoutParams(nameLp);
        return new Holder(v, listener);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        Entry e = items.get(position);
        boolean focused = h.card.isFocused();
        h.name.setAlpha(focused ? 1f : 0f);
        h.card.setAlpha(focused ? 1.0f : 0.82f);

        // Center tiles horizontally as a group when fewer items are in this row (e.g. 2 apps in secondary row or dock)
        int totalItems = items.size();
        int rowStart = (position / dockSize) * dockSize;
        int rowEnd = Math.min(totalItems, rowStart + dockSize);
        int rowCount = rowEnd - rowStart;
        if (rowCount < dockSize) {
            float density = h.itemView.getResources().getDisplayMetrics().density;
            int cellW = cachedCellWidth > 0 ? cachedCellWidth : Math.round(200 * density);
            float shift = ((dockSize - rowCount) * cellW) / 2.0f;
            h.itemView.setTranslationX(shift);
        } else {
            h.itemView.setTranslationX(0f);
        }

        bindTile(h, e);

        h.card.setOnClickListener(v -> launch(e));
        h.card.setOnLongClickListener(v -> {
            showSlotConfigDialog(position, e);
            return true;
        });
    }

    private void showSlotConfigDialog(int position, Entry current) {
        if (position < 0 || position >= items.size()) return;
        List<ResolveInfo> installed = AppDiscovery.getLeanbackApps(ctx);
        if (installed == null || installed.isEmpty()) {
            Toast.makeText(ctx, "No other apps found", Toast.LENGTH_SHORT).show();
            return;
        }

        PackageManager pm = ctx.getPackageManager();
        CharSequence[] itemsList = new CharSequence[installed.size() + 1];
        itemsList[0] = "⚙️ App Details / Settings";
        for (int i = 0; i < installed.size(); i++) {
            ResolveInfo ri = installed.get(i);
            itemsList[i + 1] = ri.loadLabel(pm);
        }

        new android.app.AlertDialog.Builder(ctx, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Customize Slot " + (position + 1) + ": " + current.label)
                .setItems(itemsList, (dialog, which) -> {
                    if (which == 0) {
                        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + current.pkg));
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        try {
                            ctx.startActivity(i);
                        } catch (ActivityNotFoundException ignored) {}
                    } else {
                        ResolveInfo selected = installed.get(which - 1);
                        String chosenPkg = selected.activityInfo.packageName;
                        DockPrefs.setSlot(ctx, position, chosenPkg);
                        Toast.makeText(ctx, "Assigned " + selected.loadLabel(pm) + " to Slot " + (position + 1), Toast.LENGTH_SHORT).show();
                        reload(Collections.emptyList());
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** Opens the tile's activity by explicit component, with no extras: a card can name nothing else. */
    private void launch(Entry e) {
        if (e.kind == Entry.HOME_SETTINGS) {
            ctx.startActivity(new Intent(ctx, HomeSettingsActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return;
        }
        if (e.component != null) {
            String pkg = e.component.getPackageName();
            if ("com.mitv.tvhome.mitvplus".equals(pkg)) {
                try {
                    Intent plusIntent = new Intent(Intent.ACTION_MAIN)
                            .setComponent(new ComponentName("com.mitv.tvhome.mitvplus", "com.mitv.tvhome.mitvplus.app.BannerLogoFree"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                    ctx.startActivity(plusIntent);
                    return;
                } catch (Exception ignored) {
                }
            }
            // Free RAM from other streaming apps BEFORE launching
            ProcessManager.freeMemoryForFocusedApp(ctx, pkg);
            if (ctx instanceof MainActivity) {
                ((MainActivity) ctx).onAppLaunched(pkg);
            }
        }
        Intent i = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                .setComponent(e.component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            ctx.startActivity(i);
        } catch (Exception ex) {
            try {
                if (e.component != null) {
                    Intent fallback = ctx.getPackageManager().getLaunchIntentForPackage(e.component.getPackageName());
                    if (fallback != null) {
                        fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        ctx.startActivity(fallback);
                        return;
                    }
                }
            } catch (Exception ignored) {}
            Toast.makeText(ctx, ctx.getString(R.string.cant_open, e.label), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @Override
    public long getItemId(int position) {
        return items.get(position).component.flattenToShortString().hashCode();
    }

    /** Stable key for the app at a position (its component), or null. */
    String keyAt(int position) {
        return position >= 0 && position < items.size() ? items.get(position).component.flattenToShortString() : null;
    }

    /** Position of the app with this key, or -1 when it is gone. */
    int positionOf(String key) {
        if (key == null) return -1;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).component.flattenToShortString().equals(key)) return i;
        }
        return -1;
    }

    String labelAt(int position) {
        return position >= 0 && position < items.size() ? items.get(position).label : "";
    }

    /** A vivid colour sampled from the app's banner (or icon), cached per entry; a card's accent when it set one. */
    int ambientColorAt(int position) {
        if (position < 0 || position >= items.size()) return 0xFF444444;
        Entry e = items.get(position);
        if ("in.startv.hotstar".equals(e.pkg)) return 0xFF1475E1;
        if ("com.google.android.youtube.tv".equals(e.pkg)) return 0xFFFF0000;
        if ("com.spotify.tv.android".equals(e.pkg)) return 0xFF1ED760;
        if ("com.netflix.ninja".equals(e.pkg)) return 0xFFE50914;
        if ("com.amazon.amazonvideo.livingroom".equals(e.pkg)) return 0xFF00A8E1;
        if ("com.mitv.tvhome.mitvplus".equals(e.pkg)) return 0xFFFF6900;
        if ("com.xiaomi.mitv.smartshare".equals(e.pkg)) return 0xFF0091FF;
        if ("com.xiaomi.mitv.mediaexplorer".equals(e.pkg)) return 0xFFFF9500;
        if (e.ambient == 0) e.ambient = vividColor(e.banner != null ? e.banner : e.icon);
        return e.ambient;
    }

    private static int vividColor(Drawable live) {
        if (live == null) return 0xFF444444;
        // Draw a private copy: changing the bounds of the drawable an ImageView shows would blank the tile.
        Drawable d = live.getConstantState() != null ? live.getConstantState().newDrawable().mutate() : live;
        Rect saved = new Rect(d.getBounds());
        int n = 12;
        Bitmap bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        d.setBounds(0, 0, n, n);
        d.draw(c);
        d.setBounds(saved);
        float[] hsv = new float[3];
        int best = 0xFF444444;
        float bestScore = -1f;
        long r = 0, g = 0, b = 0;
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                int p = bmp.getPixel(x, y);
                r += Color.red(p);
                g += Color.green(p);
                b += Color.blue(p);
                Color.colorToHSV(p, hsv);
                float score = hsv[1] * Math.min(1f, hsv[2] * 1.4f);   // saturated and not too dark
                if (score > bestScore) {
                    bestScore = score;
                    best = p;
                }
            }
        }
        bmp.recycle();
        if (bestScore < 0.25f) {   // nearly monochrome banner (e.g. white): use its average, lifted
            int avg = Color.rgb((int) (r / (n * n)), (int) (g / (n * n)), (int) (b / (n * n)));
            Color.colorToHSV(avg, hsv);
            hsv[2] = Math.max(hsv[2], 0.55f);
            return Color.HSVToColor(hsv);
        }
        Color.colorToHSV(best, hsv);
        hsv[1] = Math.min(1f, hsv[1] * 1.1f);
        hsv[2] = Math.max(0.5f, hsv[2]);
        return Color.HSVToColor(hsv);
    }

    /** Corner radius for a tile of this width: 20dp at the design width, proportionally less when smaller. */
    static float cornerRadius(int tileWidthPx, float density) {
        return 20f * density * Math.min(1f, tileWidthPx / (DESIGN_W_DP * density));
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final View card;
        final ImageView banner;
        final ImageView icon;
        final TextView label;
        final TextView name;

        Holder(View v, TileListener listener) {
            super(v);
            card = v.findViewById(R.id.card);
            banner = v.findViewById(R.id.banner);
            icon = v.findViewById(R.id.icon);
            label = v.findViewById(R.id.label);
            name = v.findViewById(R.id.name);

            final float density = v.getResources().getDisplayMetrics().density;
            card.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    // 16dp at the design width, scaled down with the tile so small tiles keep the same shape.
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), cornerRadius(view.getWidth(), density));
                }
            });
            card.setClipToOutline(true);

            card.setOnKeyListener((view, keyCode, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (keyCode != KeyEvent.KEYCODE_DPAD_UP && keyCode != KeyEvent.KEYCODE_DPAD_DOWN
                        && keyCode != KeyEvent.KEYCODE_DPAD_LEFT && keyCode != KeyEvent.KEYCODE_DPAD_RIGHT) return false;
                int pos = getBindingAdapterPosition();
                return listener != null && pos != RecyclerView.NO_POSITION && listener.onTileVerticalKey(pos, keyCode);
            });

            card.setOnFocusChangeListener((view, focused) -> {
                float s = focused ? FOCUS_SCALE : 1.0f;
                float a = focused ? 1.0f : 0.72f;
                view.animate().scaleX(s).scaleY(s).alpha(a)
                        .setDuration(focused ? 200 : 160)
                        .setInterpolator(new DecelerateInterpolator())
                        .start();
                name.animate().alpha(focused ? 1f : 0f)
                        .translationY(focused ? density * 4f : -density * 6f)
                        .setDuration(160).start();
                int pos = getBindingAdapterPosition();
                if (focused && listener != null && pos != RecyclerView.NO_POSITION) {
                    listener.onTileFocused(pos, view);
                }
            });
        }
    }
}
