package com.rauaap.calories;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.text.format.DateFormat;
import android.util.AttributeSet;
import android.view.DragEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
    /** The pill being dragged, dimmed until the drag ends. */
    private View dragging;
    /** -1 up, 1 down, 0 not scrolling: the diary scrolls while a pill is held near an edge. */
    private int autoScroll;
    private final Runnable autoScroller = new Runnable() {
        @Override
        public void run() {
            if (autoScroll == 0) return;
            scroll.scrollBy(0, autoScroll * Ui.dp(getContext(), 8));
            postDelayed(this, 16);
        }
    };
    /** Redraws the latest meal when its auto-meal interval ends. */
    private final Runnable expireTarget = this::render;

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

    /**
     * The meal quick add writes to: the day's latest one, or a new one when it has
     * none. With auto meals on, a new meal also starts once the latest one is older
     * than the chosen interval. Only on today: on earlier days "how long ago" means
     * nothing, so those always use their latest meal.
     */
    private long targetMeal() {
        Meal last = db.lastMeal(day);
        if (last == null) return db.addMeal(day);
        if (targetExpiresAt(last) <= System.currentTimeMillis()) return db.addMeal(day);
        return last.id;
    }

    /** The instant at which quick add will stop targeting this meal, or never. */
    private long targetExpiresAt(Meal meal) {
        Context c = getContext();
        if (day != Days.today(c) || !Prefs.autoMeals(c)) return Long.MAX_VALUE;
        return meal.created + Prefs.autoMealMinutes(c) * 60_000L;
    }

    private void render() {
        removeCallbacks(expireTarget);
        Context c = getContext();
        date.setText(Days.label(c, day));
        List<Meal> list = db.meals(day);
        Nutrients sum = new Nutrients();
        for (Meal m : list) sum.add(m.total());
        total.setText(Ui.kcal(sum.kcal));
        macros.setText(Ui.macros(sum));

        long now = System.currentTimeMillis();
        Meal last = list.isEmpty() ? null : list.get(list.size() - 1);
        long expiresAt = last == null ? Long.MAX_VALUE : targetExpiresAt(last);
        boolean hasTarget = last != null && expiresAt > now;

        meals.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(c);
        for (int i = 0; i < list.size(); i++) {
            View card = inflater.inflate(R.layout.meal_card, meals, false);
            bindMeal(inflater, card, list.get(i), hasTarget && i == list.size() - 1);
            meals.addView(card);
        }
        empty.setVisibility(list.isEmpty() ? VISIBLE : GONE);
        if (expiresAt != Long.MAX_VALUE && expiresAt > now) {
            postDelayed(expireTarget, expiresAt - now);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(expireTarget);
        super.onDetachedFromWindow();
    }

    private void bindMeal(LayoutInflater inflater, View card, Meal meal, boolean target) {
        Context c = getContext();
        Nutrients t = meal.total();
        TextView name = card.findViewById(R.id.meal_name);
        name.setText(meal.title());
        name.setTextColor(c.getColor(target ? R.color.accent : R.color.text_primary));
        String time = DateFormat.format(DateFormat.is24HourFormat(c) ? "HH:mm" : "h:mm a",
                new Date(meal.created)).toString();
        ((TextView) card.findViewById(R.id.meal_time)).setText(time);
        ((TextView) card.findViewById(R.id.meal_kcal)).setText(Ui.kcal(t.kcal) + " kcal");
        ((TextView) card.findViewById(R.id.meal_meta)).setText(
                Ui.join(target ? "Quick add goes here" : null, Ui.macros(t)));

        FlowLayout rows = card.findViewById(R.id.meal_entries);
        for (Meal.Entry e : meal.entries) rows.addView(pill(inflater, rows, e));
        rows.setVisibility(meal.entries.isEmpty() ? GONE : VISIBLE);
        card.setOnDragListener((v, event) -> onCardDrag(v, meal, event));

        card.setOnClickListener(v -> c.startActivity(new Intent(c, MealActivity.class)
                .putExtra(MealActivity.EXTRA_ID, meal.id)));
        card.setOnLongClickListener(v -> {
            mealOptions(meal);
            return true;
        });
    }

    /** One logged item: tap to edit the amount, long-press to drag it to another meal. */
    private View pill(LayoutInflater inflater, ViewGroup parent, Meal.Entry e) {
        View pill = inflater.inflate(R.layout.entry_pill, parent, false);
        ((TextView) pill.findViewById(R.id.pill_name)).setText(e.name);
        ((TextView) pill.findViewById(R.id.pill_detail)).setText(
                Ui.amount(e.amount, e.unit) + " · " + Ui.kcal(e.nutrients.kcal) + " kcal");
        pill.setOnClickListener(v -> Ui.amountDialog(getContext(), e.name, e.amount, e.unit, amount -> {
            db.setEntryAmount(e, amount);
            render();
        }, () -> {
            db.deleteEntry(e.id);
            render();
        }));
        pill.setOnLongClickListener(v -> {
            v.startDragAndDrop(ClipData.newPlainText("entry", e.name), new View.DragShadowBuilder(v), e, 0);
            dragging = v;
            v.setAlpha(0.3f);
            return true;
        });
        return pill;
    }

    /** Meal cards take dropped pills. */
    private boolean onCardDrag(View card, Meal meal, DragEvent event) {
        Object dragged = event.getLocalState();
        if (!(dragged instanceof Meal.Entry entry)) return false;
        switch (event.getAction()) {
            case DragEvent.ACTION_DRAG_ENTERED:
                if (entry.mealId != meal.id) setCardBackground(card, R.drawable.card_drop);
                break;
            case DragEvent.ACTION_DRAG_LOCATION:
                edgeScroll(card, event.getY());
                break;
            case DragEvent.ACTION_DRAG_EXITED:
                setCardBackground(card, R.drawable.card_clickable);
                autoScroll = 0;
                break;
            case DragEvent.ACTION_DROP:
                setCardBackground(card, R.drawable.card_clickable);
                autoScroll = 0;
                if (entry.mealId != meal.id) {
                    db.moveEntry(entry.id, meal.id);
                    render();
                }
                break;
            case DragEvent.ACTION_DRAG_ENDED:
                setCardBackground(card, R.drawable.card_clickable);
                autoScroll = 0;
                if (dragging != null) {
                    dragging.setAlpha(1f);
                    dragging = null;
                }
                break;
            default:
                break;
        }
        return true;
    }

    /** setBackgroundResource drops the view's padding, so put it back. */
    private static void setCardBackground(View card, int background) {
        int left = card.getPaddingLeft();
        int top = card.getPaddingTop();
        int right = card.getPaddingRight();
        int bottom = card.getPaddingBottom();
        card.setBackgroundResource(background);
        card.setPadding(left, top, right, bottom);
    }

    /** Scrolls the diary while a pill is held near the top or bottom of the list. */
    private void edgeScroll(View card, float yInCard) {
        int[] cardOnScreen = new int[2];
        int[] scrollOnScreen = new int[2];
        card.getLocationOnScreen(cardOnScreen);
        scroll.getLocationOnScreen(scrollOnScreen);
        float y = cardOnScreen[1] + yInCard - scrollOnScreen[1];
        float zone = Ui.dp(getContext(), 72);
        int direction = y < zone ? -1 : y > scroll.getHeight() - zone ? 1 : 0;
        if (direction == autoScroll) return;
        autoScroll = direction;
        removeCallbacks(autoScroller);
        if (direction != 0) post(autoScroller);
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
