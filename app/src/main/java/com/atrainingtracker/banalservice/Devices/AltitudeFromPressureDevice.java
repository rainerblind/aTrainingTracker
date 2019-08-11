package com.atrainingtracker.banalservice.Devices;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

import com.atrainingtracker.R;
import com.atrainingtracker.banalservice.BANALService;
import com.atrainingtracker.banalservice.Sensor.MySensor;
import com.atrainingtracker.banalservice.Sensor.MySensorManager;
import com.atrainingtracker.banalservice.Sensor.SensorType;
import com.atrainingtracker.trainingtracker.database.KnownLocationsDatabaseManager;
import com.google.android.gms.maps.model.LatLng;

// TODO: use database or preferences to store whether or not the pressure sensor is available.  really necessary??

public class AltitudeFromPressureDevice extends MyDevice
        implements SensorEventListener {
    public static final String ALTITUDE_CORRECTION_VALUE = "com.atrainingtracker.banalservice.Devices.AltitudeFromPressureDevice.ALTITUDE_CORRECTION_VALUE";
    public static final String ALTITUDE_CORRECTION_INTENT = "com.atrainingtracker.banalservice.Devices.AltitudeFromPressureDevice.ALTITUDE_CORRECTION_INTENT";
    protected static final double MY_PRESSURE_STANDARD_ATMOSPHERE = 1013.25;
    private static final boolean DEBUG = BANALService.DEBUG & false;
    protected final IntentFilter mGPSProviderEnabledFilter = new IntentFilter(BANALService.LOCATION_AVAILABLE_INTENT);
    protected MySensor<Number> mAltitudeSensor;

    protected Sensor mPressureSensor;
    protected boolean mPreassureSensorRegistered = false;
    private String TAG = "AltitudeFromPressureDevice";
    private double mAltitudeCorrection = 0;
    private boolean mPressureSensorInitialized = false;
    private final BroadcastReceiver mGPSProviderEnabledReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            AltitudeFromPressureDevice.this.initPressureSensor();
        }
    };

    public AltitudeFromPressureDevice(Context context, MySensorManager mySensorManager) {
        super(context, mySensorManager, DeviceType.ALTITUDE_FROM_PRESSURE);

        SensorManager sensorManager = (SensorManager) mContext.getSystemService(Context.SENSOR_SERVICE);
        mPressureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE);
        if (mPressureSensor != null) {
            if (DEBUG) Log.d(TAG, "Woho, we have a pressure sensor!");
            sensorManager.registerListener(this, mPressureSensor, SensorManager.SENSOR_DELAY_NORMAL);
        }

        context.registerReceiver(mGPSProviderEnabledReceiver, mGPSProviderEnabledFilter);
    }

    @Override
    public String getName() {
        return mContext.getString(R.string.AltitudeFromPressureDevice_name);
    }

    @Override
    protected void addSensors() {
        if (DEBUG) Log.d(TAG, "addSensors");

        mAltitudeSensor = new MySensor<Number>(this, SensorType.ALTITUDE);
    }

    @Override
    public void shutDown() {
        super.shutDown();
        ((SensorManager) mContext.getSystemService(Context.SENSOR_SERVICE)).unregisterListener(this);

        mContext.unregisterReceiver(mGPSProviderEnabledReceiver);
    }


    /**
     * assumes that GPS and Pressure Sensor are ready
     */
    private void initPressureSensor() {
        if (DEBUG) Log.d(TAG, "initPreassureSensor");

        if (mMySensorManager.getSensor(SensorType.LATITUDE) != null
                && mMySensorManager.getSensor(SensorType.LONGITUDE) != null
                && mMySensorManager.getSensor(SensorType.LATITUDE).getValue() != null
                && mMySensorManager.getSensor(SensorType.LONGITUDE).getValue() != null
                && mAltitudeSensor != null
                && mAltitudeSensor.getValue() != null) {

            mPressureSensorInitialized = true;

            double latitude = ((Number) mMySensorManager.getSensor(SensorType.LATITUDE).getValue()).doubleValue();
            double longitude = ((Number) mMySensorManager.getSensor(SensorType.LONGITUDE).getValue()).doubleValue();
            KnownLocationsDatabaseManager.MyLocation myLocation = KnownLocationsDatabaseManager.getMyLocation(new LatLng(latitude, longitude));

            if (myLocation != null) {
                setAltitudeCorrection(myLocation.altitude);
                mAltitudeSensor.newValue(myLocation.altitude);
            }
        }
    }


    /**
     * set the field mAltitudeCorrection
     */
    private void setAltitudeCorrection(int correctAltitude) {
        if (DEBUG) Log.d(TAG, "setAltitudeCorrection");

        mAltitudeCorrection = correctAltitude - mAltitudeSensor.getValue().doubleValue();

        if (mAltitudeCorrection != 0.0) {
            // 	also send broadcast to inform the others (like a tracker) of this change such that they can update all previous samples accordingly!
            Intent intent = new Intent(ALTITUDE_CORRECTION_INTENT);
            intent.putExtra(ALTITUDE_CORRECTION_VALUE, mAltitudeCorrection);
            mContext.sendBroadcast(intent);
        }
    }


    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        if (sensor.equals(mPressureSensor)) {
            if (DEBUG) Log.d(TAG, "Accuracy of pressure sensor changed");
            // TODO do something?
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (DEBUG) Log.d(TAG, "onSensorChanged");

        if (!mPreassureSensorRegistered) {
            mPreassureSensorRegistered = true;

            addSensor(mAltitudeSensor);
            registerSensors();
        }

        double altitude = SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, event.values[0]);
        //Log.d(TAG, "new altitude value: " + altitude);
        mAltitudeSensor.newValue(altitude + mAltitudeCorrection);
        if (!mPressureSensorInitialized) {
            initPressureSensor();
        }
    }

//    protected double getAltitude(double p0, double p)
//    {
//        return 44330 * (1-Math.pow((p/p0), 1/5.255));
//    }

}