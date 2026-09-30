package com.fx.lab.core.metrics;

import com.fx.lab.core.estimator.PressureStatus;
import org.HdrHistogram.Histogram;

import java.util.concurrent.atomic.AtomicLong;

/**
 * High-performance metrics collector wrapping HdrHistogram instances.
 * Thread-safe recording and lock-free snapshot generation.
 */
public final class LatencyMetricsCollector {
    // Max 100 seconds in nanoseconds with 3 significant digits
    private static final long MAX_LATENCY_NANOS = 100_000_000_000L;

    private final Histogram actualLatencyHdr = new Histogram(MAX_LATENCY_NANOS, 3);
    private final Histogram expectedHiddenHdr = new Histogram(MAX_LATENCY_NANOS, 3);
    private final Histogram oldestHiddenHdr = new Histogram(MAX_LATENCY_NANOS, 3);
    private final Histogram newestHiddenHdr = new Histogram(MAX_LATENCY_NANOS, 3);

    // Throughput cumulative counters
    private final AtomicLong totalInput = new AtomicLong(0);
    private final AtomicLong totalProcessed = new AtomicLong(0);
    private final AtomicLong totalPublished = new AtomicLong(0);
    private final AtomicLong totalCoalesced = new AtomicLong(0);
    private final AtomicLong coalescingGroups = new AtomicLong(0);
    private final AtomicLong cumulativeDepth = new AtomicLong(0);

    // Rate calculation state
    private long lastSnapshotTimeNs = System.nanoTime();
    private long lastInputCount = 0;
    private long lastProcessedCount = 0;
    private long lastPublishedCount = 0;
    private long lastGroupsCount = 0;

    // Moving average of estimated latency and pressure
    private volatile double currentExpectedHiddenLatencyNs = 0.0;
    private volatile double currentOldestHiddenLatencyNs = 0.0;
    private volatile double currentNewestHiddenLatencyNs = 0.0;
    private volatile double currentPressure = 0.0;
    private volatile PressureStatus currentPressureStatus = PressureStatus.HEALTHY;

    public void recordInput() {
        totalInput.incrementAndGet();
    }

    public void recordProcessed() {
        totalProcessed.incrementAndGet();
    }

    public synchronized void recordPublication(long actualLatencyNs,
                                               long expectedHiddenNs,
                                               long oldestHiddenNs,
                                               long newestHiddenNs,
                                               int depth,
                                               double pressure,
                                               PressureStatus status) {
        totalPublished.incrementAndGet();
        coalescingGroups.incrementAndGet();
        cumulativeDepth.addAndGet(depth);
        if (depth > 1) {
            totalCoalesced.addAndGet(depth - 1);
        }

        // Bound to Hdr range
        actualLatencyHdr.recordValue(Math.min(MAX_LATENCY_NANOS - 1, Math.max(1L, actualLatencyNs)));
        expectedHiddenHdr.recordValue(Math.min(MAX_LATENCY_NANOS - 1, Math.max(1L, expectedHiddenNs)));
        oldestHiddenHdr.recordValue(Math.min(MAX_LATENCY_NANOS - 1, Math.max(1L, oldestHiddenNs)));
        newestHiddenHdr.recordValue(Math.min(MAX_LATENCY_NANOS - 1, Math.max(1L, newestHiddenNs)));

        this.currentExpectedHiddenLatencyNs = expectedHiddenNs;
        this.currentOldestHiddenLatencyNs = oldestHiddenNs;
        this.currentNewestHiddenLatencyNs = newestHiddenNs;
        this.currentPressure = pressure;
        this.currentPressureStatus = status;
    }

    public synchronized LatencySnapshot getSnapshot(int queueDepth,
                                                     int virtualBacklog,
                                                     double stage1Ratio,
                                                     double stage2Ratio,
                                                     long stage1Coalesced,
                                                     long stage2Coalesced) {
        long nowNs = System.nanoTime();
        double elapsedSec = Math.max(0.001, (nowNs - lastSnapshotTimeNs) / 1_000_000_000.0);

        long currentIn = totalInput.get();
        long currentProc = totalProcessed.get();
        long currentPub = totalPublished.get();
        long currentGrp = coalescingGroups.get();

        double inRate = (currentIn - lastInputCount) / elapsedSec;
        double procRate = (currentProc - lastProcessedCount) / elapsedSec;
        double pubRate = (currentPub - lastPublishedCount) / elapsedSec;
        double grpRate = (currentGrp - lastGroupsCount) / elapsedSec;

        lastSnapshotTimeNs = nowNs;
        lastInputCount = currentIn;
        lastProcessedCount = currentProc;
        lastPublishedCount = currentPub;
        lastGroupsCount = currentGrp;

        long coalesced = totalCoalesced.get();
        double ratio = currentIn > 0 ? (double) coalesced / currentIn : 0.0;
        double avgDepth = currentGrp > 0 ? (double) cumulativeDepth.get() / currentGrp : 1.0;

        // Convert Hdr nanoseconds to microseconds
        double actP50 = actualLatencyHdr.getValueAtPercentile(50.0) / 1000.0;
        double actP95 = actualLatencyHdr.getValueAtPercentile(95.0) / 1000.0;
        double actP99 = actualLatencyHdr.getValueAtPercentile(99.0) / 1000.0;
        double actP999 = actualLatencyHdr.getValueAtPercentile(99.9) / 1000.0;

        double expHidUs = currentExpectedHiddenLatencyNs / 1000.0;
        double oldHidUs = currentOldestHiddenLatencyNs / 1000.0;
        double newHidUs = currentNewestHiddenLatencyNs / 1000.0;

        double cfP50 = expectedHiddenHdr.getValueAtPercentile(50.0) / 1000.0;
        double cfP95 = expectedHiddenHdr.getValueAtPercentile(95.0) / 1000.0;
        double cfP99 = expectedHiddenHdr.getValueAtPercentile(99.0) / 1000.0;

        double utilization = Math.min(1.0, procRate > 0 ? (pubRate / Math.max(1.0, procRate)) : 0.0);

        return new LatencySnapshot(
                System.currentTimeMillis(),
                inRate,
                procRate,
                pubRate,
                coalesced,
                ratio,
                avgDepth,
                grpRate,
                actP50,
                actP95,
                actP99,
                actP999,
                expHidUs,
                oldHidUs,
                newHidUs,
                cfP50,
                cfP95,
                cfP99,
                queueDepth,
                virtualBacklog,
                utilization,
                currentPressure,
                currentPressureStatus,
                stage1Ratio,
                stage2Ratio,
                stage1Coalesced,
                stage2Coalesced
        );
    }

    public synchronized void reset() {
        actualLatencyHdr.reset();
        expectedHiddenHdr.reset();
        oldestHiddenHdr.reset();
        newestHiddenHdr.reset();
        totalInput.set(0);
        totalProcessed.set(0);
        totalPublished.set(0);
        totalCoalesced.set(0);
        coalescingGroups.set(0);
        cumulativeDepth.set(0);
        lastSnapshotTimeNs = System.nanoTime();
        lastInputCount = 0;
        lastProcessedCount = 0;
        lastPublishedCount = 0;
        lastGroupsCount = 0;
        currentExpectedHiddenLatencyNs = 0.0;
        currentOldestHiddenLatencyNs = 0.0;
        currentNewestHiddenLatencyNs = 0.0;
        currentPressure = 0.0;
        currentPressureStatus = PressureStatus.HEALTHY;
    }
}
