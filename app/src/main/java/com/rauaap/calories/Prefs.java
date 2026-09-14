package com.rauaap.calories;

import android.content.Context;
import android.content.SharedPreferences;

/** App settings. Widget settings live per widget in CalorieWidget.Config. */
final class Prefs {
    private Prefs() {
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("settings", Context.MODE_PRIVATE);
    }

    /** Minutes after midnight at which a new diary day begins. */
    static int dayStart(Context c) {
        return prefs(c).getInt("day_start", 0);
    }

    static void setDayStart(Context c, int minutes) {
        prefs(c).edit().putInt("day_start", minutes).apply();
    }

    /** Whether launching the app puts the cursor in the quick-add food field. */
    static boolean focusQuickAdd(Context c) {
        return prefs(c).getBoolean("focus_quick_add", false);
    }

    static void setFocusQuickAdd(Context c, boolean focus) {
        prefs(c).edit().putBoolean("focus_quick_add", focus).apply();
    }

    /** Whether quick add starts a new meal once the latest one is older than the interval. */
    static boolean autoMeals(Context c) {
        return prefs(c).getBoolean("auto_meals", false);
    }

    static void setAutoMeals(Context c, boolean on) {
        prefs(c).edit().putBoolean("auto_meals", on).apply();
    }

    /** How long after a meal was created before quick add begins a new one, in minutes. */
    static int autoMealMinutes(Context c) {
        return prefs(c).getInt("auto_meal_minutes", 120);
    }

    static void setAutoMealMinutes(Context c, int minutes) {
        prefs(c).edit().putInt("auto_meal_minutes", minutes).apply();
    }

    /** Whether stats bars start just under the lowest day instead of at zero. */
    static boolean chartScaled(Context c) {
        return prefs(c).getBoolean("chart_scaled", false);
    }

    static void setChartScaled(Context c, boolean scaled) {
        prefs(c).edit().putBoolean("chart_scaled", scaled).apply();
    }
}
