package com.fx.lab.core.pipeline;

import com.fx.lab.core.metrics.LatencyMetricsCollector;
import com.fx.lab.core.model.MarketDataUpdate;
import com.fx.lab.core.recording.BinaryStreamRecorder;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Validates sequence IDs, records raw streams if recording is active, and forwards to Aggregator.
 */
public final class FeedHandler {
    private final PriceAggregator nextStage;
    private final LatencyMetricsCollector metricsCollector;
    private volatile BinaryStreamRecorder recorder;
    private final AtomicLong lastSequenceId = new AtomicLong(-1);

    public FeedHandler(PriceAggregator nextStage, LatencyMetricsCollector metricsCollector) {
        this.nextStage = nextStage;
        this.metricsCollector = metricsCollector;
    }

    public void setRecorder(BinaryStreamRecorder recorder) {
        this.recorder = recorder;
    }

    public void onMarketData(MarketDataUpdate update) {
        metricsCollector.recordInput();

        // Optional recording
        BinaryStreamRecorder activeRecorder = this.recorder;
        if (activeRecorder != null && activeRecorder.isRecording()) {
            activeRecorder.record(update);
        }

        lastSequenceId.set(update.getSequenceId());
        nextStage.onMarketData(update);
    }

    public long getLastSequenceId() {
        return lastSequenceId.get();
    }

    public void reset() {
        lastSequenceId.set(-1);
    }
}
