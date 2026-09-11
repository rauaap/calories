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

    /** Whether stats bars start just under the lowest day instead of at zero. */
    static boolean chartScaled(Context c) {
        return prefs(c).getBoolean("chart_scaled", false);
    }

    static void setChartScaled(Context c, boolean scaled) {
        prefs(c).edit().putBoolean("chart_scaled", scaled).apply();
    }
}
