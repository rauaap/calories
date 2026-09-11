package com.rauaap.calories;

import java.util.ArrayList;
import java.util.List;

/** A meal in the diary. Meals are created freely; there are no fixed slots. */
final class Meal {
    long id;
    /** Diary day as an epoch day, already shifted by the day-start setting. */
    long day;
    long created;
    /** Empty means the default "Meal N" name. */
    String name = "";
    /** 1-based position within the day. */
    int number;
    final List<Entry> entries = new ArrayList<>();

    String title() {
        return name.isEmpty() ? "Meal " + number : name;
    }

    Nutrients total() {
        Nutrients sum = new Nutrients();
        for (Entry e : entries) sum.add(e.nutrients);
        return sum;
    }

    /**
     * A logged food. Name, unit and nutrients are a snapshot taken when it was
     * logged, so editing or deleting the food later doesn't change history.
     */
    static final class Entry {
        long id;
        long mealId;
        Long foodId;
        String name = "";
        double amount;
        String unit = "";
        Nutrients nutrients = new Nutrients();
        /** Link to a not-yet-saved food during import; resolved to foodId on insert. */
        Food food;
    }
}
