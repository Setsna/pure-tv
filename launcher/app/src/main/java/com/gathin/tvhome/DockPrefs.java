package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
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
        editor.apply();
    }

    public static void setSlot(Context ctx, int slotIndex, String packageName) {
        if (slotIndex < 0 || slotIndex >= SLOT_COUNT) return;
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
           .edit()
           .putString(KEY_PREFIX + slotIndex, packageName)
           .apply();
    }
}
