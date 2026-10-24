package com.gathin.tvhome;

import org.junit.Test;

import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for AppDiscovery curated apps and DockPrefs slot configuration.
 */
public class AppDiscoveryTest {

    @Test
    public void slotCount_isEight() {
        assertEquals(8, DockPrefs.SLOT_COUNT);
    }

    @Test
    public void curatedPackages_containsUniversalPlatforms() {
        Set<String> curated = AppDiscovery.CURATED_PACKAGES;
        assertNotNull(curated);
        assertFalse(curated.isEmpty());

        assertTrue("Hotstar should be curated", curated.contains("in.startv.hotstar"));
        assertTrue("YouTube should be curated", curated.contains("com.google.android.youtube.tv"));
        assertTrue("Spotify should be curated", curated.contains("com.spotify.tv.android"));
        assertTrue("Prime Video should be curated", curated.contains("com.amazon.amazonvideo.livingroom"));
        assertTrue("Netflix TV should be curated", curated.contains("com.netflix.ninja"));
    }

    @Test
    public void curatedPackages_preservesExactPriorityOrder() {
        String[] expectedOrder = {
            "com.mitv.tvhome.mitvplus",
            "in.startv.hotstar",
            "com.google.android.youtube.tv",
            "com.spotify.tv.android"
        };
        for (int i = 0; i < expectedOrder.length; i++) {
            assertEquals("Slot " + i + " must match priority order", expectedOrder[i], AppDiscovery.DOCK_PRIORITY[i][0]);
        }
    }

    @Test
    public void dockPriority_slotZeroIsXiaomiTvPlusAndNetflixPrecedesPrime() {
        assertEquals("Slot 0 primary candidate must be Xiaomi TV+",
                "com.mitv.tvhome.mitvplus", AppDiscovery.DOCK_PRIORITY[0][0]);

        int netflixSlot = -1;
        int primeSlot = -1;
        for (int i = 0; i < AppDiscovery.DOCK_PRIORITY.length; i++) {
            for (String pkg : AppDiscovery.DOCK_PRIORITY[i]) {
                if (pkg.contains("netflix")) netflixSlot = i;
                if (pkg.contains("amazonvideo")) primeSlot = i;
            }
        }
        assertTrue("Netflix must be present in DOCK_PRIORITY", netflixSlot != -1);
        assertTrue("Prime Video must be present in DOCK_PRIORITY", primeSlot != -1);
        assertTrue("Netflix (slot " + netflixSlot + ") must precede Prime Video (slot " + primeSlot + ")",
                netflixSlot < primeSlot);
    }

    @Test
    public void getDefaultDockSelection_withMockedInstalledList_skipsMissingAppsAndPreservesOrder() {
        java.util.Set<String> installed = new java.util.HashSet<>(java.util.Arrays.asList(
            "com.mitv.tvhome.mitvplus",
            "com.netflix.ninja",
            "com.amazon.amazonvideo.livingroom",
            "com.some.extra.app"
        ));
        // Hotstar, YouTube, Spotify are missing from installed list

        java.util.List<String> selection = AppDiscovery.getDefaultDockSelection(installed, 8);

        assertEquals("First slot must be Xiaomi TV+", "com.mitv.tvhome.mitvplus", selection.get(0));
        assertEquals("Second slot must be Netflix (Hotstar, YT, Spotify skipped)", "com.netflix.ninja", selection.get(1));
        assertEquals("Third slot must be Prime Video", "com.amazon.amazonvideo.livingroom", selection.get(2));
        assertTrue("Extra app should be added in Pass 2", selection.contains("com.some.extra.app"));
        assertFalse("Missing Hotstar must not be in selection", selection.contains("in.startv.hotstar"));
    }

    @Test
    public void getDefaultDockSelection_keepsSettingsAndPlayStoreAtTheEnd() {
        java.util.Set<String> installed = new java.util.HashSet<>(java.util.Arrays.asList(
            "in.startv.hotstar", "com.google.android.youtube.tv", "com.spotify.tv.android",
            "com.netflix.ninja", "com.amazon.amazonvideo.livingroom", "com.sonyliv",
            "com.disney.disneyplus", "org.videolan.vlc",
            "com.android.tv.settings", "com.android.vending"
        ));
        java.util.List<String> selection = AppDiscovery.getDefaultDockSelection(installed, 8);
        assertEquals(8, selection.size());
        assertEquals("com.android.tv.settings", selection.get(6));
        assertEquals("com.android.vending", selection.get(7));
        assertEquals("in.startv.hotstar", selection.get(0));
    }
}
