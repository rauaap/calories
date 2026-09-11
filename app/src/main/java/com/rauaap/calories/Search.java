package com.rauaap.calories;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Ranks foods and presets against typed text. Every word must appear in the
 * name or brand; names starting with the text rank first, then names with a
 * word starting with it, then any match. Ties go to the most-used food.
 */
final class Search {
    private Search() {
    }

    private static final class Hit {
        final Object item;
        final int score;

        Hit(Object item, int score) {
            this.item = item;
            this.score = score;
        }
    }

    static List<Object> rank(List<?> items, String query, int limit) {
        List<Object> out = new ArrayList<>();
        String q = query.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) return out;
        String[] words = q.split("\\s+");
        List<Hit> hits = new ArrayList<>();
        for (Object item : items) {
            String name = name(item).toLowerCase(Locale.ROOT);
            String haystack = name + " " + brand(item).toLowerCase(Locale.ROOT);
            boolean all = true;
            for (String w : words) {
                if (!haystack.contains(w)) {
                    all = false;
                    break;
                }
            }
            if (!all) continue;
            int score = name.startsWith(q) ? 0 : startsWord(name, words[0]) ? 1 : 2;
            hits.add(new Hit(item, score));
        }
        hits.sort(Comparator.<Hit>comparingInt(h -> h.score)
                .thenComparingInt(h -> -uses(h.item))
                .thenComparing(h -> name(h.item), String.CASE_INSENSITIVE_ORDER));
        for (int i = 0; i < hits.size() && i < limit; i++) out.add(hits.get(i).item);
        return out;
    }

    static String name(Object item) {
        return item instanceof Food f ? f.name : ((Preset) item).name;
    }

    private static String brand(Object item) {
        return item instanceof Food f ? f.brand : "";
    }

    private static int uses(Object item) {
        return item instanceof Food f ? f.uses : 0;
    }

    private static boolean startsWord(String text, String word) {
        for (int i = text.indexOf(word); i >= 0; i = text.indexOf(word, i + 1)) {
            if (i == 0 || !Character.isLetterOrDigit(text.charAt(i - 1))) return true;
        }
        return false;
    }
}
