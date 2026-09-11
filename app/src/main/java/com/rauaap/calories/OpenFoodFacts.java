package com.rauaap.calories;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Open Food Facts lookups. Blocking; call off the main thread.
 *
 * Products always come back with a reference amount of 100 g (100 ml when
 * sold by volume) and per-100 nutrients only. The product's serving size only
 * ever goes to the serving field. Missing per-100 values stay NaN so the
 * form shows them blank instead of guessing from per-serving data.
 */
final class OpenFoodFacts {
    private static final String USER_AGENT = "Calories/1.0 (Android; personal calorie tracker)";
    private static final String FIELDS = "code,product_name,product_name_en,generic_name,brands,quantity,"
            + "product_quantity_unit,serving_quantity,nutriments";

    private OpenFoodFacts() {
    }

    /** The product for a barcode, or null when Open Food Facts doesn't know it. */
    static Food lookup(String barcode) throws IOException, JSONException {
        JSONObject root = get("https://world.openfoodfacts.org/api/v2/product/"
                + URLEncoder.encode(barcode, StandardCharsets.UTF_8) + ".json?fields=" + FIELDS);
        if (root == null || root.optInt("status") != 1) return null;
        return parse(root.getJSONObject("product"), barcode);
    }

    static List<Food> search(String query) throws IOException, JSONException {
        JSONObject root = get("https://search.openfoodfacts.org/search?page_size=40&fields=" + FIELDS
                + "&q=" + URLEncoder.encode(query, StandardCharsets.UTF_8));
        List<Food> out = new ArrayList<>();
        JSONArray hits = root == null ? null : root.optJSONArray("hits");
        if (hits == null) return out;
        for (int i = 0; i < hits.length(); i++) {
            JSONObject product = hits.optJSONObject(i);
            if (product == null) continue;
            Food f = parse(product, str(product, "code"));
            if (!f.name.isEmpty()) out.add(f);
        }
        return out;
    }

    static Food parse(JSONObject p, String barcode) {
        Food f = new Food();
        f.barcode = barcode == null ? "" : barcode;
        f.name = first(str(p, "product_name"), str(p, "product_name_en"), str(p, "generic_name"));
        f.brand = firstBrand(p.opt("brands"));
        f.unit = soldByVolume(p) ? "ml" : "g";
        f.per = 100;
        JSONObject n = p.optJSONObject("nutriments");
        if (n == null) n = new JSONObject();
        double kcal = num(n, "energy-kcal_100g");
        if (Double.isNaN(kcal)) {
            double kj = num(n, "energy-kj_100g");
            if (Double.isNaN(kj)) kj = num(n, "energy_100g");
            kcal = kj / 4.184;
        }
        f.nutrients = new Nutrients(kcal, num(n, "proteins_100g"), num(n, "carbohydrates_100g"), num(n, "fat_100g"));
        double serving = num(p, "serving_quantity");
        f.serving = serving > 0 ? serving : 0;
        return f;
    }

    private static boolean soldByVolume(JSONObject p) {
        if ("ml".equalsIgnoreCase(str(p, "product_quantity_unit"))) return true;
        return str(p, "quantity").toLowerCase(Locale.ROOT).matches(".*\\d\\s*(ml|cl|dl|l)\\b.*");
    }

    private static String firstBrand(Object brands) {
        if (brands instanceof JSONArray a) return a.length() > 0 ? a.optString(0).trim() : "";
        if (brands instanceof String s) return s.split(",")[0].trim();
        return "";
    }

    private static String first(String... values) {
        for (String v : values) {
            if (!v.isEmpty()) return v;
        }
        return "";
    }

    private static String str(JSONObject o, String key) {
        Object v = o.opt(key);
        return v == null || v == JSONObject.NULL ? "" : v.toString().trim();
    }

    private static double num(JSONObject o, String key) {
        Object v = o.opt(key);
        if (v instanceof Number number) return number.doubleValue();
        if (v instanceof String s) return Ui.parse(s);
        return Double.NaN;
    }

    /** GETs JSON; null on 404. */
    private static JSONObject get(String url) throws IOException, JSONException {
        HttpURLConnection con = (HttpURLConnection) URI.create(url).toURL().openConnection();
        try {
            con.setRequestProperty("User-Agent", USER_AGENT);
            con.setConnectTimeout(10_000);
            con.setReadTimeout(20_000);
            int code = con.getResponseCode();
            if (code == HttpURLConnection.HTTP_NOT_FOUND) return null;
            if (code != HttpURLConnection.HTTP_OK) throw new IOException("Open Food Facts returned HTTP " + code);
            try (InputStream in = con.getInputStream()) {
                return new JSONObject(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
        } finally {
            con.disconnect();
        }
    }
}
