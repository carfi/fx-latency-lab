# FX CLOB Coalescing Latency Lab

A self-contained, low-allocation trading-system laboratory built in **Java 21** to validate an **online estimator for hidden and counterfactual latency** caused by price coalescing in an FX Central Limit Order Book (CLOB).

[![Java](https://img.shields.io/badge/Java-21-orange.svg)]()
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.4-brightgreen.svg)]()
[![Build](https://img.shields.io/badge/Maven-Multi--Module-blue.svg)]()
[![Tests](https://img.shields.io/badge/Tests-Passing-success.svg)]()

---

## 1. Executive Summary

Price coalescing (replacing unconsumed price updates for the same instrument with the newest quote) prevents message queues from overflowing during high market volatility. However, **traditional latency metrics (P99) measured only on surviving updates appear deceptively low**, while significant queueing delays and unconsumed quote delays remain hidden.

This lab provides:
1. **LIVE Mode**: Low-allocation FX trading pipeline with dual coalescing stages (Pre-CLOB and Pre-Publish), evaluating an online zero-allocation hidden latency estimator and coalescing pressure in real time.
2. **OFFLINE Mode**: Bit-for-bit deterministic binary stream recording, replayed with Coalescing **ON** and **OFF** to establish the true counterfactual latency ground truth, calibrate model coefficients via OLS regression, and dynamically hot-reload calibrations into the live engine.
3. **Observability**: Interactive real-time Web Dashboard (SSE telemetry at 10 Hz), preconfigured ELK Stack (Elasticsearch 8, Logstash, Kibana dashboards), and JMH benchmark harness.

```
+------------------------------------------------------------------------------------------------+
|                                      TRADING PIPELINE                                          |
+------------------------------------------------------------------------------------------------+
 Market Data Generator (Deterministic)
           |
           v
+---------------------+
| Feed Handler        | -> (Raw Binary Recording: 64-byte aligned frames)
+---------------------+
           |
           v
+---------------------+
| Price Aggregator    |
+---------------------+
           |
           v
>>> COALESCER #1 <<<    (Pre-CLOB: symbol slot buffer, primitive tracking)
           |
           v
+---------------------+
| Multi-Pair FX CLOB  | (EUR/USD, GBP/USD, USD/JPY, USD/CHF, AUD/USD, EUR/GBP)
+---------------------+
           |
           v
>>> COALESCER #2 <<<    (Pre-Publish: output slot buffer)
           |
           v
+---------------------+
| Publisher           | -> Online Latency Estimator -> HdrHistogram -> Web UI & ELK
+---------------------+
```

---

## 2. Prerequisites

- **Java**: Amazon Corretto 21 or OpenJDK 21+
- **Maven**: 3.8+
- **Docker & Docker Compose**: (Optional, for running ELK Stack)

---

## 3. Quickstart & Service Management

### 3.1 Build & Package All Modules
From the root directory:
```powershell
# Set Java 21 environment if not already active
$env:JAVA_HOME = "C:\Program Files\Amazon Corretto\jdk21.0.8_9"

# Build, test, and package all modules
mvn clean package
```

---

### 3.2 Starting the Application

#### Option A: Running the Executable JAR (Recommended)
Once built, launch the standalone application:
```powershell
java -jar fx-lab-service/target/fx-lab-service-1.0.0-SNAPSHOT.jar
```

#### Option B: Running via Maven
```powershell
mvn spring-boot:run -pl fx-lab-service
```

#### Option C: Running as a Background Service (PowerShell)
To launch in the background so your terminal remains free:
```powershell
Start-Process java -ArgumentList "-jar", "fx-lab-service/target/fx-lab-service-1.0.0-SNAPSHOT.jar" -WindowStyle Hidden
```

Once started, open your web browser at:
👉 **[http://localhost:8080/](http://localhost:8080/)**

---

### 3.3 Stopping the Application

#### 1. Stopping the Simulation Market Data Feed (Keeping the Server Running)
- **Via Web UI**: Click the **⏹ Stop Simulation** button on the Dashboard or Simulation Control tab.
- **Via REST API**:
  ```powershell
  Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/simulation/stop"
  ```
  *(or on Linux/macOS: `curl -X POST http://localhost:8080/api/simulation/stop`)*

#### 2. Stopping the Web UI & Control Plane Server Process
- **Foreground Terminal**: Press `Ctrl + C` in the terminal where the process is running.
- **Background Process (PowerShell)**:
  Find and terminate the process listening on port 8080:
  ```powershell
  # Find PID and stop process
  $port = 8080
  $conn = Get-NetTCPConnection -LocalPort $port -ErrorAction SilentlyContinue | Select-Object -First 1
  if ($conn) { Stop-Process -Id $conn.OwningProcess -Force; Write-Host "Server (PID $($conn.OwningProcess)) stopped." }
  ```
- **Background Process (Linux/macOS)**:
  ```bash
  fuser -k 8080/tcp
  # or
  kill $(lsof -t -i:8080)
  ```

---

### 3.4 (Optional) Starting and Stopping the ELK Stack

#### Start ELK Stack
```powershell
cd docker
docker compose up -d
```
- **Kibana Dashboard**: [http://localhost:5601](http://localhost:5601)
- **Elasticsearch API**: [http://localhost:9200](http://localhost:9200)

#### Stop ELK Stack
```powershell
cd docker
docker compose down
```

---

## 4. End-to-End Experiment Walkthrough

### Step 1: Normal Operation (Nominal Load)
1. Open the Web Dashboard at [http://localhost:8080](http://localhost:8080).
2. Click **Start Simulation**.
3. Observe:
   - Input Rate: ~5,000 updates/sec.
   - Coalescing Ratio: ~0%.
   - Actual P99 Latency: < 20 μs.
   - Expected Hidden Latency: < 25 μs.
   - Coalescing Pressure: `HEALTHY` (< 1.0x).

### Step 2: Overload Surge (Witnessing Hidden Latency Divergence)
1. In the **Simulation Control** tab, click **OVERLOAD** (or drag input rate to 70,000 updates/sec).
2. Switch back to the **Dashboard** tab:
   - Notice that **Actual Measured P99 Latency remains flat and deceptively low** (~25 μs).
   - In stark contrast, **Expected Hidden Latency spikes to 200–500 μs**!
   - Coalescing Pressure transitions to `CRITICAL` (> 2.0x).
   - Coalescing ratio climbs to 60–85%.

### Step 3: Record Raw Stream
1. Click **Record Raw Stream (.bin)** in the controls.
2. Let the simulation record 10 seconds of overload traffic.
3. Click **Stop Recording**. The binary dataset is saved under `recordings/`.

### Step 4: Offline Replay & Ground-Truth Calibration
1. Navigate to the **Offline Replay & Calibration** tab.
2. Select your newly recorded binary stream (or the bundled `sample_overload_stream.bin`).
3. Click **Run Replay & Calibrate**.
4. The system executes:
   - **Replay Scenario A (Coalescing ON)**: Captures surviving events and online estimates.
   - **Replay Scenario B (Coalescing OFF)**: Disables coalescers and runs every quote through the FIFO queue and CLOB to capture the true counterfactual latency ground truth.
   - **Comparator**: Evaluates Mean Estimator Bias, Mean Absolute Error (MAE), P95 Error, and Pearson Correlation ($r > 0.95$).
   - **OLS Multivariate Solver**: Fits optimal linear weights:
     $$\hat{L} = w_0 + w_1 \cdot L_{\text{avg}} + w_2 \cdot \text{depth} + w_3 \cdot L_{\text{oldest}}$$
5. Inspect the **Estimated vs Ground Truth Overlay Chart**.

### Step 5: Hot-Reload Calibration into Live Engine
1. Click **✓ Apply Calibration to Live Engine**.
2. Switch back to the **Dashboard** and observe the live online estimator now tracking with calibrated weights!

---

## 5. REST API Reference

| Endpoint | Method | Description |
|---|---|---|
| `/api/simulation/start` | POST | Starts generator with scenario, rate, burst, target latency |
| `/api/simulation/stop` | POST | Stops active simulation and generator |
| `/api/simulation/pause` | POST | Pauses generator emission |
| `/api/simulation/resume` | POST | Resumes paused generator |
| `/api/simulation/reset` | POST | Clears pipeline queues and resets histograms |
| `/api/simulation/scenario` | POST | Switches scenario preset (`NORMAL`, `OVERLOAD`, `BURST`, etc.) |
| `/api/simulation/coalescing` | POST | Dynamically toggles Coalescer #1 and Coalescer #2 |
| `/api/simulation/status` | GET | Returns engine running state, active scenario, config |
| `/api/simulation/metrics` | GET | Returns instantaneous `LatencySnapshot` |
| `/api/simulation/recording/start` | POST | Begins binary stream recording to disk |
| `/api/simulation/recording/stop` | POST | Flushes and closes binary recording file |
| `/api/stream/metrics` | GET (SSE) | Server-Sent Events real-time 10 Hz telemetry stream |
| `/api/replay/recordings` | GET | Lists available `.bin` recording files |
| `/api/replay/run` | POST | Executes deterministic Replay ON and OFF comparison |
| `/api/replay/latest-result` | GET | Retrieves latest comparison statistics |
| `/api/replay/calibration/apply` | POST | Hot-reloads calibrated model coefficients |
| `/api/replay/calibration/current` | GET | Retrieves current active calibration config |

---

## 6. JMH Performance Benchmarking

To measure the overhead of the online estimator in the hot path:
```powershell
mvn clean package -pl fx-lab-benchmarks
java -jar fx-lab-benchmarks/target/benchmarks.jar -wi 3 -i 5 -f 1
```

The benchmark evaluates:
- `baselineDirectLatency`: Raw latency calculation ($T_{\text{pub}} - T_{\text{input}}$).
- `identityEstimatorEvaluation`: Ultra-lightweight primitive estimation.
- `multivariateEstimatorEvaluation`: Full linear multivariate regression evaluation.

Results verify sub-10-nanosecond execution with **0 bytes/op heap allocation** in the evaluation loop.

---

## 7. Project Structure

```
fx-clob-coalescing-lab/
├── pom.xml                               # Root Maven POM
├── README.md                             # Complete documentation
├── sample-data/
│   ├── calibration.json                  # Sample calibrated model profile
│   └── sample_overload_stream.bin        # Deterministic sample market data recording
├── docker/
│   ├── docker-compose.yml                # Elasticsearch, Logstash, Kibana stack
│   ├── logstash/logstash.conf            # Event ingestion and mapping
│   ├── elasticsearch/index_template.json # Template for fx-latency-*
│   └── kibana/dashboards.ndjson          # Pre-packaged Kibana dashboards
├── docs/
│   ├── architecture.md                   # Complete architectural design
│   └── calibration_theory.md             # Mathematical derivation & OLS solver
├── fx-lab-core/                          # Low-allocation trading engine
│   └── src/
│       ├── main/java/com/fx/lab/core/
│       │   ├── model/                    # CurrencyPair, MarketDataUpdate, BookUpdate
│       │   ├── coalescing/               # GroupState, Coalescer 1 & 2
│       │   ├── clob/                     # ClobOrderBook, L2 book ladders
│       │   ├── estimator/                # OnlineLatencyEstimator, CalibrationConfig
│       │   ├── metrics/                  # LatencyMetricsCollector (HdrHistogram)
│       │   ├── recording/                # BinaryStreamRecorder, Replayer
│       │   ├── calibration/              # CounterfactualComparator (OLS Solver)
│       │   ├── generator/                # MarketDataGenerator, TrafficProfile
│       │   └── pipeline/                 # FeedHandler, Aggregator, Publisher, Pipeline
│       └── test/                         # Comprehensive unit & integration tests
├── fx-lab-benchmarks/                    # JMH benchmark suite
└── fx-lab-service/                       # Control Plane & Web UI
    └── src/
        ├── main/java/com/fx/lab/service/ # Spring Boot App, REST APIs, SSE, ELK
        └── main/resources/
            ├── application.yml
            └── static/index.html         # Modern reactive single-page dashboard
```
