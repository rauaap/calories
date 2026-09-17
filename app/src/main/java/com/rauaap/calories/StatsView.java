package com.rauaap.calories;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Map;
import java.util.function.DoubleFunction;
import java.util.function.IntConsumer;

/** Daily totals for one metric over a range, as a bar chart plus a by-day table. */
public class StatsView extends FrameLayout implements MainActivity.Screen {
    private static final String[] METRICS = {"Calories", "Fat", "Carbs", "Protein"};
    private static final int[] METRIC_IDS = {Nutrients.KCAL, Nutrients.FAT, Nutrients.CARBS, Nutrients.PROTEIN};
    private static final int[] RANGES = {7, 14, 30, 0};
    private static final String[] RANGE_LABELS = {"7 days", "14 days", "30 days", "All"};

    private final TextView[] metricChips = new TextView[METRICS.length];
    private final TextView[] rangeChips = new TextView[RANGES.length];
    private final TextView title;
    private final TextView avg;
    private final TextView min;
    private final TextView max;
    private final TextView logged;
    private final LinearLayout days;
    private final BarChartView chart;
    private int metric;
    private int range;

    public StatsView(Context context, AttributeSet attrs) {
        super(context, attrs);
        inflate(context, R.layout.screen_stats, this);
        title = findViewById(R.id.stats_title);
        avg = findViewById(R.id.stats_avg);
        min = findViewById(R.id.stats_min);
        max = findViewById(R.id.stats_max);
        logged = findViewById(R.id.stats_logged);
        days = findViewById(R.id.stats_days);
        chart = findViewById(R.id.stats_chart);
        addChips(R.id.stats_metrics, METRICS, metricChips, i -> metric = i);
        addChips(R.id.stats_ranges, RANGE_LABELS, rangeChips, i -> range = i);
    }

    private void addChips(int containerId, String[] labels, TextView[] out, IntConsumer onPick) {
        LinearLayout container = findViewById(containerId);
        LayoutInflater inflater = LayoutInflater.from(getContext());
        for (int i = 0; i < labels.length; i++) {
            TextView chip = (TextView) inflater.inflate(R.layout.chip, container, false);
            chip.setText(labels[i]);
            int index = i;
            chip.setOnClickListener(v -> {
                onPick.accept(index);
                refresh();
            });
            container.addView(chip);
            out[i] = chip;
        }
    }

    @Override
    public void refresh() {
        Context c = getContext();
        Db db = Db.get(c);
        for (int i = 0; i < metricChips.length; i++) metricChips[i].setSelected(i == metric);
        for (int i = 0; i < rangeChips.length; i++) rangeChips[i].setSelected(i == range);

        long today = Days.today(c);
        long from;
        if (RANGES[range] > 0) {
            from = today - RANGES[range] + 1;
        } else {
            Long first = db.firstDay();
            from = first == null ? today : Math.min(first, today);
        }
        int n = (int) (today - from + 1);
        Map<Long, Nutrients> totals = db.dailyTotals(from, today);

        // Days with nothing logged are gaps, not zeros, so they don't drag down the stats.
        double[] values = new double[n];
        double sum = 0;
        double lo = Double.POSITIVE_INFINITY;
        double hi = Double.NEGATIVE_INFINITY;
        int count = 0;
        for (int i = 0; i < n; i++) {
            Nutrients t = totals.get(from + i);
            values[i] = t == null ? Double.NaN : t.get(METRIC_IDS[metric]);
            if (t == null) continue;
            sum += values[i];
            lo = Math.min(lo, values[i]);
            hi = Math.max(hi, values[i]);
            count++;
        }

        // Grams sit right against the number ("42g"); calories keep a space ("1,850 kcal").
        String unit = METRIC_IDS[metric] == Nutrients.KCAL ? " kcal" : "g";
        DoubleFunction<String> format = METRIC_IDS[metric] == Nutrients.KCAL ? Ui::kcal : Ui::grams;
        chart.setData(from, values, unit, format, Prefs.chartScaled(c));
        title.setText(METRICS[metric] + " · " + (RANGES[range] > 0 ? "last " + RANGES[range] + " days" : "all time"));
        avg.setText(count > 0 ? format.apply(sum / count) + unit : "–");
        min.setText(count > 0 ? format.apply(lo) + unit : "–");
        max.setText(count > 0 ? format.apply(hi) + unit : "–");
        logged.setText(count + " of " + n + " days logged. Days with nothing logged are left out.");

        days.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(c);
        for (int i = n - 1; i >= 0; i--) {
            View row = inflater.inflate(R.layout.entry_row, days, false);
            ((TextView) row.findViewById(R.id.entry_name)).setText(Days.label(c, from + i));
            row.findViewById(R.id.entry_amount).setVisibility(GONE);
            ((TextView) row.findViewById(R.id.entry_kcal)).setText(
                    Double.isNaN(values[i]) ? "–" : format.apply(values[i]) + unit);
            days.addView(row);
        }
    }
}
