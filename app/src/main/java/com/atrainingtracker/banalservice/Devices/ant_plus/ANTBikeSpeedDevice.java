package com.atrainingtracker.banalservice.Devices.ant_plus;

import android.content.Context;

import com.atrainingtracker.banalservice.BANALService;
import com.atrainingtracker.banalservice.Devices.DeviceType;
import com.atrainingtracker.banalservice.Sensor.MyDoubleAccumulatorSensor;
import com.atrainingtracker.banalservice.Sensor.MySensor;
import com.atrainingtracker.banalservice.Sensor.MySensorManager;
import com.atrainingtracker.banalservice.Sensor.SensorType;
import com.atrainingtracker.banalservice.Sensor.ThresholdSensor;
import com.dsi.ant.plugins.antplus.pcc.AntPlusBikeSpeedDistancePcc;
import com.dsi.ant.plugins.antplus.pcc.AntPlusBikeSpeedDistancePcc.CalculatedAccumulatedDistanceReceiver;
import com.dsi.ant.plugins.antplus.pcc.AntPlusBikeSpeedDistancePcc.CalculatedSpeedReceiver;
import com.dsi.ant.plugins.antplus.pcc.defines.EventFlag;
import com.dsi.ant.plugins.antplus.pccbase.AntPluginPcc;
import com.dsi.ant.plugins.antplus.pccbase.PccReleaseHandle;

import java.math.BigDecimal;
import java.util.EnumSet;

/**
 * Bike Speed
 */
public class ANTBikeSpeedDevice extends MyANTDevice {
    protected static final int SPEED_THRESHOLD = 8;
    private static final String TAG = "ANTBikeSpeedDevice";
    private static final boolean DEBUG = BANALService.DEBUG & false;


    // everything is stored as int, so we do not have to take care about all the castings
    // protected int mEventTime;
    // protected int mRevolutionCount;

    // protected int mAccumulatedRevolutionCount;
    protected AntPlusBikeSpeedDistancePcc bikeSpeedDistancePcc = null;
    protected MySensor<BigDecimal> mSpeedSensor;
    protected MySensor<Double> mPaceSensor;
    protected MyDoubleAccumulatorSensor mDistanceSensor;
    protected MyDoubleAccumulatorSensor mLapDistanceSensor;

    // protected int mNrZeroSpeed;
    // protected static final int NR_ZERO_SPEED_THRESHOLD = 8; 

    /**
     * constructor
     */
    public ANTBikeSpeedDevice(Context context, MySensorManager mySensorManager, long deviceID, int antDeviceNumber) {
        super(context, mySensorManager, DeviceType.BIKE_SPEED, deviceID, antDeviceNumber);
        myLog("created ANTBikeSpeedDevice");
    }

    @Override
    protected void addSensors() {
        mSpeedSensor = new ThresholdSensor<BigDecimal>(this, SensorType.SPEED_mps, SPEED_THRESHOLD);
        mPaceSensor = new ThresholdSensor<Double>(this, SensorType.PACE_spm, SPEED_THRESHOLD);
        mDistanceSensor = new MyDoubleAccumulatorSensor(this, SensorType.DISTANCE_m);
        mLapDistanceSensor = new MyDoubleAccumulatorSensor(this, SensorType.DISTANCE_m_LAP);

        addSensor(mSpeedSensor);
        addSensor(mPaceSensor);
        addSensor(mDistanceSensor);
        addSensor(mLapDistanceSensor);
    }

    @Override
    protected void newLap() {
        if (mLapDistanceSensor != null) {
            mLapDistanceSensor.reset();
        }
    }

    @Override
    protected PccReleaseHandle requestAccess() {
        myLog("requestAccess()");

        return AntPlusBikeSpeedDistancePcc.requestAccess(mContext, getANTDeviceNumber(), 0, false, new MyResultReceiver<AntPlusBikeSpeedDistancePcc>(), new MyDeviceStateChangeReceiver());
    }


    @Override
    protected void setSpecificPcc(AntPluginPcc antPluginPcc) {
        bikeSpeedDistancePcc = (AntPlusBikeSpeedDistancePcc) antPluginPcc;
    }


    @Override
    protected void subscribeSpecificEvents() {
        if (bikeSpeedDistancePcc != null) {
            myLog("subscribing specific events");

            bikeSpeedDistancePcc.subscribeCalculatedSpeedEvent(new CalculatedSpeedReceiver(new BigDecimal(mCalibrationFactor)) {
                @Override
                public void onNewCalculatedSpeed(long estTimestamp, EnumSet<EventFlag> eventFlags, BigDecimal calculatedSpeed) {
                    myLog("onNewCalculatedSpeed:timeStamp=" + estTimestamp + ": " + calculatedSpeed.doubleValue());

                    mSpeedSensor.newValue(calculatedSpeed);
                    mPaceSensor.newValue(1 / calculatedSpeed.doubleValue());   // TODO: correct?
                }
            });

            bikeSpeedDistancePcc.subscribeCalculatedAccumulatedDistanceEvent(new CalculatedAccumulatedDistanceReceiver(new BigDecimal(mCalibrationFactor)) {
                @Override
                public void onNewCalculatedAccumulatedDistance(long estTimestamp, EnumSet<EventFlag> eventFlags, BigDecimal calculatedAccumulatedDistance) {
                    if (calculatedAccumulatedDistance != null) {
                        mDistanceSensor.newValue(calculatedAccumulatedDistance.doubleValue());
                        mLapDistanceSensor.newValue(calculatedAccumulatedDistance.doubleValue());
                    } else {
                        mDistanceSensor.newValue(0.0);
                        mLapDistanceSensor.newValue(0.0);

                    }
                }
            });
        }
    }

    @Override
    protected void subscribeCommonEvents() {
        onNewBikeSpdCadCommonPccFound(bikeSpeedDistancePcc);
    }

}
