package com.fx.lab.core.metrics;

import com.fx.lab.core.estimator.PressureStatus;

/**
 * Immutable snapshot of real-time trading pipeline and estimator metrics.
 */
public record LatencySnapshot(
        long timestampMillis,
        // Throughput
        double inputMessagesPerSec,
        double processedMessagesPerSec,
        double publishedMessagesPerSec,
        // Coalescing
        long coalescedMessages,
        double coalescingRatio,
        double coalescingDepthAvg,
        double coalescingGroupsPerSec,
        // Actual Latency (nanoseconds and microseconds)
        double actualLatencyP50Us,
        double actualLatencyP95Us,
        double actualLatencyP99Us,
        double actualLatencyP999Us,
        // Hidden Latency Estimates (microseconds)
        double expectedHiddenLatencyUs,
        double oldestHiddenLatencyUs,
        double newestHiddenLatencyUs,
        double counterfactualEstimatedLatencyP50Us,
        double counterfactualEstimatedLatencyP95Us,
        double counterfactualEstimatedLatencyP99Us,
        // Queue and Pressure
        int queueDepth,
        int virtualBacklog,
        double processingUtilization,
        double coalescingPressure,
        PressureStatus pressureStatus,
        // Stage metrics
        double stage1CoalescingRatio,
        double stage2CoalescingRatio,
        long stage1CoalescedCount,
        long stage2CoalescedCount,
        // Generated pair prices
        java.util.List<com.fx.lab.core.model.PairPriceSnapshot> pairPrices
) {
}
