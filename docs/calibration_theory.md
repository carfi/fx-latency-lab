# Calibration Theory & Counterfactual Latency Derivation

## 1. Problem Formulation

When coalescing is activated in an exchange matching engine or feed-handler, updates arriving while an earlier update is unconsumed are discarded or superseded. 

Consider a sequence of price updates arriving at timestamps $t_1, t_2, \dots, t_N$ for currency pair `EUR/USD`. Under coalescing:
- Only update $N$ is processed and published at timestamp $T_{\text{pub}}$.
- The traditional measured latency is:
  $$L_{\text{meas}} = T_{\text{pub}} - t_N$$

If the system was overloaded, $t_N - t_1$ could be hundreds of microseconds. Although $L_{\text{meas}}$ may be only $15 \mu s$, clients monitoring the market experienced up to $T_{\text{pub}} - t_1$ of delayed state representation.

---

## 2. Counterfactual Latency Definition

The **counterfactual latency** $L_{\text{cf}}(i)$ answers:
> *"What would the latency of update $i$ have been had coalescing not been enabled in the pipeline?"*

In this lab, the empirical counterfactual ground truth is computed deterministically:
1. The exact same market data stream is fed through the pipeline with coalescing disabled (`Coalescing OFF`).
2. Every message queues in FIFO buffers and is matched by the CLOB.
3. The true publication time $T_{\text{pub, OFF}}(i)$ is recorded.
4. The true counterfactual latency is:
   $$L_{\text{true}}(i) = T_{\text{pub, OFF}}(i) - t_i$$

---

## 3. Estimator Validation Statistics

To validate the online estimator against ground truth, we measure across all surviving events $i \in \{1 \dots M\}$:

### 3.1 Mean Estimator Bias
$$\text{Bias} = \frac{1}{M} \sum_{i=1}^M \left(\hat{L}_i - L_{\text{true}, i}\right)$$
A bias close to 0 indicates the estimator is neither systematically over-estimating nor under-estimating hidden queueing delays.

### 3.2 Mean Absolute Error (MAE)
$$\text{MAE} = \frac{1}{M} \sum_{i=1}^M |\hat{L}_i - L_{\text{true}, i}|$$

### 3.3 Percentile Errors
- **Median Absolute Error**: 50th percentile of absolute errors.
- **P95 Absolute Error**: 95th percentile of absolute errors.
- **P99 Absolute Error**: 99th percentile of absolute errors.

### 3.4 Pearson Correlation Coefficient
$$r = \frac{\sum (\hat{L}_i - \bar{\hat{L}})(L_{\text{true}, i} - \bar{L}_{\text{true}})}{\sqrt{\sum (\hat{L}_i - \bar{\hat{L}})^2 \sum (L_{\text{true}, i} - \bar{L}_{\text{true}})^2}}$$
A correlation $r > 0.95$ indicates that the estimator accurately tracks relative spikes in queueing latency.

---

## 4. Ordinary Least Squares (OLS) Multivariate Calibration

Given feature matrix $X \in \mathbb{R}^{M \times 4}$ where row $i$ is:
$$x_i = [1, L_{\text{avg}, i}, \text{depth}_i, L_{\text{oldest}, i}]$$
and target vector $y = L_{\text{true}}$, the optimal weights $W = [w_0, w_1, w_2, w_3]^T$ minimize the squared error loss:
$$\min_W ||X W - y||_2^2 + \lambda ||W||_2^2$$

The closed-form ridge regularized solution is:
$$W = (X^T X + \lambda I)^{-1} X^T y$$

The resulting weights are exported to `calibration.json` and hot-reloaded into the running trading engine.
