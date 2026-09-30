package com.fx.lab.core.pipeline;

import com.fx.lab.core.clob.ClobOrderBook;
import com.fx.lab.core.coalescing.BookUpdateCoalescer;
import com.fx.lab.core.coalescing.MarketDataCoalescer;
import com.fx.lab.core.estimator.OnlineLatencyEstimator;
import com.fx.lab.core.metrics.LatencyMetricsCollector;
import com.fx.lab.core.metrics.LatencySnapshot;
import com.fx.lab.core.model.BookUpdate;
import com.fx.lab.core.model.CurrencyPair;
import com.fx.lab.core.model.MarketDataUpdate;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Complete trading pipeline wiring:
 * MarketData -> FeedHandler -> Aggregator -> Coalescer #1 -> CLOB -> Coalescer #2 -> Publisher
 */
public final class TradingPipeline implements AutoCloseable {
    private final MarketDataCoalescer coalescer1;
    private final BookUpdateCoalescer coalescer2;
    private final ClobOrderBook[] clobBooks;
    private final FeedHandler feedHandler;
    private final PriceAggregator priceAggregator;
    private final Publisher publisher;
    private final OnlineLatencyEstimator estimator;
    private final LatencyMetricsCollector metricsCollector;

    private final AtomicLong clobSeqGen = new AtomicLong(0);
    private final BookUpdate reusableBookUpdate = new BookUpdate();

    // Background worker for async live mode
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread workerThread;

    public TradingPipeline(boolean coalescing1Enabled,
                           boolean coalescing2Enabled,
                           long clobWorkNanos,
                           OnlineLatencyEstimator estimator,
                           LatencyMetricsCollector metricsCollector) {
        this.estimator = estimator != null ? estimator : new OnlineLatencyEstimator();
        this.metricsCollector = metricsCollector != null ? metricsCollector : new LatencyMetricsCollector();

        this.coalescer1 = new MarketDataCoalescer("Coalescer-1-PreCLOB", coalescing1Enabled);
        this.coalescer2 = new BookUpdateCoalescer("Coalescer-2-PrePublish", coalescing2Enabled);

        this.clobBooks = new ClobOrderBook[CurrencyPair.COUNT];
        for (int i = 0; i < CurrencyPair.COUNT; i++) {
            this.clobBooks[i] = new ClobOrderBook(CurrencyPair.fromId(i), clobSeqGen, clobWorkNanos);
        }

        this.priceAggregator = new PriceAggregator(this.coalescer1);
        this.feedHandler = new FeedHandler(this.priceAggregator, this.metricsCollector);
        this.publisher = new Publisher(this.estimator, this.metricsCollector);
    }

    /**
     * Start background async processing worker for live simulation.
     */
    public synchronized void start() {
        if (running.compareAndSet(false, true)) {
            workerThread = new Thread(this::runWorkerLoop, "trading-pipeline-worker");
            workerThread.setDaemon(true);
            workerThread.start();
        }
    }

    /**
     * Stop background worker.
     */
    public synchronized void stop() {
        if (running.compareAndSet(true, false)) {
            if (workerThread != null) {
                workerThread.interrupt();
                try {
                    workerThread.join(2000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                workerThread = null;
            }
        }
    }

    private void runWorkerLoop() {
        while (running.get()) {
            boolean didWork = processNext();
            if (!didWork) {
                Thread.onSpinWait();
            }
        }
    }

    /**
     * Step single event through the pipeline.
     * Useful for deterministic synchronous offline replay and unit tests.
     */
    public synchronized boolean processNext() {
        boolean workDone = false;

        // 1. Drain Coalescer #1 to CLOB and offer to Coalescer #2
        MarketDataUpdate marketUpdate = coalescer1.poll();
        if (marketUpdate != null) {
            metricsCollector.recordProcessed();
            ClobOrderBook book = clobBooks[marketUpdate.getCurrencyPair().getId()];
            book.process(marketUpdate, reusableBookUpdate);
            coalescer2.offer(reusableBookUpdate);
            workDone = true;
        }

        // 2. Drain Coalescer #2 to Publisher
        BookUpdate bookUpdate = coalescer2.poll();
        if (bookUpdate != null) {
            publisher.publish(bookUpdate);
            workDone = true;
        }

        return workDone;
    }

    /**
     * Drain all pending messages until pipeline queues are empty.
     */
    public synchronized void drainAll() {
        while (!coalescer1.isEmpty() || !coalescer2.isEmpty()) {
            processNext();
        }
    }

    public void onMarketData(MarketDataUpdate update) {
        feedHandler.onMarketData(update);
    }

    public synchronized void reset() {
        stop();
        coalescer1.reset();
        coalescer2.reset();
        for (ClobOrderBook book : clobBooks) {
            book.reset();
        }
        feedHandler.reset();
        metricsCollector.reset();
    }

    public LatencySnapshot getMetricsSnapshot() {
        int q1 = coalescer1.getQueueDepth();
        int q2 = coalescer2.getQueueDepth();
        int totalQueue = q1 + q2;
        int virtualBacklog = (int) (coalescer1.getCoalescedCount() + coalescer2.getCoalescedCount());

        return metricsCollector.getSnapshot(
                totalQueue,
                virtualBacklog,
                coalescer1.getCoalescingRatio(),
                coalescer2.getCoalescingRatio(),
                coalescer1.getCoalescedCount(),
                coalescer2.getCoalescedCount()
        );
    }

    public void setCoalescing1Enabled(boolean enabled) {
        coalescer1.setEnabled(enabled);
    }

    public void setCoalescing2Enabled(boolean enabled) {
        coalescer2.setEnabled(enabled);
    }

    public boolean isCoalescing1Enabled() {
        return coalescer1.isEnabled();
    }

    public boolean isCoalescing2Enabled() {
        return coalescer2.isEnabled();
    }

    public FeedHandler getFeedHandler() { return feedHandler; }
    public MarketDataCoalescer getCoalescer1() { return coalescer1; }
    public BookUpdateCoalescer getCoalescer2() { return coalescer2; }
    public Publisher getPublisher() { return publisher; }
    public OnlineLatencyEstimator getEstimator() { return estimator; }
    public LatencyMetricsCollector getMetricsCollector() { return metricsCollector; }
    public ClobOrderBook getClobBook(CurrencyPair pair) { return clobBooks[pair.getId()]; }
    public boolean isRunning() { return running.get(); }

    @Override
    public void close() {
        stop();
    }
}
