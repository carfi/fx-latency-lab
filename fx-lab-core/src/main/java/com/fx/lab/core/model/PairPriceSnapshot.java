package com.fx.lab.core.model;

/**
 * Snapshot of live prices and book top-of-book for a currency pair,
 * along with active coalescing depth at each stage.
 */
public record PairPriceSnapshot(
        int pairId,
        String symbol,
        double bestBid,
        double bestAsk,
        double spread,
        double mid,
        double bestBidSize,
        double bestAskSize,
        int precision,
        int stage1Depth,
        int stage2Depth
) {
}
