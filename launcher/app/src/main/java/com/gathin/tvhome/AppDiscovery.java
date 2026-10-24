package com.gathin.tvhome;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AppDiscovery {

    /**
     * Ranked dock candidates: one row per slot idea, first installed package of a row wins. Covers the
     * apps common on Android TV / Xiaomi TVs so a fresh install fills the dock sensibly on any TV.
     * The last {@link #SYSTEM_TAIL} rows (Settings, Play Store) are always kept room for at the end.
     */
    public static final String[][] DOCK_PRIORITY = {
        {"com.mitv.tvhome.mitvplus", "com.mitv.livetv"},
        {"in.startv.hotstar"},
        {"com.google.android.youtube.tv", "com.liskovsoft.smarttubetv.beta", "com.teamsmart.videomanager.tv"},
        {"com.spotify.tv.android"},
        {"com.netflix.ninja", "com.netflix.mediaclient"},
        {"com.amazon.amazonvideo.livingroom"},
        {"com.jio.media.ondemand", "com.jio.jioplay.tv"},
        {"com.sonyliv"},
        {"com.disney.disneyplus"},
        {"com.apple.atve.androidtv.appletv"},
        {"com.wbd.stream", "com.hbo.hbonow"},
        {"com.plexapp.android"},
        {"org.xbmc.kodi"},
        {"org.videolan.vlc"},
        {"com.xiaomi.mitv.mediaexplorer"},
        {"com.android.tv.settings", "com.android.settings"},
        {"com.android.vending"}
    };

    /** How many rows at the end of DOCK_PRIORITY are system shortcuts that always get a slot. */
    static final int SYSTEM_TAIL = 2;

    public static final Set<String> CURATED_PACKAGES;
    /** The curated media/streaming apps only (no Settings / Play Store): what the memory trimmer may stop. */
    public static final Set<String> STREAMING_PACKAGES;
    static {
        Set<String> set = new LinkedHashSet<>();
        Set<String> streaming = new LinkedHashSet<>();
        for (int i = 0; i < DOCK_PRIORITY.length; i++) {
            for (String pkg : DOCK_PRIORITY[i]) {
                set.add(pkg);
                if (i < DOCK_PRIORITY.length - SYSTEM_TAIL) streaming.add(pkg);
            }
        }
        CURATED_PACKAGES = Collections.unmodifiableSet(set);
        STREAMING_PACKAGES = Collections.unmodifiableSet(streaming);
    }

    public static List<ResolveInfo> getLeanbackApps(Context ctx) {
        PackageManager pm = ctx.getPackageManager();
        Intent leanback = new Intent(Intent.ACTION_MAIN);
        leanback.addCategory("android.intent.category.LEANBACK_LAUNCHER");

        List<ResolveInfo> rawApps = pm.queryIntentActivities(leanback, PackageManager.MATCH_DISABLED_COMPONENTS);

        // Fallback: standard launcher category if Leanback returns nothing
        // (some sideloaded apps on budget TVs lack the Leanback intent-filter)
        if (rawApps == null || rawApps.isEmpty()) {
            Intent standard = new Intent(Intent.ACTION_MAIN);
            standard.addCategory(Intent.CATEGORY_LAUNCHER);
            rawApps = pm.queryIntentActivities(standard, PackageManager.MATCH_DISABLED_COMPONENTS);
        }

        List<ResolveInfo> apps = (rawApps != null) ? new ArrayList<>(rawApps) : new ArrayList<>();

        // Exclude self to avoid launcher appearing in its own dock
        apps.removeIf(info -> info.activityInfo != null && info.activityInfo.packageName.equals(ctx.getPackageName()));

        return apps;
    }

    // Smart default: prioritize curated apps first, then fill remaining slots
    // with whatever else is installed, sorted alphabetically for determinism
    public static List<String> getDefaultDockSelection(Context ctx, int slotCount) {
        Set<String> installedPkgs = new HashSet<>();
        if (ctx != null) {
            PackageManager pm = ctx.getPackageManager();
            if (pm != null) {
                List<ResolveInfo> installed = getLeanbackApps(ctx);
                for (ResolveInfo ri : installed) {
                    if (ri.activityInfo != null) {
                        installedPkgs.add(ri.activityInfo.packageName);
                    }
                }
                for (String[] slotCandidates : DOCK_PRIORITY) {
                    for (String pkg : slotCandidates) {
                        if (!installedPkgs.contains(pkg)) {
                            try {
                                pm.getApplicationInfo(pkg, 0);
                                installedPkgs.add(pkg);
                            } catch (PackageManager.NameNotFoundException ignored) {}
                        }
                    }
                }
            }
        }
        return getDefaultDockSelection(installedPkgs, slotCount);
    }

    public static List<String> getDefaultDockSelection(Set<String> installedPkgs, int slotCount) {
        int appRows = DOCK_PRIORITY.length - SYSTEM_TAIL;
        List<String> system = new ArrayList<>();
        for (int i = appRows; i < DOCK_PRIORITY.length; i++) {
            String pkg = firstInstalled(DOCK_PRIORITY[i], installedPkgs);
            if (pkg != null) system.add(pkg);
        }

        // Pass 1: ranked apps, leaving room for the system shortcuts at the end
        List<String> result = new ArrayList<>();
        int appRoom = Math.max(0, slotCount - system.size());
        for (int i = 0; i < appRows && result.size() < appRoom; i++) {
            String pkg = firstInstalled(DOCK_PRIORITY[i], installedPkgs);
            if (pkg != null && !result.contains(pkg)) result.add(pkg);
        }

        // Pass 2: other installed apps (alphabetical, for determinism) into the remaining app room
        List<String> remaining = new ArrayList<>(installedPkgs);
        remaining.removeAll(result);
        remaining.removeAll(system);
        Collections.sort(remaining);
        for (String pkg : remaining) {
            if (result.size() >= appRoom) break;
            result.add(pkg);
        }

        // Pass 3: Settings and Play Store close the dock
        for (String pkg : system) {
            if (result.size() >= slotCount) break;
            result.add(pkg);
        }
        return result;
    }

    private static String firstInstalled(String[] candidates, Set<String> installed) {
        for (String pkg : candidates) {
            if (installed.contains(pkg)) return pkg;
        }
        return null;
    }
}
