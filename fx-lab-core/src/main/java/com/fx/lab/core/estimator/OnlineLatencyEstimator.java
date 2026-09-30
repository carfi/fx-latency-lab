package com.fx.lab.core.estimator;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Ultra-low overhead online estimator for hidden/counterfactual latency.
 * Evaluates in sub-microsecond time with zero allocation when using primitive query methods.
 */
public final class OnlineLatencyEstimator {
    private final AtomicReference<CalibrationConfig> configRef;

    public OnlineLatencyEstimator() {
        this(CalibrationConfig.defaultIdentity());
    }

    public OnlineLatencyEstimator(CalibrationConfig config) {
        this.configRef = new AtomicReference<>(config != null ? config : CalibrationConfig.defaultIdentity());
    }

    public void updateCalibration(CalibrationConfig newConfig) {
        if (newConfig != null) {
            this.configRef.set(newConfig);
        }
    }

    public CalibrationConfig getConfig() {
        return configRef.get();
    }

    /**
     * Compute expected hidden latency in nanoseconds without object allocation.
     */
    public long estimateExpectedHiddenLatencyNs(long publishTimestampNs,
                                                long oldestTimestampNs,
                                                long newestTimestampNs,
                                                long averageTimestampNs,
                                                int depth) {
        long avgHidden = publishTimestampNs - averageTimestampNs;
        CalibrationConfig config = configRef.get();

        if ("AVERAGE_IDENTITY".equalsIgnoreCase(config.getModelType())) {
            return Math.max(0L, avgHidden);
        }

        long oldestHidden = publishTimestampNs - oldestTimestampNs;
        long newestHidden = publishTimestampNs - newestTimestampNs;

        double predicted = config.getIntercept()
                + config.getAvgHiddenLatencyWeight() * (double) avgHidden
                + config.getDepthWeight() * (double) depth
                + config.getOldestHiddenLatencyWeight() * (double) oldestHidden
                + config.getNewestHiddenLatencyWeight() * (double) newestHidden
                + config.getDepthSquaredWeight() * (double) (depth * depth);

        return Math.max(0L, Math.round(predicted));
    }

    /**
     * Full evaluation returning an EstimateResult record.
     */
    public EstimateResult evaluate(long publishTimestampNs,
                                  long oldestTimestampNs,
                                  long newestTimestampNs,
                                  long averageTimestampNs,
                                  int depth) {
        long oldestHidden = Math.max(0L, publishTimestampNs - oldestTimestampNs);
        long newestHidden = Math.max(0L, publishTimestampNs - newestTimestampNs);
        long avgHidden = Math.max(0L, publishTimestampNs - averageTimestampNs);

        long expectedHidden = estimateExpectedHiddenLatencyNs(publishTimestampNs, oldestTimestampNs, newestTimestampNs, averageTimestampNs, depth);

        CalibrationConfig config = configRef.get();
        double targetLatency = config.getTargetLatencyNanos() > 0 ? config.getTargetLatencyNanos() : 50_000.0;
        double pressure = (double) expectedHidden / targetLatency;
        PressureStatus status = PressureStatus.fromRatio(pressure);

        return new EstimateResult(oldestHidden, newestHidden, avgHidden, expectedHidden, depth, pressure, status);
    }
}
