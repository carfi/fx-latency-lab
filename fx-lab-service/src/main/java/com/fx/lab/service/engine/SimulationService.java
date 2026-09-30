package com.fx.lab.service.engine;

import com.fx.lab.core.estimator.CalibrationConfig;
import com.fx.lab.core.estimator.OnlineLatencyEstimator;
import com.fx.lab.core.generator.GeneratorConfig;
import com.fx.lab.core.generator.MarketDataGenerator;
import com.fx.lab.core.generator.TrafficProfile;
import com.fx.lab.core.metrics.LatencyMetricsCollector;
import com.fx.lab.core.metrics.LatencySnapshot;
import com.fx.lab.core.pipeline.TradingPipeline;
import com.fx.lab.core.recording.BinaryStreamRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class SimulationService {
    private static final Logger log = LoggerFactory.getLogger(SimulationService.class);

    private final Path recordingDir = Paths.get("recordings");
    private final OnlineLatencyEstimator estimator;
    private final LatencyMetricsCollector metricsCollector;
    private final TradingPipeline pipeline;
    private MarketDataGenerator generator;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean paused = new AtomicBoolean(false);
    private final AtomicBoolean recording = new AtomicBoolean(false);
    private BinaryStreamRecorder activeRecorder;
    private Path activeRecordingFile;

    private GeneratorConfig currentConfig;
    private long clobWorkNanos = 20_000L; // 20 microseconds default CLOB matching work
    private String activeScenario = "NORMAL";

    public SimulationService() {
        this.estimator = new OnlineLatencyEstimator(CalibrationConfig.defaultIdentity());
        this.metricsCollector = new LatencyMetricsCollector();
        this.pipeline = new TradingPipeline(true, true, clobWorkNanos, estimator, metricsCollector);
        this.currentConfig = GeneratorConfig.fromProfile(TrafficProfile.NORMAL);
        this.generator = new MarketDataGenerator(this.currentConfig);
    }

    public synchronized void start(TrafficProfile profile, int rate, int burstSize, double targetLatencyUs) {
        if (profile != null) {
            this.activeScenario = profile.name();
            this.currentConfig = GeneratorConfig.fromProfile(profile);
            if (rate > 0) this.currentConfig.setUpdatesPerSecond(rate);
            if (burstSize > 0) this.currentConfig.setBurstSize(burstSize);
        }

        if (targetLatencyUs > 0) {
            estimator.getConfig().setTargetLatencyNanos(targetLatencyUs * 1000.0);
        }

        if (generator.isRunning()) {
            generator.stop();
        }
        generator = new MarketDataGenerator(currentConfig);

        pipeline.start();
        generator.start(pipeline);
        running.set(true);
        paused.set(false);
        log.info("Simulation started with profile: {}, rate: {}/s", activeScenario, currentConfig.getUpdatesPerSecond());
    }

    public synchronized void stop() {
        generator.stop();
        pipeline.stop();
        if (activeRecorder != null) {
            stopRecording();
        }
        running.set(false);
        paused.set(false);
        log.info("Simulation stopped");
    }

    public synchronized void pause() {
        if (running.get() && !paused.get()) {
            generator.stop();
            paused.set(true);
            log.info("Simulation paused");
        }
    }

    public synchronized void resume() {
        if (running.get() && paused.get()) {
            generator.start(pipeline);
            paused.set(false);
            log.info("Simulation resumed");
        }
    }

    public synchronized void reset() {
        stop();
        pipeline.reset();
        generator.reset();
        log.info("Simulation reset");
    }

    public synchronized void setScenario(TrafficProfile profile) {
        this.activeScenario = profile.name();
        this.currentConfig = GeneratorConfig.fromProfile(profile);
        if (running.get() && !paused.get()) {
            generator.stop();
            generator = new MarketDataGenerator(currentConfig);
            generator.start(pipeline);
        }
        log.info("Scenario switched to: {}", profile.name());
    }

    public synchronized Path startRecording() throws IOException {
        if (recording.get()) {
            return activeRecordingFile;
        }

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        Path filePath = recordingDir.resolve("stream_" + activeScenario.toLowerCase() + "_" + timestamp + ".bin");
        activeRecorder = new BinaryStreamRecorder(filePath);
        pipeline.getFeedHandler().setRecorder(activeRecorder);
        recording.set(true);
        activeRecordingFile = filePath;
        log.info("Recording started to: {}", filePath.toAbsolutePath());
        return filePath;
    }

    public synchronized Path stopRecording() {
        if (!recording.get()) {
            return activeRecordingFile;
        }

        pipeline.getFeedHandler().setRecorder(null);
        if (activeRecorder != null) {
            activeRecorder.stop();
            try {
                activeRecorder.close();
            } catch (IOException ignored) {
            }
            activeRecorder = null;
        }
        recording.set(false);
        log.info("Recording stopped. File: {}", activeRecordingFile);
        return activeRecordingFile;
    }

    public void updateCalibration(CalibrationConfig config) {
        estimator.updateCalibration(config);
        log.info("Estimator calibration updated: {}", config.getModelType());
    }

    public LatencySnapshot getSnapshot() {
        return pipeline.getMetricsSnapshot();
    }

    public boolean isRunning() { return running.get(); }
    public boolean isPaused() { return paused.get(); }
    public boolean isRecording() { return recording.get(); }
    public String getActiveScenario() { return activeScenario; }
    public GeneratorConfig getCurrentConfig() { return currentConfig; }
    public OnlineLatencyEstimator getEstimator() { return estimator; }
    public TradingPipeline getPipeline() { return pipeline; }
    public Path getActiveRecordingFile() { return activeRecordingFile; }
}
