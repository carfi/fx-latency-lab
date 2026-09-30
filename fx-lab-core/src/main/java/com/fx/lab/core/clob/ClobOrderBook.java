package com.fx.lab.core.clob;

import com.fx.lab.core.model.BookUpdate;
import com.fx.lab.core.model.CurrencyPair;
import com.fx.lab.core.model.MarketDataUpdate;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * FX Central Limit Order Book (CLOB) implementation.
 * Maintains sorted bid/ask ladders per currency pair.
 */
public final class ClobOrderBook {
    public static final int MAX_LEVELS = 10;

    private final CurrencyPair currencyPair;
    private final AtomicLong clobSequenceGenerator;
    private final long processingWorkNanos;

    // Price levels: bids descending, asks ascending
    private final TreeMap<Double, Double> bidBook = new TreeMap<>(Collections.reverseOrder());
    private final TreeMap<Double, Double> askBook = new TreeMap<>();

    private double bestBid = 0.0;
    private double bestAsk = 0.0;
    private double bestBidSize = 0.0;
    private double bestAskSize = 0.0;

    public ClobOrderBook(CurrencyPair currencyPair, AtomicLong clobSequenceGenerator, long processingWorkNanos) {
        this.currencyPair = currencyPair;
        this.clobSequenceGenerator = clobSequenceGenerator;
        this.processingWorkNanos = processingWorkNanos;

        // Seed initial book around base price
        initializeBook();
    }

    private void initializeBook() {
        double mid = currencyPair.getBasePrice();
        double tick = currencyPair.getTickSize();
        for (int i = 1; i <= MAX_LEVELS; i++) {
            double bidPrice = Math.round((mid - i * tick) * 100000.0) / 100000.0;
            double askPrice = Math.round((mid + i * tick) * 100000.0) / 100000.0;
            bidBook.put(bidPrice, 1_000_000.0 * (11 - i));
            askBook.put(askPrice, 1_000_000.0 * (11 - i));
        }
        updateBBO();
    }

    /**
     * Process an incoming market data update and generate the updated book state.
     */
    public synchronized void process(MarketDataUpdate update, BookUpdate outBookUpdate) {
        // Simulate realistic CLOB processing load / algorithmic matching work
        if (processingWorkNanos > 0) {
            simulateProcessingWork(processingWorkNanos);
        }

        // Apply bid update
        double bid = update.getBid();
        double bidSize = update.getBidSize();
        if (bidSize > 0) {
            bidBook.put(bid, bidSize);
        } else {
            bidBook.remove(bid);
        }

        // Apply ask update
        double ask = update.getAsk();
        double askSize = update.getAskSize();
        if (askSize > 0) {
            askBook.put(ask, askSize);
        } else {
            askBook.remove(ask);
        }

        // Trim book to MAX_LEVELS
        while (bidBook.size() > MAX_LEVELS) {
            bidBook.pollLastEntry();
        }
        while (askBook.size() > MAX_LEVELS) {
            askBook.pollLastEntry();
        }

        updateBBO();

        long clobSeq = clobSequenceGenerator.incrementAndGet();
        long nowNs = System.nanoTime();

        outBookUpdate.set(
                clobSeq,
                update.getSequenceId(),
                nowNs,
                update.getTimestampNs(),
                currencyPair,
                bestBid,
                bestAsk,
                bestBidSize,
                bestAskSize
        );

        // Pass through stage 1 coalescing metrics to stage 2
        outBookUpdate.setStage1CoalescingDepth(update.getCoalescingDepth());
        outBookUpdate.setStage1OldestTimestampNs(update.getOldestCoalescedTimestampNs());
    }

    private void updateBBO() {
        if (!bidBook.isEmpty()) {
            Map.Entry<Double, Double> topBid = bidBook.firstEntry();
            bestBid = topBid.getKey();
            bestBidSize = topBid.getValue();
        }
        if (!askBook.isEmpty()) {
            Map.Entry<Double, Double> topAsk = askBook.firstEntry();
            bestAsk = topAsk.getKey();
            bestAskSize = topAsk.getValue();
        }
    }

    private void simulateProcessingWork(long nanos) {
        long start = System.nanoTime();
        // Busy spin to avoid thread descheduling while imposing CPU work
        while ((System.nanoTime() - start) < nanos) {
            Thread.onSpinWait();
        }
    }

    public synchronized void reset() {
        bidBook.clear();
        askBook.clear();
        initializeBook();
    }

    public CurrencyPair getCurrencyPair() { return currencyPair; }
    public synchronized double getBestBid() { return bestBid; }
    public synchronized double getBestAsk() { return bestAsk; }
    public synchronized double getBestBidSize() { return bestBidSize; }
    public synchronized double getBestAskSize() { return bestAskSize; }
    public synchronized double getSpread() { return bestAsk - bestBid; }
    public synchronized double getMid() { return (bestBid + bestAsk) * 0.5; }
    public synchronized int getBidDepth() { return bidBook.size(); }
    public synchronized int getAskDepth() { return askBook.size(); }
}
