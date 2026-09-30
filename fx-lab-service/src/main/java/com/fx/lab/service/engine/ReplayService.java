package com.fx.lab.service.engine;

import com.fx.lab.core.calibration.CounterfactualComparator;
import com.fx.lab.core.calibration.CounterfactualComparisonResult;
import com.fx.lab.core.estimator.CalibrationConfig;
import com.fx.lab.core.estimator.OnlineLatencyEstimator;
import com.fx.lab.core.metrics.LatencyMetricsCollector;
import com.fx.lab.core.model.MarketDataUpdate;
import com.fx.lab.core.pipeline.TradingPipeline;
import com.fx.lab.core.recording.BinaryStreamReplayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class ReplayService {
    private static final Logger log = LoggerFactory.getLogger(ReplayService.class);
    private final Path recordingDir = Paths.get("recordings");

    private final AtomicReference<CounterfactualComparisonResult> latestComparison = new AtomicReference<>(null);

    public List<String> listRecordings() {
        if (!Files.exists(recordingDir)) {
            return Collections.emptyList();
        }
        try (Stream<Path> stream = Files.list(recordingDir)) {
            return stream.filter(p -> p.toString().endsWith(".bin"))
                    .map(p -> p.getFileName().toString())
                    .sorted(Collections.reverseOrder())
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.error("Failed to list recordings", e);
            return Collections.emptyList();
        }
    }

    public CounterfactualComparisonResult runReplay(String filename) throws IOException {
        Path filePath = filename.contains("/") || filename.contains("\\")
                ? Paths.get(filename)
                : recordingDir.resolve(filename);

        if (!Files.exists(filePath)) {
            throw new IllegalArgumentException("Recording file not found: " + filePath);
        }

        log.info("Loading binary stream from: {}", filePath);
        BinaryStreamReplayer replayer = new BinaryStreamReplayer(filePath);
        List<MarketDataUpdate> updates = replayer.loadAll();
        log.info("Loaded {} updates from binary recording", updates.size());

        // 1. Scenario A: Replay with Coalescing ON
        List<CounterfactualComparator.RunRecord> onRecords = new ArrayList<>();
        TradingPipeline pipelineOn = new TradingPipeline(
                true, true, 20_000L,
                new OnlineLatencyEstimator(CalibrationConfig.defaultIdentity()),
                new LatencyMetricsCollector()
        );
        pipelineOn.getPublisher().addListener(event -> onRecords.add(CounterfactualComparator.RunRecord.fromPublished(event)));

        for (MarketDataUpdate update : updates) {
            pipelineOn.onMarketData(update);
            pipelineOn.processNext();
        }
        pipelineOn.drainAll();

        // 2. Scenario B: Replay with Coalescing OFF (Ground Truth)
        List<CounterfactualComparator.RunRecord> offRecords = new ArrayList<>();
        TradingPipeline pipelineOff = new TradingPipeline(
                false, false, 20_000L,
                new OnlineLatencyEstimator(CalibrationConfig.defaultIdentity()),
                new LatencyMetricsCollector()
        );
        pipelineOff.getPublisher().addListener(event -> offRecords.add(CounterfactualComparator.RunRecord.fromPublished(event)));

        for (MarketDataUpdate update : updates) {
            pipelineOff.onMarketData(update);
            pipelineOff.processNext();
        }
        pipelineOff.drainAll();

        // 3. Compare ON vs OFF and calibrate
        CounterfactualComparator comparator = new CounterfactualComparator();
        CounterfactualComparisonResult result = comparator.compare(onRecords, offRecords);
        latestComparison.set(result);

        log.info("Replay completed. ON published: {}, OFF published: {}, Matched: {}, MAE: {}us, Bias: {}us, Corr: {}",
                result.totalOnPublished(), result.totalOffPublished(), result.matchedSamples(),
                result.meanAbsoluteErrorNs() / 1000.0, result.estimatorBiasNs() / 1000.0, result.pearsonCorrelation());

        return result;
    }

    public CounterfactualComparisonResult getLatestResult() {
        return latestComparison.get();
    }
}
