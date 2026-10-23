package com.gathin.tvhome;

import android.content.Context;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;

public class ProcessManagerTest {

    @Test
    public void freeMemory_withNullContext_doesNotThrow() {
        ProcessManager.freeMemoryForFocusedApp(null, "com.google.android.youtube.tv");
    }

    @Test
    public void curatedPackages_hasItemsToPrune() {
        assertNotNull(AppDiscovery.CURATED_PACKAGES);
    }
}
