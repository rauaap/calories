package com.rauaap.calories;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.Arrays;

/**
 * Creates or edits a food. Opened with {@link #EXTRA_ID} to edit, or with a
 * prefilled unsaved food (Food.toIntent) from a barcode or online search.
 * On save it returns the food's id, which the scanner and search pass back
 * to the quick-add bar.
 */
public class FoodEditActivity extends Activity {
    static final String EXTRA_ID = "food_id";

    private Food food;
    private EditText name;
    private EditText brand;
    private EditText barcode;
    private EditText per;
    private EditText kcal;
    private EditText protein;
    private EditText carbs;
    private EditText fat;
    private EditText serving;
    private Spinner unit;
    private TextView servingUnit;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_food_edit);
        Ui.fitInsets(findViewById(R.id.root));
        long id = getIntent().getLongExtra(EXTRA_ID, 0);
        food = id > 0 ? Db.get(this).food(id) : Food.fromIntent(getIntent());
        if (food == null) {
            finish();
            return;
        }

        name = findViewById(R.id.food_name);
        brand = findViewById(R.id.food_brand);
        barcode = findViewById(R.id.food_barcode);
        per = findViewById(R.id.food_per);
        kcal = findViewById(R.id.food_kcal);
        protein = findViewById(R.id.food_protein);
        carbs = findViewById(R.id.food_carbs);
        fat = findViewById(R.id.food_fat);
        serving = findViewById(R.id.food_serving);
        unit = findViewById(R.id.food_unit);
        servingUnit = findViewById(R.id.food_serving_unit);
        for (EditText e : new EditText[] {per, kcal, protein, carbs, fat, serving}) Ui.decimalInput(e);

        ((TextView) findViewById(R.id.food_title)).setText(food.id > 0 ? "Edit food" : "New food");
        name.setText(food.name);
        brand.setText(food.brand);
        barcode.setText(food.barcode);
        per.setText(Ui.amount(food.per));
        fill(kcal, food.nutrients.kcal);
        fill(protein, food.nutrients.protein);
        fill(carbs, food.nutrients.carbs);
        fill(fat, food.nutrients.fat);
        fill(serving, food.serving);
        boolean noData = Double.isNaN(food.nutrients.kcal) && Double.isNaN(food.nutrients.protein)
                && Double.isNaN(food.nutrients.carbs) && Double.isNaN(food.nutrients.fat);
        findViewById(R.id.food_note).setVisibility(food.id == 0 && noData ? View.VISIBLE : View.GONE);

        ArrayAdapter<String> units = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, Food.UNITS);
        units.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        unit.setAdapter(units);
        unit.setSelection(Math.max(0, Arrays.asList(Food.UNITS).indexOf(food.unit)), false);
        unit.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            private String previous = selectedUnit();

            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String now = Food.UNITS[position];
                // Keep the reference amount in step with the unit unless it was customised.
                if (Ui.parse(per.getText()) == Food.defaultPer(previous)) per.setText(Ui.amount(Food.defaultPer(now)));
                previous = now;
                servingUnit.setText(now);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        servingUnit.setText(selectedUnit());

        View delete = findViewById(R.id.food_delete);
        delete.setVisibility(food.id > 0 ? View.VISIBLE : View.GONE);
        delete.setOnClickListener(v -> Ui.confirm(this,
                "Delete " + food.name + "? Diary entries keep their numbers; it's removed from meals.",
                "Delete", () -> {
                    Db.get(this).deleteFood(food.id);
                    finish();
                }));
        findViewById(R.id.food_back).setOnClickListener(v -> finish());
        findViewById(R.id.food_save).setOnClickListener(v -> save());
    }

    private String selectedUnit() {
        return Food.UNITS[Math.max(0, unit.getSelectedItemPosition())];
    }

    private void save() {
        String n = name.getText().toString().trim();
        if (n.isEmpty()) {
            name.setError("Required");
            return;
        }
        double reference = Ui.parse(per.getText());
        if (!(reference > 0)) {
            per.setError("Must be more than 0");
            return;
        }
        food.name = n;
        food.brand = brand.getText().toString().trim();
        food.barcode = barcode.getText().toString().trim();
        food.unit = selectedUnit();
        food.per = reference;
        food.nutrients = new Nutrients(value(kcal), value(protein), value(carbs), value(fat));
        food.serving = value(serving);
        Db.get(this).saveFood(food);
        setResult(RESULT_OK, new Intent().putExtra(EXTRA_ID, food.id));
        finish();
    }

    /** Blank for zero or unknown, so empty fields read as "fill me in". */
    private static void fill(EditText field, double v) {
        field.setText(Double.isNaN(v) || v == 0 ? "" : Ui.amount(v));
    }

    private static double value(EditText field) {
        double v = Ui.parse(field.getText());
        return v > 0 ? v : 0;
    }
}
