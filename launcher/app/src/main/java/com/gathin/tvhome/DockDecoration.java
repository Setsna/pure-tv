package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.HashMap;
import java.util.Map;

/** Draws the tvOS style lighter band behind app rows (both primary dock and secondary drawer). */
public class DockDecoration extends RecyclerView.ItemDecoration {

    private final int dockSize;
    private final float density;
    private final Paint band = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    DockDecoration(int dockSize, float density) {
        this.dockSize = dockSize;
        this.density = density;
        band.setColor(0xD61A1C22); // Night: rgba(26, 28, 34, 0.84) neutral charcoal
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(density);
        edge.setColor(0x26F5F2EB); // Night: rgba(245, 242, 235, 0.15) subtle warm ivory edge
    }

    void setDayMode(boolean isDay) {
        if (isDay) {
            band.setColor(0x73F5F4EF); // Day: rgba(245, 244, 239, 0.45) translucent warm-white glass
            edge.setColor(0x26191A1F); // Day: rgba(25, 26, 31, 0.15) subtle warm edge
        } else {
            band.setColor(0xD61A1C22); // Night: rgba(26, 28, 34, 0.84) neutral charcoal
            edge.setColor(0x26F5F2EB); // Night: rgba(245, 242, 235, 0.15) subtle warm ivory edge
        }
    }

    @Override
    public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent,
                               @NonNull RecyclerView.State state) {
        int pos = parent.getChildAdapterPosition(view);
        if (pos >= 0 && pos < dockSize) {
            outRect.top = (int) (6 * density);
            outRect.bottom = (int) (48 * density);
        } else if (pos >= dockSize) {
            outRect.top = (int) (6 * density);
            outRect.bottom = (int) (48 * density);
        }
    }

    @Override
    public void onDraw(@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        float pad = 14 * density;
        float radius = 30 * density;

        int childCount = parent.getChildCount();
        if (childCount == 0) return;

        // Group cards by row index so both Row 0 (dock) and Row 1 (secondary) have a cohesive shelf
        Map<Integer, RectF> rowBounds = new HashMap<>();
        for (int i = 0; i < childCount; i++) {
            View child = parent.getChildAt(i);
            int pos = parent.getChildAdapterPosition(child);
            if (pos < 0) continue;
            int row = pos / dockSize;
            View card = child.findViewById(R.id.card);
            if (card == null) continue;

            float l = child.getLeft() + card.getLeft() + child.getTranslationX();
            float t = child.getTop() + card.getTop() + child.getTranslationY();
            float r = l + card.getWidth();
            float b = t + card.getHeight();

            RectF bounds = rowBounds.get(row);
            if (bounds == null) {
                bounds = new RectF(l, t, r, b);
                rowBounds.put(row, bounds);
            } else {
                bounds.union(l, t, r, b);
            }
        }

        for (RectF r : rowBounds.values()) {
            rect.set(r);
            rect.inset(-pad, -pad);
            c.drawRoundRect(rect, radius, radius, band);
            c.drawRoundRect(rect, radius, radius, edge);
        }
    }
}
