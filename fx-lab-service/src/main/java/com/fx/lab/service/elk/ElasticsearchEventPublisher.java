package com.fx.lab.service.elk;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fx.lab.core.calibration.CounterfactualComparisonResult;
import com.fx.lab.core.estimator.EstimateResult;
import com.fx.lab.core.model.BookUpdate;
import com.fx.lab.core.pipeline.Publisher;
import com.fx.lab.service.engine.SimulationService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Publishes structured JSON events for Elasticsearch / Logstash ingestion.
 */
@Component
public class ElasticsearchEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(ElasticsearchEventPublisher.class);
    private static final Logger elkLogger = LoggerFactory.getLogger("ELK_EVENT_LOGGER");

    private final SimulationService simulationService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final BlockingQueue<Map<String, Object>> eventQueue = new LinkedBlockingQueue<>(50_000);
    private final Path eventLogPath = Paths.get("logs", "fx-events.ndjson");

    private final String simulationId = UUID.randomUUID().toString();
    private long sampleCounter = 0;

    public ElasticsearchEventPublisher(SimulationService simulationService) {
        this.simulationService = simulationService;
    }

    @PostConstruct
    public void init() {
        try {
            if (eventLogPath.getParent() != null) {
                Files.createDirectories(eventLogPath.getParent());
            }
        } catch (IOException ignored) {}

        // Listen to publisher published events (sampled to prevent disk saturation at 100k/s)
        simulationService.getPipeline().getPublisher().addListener(this::onPublished);

        // Start background writer thread
        Thread writer = new Thread(this::eventWriterLoop, "elk-event-writer");
        writer.setDaemon(true);
        writer.start();
    }

    private void onPublished(Publisher.PublishedEvent event) {
        // Sample 1 out of every 20 events for detailed ELK tracking to maintain low overhead
        if ((++sampleCounter % 20) != 0) return;

        BookUpdate update = event.bookUpdate();
        EstimateResult est = event.estimateResult();

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("@timestamp", Instant.now().toString());
        map.put("eventType", "PUBLISH_EVENT");
        map.put("simulationId", simulationId);
        map.put("mode", "LIVE");
        map.put("component", "Publisher");
        map.put("currencyPair", update.getCurrencyPair().getSymbol());
        map.put("sequenceId", update.getSourceSequenceId());
        map.put("actualLatencyNs", event.actualLatencyNs());
        map.put("actualLatencyUs", event.actualLatencyNs() / 1000.0);
        map.put("expectedHiddenLatencyUs", est.expectedHiddenLatencyNs() / 1000.0);
        map.put("oldestHiddenLatencyUs", est.oldestHiddenLatencyNs() / 1000.0);
        map.put("coalescingDepth", est.coalescingDepth());
        map.put("coalescingPressure", est.coalescingPressure());
        map.put("pressureStatus", est.pressureStatus().name());
        map.put("bestBid", update.getBestBid());
        map.put("bestAsk", update.getBestAsk());
        map.put("spread", update.getSpread());

        eventQueue.offer(map);
    }

    public void publishCalibrationResult(CounterfactualComparisonResult res) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("@timestamp", Instant.now().toString());
        map.put("eventType", "CALIBRATION_RESULT");
        map.put("simulationId", simulationId);
        map.put("mode", "OFFLINE_REPLAY");
        map.put("component", "CounterfactualComparator");
        map.put("matchedSamples", res.matchedSamples());
        map.put("estimatorBiasUs", res.estimatorBiasNs() / 1000.0);
        map.put("maeUs", res.meanAbsoluteErrorNs() / 1000.0);
        map.put("p95ErrorUs", res.p95AbsoluteErrorNs() / 1000.0);
        map.put("correlation", res.pearsonCorrelation());
        map.put("relativeErrorPercent", res.relativeErrorPercent());

        eventQueue.offer(map);
    }

    private void eventWriterLoop() {
        try (BufferedWriter writer = Files.newBufferedWriter(eventLogPath,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            while (true) {
                Map<String, Object> event = eventQueue.take();
                String json = objectMapper.writeValueAsString(event);
                writer.write(json);
                writer.newLine();

                // Drain burst
                int drained = 0;
                while (!eventQueue.isEmpty() && drained < 500) {
                    Map<String, Object> next = eventQueue.poll();
                    if (next != null) {
                        writer.write(objectMapper.writeValueAsString(next));
                        writer.newLine();
                        drained++;
                    }
                }
                writer.flush();
            }
        } catch (Exception e) {
            log.warn("Event writer stopped: {}", e.getMessage());
        }
    }
}
