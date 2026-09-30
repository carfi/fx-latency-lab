package com.fx.lab.service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fx.lab.core.calibration.CounterfactualComparisonResult;
import com.fx.lab.core.estimator.CalibrationConfig;
import com.fx.lab.service.engine.ReplayService;
import com.fx.lab.service.engine.SimulationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/replay")
@CrossOrigin(origins = "*")
public class ReplayApiController {

    private final ReplayService replayService;
    private final SimulationService simulationService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Path calibrationFile = Paths.get("calibration.json");

    public ReplayApiController(ReplayService replayService, SimulationService simulationService) {
        this.replayService = replayService;
        this.simulationService = simulationService;
    }

    @GetMapping("/recordings")
    public ResponseEntity<List<String>> listRecordings() {
        return ResponseEntity.ok(replayService.listRecordings());
    }

    @PostMapping("/run")
    public ResponseEntity<?> runReplay(@RequestBody Map<String, String> body) {
        String filename = body.get("filename");
        if (filename == null || filename.isBlank()) {
            List<String> list = replayService.listRecordings();
            if (list.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "No recordings available to replay"));
            }
            filename = list.get(0);
        }

        try {
            CounterfactualComparisonResult result = replayService.runReplay(filename);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/latest-result")
    public ResponseEntity<?> getLatestResult() {
        CounterfactualComparisonResult result = replayService.getLatestResult();
        if (result == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/calibration/apply")
    public ResponseEntity<?> applyCalibration(@RequestBody(required = false) CalibrationConfig customConfig) {
        CalibrationConfig configToApply = customConfig;
        if (configToApply == null) {
            CounterfactualComparisonResult result = replayService.getLatestResult();
            if (result != null && result.recommendedCalibration() != null) {
                configToApply = result.recommendedCalibration();
            } else {
                return ResponseEntity.badRequest().body(Map.of("error", "No calibration available to apply"));
            }
        }

        simulationService.updateCalibration(configToApply);

        // Also save to calibration.json
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(calibrationFile.toFile(), configToApply);
        } catch (IOException e) {
            // Ignore error writing file
        }

        return ResponseEntity.ok(Map.of(
                "status", "CALIBRATION_APPLIED",
                "config", configToApply
        ));
    }

    @GetMapping("/calibration/current")
    public ResponseEntity<?> getCurrentCalibration() {
        return ResponseEntity.ok(simulationService.getEstimator().getConfig());
    }
}
