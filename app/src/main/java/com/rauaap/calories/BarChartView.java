package com.rauaap.calories;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.function.DoubleFunction;

/**
 * Single-series daily bar chart. Days without data (NaN) are gaps. Bars start
 * at zero, or when scaled, at a round number just under the lowest day so it
 * never looks empty. Tapping a bar shows its value.
 */
public class BarChartView extends View {
    private static final DateTimeFormatter AXIS_DAY = DateTimeFormatter.ofPattern("d MMM");
    private static final DateTimeFormatter TIP_DAY = DateTimeFormatter.ofPattern("EEE d MMM");

    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint();
    private final Paint axisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tipPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tipTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();
    private final float[] radii = new float[8];

    private double[] values = new double[0];
    private long firstDay;
    /** Appended to values as-is, including any space ("g", " kcal"). */
    private String unit = "";
    private DoubleFunction<String> format = String::valueOf;
    private boolean scaled;
    private boolean hasData;
    private double lo;
    private double hi;
    private double step;
    private int selected = -1;
    private float plotLeft;
    private float plotRight;
    private float plotTop;
    private float plotBottom;
    private float slot;

    public BarChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        barPaint.setColor(context.getColor(R.color.accent));
        gridPaint.setColor(context.getColor(R.color.rule));
        gridPaint.setStrokeWidth(1);
        axisPaint.setColor(context.getColor(R.color.text_muted));
        axisPaint.setTextSize(sp(11));
        axisPaint.setFontFeatureSettings("tnum");
        tipPaint.setColor(context.getColor(R.color.surface_high));
        tipTextPaint.setColor(context.getColor(R.color.text_primary));
        tipTextPaint.setTextSize(sp(13));
    }

    void setData(long firstDay, double[] values, String unit, DoubleFunction<String> format, boolean scaled) {
        this.firstDay = firstDay;
        this.values = values;
        this.unit = unit;
        this.format = format;
        this.scaled = scaled;
        selected = -1;
        computeScale();
        invalidate();
    }

    private void computeScale() {
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (double v : values) {
            if (Double.isNaN(v)) continue;
            min = Math.min(min, v);
            max = Math.max(max, v);
        }
        hasData = min <= max;
        if (!hasData) return;
        if (scaled) {
            // Leave headroom under the lowest day so its bar never reads as zero.
            double spread = Math.max(max - min, Math.max(max, 1) * 0.1);
            double floor = Math.max(0, min - spread * 0.3);
            step = niceStep((max - floor) / 4);
            lo = Math.floor(floor / step) * step;
        } else {
            step = niceStep(Math.max(max, 1) / 4);
            lo = 0;
        }
        hi = Math.max(Math.ceil(max / step) * step, lo + step);
    }

    /** Rounds a raw tick step up to 1, 2, 2.5 or 5 times a power of ten. */
    static double niceStep(double raw) {
        if (!(raw > 0)) return 1;
        double magnitude = Math.pow(10, Math.floor(Math.log10(raw)));
        double f = raw / magnitude;
        double nice = f <= 1 ? 1 : f <= 2 ? 2 : f <= 2.5 ? 2.5 : f <= 5 ? 5 : 10;
        return nice * magnitude;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        if (!hasData) {
            axisPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("Nothing logged in this period", w / 2, h / 2, axisPaint);
            return;
        }

        int ticks = (int) Math.round((hi - lo) / step);
        float labelWidth = 0;
        for (int k = 0; k <= ticks; k++) labelWidth = Math.max(labelWidth, axisPaint.measureText(format.apply(lo + k * step)));
        float textHeight = axisPaint.getFontSpacing();
        plotLeft = getPaddingLeft() + labelWidth + dp(8);
        plotRight = w - getPaddingRight();
        plotTop = getPaddingTop() + textHeight / 2;
        plotBottom = h - getPaddingBottom() - textHeight - dp(6);
        float centerOffset = -(axisPaint.ascent() + axisPaint.descent()) / 2;

        // Hairline grid with y labels.
        axisPaint.setTextAlign(Paint.Align.RIGHT);
        for (int k = 0; k <= ticks; k++) {
            double t = lo + k * step;
            float y = y(t);
            canvas.drawLine(plotLeft, y, plotRight, y, gridPaint);
            canvas.drawText(format.apply(t), plotLeft - dp(8), y + centerOffset, axisPaint);
        }

        // Bars: capped width, 2dp gap, rounded data end, square baseline.
        int n = values.length;
        slot = (plotRight - plotLeft) / n;
        float gap = Math.min(dp(2), slot * 0.3f);
        float barWidth = Math.max(1, Math.min(dp(24), slot - gap));
        for (int i = 0; i < n; i++) {
            double v = values[i];
            if (Double.isNaN(v)) continue;
            float left = plotLeft + slot * i + (slot - barWidth) / 2;
            float top = Math.min(y(Math.max(v, lo)), plotBottom - 1);
            float r = Math.min(dp(4), Math.min(barWidth / 2, plotBottom - top));
            radii[0] = r;
            radii[1] = r;
            radii[2] = r;
            radii[3] = r;
            rect.set(left, top, left + barWidth, plotBottom);
            path.reset();
            path.addRoundRect(rect, radii, Path.Direction.CW);
            barPaint.setAlpha(selected >= 0 && i != selected ? 90 : 255);
            canvas.drawPath(path, barPaint);
        }

        // Date labels, anchored on the latest day and thinned so they don't collide.
        axisPaint.setTextAlign(Paint.Align.CENTER);
        float labelSpace = axisPaint.measureText(LocalDate.of(2026, 12, 28).format(AXIS_DAY)) + dp(12);
        int every = Math.max(1, (int) Math.ceil(labelSpace / slot));
        float baseline = h - getPaddingBottom() - axisPaint.descent();
        for (int i = n - 1; i >= 0; i -= every) {
            String label = LocalDate.ofEpochDay(firstDay + i).format(AXIS_DAY);
            float half = axisPaint.measureText(label) / 2;
            float x = Math.max(half, Math.min(plotLeft + slot * (i + 0.5f), w - half));
            canvas.drawText(label, x, baseline, axisPaint);
        }

        if (selected >= 0 && selected < n && !Double.isNaN(values[selected])) drawTip(canvas, w);
    }

    private void drawTip(Canvas canvas, float w) {
        double v = values[selected];
        String text = LocalDate.ofEpochDay(firstDay + selected).format(TIP_DAY) + "   " + format.apply(v) + unit;
        float pad = dp(10);
        float tipWidth = tipTextPaint.measureText(text) + pad * 2;
        float tipHeight = tipTextPaint.getFontSpacing() + dp(12);
        float center = plotLeft + slot * (selected + 0.5f);
        float left = Math.max(0, Math.min(center - tipWidth / 2, w - tipWidth));
        float top = Math.max(0, y(Math.max(v, lo)) - tipHeight - dp(6));
        rect.set(left, top, left + tipWidth, top + tipHeight);
        canvas.drawRoundRect(rect, dp(6), dp(6), tipPaint);
        float baseline = rect.centerY() - (tipTextPaint.ascent() + tipTextPaint.descent()) / 2;
        canvas.drawText(text, left + pad, baseline, tipTextPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (!hasData || slot <= 0) return false;
        if (e.getActionMasked() == MotionEvent.ACTION_UP) {
            // The whole column is the hit target, not just the bar.
            int i = (int) Math.floor((e.getX() - plotLeft) / slot);
            boolean valid = i >= 0 && i < values.length && !Double.isNaN(values[i]);
            selected = !valid || i == selected ? -1 : i;
            invalidate();
            performClick();
        }
        return true;
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    private float y(double v) {
        return (float) (plotBottom - (v - lo) / (hi - lo) * (plotBottom - plotTop));
    }

    private float dp(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private float sp(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, getResources().getDisplayMetrics());
    }
}
