package com.fx.lab.core.coalescing;

import com.fx.lab.core.model.BookUpdate;
import com.fx.lab.core.model.CurrencyPair;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Coalescer #2: Post-CLOB coalescing stage for downstream BookUpdate messages before publication.
 */
public final class BookUpdateCoalescer {
    private final String stageName;
    private volatile boolean enabled;

    // Slot buffer for coalescing mode: one slot per CurrencyPair
    private final BookUpdate[] slots;
    private final CoalescingGroupState[] groupStates;
    private final boolean[] slotOccupied;
    private final int[] pendingPairs;
    private int pendingHead = 0;
    private int pendingTail = 0;
    private int pendingCount = 0;

    // FIFO queue for non-coalescing mode
    private final Queue<BookUpdate> fifoQueue;

    // Metrics counters
    private final AtomicLong inputCount = new AtomicLong(0);
    private final AtomicLong outputCount = new AtomicLong(0);
    private final AtomicLong coalescedCount = new AtomicLong(0);

    public BookUpdateCoalescer(String stageName, boolean enabled) {
        this.stageName = stageName;
        this.enabled = enabled;

        int numPairs = CurrencyPair.COUNT;
        this.slots = new BookUpdate[numPairs];
        this.groupStates = new CoalescingGroupState[numPairs];
        this.slotOccupied = new boolean[numPairs];
        this.pendingPairs = new int[numPairs];
        for (int i = 0; i < numPairs; i++) {
            this.slots[i] = new BookUpdate();
            this.groupStates[i] = new CoalescingGroupState();
            this.slotOccupied[i] = false;
        }

        this.fifoQueue = new ArrayDeque<>(1024);
    }

    public synchronized void offer(BookUpdate update) {
        inputCount.incrementAndGet();

        if (!enabled) {
            fifoQueue.offer(update.copy());
            return;
        }

        int pairId = update.getCurrencyPair().getId();
        CoalescingGroupState state = groupStates[pairId];

        if (slotOccupied[pairId]) {
            // Overwrite existing slot
            coalescedCount.incrementAndGet();
            state.record(update.getTimestampNs());
            slots[pairId].copyFrom(update);
            // Retain cumulative coalescing depth & timestamps for stage 2
            slots[pairId].setCoalescingDepth(state.getCount());
            slots[pairId].setOldestCoalescedTimestampNs(state.getOldestTimestampNs());
            slots[pairId].setNewestCoalescedTimestampNs(state.getNewestTimestampNs());
            slots[pairId].setSumCoalescedTimestampNs(state.getSumTimestampNs());
        } else {
            // First update for this pair
            state.reset();
            state.record(update.getTimestampNs());
            slots[pairId].copyFrom(update);
            slots[pairId].setCoalescingDepth(1);
            slots[pairId].setOldestCoalescedTimestampNs(update.getTimestampNs());
            slots[pairId].setNewestCoalescedTimestampNs(update.getTimestampNs());
            slots[pairId].setSumCoalescedTimestampNs(update.getTimestampNs());

            slotOccupied[pairId] = true;
            pendingPairs[pendingTail] = pairId;
            pendingTail = (pendingTail + 1) % CurrencyPair.COUNT;
            pendingCount++;
        }
    }

    public synchronized BookUpdate poll() {
        if (!enabled) {
            BookUpdate update = fifoQueue.poll();
            if (update != null) {
                outputCount.incrementAndGet();
            }
            return update;
        }

        if (pendingCount == 0) {
            return null;
        }

        int pairId = pendingPairs[pendingHead];
        pendingHead = (pendingHead + 1) % CurrencyPair.COUNT;
        pendingCount--;
        slotOccupied[pairId] = false;

        BookUpdate result = slots[pairId].copy();
        groupStates[pairId].reset();
        outputCount.incrementAndGet();
        return result;
    }

    public synchronized boolean isEmpty() {
        return enabled ? (pendingCount == 0) : fifoQueue.isEmpty();
    }

    public synchronized int getQueueDepth() {
        return enabled ? pendingCount : fifoQueue.size();
    }

    public synchronized void reset() {
        inputCount.set(0);
        outputCount.set(0);
        coalescedCount.set(0);
        pendingHead = 0;
        pendingTail = 0;
        pendingCount = 0;
        for (int i = 0; i < CurrencyPair.COUNT; i++) {
            slotOccupied[i] = false;
            groupStates[i].reset();
        }
        fifoQueue.clear();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getStageName() {
        return stageName;
    }

    public long getInputCount() {
        return inputCount.get();
    }

    public long getOutputCount() {
        return outputCount.get();
    }

    public long getCoalescedCount() {
        return coalescedCount.get();
    }

    public double getCoalescingRatio() {
        long in = inputCount.get();
        return in > 0 ? (double) coalescedCount.get() / in : 0.0;
    }

    public synchronized int getDepth(CurrencyPair pair) {
        int pairId = pair.getId();
        return slotOccupied[pairId] ? groupStates[pairId].getCount() : 0;
    }
}
