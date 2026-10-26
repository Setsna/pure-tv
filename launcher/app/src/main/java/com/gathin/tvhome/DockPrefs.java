package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import java.util.ArrayList;
import java.util.List;

public class DockPrefs {
    private static final String PREFS_NAME = "dock_prefs";
    private static final String KEY_PREFIX = "dock_slot_";
    public static final String KEY_SCHEMA = "dock_schema_version";
    public static final int CURRENT_SCHEMA = 2;
    public static final int SLOT_COUNT = 8;

    public static List<String> loadDock(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        List<String> dock = new ArrayList<>();

        boolean firstRun = !prefs.contains(KEY_PREFIX + "0")
                || prefs.getInt(KEY_SCHEMA, 0) < CURRENT_SCHEMA;
        if (firstRun) {
            // First launch or schema update: populate with smart defaults and persist immediately
            dock = AppDiscovery.getDefaultDockSelection(ctx, SLOT_COUNT);
            saveDock(ctx, dock);
            prefs.edit().putInt(KEY_SCHEMA, CURRENT_SCHEMA).apply();
            return dock;
        }

        for (int i = 0; i < SLOT_COUNT; i++) {
            String pkg = prefs.getString(KEY_PREFIX + i, null);
            if (pkg != null && !pkg.isEmpty()) {
                if ("com.mitv.livetv".equals(pkg)) {
                    pkg = "com.mitv.tvhome.mitvplus";
                }
                dock.add(pkg);
            }
        }

        if (dock.isEmpty()) {
            dock = AppDiscovery.getDefaultDockSelection(ctx, SLOT_COUNT);
            saveDock(ctx, dock);
        }
        return dock;
    }

    public static void saveDock(Context ctx, List<String> dock) {
        SharedPreferences.Editor editor =
            ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit();
        for (int i = 0; i < SLOT_COUNT; i++) {
            if (i < dock.size()) {
                editor.putString(KEY_PREFIX + i, dock.get(i));
            } else {
                editor.remove(KEY_PREFIX + i);
            }
        }
        editor.putInt(KEY_REVISION, revision(ctx) + 1);
        editor.apply();
    }

    private static final String KEY_REVISION = "dock_revision";
    private static final String KEY_THEME_DOCK = "theme_dock";

    /**
     * A theme's dock_apps (config.json) replaces the saved dock once, each time the theme's list
     * changes; edits made on the TV afterwards are kept until the next different list. Apps that
     * aren't installed are skipped, and a list with none installed changes nothing.
     */
    public static void applyThemeDock(Context ctx, List<String> themeDock) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String key = String.join(",", themeDock);
        if (key.equals(prefs.getString(KEY_THEME_DOCK, null))) return;
        List<String> dock = new ArrayList<>();
        for (String pkg : themeDock) {
            if (dock.size() >= SLOT_COUNT || dock.contains(pkg)) continue;
            try {
                if (ctx.getPackageManager().getApplicationInfo(pkg, 0).enabled) dock.add(pkg);
            } catch (PackageManager.NameNotFoundException ignored) {}
        }
        if (!dock.isEmpty()) {
            saveDock(ctx, dock);
            prefs.edit().putInt(KEY_SCHEMA, CURRENT_SCHEMA).apply();
        }
        prefs.edit().putString(KEY_THEME_DOCK, key).apply();
    }

    /** Bumped on every dock edit, so the home screen knows to rebuild its tiles. */
    public static int revision(Context ctx) {
        return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(KEY_REVISION, 0);
    }

    /**
     * Puts {@code pkg} in the slot now holding {@code replacedPkg} (or {@code fallbackSlot} when that
     * app isn't a saved slot). If {@code pkg} was already docked elsewhere, the two swap places.
     */
    public static void assign(Context ctx, String replacedPkg, int fallbackSlot, String pkg) {
        List<String> dock = new ArrayList<>(loadDock(ctx));
        while (dock.size() < SLOT_COUNT) dock.add("");
        int slot = replacedPkg != null ? dock.indexOf(replacedPkg) : -1;
        if (slot < 0) slot = Math.max(0, Math.min(SLOT_COUNT - 1, fallbackSlot));
        int existing = dock.indexOf(pkg);
        if (existing == slot) return;
        if (existing >= 0) dock.set(existing, dock.get(slot));
        dock.set(slot, pkg);
        dock.removeIf(String::isEmpty);
        saveDock(ctx, dock);
    }

    /** Back to the automatic, installed-apps-based dock. */
    public static void resetToRecommended(Context ctx) {
        saveDock(ctx, AppDiscovery.getDefaultDockSelection(ctx, SLOT_COUNT));
    }

    public static void setSlot(Context ctx, int slotIndex, String packageName) {
        if (slotIndex < 0 || slotIndex >= SLOT_COUNT) return;
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
           .edit()
           .putString(KEY_PREFIX + slotIndex, packageName)
           .putInt(KEY_REVISION, revision(ctx) + 1)
           .apply();
    }
}
