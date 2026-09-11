package com.rauaap.calories;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import org.json.JSONException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Searches Open Food Facts by name. Picking a result opens the prefilled food
 * form; when started for a result, the saved food's id is passed back.
 */
public class OnlineSearchActivity extends Activity {
    static final String EXTRA_QUERY = "query";
    private static final int REQUEST_EDIT = 1;

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private EditText input;
    private TextView status;
    private ItemAdapter<Food> adapter;
    private int generation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);
        Ui.fitInsets(findViewById(R.id.root));
        input = findViewById(R.id.search_input);
        status = findViewById(R.id.search_status);

        adapter = new ItemAdapter<>(R.layout.two_line_item) {
            @Override
            void bind(View view, Food f) {
                ((TextView) view.findViewById(R.id.item_title)).setText(f.name);
                ((TextView) view.findViewById(R.id.item_subtitle)).setText(Ui.join(f.brand, "per " + f.perLabel()));
                ((TextView) view.findViewById(R.id.item_trailing)).setText(
                        Double.isNaN(f.nutrients.kcal) ? "? kcal" : Ui.kcal(f.nutrients.kcal) + " kcal");
            }
        };
        ListView list = findViewById(R.id.search_list);
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> choose(adapter.getItem(position)));

        input.setOnEditorActionListener((v, action, event) -> {
            if (!Ui.isEnter(action, event)) return false;
            if (Ui.isPress(event)) search();
            return true;
        });
        findViewById(R.id.search_back).setOnClickListener(v -> finish());

        String query = getIntent().getStringExtra(EXTRA_QUERY);
        if (query != null && !query.isEmpty()) {
            input.setText(query);
            input.setSelection(query.length());
            search();
        } else {
            Ui.showKeyboard(input);
        }
    }

    @Override
    protected void onDestroy() {
        io.shutdownNow();
        super.onDestroy();
    }

    private void search() {
        String query = input.getText().toString().trim();
        if (query.isEmpty()) return;
        Ui.hideKeyboard(input);
        int current = ++generation;
        adapter.set(new ArrayList<>());
        showStatus("Searching…");
        io.execute(() -> {
            try {
                List<Food> results = OpenFoodFacts.search(query);
                runOnUiThread(() -> {
                    if (current != generation) return;
                    adapter.set(results);
                    showStatus(results.isEmpty() ? "No results for “" + query + "”." : null);
                });
            } catch (IOException | JSONException e) {
                runOnUiThread(() -> {
                    if (current == generation) showStatus("Search failed: " + e.getMessage());
                });
            }
        });
    }

    private void showStatus(String text) {
        status.setText(text);
        status.setVisibility(text == null ? View.GONE : View.VISIBLE);
    }

    private void choose(Food food) {
        Food existing = Db.get(this).foodByBarcode(food.barcode);
        if (existing != null && getCallingActivity() != null) {
            setResult(RESULT_OK, new Intent().putExtra(FoodEditActivity.EXTRA_ID, existing.id));
            finish();
            return;
        }
        Intent edit = new Intent(this, FoodEditActivity.class);
        if (existing != null) edit.putExtra(FoodEditActivity.EXTRA_ID, existing.id);
        else food.toIntent(edit);
        startActivityForResult(edit, REQUEST_EDIT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQUEST_EDIT) {
            super.onActivityResult(requestCode, resultCode, data);
        } else if (resultCode == RESULT_OK) {
            setResult(RESULT_OK, data);
            finish();
        }
    }
}
