package com.rauaap.calories;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import java.util.function.Consumer;

/** Widget settings: macros under the calories, colors and corner radius, with a live preview. */
public class WidgetConfigActivity extends Activity {
    private int widgetId;
    private CalorieWidget.Config cfg;
    private Nutrients today;
    private FrameLayout preview;
    private TextView radiusValue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setResult(RESULT_CANCELED);
        widgetId = getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish();
            return;
        }
        setContentView(R.layout.activity_widget_config);
        Ui.fitInsets(findViewById(R.id.root));
        cfg = CalorieWidget.Config.load(this, widgetId);
        today = Db.get(this).dayTotal(Days.today(this));
        preview = findViewById(R.id.widget_preview);
        radiusValue = findViewById(R.id.widget_radius_value);

        bindSwitch(R.id.widget_protein, cfg.protein, on -> cfg.protein = on);
        bindSwitch(R.id.widget_carbs, cfg.carbs, on -> cfg.carbs = on);
        bindSwitch(R.id.widget_fat, cfg.fat, on -> cfg.fat = on);
        findViewById(R.id.widget_text_color).setOnClickListener(v ->
                ColorPickerView.show(this, "Text color", cfg.textColor, color -> {
                    cfg.textColor = color;
                    render();
                }));
        findViewById(R.id.widget_bg_color).setOnClickListener(v ->
                ColorPickerView.show(this, "Background color", cfg.backgroundColor, color -> {
                    cfg.backgroundColor = color;
                    render();
                }));
        SeekBar radius = findViewById(R.id.widget_radius);
        radius.setProgress(cfg.radius);
        radius.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                cfg.radius = progress;
                render();
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
            }
        });
        findViewById(R.id.widget_close).setOnClickListener(v -> finish());
        findViewById(R.id.widget_save).setOnClickListener(v -> save());
        render();
    }

    private void bindSwitch(int id, boolean value, Consumer<Boolean> set) {
        Switch s = findViewById(id);
        s.setChecked(value);
        s.setOnCheckedChangeListener((button, on) -> {
            set.accept(on);
            render();
        });
    }

    private void render() {
        preview.removeAllViews();
        preview.addView(CalorieWidget.views(this, cfg, today).apply(this, preview));
        swatch(R.id.widget_text_swatch, R.id.widget_text_hex, cfg.textColor);
        swatch(R.id.widget_bg_swatch, R.id.widget_bg_hex, cfg.backgroundColor);
        radiusValue.setText(cfg.radius + " dp");
    }

    private void swatch(int swatchId, int hexId, int color) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        d.setStroke(Ui.dp(this, 1), getColor(R.color.text_muted));
        View swatch = findViewById(swatchId);
        swatch.setBackground(d);
        ((TextView) findViewById(hexId)).setText(ColorPickerView.hex(color));
    }

    private void save() {
        cfg.save(this, widgetId);
        CalorieWidget.update(this, AppWidgetManager.getInstance(this), widgetId);
        setResult(RESULT_OK, new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId));
        finish();
    }
}
