/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (C) 2011 - 2019 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see https://www.gnu.org/licenses/gpl-3.0
 */

package com.atrainingtracker.banalservice.sensor;

import android.content.Context;
import android.util.Log;

import com.atrainingtracker.banalservice.BANALService;
import com.atrainingtracker.banalservice.devices.DeviceType;
import com.atrainingtracker.banalservice.devices.MyDevice;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MySensorManager extends MyDevice {
    public static final String EMPTY_GC_DATA = "-----------";
    protected static final DeviceType[] SPEED_PRIORITY
            = new DeviceType[]{
            DeviceType.RUN_SPEED,
            DeviceType.BIKE_SPEED,
            DeviceType.BIKE_SPEED_AND_CADENCE,
            DeviceType.BIKE_POWER,
            // DeviceType.SPEED_AND_LOCATION_GOOGLE_FUSED,
            DeviceType.SPEED_AND_LOCATION_GPS,
            DeviceType.SPEED_AND_LOCATION_NETWORK
    };
    protected static final DeviceType[] CADENCE_PRIORITY
            = new DeviceType[]{
            DeviceType.RUN_SPEED, // Does this device always contain a cadence sensor???
            DeviceType.BIKE_CADENCE,
            DeviceType.BIKE_SPEED_AND_CADENCE,
            DeviceType.BIKE_POWER
    };
    protected static final DeviceType[] ALTITUDE_PRIORITY
            = new DeviceType[]{
            DeviceType.ALTITUDE_FROM_PRESSURE,
            DeviceType.SPEED_AND_LOCATION_GPS,
            DeviceType.SPEED_AND_LOCATION_GOOGLE_FUSED,
            DeviceType.SPEED_AND_LOCATION_NETWORK
    };
    protected static final DeviceType[] LOCATION_PRIORITY
            = new DeviceType[]{
            DeviceType.SPEED_AND_LOCATION_GPS,
            DeviceType.SPEED_AND_LOCATION_GOOGLE_FUSED,
            DeviceType.SPEED_AND_LOCATION_NETWORK
    };
    private static final String TAG = "MySensorManager";
    // String        mGCData            = EMPTY_GC_DATA;
    // protected int mAccumulatedSensors = 0; 
    private static final boolean DEBUG = BANALService.getDebug(false);
    protected Map<SensorType, Object> mInitialValues = new HashMap<SensorType, Object>();
    protected StringBuilder mAccumulatedGCData = new StringBuilder(EMPTY_GC_DATA);
    protected Set<SensorType> mAccumulatedSensorTypeSet = new HashSet<SensorType>();
    protected EnumMap<SensorType, LinkedList<DeviceType>> mDevicePriorityList
            = new EnumMap<SensorType, LinkedList<DeviceType>>(SensorType.class);

    protected EnumMap<SensorType, EnumMap<DeviceType, LinkedList<MySensor>>> mAllSensorsMapMap
            = new EnumMap<SensorType, EnumMap<DeviceType, LinkedList<MySensor>>>(SensorType.class);


    protected MySensor<String> mSensorsSensor;
    protected MySensor<String> mAccumulatedSensorsSensor;

    public MySensorManager(Context context) {
        super(context, null, DeviceType.SENSOR_MANAGER);
        this.mMySensorManager = this;

        setPriorityList(SensorType.SPEED_mps, SPEED_PRIORITY);
        setPriorityList(SensorType.PACE_spm, SPEED_PRIORITY);
        setPriorityList(SensorType.DISTANCE_m, SPEED_PRIORITY);
        setPriorityList(SensorType.DISTANCE_m_LAP, SPEED_PRIORITY);
        setPriorityList(SensorType.CADENCE, CADENCE_PRIORITY);
        setPriorityList(SensorType.ALTITUDE, ALTITUDE_PRIORITY);

        setPriorityList(SensorType.ACCURACY, LOCATION_PRIORITY);
        setPriorityList(SensorType.BEARING, LOCATION_PRIORITY);
        setPriorityList(SensorType.LATITUDE, LOCATION_PRIORITY);
        setPriorityList(SensorType.LONGITUDE, LOCATION_PRIORITY);

        mInitialValues.put(SensorType.LAP_NR, BANALService.INIT_LAP_NR);

        // TODO: what else do we have to do here?
    }

    @Override
    public String getName() {
        // return mContext.getString(R.string.MySensorManager_name);
        return null;
    }

    @Override
    protected void addSensors() {

        mSensorsSensor = new MySensor<String>(this, SensorType.SENSORS);
        mAccumulatedSensorsSensor = new MySensor<String>(this, SensorType.ACCUMULATED_SENSORS);

        addSensor(mSensorsSensor);
        addSensor(mAccumulatedSensorsSensor);
    }

    @Override
    public void shutDown() {
        super.shutDown();
    }

    // @Override
    // public boolean isPaired()
    // {
    //     return true;
    // }    

    public ArrayList<MySensor> getSensorList(Collection<SensorType> sensorCollection) {
        ArrayList<MySensor> sensorList = new ArrayList<MySensor>();

        for (SensorType sensorType : sensorCollection) {
            MySensor mySensor = getSensor(sensorType);
            if (mySensor != null) {
                sensorList.add(mySensor);
            }
        }

        return sensorList;
    }

    public Set<SensorType> getAccumulatedSensorTypeSet() {
        return mAccumulatedSensorTypeSet;
    }

    public void setPriorityList(SensorType sensorType, DeviceType[] deviceTypeArray) {
        mDevicePriorityList.put(sensorType, new LinkedList<DeviceType>(Arrays.asList(deviceTypeArray)));
    }

    protected LinkedList<DeviceType> getPriorityList(SensorType sensorType) {
        return mDevicePriorityList.get(sensorType);
    }

    public void registerSensor(MySensor mySensor) {
        if (DEBUG) {
            Log.d(TAG, "registerSensor(" + mySensor.getSensorType() + ")");
        }

        SensorType sensorType = mySensor.getSensorType();
        DeviceType deviceType = mySensor.getDevice().getDeviceType();

        boolean newSensor = false;
        if (getBestSensor(sensorType) == null) {
            addSensor(new ProxySensor(this, sensorType, mySensor));
            newSensor = true;
        }

        if (mAllSensorsMapMap.get(sensorType) == null) {
            mAllSensorsMapMap.put(sensorType, new EnumMap<DeviceType, LinkedList<MySensor>>(DeviceType.class));
        }
        if (mAllSensorsMapMap.get(sensorType).get(deviceType) == null) {
            mAllSensorsMapMap.get(sensorType).put(deviceType, new LinkedList<MySensor>());
        }

        mAllSensorsMapMap.get(sensorType).get(deviceType).addLast(mySensor);

        setBestSensorForProxySensor(sensorType);

        if (newSensor) notifyNewSensor(mySensor);
    }

    public List<MySensor> getAllButBestSensors() {
        LinkedList<MySensor> mySensorList = new LinkedList<>();

        for (SensorType sensorType : mAllSensorsMapMap.keySet()) {
            for (DeviceType deviceType : mAllSensorsMapMap.get(sensorType).keySet()) {
                mySensorList.addAll(mAllSensorsMapMap.get(sensorType).get(deviceType));
            }
        }

        return mySensorList;
    }

    public List<MySensor> getAllSensors() {
        LinkedList<MySensor> mySensorList = new LinkedList<>();

        mySensorList.addAll(getSensors());
        mySensorList.addAll(getAllButBestSensors());

        return mySensorList;
    }


    public void registerSensors(Collection<MySensor> sensorCollection) {
        if (DEBUG) Log.d(TAG, "registerSensors");

        for (MySensor mySensor : sensorCollection) {
            registerSensor(mySensor);
        }
    }

    public void unregisterSensor(MySensor mySensor) {
        if (DEBUG) Log.d(TAG, "unregisterSensor(" + mySensor.getSensorType() + ")");

        SensorType sensorType = mySensor.getSensorType();
        DeviceType deviceType = mySensor.getDevice().getDeviceType();

        if (mAllSensorsMapMap.get(sensorType) != null
                && mAllSensorsMapMap.get(sensorType).get(deviceType) != null) {

            boolean sensorRemoved = false;
            mAllSensorsMapMap.get(sensorType).get(deviceType).remove(mySensor);
            MySensor newBestSensor = getBestSensor(sensorType);
            if (newBestSensor == null) {
                if (DEBUG) Log.d(TAG, "newBestSensor == null => removeSensor");
                removeSensor(sensorType); // foo
                sensorRemoved = true;
            }

            setBestSensorForProxySensor(sensorType);

            if (sensorRemoved) notifySensorRemoved(mySensor);
        }

    }

    public void unregisterSensors(Collection<MySensor> sensorCollection) {
        for (MySensor mySensor : sensorCollection) {
            unregisterSensor(mySensor);
        }
    }


    protected void setBestSensorForProxySensor(SensorType sensorType) {
        if (DEBUG) Log.d(TAG, "setBestSensorForProxySensor(" + sensorType.name() + ")");

        MySensor bestSensor = getBestSensor(sensorType);
        if (bestSensor != null) {
            ((ProxySensor) getSensor(sensorType)).setSourceSensor(bestSensor);
        } else {
            removeSensor(sensorType);
        }
    }

    protected MySensor getBestSensor(SensorType sensorType) {
        MySensor bestSensor = null;

        Collection<DeviceType> priorityList = getPriorityList(sensorType);

        if (priorityList == null) {
            if (mAllSensorsMapMap.get(sensorType) == null) {
                return null;  // sensorType not yet seen, so there will be no corresponding sensor
            } else {
                priorityList = mAllSensorsMapMap.get(sensorType).keySet();
            }
        }

        for (DeviceType deviceType : priorityList) {
            bestSensor = getFirstSensor(sensorType, deviceType);
            if (bestSensor != null) {
                break;
            }
        }

        return bestSensor;
    }

    protected MySensor getFirstSensor(SensorType sensorType, DeviceType deviceType) {
        if (mAllSensorsMapMap.get(sensorType) != null
                && mAllSensorsMapMap.get(sensorType).get(deviceType) != null
                && mAllSensorsMapMap.get(sensorType).get(deviceType).size() != 0) {
            return mAllSensorsMapMap.get(sensorType).get(deviceType).getFirst();
        } else {
            return null;
        }
    }


    /**
     * called when there is a new Sensor
     */
    private void notifyNewSensor(MySensor mySensor) {
        if (DEBUG) Log.d(TAG, "notifyNewSensor(" + mySensor.mSensorType.name() + ")");

        mAccumulatedSensorTypeSet.add(mySensor.getSensorType());

        updateGCData();
        addGCSensor(mySensor.getSensorType(), mAccumulatedGCData);
        mAccumulatedSensorsSensor.newValue(mAccumulatedGCData.toString());

        // Intent intent =  new Intent(BANALService.NEW_SENSOR_INTENT);
        // intent.putExtra(BANALService.SENSOR_TYPE_NAME, mySensor.getSensorType().name());
        // intent.putExtra(BANALService.SENSOR_UNIT, mContext.getString(mySensor.getSensorType().getUnitId()));
        // mContext.sendBroadcast(intent.setPackage(getActivity().getPackageName()));
    }


    /**
     * called when a Sensor is removed
     */
    private void notifySensorRemoved(MySensor mySensor) {
        if (DEBUG) Log.d(TAG, "notifySensorRemoved");

        updateGCData();

        // Intent intent = new Intent(BANALService.SENSOR_REMOVED_INTENT);
        // intent.putExtra(BANALService.SENSOR_TYPE_NAME, mySensor.getSensorType().name());
        // mContext.sendBroadcast(intent.setPackage(getActivity().getPackageName()));
    }

    private void updateGCData() {
        if (DEBUG) Log.d(TAG, "updateGCData()");

        StringBuilder GCdata = new StringBuilder(EMPTY_GC_DATA);

        for (MySensor mySensor : getSensors()) {
            addGCSensor(mySensor.getSensorType(), GCdata);
        }
        mSensorsSensor.newValue(GCdata.toString());
        if (DEBUG) Log.d(TAG, "updatedGCData: " + GCdata);
    }

    private void addGCSensor(SensorType sensorType, StringBuilder gcDataString) {

        switch (sensorType) {
            case TIME_TOTAL:
                gcDataString.setCharAt(0, 'T');
                break;
            case DISTANCE_m:
                gcDataString.setCharAt(1, 'D');
                break;
            case SPEED_mps:
                gcDataString.setCharAt(2, 'S');
                break;
            case POWER:
                gcDataString.setCharAt(3, 'P');
                break;
            case HR:
                gcDataString.setCharAt(4, 'H');
                break;
            case CADENCE:
                gcDataString.setCharAt(5, 'C');
                break;
            case TORQUE:
                gcDataString.setCharAt(6, 'N');
                break;
            case ALTITUDE:
                gcDataString.setCharAt(7, 'A');
                break;
            case LONGITUDE:
            case LATITUDE:
                gcDataString.setCharAt(8, 'G');
                break;
        }
    }

    public Object getInitialValue(SensorType sensorType) {
        if (DEBUG)
            Log.d(TAG, "getInitialValue: " + sensorType + ": " + mInitialValues.get(sensorType));

        return mInitialValues.get(sensorType);
    }

    public void setInitialValue(SensorType key, Object value) {
        if (DEBUG) Log.d(TAG, "setting key:" + Object.class + " value" + value);

        mInitialValues.put(key, value);
    }
}
