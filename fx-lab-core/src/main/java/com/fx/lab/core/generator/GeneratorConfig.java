package com.fx.lab.core.generator;

public class GeneratorConfig {
    private TrafficProfile profile = TrafficProfile.NORMAL;
    private int numberOfCurrencyPairs = 6;
    private int updatesPerSecond = 5_000;
    private int burstSize = 1;
    private int burstIntervalMs = 0;
    private double volatility = 0.0001;
    private double correlation = 0.4;
    private long randomSeed = 42L;

    public GeneratorConfig() {}

    public static GeneratorConfig fromProfile(TrafficProfile profile) {
        GeneratorConfig config = new GeneratorConfig();
        config.setProfile(profile);
        config.setUpdatesPerSecond(profile.getDefaultRatePerSec());
        config.setBurstSize(profile.getDefaultBurstSize());
        config.setBurstIntervalMs(profile.getDefaultBurstIntervalMs());
        config.setVolatility(profile.getDefaultVolatility());
        return config;
    }

    public TrafficProfile getProfile() { return profile; }
    public void setProfile(TrafficProfile profile) { this.profile = profile; }

    public int getNumberOfCurrencyPairs() { return numberOfCurrencyPairs; }
    public void setNumberOfCurrencyPairs(int numberOfCurrencyPairs) { this.numberOfCurrencyPairs = numberOfCurrencyPairs; }

    public int getUpdatesPerSecond() { return updatesPerSecond; }
    public void setUpdatesPerSecond(int updatesPerSecond) { this.updatesPerSecond = updatesPerSecond; }

    public int getBurstSize() { return burstSize; }
    public void setBurstSize(int burstSize) { this.burstSize = burstSize; }

    public int getBurstIntervalMs() { return burstIntervalMs; }
    public void setBurstIntervalMs(int burstIntervalMs) { this.burstIntervalMs = burstIntervalMs; }

    public double getVolatility() { return volatility; }
    public void setVolatility(double volatility) { this.volatility = volatility; }

    public double getCorrelation() { return correlation; }
    public void setCorrelation(double correlation) { this.correlation = correlation; }

    public long getRandomSeed() { return randomSeed; }
    public void setRandomSeed(long randomSeed) { this.randomSeed = randomSeed; }
}
