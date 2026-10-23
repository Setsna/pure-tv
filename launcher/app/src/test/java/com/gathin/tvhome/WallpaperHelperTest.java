package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertNull;

public class WallpaperHelperTest {

    @Test
    public void getWallpaperFile_withNullContext_returnsNull() {
        assertNull(WallpaperHelper.getWallpaperFile(null));
    }

    @Test
    public void loadPermanentWallpaper_withNull_doesNotCrash() {
        WallpaperHelper.loadPermanentWallpaper(null, null);
    }
}
