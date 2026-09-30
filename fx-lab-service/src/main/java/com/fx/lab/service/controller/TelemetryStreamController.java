package com.fx.lab.service.controller;

import com.fx.lab.core.metrics.LatencySnapshot;
import com.fx.lab.service.engine.SimulationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@RestController
@RequestMapping("/api/stream")
@CrossOrigin(origins = "*")
@EnableScheduling
public class TelemetryStreamController {
    private static final Logger log = LoggerFactory.getLogger(TelemetryStreamController.class);

    private final SimulationService simulationService;
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public TelemetryStreamController(SimulationService simulationService) {
        this.simulationService = simulationService;
    }

    @GetMapping(value = "/metrics", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamMetrics() {
        SseEmitter emitter = new SseEmitter(1800_000L); // 30 mins timeout
        emitters.add(emitter);

        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));

        return emitter;
    }

    @Scheduled(fixedRate = 100) // 10 Hz broadcast
    public void broadcastMetrics() {
        if (emitters.isEmpty()) return;

        LatencySnapshot snapshot = simulationService.getSnapshot();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("metrics")
                        .data(snapshot, MediaType.APPLICATION_JSON));
            } catch (IOException | IllegalStateException e) {
                emitters.remove(emitter);
            }
        }
    }
}
