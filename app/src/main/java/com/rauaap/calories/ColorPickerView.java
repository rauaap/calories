package com.rauaap.calories;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ComposeShader;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.text.InputType;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;

import java.util.function.IntConsumer;

/** Saturation/value square, hue strip and alpha strip. Use {@link #show} for the dialog with a hex field. */
public class ColorPickerView extends View {
    private static final int[] HUES = {
            0xFFFF0000, 0xFFFFFF00, 0xFF00FF00, 0xFF00FFFF, 0xFF0000FF, 0xFFFF00FF, 0xFFFF0000};

    private final float[] hsv = {0, 0, 1};
    private int alpha = 255;
    private IntConsumer listener;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint checker = new Paint();
    private final RectF sv = new RectF();
    private final RectF hue = new RectF();
    private final RectF alphaBar = new RectF();
    private final Path clip = new Path();
    private final float pad;
    private final float barHeight;
    private final float gap;
    private final float corner;
    /** 0 none, 1 square, 2 hue, 3 alpha. */
    private int dragging;

    public ColorPickerView(Context context) {
        super(context);
        pad = Ui.dp(context, 12);
        barHeight = Ui.dp(context, 24);
        gap = Ui.dp(context, 16);
        corner = Ui.dp(context, 8);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(Ui.dp(context, 2));
        checker.setColor(Color.LTGRAY);
    }

    static void show(Context c, String title, int initial, IntConsumer onPick) {
        ColorPickerView picker = new ColorPickerView(c);
        picker.setColor(initial);
        EditText hex = new EditText(c);
        hex.setSingleLine(true);
        hex.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        hex.setTypeface(Typeface.MONOSPACE);
        hex.setText(hex(initial));
        boolean[] syncing = {false};
        picker.listener = color -> {
            syncing[0] = true;
            hex.setText(hex(color));
            syncing[0] = false;
        };
        Ui.onTextChanged(hex, () -> {
            if (syncing[0]) return;
            Integer color = parseHex(hex.getText().toString());
            if (color != null) picker.setColor(color);
        });
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.dp(c, 12), Ui.dp(c, 8), Ui.dp(c, 12), 0);
        box.addView(picker, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        box.addView(hex);
        new AlertDialog.Builder(c)
                .setTitle(title)
                .setView(box)
                .setPositiveButton("OK", (d, w) -> onPick.accept(picker.getColor()))
                .setNegativeButton("Cancel", null)
                .show();
    }

    static String hex(int color) {
        return String.format("#%08X", color);
    }

    /** #RRGGBB (opaque) or #AARRGGBB; null if malformed. */
    static Integer parseHex(String text) {
        String h = text.trim();
        if (h.startsWith("#")) h = h.substring(1);
        if (!h.matches("[0-9a-fA-F]{6}|[0-9a-fA-F]{8}")) return null;
        long v = Long.parseLong(h, 16);
        return h.length() == 6 ? (int) (0xFF000000L | v) : (int) v;
    }

    int getColor() {
        return Color.HSVToColor(alpha, hsv);
    }

    void setColor(int color) {
        Color.colorToHSV(color, hsv);
        alpha = Color.alpha(color);
        invalidate();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int w = MeasureSpec.getSize(widthSpec);
        int h = (int) (pad * 2 + (w - pad * 2) * 0.55f + gap * 2 + barHeight * 2);
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        float width = w - pad * 2;
        sv.set(pad, pad, pad + width, pad + width * 0.55f);
        hue.set(pad, sv.bottom + gap, pad + width, sv.bottom + gap + barHeight);
        alphaBar.set(pad, hue.bottom + gap, pad + width, hue.bottom + gap + barHeight);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int pure = Color.HSVToColor(new float[] {hsv[0], 1, 1});
        Shader saturation = new LinearGradient(sv.left, 0, sv.right, 0, Color.WHITE, pure, Shader.TileMode.CLAMP);
        Shader value = new LinearGradient(0, sv.top, 0, sv.bottom, Color.WHITE, Color.BLACK, Shader.TileMode.CLAMP);
        paint.setShader(new ComposeShader(saturation, value, PorterDuff.Mode.MULTIPLY));
        canvas.drawRoundRect(sv, corner, corner, paint);

        paint.setShader(new LinearGradient(hue.left, 0, hue.right, 0, HUES, null, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(hue, corner, corner, paint);

        // Alpha: a checkerboard under a transparent-to-opaque ramp of the current color.
        canvas.save();
        clip.reset();
        clip.addRoundRect(alphaBar, corner, corner, Path.Direction.CW);
        canvas.clipPath(clip);
        canvas.drawColor(Color.WHITE);
        float cell = barHeight / 2;
        for (int row = 0; row < 2; row++) {
            for (int col = 0; alphaBar.left + col * cell < alphaBar.right; col++) {
                if ((row + col) % 2 == 0) continue;
                float x = alphaBar.left + col * cell;
                float y = alphaBar.top + row * cell;
                canvas.drawRect(x, y, x + cell, y + cell, checker);
            }
        }
        int opaque = getColor() | 0xFF000000;
        paint.setShader(new LinearGradient(alphaBar.left, 0, alphaBar.right, 0,
                opaque & 0x00FFFFFF, opaque, Shader.TileMode.CLAMP));
        canvas.drawRect(alphaBar, paint);
        canvas.restore();
        paint.setShader(null);

        drawThumb(canvas, sv.left + hsv[1] * sv.width(), sv.top + (1 - hsv[2]) * sv.height());
        drawThumb(canvas, hue.left + hsv[0] / 360f * hue.width(), hue.centerY());
        drawThumb(canvas, alphaBar.left + alpha / 255f * alphaBar.width(), alphaBar.centerY());
    }

    /** White ring with a dark halo, visible on any color. */
    private void drawThumb(Canvas canvas, float x, float y) {
        float r = barHeight / 2 - ring.getStrokeWidth();
        ring.setColor(0x80000000);
        canvas.drawCircle(x, y, r + ring.getStrokeWidth(), ring);
        ring.setColor(Color.WHITE);
        canvas.drawCircle(x, y, r, ring);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                float y = e.getY();
                dragging = y <= sv.bottom + gap / 2 ? 1 : y <= hue.bottom + gap / 2 ? 2 : 3;
                getParent().requestDisallowInterceptTouchEvent(true);
                update(e.getX(), e.getY());
                return true;
            case MotionEvent.ACTION_MOVE:
                update(e.getX(), e.getY());
                return true;
            case MotionEvent.ACTION_UP:
                performClick();
                dragging = 0;
                return true;
            case MotionEvent.ACTION_CANCEL:
                dragging = 0;
                return true;
            default:
                return super.onTouchEvent(e);
        }
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    private void update(float x, float y) {
        float fx = clamp((x - sv.left) / sv.width());
        if (dragging == 1) {
            hsv[1] = fx;
            hsv[2] = 1 - clamp((y - sv.top) / sv.height());
        } else if (dragging == 2) {
            hsv[0] = Math.min(fx * 360f, 359.9f);
        } else if (dragging == 3) {
            alpha = Math.round(fx * 255);
        }
        invalidate();
        if (listener != null) listener.accept(getColor());
    }

    private static float clamp(float v) {
        return Math.max(0, Math.min(1, v));
    }
}
