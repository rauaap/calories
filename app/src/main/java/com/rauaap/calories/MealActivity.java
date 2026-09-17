package com.rauaap.calories;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.TextView;

/** One diary meal: its items, editable amounts, and a quick-add bar for this meal. */
public class MealActivity extends Activity {
    static final String EXTRA_ID = "meal_id";

    private Db db;
    private long mealId;
    private Meal meal;
    private TextView title;
    private TextView totals;
    private ItemAdapter<Meal.Entry> adapter;
    private QuickAddBar quickAdd;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_meal);
        db = Db.get(this);
        mealId = getIntent().getLongExtra(EXTRA_ID, 0);
        title = findViewById(R.id.meal_title);
        totals = findViewById(R.id.meal_totals);
        quickAdd = findViewById(R.id.meal_quick_add);

        adapter = new ItemAdapter<>(R.layout.two_line_item) {
            @Override
            void bind(View view, Meal.Entry e) {
                ((TextView) view.findViewById(R.id.item_title)).setText(e.name);
                ((TextView) view.findViewById(R.id.item_subtitle)).setText(
                        Ui.join(Ui.amount(e.amount, e.unit), Ui.macros(e.nutrients)));
                ((TextView) view.findViewById(R.id.item_trailing)).setText(Ui.kcal(e.nutrients.kcal) + " kcal");
            }
        };
        ListView list = findViewById(R.id.meal_list);
        Ui.fitInsets(findViewById(R.id.root), Ui.followKeyboard(list));
        list.setAdapter(adapter);
        list.setEmptyView(findViewById(R.id.meal_empty));
        list.setOnItemClickListener((parent, view, position, id) -> {
            Meal.Entry e = adapter.getItem(position);
            Ui.amountDialog(this, e.name, e.amount, e.unit, amount -> {
                db.setEntryAmount(e, amount);
                render();
            }, () -> {
                db.deleteEntry(e.id);
                render();
            });
        });

        findViewById(R.id.meal_back).setOnClickListener(v -> finish());
        title.setOnClickListener(v -> rename());
        findViewById(R.id.meal_more).setOnClickListener(this::showMenu);

        quickAdd.setIncludePresets(true);
        quickAdd.setListener(new QuickAddBar.Listener() {
            @Override
            public void onAddFood(Food food, double amount) {
                db.addEntry(mealId, food, amount);
                render();
            }

            @Override
            public void onAddPreset(Preset preset, double times) {
                db.addPreset(mealId, preset, times);
                render();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
        quickAdd.reload();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (!quickAdd.onActivityResult(requestCode, resultCode, data)) super.onActivityResult(requestCode, resultCode, data);
    }

    private void render() {
        meal = db.meal(mealId);
        if (meal == null) {
            finish();
            return;
        }
        title.setText(meal.title());
        Nutrients t = meal.total();
        totals.setText(Ui.kcal(t.kcal) + " kcal · " + Ui.macros(t));
        adapter.set(meal.entries);
    }

    private void rename() {
        Ui.prompt(this, "Rename meal", meal.title(), name -> {
            db.renameMeal(mealId, name);
            render();
        });
    }

    private void showMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(0, 1, 0, "Rename");
        menu.getMenu().add(0, 2, 1, "Delete meal");
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                rename();
            } else {
                Ui.confirm(this, "Delete " + meal.title() + " and its " + meal.entries.size() + " items?",
                        "Delete", () -> {
                            db.deleteMeal(mealId);
                            finish();
                        });
            }
            return true;
        });
        menu.show();
    }
}
