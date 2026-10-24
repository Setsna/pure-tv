package com.gathin.tvhome;

import android.app.ActivityManager;
import android.content.Context;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Frees memory only when the TV is actually short of it, and only from streaming apps the viewer
 * left a while ago. Apps used recently stay warm, so switching back to them is instant instead of a
 * cold start; Android's own low-memory killer handles everything else.
 */
public class ProcessManager {

    /** An app left this long ago is fair game when memory runs low. */
    static final long IDLE_BEFORE_TRIM_MS = 10 * 60 * 1000L;

    /** Never trimmed: the TV tuner and hardware video services, so live channels are never disturbed. */
    static boolean isProtected(String pkg) {
        return pkg.equals("com.mitv.livetv") || pkg.equals("mitv.service") || pkg.startsWith("com.mediatek.wwtv");
    }

    /**
     * The curated streaming apps that were left at least {@code idleMs} ago, never {@code keep}
     * (the app in use) and never a protected package.
     */
    static List<String> idleAppsToTrim(Map<String, Long> exitedAt, long now, long idleMs, String keep) {
        List<String> out = new ArrayList<>();
        if (exitedAt == null) return out;
        for (Map.Entry<String, Long> e : exitedAt.entrySet()) {
            String pkg = e.getKey();
            if (pkg == null || pkg.equals(keep) || isProtected(pkg)) continue;
            if (!AppDiscovery.STREAMING_PACKAGES.contains(pkg)) continue;
            if (e.getValue() != null && now - e.getValue() >= idleMs) out.add(pkg);
        }
        return out;
    }

    /** Kills the background processes of idle streaming apps; returns the packages it asked to stop. */
    static List<String> trimIdleApps(Context ctx, Map<String, Long> exitedAt, String keep) {
        List<String> trim = idleAppsToTrim(exitedAt, System.currentTimeMillis(), IDLE_BEFORE_TRIM_MS, keep);
        if (ctx == null || trim.isEmpty()) return trim;
        ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) return trim;
        for (String pkg : trim) {
            try {
                // Only affects cached background processes, never one in the foreground.
                am.killBackgroundProcesses(pkg);
            } catch (SecurityException ignored) {
                // Some OEM skins restrict this for system-adjacent packages.
            }
        }
        return trim;
    }
}
