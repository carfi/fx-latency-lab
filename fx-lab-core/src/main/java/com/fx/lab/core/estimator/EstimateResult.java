package com.fx.lab.core.estimator;

/**
 * Result record of an online estimation step.
 * Pre-allocated or cleanly returned for metrics recording.
 */
public record EstimateResult(
        long oldestHiddenLatencyNs,
        long newestHiddenLatencyNs,
        long averageHiddenLatencyNs,
        long expectedHiddenLatencyNs,
        int coalescingDepth,
        double coalescingPressure,
        PressureStatus pressureStatus
) {
}
