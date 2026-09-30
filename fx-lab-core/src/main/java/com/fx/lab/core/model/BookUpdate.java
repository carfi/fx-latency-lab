package com.fx.lab.core.model;

/**
 * Downstream order book state emitted by the CLOB.
 */
public final class BookUpdate {
    private long sequenceId;
    private long sourceSequenceId;
    private long timestampNs;
    private long originTimestampNs;
    private CurrencyPair currencyPair;

    private double bestBid;
    private double bestAsk;
    private double bestBidSize;
    private double bestAskSize;
    private double spread;
    private double mid;

    // Coalescing metadata (accumulated at Coalescer #2)
    private int coalescingDepth = 1;
    private long oldestCoalescedTimestampNs;
    private long newestCoalescedTimestampNs;
    private long sumCoalescedTimestampNs;

    // Pipeline traversal metadata
    private int stage1CoalescingDepth = 1;
    private long stage1OldestTimestampNs;

    public BookUpdate() {
        this.currencyPair = CurrencyPair.EUR_USD;
    }

    public void set(long sequenceId, long sourceSequenceId, long timestampNs, long originTimestampNs,
                    CurrencyPair currencyPair, double bestBid, double bestAsk,
                    double bestBidSize, double bestAskSize) {
        this.sequenceId = sequenceId;
        this.sourceSequenceId = sourceSequenceId;
        this.timestampNs = timestampNs;
        this.originTimestampNs = originTimestampNs;
        this.currencyPair = currencyPair;
        this.bestBid = bestBid;
        this.bestAsk = bestAsk;
        this.bestBidSize = bestBidSize;
        this.bestAskSize = bestAskSize;
        this.spread = bestAsk - bestBid;
        this.mid = (bestBid + bestAsk) * 0.5;
        this.coalescingDepth = 1;
        this.oldestCoalescedTimestampNs = timestampNs;
        this.newestCoalescedTimestampNs = timestampNs;
        this.sumCoalescedTimestampNs = timestampNs;
        this.stage1CoalescingDepth = 1;
        this.stage1OldestTimestampNs = originTimestampNs;
    }

    public void copyFrom(BookUpdate other) {
        this.sequenceId = other.sequenceId;
        this.sourceSequenceId = other.sourceSequenceId;
        this.timestampNs = other.timestampNs;
        this.originTimestampNs = other.originTimestampNs;
        this.currencyPair = other.currencyPair;
        this.bestBid = other.bestBid;
        this.bestAsk = other.bestAsk;
        this.bestBidSize = other.bestBidSize;
        this.bestAskSize = other.bestAskSize;
        this.spread = other.spread;
        this.mid = other.mid;
        this.coalescingDepth = other.coalescingDepth;
        this.oldestCoalescedTimestampNs = other.oldestCoalescedTimestampNs;
        this.newestCoalescedTimestampNs = other.newestCoalescedTimestampNs;
        this.sumCoalescedTimestampNs = other.sumCoalescedTimestampNs;
        this.stage1CoalescingDepth = other.stage1CoalescingDepth;
        this.stage1OldestTimestampNs = other.stage1OldestTimestampNs;
    }

    public BookUpdate copy() {
        BookUpdate copy = new BookUpdate();
        copy.copyFrom(this);
        return copy;
    }

    public long getSequenceId() { return sequenceId; }
    public void setSequenceId(long sequenceId) { this.sequenceId = sequenceId; }

    public long getSourceSequenceId() { return sourceSequenceId; }
    public void setSourceSequenceId(long sourceSequenceId) { this.sourceSequenceId = sourceSequenceId; }

    public long getTimestampNs() { return timestampNs; }
    public void setTimestampNs(long timestampNs) { this.timestampNs = timestampNs; }

    public long getOriginTimestampNs() { return originTimestampNs; }
    public void setOriginTimestampNs(long originTimestampNs) { this.originTimestampNs = originTimestampNs; }

    public CurrencyPair getCurrencyPair() { return currencyPair; }
    public void setCurrencyPair(CurrencyPair currencyPair) { this.currencyPair = currencyPair; }

    public double getBestBid() { return bestBid; }
    public void setBestBid(double bestBid) { this.bestBid = bestBid; }

    public double getBestAsk() { return bestAsk; }
    public void setBestAsk(double bestAsk) { this.bestAsk = bestAsk; }

    public double getBestBidSize() { return bestBidSize; }
    public void setBestBidSize(double bestBidSize) { this.bestBidSize = bestBidSize; }

    public double getBestAskSize() { return bestAskSize; }
    public void setBestAskSize(double bestAskSize) { this.bestAskSize = bestAskSize; }

    public double getSpread() { return spread; }
    public void setSpread(double spread) { this.spread = spread; }

    public double getMid() { return mid; }
    public void setMid(double mid) { this.mid = mid; }

    public int getCoalescingDepth() { return coalescingDepth; }
    public void setCoalescingDepth(int coalescingDepth) { this.coalescingDepth = coalescingDepth; }

    public long getOldestCoalescedTimestampNs() { return oldestCoalescedTimestampNs; }
    public void setOldestCoalescedTimestampNs(long oldestCoalescedTimestampNs) { this.oldestCoalescedTimestampNs = oldestCoalescedTimestampNs; }

    public long getNewestCoalescedTimestampNs() { return newestCoalescedTimestampNs; }
    public void setNewestCoalescedTimestampNs(long newestCoalescedTimestampNs) { this.newestCoalescedTimestampNs = newestCoalescedTimestampNs; }

    public long getSumCoalescedTimestampNs() { return sumCoalescedTimestampNs; }
    public void setSumCoalescedTimestampNs(long sumCoalescedTimestampNs) { this.sumCoalescedTimestampNs = sumCoalescedTimestampNs; }

    public int getStage1CoalescingDepth() { return stage1CoalescingDepth; }
    public void setStage1CoalescingDepth(int stage1CoalescingDepth) { this.stage1CoalescingDepth = stage1CoalescingDepth; }

    public long getStage1OldestTimestampNs() { return stage1OldestTimestampNs; }
    public void setStage1OldestTimestampNs(long stage1OldestTimestampNs) { this.stage1OldestTimestampNs = stage1OldestTimestampNs; }

    public long getAverageCoalescedTimestampNs() {
        return coalescingDepth > 0 ? (sumCoalescedTimestampNs / coalescingDepth) : timestampNs;
    }
}
