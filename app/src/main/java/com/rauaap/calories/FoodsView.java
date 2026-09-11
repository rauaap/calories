package com.rauaap.calories;

import android.content.Context;
import android.content.Intent;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Saved foods and meal presets, with a filter, scanning and online search. */
public class FoodsView extends LinearLayout implements MainActivity.Screen {
    private final Db db;
    private final TextView foodsTab;
    private final TextView presetsTab;
    private final TextView empty;
    private final EditText filter;
    private final Button scan;
    private final Button search;
    private final Button add;
    private final ItemAdapter<Object> adapter;
    private List<Food> foods = new ArrayList<>();
    private List<Preset> presets = new ArrayList<>();
    private boolean showPresets;

    public FoodsView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOrientation(VERTICAL);
        inflate(context, R.layout.screen_foods, this);
        db = Db.get(context);
        foodsTab = findViewById(R.id.foods_tab_foods);
        presetsTab = findViewById(R.id.foods_tab_presets);
        empty = findViewById(R.id.foods_empty);
        filter = findViewById(R.id.foods_filter);
        scan = findViewById(R.id.foods_scan);
        search = findViewById(R.id.foods_search);
        add = findViewById(R.id.foods_add);

        adapter = new ItemAdapter<>(R.layout.two_line_item) {
            @Override
            void bind(View view, Object item) {
                bindItem(view, item);
            }
        };
        ListView list = findViewById(R.id.foods_list);
        list.setAdapter(adapter);
        list.setEmptyView(empty);
        list.setOnItemClickListener((parent, view, position, id) -> open(adapter.getItem(position)));

        foodsTab.setOnClickListener(v -> {
            showPresets = false;
            apply();
        });
        presetsTab.setOnClickListener(v -> {
            showPresets = true;
            apply();
        });
        Ui.onTextChanged(filter, this::apply);
        scan.setOnClickListener(v -> context.startActivity(new Intent(context, ScanActivity.class)));
        search.setOnClickListener(v -> context.startActivity(new Intent(context, OnlineSearchActivity.class)
                .putExtra(OnlineSearchActivity.EXTRA_QUERY, filter.getText().toString().trim())));
        add.setOnClickListener(v -> context.startActivity(
                new Intent(context, showPresets ? PresetEditActivity.class : FoodEditActivity.class)));
    }

    @Override
    public void refresh() {
        foods = db.foods();
        presets = db.presets();
        apply();
    }

    private void apply() {
        foodsTab.setSelected(!showPresets);
        presetsTab.setSelected(showPresets);
        List<?> source = showPresets ? presets : foods;
        String query = filter.getText().toString().trim();
        adapter.set(query.isEmpty() ? new ArrayList<>(source) : Search.rank(source, query, Integer.MAX_VALUE));
        if (!query.isEmpty()) empty.setText("Nothing matches “" + query + "”.");
        else if (showPresets) empty.setText("No meals yet. Combine foods you often eat together into one.");
        else empty.setText("No foods yet. Scan a barcode, search online, or add one yourself.");
        scan.setVisibility(showPresets ? GONE : VISIBLE);
        search.setVisibility(showPresets ? GONE : VISIBLE);
        add.setText(showPresets ? "New meal" : "Add food");
    }

    private void bindItem(View view, Object item) {
        TextView title = view.findViewById(R.id.item_title);
        TextView subtitle = view.findViewById(R.id.item_subtitle);
        TextView trailing = view.findViewById(R.id.item_trailing);
        if (item instanceof Food f) {
            title.setText(f.name);
            subtitle.setText(Ui.join(f.brand, "per " + f.perLabel(), "P " + Ui.grams(f.nutrients.protein)
                    + " · C " + Ui.grams(f.nutrients.carbs) + " · F " + Ui.grams(f.nutrients.fat)));
            trailing.setText(Ui.kcal(f.nutrients.kcal) + " kcal");
        } else {
            Preset p = (Preset) item;
            List<String> names = new ArrayList<>();
            for (Preset.Item i : p.items) names.add(i.food.name);
            title.setText(p.name);
            subtitle.setText(String.join(", ", names));
            trailing.setText(Ui.kcal(p.total().kcal) + " kcal");
        }
    }

    private void open(Object item) {
        Context c = getContext();
        if (item instanceof Food f) {
            c.startActivity(new Intent(c, FoodEditActivity.class).putExtra(FoodEditActivity.EXTRA_ID, f.id));
        } else {
            c.startActivity(new Intent(c, PresetEditActivity.class)
                    .putExtra(PresetEditActivity.EXTRA_ID, ((Preset) item).id));
        }
    }
}
