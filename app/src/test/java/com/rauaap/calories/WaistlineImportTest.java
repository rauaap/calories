package com.rauaap.calories;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import org.junit.Assume;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public class WaistlineImportTest {
    private static final String FIXTURE = "{"
            + "\"version\":34,"
            + "\"settings\":{\"diary\":{\"meal-names\":[\"Breakfast\",\"Lunch\",\"Dinner\",\"Snacks\",\"\",\"\",\"\"]}},"
            + "\"foodList\":["
            + "{\"id\":1,\"name\":\"Bread\",\"brand\":\"B\",\"barcode\":\"123\",\"portion\":\"38\",\"unit\":\"g\","
            + "\"nutrition\":{\"calories\":95,\"proteins\":3.8,\"carbohydrates\":15.2,\"fat\":1.9}},"
            + "{\"id\":2,\"name\":\"Bun\",\"portion\":\"1\",\"unit\":\"bun\",\"nutrition\":{\"calories\":200,\"proteins\":null}},"
            + "{\"id\":3,\"name\":\"Old yogurt\",\"portion\":\"150\",\"unit\":\"g\",\"archived\":true,\"nutrition\":{\"calories\":120}},"
            + "{\"id\":4,\"name\":\"Juice\",\"portion\":\"0.5\",\"unit\":\"l\",\"nutrition\":{\"calories\":220}},"
            + "{\"id\":42,\"name\":\"Quick Add\",\"barcode\":\"quick-add\",\"portion\":1,\"nutrition\":{\"calories\":1},\"archived\":true}"
            + "],"
            + "\"diary\":[{\"dateTime\":\"2026-09-10T00:00:00.000Z\",\"items\":["
            + "{\"id\":1,\"portion\":\"76\",\"quantity\":\"1\",\"type\":\"food\",\"category\":0,"
            + "\"dateTime\":\"2026-09-10T07:44:44.618Z\"},"
            + "{\"id\":2,\"portion\":\"1\",\"quantity\":\"2\",\"type\":\"food\",\"category\":3},"
            + "{\"id\":3,\"portion\":\"75\",\"quantity\":\"1\",\"type\":\"food\",\"category\":3},"
            + "{\"id\":42,\"portion\":\"1\",\"quantity\":\"250\",\"type\":\"food\",\"category\":3},"
            + "{\"quantity\":\"1\"},"
            + "{\"id\":99,\"portion\":\"10\",\"quantity\":\"1\",\"type\":\"food\",\"category\":1}"
            + "]}],"
            + "\"meals\":[{\"id\":1,\"name\":\"Porridge\",\"items\":["
            + "{\"id\":1,\"portion\":\"38\",\"quantity\":\"1\",\"type\":\"food\"},"
            + "{\"id\":3,\"portion\":\"10\",\"quantity\":\"1\",\"type\":\"food\"}]}]"
            + "}";

    @Test
    public void foodsGetReferenceAmountAndServing() throws Exception {
        ImportData d = WaistlineImport.parse(FIXTURE);
        assertEquals(3, d.foods.size()); // archived and quick-add aren't listed

        Food bread = d.foods.get(0);
        assertEquals("g", bread.unit);
        assertEquals(100, bread.per, 1e-9);
        assertEquals(38, bread.serving, 1e-9);
        assertEquals(250, bread.nutrients.kcal, 1e-9);
        assertEquals(10, bread.nutrients.protein, 1e-9);

        Food bun = d.foods.get(1);
        assertEquals("pcs", bun.unit);
        assertEquals(1, bun.per, 1e-9);
        assertEquals(0, bun.serving, 1e-9);
        assertEquals(0, bun.nutrients.protein, 1e-9);

        Food juice = d.foods.get(2);
        assertEquals("ml", juice.unit);
        assertEquals(500, juice.serving, 1e-9);
        assertEquals(44, juice.nutrients.kcal, 1e-9);
    }

    @Test
    public void diaryMatchesWaistlineMath() throws Exception {
        ImportData d = WaistlineImport.parse(FIXTURE);
        assertEquals(2, d.skipped); // the item without an id, and the unknown food
        assertEquals(2, d.meals.size());

        Meal breakfast = d.meals.get(0);
        assertEquals("Breakfast", breakfast.name);
        assertEquals(LocalDate.of(2026, 9, 10).toEpochDay(), breakfast.day);
        assertEquals(Instant.parse("2026-09-10T07:44:44.618Z").toEpochMilli(), breakfast.created);
        assertEquals(190, breakfast.entries.get(0).nutrients.kcal, 1e-9);
        assertEquals(76, breakfast.entries.get(0).amount, 1e-9);
        assertSame(d.foods.get(0), breakfast.entries.get(0).food);

        Meal snacks = d.meals.get(1);
        assertEquals("Snacks", snacks.name);
        assertEquals(400 + 60 + 250, snacks.total().kcal, 1e-9);
        Meal.Entry archived = snacks.entries.get(1);
        assertNull(archived.food);
        assertEquals("g", archived.unit);
        assertEquals(75, archived.amount, 1e-9);
        Meal.Entry quick = snacks.entries.get(2);
        assertEquals("kcal", quick.unit);
        assertEquals(250, quick.amount, 1e-9);
    }

    @Test
    public void savedMealsBecomePresetsOfListedFoods() throws Exception {
        ImportData d = WaistlineImport.parse(FIXTURE);
        assertEquals(1, d.presets.size());
        Preset p = d.presets.get(0);
        assertEquals("Porridge", p.name);
        assertEquals(1, p.items.size()); // the archived yogurt is dropped
        assertEquals(38, p.items.get(0).amount, 1e-9);
    }

    /**
     * Runs against a real export in the repo root when present (it's gitignored),
     * writing per-day totals to build/waistline-totals.tsv for comparison.
     */
    @Test
    public void realExport() throws Exception {
        File file = new File("../waistline_export.json");
        Assume.assumeTrue(file.exists());
        ImportData d = WaistlineImport.parse(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
        Map<Long, Nutrients> totals = new TreeMap<>();
        for (Meal m : d.meals) totals.computeIfAbsent(m.day, k -> new Nutrients()).add(m.total());
        StringBuilder out = new StringBuilder(String.format(Locale.ROOT,
                "# foods=%d presets=%d days=%d meals=%d entries=%d skipped=%d%n",
                d.foods.size(), d.presets.size(), d.dayCount(), d.meals.size(), d.entryCount(), d.skipped));
        for (Map.Entry<Long, Nutrients> e : totals.entrySet()) {
            Nutrients n = e.getValue();
            out.append(String.format(Locale.ROOT, "%s\t%.2f\t%.2f\t%.2f\t%.2f%n",
                    LocalDate.ofEpochDay(e.getKey()), n.kcal, n.protein, n.carbs, n.fat));
        }
        Files.write(new File("build/waistline-totals.tsv").toPath(), out.toString().getBytes(StandardCharsets.UTF_8));
    }
}
