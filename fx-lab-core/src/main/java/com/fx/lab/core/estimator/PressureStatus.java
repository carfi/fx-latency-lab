package com.fx.lab.core.estimator;

public enum PressureStatus {
    HEALTHY,
    WARNING,
    CRITICAL;

    public static PressureStatus fromRatio(double ratio) {
        if (ratio < 1.0) {
            return HEALTHY;
        } else if (ratio < 2.0) {
            return WARNING;
        } else {
            return CRITICAL;
        }
    }
}
