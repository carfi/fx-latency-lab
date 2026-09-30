package com.fx.lab.service.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SimulationApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void testSimulationLifecycleAndEndpoints() throws Exception {
        // 1. Get initial status
        mockMvc.perform(get("/api/simulation/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.running").value(false));

        // 2. Start simulation
        mockMvc.perform(post("/api/simulation/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scenario\":\"NORMAL\",\"rate\":5000,\"burstSize\":1,\"targetLatencyUs\":50.0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("STARTED"));

        // 3. Check status is running
        mockMvc.perform(get("/api/simulation/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.running").value(true));

        // 4. Switch scenario to OVERLOAD
        mockMvc.perform(post("/api/simulation/scenario")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scenario\":\"OVERLOAD\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scenario").value("OVERLOAD"));

        // 5. Query metrics
        mockMvc.perform(get("/api/simulation/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inputMessagesPerSec").exists())
                .andExpect(jsonPath("$.pressureStatus").exists());

        // 6. Stop simulation
        mockMvc.perform(post("/api/simulation/stop"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("STOPPED"));
    }

    @Test
    void testRecordingAndReplayEndpoints() throws Exception {
        // Start and stop recording
        mockMvc.perform(post("/api/simulation/recording/start"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECORDING_STARTED"));

        mockMvc.perform(post("/api/simulation/recording/stop"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECORDING_STOPPED"));

        // List recordings
        mockMvc.perform(get("/api/replay/recordings"))
                .andExpect(status().isOk());

        // Get current calibration
        mockMvc.perform(get("/api/replay/calibration/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modelType").exists());
    }
}
