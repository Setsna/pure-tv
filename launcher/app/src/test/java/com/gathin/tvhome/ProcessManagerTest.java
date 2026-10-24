package com.gathin.tvhome;

import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ProcessManagerTest {

    private static final String YT = "com.google.android.youtube.tv";
    private static final String HOTSTAR = "in.startv.hotstar";
    private static final String NETFLIX = "com.netflix.ninja";
    private static final long MIN = 60_000L;

    @Test
    public void recentlyLeftAppsStayWarm() {
        Map<String, Long> exited = new HashMap<>();
        exited.put(YT, 100 * MIN - 2 * MIN);
        assertTrue(ProcessManager.idleAppsToTrim(exited, 100 * MIN, 10 * MIN, null).isEmpty());
    }

    @Test
    public void longIdleAppsAreTrimmed_butNeverTheOneInUse() {
        Map<String, Long> exited = new HashMap<>();
        exited.put(YT, 0L);
        exited.put(HOTSTAR, 0L);
        exited.put(NETFLIX, 95 * MIN);
        List<String> trim = ProcessManager.idleAppsToTrim(exited, 100 * MIN, 10 * MIN, HOTSTAR);
        assertEquals(1, trim.size());
        assertEquals(YT, trim.get(0));
    }

    @Test
    public void tunerAndUnknownPackagesAreNeverTrimmed() {
        Map<String, Long> exited = new HashMap<>();
        exited.put("com.mitv.livetv", 0L);
        exited.put("com.example.notcurated", 0L);
        assertTrue(ProcessManager.idleAppsToTrim(exited, 100 * MIN, 10 * MIN, null).isEmpty());
    }

    @Test
    public void trimWithNullContext_doesNotThrow() {
        ProcessManager.trimIdleApps(null, new HashMap<>(), YT);
    }
}
