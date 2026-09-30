package com.fx.lab.core.pipeline;

import com.fx.lab.core.estimator.EstimateResult;
import com.fx.lab.core.estimator.OnlineLatencyEstimator;
import com.fx.lab.core.metrics.LatencyMetricsCollector;
import com.fx.lab.core.model.BookUpdate;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Publishes final BookUpdate messages downstream, triggers the online latency estimator,
 * and records latency percentiles into HdrHistogram.
 */
public final class Publisher {
    private final OnlineLatencyEstimator estimator;
    private final LatencyMetricsCollector metricsCollector;
    private final List<Consumer<PublishedEvent>> listeners = new CopyOnWriteArrayList<>();

    public record PublishedEvent(
            BookUpdate bookUpdate,
            long publishTimestampNs,
            long actualLatencyNs,
            EstimateResult estimateResult
    ) {}

    public Publisher(OnlineLatencyEstimator estimator, LatencyMetricsCollector metricsCollector) {
        this.estimator = estimator;
        this.metricsCollector = metricsCollector;
    }

    public void addListener(Consumer<PublishedEvent> listener) {
        listeners.add(listener);
    }

    public void removeListener(Consumer<PublishedEvent> listener) {
        listeners.remove(listener);
    }

    public void publish(BookUpdate update) {
        long publishTimestampNs = System.nanoTime();
        long actualLatencyNs = Math.max(1L, publishTimestampNs - update.getOriginTimestampNs());

        // Multi-stage combined depth and timestamps:
        // Combined oldest timestamp is the minimum of Stage 1 oldest and Stage 2 oldest
        long combinedOldestNs = Math.min(update.getStage1OldestTimestampNs(), update.getOldestCoalescedTimestampNs());
        long combinedNewestNs = update.getNewestCoalescedTimestampNs();
        int combinedDepth = update.getStage1CoalescingDepth() * update.getCoalescingDepth();
        long combinedAvgNs = (update.getOriginTimestampNs() + update.getAverageCoalescedTimestampNs()) / 2;

        EstimateResult estimate = estimator.evaluate(
                publishTimestampNs,
                combinedOldestNs,
                combinedNewestNs,
                combinedAvgNs,
                combinedDepth
        );

        // Record in HdrHistogram
        metricsCollector.recordPublication(
                actualLatencyNs,
                estimate.expectedHiddenLatencyNs(),
                estimate.oldestHiddenLatencyNs(),
                estimate.newestHiddenLatencyNs(),
                combinedDepth,
                estimate.coalescingPressure(),
                estimate.pressureStatus()
        );

        if (!listeners.isEmpty()) {
            PublishedEvent event = new PublishedEvent(update.copy(), publishTimestampNs, actualLatencyNs, estimate);
            for (Consumer<PublishedEvent> listener : listeners) {
                try {
                    listener.accept(event);
                } catch (Exception ignored) {
                }
            }
        }
    }
}
