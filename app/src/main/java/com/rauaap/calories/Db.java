package com.rauaap.calories;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * All persistent data. Diary entries store a snapshot of the nutrients they
 * were logged with, so editing or deleting a food never rewrites history.
 * Every write that changes a day's totals refreshes the home-screen widget.
 */
final class Db extends SQLiteOpenHelper {
    private static final String FOOD_QUERY =
            "SELECT f.*, (SELECT COUNT(*) FROM entry e WHERE e.food_id = f.id) AS uses FROM food f";

    private static Db instance;
    private final Context context;

    static synchronized Db get(Context c) {
        if (instance == null) instance = new Db(c.getApplicationContext());
        return instance;
    }

    private Db(Context context) {
        super(context, "calories.db", null, 1);
        this.context = context;
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE food (id INTEGER PRIMARY KEY, name TEXT NOT NULL,"
                + " brand TEXT NOT NULL DEFAULT '', barcode TEXT NOT NULL DEFAULT '',"
                + " unit TEXT NOT NULL, per REAL NOT NULL, kcal REAL NOT NULL, protein REAL NOT NULL,"
                + " carbs REAL NOT NULL, fat REAL NOT NULL, serving REAL NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX food_barcode ON food(barcode)");
        db.execSQL("CREATE TABLE preset (id INTEGER PRIMARY KEY, name TEXT NOT NULL)");
        db.execSQL("CREATE TABLE preset_item (id INTEGER PRIMARY KEY,"
                + " preset_id INTEGER NOT NULL REFERENCES preset(id) ON DELETE CASCADE,"
                + " food_id INTEGER NOT NULL REFERENCES food(id) ON DELETE CASCADE, amount REAL NOT NULL)");
        db.execSQL("CREATE INDEX preset_item_preset ON preset_item(preset_id)");
        db.execSQL("CREATE INDEX preset_item_food ON preset_item(food_id)");
        db.execSQL("CREATE TABLE meal (id INTEGER PRIMARY KEY, day INTEGER NOT NULL,"
                + " created INTEGER NOT NULL, name TEXT NOT NULL DEFAULT '')");
        db.execSQL("CREATE INDEX meal_day ON meal(day)");
        db.execSQL("CREATE TABLE entry (id INTEGER PRIMARY KEY,"
                + " meal_id INTEGER NOT NULL REFERENCES meal(id) ON DELETE CASCADE,"
                + " food_id INTEGER REFERENCES food(id) ON DELETE SET NULL,"
                + " name TEXT NOT NULL, amount REAL NOT NULL, unit TEXT NOT NULL,"
                + " kcal REAL NOT NULL, protein REAL NOT NULL, carbs REAL NOT NULL, fat REAL NOT NULL)");
        db.execSQL("CREATE INDEX entry_meal ON entry(meal_id)");
        db.execSQL("CREATE INDEX entry_food ON entry(food_id)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    }

    // Foods

    List<Food> foods() {
        List<Food> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(FOOD_QUERY + " ORDER BY f.name COLLATE NOCASE", null)) {
            while (c.moveToNext()) out.add(readFood(c));
        }
        return out;
    }

    Food food(long id) {
        return firstFood(FOOD_QUERY + " WHERE f.id = ?", String.valueOf(id));
    }

    Food foodByBarcode(String barcode) {
        if (barcode == null || barcode.isEmpty()) return null;
        return firstFood(FOOD_QUERY + " WHERE f.barcode = ? LIMIT 1", barcode);
    }

    private Food firstFood(String sql, String arg) {
        try (Cursor c = getReadableDatabase().rawQuery(sql, new String[] {arg})) {
            return c.moveToFirst() ? readFood(c) : null;
        }
    }

    long saveFood(Food f) {
        SQLiteDatabase db = getWritableDatabase();
        if (f.id > 0) db.update("food", foodValues(f), "id = ?", args(f.id));
        else f.id = db.insertOrThrow("food", null, foodValues(f));
        return f.id;
    }

    void deleteFood(long id) {
        getWritableDatabase().delete("food", "id = ?", args(id));
    }

    // Presets

    List<Preset> presets() {
        return loadPresets(null);
    }

    Preset preset(long id) {
        List<Preset> list = loadPresets(id);
        return list.isEmpty() ? null : list.get(0);
    }

    private List<Preset> loadPresets(Long id) {
        SQLiteDatabase db = getReadableDatabase();
        String[] a = id == null ? null : args(id);
        Map<Long, Preset> byId = new LinkedHashMap<>();
        try (Cursor c = db.rawQuery("SELECT id, name FROM preset" + (id == null ? "" : " WHERE id = ?")
                + " ORDER BY name COLLATE NOCASE", a)) {
            while (c.moveToNext()) {
                Preset p = new Preset();
                p.id = c.getLong(0);
                p.name = c.getString(1);
                byId.put(p.id, p);
            }
        }
        try (Cursor c = db.rawQuery("SELECT i.preset_id AS preset_id, i.amount AS amount, f.*, 0 AS uses"
                + " FROM preset_item i JOIN food f ON f.id = i.food_id"
                + (id == null ? "" : " WHERE i.preset_id = ?") + " ORDER BY i.id", a)) {
            int presetCol = c.getColumnIndexOrThrow("preset_id");
            int amountCol = c.getColumnIndexOrThrow("amount");
            while (c.moveToNext()) {
                Preset p = byId.get(c.getLong(presetCol));
                if (p != null) p.items.add(new Preset.Item(readFood(c), c.getDouble(amountCol)));
            }
        }
        return new ArrayList<>(byId.values());
    }

    long savePreset(Preset p) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            if (p.id > 0) {
                ContentValues v = new ContentValues();
                v.put("name", p.name);
                db.update("preset", v, "id = ?", args(p.id));
                db.delete("preset_item", "preset_id = ?", args(p.id));
                insertPresetItems(db, p);
            } else {
                insertPreset(db, p);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return p.id;
    }

    void deletePreset(long id) {
        getWritableDatabase().delete("preset", "id = ?", args(id));
    }

    private static void insertPreset(SQLiteDatabase db, Preset p) {
        ContentValues v = new ContentValues();
        v.put("name", p.name);
        p.id = db.insertOrThrow("preset", null, v);
        insertPresetItems(db, p);
    }

    private static void insertPresetItems(SQLiteDatabase db, Preset p) {
        for (Preset.Item item : p.items) {
            if (item.food.id <= 0) continue;
            ContentValues v = new ContentValues();
            v.put("preset_id", p.id);
            v.put("food_id", item.food.id);
            v.put("amount", item.amount);
            db.insertOrThrow("preset_item", null, v);
        }
    }

    // Diary

    List<Meal> meals(long day) {
        return loadMeals("WHERE day = ?", args(day));
    }

    Meal meal(long id) {
        List<Meal> list = loadMeals("WHERE id = ?", args(id));
        if (list.isEmpty()) return null;
        Meal m = list.get(0);
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM meal WHERE day = ? AND id <= ?", args(m.day, m.id))) {
            if (c.moveToFirst()) m.number = c.getInt(0);
        }
        return m;
    }

    /** Meals of one query, ordered by day then creation, numbered within each day. */
    private List<Meal> loadMeals(String where, String[] a) {
        SQLiteDatabase db = getReadableDatabase();
        Map<Long, Meal> byId = new LinkedHashMap<>();
        try (Cursor c = db.rawQuery("SELECT id, day, created, name FROM meal " + where + " ORDER BY day, id", a)) {
            long day = Long.MIN_VALUE;
            int number = 0;
            while (c.moveToNext()) {
                Meal m = new Meal();
                m.id = c.getLong(0);
                m.day = c.getLong(1);
                m.created = c.getLong(2);
                m.name = c.getString(3);
                number = m.day == day ? number + 1 : 1;
                day = m.day;
                m.number = number;
                byId.put(m.id, m);
            }
        }
        if (byId.isEmpty()) return new ArrayList<>();
        try (Cursor c = db.rawQuery("SELECT * FROM entry WHERE meal_id IN (SELECT id FROM meal " + where + ")"
                + " ORDER BY id", a)) {
            while (c.moveToNext()) {
                Meal.Entry e = readEntry(c);
                Meal m = byId.get(e.mealId);
                if (m != null) m.entries.add(e);
            }
        }
        return new ArrayList<>(byId.values());
    }

    long addMeal(long day) {
        ContentValues v = new ContentValues();
        v.put("day", day);
        v.put("created", System.currentTimeMillis());
        return getWritableDatabase().insertOrThrow("meal", null, v);
    }

    /** The day's most recently created meal, or null. Only id, day and created are filled in. */
    Meal lastMeal(long day) {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT id, created FROM meal WHERE day = ? ORDER BY id DESC LIMIT 1", args(day))) {
            if (!c.moveToFirst()) return null;
            Meal m = new Meal();
            m.id = c.getLong(0);
            m.day = day;
            m.created = c.getLong(1);
            return m;
        }
    }

    void renameMeal(long id, String name) {
        ContentValues v = new ContentValues();
        v.put("name", name);
        getWritableDatabase().update("meal", v, "id = ?", args(id));
    }

    void deleteMeal(long id) {
        getWritableDatabase().delete("meal", "id = ?", args(id));
        changed();
    }

    void addEntry(long mealId, Food food, double amount) {
        insertEntry(getWritableDatabase(), entryFor(mealId, food, amount));
        changed();
    }

    void addPreset(long mealId, Preset preset, double times) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            for (Preset.Item item : preset.items) insertEntry(db, entryFor(mealId, item.food, item.amount * times));
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        changed();
    }

    void setEntryAmount(Meal.Entry e, double amount) {
        Nutrients n = e.nutrients.times(amount / e.amount);
        ContentValues v = new ContentValues();
        v.put("amount", amount);
        putNutrients(v, n);
        getWritableDatabase().update("entry", v, "id = ?", args(e.id));
        changed();
    }

    /** Moves an entry to another meal. The day's totals don't change, so the widget stays as it is. */
    void moveEntry(long entryId, long mealId) {
        ContentValues v = new ContentValues();
        v.put("meal_id", mealId);
        getWritableDatabase().update("entry", v, "id = ?", args(entryId));
    }

    void deleteEntry(long id) {
        getWritableDatabase().delete("entry", "id = ?", args(id));
        changed();
    }

    private static Meal.Entry entryFor(long mealId, Food food, double amount) {
        Meal.Entry e = new Meal.Entry();
        e.mealId = mealId;
        e.foodId = food.id > 0 ? food.id : null;
        e.name = food.name;
        e.amount = amount;
        e.unit = food.unit;
        e.nutrients = food.forAmount(amount);
        return e;
    }

    private static void insertEntry(SQLiteDatabase db, Meal.Entry e) {
        ContentValues v = new ContentValues();
        v.put("meal_id", e.mealId);
        if (e.foodId != null) v.put("food_id", e.foodId);
        else v.putNull("food_id");
        v.put("name", e.name);
        v.put("amount", e.amount);
        v.put("unit", e.unit);
        putNutrients(v, e.nutrients);
        e.id = db.insertOrThrow("entry", null, v);
    }

    // Stats

    Nutrients dayTotal(long day) {
        Nutrients n = dailyTotals(day, day).get(day);
        return n != null ? n : new Nutrients();
    }

    /** Totals per day for days with at least one entry, from..to inclusive. */
    Map<Long, Nutrients> dailyTotals(long from, long to) {
        Map<Long, Nutrients> out = new HashMap<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT m.day, SUM(e.kcal), SUM(e.protein),"
                + " SUM(e.carbs), SUM(e.fat) FROM entry e JOIN meal m ON m.id = e.meal_id"
                + " WHERE m.day BETWEEN ? AND ? GROUP BY m.day", args(from, to))) {
            while (c.moveToNext()) {
                out.put(c.getLong(0), new Nutrients(c.getDouble(1), c.getDouble(2), c.getDouble(3), c.getDouble(4)));
            }
        }
        return out;
    }

    /** First day with any entry, or null when the diary is empty. */
    Long firstDay() {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT MIN(m.day) FROM meal m JOIN entry e ON e.meal_id = m.id", null)) {
            return c.moveToFirst() && !c.isNull(0) ? c.getLong(0) : null;
        }
    }

    // Backup and import

    ImportData snapshot() {
        ImportData d = new ImportData();
        d.foods.addAll(foods());
        d.presets.addAll(presets());
        d.meals.addAll(loadMeals("", null));
        return d;
    }

    /**
     * Writes parsed data. With replace, everything is wiped first. Otherwise
     * foods already present (same barcode, or same name and brand) are reused
     * instead of duplicated.
     */
    void importData(ImportData data, boolean replace) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            if (replace) {
                db.delete("entry", null, null);
                db.delete("meal", null, null);
                db.delete("preset_item", null, null);
                db.delete("preset", null, null);
                db.delete("food", null, null);
            }
            Map<String, Long> known = new HashMap<>();
            if (!replace) {
                for (Food f : foods()) remember(known, f);
            }
            for (Food f : data.foods) {
                Long existing = known.get(barcodeKey(f));
                if (existing == null) existing = known.get(nameKey(f));
                if (existing != null) {
                    f.id = existing;
                    continue;
                }
                f.id = db.insertOrThrow("food", null, foodValues(f));
                remember(known, f);
            }
            for (Preset p : data.presets) insertPreset(db, p);
            for (Meal m : data.meals) {
                ContentValues v = new ContentValues();
                v.put("day", m.day);
                v.put("created", m.created);
                v.put("name", m.name);
                m.id = db.insertOrThrow("meal", null, v);
                for (Meal.Entry e : m.entries) {
                    e.mealId = m.id;
                    e.foodId = e.food != null && e.food.id > 0 ? e.food.id : null;
                    insertEntry(db, e);
                }
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        changed();
    }

    private static void remember(Map<String, Long> known, Food f) {
        String barcode = barcodeKey(f);
        if (barcode != null) known.putIfAbsent(barcode, f.id);
        known.putIfAbsent(nameKey(f), f.id);
    }

    private static String barcodeKey(Food f) {
        return f.barcode.isEmpty() ? null : "barcode:" + f.barcode;
    }

    private static String nameKey(Food f) {
        return "name:" + f.name.toLowerCase(Locale.ROOT) + "|" + f.brand.toLowerCase(Locale.ROOT);
    }

    // Row mapping

    private static Food readFood(Cursor c) {
        Food f = new Food();
        f.id = c.getLong(c.getColumnIndexOrThrow("id"));
        f.name = c.getString(c.getColumnIndexOrThrow("name"));
        f.brand = c.getString(c.getColumnIndexOrThrow("brand"));
        f.barcode = c.getString(c.getColumnIndexOrThrow("barcode"));
        f.unit = c.getString(c.getColumnIndexOrThrow("unit"));
        f.per = c.getDouble(c.getColumnIndexOrThrow("per"));
        f.nutrients = readNutrients(c);
        f.serving = c.getDouble(c.getColumnIndexOrThrow("serving"));
        f.uses = c.getInt(c.getColumnIndexOrThrow("uses"));
        return f;
    }

    private static Meal.Entry readEntry(Cursor c) {
        Meal.Entry e = new Meal.Entry();
        e.id = c.getLong(c.getColumnIndexOrThrow("id"));
        e.mealId = c.getLong(c.getColumnIndexOrThrow("meal_id"));
        int foodCol = c.getColumnIndexOrThrow("food_id");
        e.foodId = c.isNull(foodCol) ? null : c.getLong(foodCol);
        e.name = c.getString(c.getColumnIndexOrThrow("name"));
        e.amount = c.getDouble(c.getColumnIndexOrThrow("amount"));
        e.unit = c.getString(c.getColumnIndexOrThrow("unit"));
        e.nutrients = readNutrients(c);
        return e;
    }

    private static Nutrients readNutrients(Cursor c) {
        return new Nutrients(c.getDouble(c.getColumnIndexOrThrow("kcal")),
                c.getDouble(c.getColumnIndexOrThrow("protein")),
                c.getDouble(c.getColumnIndexOrThrow("carbs")),
                c.getDouble(c.getColumnIndexOrThrow("fat")));
    }

    private static ContentValues foodValues(Food f) {
        ContentValues v = new ContentValues();
        v.put("name", f.name);
        v.put("brand", f.brand);
        v.put("barcode", f.barcode);
        v.put("unit", f.unit);
        v.put("per", f.per);
        putNutrients(v, f.nutrients);
        v.put("serving", f.serving);
        return v;
    }

    private static void putNutrients(ContentValues v, Nutrients n) {
        v.put("kcal", n.kcal);
        v.put("protein", n.protein);
        v.put("carbs", n.carbs);
        v.put("fat", n.fat);
    }

    private static String[] args(Object... values) {
        String[] out = new String[values.length];
        for (int i = 0; i < values.length; i++) out[i] = String.valueOf(values[i]);
        return out;
    }

    private void changed() {
        CalorieWidget.refresh(context);
    }
}
