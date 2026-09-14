package com.rauaap.calories;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.method.DigitsKeyListener;
import android.view.View;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Day start, chart baseline, and backup / restore / waistline import. */
public class SettingsActivity extends Activity {
    private static final int REQUEST_EXPORT = 1;
    private static final int REQUEST_RESTORE = 2;
    private static final int REQUEST_WAISTLINE = 3;

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private TextView dayStart;
    private Switch focusQuickAdd;
    private Switch autoMeals;
    private View intervalRow;
    private EditText interval;
    private Switch chartScaled;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        Ui.fitInsets(findViewById(R.id.root));
        dayStart = findViewById(R.id.settings_day_start_value);
        focusQuickAdd = findViewById(R.id.settings_focus_quick);
        autoMeals = findViewById(R.id.settings_auto_meals);
        intervalRow = findViewById(R.id.settings_interval_row);
        interval = findViewById(R.id.settings_interval);
        chartScaled = findViewById(R.id.settings_chart_scaled);

        findViewById(R.id.settings_back).setOnClickListener(v -> finish());
        findViewById(R.id.settings_day_start).setOnClickListener(v -> pickDayStart());
        findViewById(R.id.settings_focus_row).setOnClickListener(v -> focusQuickAdd.toggle());
        focusQuickAdd.setOnCheckedChangeListener((button, on) -> Prefs.setFocusQuickAdd(this, on));
        findViewById(R.id.settings_auto_meals_row).setOnClickListener(v -> autoMeals.toggle());
        autoMeals.setOnCheckedChangeListener((button, on) -> {
            Prefs.setAutoMeals(this, on);
            updateIntervalRow();
        });
        intervalRow.setOnClickListener(v -> Ui.showKeyboard(interval));
        bindInterval();
        findViewById(R.id.settings_chart_row).setOnClickListener(v -> chartScaled.toggle());
        chartScaled.setOnCheckedChangeListener((button, on) -> Prefs.setChartScaled(this, on));
        findViewById(R.id.settings_export).setOnClickListener(v -> startActivityForResult(
                new Intent(Intent.ACTION_CREATE_DOCUMENT)
                        .addCategory(Intent.CATEGORY_OPENABLE)
                        .setType("application/json")
                        .putExtra(Intent.EXTRA_TITLE, "calories-backup-" + LocalDate.now() + ".json"),
                REQUEST_EXPORT));
        findViewById(R.id.settings_restore).setOnClickListener(v -> openDocument(REQUEST_RESTORE));
        findViewById(R.id.settings_waistline).setOnClickListener(v -> openDocument(REQUEST_WAISTLINE));
        render();
    }

    @Override
    protected void onDestroy() {
        io.shutdown();
        super.onDestroy();
    }

    private void render() {
        dayStart.setText(Ui.clock(Prefs.dayStart(this)));
        focusQuickAdd.setChecked(Prefs.focusQuickAdd(this));
        autoMeals.setChecked(Prefs.autoMeals(this));
        interval.setText(Ui.clock(Prefs.autoMealMinutes(this)));
        chartScaled.setChecked(Prefs.chartScaled(this));
        updateIntervalRow();
    }

    /** The interval only applies with auto meals on, so grey it out otherwise. */
    private void updateIntervalRow() {
        boolean on = autoMeals.isChecked();
        intervalRow.setAlpha(on ? 1f : 0.4f);
        intervalRow.setEnabled(on);
        interval.setEnabled(on);
    }

    /** Digits only, with the colon filled in after the hours: typing 02 shows "02:". */
    private void bindInterval() {
        interval.setKeyListener(DigitsKeyListener.getInstance("0123456789"));
        interval.setRawInputType(InputType.TYPE_CLASS_NUMBER);
        interval.addTextChangedListener(new TextWatcher() {
            private boolean formatting;
            private boolean deleting;

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                deleting = after < count;
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (formatting) return;
                formatting = true;
                String raw = s.toString();
                String digits = raw.replaceAll("\\D", "");
                if (digits.length() > 4) digits = digits.substring(0, 4);
                // Backspacing the colon the field added takes the hour digit with it, so
                // "02:" goes straight to "0". Deleting a minute digit keeps the colon.
                if (deleting && digits.length() == 2 && raw.indexOf(':') < 0) digits = digits.substring(0, 1);
                String text = digits.length() < 2 ? digits : digits.substring(0, 2) + ":" + digits.substring(2);
                if (!text.contentEquals(s)) s.replace(0, s.length(), text);
                formatting = false;
                if (digits.length() == 4 && !deleting) {
                    int hours = Integer.parseInt(digits.substring(0, 2));
                    int minutes = Math.min(Integer.parseInt(digits.substring(2)), 59);
                    Prefs.setAutoMealMinutes(SettingsActivity.this, hours * 60 + minutes);
                    // The last digit means they're done: drop the cursor and the keyboard.
                    interval.clearFocus();
                    Ui.hideKeyboard(interval);
                }
            }
        });
        // Enter (or Done) finishes editing rather than leaving the cursor sitting there.
        interval.setOnEditorActionListener((v, action, event) -> {
            if (!Ui.isEnter(action, event)) return false;
            if (Ui.isPress(event)) {
                interval.clearFocus();
                Ui.hideKeyboard(interval);
            }
            return true;
        });
        // Half-typed input reverts to what's saved.
        interval.setOnFocusChangeListener((v, focused) -> {
            if (!focused) interval.setText(Ui.clock(Prefs.autoMealMinutes(this)));
        });
    }

    private void pickDayStart() {
        int m = Prefs.dayStart(this);
        new TimePickerDialog(this, (view, hour, minute) -> {
            Prefs.setDayStart(this, hour * 60 + minute);
            render();
            CalorieWidget.refresh(this);
        }, m / 60, m % 60, true).show();
    }

    private void openDocument(int request) {
        // Any type: file managers often label .json as octet-stream.
        startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*"), request);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (requestCode == REQUEST_EXPORT) export(uri);
        else if (requestCode == REQUEST_RESTORE) load(uri, false);
        else if (requestCode == REQUEST_WAISTLINE) load(uri, true);
    }

    private void export(Uri uri) {
        int start = Prefs.dayStart(this);
        boolean scaled = Prefs.chartScaled(this);
        io.execute(() -> {
            try (OutputStream out = getContentResolver().openOutputStream(uri, "wt")) {
                if (out == null) throw new IOException("can't open the file");
                String json = Backup.toJson(Db.get(this).snapshot(), start, scaled).toString(1);
                out.write(json.getBytes(StandardCharsets.UTF_8));
                toast("Backup saved");
            } catch (IOException | JSONException e) {
                toast("Export failed: " + e.getMessage());
            }
        });
    }

    private void load(Uri uri, boolean waistline) {
        io.execute(() -> {
            try {
                String json;
                try (InputStream in = getContentResolver().openInputStream(uri)) {
                    if (in == null) throw new IOException("can't open the file");
                    json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                }
                ImportData data = waistline ? WaistlineImport.parse(json) : Backup.parse(json);
                runOnUiThread(() -> confirmImport(data, waistline));
            } catch (IOException | JSONException | RuntimeException e) {
                toast("Import failed: " + e.getMessage());
            }
        });
    }

    private void confirmImport(ImportData data, boolean waistline) {
        if (isFinishing()) return;
        String summary = data.foods.size() + " foods, " + data.dayCount() + " diary days ("
                + data.entryCount() + " items) and " + data.presets.size() + " meals";
        String message = waistline
                ? "Add " + summary + " from waistline? Foods you already have are reused. "
                        + "Importing the same file twice duplicates the diary."
                : "Replace everything with " + summary + " from this backup? Your current data will be deleted.";
        if (data.skipped > 0) {
            message += "\n\n" + data.skipped + " diary items will be skipped (no food, or burned calories).";
        }
        new AlertDialog.Builder(this)
                .setTitle(waistline ? "Import from waistline" : "Restore backup")
                .setMessage(message)
                .setPositiveButton(waistline ? "Import" : "Replace", (d, w) -> io.execute(() -> {
                    Db.get(this).importData(data, !waistline);
                    if (data.dayStart != null) Prefs.setDayStart(this, data.dayStart);
                    if (data.chartScaled != null) Prefs.setChartScaled(this, data.chartScaled);
                    runOnUiThread(this::render);
                    toast("Import complete");
                }))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void toast(String message) {
        runOnUiThread(() -> Toast.makeText(this, message, Toast.LENGTH_LONG).show());
    }
}
