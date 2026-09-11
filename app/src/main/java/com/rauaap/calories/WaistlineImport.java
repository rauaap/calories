package com.rauaap.calories;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Reads waistline's "Export database" file (waistline_export.json).
 *
 * Waistline stores nutrients per food "portion" and logs items as portion
 * times quantity. Foods become ours with a reference amount of 100 g / 100 ml
 * (1 for pieces), and waistline's portion becomes the serving when it differs.
 * Diary entries get exactly what waistline showed:
 * food nutrients × (item portion ÷ food portion) × quantity.
 *
 * Archived and hidden foods, and recipes, don't become foods, but diary
 * entries that used them still import with their nutrients.
 */
final class WaistlineImport {
    private static final String[] DEFAULT_MEAL_NAMES = {"Breakfast", "Lunch", "Dinner", "Snacks"};

    private WaistlineImport() {
    }

    /** A waistline food or recipe as stored, plus the conversion to our units. */
    private static final class Source {
        String name;
        String brand;
        String barcode;
        double portion;
        Nutrients nutrients;
        /** Our unit: g, ml or pcs. */
        String unit;
        /** Multiplier from waistline's unit to ours. */
        double factor;
        /** The converted food, when it goes into the food list. */
        Food food;
    }

    static ImportData parse(String json) throws JSONException {
        JSONObject root = new JSONObject(json);
        if (!root.has("foodList") && !root.has("diary")) throw new JSONException("not a waistline export");
        ImportData data = new ImportData();

        Map<Long, Source> foods = new HashMap<>();
        JSONArray foodList = array(root, "foodList");
        for (int i = 0; i < foodList.length(); i++) {
            JSONObject o = foodList.optJSONObject(i);
            if (o == null || !o.has("id")) continue;
            Source s = source(o);
            boolean listed = !o.optBoolean("archived") && !o.optBoolean("hidden")
                    && !"quick-add".equals(s.barcode) && !s.name.isEmpty();
            if (listed) {
                s.food = toFood(s);
                data.foods.add(s.food);
            }
            foods.put(o.optLong("id"), s);
        }
        Map<Long, Source> recipes = new HashMap<>();
        JSONArray recipeList = array(root, "recipes");
        for (int i = 0; i < recipeList.length(); i++) {
            JSONObject o = recipeList.optJSONObject(i);
            if (o != null && o.has("id")) recipes.put(o.optLong("id"), source(o));
        }

        String[] mealNames = mealNames(root);
        JSONArray diary = array(root, "diary");
        for (int i = 0; i < diary.length(); i++) {
            JSONObject day = diary.optJSONObject(i);
            LocalDate date = day == null ? null : date(str(day, "dateTime"));
            if (date == null) continue;
            // One of our meals per waistline meal slot, in slot order.
            TreeMap<Integer, Meal> meals = new TreeMap<>();
            JSONArray items = array(day, "items");
            for (int j = 0; j < items.length(); j++) {
                JSONObject item = items.optJSONObject(j);
                Meal.Entry entry = item == null ? null : entry(item, foods, recipes);
                if (entry == null) {
                    data.skipped++;
                    continue;
                }
                int slot = item.optInt("category", 0);
                Meal meal = meals.computeIfAbsent(slot, c -> newMeal(date, c, mealNames));
                meal.entries.add(entry);
                long logged = instant(str(item, "dateTime"));
                if (logged > 0 && (meal.created == 0 || logged < meal.created)) meal.created = logged;
            }
            for (Meal meal : meals.values()) {
                if (meal.created == 0) {
                    meal.created = date.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
                }
                data.meals.add(meal);
            }
        }

        JSONArray saved = array(root, "meals");
        for (int i = 0; i < saved.length(); i++) {
            JSONObject o = saved.optJSONObject(i);
            if (o == null) continue;
            Preset preset = new Preset();
            preset.name = str(o, "name");
            JSONArray items = array(o, "items");
            for (int j = 0; j < items.length(); j++) {
                JSONObject item = items.optJSONObject(j);
                if (item == null || "recipe".equals(str(item, "type"))) continue;
                Source s = foods.get(item.optLong("id", -1));
                if (s == null || s.food == null) continue;
                double portion = num(item, "portion");
                if (!(portion > 0)) portion = s.portion;
                double quantity = num(item, "quantity");
                if (!(quantity > 0)) quantity = 1;
                preset.items.add(new Preset.Item(s.food, portion * quantity * s.factor));
            }
            if (!preset.name.isEmpty() && !preset.items.isEmpty()) data.presets.add(preset);
        }
        return data;
    }

    /** Null when the item can't be imported: no food, or zero / burned (negative) amounts. */
    private static Meal.Entry entry(JSONObject item, Map<Long, Source> foods, Map<Long, Source> recipes) {
        if (!item.has("id") || item.isNull("id")) return null;
        Source s = ("recipe".equals(str(item, "type")) ? recipes : foods).get(item.optLong("id"));
        if (s == null || !(s.portion > 0)) return null;
        double portion = num(item, "portion");
        if (Double.isNaN(portion)) portion = s.portion;
        double quantity = num(item, "quantity");
        if (!(portion > 0) || !(quantity > 0)) return null;

        Meal.Entry e = new Meal.Entry();
        e.nutrients = s.nutrients.times(portion / s.portion * quantity);
        e.food = s.food;
        if ("quick-add".equals(s.barcode)) {
            e.name = "Quick add";
            e.amount = e.nutrients.kcal;
            e.unit = "kcal";
        } else {
            e.name = s.name.isEmpty() ? "Unnamed" : s.name;
            e.amount = portion * quantity * s.factor;
            e.unit = s.unit;
        }
        return e;
    }

    private static Source source(JSONObject o) {
        Source s = new Source();
        s.name = str(o, "name");
        s.brand = str(o, "brand");
        s.barcode = str(o, "barcode");
        s.portion = num(o, "portion");
        JSONObject n = o.optJSONObject("nutrition");
        if (n == null) n = new JSONObject();
        double kcal = num(n, "calories");
        if (Double.isNaN(kcal)) kcal = num(n, "kilojoules") / 4.184;
        s.nutrients = new Nutrients(orZero(kcal), orZero(num(n, "proteins")),
                orZero(num(n, "carbohydrates")), orZero(num(n, "fat")));
        convertUnit(s, str(o, "unit").toLowerCase(Locale.ROOT));
        return s;
    }

    /** Mass and volume units convert; anything else ("serving", "bun", ...) counts as pieces. */
    private static void convertUnit(Source s, String unit) {
        switch (unit) {
            case "g", "gr", "gram", "grams", "gramm", "gramme", "grammes" -> set(s, "g", 1);
            case "kg" -> set(s, "g", 1000);
            case "mg" -> set(s, "g", 0.001);
            case "oz" -> set(s, "g", 28.349523125);
            case "lb" -> set(s, "g", 453.59237);
            case "ml", "millilitre", "milliliter" -> set(s, "ml", 1);
            case "cl" -> set(s, "ml", 10);
            case "dl" -> set(s, "ml", 100);
            case "l" -> set(s, "ml", 1000);
            case "fl oz" -> set(s, "ml", 29.5735295625);
            default -> set(s, "pcs", 1);
        }
    }

    private static void set(Source s, String unit, double factor) {
        s.unit = unit;
        s.factor = factor;
    }

    private static Food toFood(Source s) {
        Food f = new Food();
        f.name = s.name;
        f.brand = s.brand;
        f.barcode = s.barcode;
        f.unit = s.unit;
        f.per = Food.defaultPer(s.unit);
        double portion = s.portion > 0 ? s.portion * s.factor : f.per;
        f.nutrients = s.nutrients.times(f.per / portion);
        f.serving = Math.abs(portion - f.per) < 1e-9 ? 0 : portion;
        return f;
    }

    private static Meal newMeal(LocalDate date, int slot, String[] names) {
        Meal m = new Meal();
        m.day = date.toEpochDay();
        if (slot >= 0 && slot < names.length && !names[slot].isEmpty()) m.name = names[slot];
        else if (slot >= 0 && slot < DEFAULT_MEAL_NAMES.length) m.name = DEFAULT_MEAL_NAMES[slot];
        else m.name = "Meal " + (slot + 1);
        return m;
    }

    private static String[] mealNames(JSONObject root) {
        JSONObject settings = root.optJSONObject("settings");
        JSONObject diary = settings == null ? null : settings.optJSONObject("diary");
        JSONArray names = diary == null ? null : diary.optJSONArray("meal-names");
        if (names == null) return DEFAULT_MEAL_NAMES;
        String[] out = new String[names.length()];
        for (int i = 0; i < out.length; i++) {
            Object v = names.opt(i);
            out[i] = v == null || v == JSONObject.NULL ? "" : v.toString().trim();
        }
        return out;
    }

    /** Waistline stores diary days as UTC midnight of the local date, so the date part is the day. */
    private static LocalDate date(String iso) {
        if (iso.length() < 10) return null;
        try {
            return LocalDate.parse(iso.substring(0, 10));
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static long instant(String iso) {
        try {
            return Instant.parse(iso).toEpochMilli();
        } catch (DateTimeParseException e) {
            return 0;
        }
    }

    private static JSONArray array(JSONObject o, String key) {
        JSONArray a = o.optJSONArray(key);
        return a != null ? a : new JSONArray();
    }

    private static String str(JSONObject o, String key) {
        Object v = o.opt(key);
        return v == null || v == JSONObject.NULL ? "" : v.toString().trim();
    }

    /** Waistline mixes numbers and numeric strings. NaN when absent. */
    private static double num(JSONObject o, String key) {
        Object v = o.opt(key);
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof String s) return Ui.parse(s);
        return Double.NaN;
    }

    private static double orZero(double v) {
        return Double.isNaN(v) ? 0 : v;
    }
}
