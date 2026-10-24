package com.gathin.tvhome;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Outline;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Full-screen picker for a dock slot: every installed TV app, drawn with the same artwork as the
 * dock. Choosing an app puts it in the slot (swapping places if it was already docked elsewhere).
 * Opened by pressing and holding a dock tile.
 */
public class DockEditorActivity extends Activity {

    private static final String EXTRA_SLOT = "slot";
    private static final String EXTRA_PKG = "pkg";
    private static final String EXTRA_LABEL = "label";
    private static final int COLUMNS = 5;

    static void open(Context ctx, int slot, String pkg, String label) {
        ctx.startActivity(new Intent(ctx, DockEditorActivity.class)
                .putExtra(EXTRA_SLOT, slot)
                .putExtra(EXTRA_PKG, pkg)
                .putExtra(EXTRA_LABEL, label)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    private static final class App {
        final String pkg;
        final String label;
        final ResolveInfo info;

        App(String pkg, String label, ResolveInfo info) {
            this.pkg = pkg;
            this.label = label;
            this.info = info;
        }
    }

    private int slot;
    private String replacedPkg;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dock_editor);
        slot = getIntent().getIntExtra(EXTRA_SLOT, 0);
        replacedPkg = getIntent().getStringExtra(EXTRA_PKG);
        String replacedLabel = getIntent().getStringExtra(EXTRA_LABEL);

        WallpaperHelper.loadPermanentWallpaper(this, findViewById(R.id.editorWallpaper));
        ((TextView) findViewById(R.id.editorSubtitle)).setText(
                getString(R.string.dock_editor_subtitle, replacedLabel != null ? replacedLabel : ""));

        findViewById(R.id.btnResetDock).setOnClickListener(v -> {
            DockPrefs.resetToRecommended(this);
            Toast.makeText(this, R.string.dock_reset_done, Toast.LENGTH_SHORT).show();
            finish();
        });
        View info = findViewById(R.id.btnAppInfo);
        info.setVisibility(replacedPkg != null ? View.VISIBLE : View.GONE);
        info.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + replacedPkg)));
            } catch (Exception ignored) {
            }
        });

        List<App> apps = installedApps();
        Set<String> docked = new HashSet<>(DockPrefs.loadDock(this));
        RecyclerView grid = findViewById(R.id.editorGrid);
        grid.setLayoutManager(new GridLayoutManager(this, COLUMNS));
        grid.setItemAnimator(null);
        grid.setAdapter(new PickAdapter(apps, docked));

        // Start on the app being replaced, so pressing OK straight away changes nothing.
        int start = 0;
        for (int i = 0; i < apps.size(); i++) {
            if (apps.get(i).pkg.equals(replacedPkg)) start = i;
        }
        final int first = start;
        grid.scrollToPosition(first);
        grid.post(() -> {
            RecyclerView.ViewHolder vh = grid.findViewHolderForAdapterPosition(first);
            if (vh != null) vh.itemView.findViewById(R.id.pickCard).requestFocus();
        });
    }

    /** Every enabled TV app, one per package, alphabetical, without this launcher itself. */
    private List<App> installedApps() {
        PackageManager pm = getPackageManager();
        Map<String, App> byPkg = new LinkedHashMap<>();
        for (ResolveInfo ri : AppDiscovery.getLeanbackApps(this)) {
            if (ri.activityInfo == null) continue;
            // Apps switched off (debloated, or by the viewer in Settings) can't be launched: don't offer them.
            if (!ri.activityInfo.applicationInfo.enabled || !ri.activityInfo.enabled) continue;
            String pkg = ri.activityInfo.packageName;
            if (byPkg.containsKey(pkg)) continue;
            String label = "com.mitv.tvhome.mitvplus".equals(pkg) ? "Xiaomi TV+" : String.valueOf(ri.loadLabel(pm));
            byPkg.put(pkg, new App(pkg, label, ri));
        }
        List<App> apps = new ArrayList<>(byPkg.values());
        apps.sort((a, b) -> a.label.compareToIgnoreCase(b.label));
        return apps;
    }

    private void choose(App app) {
        DockPrefs.assign(this, replacedPkg, slot, app.pkg);
        finish();
    }

    private final class PickAdapter extends RecyclerView.Adapter<PickAdapter.Holder> {
        private final List<App> apps;
        private final Set<String> docked;

        PickAdapter(List<App> apps, Set<String> docked) {
            this.apps = apps;
            this.docked = docked;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_dock_pick, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            App app = apps.get(position);
            PackageManager pm = getPackageManager();
            h.art.setImageDrawable(AppAdapter.artFor(DockEditorActivity.this, app.pkg, app.label,
                    app.info.activityInfo.loadBanner(pm), app.info.loadIcon(pm), false, 0));
            h.name.setText(app.label);
            h.docked.setVisibility(docked.contains(app.pkg) ? View.VISIBLE : View.GONE);
            h.card.setContentDescription(app.label);
            h.card.setOnClickListener(v -> choose(app));
        }

        @Override
        public int getItemCount() {
            return apps.size();
        }

        final class Holder extends RecyclerView.ViewHolder {
            final View card;
            final ImageView art;
            final View sheen;
            final TextView name;
            final TextView docked;

            Holder(View v) {
                super(v);
                card = v.findViewById(R.id.pickCard);
                art = v.findViewById(R.id.pickArt);
                sheen = v.findViewById(R.id.pickSheen);
                name = v.findViewById(R.id.pickName);
                docked = v.findViewById(R.id.pickDocked);

                final android.content.res.Resources res = v.getResources();
                card.setOutlineProvider(new ViewOutlineProvider() {
                    @Override
                    public void getOutline(View view, Outline outline) {
                        outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(),
                                AppAdapter.cornerRadius(view.getWidth(), res));
                    }
                });
                card.setClipToOutline(true);
                final float density = res.getDisplayMetrics().density;
                card.setOnFocusChangeListener((view, focused) -> {
                    float s = focused ? AppAdapter.FOCUS_SCALE : 1f;
                    view.animate().scaleX(s).scaleY(s)
                            .translationZ(focused ? 12 * density : 0f)
                            .setDuration(focused ? 150 : 120)
                            .setInterpolator(new DecelerateInterpolator()).start();
                    sheen.animate().alpha(focused ? 1f : AppAdapter.SHEEN_REST_ALPHA).setDuration(150).start();
                });
            }
        }
    }
}
