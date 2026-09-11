package com.rauaap.calories;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.util.TypedValue;
import android.view.View;
import android.widget.RemoteViews;

import java.util.ArrayList;
import java.util.List;

/**
 * Home-screen widget with today's calories and optionally macros. Redrawn
 * whenever the diary changes, and at the configured day start via an alarm.
 */
public class CalorieWidget extends AppWidgetProvider {
    private static final String ACTION_ROLLOVER = "com.rauaap.calories.ROLLOVER";

    /** Per-widget appearance, set in WidgetConfigActivity. */
    static final class Config {
        boolean protein;
        boolean carbs;
        boolean fat;
        int textColor;
        int backgroundColor;
        /** Corner radius in dp. */
        int radius;

        static Config load(Context c, int widgetId) {
            SharedPreferences p = prefs(c);
            String k = widgetId + "_";
            Config cfg = new Config();
            cfg.protein = p.getBoolean(k + "protein", false);
            cfg.carbs = p.getBoolean(k + "carbs", false);
            cfg.fat = p.getBoolean(k + "fat", false);
            cfg.textColor = p.getInt(k + "text", Color.WHITE);
            cfg.backgroundColor = p.getInt(k + "background", c.getColor(R.color.background));
            cfg.radius = p.getInt(k + "radius", 16);
            return cfg;
        }

        void save(Context c, int widgetId) {
            String k = widgetId + "_";
            prefs(c).edit()
                    .putBoolean(k + "protein", protein)
                    .putBoolean(k + "carbs", carbs)
                    .putBoolean(k + "fat", fat)
                    .putInt(k + "text", textColor)
                    .putInt(k + "background", backgroundColor)
                    .putInt(k + "radius", radius)
                    .apply();
        }

        static void delete(Context c, int widgetId) {
            String k = widgetId + "_";
            SharedPreferences.Editor e = prefs(c).edit();
            for (String key : new String[] {"protein", "carbs", "fat", "text", "background", "radius"}) e.remove(k + key);
            e.apply();
        }

        private static SharedPreferences prefs(Context c) {
            return c.getSharedPreferences("widgets", Context.MODE_PRIVATE);
        }
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] widgetIds) {
        for (int id : widgetIds) update(context, manager, id);
        scheduleRollover(context);
    }

    @Override
    public void onDeleted(Context context, int[] widgetIds) {
        for (int id : widgetIds) Config.delete(context, id);
    }

    @Override
    public void onDisabled(Context context) {
        context.getSystemService(AlarmManager.class).cancel(rolloverIntent(context));
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (ACTION_ROLLOVER.equals(intent.getAction())) refresh(context);
        else super.onReceive(context, intent);
    }

    /** Redraws every placed widget. */
    static void refresh(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, CalorieWidget.class));
        if (ids.length == 0) return;
        for (int id : ids) update(context, manager, id);
        scheduleRollover(context);
    }

    static void update(Context context, AppWidgetManager manager, int widgetId) {
        Nutrients today = Db.get(context).dayTotal(Days.today(context));
        RemoteViews views = views(context, Config.load(context, widgetId), today);
        Intent open = new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        views.setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(context, 0, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        manager.updateAppWidget(widgetId, views);
    }

    /** The widget's content without a click action; also used for the config preview. */
    static RemoteViews views(Context context, Config cfg, Nutrients today) {
        RemoteViews v = new RemoteViews(context.getPackageName(), R.layout.widget);
        v.setTextViewText(R.id.widget_kcal, Ui.kcal(today.kcal));
        v.setTextColor(R.id.widget_kcal, cfg.textColor);
        v.setTextColor(R.id.widget_unit, fade(cfg.textColor, 0.7f));
        List<String> macros = new ArrayList<>();
        if (cfg.protein) macros.add("P " + Ui.grams(today.protein) + "g");
        if (cfg.carbs) macros.add("C " + Ui.grams(today.carbs) + "g");
        if (cfg.fat) macros.add("F " + Ui.grams(today.fat) + "g");
        v.setTextViewText(R.id.widget_macros, String.join(" · ", macros));
        v.setTextColor(R.id.widget_macros, fade(cfg.textColor, 0.85f));
        v.setViewVisibility(R.id.widget_macros, macros.isEmpty() ? View.GONE : View.VISIBLE);
        v.setInt(R.id.widget_root, "setBackgroundColor", cfg.backgroundColor);
        v.setViewOutlinePreferredRadius(R.id.widget_root, cfg.radius, TypedValue.COMPLEX_UNIT_DIP);
        return v;
    }

    private static int fade(int color, float factor) {
        int alpha = Math.round(Color.alpha(color) * factor);
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    /** Inexact is fine: the widget only has to flip to the new day's zero. */
    private static void scheduleRollover(Context context) {
        context.getSystemService(AlarmManager.class).setAndAllowWhileIdle(AlarmManager.RTC,
                Days.nextBoundaryMillis(context) + 1000, rolloverIntent(context));
    }

    private static PendingIntent rolloverIntent(Context context) {
        Intent intent = new Intent(context, CalorieWidget.class).setAction(ACTION_ROLLOVER);
        return PendingIntent.getBroadcast(context, 0, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }
}
