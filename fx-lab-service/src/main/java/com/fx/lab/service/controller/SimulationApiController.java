package com.fx.lab.service.controller;

import com.fx.lab.core.generator.TrafficProfile;
import com.fx.lab.core.metrics.LatencySnapshot;
import com.fx.lab.service.engine.SimulationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

@RestController
@RequestMapping("/api/simulation")
@CrossOrigin(origins = "*")
public class SimulationApiController {

    private final SimulationService simulationService;

    public SimulationApiController(SimulationService simulationService) {
        this.simulationService = simulationService;
    }

    public record StartRequest(
            String scenario,
            Integer rate,
            Integer burstSize,
            Double targetLatencyUs
    ) {}

    @PostMapping("/start")
    public ResponseEntity<?> start(@RequestBody(required = false) StartRequest request) {
        TrafficProfile profile = TrafficProfile.NORMAL;
        int rate = 5_000;
        int burstSize = 1;
        double targetLatencyUs = 50.0;

        if (request != null) {
            if (request.scenario() != null) {
                try {
                    profile = TrafficProfile.valueOf(request.scenario().toUpperCase());
                } catch (IllegalArgumentException ignored) {
                }
            }
            if (request.rate() != null && request.rate() > 0) rate = request.rate();
            if (request.burstSize() != null && request.burstSize() > 0) burstSize = request.burstSize();
            if (request.targetLatencyUs() != null && request.targetLatencyUs() > 0) targetLatencyUs = request.targetLatencyUs();
        }

        simulationService.start(profile, rate, burstSize, targetLatencyUs);
        return ResponseEntity.ok(Map.of(
                "status", "STARTED",
                "scenario", simulationService.getActiveScenario(),
                "rate", simulationService.getCurrentConfig().getUpdatesPerSecond()
        ));
    }

    @PostMapping("/stop")
    public ResponseEntity<?> stop() {
        simulationService.stop();
        return ResponseEntity.ok(Map.of("status", "STOPPED"));
    }

    @PostMapping("/pause")
    public ResponseEntity<?> pause() {
        simulationService.pause();
        return ResponseEntity.ok(Map.of("status", "PAUSED"));
    }

    @PostMapping("/resume")
    public ResponseEntity<?> resume() {
        simulationService.resume();
        return ResponseEntity.ok(Map.of("status", "RESUMED"));
    }

    @PostMapping("/reset")
    public ResponseEntity<?> reset() {
        simulationService.reset();
        return ResponseEntity.ok(Map.of("status", "RESET"));
    }

    @PostMapping("/scenario")
    public ResponseEntity<?> setScenario(@RequestBody Map<String, String> body) {
        String scenarioName = body.getOrDefault("scenario", "NORMAL");
        TrafficProfile profile = TrafficProfile.valueOf(scenarioName.toUpperCase());
        simulationService.setScenario(profile);
        return ResponseEntity.ok(Map.of(
                "status", "SCENARIO_UPDATED",
                "scenario", simulationService.getActiveScenario()
        ));
    }

    @PostMapping("/coalescing")
    public ResponseEntity<?> toggleCoalescing(@RequestBody Map<String, Boolean> body) {
        Boolean c1 = body.get("coalescer1");
        Boolean c2 = body.get("coalescer2");
        if (c1 != null) simulationService.getPipeline().setCoalescing1Enabled(c1);
        if (c2 != null) simulationService.getPipeline().setCoalescing2Enabled(c2);

        return ResponseEntity.ok(Map.of(
                "coalescer1", simulationService.getPipeline().isCoalescing1Enabled(),
                "coalescer2", simulationService.getPipeline().isCoalescing2Enabled()
        ));
    }

    @GetMapping("/status")
    public ResponseEntity<?> getStatus() {
        return ResponseEntity.ok(Map.of(
                "running", simulationService.isRunning(),
                "paused", simulationService.isPaused(),
                "recording", simulationService.isRecording(),
                "scenario", simulationService.getActiveScenario(),
                "recordingFile", simulationService.getActiveRecordingFile() != null
                        ? simulationService.getActiveRecordingFile().getFileName().toString() : "",
                "coalescer1Enabled", simulationService.getPipeline().isCoalescing1Enabled(),
                "coalescer2Enabled", simulationService.getPipeline().isCoalescing2Enabled(),
                "config", simulationService.getCurrentConfig()
        ));
    }

    @GetMapping("/metrics")
    public ResponseEntity<LatencySnapshot> getMetrics() {
        return ResponseEntity.ok(simulationService.getSnapshot());
    }

    @PostMapping("/recording/start")
    public ResponseEntity<?> startRecording() {
        try {
            Path file = simulationService.startRecording();
            return ResponseEntity.ok(Map.of(
                    "status", "RECORDING_STARTED",
                    "file", file.getFileName().toString()
            ));
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/recording/stop")
    public ResponseEntity<?> stopRecording() {
        Path file = simulationService.stopRecording();
        return ResponseEntity.ok(Map.of(
                "status", "RECORDING_STOPPED",
                "file", file != null ? file.getFileName().toString() : ""
        ));
    }
}
