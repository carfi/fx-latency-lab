package com.fx.lab.core.calibration;

import com.fx.lab.core.estimator.CalibrationConfig;
import com.fx.lab.core.estimator.OnlineLatencyEstimator;
import com.fx.lab.core.generator.GeneratorConfig;
import com.fx.lab.core.generator.MarketDataGenerator;
import com.fx.lab.core.generator.TrafficProfile;
import com.fx.lab.core.metrics.LatencyMetricsCollector;
import com.fx.lab.core.model.MarketDataUpdate;
import com.fx.lab.core.pipeline.TradingPipeline;
import com.fx.lab.core.recording.BinaryStreamRecorder;
import com.fx.lab.core.recording.BinaryStreamReplayer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DeterministicReplayIntegrationTest {

    @Test
    void testEndToEndRecordingReplayAndCalibration(@TempDir Path tempDir) throws IOException {
        Path recordPath = tempDir.resolve("market_data_run.bin");

        // 1. Generate & Record 500 deterministic updates
        GeneratorConfig genConfig = GeneratorConfig.fromProfile(TrafficProfile.OVERLOAD);
        genConfig.setRandomSeed(12345L);
        MarketDataGenerator generator = new MarketDataGenerator(genConfig);

        try (BinaryStreamRecorder recorder = new BinaryStreamRecorder(recordPath)) {
            MarketDataUpdate update = new MarketDataUpdate();
            for (int i = 0; i < 500; i++) {
                generator.nextUpdate(update);
                recorder.record(update);
            }
        }

        // 2. Load recorded updates
        BinaryStreamReplayer replayer = new BinaryStreamReplayer(recordPath);
        List<MarketDataUpdate> dataset = replayer.loadAll();
        assertThat(dataset).hasSize(500);

        // 3. Scenario A: Replay with Coalescing ON
        List<CounterfactualComparator.RunRecord> onRecords = new ArrayList<>();
        TradingPipeline pipelineOn = new TradingPipeline(true, true, 5000L, new OnlineLatencyEstimator(), new LatencyMetricsCollector());
        pipelineOn.getPublisher().addListener(event -> onRecords.add(CounterfactualComparator.RunRecord.fromPublished(event)));

        for (MarketDataUpdate u : dataset) {
            pipelineOn.onMarketData(u);
            pipelineOn.processNext();
        }
        pipelineOn.drainAll();

        // 4. Scenario B: Replay with Coalescing OFF
        List<CounterfactualComparator.RunRecord> offRecords = new ArrayList<>();
        TradingPipeline pipelineOff = new TradingPipeline(false, false, 5000L, new OnlineLatencyEstimator(), new LatencyMetricsCollector());
        pipelineOff.getPublisher().addListener(event -> offRecords.add(CounterfactualComparator.RunRecord.fromPublished(event)));

        for (MarketDataUpdate u : dataset) {
            pipelineOff.onMarketData(u);
            pipelineOff.processNext();
        }
        pipelineOff.drainAll();

        // 5. Compare & Calibrate
        CounterfactualComparator comparator = new CounterfactualComparator();
        CounterfactualComparisonResult comparison = comparator.compare(onRecords, offRecords);

        assertThat(comparison.totalOffPublished()).isEqualTo(500);
        assertThat(comparison.totalOnPublished()).isLessThanOrEqualTo(500);
        assertThat(comparison.matchedSamples()).isGreaterThan(0);
        assertThat(comparison.recommendedCalibration()).isNotNull();

        // Verify calibrated config is usable in OnlineLatencyEstimator
        CalibrationConfig calibratedConfig = comparison.recommendedCalibration();
        OnlineLatencyEstimator calibratedEstimator = new OnlineLatencyEstimator(calibratedConfig);
        long estimate = calibratedEstimator.estimateExpectedHiddenLatencyNs(10_000_000L, 9_900_000L, 9_990_000L, 9_950_000L, 5);
        assertThat(estimate).isGreaterThan(0L);
    }
}
