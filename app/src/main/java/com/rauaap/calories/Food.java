package com.rauaap.calories;

import android.content.Intent;

/**
 * A food. Nutrients are always entered against the reference amount
 * ({@link #per} {@link #unit}, e.g. per 100 g). The serving is separate and
 * only prefills the amount field when the food is added.
 */
final class Food {
    static final String[] UNITS = {"g", "ml", "pcs"};

    long id;
    String name = "";
    String brand = "";
    String barcode = "";
    String unit = "g";
    double per = 100;
    Nutrients nutrients = new Nutrients();
    /** Default amount to prefill, in {@link #unit}; 0 when there is none. */
    double serving;
    /** How many diary entries use this food; ranks quick-add matches. */
    int uses;

    static double defaultPer(String unit) {
        return "pcs".equals(unit) ? 1 : 100;
    }

    Nutrients forAmount(double amount) {
        return nutrients.times(amount / per);
    }

    String perLabel() {
        return Ui.amount(per, unit);
    }

    /** Carries an unsaved food (e.g. from Open Food Facts) to FoodEditActivity. */
    void toIntent(Intent i) {
        i.putExtra("food.name", name)
                .putExtra("food.brand", brand)
                .putExtra("food.barcode", barcode)
                .putExtra("food.unit", unit)
                .putExtra("food.per", per)
                .putExtra("food.kcal", nutrients.kcal)
                .putExtra("food.protein", nutrients.protein)
                .putExtra("food.carbs", nutrients.carbs)
                .putExtra("food.fat", nutrients.fat)
                .putExtra("food.serving", serving);
    }

    static Food fromIntent(Intent i) {
        Food f = new Food();
        if (!i.hasExtra("food.name")) return f;
        f.name = i.getStringExtra("food.name");
        f.brand = i.getStringExtra("food.brand");
        f.barcode = i.getStringExtra("food.barcode");
        f.unit = i.getStringExtra("food.unit");
        f.per = i.getDoubleExtra("food.per", 100);
        f.nutrients = new Nutrients(i.getDoubleExtra("food.kcal", 0), i.getDoubleExtra("food.protein", 0),
                i.getDoubleExtra("food.carbs", 0), i.getDoubleExtra("food.fat", 0));
        f.serving = i.getDoubleExtra("food.serving", 0);
        return f;
    }
}
