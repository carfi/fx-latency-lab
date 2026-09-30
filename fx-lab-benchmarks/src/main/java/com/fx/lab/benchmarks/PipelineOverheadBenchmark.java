package com.fx.lab.benchmarks;

import com.fx.lab.core.estimator.CalibrationConfig;
import com.fx.lab.core.estimator.OnlineLatencyEstimator;
import com.fx.lab.core.model.CurrencyPair;
import com.fx.lab.core.model.MarketDataUpdate;
import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class PipelineOverheadBenchmark {

    private OnlineLatencyEstimator identityEstimator;
    private OnlineLatencyEstimator multivariateEstimator;
    private MarketDataUpdate update;
    private long publishTimestamp;

    @Setup(Level.Trial)
    public void setup() {
        identityEstimator = new OnlineLatencyEstimator(CalibrationConfig.defaultIdentity());

        CalibrationConfig cal = new CalibrationConfig();
        cal.setModelType("LINEAR_MULTIVARIATE");
        cal.setIntercept(100.0);
        cal.setAvgHiddenLatencyWeight(0.95);
        cal.setDepthWeight(20.0);
        cal.setOldestHiddenLatencyWeight(0.05);
        multivariateEstimator = new OnlineLatencyEstimator(cal);

        update = new MarketDataUpdate(
                1001L,
                1_000_000L,
                CurrencyPair.EUR_USD,
                1.0850,
                1.0852,
                1_000_000,
                1_000_000
        );
        update.setCoalescingDepth(5);
        update.setOldestCoalescedTimestampNs(950_000L);
        update.setNewestCoalescedTimestampNs(990_000L);
        update.setSumCoalescedTimestampNs(4_850_000L);

        publishTimestamp = 1_050_000L;
    }

    @Benchmark
    public long baselineDirectLatency() {
        return publishTimestamp - update.getTimestampNs();
    }

    @Benchmark
    public long identityEstimatorEvaluation() {
        return identityEstimator.estimateExpectedHiddenLatencyNs(
                publishTimestamp,
                update.getOldestCoalescedTimestampNs(),
                update.getNewestCoalescedTimestampNs(),
                update.getAverageCoalescedTimestampNs(),
                update.getCoalescingDepth()
        );
    }

    @Benchmark
    public long multivariateEstimatorEvaluation() {
        return multivariateEstimator.estimateExpectedHiddenLatencyNs(
                publishTimestamp,
                update.getOldestCoalescedTimestampNs(),
                update.getNewestCoalescedTimestampNs(),
                update.getAverageCoalescedTimestampNs(),
                update.getCoalescingDepth()
        );
    }
}
