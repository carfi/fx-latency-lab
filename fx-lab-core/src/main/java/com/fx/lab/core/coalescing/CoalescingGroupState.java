package com.fx.lab.core.coalescing;

/**
 * Low-overhead primitive tracking state for coalescing groups.
 * Completely zero-allocation in the hot path.
 */
public final class CoalescingGroupState {
    private int count;
    private long oldestTimestampNs;
    private long newestTimestampNs;
    private long sumTimestampNs;

    public CoalescingGroupState() {
        reset();
    }

    public void reset() {
        this.count = 0;
        this.oldestTimestampNs = 0L;
        this.newestTimestampNs = 0L;
        this.sumTimestampNs = 0L;
    }

    public void record(long timestampNs) {
        if (count == 0) {
            this.oldestTimestampNs = timestampNs;
            this.newestTimestampNs = timestampNs;
            this.sumTimestampNs = timestampNs;
            this.count = 1;
        } else {
            this.count++;
            if (timestampNs < oldestTimestampNs) {
                this.oldestTimestampNs = timestampNs;
            }
            if (timestampNs > newestTimestampNs) {
                this.newestTimestampNs = timestampNs;
            }
            this.sumTimestampNs += timestampNs;
        }
    }

    public void recordMerged(int additionalCount, long oldestNs, long newestNs, long sumNs) {
        if (additionalCount <= 0) return;
        if (count == 0) {
            this.count = additionalCount;
            this.oldestTimestampNs = oldestNs;
            this.newestTimestampNs = newestNs;
            this.sumTimestampNs = sumNs;
        } else {
            this.count += additionalCount;
            if (oldestNs < oldestTimestampNs) {
                this.oldestTimestampNs = oldestNs;
            }
            if (newestNs > newestTimestampNs) {
                this.newestTimestampNs = newestNs;
            }
            this.sumTimestampNs += sumNs;
        }
    }

    public int getCount() {
        return count;
    }

    public long getOldestTimestampNs() {
        return oldestTimestampNs;
    }

    public long getNewestTimestampNs() {
        return newestTimestampNs;
    }

    public long getSumTimestampNs() {
        return sumTimestampNs;
    }

    public long getAverageTimestampNs() {
        return count > 0 ? (sumTimestampNs / count) : 0L;
    }
}
