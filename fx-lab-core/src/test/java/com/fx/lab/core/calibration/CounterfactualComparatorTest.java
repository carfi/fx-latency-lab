package com.fx.lab.core.calibration;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CounterfactualComparatorTest {

    @Test
    void testOfflineCounterfactualComparisonAndOlsFitting() {
        CounterfactualComparator comparator = new CounterfactualComparator();

        List<CounterfactualComparator.RunRecord> onRecords = new ArrayList<>();
        List<CounterfactualComparator.RunRecord> offRecords = new ArrayList<>();

        // Generate synthetic matching pairs: trueLatency = 1.1 * avgHidden + 200 * depth
        for (long seq = 1; seq <= 50; seq++) {
            int depth = (int) (1 + (seq % 5));
            long inputTime = 1_000_000L + seq * 100_000L;
            long avgHidden = 20_000L + seq * 500L;
            long estimated = avgHidden; // Identity estimation
            long trueLatency = Math.round(1.1 * avgHidden + 200 * depth);

            onRecords.add(new CounterfactualComparator.RunRecord(
                    seq, "EUR/USD", depth, inputTime, inputTime + estimated,
                    5_000L, estimated, avgHidden + 5_000L, avgHidden - 5_000L, avgHidden
            ));

            offRecords.add(new CounterfactualComparator.RunRecord(
                    seq, "EUR/USD", 1, inputTime, inputTime + trueLatency,
                    trueLatency, trueLatency, trueLatency, trueLatency, trueLatency
            ));
        }

        CounterfactualComparisonResult result = comparator.compare(onRecords, offRecords);

        assertThat(result.matchedSamples()).isEqualTo(50);
        assertThat(result.meanAbsoluteErrorNs()).isGreaterThan(0.0);
        assertThat(result.pearsonCorrelation()).isGreaterThan(0.9);
        assertThat(result.recommendedCalibration()).isNotNull();
        assertThat(result.recommendedCalibration().getModelType()).isEqualTo("LINEAR_MULTIVARIATE");
        assertThat(result.recommendedCalibration().getAvgHiddenLatencyWeight()).isCloseTo(1.1, org.assertj.core.data.Offset.offset(0.2));
    }
}
