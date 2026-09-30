package com.fx.lab.core.pipeline;

import com.fx.lab.core.coalescing.MarketDataCoalescer;
import com.fx.lab.core.model.MarketDataUpdate;

/**
 * Normalizes quotes and feeds Coalescer #1.
 */
public final class PriceAggregator {
    private final MarketDataCoalescer coalescerStage1;

    public PriceAggregator(MarketDataCoalescer coalescerStage1) {
        this.coalescerStage1 = coalescerStage1;
    }

    public void onMarketData(MarketDataUpdate update) {
        // Enforce spread sanity: ask must be >= bid
        if (update.getAsk() < update.getBid()) {
            double temp = update.getBid();
            update.setBid(update.getAsk());
            update.setAsk(temp);
        }

        // Pass to Coalescer #1
        coalescerStage1.offer(update);
    }
}
