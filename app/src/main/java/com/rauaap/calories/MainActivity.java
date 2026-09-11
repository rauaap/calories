package com.rauaap.calories;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Insets;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;
import android.widget.TextView;

/** Diary, Stats and Foods tabs. */
public class MainActivity extends Activity {
    /** A top-level tab. */
    interface Screen {
        void refresh();
    }

    private static final String[] TITLES = {"Diary", "Stats", "Foods"};

    private View[] screens;
    private TextView[] tabs;
    private TextView title;
    private View tabBar;
    private DiaryView diary;
    private int current;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        title = findViewById(R.id.main_title);
        tabBar = findViewById(R.id.main_tabs);
        diary = findViewById(R.id.screen_diary);
        screens = new View[] {diary, findViewById(R.id.screen_stats), findViewById(R.id.screen_foods)};
        tabs = new TextView[] {findViewById(R.id.tab_diary), findViewById(R.id.tab_stats), findViewById(R.id.tab_foods)};
        for (int i = 0; i < tabs.length; i++) {
            int index = i;
            tabs[i].setOnClickListener(v -> {
                select(index);
                ((Screen) screens[index]).refresh();
            });
        }
        findViewById(R.id.main_settings).setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));

        // Edge-to-edge: pad for system bars and keyboard, and drop the tabs while typing.
        findViewById(R.id.root).setOnApplyWindowInsetsListener((v, insets) -> {
            Insets i = insets.getInsets(WindowInsets.Type.systemBars()
                    | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
            v.setPadding(i.left, i.top, i.right, i.bottom);
            tabBar.setVisibility(insets.isVisible(WindowInsets.Type.ime()) ? View.GONE : View.VISIBLE);
            return WindowInsets.CONSUMED;
        });
        select(savedInstanceState != null ? savedInstanceState.getInt("tab") : 0);
        // Only on a real launch: not when coming back to the app, or after a rotation.
        if (savedInstanceState == null && current == 0 && Prefs.focusQuickAdd(this)) diary.focusQuickAdd();
    }

    @Override
    protected void onResume() {
        super.onResume();
        ((Screen) screens[current]).refresh();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("tab", current);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (!diary.onActivityResult(requestCode, resultCode, data)) super.onActivityResult(requestCode, resultCode, data);
    }

    private void select(int index) {
        current = index;
        for (int i = 0; i < screens.length; i++) {
            screens[i].setVisibility(i == index ? View.VISIBLE : View.GONE);
            tabs[i].setSelected(i == index);
        }
        title.setText(TITLES[index]);
        Ui.hideKeyboard(tabBar);
    }
}
