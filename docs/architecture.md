# Architecture Specification: FX CLOB Coalescing Latency Lab

## 1. System Overview

The **FX CLOB Coalescing Latency Lab** is an ultra-low latency, low-allocation pair-trading and limit order book laboratory designed to investigate and quantify **hidden queueing delay** introduced by price coalescing in high-frequency trading pipelines.

In electronic financial markets, price coalescing (or quote confluence) replaces unconsumed older price updates with the most recent quote for a given instrument. While coalescing prevents internal message queues from exploding during market bursts, **traditionally measured latency** (time spent by surviving quotes in the engine) remains low, hiding the fact that unconsumed quotes sat waiting in queues before being superseded.

This lab implements an online, zero-allocation estimator that computes hidden latency in real-time, accompanied by a deterministic offline replay engine that calculates the true counterfactual latency ground truth and calibrates the estimator coefficients.

---

## 2. Trading Pipeline Components

```
Market Data Generator (Deterministic)
             |
             v
+---------------------------+
| Feed Handler              | (Sequence validation, zero-copy arrival marking, binary streaming)
+---------------------------+
             |
             v
+---------------------------+
| Price Aggregator          | (Spread validation, tick alignment)
+---------------------------+
             |
             v
+===========================+
| COALESCER #1 (Pre-CLOB)   | (Per-symbol slot buffer, zero-alloc state accumulation)
+===========================+
             |
             v
+---------------------------+
| FX CLOB Matching Engine   | (Bid & Ask ladder per currency pair, synthetic work simulation)
+---------------------------+
             |
             v
+===========================+
| COALESCER #2 (Pre-Publish)| (Output book slot buffer)
+===========================+
             |
             v
+---------------------------+
| Publisher & Estimator     | (Online latency estimator, HdrHistogram recording, SSE broadcast)
+---------------------------+
             |
             v
      Downstream Clients
  (Web Dashboard, Logstash, ELK)
```

### 2.1 Multi-Currency Pair Model
Six standard FX pairs are supported with zero-allocation integer ID lookups (`0..5`):
- `EUR/USD` (base 1.0850, tick 0.0001)
- `GBP/USD` (base 1.2950, tick 0.0001)
- `USD/JPY` (base 154.50, tick 0.01)
- `USD/CHF` (base 0.8920, tick 0.0001)
- `AUD/USD` (base 0.6550, tick 0.0001)
- `EUR/GBP` (base 0.8380, tick 0.0001)

### 2.2 Dual Coalescing Stages
The architecture incorporates two independent coalescing points:
1. **Coalescer #1 (Pre-CLOB)**: Absorbs surges in incoming quote market data. When a producer exceeds CLOB throughput, updates for the same currency pair overwrite the slot.
2. **Coalescer #2 (Pre-Publish)**: Absorbs publication congestion when downstream consumers or network publication buffers are saturated.

Both coalescers expose independent and aggregated metrics:
- Stage 1 Coalescing Ratio & Discard Count
- Stage 2 Coalescing Ratio & Discard Count
- Global Pipeline Coalescing Ratio

---

## 3. Online Latency Estimator (Hot-Path)

### 3.1 Metadata Tracking
Rather than retaining discarded messages in expensive secondary queues, each coalescing slot maintains a primitive `CoalescingGroupState` struct:
- `count` ($N$): number of updates coalesced into the slot
- `oldestTimestampNs` ($T_{\text{oldest}}$)
- `newestTimestampNs` ($T_{\text{newest}}$)
- `sumTimestampNs` ($\sum T_i$)

### 3.2 Real-Time Online Latency Formulas
When a surviving update is published at time $T_{\text{pub}}$:
$$L_{\text{oldest}} = T_{\text{pub}} - T_{\text{oldest}}$$
$$L_{\text{newest}} = T_{\text{pub}} - T_{\text{newest}}$$
$$L_{\text{avg}} = T_{\text{pub}} - \frac{\sum T_i}{N}$$

### 3.3 Calibrated Estimation Model
The online estimator evaluates the expected hidden latency $\hat{L}$:
- **Identity Model**:
  $$\hat{L} = L_{\text{avg}}$$
- **Multivariate Linear Calibrated Model**:
  $$\hat{L} = w_0 + w_1 \cdot L_{\text{avg}} + w_2 \cdot N + w_3 \cdot L_{\text{oldest}} + w_4 \cdot L_{\text{newest}}$$

### 3.4 Coalescing Pressure Metric
$$\text{Pressure} = \frac{\hat{L}}{\text{TargetLatency}}$$
- `HEALTHY`: $\text{Pressure} < 1.0$
- `WARNING`: $1.0 \le \text{Pressure} < 2.0$
- `CRITICAL`: $\text{Pressure} \ge 2.0$

---

## 4. Deterministic Replay & Calibration Engine

1. **Binary Stream Format**: 64-byte aligned frames recording raw updates (`sequenceId`, `timestampNs`, `pairId`, `bid`, `ask`, `bidSize`, `askSize`).
2. **Scenario A (Coalescing ON)**: Processes stream with coalescing enabled, recording online estimates $\hat{L}_i$.
3. **Scenario B (Coalescing OFF)**: Replays identical raw stream with coalescing disabled. Every message is processed and delivered; its true counterfactual latency $L_{\text{true}, i} = T_{\text{pub, OFF}} - T_{\text{input}}$ represents the empirical ground truth.
4. **OLS Model Solver**: Fits model weights $W$ by solving $(X^T X + \lambda I) W = X^T Y$, computing Mean Absolute Error (MAE), Bias, and Pearson Correlation.
5. **Dynamic Hot-Reloading**: Exports `calibration.json` and updates the live estimator atomically via REST API.
