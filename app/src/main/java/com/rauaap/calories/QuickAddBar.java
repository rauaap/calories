package com.rauaap.calories;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Type a food, Enter, type an amount, Enter. The dropdown filters saved foods
 * (and presets when enabled) as you type. The bar sits at the bottom of the
 * screen, so the dropdown opens upward and is listed bottom-up: the best match
 * is next to the field, marked with an arrow, and Enter takes it and moves to
 * the amount, which is prefilled with the food's serving. The top row searches
 * Open Food Facts, and the barcode button scans.
 *
 * Hosts forward onActivityResult to {@link #onActivityResult}.
 */
public class QuickAddBar extends LinearLayout {
    static final int REQUEST_PICK = 7301;

    interface Listener {
        void onAddFood(Food food, double amount);

        void onAddPreset(Preset preset, double times);
    }

    /** Dropdown row that hands the typed text to the online search. */
    private static final class OnlineSearch {
        final String query;

        OnlineSearch(String query) {
            this.query = query;
        }
    }

    private final AutoCompleteTextView foodInput;
    private final EditText amountInput;
    private final TextView unitLabel;
    private final PickAdapter adapter = new PickAdapter();
    private boolean includePresets;
    private Listener listener;
    private Object picked;
    private boolean settingText;

    public QuickAddBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        inflate(context, R.layout.quick_add_bar, this);
        foodInput = findViewById(R.id.quick_food);
        amountInput = findViewById(R.id.quick_amount);
        unitLabel = findViewById(R.id.quick_unit);
        Ui.decimalInput(amountInput);

        foodInput.setAdapter(adapter);
        foodInput.setDropDownBackgroundResource(R.drawable.popup);
        if (getId() != View.NO_ID) foodInput.setDropDownAnchor(getId());
        foodInput.setOnItemClickListener((parent, view, position, id) -> choose(adapter.getItem(position)));
        foodInput.setOnEditorActionListener((v, action, event) -> {
            if (!Ui.isEnter(action, event)) return false;
            if (Ui.isPress(event)) submitFood();
            return true;
        });
        Ui.onTextChanged(foodInput, () -> {
            if (!settingText && picked != null) {
                picked = null;
                unitLabel.setText("");
            }
        });
        amountInput.setOnEditorActionListener((v, action, event) -> {
            if (!Ui.isEnter(action, event)) return false;
            if (Ui.isPress(event)) submitAmount();
            return true;
        });
        findViewById(R.id.quick_scan).setOnClickListener(v ->
                host().startActivityForResult(new Intent(getContext(), ScanActivity.class), REQUEST_PICK));
    }

    void setIncludePresets(boolean include) {
        includePresets = include;
    }

    void setListener(Listener listener) {
        this.listener = listener;
    }

    /** Reloads the searchable foods (and presets); call when the host resumes. */
    void reload() {
        Db db = Db.get(getContext());
        List<Object> items = new ArrayList<>(db.foods());
        if (includePresets) items.addAll(db.presets());
        adapter.searchable = items;
    }

    /** Takes a food picked by the scanner or online search. Returns true if the request was ours. */
    boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQUEST_PICK) return false;
        if (resultCode == Activity.RESULT_OK && data != null) {
            Food food = Db.get(getContext()).food(data.getLongExtra(FoodEditActivity.EXTRA_ID, 0));
            if (food != null) {
                reload();
                choose(food);
            }
        }
        return true;
    }

    private Activity host() {
        return (Activity) getContext();
    }

    private void submitFood() {
        if (picked != null) {
            focusAmount();
            return;
        }
        String query = foodInput.getText().toString().trim();
        if (query.isEmpty()) return;
        int selection = foodInput.isPopupShowing() ? foodInput.getListSelection() : ListView.INVALID_POSITION;
        choose(selection != ListView.INVALID_POSITION ? adapter.getItem(selection) : adapter.match(query).get(0));
    }

    private void choose(Object item) {
        foodInput.dismissDropDown();
        if (item instanceof OnlineSearch search) {
            host().startActivityForResult(new Intent(getContext(), OnlineSearchActivity.class)
                    .putExtra(OnlineSearchActivity.EXTRA_QUERY, search.query), REQUEST_PICK);
            return;
        }
        picked = item;
        setFoodText(Search.name(item));
        double prefill;
        if (item instanceof Food food) {
            unitLabel.setText(food.unit);
            prefill = food.serving;
        } else {
            unitLabel.setText("×");
            prefill = 1;
        }
        amountInput.setText(prefill > 0 ? Ui.amount(prefill) : "");
        amountInput.setError(null);
        focusAmount();
    }

    private void focusAmount() {
        amountInput.requestFocus();
        amountInput.selectAll();
        Ui.showKeyboard(amountInput);
    }

    private void submitAmount() {
        if (picked == null) {
            foodInput.requestFocus();
            return;
        }
        double amount = Ui.parse(amountInput.getText());
        if (!(amount > 0)) {
            amountInput.setError("Enter an amount");
            return;
        }
        Object item = picked;
        picked = null;
        setFoodText("");
        amountInput.setText("");
        unitLabel.setText("");
        foodInput.requestFocus();
        if (listener != null) {
            if (item instanceof Food food) listener.onAddFood(food, amount);
            else listener.onAddPreset((Preset) item, amount);
        }
        reload();
    }

    private void setFoodText(String text) {
        settingText = true;
        foodInput.setText(text, false);
        foodInput.setSelection(text.length());
        settingText = false;
    }

    private final class PickAdapter extends ItemAdapter<Object> implements Filterable {
        volatile List<Object> searchable = new ArrayList<>();
        /** The best match: what Enter adds, marked with the arrow. */
        private Object target;

        PickAdapter() {
            super(R.layout.two_line_item);
        }

        /** Ranked matches, always ending with the online search row. */
        List<Object> match(String query) {
            List<Object> out = Search.rank(searchable, query, 30);
            out.add(new OnlineSearch(query.trim()));
            return out;
        }

        @Override
        void bind(View view, Object item) {
            TextView title = view.findViewById(R.id.item_title);
            TextView subtitle = view.findViewById(R.id.item_subtitle);
            TextView trailing = view.findViewById(R.id.item_trailing);
            if (item instanceof Food food) {
                title.setText(marked(view, item, food.name));
                subtitle.setText(Ui.join(food.brand, "per " + food.perLabel()));
                trailing.setText(Ui.kcal(food.nutrients.kcal) + " kcal");
            } else if (item instanceof Preset preset) {
                title.setText(marked(view, item, preset.name));
                subtitle.setText("Meal · " + preset.items.size() + " items");
                trailing.setText(Ui.kcal(preset.total().kcal) + " kcal");
            } else {
                title.setText(marked(view, item, "Search Open Food Facts"));
                subtitle.setText("“" + ((OnlineSearch) item).query + "”");
                trailing.setText("");
            }
        }

        /** Prefixes the row Enter will add with an accent arrow; the other rows keep their plain alignment. */
        private CharSequence marked(View view, Object item, String text) {
            if (item != target) return text;
            SpannableStringBuilder s = new SpannableStringBuilder("→ ").append(text);
            s.setSpan(new ForegroundColorSpan(view.getContext().getColor(R.color.accent)), 0, 1,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            return s;
        }

        @Override
        public Filter getFilter() {
            return new Filter() {
                @Override
                protected FilterResults performFiltering(CharSequence text) {
                    List<Object> list = text == null || text.toString().trim().isEmpty()
                            ? new ArrayList<>() : match(text.toString());
                    FilterResults results = new FilterResults();
                    results.values = list;
                    results.count = list.size();
                    return results;
                }

                @Override
                @SuppressWarnings("unchecked")
                protected void publishResults(CharSequence text, FilterResults results) {
                    // Opening upward, the list reads bottom-up: best match nearest the field.
                    List<Object> ranked = (List<Object>) results.values;
                    target = ranked.isEmpty() ? null : ranked.get(0);
                    List<Object> shown = new ArrayList<>(ranked);
                    Collections.reverse(shown);
                    set(shown);
                    // Keep the bottom (the arrowed row) in view when the list has to scroll.
                    foodInput.post(() -> {
                        if (foodInput.isPopupShowing() && getCount() > 0) foodInput.setListSelection(getCount() - 1);
                    });
                }

                @Override
                public CharSequence convertResultToString(Object item) {
                    return item instanceof OnlineSearch search ? search.query : Search.name(item);
                }
            };
        }
    }
}
