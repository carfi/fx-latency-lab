package com.fx.lab.core.pipeline;

import com.fx.lab.core.estimator.OnlineLatencyEstimator;
import com.fx.lab.core.metrics.LatencyMetricsCollector;
import com.fx.lab.core.metrics.LatencySnapshot;
import com.fx.lab.core.model.CurrencyPair;
import com.fx.lab.core.model.MarketDataUpdate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class TradingPipelineTest {
    private TradingPipeline pipeline;
    private final AtomicInteger publishedEvents = new AtomicInteger(0);

    @BeforeEach
    void setUp() {
        pipeline = new TradingPipeline(
                true,
                true,
                0L,
                new OnlineLatencyEstimator(),
                new LatencyMetricsCollector()
        );
        pipeline.getPublisher().addListener(event -> publishedEvents.incrementAndGet());
    }

    @AfterEach
    void tearDown() {
        pipeline.close();
    }

    @Test
    void testSynchronousSteppingPipeline() {
        long now = System.nanoTime();
        MarketDataUpdate u1 = new MarketDataUpdate(1, now, CurrencyPair.EUR_USD, 1.0850, 1.0852, 100, 100);
        MarketDataUpdate u2 = new MarketDataUpdate(2, now + 1000, CurrencyPair.EUR_USD, 1.0851, 1.0853, 100, 100);

        pipeline.onMarketData(u1);
        pipeline.onMarketData(u2);

        // Step 1: Process next event through pipeline (Coalescer 1 -> CLOB -> Coalescer 2 -> Publisher)
        boolean didWork1 = pipeline.processNext();
        assertThat(didWork1).isTrue();
        assertThat(publishedEvents.get()).isEqualTo(1);

        // Step 2: Now pipeline is empty
        boolean didWork2 = pipeline.processNext();
        assertThat(didWork2).isFalse();

        LatencySnapshot snapshot = pipeline.getMetricsSnapshot();
        assertThat(snapshot.coalescedMessages()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void testLiveAsyncPipelineUnderLoad() throws InterruptedException {
        pipeline.start();

        long now = System.nanoTime();
        for (int i = 1; i <= 200; i++) {
            CurrencyPair pair = CurrencyPair.fromId(i % CurrencyPair.COUNT);
            MarketDataUpdate update = new MarketDataUpdate(
                    i, now + i * 100, pair,
                    pair.getBasePrice(), pair.getBasePrice() + 0.0002, 100, 100
            );
            pipeline.onMarketData(update);
        }

        Thread.sleep(200); // Allow async worker to process

        assertThat(publishedEvents.get()).isGreaterThan(0);
        LatencySnapshot snapshot = pipeline.getMetricsSnapshot();
        assertThat(snapshot.coalescingRatio()).isGreaterThanOrEqualTo(0.0);
    }
}
