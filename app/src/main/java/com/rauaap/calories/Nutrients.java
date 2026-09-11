package com.rauaap.calories;

/** Calories and macros. Also used for the stats metric index. */
final class Nutrients {
    static final int KCAL = 0;
    static final int PROTEIN = 1;
    static final int CARBS = 2;
    static final int FAT = 3;

    double kcal;
    double protein;
    double carbs;
    double fat;

    Nutrients() {
    }

    Nutrients(double kcal, double protein, double carbs, double fat) {
        this.kcal = kcal;
        this.protein = protein;
        this.carbs = carbs;
        this.fat = fat;
    }

    void add(Nutrients o) {
        kcal += o.kcal;
        protein += o.protein;
        carbs += o.carbs;
        fat += o.fat;
    }

    Nutrients times(double factor) {
        return new Nutrients(kcal * factor, protein * factor, carbs * factor, fat * factor);
    }

    double get(int metric) {
        switch (metric) {
            case PROTEIN:
                return protein;
            case CARBS:
                return carbs;
            case FAT:
                return fat;
            default:
                return kcal;
        }
    }
}
