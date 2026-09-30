package com.fx.lab.core.generator;

import com.fx.lab.core.model.CurrencyPair;
import com.fx.lab.core.model.MarketDataUpdate;
import com.fx.lab.core.pipeline.TradingPipeline;

import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

/**
 * Deterministic, configurable FX market data generator.
 */
public final class MarketDataGenerator implements AutoCloseable {
    private final GeneratorConfig config;
    private final Random random;
    private final AtomicLong sequenceGenerator = new AtomicLong(0);

    // Current price levels per pair
    private final double[] currentMidPrices;
    private final double[] basePrices;

    // Running state
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread generatorThread;
    private TradingPipeline targetPipeline;

    public MarketDataGenerator(GeneratorConfig config) {
        this.config = config != null ? config : new GeneratorConfig();
        this.random = new Random(this.config.getRandomSeed());

        int numPairs = CurrencyPair.COUNT;
        this.currentMidPrices = new double[numPairs];
        this.basePrices = new double[numPairs];

        for (int i = 0; i < numPairs; i++) {
            CurrencyPair pair = CurrencyPair.fromId(i);
            this.basePrices[i] = pair.getBasePrice();
            this.currentMidPrices[i] = pair.getBasePrice();
        }
    }

    /**
     * Generate the next deterministic update in-place without heap allocation.
     */
    public synchronized void nextUpdate(MarketDataUpdate outUpdate) {
        long seq = sequenceGenerator.incrementAndGet();
        long nowNs = System.nanoTime();

        int numActivePairs = Math.min(config.getNumberOfCurrencyPairs(), CurrencyPair.COUNT);
        int pairIndex = random.nextInt(numActivePairs);
        CurrencyPair pair = CurrencyPair.fromId(pairIndex);

        // Mean-reversion random walk
        double base = basePrices[pairIndex];
        double currentMid = currentMidPrices[pairIndex];
        double meanReversion = 0.05 * (base - currentMid);
        double shock = (random.nextGaussian()) * config.getVolatility() * base;
        double newMid = currentMid + meanReversion + shock;
        currentMidPrices[pairIndex] = newMid;

        double halfSpread = (pair.getTickSize() * (1.0 + random.nextInt(3))) * 0.5;
        double bid = Math.round((newMid - halfSpread) * 100000.0) / 100000.0;
        double ask = Math.round((newMid + halfSpread) * 100000.0) / 100000.0;
        double bidSize = 100_000.0 * (1 + random.nextInt(20));
        double askSize = 100_000.0 * (1 + random.nextInt(20));

        outUpdate.set(seq, nowNs, pair, bid, ask, bidSize, askSize);
    }

    public synchronized void start(TradingPipeline pipeline) {
        this.targetPipeline = pipeline;
        if (running.compareAndSet(false, true)) {
            generatorThread = new Thread(this::runGeneratorLoop, "market-data-generator");
            generatorThread.setDaemon(true);
            generatorThread.start();
        }
    }

    public synchronized void stop() {
        if (running.compareAndSet(true, false)) {
            if (generatorThread != null) {
                generatorThread.interrupt();
                try {
                    generatorThread.join(2000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                generatorThread = null;
            }
        }
    }

    private void runGeneratorLoop() {
        MarketDataUpdate tempUpdate = new MarketDataUpdate();
        long lastBurstTime = System.currentTimeMillis();

        while (running.get()) {
            int targetRate = config.getUpdatesPerSecond();
            long intervalNanos = targetRate > 0 ? (1_000_000_000L / targetRate) : 1_000_000L;

            // Check if burst condition triggered
            long nowMs = System.currentTimeMillis();
            boolean inBurst = config.getBurstIntervalMs() > 0
                    && (nowMs - lastBurstTime) >= config.getBurstIntervalMs();

            if (inBurst) {
                lastBurstTime = nowMs;
                int burst = config.getBurstSize();
                for (int b = 0; b < burst && running.get(); b++) {
                    nextUpdate(tempUpdate);
                    if (targetPipeline != null) {
                        targetPipeline.onMarketData(tempUpdate.copy());
                    }
                }
            } else {
                nextUpdate(tempUpdate);
                if (targetPipeline != null) {
                    targetPipeline.onMarketData(tempUpdate.copy());
                }
            }

            // Pacing
            if (intervalNanos > 100_000L) {
                LockSupport.parkNanos(intervalNanos);
            } else {
                // Short wait / spin
                long spinUntil = System.nanoTime() + intervalNanos;
                while (System.nanoTime() < spinUntil) {
                    Thread.onSpinWait();
                }
            }
        }
    }

    public synchronized void reset() {
        stop();
        sequenceGenerator.set(0);
        for (int i = 0; i < CurrencyPair.COUNT; i++) {
            currentMidPrices[i] = basePrices[i];
        }
    }

    public GeneratorConfig getConfig() {
        return config;
    }

    public boolean isRunning() {
        return running.get();
    }

    public long getCurrentSequenceId() {
        return sequenceGenerator.get();
    }

    @Override
    public void close() {
        stop();
    }
}
