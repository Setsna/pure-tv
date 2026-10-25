package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.widget.ImageView;

import java.io.File;

/**
 * Loads the wallpaper from TV storage when a readable file is there, otherwise the bundled photo.
 */
public class WallpaperHelper {

    /** Bundled backdrop used when no wallpaper file on the TV is readable. */
    static final int DEFAULT_WALLPAPER = R.drawable.wp_river;

    public static File getWallpaperFile(Context ctx) {
        if (ctx == null) return null;
        File[] candidates = new File[] {
            new File(ctx.getExternalFilesDir(null), "wallpaper.jpg"),
            new File(ctx.getFilesDir(), "wallpaper.jpg"),
            new File("/sdcard/wallpaper.jpg"),
            new File("/sdcard/Android/data/" + ctx.getPackageName() + "/files/wallpaper.jpg")
        };
        for (File f : candidates) {
            if (f != null && f.exists() && f.canRead() && f.length() > 0) {
                return f;
            }
        }
        return null;
    }

    /** The decoded wallpaper, shared by Home, the dock editor and the system screensaver (one copy in RAM). */
    private static Bitmap sWallpaper;

    public static void loadPermanentWallpaper(Context ctx, ImageView iv) {
        if (ctx == null || iv == null) return;
        Bitmap cached = sWallpaper;
        if (cached != null && !cached.isRecycled()) {
            iv.setImageBitmap(cached);
            return;
        }
        final Context app = ctx.getApplicationContext();
        new Thread(() -> {
            Bitmap bmp = decode(app);
            if (bmp == null) return;
            synchronized (WallpaperHelper.class) {
                if (sWallpaper == null || sWallpaper.isRecycled()) sWallpaper = bmp;
            }
            final Bitmap shown = sWallpaper;
            new Handler(Looper.getMainLooper()).post(() -> iv.setImageBitmap(shown));
        }, "WallpaperDecode").start();
    }

    /**
     * The wallpaper file from TV storage, or the bundled photo when none is readable (Android 11 blocks
     * /sdcard for apps), decoded as RGB_565 (half the memory of ARGB; a photo has no transparency) and
     * sampled down so it is no larger than needed to cover the screen.
     */
    private static Bitmap decode(Context ctx) {
        try {
            DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
            int targetW = dm.widthPixels > 0 ? dm.widthPixels : 1920;
            int targetH = dm.heightPixels > 0 ? dm.heightPixels : 1080;
            File target = getWallpaperFile(ctx);
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            if (target != null) BitmapFactory.decodeFile(target.getAbsolutePath(), opts);
            else BitmapFactory.decodeResource(ctx.getResources(), DEFAULT_WALLPAPER, opts);
            int sample = 1;
            while (opts.outWidth / (sample * 2) >= targetW && opts.outHeight / (sample * 2) >= targetH) {
                sample *= 2;
            }
            opts.inJustDecodeBounds = false;
            opts.inSampleSize = sample;
            opts.inPreferredConfig = Bitmap.Config.RGB_565;
            return target != null
                    ? BitmapFactory.decodeFile(target.getAbsolutePath(), opts)
                    : BitmapFactory.decodeResource(ctx.getResources(), DEFAULT_WALLPAPER, opts);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
