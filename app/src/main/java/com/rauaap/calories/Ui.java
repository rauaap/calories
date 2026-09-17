package com.rauaap.calories;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Insets;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.method.DigitsKeyListener;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.NumberFormat;
import java.util.Locale;
import java.util.StringJoiner;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;

/** Shared formatting, input and dialog helpers. */
final class Ui {
    interface InsetsListener {
        void onInsets(WindowInsets insets, int bottomChange);
    }

    private Ui() {
    }

    static int dp(Context c, float dp) {
        return Math.round(dp * c.getResources().getDisplayMetrics().density);
    }

    /** Pads a root view by the system bars and keyboard (the app is edge-to-edge). */
    static void fitInsets(View root, InsetsListener... listeners) {
        final int[] previousBottom = {-1};
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            Insets i = insets.getInsets(WindowInsets.Type.systemBars()
                    | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
            int bottomChange = previousBottom[0] < 0 ? 0 : i.bottom - previousBottom[0];
            previousBottom[0] = i.bottom;
            v.setPadding(i.left, i.top, i.right, i.bottom);
            for (InsetsListener listener : listeners) listener.onInsets(insets, bottomChange);
            return WindowInsets.CONSUMED;
        });
    }

    /** Keeps a ListView anchored to the keyboard and restores its exact position when it closes. */
    static InsetsListener followKeyboard(ListView list) {
        final boolean[] keyboardVisible = {false};
        final int[] position = {ListView.INVALID_POSITION};
        final int[] top = {0};
        return (insets, bottomChange) -> {
            boolean visible = insets.isVisible(WindowInsets.Type.ime());
            if (visible && !keyboardVisible[0]) {
                position[0] = list.getFirstVisiblePosition();
                View first = list.getChildAt(0);
                top[0] = first == null ? 0 : first.getTop();
            }
            if (!visible && keyboardVisible[0]) {
                if (position[0] != ListView.INVALID_POSITION) {
                    list.post(() -> list.setSelectionFromTop(position[0], top[0]));
                }
            } else if (visible && bottomChange != 0) {
                list.post(() -> list.scrollListBy(bottomChange));
            }
            keyboardVisible[0] = visible;
        };
    }

    /** Keeps a ScrollView anchored to the keyboard and restores its exact position when it closes. */
    static InsetsListener followKeyboard(ScrollView scroll) {
        final boolean[] keyboardVisible = {false};
        final int[] position = {0};
        return (insets, bottomChange) -> {
            boolean visible = insets.isVisible(WindowInsets.Type.ime());
            if (visible && !keyboardVisible[0]) position[0] = scroll.getScrollY();
            if (!visible && keyboardVisible[0]) {
                scroll.post(() -> scroll.scrollTo(0, position[0]));
            } else if (visible && bottomChange != 0) {
                scroll.post(() -> scroll.scrollBy(0, bottomChange));
            }
            keyboardVisible[0] = visible;
        };
    }

    static String kcal(double v) {
        return NumberFormat.getIntegerInstance().format(Math.round(v));
    }

    static String grams(double v) {
        NumberFormat f = NumberFormat.getNumberInstance();
        f.setMaximumFractionDigits(Math.abs(v) < 10 ? 1 : 0);
        return f.format(v);
    }

    static String amount(double v) {
        NumberFormat f = NumberFormat.getNumberInstance();
        f.setMaximumFractionDigits(2);
        f.setGroupingUsed(false);
        return f.format(v);
    }

    /** "150g", "250ml", but "2 pcs" and "300 kcal": unit symbols hug the number, words get a space. */
    static String amount(double v, String unit) {
        return amount(v) + ("g".equals(unit) || "ml".equals(unit) ? "" : " ") + unit;
    }

    /** "02:00" for a number of minutes, used for times of day and intervals alike. */
    static String clock(int minutes) {
        return String.format(Locale.ROOT, "%02d:%02d", minutes / 60, minutes % 60);
    }

    static String macros(Nutrients n) {
        return "F " + grams(n.fat) + "g · C " + grams(n.carbs) + "g · P " + grams(n.protein) + "g";
    }

    /** Joins the non-empty parts with a middle dot. */
    static String join(String... parts) {
        StringJoiner j = new StringJoiner(" · ");
        for (String p : parts) {
            if (p != null && !p.isEmpty()) j.add(p);
        }
        return j.toString();
    }

    /** Parses a user-typed decimal, accepting either separator. NaN if invalid. */
    static double parse(CharSequence s) {
        try {
            double v = Double.parseDouble(s.toString().trim().replace(',', '.'));
            return Double.isFinite(v) ? v : Double.NaN;
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    /** Numeric keyboard that accepts both '.' and ',' regardless of locale. */
    static void decimalInput(EditText e) {
        e.setKeyListener(DigitsKeyListener.getInstance("0123456789.,"));
        e.setRawInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
    }

    static void onTextChanged(TextView view, Runnable action) {
        view.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                action.run();
            }
        });
    }

    static void showKeyboard(View view) {
        view.requestFocus();
        view.post(() -> {
            WindowInsetsController controller = view.getWindowInsetsController();
            if (controller != null) controller.show(WindowInsets.Type.ime());
        });
    }

    static void hideKeyboard(View view) {
        WindowInsetsController controller = view.getWindowInsetsController();
        if (controller != null) controller.hide(WindowInsets.Type.ime());
    }

    /** True for the keyboard's action key or Enter; see {@link #isPress}. */
    static boolean isEnter(int action, KeyEvent event) {
        return action == EditorInfo.IME_ACTION_NEXT || action == EditorInfo.IME_ACTION_DONE
                || action == EditorInfo.IME_ACTION_GO || action == EditorInfo.IME_ACTION_SEARCH
                || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER);
    }

    /** Hardware Enter reports both down and up; act on the down (or on IME actions, which have no event). */
    static boolean isPress(KeyEvent event) {
        return event == null || event.getAction() == KeyEvent.ACTION_DOWN;
    }

    static void prompt(Context c, String title, String initial, Consumer<String> ok) {
        EditText input = new EditText(c);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setText(initial);
        input.setSelectAllOnFocus(true);
        AlertDialog dialog = new AlertDialog.Builder(c)
                .setTitle(title)
                .setView(padded(c, input))
                .setPositiveButton("OK", (d, w) -> ok.accept(input.getText().toString().trim()))
                .setNegativeButton("Cancel", null)
                .create();
        showWithKeyboard(dialog, input);
    }

    /** Edits an amount; onRemove adds a Remove button when non-null. */
    static void amountDialog(Context c, String title, double amount, String unit,
            DoubleConsumer onSave, Runnable onRemove) {
        EditText input = new EditText(c);
        decimalInput(input);
        input.setText(amount(amount));
        input.setSelectAllOnFocus(true);
        TextView suffix = new TextView(c);
        suffix.setText(unit);
        suffix.setTextColor(c.getColor(R.color.text_secondary));
        suffix.setPadding(dp(c, 8), 0, 0, 0);
        LinearLayout row = new LinearLayout(c);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(input, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        row.addView(suffix);
        AlertDialog.Builder builder = new AlertDialog.Builder(c)
                .setTitle(title)
                .setView(padded(c, row))
                .setPositiveButton("Save", (d, w) -> {
                    double v = parse(input.getText());
                    if (v > 0) onSave.accept(v);
                })
                .setNegativeButton("Cancel", null);
        if (onRemove != null) builder.setNeutralButton("Remove", (d, w) -> onRemove.run());
        showWithKeyboard(builder.create(), input);
    }

    static void confirm(Context c, String message, String action, Runnable ok) {
        new AlertDialog.Builder(c)
                .setMessage(message)
                .setPositiveButton(action, (d, w) -> ok.run())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private static View padded(Context c, View v) {
        FrameLayout frame = new FrameLayout(c);
        frame.setPadding(dp(c, 20), dp(c, 8), dp(c, 20), 0);
        frame.addView(v);
        return frame;
    }

    /** Shows the dialog with the keyboard up; Enter presses the positive button. */
    private static void showWithKeyboard(AlertDialog dialog, EditText input) {
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        input.setOnEditorActionListener((v, action, event) -> {
            if (!isEnter(action, event)) return false;
            if (isPress(event)) dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            return true;
        });
        dialog.show();
        input.requestFocus();
    }
}
