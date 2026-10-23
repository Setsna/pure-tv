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

    // Curated apps get custom charcoal tiles; everything else gets auto-generated banner
    public static final Set<String> CURATED_PACKAGES = new LinkedHashSet<>(Arrays.asList(
        "in.startv.hotstar",                 // JioHotstar
        "com.google.android.youtube.tv",     // YouTube TV
        "com.netflix.ninja",                 // Netflix Android TV
        "com.netflix.mediaclient",           // Netflix mobile / sideloaded
        "com.spotify.tv.android",            // Spotify
        "com.amazon.amazonvideo.livingroom", // Amazon Prime Video
        "com.mitv.tvhome.mitvplus",          // Xiaomi TV+ Live Channels
        "com.xiaomi.mitv.smartshare",        // Xiaomi SmartShare / Miracast
        "com.xiaomi.mitv.mediaexplorer",     // Xiaomi MediaExplorer
        "com.jio.jiotvsdk.tv",
        "org.videolan.vlc",
        "com.plexapp.android"
    ));

    public static List<ResolveInfo> getLeanbackApps(Context ctx) {
        PackageManager pm = ctx.getPackageManager();
        Intent leanback = new Intent(Intent.ACTION_MAIN);
        leanback.addCategory("android.intent.category.LEANBACK_LAUNCHER");

        List<ResolveInfo> rawApps = pm.queryIntentActivities(leanback, 0);

        // Fallback: standard launcher category if Leanback returns nothing
        // (some sideloaded apps on budget TVs lack the Leanback intent-filter)
        if (rawApps == null || rawApps.isEmpty()) {
            Intent standard = new Intent(Intent.ACTION_MAIN);
            standard.addCategory(Intent.CATEGORY_LAUNCHER);
            rawApps = pm.queryIntentActivities(standard, 0);
        }

        List<ResolveInfo> apps = (rawApps != null) ? new ArrayList<>(rawApps) : new ArrayList<>();

        // Exclude self to avoid launcher appearing in its own dock
        apps.removeIf(info -> info.activityInfo != null && info.activityInfo.packageName.equals(ctx.getPackageName()));

        return apps;
    }

    // Smart default: prioritize curated apps first, then fill remaining slots
    // with whatever else is installed, sorted alphabetically for determinism
    public static List<String> getDefaultDockSelection(Context ctx, int slotCount) {
        List<ResolveInfo> installed = getLeanbackApps(ctx);
        Map<String, ResolveInfo> installedMap = new HashMap<>();
        for (ResolveInfo ri : installed) {
            if (ri.activityInfo != null) {
                installedMap.put(ri.activityInfo.packageName, ri);
            }
        }

        List<String> result = new ArrayList<>();

        // Pass 1: curated apps, in the order declared above
        for (String pkg : CURATED_PACKAGES) {
            if (installedMap.containsKey(pkg) && result.size() < slotCount) {
                result.add(pkg);
            }
        }

        // Pass 2: fill remaining slots with other installed apps
        List<String> remaining = new ArrayList<>(installedMap.keySet());
        remaining.removeAll(result);
        Collections.sort(remaining);
        for (String pkg : remaining) {
            if (result.size() >= slotCount) break;
            result.add(pkg);
        }

        return result;
    }
}
