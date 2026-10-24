package com.gathin.tvhome;

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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
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

    static final float FOCUS_SCALE = 1.08f;
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

    /** Opacity of a tile that doesn't have focus; one value for first bind and focus changes alike. */
    static final float UNFOCUSED_ALPHA = 0.9f;
    /** Sheen opacity on a tile without focus; full sheen marks the focused tile. */
    static final float SHEEN_REST_ALPHA = 0.35f;

    /** Height of one dock row: the tile plus the row padding above and below (see DockDecoration). */
    static int rowHeightPx(int tileHeightPx, android.content.res.Resources res) {
        return tileHeightPx + res.getDimensionPixelSize(R.dimen.dock_row_pad_top)
                + res.getDimensionPixelSize(R.dimen.dock_row_pad_bottom);
    }

    private final Context ctx;
    private final TileListener listener;
    private final int dockSize;
    /** Dock order (config.xml dock_apps, or a theme's config.json). */
    private final List<String> favourites;
    /** Kept out of the general app list (res/values/config.xml, hidden_apps); a pinned dock slot still shows. */
    private final Set<String> hidden;
    private final boolean showHomeSettings;
    private final List<Entry> allItems = new ArrayList<>();
    private final List<Entry> items = new ArrayList<>();
    private boolean isExpanded = false;

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
        artCache.clear();
        this.favourites.clear();
        this.favourites.addAll(DockPrefs.loadDock(ctx));

        PackageManager pm = ctx.getPackageManager();
        Intent probe = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> found = pm.queryIntentActivities(probe, PackageManager.MATCH_DISABLED_COMPONENTS);

        Map<String, ResolveInfo> appMap = new LinkedHashMap<>();
        for (ResolveInfo ri : found) {
            if (ri.activityInfo != null && !appMap.containsKey(ri.activityInfo.packageName)) {
                appMap.put(ri.activityInfo.packageName, ri);
            }
        }

        // Ensure every favourite is resolved
        for (String favPkg : favourites) {
            if (!appMap.containsKey(favPkg)) {
                if ("com.mitv.tvhome.mitvplus".equals(favPkg)) {
                    Intent mitvIntent = new Intent(Intent.ACTION_MAIN)
                            .setComponent(new ComponentName("com.mitv.tvhome.mitvplus", "com.mitv.tvhome.mitvplus.app.BannerLogoFree"));
                    ResolveInfo ri = pm.resolveActivity(mitvIntent, PackageManager.MATCH_DISABLED_COMPONENTS);
                    if (ri != null && ri.activityInfo != null) {
                        appMap.put(favPkg, ri);
                        continue;
                    }
                }
                Intent li = pm.getLeanbackLaunchIntentForPackage(favPkg);
                if (li == null) li = pm.getLaunchIntentForPackage(favPkg);
                if (li != null) {
                    ResolveInfo ri = pm.resolveActivity(li, 0);
                    if (ri != null && ri.activityInfo != null) {
                        appMap.put(favPkg, ri);
                    }
                }
            }
        }

        Map<String, Cards.Card> cardByPkg = new HashMap<>();
        for (Cards.Card c : cards) cardByPkg.put(c.pkg, c);

        List<Entry> fresh = new ArrayList<>();
        Set<String> addedPkgs = new HashSet<>();

        // 1. Add configured dock favourites in strict priority order (exactly one tile per app).
        //    A pinned slot wins over hidden_apps: that list only keeps apps out of the general list.
        for (String favPkg : favourites) {
            if (addedPkgs.contains(favPkg)) continue;
            ResolveInfo ri = appMap.get(favPkg);
            if (ri == null || ri.activityInfo == null) continue;

            String pkg = ri.activityInfo.packageName;
            ComponentName component = new ComponentName(pkg, ri.activityInfo.name);
            Cards.Card card = cardByPkg.get(pkg);
            if (card != null && !card.component.equals(component)) continue;

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
            addedPkgs.add(pkg);
        }

        // 2. Add any other installed apps not in favourites (alphabetical)
        List<ResolveInfo> remaining = new ArrayList<>();
        for (Map.Entry<String, ResolveInfo> e : appMap.entrySet()) {
            if (!addedPkgs.contains(e.getKey()) && !hidden.contains(e.getKey()) && !e.getKey().equals(ctx.getPackageName())) {
                remaining.add(e.getValue());
            }
        }
        remaining.sort((a, b) -> String.valueOf(a.loadLabel(pm)).compareToIgnoreCase(String.valueOf(b.loadLabel(pm))));
        for (ResolveInfo ri : remaining) {
            String pkg = ri.activityInfo.packageName;
            if (addedPkgs.contains(pkg)) continue;
            ComponentName component = new ComponentName(pkg, ri.activityInfo.name);
            Drawable banner = ri.activityInfo.loadBanner(pm);
            if (banner == null) banner = ri.activityInfo.applicationInfo.loadBanner(pm);
            Drawable icon = ri.loadIcon(pm);
            String label = String.valueOf(ri.loadLabel(pm));
            fresh.add(new Entry(label, component, banner, icon));
            addedPkgs.add(pkg);
        }

        // Optional "Default home" tile (config.xml), only when this TV actually has a home-screen picker.
        if (showHomeSettings
                && pm.resolveActivity(new Intent(Settings.ACTION_HOME_SETTINGS), 0) != null) {
            fresh.add(new Entry(Entry.HOME_SETTINGS, ctx.getString(R.string.home_settings),
                    new ComponentName(ctx, HomeSettingsActivity.class), null,
                    ctx.getDrawable(R.drawable.ic_home_settings), null, 0));
        }
        allItems.clear();
        allItems.addAll(fresh);
        items.clear();
        int max = isExpanded ? Math.min(dockSize * 2, allItems.size()) : Math.min(dockSize, allItems.size());
        items.addAll(allItems.subList(0, max));
        notifyDataSetChanged();
    }

    public boolean isExpanded() {
        return isExpanded;
    }

    public void setExpanded(boolean expanded) {
        if (this.isExpanded == expanded) return;
        this.isExpanded = expanded;
        if (expanded) {
            int start = items.size();
            int end = Math.min(dockSize * 2, allItems.size());
            if (end > start) {
                for (int i = start; i < end; i++) {
                    items.add(allItems.get(i));
                }
                notifyItemRangeInserted(start, end - start);
            }
        } else {
            int count = items.size() - dockSize;
            if (count > 0) {
                while (items.size() > dockSize) {
                    items.remove(items.size() - 1);
                }
                notifyItemRangeRemoved(dockSize, count);
            }
        }
    }

    private final Map<String, Drawable> artCache = new HashMap<>();

    private static final String PKG_MITV_PLUS = "com.mitv.tvhome.mitvplus";

    /**
     * Full-bleed artwork for a tile: our own brand art for apps whose TV banner is missing or weak
     * (TileArt specs), otherwise the app's own 16:9 banner, otherwise brand art built from its icon.
     */
    private void bindTile(@NonNull Holder h, @NonNull Entry e) {
        CharSequence label = PKG_MITV_PLUS.equals(e.pkg) ? "Xiaomi TV+" : e.label;
        h.name.setText(label);
        h.card.setContentDescription(label);

        String key = e.kind == Entry.HOME_SETTINGS ? "#home_settings" : e.pkg;
        Drawable art = key != null ? artCache.get(key) : null;
        if (art == null) {
            art = resolveArt(e, label);
            if (key != null) artCache.put(key, art);
        }
        h.art.setImageDrawable(art);
    }

    @NonNull
    private Drawable resolveArt(@NonNull Entry e, CharSequence label) {
        return artFor(ctx, e.pkg, label, e.banner, e.icon, e.kind == Entry.HOME_SETTINGS, e.ambient);
    }

    /**
     * Tile artwork for any app, shared by the dock and the dock editor: our brand art (TileArt spec),
     * else the app's own 16:9 banner, else brand art built from its icon's colour.
     */
    @NonNull
    static Drawable artFor(Context ctx, String pkg, CharSequence label, Drawable banner, Drawable icon,
                           boolean homeSettings, int accent) {
        if (homeSettings) {
            return new TileArt(ctx, new TileArt.Spec(0xFF8E8E93, 0xFF3A3A3C,
                    R.drawable.ic_home_settings, String.valueOf(label), null), null);
        }
        TileArt.Spec spec = TileArt.specFor(pkg);
        if (spec != null) return new TileArt(ctx, spec, null);

        PackageManager pm = ctx.getPackageManager();
        if (banner == null && pkg != null) {
            try {
                banner = pm.getApplicationInfo(pkg, 0).loadBanner(pm);
            } catch (PackageManager.NameNotFoundException ignored) {
            }
        }
        if (banner != null && banner.getIntrinsicWidth() > banner.getIntrinsicHeight() * 1.3f) {
            return banner;
        }
        if (icon == null && pkg != null) {
            try {
                icon = pm.getApplicationIcon(pkg);
            } catch (PackageManager.NameNotFoundException ignored) {
            }
        }
        int color = accent != 0 ? accent : vividColor(icon);
        return new TileArt(ctx, TileArt.fromAccent(color, String.valueOf(label)), icon);
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
        // The item is exactly the tile's height; the name below draws into the row's bottom padding.
        ViewGroup.LayoutParams itemLp = v.getLayoutParams();
        itemLp.height = h;
        v.setLayoutParams(itemLp);
        View card = v.findViewById(R.id.card);
        ViewGroup.LayoutParams cardLp = card.getLayoutParams();
        cardLp.width = w;
        cardLp.height = h;
        card.setLayoutParams(cardLp);
        View name = v.findViewById(R.id.name);
        ViewGroup.MarginLayoutParams nameLp = (ViewGroup.MarginLayoutParams) name.getLayoutParams();
        nameLp.topMargin = h + Math.round(10 * density);
        name.setLayoutParams(nameLp);
        return new Holder(v, listener);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        Entry e = items.get(position);
        boolean focused = h.card.isFocused();
        h.card.setAlpha(focused ? 1.0f : UNFOCUSED_ALPHA);
        h.name.setAlpha(focused ? 1f : 0f);
        h.sheen.setAlpha(focused ? 1f : SHEEN_REST_ALPHA);

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
            if (e.kind == Entry.APP || e.kind == Entry.CARD) {
                DockEditorActivity.open(ctx, h.getBindingAdapterPosition(), e.pkg, String.valueOf(e.label));
            }
            return true;
        });
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
        }
        // Launch first, bookkeeping after: nothing may delay the app opening. Other apps are left
        // running so switching back to them is instant (ProcessManager trims only idle ones later).
        Intent i = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                .setComponent(e.component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            ctx.startActivity(i);
            if (ctx instanceof MainActivity && e.component != null) {
                ((MainActivity) ctx).onAppLaunched(e.component.getPackageName());
            }
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
        if ("com.mitv.tvhome.mitvplus".equals(e.pkg)) return 0xFFE2E8F0;
        if ("com.xiaomi.mitv.smartshare".equals(e.pkg)) return 0xFF0091FF;
        if ("com.xiaomi.mitv.mediaexplorer".equals(e.pkg)) return 0xFFFF9500;
        if (e.ambient == 0) e.ambient = vividColor(e.banner != null ? e.banner : e.icon);
        return e.ambient;
    }

    static int vividColor(Drawable live) {
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

    /** Corner radius for a tile of this width: radius_tile at the design width, proportionally less when smaller. */
    static float cornerRadius(int tileWidthPx, android.content.res.Resources res) {
        float density = res.getDisplayMetrics().density;
        return res.getDimension(R.dimen.radius_tile) * Math.min(1f, tileWidthPx / (DESIGN_W_DP * density));
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final View card;
        final ImageView art;
        final View sheen;
        final TextView name;

        Holder(View v, TileListener listener) {
            super(v);
            card = v.findViewById(R.id.card);
            art = v.findViewById(R.id.art);
            sheen = v.findViewById(R.id.sheen);
            name = v.findViewById(R.id.name);

            final android.content.res.Resources res = v.getResources();
            final float density = res.getDisplayMetrics().density;
            card.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    // radius_tile at the design width, scaled down with the tile so small tiles keep the same shape.
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), cornerRadius(view.getWidth(), res));
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
                // Apple TV style lift: grow, rise on a soft shadow, catch the light, show the name.
                float s = focused ? FOCUS_SCALE : 1.0f;
                float a = focused ? 1.0f : UNFOCUSED_ALPHA;
                view.animate().scaleX(s).scaleY(s).alpha(a)
                        .translationZ(focused ? 14 * density : 0f)
                        .setDuration(focused ? 150 : 120)
                        .setInterpolator(new DecelerateInterpolator())
                        .start();
                sheen.animate().alpha(focused ? 1f : SHEEN_REST_ALPHA).setDuration(150).start();
                name.animate().alpha(focused ? 1f : 0f)
                        .translationY(focused ? 0f : -4 * density)
                        .setDuration(140).start();
                int pos = getBindingAdapterPosition();
                if (focused && listener != null && pos != RecyclerView.NO_POSITION) {
                    listener.onTileFocused(pos, view);
                }
            });
        }
    }
}
