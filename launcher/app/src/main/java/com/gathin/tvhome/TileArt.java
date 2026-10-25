package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Full-bleed 16:9 artwork for dock tiles of apps that ship no good TV banner (Xiaomi TV+, Live TV,
 * Settings, the media player, …): a brand gradient with a soft light bloom, a glyph and a title.
 * Apps with a proper banner use it directly; this keeps the rest at the same level of polish.
 */
final class TileArt extends Drawable {

    /** Brand look for one package: gradient colours, glyph, title and an optional small caption. */
    static final class Spec {
        final int top, bottom, glyphRes;
        final String title, caption;

        Spec(int top, int bottom, int glyphRes, String title, String caption) {
            this.top = top;
            this.bottom = bottom;
            this.glyphRes = glyphRes;
            this.title = title;
            this.caption = caption;
        }
    }

    private static final Map<String, Spec> SPECS = new HashMap<>();
    static {
        SPECS.put("com.mitv.tvhome.mitvplus",
                new Spec(0xFFFF9F0A, 0xFFFF375F, R.drawable.ic_livetv, "Xiaomi TV+", "FREE LIVE CHANNELS"));
        SPECS.put("com.mitv.livetv",
                new Spec(0xFF0A84FF, 0xFF5E5CE6, R.drawable.ic_livetv, "Live TV", "ANTENNA · CABLE"));
        SPECS.put("com.xiaomi.mitv.mediaexplorer",
                new Spec(0xFFBF5AF2, 0xFF5E5CE6, R.drawable.ic_media_library, "Media Player", "USB · LOCAL FILES"));
        SPECS.put("com.xiaomi.mitv.smartshare",
                new Spec(0xFF64D2FF, 0xFF0A84FF, R.drawable.ic_wifi, "Screen Cast", "MIRACAST"));
        SPECS.put("com.android.tv.settings",
                new Spec(0xFF8E8E93, 0xFF3A3A3C, R.drawable.ic_settings, "Settings", null));
        SPECS.put("com.xiaomi.gtv.settings",
                new Spec(0xFF8E8E93, 0xFF3A3A3C, R.drawable.ic_settings, "Settings", null));
    }

    /** The custom look for this package, or null when the app's own banner should be used. */
    @Nullable
    static Spec specFor(@Nullable String pkg) {
        return pkg == null ? null : SPECS.get(pkg);
    }

    /** A look derived from an accent colour, for apps with neither a banner nor a custom spec. */
    static Spec fromAccent(int accent, String title) {
        float[] hsv = new float[3];
        Color.colorToHSV(accent, hsv);
        float[] top = {hsv[0], Math.min(1f, hsv[1] * 0.9f), Math.max(0.55f, hsv[2])};
        float[] bottom = {(hsv[0] + 18f) % 360f, Math.min(1f, hsv[1] * 1.05f), Math.max(0.35f, hsv[2] * 0.6f)};
        return new Spec(Color.HSVToColor(top), Color.HSVToColor(bottom), 0, title, null);
    }

    private final Spec spec;
    private final Drawable glyph;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bloom = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint title = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint caption = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;

    /** @param icon drawn instead of the spec's glyph when non-null (an app's own icon, untinted) */
    TileArt(Context ctx, Spec spec, @Nullable Drawable icon) {
        this.spec = spec;
        this.density = ctx.getResources().getDisplayMetrics().density;
        if (icon != null) {
            glyph = icon.getConstantState() != null ? icon.getConstantState().newDrawable().mutate() : icon;
        } else if (spec.glyphRes != 0) {
            glyph = ctx.getDrawable(spec.glyphRes).mutate();
            glyph.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));
        } else {
            glyph = null;
        }

        // Fixed-weight font files: variation settings would build a new typeface for every tile.
        title.setTypeface(ctx.getResources().getFont(R.font.outfit_bold));
        title.setColor(Color.WHITE);
        title.setShadowLayer(6 * density, 0, density, 0x40000000);
        caption.setTypeface(ctx.getResources().getFont(R.font.outfit_semibold));
        caption.setColor(0xD9FFFFFF);
        caption.setLetterSpacing(0.14f);
    }

    @Override
    protected void onBoundsChange(@NonNull Rect b) {
        fill.setShader(new LinearGradient(b.left, b.top, b.right, b.bottom,
                spec.top, spec.bottom, Shader.TileMode.CLAMP));
        // Soft light bloom from the top-left, the way Apple TV artwork catches light.
        float r = Math.max(b.width(), b.height()) * 0.9f;
        bloom.setShader(new RadialGradient(b.left + b.width() * 0.2f, b.top, r,
                0x47FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
    }

    @Override
    public void draw(@NonNull Canvas c) {
        Rect b = getBounds();
        c.drawRect(b, fill);
        c.drawRect(b, bloom);

        float scale = b.width() / (180f * density);   // artwork is laid out for the 180dp design tile
        float glyphSize = 34 * density * scale;
        float titleSize = 15 * density * scale;
        float captionSize = 7.5f * density * scale;
        title.setTextSize(titleSize);
        caption.setTextSize(captionSize);

        boolean hasCaption = spec.caption != null;
        float gap = 8 * density * scale;
        float block = (glyph != null ? glyphSize + gap : 0) + titleSize
                + (hasCaption ? 5 * density * scale + captionSize : 0);
        float y = b.exactCenterY() - block / 2f;

        if (glyph != null) {
            int gx = Math.round(b.exactCenterX() - glyphSize / 2f);
            int gy = Math.round(y);
            glyph.setBounds(gx, gy, gx + Math.round(glyphSize), gy + Math.round(glyphSize));
            glyph.draw(c);
            y += glyphSize + gap;
        }

        String t = ellipsize(spec.title, title, b.width() - 24 * density * scale);
        c.drawText(t, b.exactCenterX() - title.measureText(t) / 2f, y + titleSize * 0.8f, title);
        y += titleSize;

        if (hasCaption) {
            y += 5 * density * scale;
            String cap = ellipsize(spec.caption, caption, b.width() - 24 * density * scale);
            c.drawText(cap, b.exactCenterX() - caption.measureText(cap) / 2f, y + captionSize * 0.8f, caption);
        }
    }

    private static String ellipsize(String s, Paint p, float max) {
        if (p.measureText(s) <= max) return s;
        String e = "…";
        int n = s.length();
        while (n > 1 && p.measureText(s, 0, n) + p.measureText(e) > max) n--;
        return s.substring(0, n) + e;
    }

    @Override
    public void setAlpha(int alpha) {
        fill.setAlpha(alpha);
        title.setAlpha(alpha);
        caption.setAlpha(alpha);
        invalidateSelf();
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter cf) {
        fill.setColorFilter(cf);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.OPAQUE;
    }
}
