package com.fx.lab.core.generator;

public enum TrafficProfile {
    NORMAL("Normal", "Stable uniform update rate within nominal capacity", 5_000, 1, 0, 0.0001),
    BURST("Burst", "Periodic short high-rate bursts", 8_000, 500, 2000, 0.0002),
    HIGH_LOAD("High Load", "High sustained rate near CLOB limit", 25_000, 100, 1000, 0.0002),
    OVERLOAD("Overload", "Rate intentionally exceeds processing capacity to force deep coalescing", 70_000, 2000, 500, 0.0003),
    SPIKE("Spike", "Sudden massive impulse burst", 10_000, 5000, 3000, 0.0004),
    RECOVERY("Recovery", "Transitions from overload down to normal", 5_000, 50, 0, 0.0001),
    EXTREME_COALESCING("Extreme Coalescing", "Ultra-high rate producing coalescing depth > 50", 150_000, 10_000, 200, 0.0005);

    private final String displayName;
    private final String description;
    private final int defaultRatePerSec;
    private final int defaultBurstSize;
    private final int defaultBurstIntervalMs;
    private final double defaultVolatility;

    TrafficProfile(String displayName, String description, int defaultRatePerSec,
                   int defaultBurstSize, int defaultBurstIntervalMs, double defaultVolatility) {
        this.displayName = displayName;
        this.description = description;
        this.defaultRatePerSec = defaultRatePerSec;
        this.defaultBurstSize = defaultBurstSize;
        this.defaultBurstIntervalMs = defaultBurstIntervalMs;
        this.defaultVolatility = defaultVolatility;
    }

    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public int getDefaultRatePerSec() { return defaultRatePerSec; }
    public int getDefaultBurstSize() { return defaultBurstSize; }
    public int getDefaultBurstIntervalMs() { return defaultBurstIntervalMs; }
    public double getDefaultVolatility() { return defaultVolatility; }
}
