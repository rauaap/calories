package com.rauaap.calories;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Foods, presets and diary parsed from a file (or snapshotted for export), not yet in the database. */
final class ImportData {
    final List<Food> foods = new ArrayList<>();
    final List<Preset> presets = new ArrayList<>();
    final List<Meal> meals = new ArrayList<>();
    /** Source items that couldn't be converted. */
    int skipped;
    /** Settings carried by our own backups; null when absent. */
    Integer dayStart;
    Boolean chartScaled;

    int entryCount() {
        int n = 0;
        for (Meal m : meals) n += m.entries.size();
        return n;
    }

    int dayCount() {
        Set<Long> days = new HashSet<>();
        for (Meal m : meals) days.add(m.day);
        return days.size();
    }
}
