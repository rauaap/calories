package com.rauaap.calories;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

/** Lays children out left to right, wrapping onto the next line. Holds the diary's entry pills. */
public class FlowLayout extends ViewGroup {
    private final int gap;

    public FlowLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        gap = Ui.dp(context, 6);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec) - getPaddingLeft() - getPaddingRight();
        int childHeightSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        int x = 0;
        int rowHeight = 0;
        int height = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) continue;
            int childWidthMode = child.getLayoutParams().width == LayoutParams.MATCH_PARENT
                    ? MeasureSpec.EXACTLY : MeasureSpec.AT_MOST;
            child.measure(MeasureSpec.makeMeasureSpec(width, childWidthMode), childHeightSpec);
            int w = child.getMeasuredWidth();
            if (x > 0 && x + w > width) {
                height += rowHeight + gap;
                x = 0;
                rowHeight = 0;
            }
            x += w + gap;
            rowHeight = Math.max(rowHeight, child.getMeasuredHeight());
        }
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec),
                height + rowHeight + getPaddingTop() + getPaddingBottom());
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int width = right - left - getPaddingRight();
        int x = getPaddingLeft();
        int y = getPaddingTop();
        int rowHeight = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) continue;
            int w = child.getMeasuredWidth();
            int h = child.getMeasuredHeight();
            if (x > getPaddingLeft() && x + w > width) {
                x = getPaddingLeft();
                y += rowHeight + gap;
                rowHeight = 0;
            }
            child.layout(x, y, x + w, y + h);
            x += w + gap;
            rowHeight = Math.max(rowHeight, h);
        }
    }
}
