package com.rauaap.calories;

import java.util.ArrayList;
import java.util.List;

/** A saved meal: foods with amounts, added to the diary in one go. */
final class Preset {
    long id;
    String name = "";
    final List<Item> items = new ArrayList<>();

    Nutrients total() {
        Nutrients sum = new Nutrients();
        for (Item item : items) sum.add(item.food.forAmount(item.amount));
        return sum;
    }

    static final class Item {
        final Food food;
        double amount;

        Item(Food food, double amount) {
            this.food = food;
            this.amount = amount;
        }
    }
}
