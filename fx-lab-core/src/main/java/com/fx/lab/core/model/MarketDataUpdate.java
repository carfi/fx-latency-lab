package com.fx.lab.core.model;

import java.nio.ByteBuffer;

/**
 * Cache-friendly mutable market data update container.
 * Supports zero-allocation reuse and fast binary serialization.
 */
public final class MarketDataUpdate {
    public static final int BINARY_FRAME_SIZE = 64;

    private long sequenceId;
    private long timestampNs;
    private CurrencyPair currencyPair;
    private double bid;
    private double ask;
    private double bidSize;
    private double askSize;

    // In-flight pipeline tracking metadata
    private int coalescingDepth = 1;
    private long oldestCoalescedTimestampNs;
    private long newestCoalescedTimestampNs;
    private long sumCoalescedTimestampNs;

    public MarketDataUpdate() {
        this.currencyPair = CurrencyPair.EUR_USD;
    }

    public MarketDataUpdate(long sequenceId, long timestampNs, CurrencyPair currencyPair,
                            double bid, double ask, double bidSize, double askSize) {
        set(sequenceId, timestampNs, currencyPair, bid, ask, bidSize, askSize);
    }

    public void set(long sequenceId, long timestampNs, CurrencyPair currencyPair,
                    double bid, double ask, double bidSize, double askSize) {
        this.sequenceId = sequenceId;
        this.timestampNs = timestampNs;
        this.currencyPair = currencyPair;
        this.bid = bid;
        this.ask = ask;
        this.bidSize = bidSize;
        this.askSize = askSize;
        this.coalescingDepth = 1;
        this.oldestCoalescedTimestampNs = timestampNs;
        this.newestCoalescedTimestampNs = timestampNs;
        this.sumCoalescedTimestampNs = timestampNs;
    }

    public void copyFrom(MarketDataUpdate other) {
        this.sequenceId = other.sequenceId;
        this.timestampNs = other.timestampNs;
        this.currencyPair = other.currencyPair;
        this.bid = other.bid;
        this.ask = other.ask;
        this.bidSize = other.bidSize;
        this.askSize = other.askSize;
        this.coalescingDepth = other.coalescingDepth;
        this.oldestCoalescedTimestampNs = other.oldestCoalescedTimestampNs;
        this.newestCoalescedTimestampNs = other.newestCoalescedTimestampNs;
        this.sumCoalescedTimestampNs = other.sumCoalescedTimestampNs;
    }

    public MarketDataUpdate copy() {
        MarketDataUpdate copy = new MarketDataUpdate();
        copy.copyFrom(this);
        return copy;
    }

    public void writeTo(ByteBuffer buffer) {
        buffer.putLong(sequenceId);               // 8
        buffer.putLong(timestampNs);              // 8
        buffer.put((byte) currencyPair.getId());  // 1
        buffer.put((byte) 0);                     // 1 (flags)
        buffer.putShort((short) 0);               // 2 (padding)
        buffer.putInt(coalescingDepth);           // 4
        buffer.putDouble(bid);                    // 8
        buffer.putDouble(ask);                    // 8
        buffer.putDouble(bidSize);                // 8
        buffer.putDouble(askSize);                // 8
        buffer.putLong(0L);                       // 8 (reserved padding)
        // Total = 8+8+1+1+2+4+8+8+8+8+8 = 64 bytes
    }

    public static MarketDataUpdate readFrom(ByteBuffer buffer) {
        MarketDataUpdate update = new MarketDataUpdate();
        update.readInto(buffer);
        return update;
    }

    public void readInto(ByteBuffer buffer) {
        this.sequenceId = buffer.getLong();
        this.timestampNs = buffer.getLong();
        int pairId = buffer.get() & 0xFF;
        this.currencyPair = CurrencyPair.fromId(pairId);
        buffer.get(); // skip flags
        buffer.getShort(); // skip padding
        this.coalescingDepth = buffer.getInt();
        this.bid = buffer.getDouble();
        this.ask = buffer.getDouble();
        this.bidSize = buffer.getDouble();
        this.askSize = buffer.getDouble();
        buffer.getLong(); // skip reserved
        this.oldestCoalescedTimestampNs = this.timestampNs;
        this.newestCoalescedTimestampNs = this.timestampNs;
        this.sumCoalescedTimestampNs = this.timestampNs;
    }

    // Getters and Setters
    public long getSequenceId() { return sequenceId; }
    public void setSequenceId(long sequenceId) { this.sequenceId = sequenceId; }

    public long getTimestampNs() { return timestampNs; }
    public void setTimestampNs(long timestampNs) { this.timestampNs = timestampNs; }

    public CurrencyPair getCurrencyPair() { return currencyPair; }
    public void setCurrencyPair(CurrencyPair currencyPair) { this.currencyPair = currencyPair; }

    public double getBid() { return bid; }
    public void setBid(double bid) { this.bid = bid; }

    public double getAsk() { return ask; }
    public void setAsk(double ask) { this.ask = ask; }

    public double getBidSize() { return bidSize; }
    public void setBidSize(double bidSize) { this.bidSize = bidSize; }

    public double getAskSize() { return askSize; }
    public void setAskSize(double askSize) { this.askSize = askSize; }

    public int getCoalescingDepth() { return coalescingDepth; }
    public void setCoalescingDepth(int coalescingDepth) { this.coalescingDepth = coalescingDepth; }

    public long getOldestCoalescedTimestampNs() { return oldestCoalescedTimestampNs; }
    public void setOldestCoalescedTimestampNs(long oldestCoalescedTimestampNs) { this.oldestCoalescedTimestampNs = oldestCoalescedTimestampNs; }

    public long getNewestCoalescedTimestampNs() { return newestCoalescedTimestampNs; }
    public void setNewestCoalescedTimestampNs(long newestCoalescedTimestampNs) { this.newestCoalescedTimestampNs = newestCoalescedTimestampNs; }

    public long getSumCoalescedTimestampNs() { return sumCoalescedTimestampNs; }
    public void setSumCoalescedTimestampNs(long sumCoalescedTimestampNs) { this.sumCoalescedTimestampNs = sumCoalescedTimestampNs; }

    public long getAverageCoalescedTimestampNs() {
        return coalescingDepth > 0 ? (sumCoalescedTimestampNs / coalescingDepth) : timestampNs;
    }

    @Override
    public String toString() {
        return "MarketDataUpdate{" +
                "seq=" + sequenceId +
                ", time=" + timestampNs +
                ", pair=" + (currencyPair != null ? currencyPair.getSymbol() : "null") +
                ", bid=" + bid +
                ", ask=" + ask +
                ", bidSize=" + bidSize +
                ", askSize=" + askSize +
                ", depth=" + coalescingDepth +
                '}';
    }
}
