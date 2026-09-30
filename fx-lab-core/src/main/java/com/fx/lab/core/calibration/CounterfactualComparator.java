package com.fx.lab.core.calibration;

import com.fx.lab.core.estimator.CalibrationConfig;
import com.fx.lab.core.pipeline.Publisher;

import java.util.*;

/**
 * Offline comparison engine:
 * Compares Coalescing ON (estimates) against Coalescing OFF (true counterfactual ground truth),
 * computes validation statistics, and fits calibrated model coefficients.
 */
public final class CounterfactualComparator {

    public static class RunRecord {
        public final long sequenceId;
        public final String pair;
        public final int depth;
        public final long originTimestampNs;
        public final long publishTimestampNs;
        public final long actualLatencyNs;
        public final long expectedHiddenNs;
        public final long oldestHiddenNs;
        public final long newestHiddenNs;
        public final long averageHiddenNs;

        public RunRecord(long sequenceId, String pair, int depth, long originTimestampNs,
                         long publishTimestampNs, long actualLatencyNs, long expectedHiddenNs,
                         long oldestHiddenNs, long newestHiddenNs, long averageHiddenNs) {
            this.sequenceId = sequenceId;
            this.pair = pair;
            this.depth = depth;
            this.originTimestampNs = originTimestampNs;
            this.publishTimestampNs = publishTimestampNs;
            this.actualLatencyNs = actualLatencyNs;
            this.expectedHiddenNs = expectedHiddenNs;
            this.oldestHiddenNs = oldestHiddenNs;
            this.newestHiddenNs = newestHiddenNs;
            this.averageHiddenNs = averageHiddenNs;
        }

        public static RunRecord fromPublished(Publisher.PublishedEvent event) {
            return new RunRecord(
                    event.bookUpdate().getSourceSequenceId(),
                    event.bookUpdate().getCurrencyPair().getSymbol(),
                    event.estimateResult().coalescingDepth(),
                    event.bookUpdate().getOriginTimestampNs(),
                    event.publishTimestampNs(),
                    event.actualLatencyNs(),
                    event.estimateResult().expectedHiddenLatencyNs(),
                    event.estimateResult().oldestHiddenLatencyNs(),
                    event.estimateResult().newestHiddenLatencyNs(),
                    event.estimateResult().averageHiddenLatencyNs()
            );
        }
    }

    public CounterfactualComparisonResult compare(List<RunRecord> onRecords, List<RunRecord> offRecords) {
        // Map OFF records by sequenceId (the ground truth run)
        Map<Long, RunRecord> offMap = new HashMap<>(offRecords.size());
        for (RunRecord off : offRecords) {
            offMap.put(off.sequenceId, off);
        }

        List<CounterfactualComparisonResult.SamplePair> samplePairs = new ArrayList<>();
        List<Double> absoluteErrors = new ArrayList<>();

        double sumEstimated = 0.0;
        double sumTrue = 0.0;
        double sumError = 0.0;
        double sumAbsError = 0.0;

        List<double[]> regressionX = new ArrayList<>();
        List<Double> regressionY = new ArrayList<>();

        for (RunRecord on : onRecords) {
            RunRecord off = offMap.get(on.sequenceId);
            if (off != null) {
                // True counterfactual latency: the true elapsed time for this update to be published in OFF mode
                long trueLatencyNs = off.actualLatencyNs;
                long estimatedNs = on.expectedHiddenNs;
                long errorNs = estimatedNs - trueLatencyNs;
                double absErr = Math.abs(errorNs);

                samplePairs.add(new CounterfactualComparisonResult.SamplePair(
                        on.sequenceId,
                        on.pair,
                        on.depth,
                        estimatedNs,
                        trueLatencyNs,
                        errorNs
                ));

                absoluteErrors.add(absErr);
                sumEstimated += estimatedNs;
                sumTrue += trueLatencyNs;
                sumError += errorNs;
                sumAbsError += absErr;

                // Regression features: [1.0 (intercept), avgHidden, depth, oldestHidden]
                regressionX.add(new double[]{
                        1.0,
                        (double) on.averageHiddenNs,
                        (double) on.depth,
                        (double) on.oldestHiddenNs
                });
                regressionY.add((double) trueLatencyNs);
            }
        }

        int n = samplePairs.size();
        if (n == 0) {
            return new CounterfactualComparisonResult(
                    onRecords.size(), offRecords.size(), 0,
                    0, 0, 0, 0, 0, 0, 0, 0, 0,
                    CalibrationConfig.defaultIdentity(), Collections.emptyList()
            );
        }

        Collections.sort(absoluteErrors);

        double meanEst = sumEstimated / n;
        double meanTrue = sumTrue / n;
        double bias = sumError / n;
        double mae = sumAbsError / n;
        double medianAe = absoluteErrors.get(n / 2);
        double p95Ae = absoluteErrors.get(Math.min(n - 1, (int) Math.round(n * 0.95)));
        double p99Ae = absoluteErrors.get(Math.min(n - 1, (int) Math.round(n * 0.99)));
        double relativeError = sumTrue > 0 ? (sumAbsError / sumTrue) * 100.0 : 0.0;

        // Pearson correlation
        double sumProduct = 0.0;
        double sumSqEst = 0.0;
        double sumSqTrue = 0.0;
        for (CounterfactualComparisonResult.SamplePair p : samplePairs) {
            double dEst = p.estimatedHiddenLatencyNs() - meanEst;
            double dTrue = p.trueCounterfactualLatencyNs() - meanTrue;
            sumProduct += dEst * dTrue;
            sumSqEst += dEst * dEst;
            sumSqTrue += dTrue * dTrue;
        }
        double correlation = (sumSqEst > 0 && sumSqTrue > 0)
                ? (sumProduct / (Math.sqrt(sumSqEst) * Math.sqrt(sumSqTrue)))
                : 1.0;

        // Fit OLS regression: Y = X * W
        double[] weights = solveOls(regressionX, regressionY);

        CalibrationConfig cal = new CalibrationConfig();
        cal.setModelType("LINEAR_MULTIVARIATE");
        cal.setIntercept(weights[0]);
        cal.setAvgHiddenLatencyWeight(weights[1]);
        cal.setDepthWeight(weights[2]);
        cal.setOldestHiddenLatencyWeight(weights[3]);
        cal.setTargetLatencyNanos(50_000.0);
        cal.setMeanAbsoluteErrorNanos(mae);
        cal.setMedianAbsoluteErrorNanos(medianAe);
        cal.setP95AbsoluteErrorNanos(p95Ae);
        cal.setP99AbsoluteErrorNanos(p99Ae);
        cal.setBiasNanos(bias);
        cal.setCorrelation(correlation);
        cal.setRelativeError(relativeError);
        cal.setSampleCount(n);
        cal.setCalibratedTimestamp(System.currentTimeMillis());

        return new CounterfactualComparisonResult(
                onRecords.size(),
                offRecords.size(),
                n,
                meanEst,
                meanTrue,
                bias,
                mae,
                medianAe,
                p95Ae,
                p99Ae,
                correlation,
                relativeError,
                cal,
                samplePairs
        );
    }

    /**
     * Solves normal equations (X^T * X + lambda * I) * W = X^T * Y using 4x4 matrix inversion.
     */
    private double[] solveOls(List<double[]> X, List<Double> Y) {
        int p = 4; // number of features
        double[][] xtx = new double[p][p];
        double[] xty = new double[p];

        for (int i = 0; i < X.size(); i++) {
            double[] x = X.get(i);
            double y = Y.get(i);
            for (int r = 0; r < p; r++) {
                xty[r] += x[r] * y;
                for (int c = 0; c < p; c++) {
                    xtx[r][c] += x[r] * x[c];
                }
            }
        }

        // Ridge regularization for numerical stability
        double lambda = 1e-4;
        for (int i = 0; i < p; i++) {
            xtx[i][i] += lambda;
        }

        // Invert 4x4 matrix via Gauss-Jordan elimination
        double[][] inv = invertMatrix(xtx);
        if (inv == null) {
            // Fallback to identity
            return new double[]{0.0, 1.0, 0.0, 0.0};
        }

        double[] w = new double[p];
        for (int r = 0; r < p; r++) {
            double sum = 0.0;
            for (int c = 0; c < p; c++) {
                sum += inv[r][c] * xty[c];
            }
            w[r] = sum;
        }

        return w;
    }

    private double[][] invertMatrix(double[][] a) {
        int n = a.length;
        double[][] augmented = new double[n][2 * n];
        for (int i = 0; i < n; i++) {
            System.arraycopy(a[i], 0, augmented[i], 0, n);
            augmented[i][i + n] = 1.0;
        }

        for (int i = 0; i < n; i++) {
            double pivot = augmented[i][i];
            if (Math.abs(pivot) < 1e-12) {
                // Find non-zero pivot row
                int swapRow = -1;
                for (int r = i + 1; r < n; r++) {
                    if (Math.abs(augmented[r][i]) > 1e-12) {
                        swapRow = r;
                        break;
                    }
                }
                if (swapRow == -1) return null; // Singular
                double[] temp = augmented[i];
                augmented[i] = augmented[swapRow];
                augmented[swapRow] = temp;
                pivot = augmented[i][i];
            }

            for (int j = 0; j < 2 * n; j++) {
                augmented[i][j] /= pivot;
            }

            for (int r = 0; r < n; r++) {
                if (r != i) {
                    double factor = augmented[r][i];
                    for (int j = 0; j < 2 * n; j++) {
                        augmented[r][j] -= factor * augmented[i][j];
                    }
                }
            }
        }

        double[][] result = new double[n][n];
        for (int i = 0; i < n; i++) {
            System.arraycopy(augmented[i], n, result[i], 0, n);
        }
        return result;
    }
}
