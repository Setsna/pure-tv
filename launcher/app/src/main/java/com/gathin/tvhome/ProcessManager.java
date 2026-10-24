package com.gathin.tvhome;

import android.app.ActivityManager;
import android.content.Context;

public class ProcessManager {

    /**
     * Kills background processes of all OTHER known streaming apps
     * when the user launches one app, freeing RAM for the focused app.
     * Does NOT touch the app actually being launched, and does NOT touch
     * system/launcher-critical processes.
     */
    public static void freeMemoryForFocusedApp(Context ctx, String launchingPackage) {
        if (ctx == null) return;
        ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) return;

        for (String pkg : AppDiscovery.CURATED_PACKAGES) {
            if (pkg == null) continue;
            if (pkg.equals(launchingPackage)) continue; // never kill the app you're opening
            if (pkg.equals(ctx.getPackageName())) continue; // never kill self
            // Protect TV tuner and hardware video services so Airtel channel playback is never disturbed
            if (pkg.equals("com.mitv.livetv") || pkg.equals("mitv.service") || pkg.startsWith("com.mediatek.wwtv")) {
                continue;
            }

            try {
                // killBackgroundProcesses only affects cached/background processes —
                // it will NOT kill a process currently in foreground or mid-playback
                // elsewhere, which is the safe, correct Android behavior here.
                am.killBackgroundProcesses(pkg);
            } catch (SecurityException ignored) {
                // Some OEM TV skins restrict this for certain system-adjacent packages
            }
        }
    }
}
