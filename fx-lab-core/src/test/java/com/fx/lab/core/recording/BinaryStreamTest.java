package com.fx.lab.core.recording;

import com.fx.lab.core.model.CurrencyPair;
import com.fx.lab.core.model.MarketDataUpdate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BinaryStreamTest {

    @Test
    void testRecordAndReplayExactMatches(@TempDir Path tempDir) throws IOException {
        Path filePath = tempDir.resolve("test_stream.bin");

        List<MarketDataUpdate> written = new ArrayList<>();
        try (BinaryStreamRecorder recorder = new BinaryStreamRecorder(filePath)) {
            for (int i = 1; i <= 100; i++) {
                CurrencyPair pair = CurrencyPair.fromId(i % CurrencyPair.COUNT);
                MarketDataUpdate update = new MarketDataUpdate(
                        i,
                        10_000_000L + i * 1_000L,
                        pair,
                        pair.getBasePrice() + i * 0.0001,
                        pair.getBasePrice() + (i + 1) * 0.0001,
                        100_000 + i,
                        100_000 + i
                );
                written.add(update);
                recorder.record(update);
            }
        }

        BinaryStreamReplayer replayer = new BinaryStreamReplayer(filePath);
        List<MarketDataUpdate> read = replayer.loadAll();

        assertThat(read).hasSize(written.size());
        for (int i = 0; i < written.size(); i++) {
            MarketDataUpdate orig = written.get(i);
            MarketDataUpdate loaded = read.get(i);
            assertThat(loaded.getSequenceId()).isEqualTo(orig.getSequenceId());
            assertThat(loaded.getTimestampNs()).isEqualTo(orig.getTimestampNs());
            assertThat(loaded.getCurrencyPair()).isEqualTo(orig.getCurrencyPair());
            assertThat(loaded.getBid()).isEqualTo(orig.getBid());
            assertThat(loaded.getAsk()).isEqualTo(orig.getAsk());
            assertThat(loaded.getBidSize()).isEqualTo(orig.getBidSize());
            assertThat(loaded.getAskSize()).isEqualTo(orig.getAskSize());
        }
    }
}
