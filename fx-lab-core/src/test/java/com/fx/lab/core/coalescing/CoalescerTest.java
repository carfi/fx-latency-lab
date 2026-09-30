package com.fx.lab.core.coalescing;

import com.fx.lab.core.model.CurrencyPair;
import com.fx.lab.core.model.MarketDataUpdate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CoalescerTest {
    private MarketDataCoalescer coalescer;

    @BeforeEach
    void setUp() {
        coalescer = new MarketDataCoalescer("TestCoalescer", true);
    }

    @Test
    void testCoalescingOverwritesOlderPendingUpdate() {
        long t1 = 1_000_000L;
        long t2 = 1_001_000L;
        long t3 = 1_002_000L;

        MarketDataUpdate u1 = new MarketDataUpdate(1, t1, CurrencyPair.EUR_USD, 1.0850, 1.0852, 100, 100);
        MarketDataUpdate u2 = new MarketDataUpdate(2, t2, CurrencyPair.EUR_USD, 1.0851, 1.0853, 100, 100);
        MarketDataUpdate u3 = new MarketDataUpdate(3, t3, CurrencyPair.EUR_USD, 1.0852, 1.0854, 100, 100);

        coalescer.offer(u1);
        coalescer.offer(u2);
        coalescer.offer(u3);

        assertThat(coalescer.getQueueDepth()).isEqualTo(1);
        assertThat(coalescer.getCoalescedCount()).isEqualTo(2);

        MarketDataUpdate polled = coalescer.poll();
        assertThat(polled).isNotNull();
        assertThat(polled.getSequenceId()).isEqualTo(3);
        assertThat(polled.getBid()).isEqualTo(1.0852);
        assertThat(polled.getCoalescingDepth()).isEqualTo(3);
        assertThat(polled.getOldestCoalescedTimestampNs()).isEqualTo(t1);
        assertThat(polled.getNewestCoalescedTimestampNs()).isEqualTo(t3);
        assertThat(polled.getSumCoalescedTimestampNs()).isEqualTo(t1 + t2 + t3);
        assertThat(polled.getAverageCoalescedTimestampNs()).isEqualTo((t1 + t2 + t3) / 3);

        assertThat(coalescer.poll()).isNull();
        assertThat(coalescer.getQueueDepth()).isEqualTo(0);
    }

    @Test
    void testCoalescingOffBehavesAsFifo() {
        coalescer.setEnabled(false);

        MarketDataUpdate u1 = new MarketDataUpdate(1, 100, CurrencyPair.EUR_USD, 1.0850, 1.0852, 100, 100);
        MarketDataUpdate u2 = new MarketDataUpdate(2, 200, CurrencyPair.EUR_USD, 1.0851, 1.0853, 100, 100);

        coalescer.offer(u1);
        coalescer.offer(u2);

        assertThat(coalescer.getQueueDepth()).isEqualTo(2);
        assertThat(coalescer.poll().getSequenceId()).isEqualTo(1);
        assertThat(coalescer.poll().getSequenceId()).isEqualTo(2);
        assertThat(coalescer.poll()).isNull();
    }

    @Test
    void testMultipleCurrencyPairsDoNotInterfere() {
        MarketDataUpdate eur = new MarketDataUpdate(1, 100, CurrencyPair.EUR_USD, 1.0850, 1.0852, 100, 100);
        MarketDataUpdate jpy = new MarketDataUpdate(2, 200, CurrencyPair.USD_JPY, 154.50, 154.52, 100, 100);

        coalescer.offer(eur);
        coalescer.offer(jpy);

        assertThat(coalescer.getQueueDepth()).isEqualTo(2);
        MarketDataUpdate first = coalescer.poll();
        MarketDataUpdate second = coalescer.poll();

        assertThat(first.getCurrencyPair()).isEqualTo(CurrencyPair.EUR_USD);
        assertThat(second.getCurrencyPair()).isEqualTo(CurrencyPair.USD_JPY);
    }
}
