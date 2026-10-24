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

    public static final String[][] DOCK_PRIORITY = {
        {"com.mitv.tvhome.mitvplus", "com.mitv.livetv"},
        {"in.startv.hotstar"},
        {"com.google.android.youtube.tv"},
        {"com.spotify.tv.android"},
        {"com.netflix.ninja", "com.netflix.mediaclient"},
        {"com.amazon.amazonvideo.livingroom"},
        {"com.jio.jiotvsdk.tv"},
        {"org.videolan.vlc"}
    };

    public static final Set<String> CURATED_PACKAGES;
    static {
        Set<String> set = new LinkedHashSet<>();
        for (String[] candidates : DOCK_PRIORITY) {
            for (String pkg : candidates) {
                set.add(pkg);
            }
        }
        CURATED_PACKAGES = Collections.unmodifiableSet(set);
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
        List<String> result = new ArrayList<>();

        // Pass 1: priority candidates per slot
        for (String[] slotCandidates : DOCK_PRIORITY) {
            if (result.size() >= slotCount) break;
            for (String pkg : slotCandidates) {
                if (installedPkgs.contains(pkg)) {
                    if (!result.contains(pkg)) {
                        result.add(pkg);
                        break;
                    }
                }
            }
        }

        // Pass 2: fill remaining slots with other installed apps
        List<String> remaining = new ArrayList<>(installedPkgs);
        remaining.removeAll(result);
        Collections.sort(remaining);
        for (String pkg : remaining) {
            if (result.size() >= slotCount) break;
            result.add(pkg);
        }

        return result;
    }
}
