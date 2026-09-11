package com.rauaap.calories;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

/** Builds a meal preset from foods. Changes are kept in memory until Save. */
public class PresetEditActivity extends Activity {
    static final String EXTRA_ID = "preset_id";

    private Preset preset;
    private EditText name;
    private TextView totals;
    private ItemAdapter<Preset.Item> adapter;
    private QuickAddBar quickAdd;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_preset_edit);
        Ui.fitInsets(findViewById(R.id.root));
        long id = getIntent().getLongExtra(EXTRA_ID, 0);
        preset = id > 0 ? Db.get(this).preset(id) : new Preset();
        if (preset == null) {
            finish();
            return;
        }

        name = findViewById(R.id.preset_name);
        totals = findViewById(R.id.preset_totals);
        quickAdd = findViewById(R.id.preset_quick_add);
        name.setText(preset.name);

        adapter = new ItemAdapter<>(R.layout.two_line_item) {
            @Override
            void bind(View view, Preset.Item item) {
                ((TextView) view.findViewById(R.id.item_title)).setText(item.food.name);
                ((TextView) view.findViewById(R.id.item_subtitle)).setText(Ui.amount(item.amount, item.food.unit));
                ((TextView) view.findViewById(R.id.item_trailing)).setText(
                        Ui.kcal(item.food.forAmount(item.amount).kcal) + " kcal");
            }
        };
        ListView list = findViewById(R.id.preset_list);
        list.setAdapter(adapter);
        list.setEmptyView(findViewById(R.id.preset_empty));
        list.setOnItemClickListener((parent, view, position, rowId) -> {
            Preset.Item item = adapter.getItem(position);
            Ui.amountDialog(this, item.food.name, item.amount, item.food.unit, amount -> {
                item.amount = amount;
                render();
            }, () -> {
                preset.items.remove(item);
                render();
            });
        });

        quickAdd.setListener(new QuickAddBar.Listener() {
            @Override
            public void onAddFood(Food food, double amount) {
                preset.items.add(new Preset.Item(food, amount));
                render();
            }

            @Override
            public void onAddPreset(Preset other, double times) {
            }
        });

        View delete = findViewById(R.id.preset_delete);
        delete.setVisibility(preset.id > 0 ? View.VISIBLE : View.GONE);
        delete.setOnClickListener(v -> Ui.confirm(this, "Delete " + preset.name + "?", "Delete", () -> {
            Db.get(this).deletePreset(preset.id);
            finish();
        }));
        findViewById(R.id.preset_back).setOnClickListener(v -> finish());
        findViewById(R.id.preset_save).setOnClickListener(v -> save());
        render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        quickAdd.reload();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (!quickAdd.onActivityResult(requestCode, resultCode, data)) super.onActivityResult(requestCode, resultCode, data);
    }

    private void render() {
        adapter.set(new ArrayList<>(preset.items));
        Nutrients t = preset.total();
        totals.setText(Ui.kcal(t.kcal) + " kcal · " + Ui.macros(t));
    }

    private void save() {
        String n = name.getText().toString().trim();
        if (n.isEmpty()) {
            name.setError("Give it a name");
            return;
        }
        if (preset.items.isEmpty()) {
            Toast.makeText(this, "Add at least one food", Toast.LENGTH_SHORT).show();
            return;
        }
        preset.name = n;
        Db.get(this).savePreset(preset);
        finish();
    }
}
