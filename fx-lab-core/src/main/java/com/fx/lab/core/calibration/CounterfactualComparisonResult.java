package com.fx.lab.core.calibration;

import com.fx.lab.core.estimator.CalibrationConfig;

import java.util.List;

/**
 * Results of offline counterfactual comparison between Coalescing ON and OFF.
 */
public record CounterfactualComparisonResult(
        int totalOnPublished,
        int totalOffPublished,
        int matchedSamples,
        double meanEstimatedLatencyNs,
        double meanTrueCounterfactualLatencyNs,
        double estimatorBiasNs,
        double meanAbsoluteErrorNs,
        double medianAbsoluteErrorNs,
        double p95AbsoluteErrorNs,
        double p99AbsoluteErrorNs,
        double pearsonCorrelation,
        double relativeErrorPercent,
        CalibrationConfig recommendedCalibration,
        List<SamplePair> samplePairs
) {
    public record SamplePair(
            long sequenceId,
            String currencyPair,
            int depth,
            long estimatedHiddenLatencyNs,
            long trueCounterfactualLatencyNs,
            long errorNs
    ) {}
}
