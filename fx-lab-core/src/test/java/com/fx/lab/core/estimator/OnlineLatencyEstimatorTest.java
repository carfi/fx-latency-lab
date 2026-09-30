package com.fx.lab.core.estimator;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OnlineLatencyEstimatorTest {

    @Test
    void testIdentityEstimatorComputesCorrectAverages() {
        OnlineLatencyEstimator estimator = new OnlineLatencyEstimator();

        long pubTime = 10_000L;
        long oldestTime = 1_000L;
        long newestTime = 7_000L;
        long avgTime = 4_000L;
        int depth = 4;

        EstimateResult result = estimator.evaluate(pubTime, oldestTime, newestTime, avgTime, depth);

        assertThat(result.oldestHiddenLatencyNs()).isEqualTo(9_000L);
        assertThat(result.newestHiddenLatencyNs()).isEqualTo(3_000L);
        assertThat(result.averageHiddenLatencyNs()).isEqualTo(6_000L);
        assertThat(result.expectedHiddenLatencyNs()).isEqualTo(6_000L); // identity model
        assertThat(result.coalescingDepth()).isEqualTo(4);
    }

    @Test
    void testPressureStatusThresholds() {
        CalibrationConfig config = CalibrationConfig.defaultIdentity();
        config.setTargetLatencyNanos(10_000.0); // 10 microseconds target
        OnlineLatencyEstimator estimator = new OnlineLatencyEstimator(config);

        // Latency 5us / target 10us = 0.5 -> HEALTHY
        EstimateResult r1 = estimator.evaluate(15_000, 10_000, 10_000, 10_000, 1);
        assertThat(r1.pressureStatus()).isEqualTo(PressureStatus.HEALTHY);

        // Latency 15us / target 10us = 1.5 -> WARNING
        EstimateResult r2 = estimator.evaluate(25_000, 10_000, 10_000, 10_000, 2);
        assertThat(r2.pressureStatus()).isEqualTo(PressureStatus.WARNING);

        // Latency 30us / target 10us = 3.0 -> CRITICAL
        EstimateResult r3 = estimator.evaluate(40_000, 10_000, 10_000, 10_000, 5);
        assertThat(r3.pressureStatus()).isEqualTo(PressureStatus.CRITICAL);
    }

    @Test
    void testCalibratedModelCalculation() {
        CalibrationConfig config = new CalibrationConfig();
        config.setModelType("LINEAR_MULTIVARIATE");
        config.setIntercept(100.0);
        config.setAvgHiddenLatencyWeight(0.8);
        config.setDepthWeight(50.0);
        config.setOldestHiddenLatencyWeight(0.1);
        config.setTargetLatencyNanos(50_000.0);

        OnlineLatencyEstimator estimator = new OnlineLatencyEstimator(config);

        long pub = 10_000L;
        long oldest = 1_000L; // oldestHidden = 9,000
        long newest = 8_000L; // newestHidden = 2,000
        long avg = 5_000L;    // avgHidden = 5,000
        int depth = 3;

        // Expected = 100 + 0.8 * 5000 + 50 * 3 + 0.1 * 9000 = 100 + 4000 + 150 + 900 = 5150
        EstimateResult result = estimator.evaluate(pub, oldest, newest, avg, depth);
        assertThat(result.expectedHiddenLatencyNs()).isEqualTo(5150L);
    }
}
