package com.gathin.tvhome;

import android.content.res.Resources;
import android.graphics.Rect;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Spaces the dock rows. Tiles float on the wallpaper with no shelf behind them; each row is the
 * tile plus dock_row_pad_top above and dock_row_pad_bottom below (room for the focused app's name),
 * so the dock container can show exactly one or two rows.
 */
public class DockDecoration extends RecyclerView.ItemDecoration {

    private final int padTop;
    private final int padBottom;

    DockDecoration(Resources res) {
        this.padTop = res.getDimensionPixelSize(R.dimen.dock_row_pad_top);
        this.padBottom = res.getDimensionPixelSize(R.dimen.dock_row_pad_bottom);
    }

    @Override
    public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent,
                               @NonNull RecyclerView.State state) {
        outRect.top = padTop;
        outRect.bottom = padBottom;
    }
}
