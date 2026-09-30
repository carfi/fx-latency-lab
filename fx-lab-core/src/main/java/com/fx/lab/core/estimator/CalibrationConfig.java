package com.fx.lab.core.estimator;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

/**
 * Configuration and coefficient container for the calibrated estimator model.
 * Serializable to / deserializable from calibration.json.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CalibrationConfig {
    private String modelType = "AVERAGE_IDENTITY"; // AVERAGE_IDENTITY, LINEAR_MULTIVARIATE, POLYNOMIAL_DEPTH
    private double intercept = 0.0;
    private double avgHiddenLatencyWeight = 1.0;
    private double depthWeight = 0.0;
    private double oldestHiddenLatencyWeight = 0.0;
    private double newestHiddenLatencyWeight = 0.0;
    private double depthSquaredWeight = 0.0;
    private double targetLatencyNanos = 50_000.0; // 50 microseconds default target

    // Calibration quality summary metrics from offline replay
    private double meanAbsoluteErrorNanos;
    private double medianAbsoluteErrorNanos;
    private double p95AbsoluteErrorNanos;
    private double p99AbsoluteErrorNanos;
    private double biasNanos;
    private double correlation;
    private double relativeError;
    private int sampleCount;
    private long calibratedTimestamp;

    public CalibrationConfig() {
    }

    public static CalibrationConfig defaultIdentity() {
        CalibrationConfig config = new CalibrationConfig();
        config.setModelType("AVERAGE_IDENTITY");
        config.setIntercept(0.0);
        config.setAvgHiddenLatencyWeight(1.0);
        config.setDepthWeight(0.0);
        config.setOldestHiddenLatencyWeight(0.0);
        config.setNewestHiddenLatencyWeight(0.0);
        config.setTargetLatencyNanos(50_000.0);
        return config;
    }

    // Getters and Setters
    public String getModelType() { return modelType; }
    public void setModelType(String modelType) { this.modelType = modelType; }

    public double getIntercept() { return intercept; }
    public void setIntercept(double intercept) { this.intercept = intercept; }

    public double getAvgHiddenLatencyWeight() { return avgHiddenLatencyWeight; }
    public void setAvgHiddenLatencyWeight(double avgHiddenLatencyWeight) { this.avgHiddenLatencyWeight = avgHiddenLatencyWeight; }

    public double getDepthWeight() { return depthWeight; }
    public void setDepthWeight(double depthWeight) { this.depthWeight = depthWeight; }

    public double getOldestHiddenLatencyWeight() { return oldestHiddenLatencyWeight; }
    public void setOldestHiddenLatencyWeight(double oldestHiddenLatencyWeight) { this.oldestHiddenLatencyWeight = oldestHiddenLatencyWeight; }

    public double getNewestHiddenLatencyWeight() { return newestHiddenLatencyWeight; }
    public void setNewestHiddenLatencyWeight(double newestHiddenLatencyWeight) { this.newestHiddenLatencyWeight = newestHiddenLatencyWeight; }

    public double getDepthSquaredWeight() { return depthSquaredWeight; }
    public void setDepthSquaredWeight(double depthSquaredWeight) { this.depthSquaredWeight = depthSquaredWeight; }

    public double getTargetLatencyNanos() { return targetLatencyNanos; }
    public void setTargetLatencyNanos(double targetLatencyNanos) { this.targetLatencyNanos = targetLatencyNanos; }

    public double getMeanAbsoluteErrorNanos() { return meanAbsoluteErrorNanos; }
    public void setMeanAbsoluteErrorNanos(double meanAbsoluteErrorNanos) { this.meanAbsoluteErrorNanos = meanAbsoluteErrorNanos; }

    public double getMedianAbsoluteErrorNanos() { return medianAbsoluteErrorNanos; }
    public void setMedianAbsoluteErrorNanos(double medianAbsoluteErrorNanos) { this.medianAbsoluteErrorNanos = medianAbsoluteErrorNanos; }

    public double getP95AbsoluteErrorNanos() { return p95AbsoluteErrorNanos; }
    public void setP95AbsoluteErrorNanos(double p95AbsoluteErrorNanos) { this.p95AbsoluteErrorNanos = p95AbsoluteErrorNanos; }

    public double getP99AbsoluteErrorNanos() { return p99AbsoluteErrorNanos; }
    public void setP99AbsoluteErrorNanos(double p99AbsoluteErrorNanos) { this.p99AbsoluteErrorNanos = p99AbsoluteErrorNanos; }

    public double getBiasNanos() { return biasNanos; }
    public void setBiasNanos(double biasNanos) { this.biasNanos = biasNanos; }

    public double getCorrelation() { return correlation; }
    public void setCorrelation(double correlation) { this.correlation = correlation; }

    public double getRelativeError() { return relativeError; }
    public void setRelativeError(double relativeError) { this.relativeError = relativeError; }

    public int getSampleCount() { return sampleCount; }
    public void setSampleCount(int sampleCount) { this.sampleCount = sampleCount; }

    public long getCalibratedTimestamp() { return calibratedTimestamp; }
    public void setCalibratedTimestamp(long calibratedTimestamp) { this.calibratedTimestamp = calibratedTimestamp; }
}
