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
            "com.mitv.livetv",
            "in.startv.hotstar",
            "com.google.android.youtube.tv",
            "com.spotify.tv.android"
        };
        int idx = 0;
        for (String pkg : AppDiscovery.CURATED_PACKAGES) {
            if (idx < expectedOrder.length) {
                assertEquals("Slot " + idx + " must match priority order", expectedOrder[idx], pkg);
                idx++;
            }
        }
        assertEquals("First 4 priority slots must be verified", 4, idx);
    }
}
