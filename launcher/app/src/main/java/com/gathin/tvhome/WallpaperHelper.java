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
 * Loads wallpaper dynamically from local TV storage without bundling inside the APK.
 * Keeps the APK ultra-lean while supporting high-res permanent wallpapers on home and lock screen.
 */
public class WallpaperHelper {

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
            if (target == null) return;

            Bitmap bmp = null;
            try {
                DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
                BitmapFactory.Options opts = new BitmapFactory.Options();
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
