package com.fx.lab.core.model;

/**
 * Currency pairs supported by the FX CLOB Trading Lab.
 * Includes fixed integer ID (0..5) for zero-allocation array indexing.
 */
public enum CurrencyPair {
    EUR_USD(0, "EUR/USD", 1.0850, 0.0001, 5),
    GBP_USD(1, "GBP/USD", 1.2950, 0.0001, 5),
    USD_JPY(2, "USD/JPY", 154.50, 0.01, 3),
    USD_CHF(3, "USD/CHF", 0.8920, 0.0001, 5),
    AUD_USD(4, "AUD/USD", 0.6550, 0.0001, 5),
    EUR_GBP(5, "EUR/GBP", 0.8380, 0.0001, 5);

    public static final int COUNT = values().length;
    private static final CurrencyPair[] VALUES = values();

    private final int id;
    private final String symbol;
    private final double basePrice;
    private final double tickSize;
    private final int precision;

    CurrencyPair(int id, String symbol, double basePrice, double tickSize, int precision) {
        this.id = id;
        this.symbol = symbol;
        this.basePrice = basePrice;
        this.tickSize = tickSize;
        this.precision = precision;
    }

    public int getId() {
        return id;
    }

    public String getSymbol() {
        return symbol;
    }

    public double getBasePrice() {
        return basePrice;
    }

    public double getTickSize() {
        return tickSize;
    }

    public int getPrecision() {
        return precision;
    }

    public static CurrencyPair fromId(int id) {
        if (id < 0 || id >= VALUES.length) {
            throw new IllegalArgumentException("Unknown CurrencyPair ID: " + id);
        }
        return VALUES[id];
    }

    public static CurrencyPair fromSymbol(String symbol) {
        for (CurrencyPair pair : VALUES) {
            if (pair.symbol.equalsIgnoreCase(symbol) || pair.name().equalsIgnoreCase(symbol)) {
                return pair;
            }
        }
        throw new IllegalArgumentException("Unknown CurrencyPair symbol: " + symbol);
    }
}
