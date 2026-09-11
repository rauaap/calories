package com.rauaap.calories;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.text.format.DateFormat;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Date;
import java.util.List;

/**
 * One diary day: totals, the day's meals, a + button for a new meal, and the
 * quick-add bar, which always adds to the day's most recent meal.
 */
public class DiaryView extends LinearLayout implements MainActivity.Screen {
    private final Db db;
    private final TextView date;
    private final TextView total;
    private final TextView macros;
    private final TextView empty;
    private final LinearLayout meals;
    private final ScrollView scroll;
    private final QuickAddBar quickAdd;
    private long day;
    private boolean followToday = true;

    public DiaryView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOrientation(VERTICAL);
        inflate(context, R.layout.screen_diary, this);
        db = Db.get(context);
        date = findViewById(R.id.diary_date);
        total = findViewById(R.id.diary_total);
        macros = findViewById(R.id.diary_macros);
        empty = findViewById(R.id.diary_empty);
        meals = findViewById(R.id.diary_meals);
        scroll = findViewById(R.id.diary_scroll);
        quickAdd = findViewById(R.id.diary_quick_add);

        findViewById(R.id.diary_prev).setOnClickListener(v -> go(day - 1));
        findViewById(R.id.diary_next).setOnClickListener(v -> go(day + 1));
        date.setOnClickListener(v -> go(Days.today(getContext())));
        findViewById(R.id.diary_add_meal).setOnClickListener(v -> {
            db.addMeal(day);
            render();
            scrollToEnd();
        });
        quickAdd.setIncludePresets(true);
        quickAdd.setListener(new QuickAddBar.Listener() {
            @Override
            public void onAddFood(Food food, double amount) {
                db.addEntry(targetMeal(), food, amount);
                render();
                scrollToEnd();
            }

            @Override
            public void onAddPreset(Preset preset, double times) {
                db.addPreset(targetMeal(), preset, times);
                render();
                scrollToEnd();
            }
        });
    }

    @Override
    public void refresh() {
        if (followToday) day = Days.today(getContext());
        render();
        quickAdd.reload();
    }

    /** Puts the cursor in the quick-add food field, keyboard up. */
    void focusQuickAdd() {
        quickAdd.focusFood();
    }

    boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        return quickAdd.onActivityResult(requestCode, resultCode, data);
    }

    private void go(long newDay) {
        day = newDay;
        followToday = newDay == Days.today(getContext());
        render();
    }

    /** The meal quick add writes to: the latest one, created if the day has none. */
    private long targetMeal() {
        long id = db.lastMealId(day);
        return id >= 0 ? id : db.addMeal(day);
    }

    private void render() {
        Context c = getContext();
        date.setText(Days.label(c, day));
        List<Meal> list = db.meals(day);
        Nutrients sum = new Nutrients();
        for (Meal m : list) sum.add(m.total());
        total.setText(Ui.kcal(sum.kcal));
        macros.setText(Ui.macros(sum));

        meals.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(c);
        for (int i = 0; i < list.size(); i++) {
            View card = inflater.inflate(R.layout.meal_card, meals, false);
            bindMeal(inflater, card, list.get(i), i == list.size() - 1);
            meals.addView(card);
        }
        empty.setVisibility(list.isEmpty() ? VISIBLE : GONE);
    }

    private void bindMeal(LayoutInflater inflater, View card, Meal meal, boolean target) {
        Context c = getContext();
        Nutrients t = meal.total();
        TextView name = card.findViewById(R.id.meal_name);
        name.setText(meal.title());
        name.setTextColor(c.getColor(target ? R.color.accent : R.color.text_primary));
        ((TextView) card.findViewById(R.id.meal_kcal)).setText(Ui.kcal(t.kcal) + " kcal");
        String time = DateFormat.getTimeFormat(c).format(new Date(meal.created));
        ((TextView) card.findViewById(R.id.meal_meta)).setText(
                Ui.join(target ? "Quick add goes here" : null, time, Ui.macros(t)));

        LinearLayout rows = card.findViewById(R.id.meal_entries);
        for (Meal.Entry e : meal.entries) {
            View row = inflater.inflate(R.layout.entry_row, rows, false);
            ((TextView) row.findViewById(R.id.entry_name)).setText(e.name);
            ((TextView) row.findViewById(R.id.entry_amount)).setText(Ui.amount(e.amount, e.unit));
            ((TextView) row.findViewById(R.id.entry_kcal)).setText(Ui.kcal(e.nutrients.kcal) + " kcal");
            rows.addView(row);
        }
        rows.setVisibility(meal.entries.isEmpty() ? GONE : VISIBLE);

        card.setOnClickListener(v -> c.startActivity(new Intent(c, MealActivity.class)
                .putExtra(MealActivity.EXTRA_ID, meal.id)));
        card.setOnLongClickListener(v -> {
            mealOptions(meal);
            return true;
        });
    }

    private void mealOptions(Meal meal) {
        Context c = getContext();
        new AlertDialog.Builder(c)
                .setTitle(meal.title())
                .setItems(new String[] {"Rename", "Delete"}, (d, which) -> {
                    if (which == 0) {
                        Ui.prompt(c, "Rename meal", meal.title(), name -> {
                            db.renameMeal(meal.id, name);
                            render();
                        });
                    } else {
                        Ui.confirm(c, "Delete " + meal.title() + " and its " + meal.entries.size() + " items?",
                                "Delete", () -> {
                                    db.deleteMeal(meal.id);
                                    render();
                                });
                    }
                })
                .show();
    }

    /** Scrolls without moving focus, so the quick-add field keeps the keyboard. */
    private void scrollToEnd() {
        scroll.post(() -> scroll.smoothScrollTo(0, meals.getBottom()));
    }
}
