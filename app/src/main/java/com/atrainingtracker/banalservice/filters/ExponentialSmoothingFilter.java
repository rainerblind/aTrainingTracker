package com.atrainingtracker.banalservice.filters;


import com.atrainingtracker.banalservice.BANALService;
import com.atrainingtracker.banalservice.Sensor.SensorType;

// simple filter for y[k+1] = \alpha m[k] + (1-\alpha) y[k]
public class ExponentialSmoothingFilter
        extends MyFilter<Number> {
    private static final boolean DEBUG = BANALService.DEBUG & false;
    private static final String TAG = ExponentialSmoothingFilter.class.getName();

    protected double mFilteredValue;
    protected double mAlpha;

    public ExponentialSmoothingFilter(String deviceName, SensorType sensorType, double alpha) {
        super(deviceName, sensorType);

        mAlpha = alpha;
    }

    @Override
    public double getFilterConstant() {
        return mAlpha;
    }

    @Override
    public FilterType getFilterType() {
        return FilterType.EXPONENTIAL_SMOOTHING;
    }


    @Override
    public synchronized Number getFilteredValue() {
        return mFilteredValue;
    }

    @Override
    public synchronized void newValue(Number value) {
        mFilteredValue = mAlpha * value.doubleValue() + (1 - mAlpha) * mFilteredValue;
    }
}
