package com.rauaap.calories;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/** The app's own backup: one JSON document with foods, meal presets, the diary and settings. */
final class Backup {
    private static final String FORMAT = "calories-backup";

    private Backup() {
    }

    static JSONObject toJson(ImportData data, int dayStart, boolean chartScaled) throws JSONException {
        JSONArray foods = new JSONArray();
        for (Food f : data.foods) {
            foods.put(nutrients(new JSONObject(), f.nutrients)
                    .put("id", f.id)
                    .put("name", f.name)
                    .put("brand", f.brand)
                    .put("barcode", f.barcode)
                    .put("unit", f.unit)
                    .put("per", f.per)
                    .put("serving", f.serving));
        }
        JSONArray presets = new JSONArray();
        for (Preset p : data.presets) {
            JSONArray items = new JSONArray();
            for (Preset.Item i : p.items) items.put(new JSONObject().put("food", i.food.id).put("amount", i.amount));
            presets.put(new JSONObject().put("name", p.name).put("items", items));
        }
        JSONArray meals = new JSONArray();
        for (Meal m : data.meals) {
            JSONArray entries = new JSONArray();
            for (Meal.Entry e : m.entries) {
                entries.put(nutrients(new JSONObject(), e.nutrients)
                        .put("food", e.foodId != null ? e.foodId : JSONObject.NULL)
                        .put("name", e.name)
                        .put("amount", e.amount)
                        .put("unit", e.unit));
            }
            meals.put(new JSONObject()
                    .put("day", LocalDate.ofEpochDay(m.day).toString())
                    .put("created", m.created)
                    .put("name", m.name)
                    .put("entries", entries));
        }
        return new JSONObject()
                .put("format", FORMAT)
                .put("version", 1)
                .put("settings", new JSONObject().put("dayStart", dayStart).put("chartScaled", chartScaled))
                .put("foods", foods)
                .put("presets", presets)
                .put("meals", meals);
    }

    static ImportData parse(String json) throws JSONException {
        JSONObject root = new JSONObject(json);
        if (!FORMAT.equals(root.optString("format"))) throw new JSONException("not a Calories backup");
        ImportData data = new ImportData();
        JSONObject settings = root.optJSONObject("settings");
        if (settings != null) {
            if (settings.has("dayStart")) data.dayStart = settings.getInt("dayStart");
            if (settings.has("chartScaled")) data.chartScaled = settings.getBoolean("chartScaled");
        }

        Map<Long, Food> byId = new HashMap<>();
        JSONArray foods = array(root, "foods");
        for (int i = 0; i < foods.length(); i++) {
            JSONObject o = foods.getJSONObject(i);
            Food f = new Food();
            f.name = o.getString("name");
            f.brand = o.optString("brand");
            f.barcode = o.optString("barcode");
            f.unit = o.optString("unit", "g");
            f.per = o.getDouble("per");
            f.nutrients = nutrients(o);
            f.serving = o.optDouble("serving", 0);
            byId.put(o.getLong("id"), f);
            data.foods.add(f);
        }

        JSONArray presets = array(root, "presets");
        for (int i = 0; i < presets.length(); i++) {
            JSONObject o = presets.getJSONObject(i);
            Preset p = new Preset();
            p.name = o.getString("name");
            JSONArray items = array(o, "items");
            for (int j = 0; j < items.length(); j++) {
                JSONObject item = items.getJSONObject(j);
                Food f = byId.get(item.getLong("food"));
                if (f != null) p.items.add(new Preset.Item(f, item.getDouble("amount")));
            }
            data.presets.add(p);
        }

        JSONArray meals = array(root, "meals");
        for (int i = 0; i < meals.length(); i++) {
            JSONObject o = meals.getJSONObject(i);
            Meal m = new Meal();
            m.day = LocalDate.parse(o.getString("day")).toEpochDay();
            m.created = o.optLong("created");
            m.name = o.optString("name");
            JSONArray entries = array(o, "entries");
            for (int j = 0; j < entries.length(); j++) {
                JSONObject item = entries.getJSONObject(j);
                Meal.Entry e = new Meal.Entry();
                e.food = item.isNull("food") ? null : byId.get(item.getLong("food"));
                e.name = item.getString("name");
                e.amount = item.getDouble("amount");
                e.unit = item.getString("unit");
                e.nutrients = nutrients(item);
                m.entries.add(e);
            }
            data.meals.add(m);
        }
        return data;
    }

    private static JSONObject nutrients(JSONObject o, Nutrients n) throws JSONException {
        return o.put("kcal", n.kcal).put("protein", n.protein).put("carbs", n.carbs).put("fat", n.fat);
    }

    private static Nutrients nutrients(JSONObject o) {
        return new Nutrients(o.optDouble("kcal", 0), o.optDouble("protein", 0),
                o.optDouble("carbs", 0), o.optDouble("fat", 0));
    }

    private static JSONArray array(JSONObject o, String key) {
        JSONArray a = o.optJSONArray(key);
        return a != null ? a : new JSONArray();
    }
}
