package com.rauaap.calories;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import org.junit.Test;

import java.util.List;

public class SearchTest {
    private static final long DAY = 24L * 60 * 60 * 1000;

    @Test
    public void eachUseLosesHalfItsWeightInThirtyDays() {
        long now = 100L * DAY;
        assertEquals(1, Search.useWeight(now, now), 1e-12);
        assertEquals(0.5, Search.useWeight(now - 30L * DAY, now), 1e-12);
        assertEquals(0.25, Search.useWeight(now - 60L * DAY, now), 1e-12);
    }

    @Test
    public void futureUsesDoNotGetExtraWeight() {
        long now = 100L * DAY;
        assertEquals(1, Search.useWeight(now + DAY, now), 1e-12);
    }

    @Test
    public void frecencyBreaksTiesWithinATextTier() {
        Food chocolate = food("Chocolate milk", 2);
        Food oat = food("Oat milk", 5);

        List<Object> ranked = Search.rank(List.of(chocolate, oat), "milk", 10);

        assertSame(oat, ranked.get(0));
        assertSame(chocolate, ranked.get(1));
    }

    @Test
    public void textQualityStillComesBeforeFrecency() {
        Food prefix = food("Milk chocolate", 1);
        Food wordPrefix = food("Dark milk chocolate", 100);

        List<Object> ranked = Search.rank(List.of(wordPrefix, prefix), "milk chocolate", 10);

        assertSame(prefix, ranked.get(0));
        assertSame(wordPrefix, ranked.get(1));
    }

    private static Food food(String name, double frecency) {
        Food food = new Food();
        food.name = name;
        food.frecency = frecency;
        return food;
    }
}
