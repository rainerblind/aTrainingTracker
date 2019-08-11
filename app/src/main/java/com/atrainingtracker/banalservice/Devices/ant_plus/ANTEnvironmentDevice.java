package com.atrainingtracker.banalservice.Devices.ant_plus;

import android.content.Context;

import com.atrainingtracker.banalservice.BANALService;
import com.atrainingtracker.banalservice.Devices.DeviceType;
import com.atrainingtracker.banalservice.Sensor.MySensor;
import com.atrainingtracker.banalservice.Sensor.MySensorManager;
import com.atrainingtracker.banalservice.Sensor.SensorType;
import com.dsi.ant.plugins.antplus.pcc.AntPlusEnvironmentPcc;
import com.dsi.ant.plugins.antplus.pcc.AntPlusEnvironmentPcc.ITemperatureDataReceiver;
import com.dsi.ant.plugins.antplus.pcc.defines.EventFlag;
import com.dsi.ant.plugins.antplus.pccbase.AntPluginPcc;
import com.dsi.ant.plugins.antplus.pccbase.PccReleaseHandle;

import java.math.BigDecimal;
import java.util.EnumSet;

/**
 * A Bike Cadence ManagedSensor
 */
public class ANTEnvironmentDevice extends MyANTDevice {
    private static final String TAG = "ANTEnvironmentDevice";
    private static final boolean DEBUG = BANALService.DEBUG & false;

    protected AntPlusEnvironmentPcc mEnvironmentPcc = null;

    protected MySensor mTemperatureSensor;
    protected MySensor mMinTemperatureSensor;
    protected MySensor mMaxTemperatureSensor;

    /**
     * constructor
     **/
    public ANTEnvironmentDevice(Context context, MySensorManager mySensorManager, long deviceID, int antDeviceNumber) {
        super(context, mySensorManager, DeviceType.ENVIRONMENT, deviceID, antDeviceNumber);
    }

    @Override
    protected void addSensors() {
        mTemperatureSensor = new MySensor(this, SensorType.TEMPERATURE);
        mMinTemperatureSensor = new MySensor(this, SensorType.TEMPERATURE_MIN);
        mMaxTemperatureSensor = new MySensor(this, SensorType.TEMPERATURE_MAX);

        addSensor(mTemperatureSensor);
        addSensor(mMinTemperatureSensor);
        addSensor(mMaxTemperatureSensor);
    }


    @Override
    protected void setSpecificPcc(AntPluginPcc antPluginPcc) {
        mEnvironmentPcc = (AntPlusEnvironmentPcc) antPluginPcc;
    }


    @Override
    protected void subscribeSpecificEvents() {
        if (mEnvironmentPcc != null) {
            mEnvironmentPcc.subscribeTemperatureDataEvent(new ITemperatureDataReceiver() {

                @Override
                public void onNewTemperatureData(long estTimestamp,
                                                 EnumSet<EventFlag> eventFlags,
                                                 BigDecimal currentTemperature,
                                                 long eventCount,
                                                 BigDecimal lowLast24Hours,
                                                 BigDecimal highLast24Hours) {
                    mTemperatureSensor.newValue(currentTemperature);
                    mMinTemperatureSensor.newValue(lowLast24Hours);
                    mMaxTemperatureSensor.newValue(highLast24Hours);
                }
            });
        }
    }


    @Override
    protected void subscribeCommonEvents() {
        onNewCommonPccFound(mEnvironmentPcc);
    }


    @Override
    protected PccReleaseHandle requestAccess() {
        return AntPlusEnvironmentPcc.requestAccess(mContext, getANTDeviceNumber(), 0, new MyResultReceiver<AntPlusEnvironmentPcc>(), new MyDeviceStateChangeReceiver());
    }

}
