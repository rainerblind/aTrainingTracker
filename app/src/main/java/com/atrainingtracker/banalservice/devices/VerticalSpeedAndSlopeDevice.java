package com.atrainingtracker.banalservice.devices;

import static java.lang.Math.abs;

import android.content.Context;

import com.atrainingtracker.banalservice.BANALService;
import com.atrainingtracker.banalservice.filters.FilterData;
import com.atrainingtracker.banalservice.filters.FilterType;
import com.atrainingtracker.banalservice.filters.FilteredSensorData;
import com.atrainingtracker.banalservice.sensor.MySensor;
import com.atrainingtracker.banalservice.sensor.MySensorManager;
import com.atrainingtracker.banalservice.sensor.SensorType;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class VerticalSpeedAndSlopeDevice extends MyDevice {


    private static final String TAG = "VerticalSpeedAndSlopeDevice";
    private static final Boolean DEBUG = true;

    private final ScheduledExecutorService mScheduler = Executors.newSingleThreadScheduledExecutor();

    private MySensor<Double> mVerticalSpeedSensor;
    private MySensor<Double> mSlopeSensor;

    private static final FilterData cAltitudeFilter = new FilterData(null, SensorType.ALTITUDE, FilterType.MOVING_AVERAGE_TIME, 5);
    private static final FilterData cSpeedFilter = new FilterData(null, SensorType.SPEED_mps, FilterType.MOVING_AVERAGE_TIME, 5);

    private Double mLastAltitude;

    private static final double MIN_SPEED = 0.01;  // min speed to calculate slope

    public VerticalSpeedAndSlopeDevice(Context context, MySensorManager mySensorManager) {
        super(context, mySensorManager, DeviceType.VERTICAL_SPEED_AND_SLOPE);

        BANALService.createFilter(cAltitudeFilter);
        BANALService.createFilter(cSpeedFilter);

        registerSensors();

        mScheduler.scheduleWithFixedDelay(this::calculateMetrics, 1, 1, TimeUnit.SECONDS);
    }

    @Override
    public String getName() {
        return "Vertical speed and slope";
    }

    @Override
    public void shutDown() {
        super.shutDown();

        if (mScheduler != null && !mScheduler.isShutdown()) {
            mScheduler.shutdown();
        }
    }

    @Override
    protected void addSensors() {

        mVerticalSpeedSensor = new MySensor<>(this, SensorType.VERTICAL_SPEED);
        mSlopeSensor = new MySensor<>(this, SensorType.SLOPE);

        addSensor(mVerticalSpeedSensor);
        addSensor(mSlopeSensor);
    }

    private void calculateMetrics() {
        // get current values
        FilteredSensorData<Double> altitudeFilteredSensorData = BANALService.getFilteredSensorData(cAltitudeFilter);
        FilteredSensorData<Double> speedFilteredSensorData = BANALService.getFilteredSensorData(cSpeedFilter);

        // check if we have all values
        if (altitudeFilteredSensorData == null || altitudeFilteredSensorData.getValue() == null
                || speedFilteredSensorData == null || speedFilteredSensorData.getValue() == null) {
            return;
        }

        // check if we already had a value for the altitude
        if (mLastAltitude == null) {
            mLastAltitude = altitudeFilteredSensorData.getValue();
            return;
        }

        // ok, now, we are ready to do all the calculations

        // first, calculate the vertical speed
        double deltaAltitude_mps = altitudeFilteredSensorData.getValue() - mLastAltitude;
        mVerticalSpeedSensor.newValue(deltaAltitude_mps * 60 * 60 /* convert m/s to m/h*/);

        // and store the current altitude for the next time
        mLastAltitude = altitudeFilteredSensorData.getValue();

        // next, calculate the slope
        double speed = speedFilteredSensorData.getValue();
        if (abs(speed) > MIN_SPEED) {
            mSlopeSensor.newValue(deltaAltitude_mps/speed);  // TODO: make this mathematically more correct by using arctan
        }
    }
}
