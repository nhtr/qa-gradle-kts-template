package com.example;

public final class Pricing {
    private Pricing() {
    }

    /**
     * Calculate discounted price, where rate is in [0,1].
     */
    public static double calculateDiscount(double price, double rate) {
        if (rate < 0.0 || rate > 1.0) {
            throw new IllegalArgumentException("rate must be in [0,1]");
        }
        return price * (1.0 - rate);
    }
}
