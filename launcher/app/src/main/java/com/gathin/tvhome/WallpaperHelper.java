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

    public static void loadPermanentWallpaper(Context ctx, ImageView iv) {
        if (ctx == null || iv == null) return;
        new Thread(() -> {
            File target = getWallpaperFile(ctx);

            Bitmap bmp = null;
            try {
                DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
                BitmapFactory.Options opts = new BitmapFactory.Options();
                if (target == null) {
                    // No readable file on the TV (Android 11 blocks /sdcard for apps): use the bundled
                    // photo so the home screen never falls back to a flat black backdrop.
                    opts.inPreferredConfig = Bitmap.Config.RGB_565;
                    bmp = BitmapFactory.decodeResource(ctx.getResources(), DEFAULT_WALLPAPER, opts);
                    final Bitmap bundled = bmp;
                    if (bundled != null) {
                        new Handler(Looper.getMainLooper()).post(() -> iv.setImageBitmap(bundled));
                    }
                    return;
                }
                opts.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(target.getAbsolutePath(), opts);

                int sample = 1;
                int targetW = dm.widthPixels > 0 ? dm.widthPixels : 1920;
                int targetH = dm.heightPixels > 0 ? dm.heightPixels : 1080;
                while (opts.outWidth / (sample * 2) >= targetW && opts.outHeight / (sample * 2) >= targetH) {
                    sample *= 2;
                }
                opts.inJustDecodeBounds = false;
                opts.inSampleSize = sample;
                opts.inPreferredConfig = Bitmap.Config.RGB_565; // High performance, 50% RAM
                bmp = BitmapFactory.decodeFile(target.getAbsolutePath(), opts);
            } catch (Throwable ignored) {}

            final Bitmap finalBmp = bmp;
            if (finalBmp != null) {
                new Handler(Looper.getMainLooper()).post(() -> iv.setImageBitmap(finalBmp));
            }
        }).start();
    }
}
