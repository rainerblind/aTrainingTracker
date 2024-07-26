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

package com.atrainingtracker.banalservice.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Build;
import android.provider.BaseColumns;
import android.util.Log;

import com.atrainingtracker.R;
import com.atrainingtracker.banalservice.BANALService;
import com.atrainingtracker.banalservice.devices.BikePowerSensorsHelper;
import com.atrainingtracker.banalservice.devices.DeviceType;
import com.atrainingtracker.banalservice.devices.Manufacturer;
import com.atrainingtracker.banalservice.devices.bluetooth_le.BTLEBikePowerDevice;
import com.atrainingtracker.banalservice.Protocol;
import com.atrainingtracker.banalservice.sensor.SensorType;
import com.atrainingtracker.banalservice.helpers.BatteryStatusHelper;
import com.atrainingtracker.banalservice.helpers.HavePressureSensor;
import com.atrainingtracker.trainingtracker.TrainingApplication;
import com.dsi.ant.plugins.antplus.pcc.defines.BatteryStatus;

import java.text.DateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class DevicesDatabaseManager {
    private static final String TAG = DevicesDatabaseManager.class.getName();
    private static final boolean DEBUG = BANALService.getDebug(false);
    private static DevicesDatabaseManager cInstance;
    private static DevicesDbHelper cDevicesDbHelper;
    private int mOpenCounter;
    private SQLiteDatabase mDatabase;

    public static synchronized void initializeInstance(DevicesDbHelper devicesDbHelper) {
        if (cInstance == null) {
            cInstance = new DevicesDatabaseManager();
            cDevicesDbHelper = devicesDbHelper;
        }
    }

    public static synchronized DevicesDatabaseManager getInstance() {
        if (cInstance == null) {
            throw new IllegalStateException(DevicesDatabaseManager.class.getSimpleName() +
                    " is not initialized, call initializeInstance(..) method first.");
        }

        return cInstance;
    }

    public static boolean isANTDeviceInDB(DeviceType deviceType, int antDeviceNumber) {
        if (DEBUG) Log.d(TAG, "isANTDeviceInDB");
        boolean result = false;

        DevicesDatabaseManager databaseManager = DevicesDatabaseManager.getInstance();

        SQLiteDatabase db = databaseManager.getOpenDatabase();
        Cursor cursor = db.query(DevicesDbHelper.DEVICES,
                new String[]{DevicesDbHelper.C_ID},
                DevicesDbHelper.PROTOCOL + "=? AND " + DevicesDbHelper.DEVICE_TYPE + "=? AND " + DevicesDbHelper.ANT_DEVICE_NUMBER + "=?",
                new String[]{Protocol.ANT_PLUS.name(), deviceType.name(), Integer.toString(antDeviceNumber)},
                null,
                null,
                null);
        // device already known
        result = cursor.getCount() > 0;

        databaseManager.closeDatabase(); // db.close();
        cursor.close();

        return result;
    }

    public static boolean haveTemperatureDevice(Context context) {
        if (DEBUG) Log.i(TAG, "haveTemperatureDevice");
        if (context == null) Log.d(TAG, "WTF: context == null");

        boolean result = false;

        DevicesDatabaseManager databaseManager = DevicesDatabaseManager.getInstance();
        SQLiteDatabase db = databaseManager.getOpenDatabase();
        Cursor cursor = db.query(DevicesDbHelper.DEVICES,
                new String[]{DevicesDbHelper.C_ID},
                DevicesDbHelper.DEVICE_TYPE + "=?",
                new String[]{DeviceType.ENVIRONMENT.name()},
                null,
                null,
                null);
        // there is at least one environment/temperature device
        result = cursor.getCount() > 0;

        databaseManager.closeDatabase(); // db.close();
        cursor.close();

        return result;
    }

    public static long getDeviceId(DeviceType deviceType, int antDeviceNumber) {
        if (DEBUG) Log.d(TAG, "getDeviceId");

        long deviceId = -1;

        DevicesDatabaseManager databaseManager = DevicesDatabaseManager.getInstance();
        SQLiteDatabase db = databaseManager.getOpenDatabase();
        Cursor cursor = db.query(DevicesDbHelper.DEVICES,
                new String[]{DevicesDbHelper.C_ID},
                DevicesDbHelper.PROTOCOL + "=? AND " + DevicesDbHelper.DEVICE_TYPE + "=? AND " + DevicesDbHelper.ANT_DEVICE_NUMBER + "=?",
                new String[]{Protocol.ANT_PLUS.name(), deviceType.name(), Integer.toString(antDeviceNumber)},
                null,
                null,
                null);
        if (cursor.getCount() > 0) {
            // device already known
            cursor.moveToFirst();
            deviceId = cursor.getLong(cursor.getColumnIndex(DevicesDbHelper.C_ID));
        }

        databaseManager.closeDatabase(); //.close();
        cursor.close();

        return deviceId;
    }

    public static boolean isBluetoothDeviceInDB(DeviceType deviceType, String bluetooth_address) {
        if (DEBUG) Log.d(TAG, "isBluetoothDeviceInDB");
        boolean result = false;

        DevicesDatabaseManager databaseManager = DevicesDatabaseManager.getInstance();
        SQLiteDatabase db = databaseManager.getOpenDatabase();
        Cursor cursor = db.query(DevicesDbHelper.DEVICES,
                new String[]{DevicesDbHelper.C_ID},
                DevicesDbHelper.PROTOCOL + "=? AND " + DevicesDbHelper.DEVICE_TYPE + "=? AND " + DevicesDbHelper.BT_ADDRESS + "=?",
                new String[]{Protocol.BLUETOOTH_LE.name(), deviceType.name(), bluetooth_address},
                null,
                null,
                null);
        // device already known
        result = cursor.getCount() > 0;

        databaseManager.closeDatabase(); // db.close();
        cursor.close();

        return result;
    }

    public static long getDeviceId(DeviceType deviceType, String bluetooth_address) {
        if (DEBUG) Log.d(TAG, "getDeviceId");
        long result = -1;

        DevicesDatabaseManager databaseManager = DevicesDatabaseManager.getInstance();
        SQLiteDatabase db = databaseManager.getOpenDatabase();
        Cursor cursor = db.query(DevicesDbHelper.DEVICES,
                new String[]{DevicesDbHelper.C_ID},
                DevicesDbHelper.PROTOCOL + "=? AND " + DevicesDbHelper.DEVICE_TYPE + "=? AND " + DevicesDbHelper.BT_ADDRESS + "=?",
                new String[]{Protocol.BLUETOOTH_LE.name(), deviceType.name(), bluetooth_address},
                null,
                null,
                null);
        if (cursor.moveToFirst()) {
            result = cursor.getLong(cursor.getColumnIndex(DevicesDbHelper.C_ID));
        }

        databaseManager.closeDatabase(); // db.close();
        cursor.close();

        return result;
    }

    public static DeviceIdAndNameLists getDeviceIdAndNameLists(SensorType sensorType) {
        if (DEBUG) Log.i(TAG, "getDeviceIdAndNameLists(" + sensorType + ")");

        Set deviceTypeSet = DeviceType.getDeviceTypeList(sensorType);

        if (deviceTypeSet == null) {
            return null;
        }

        LinkedList<Long> deviceIdsList = new LinkedList<>();
        LinkedList<String> namesList = new LinkedList<>();

        SQLiteDatabase db = getInstance().getOpenDatabase();

        Cursor cursor = db.query(DevicesDbHelper.DEVICES,
                null,
                null,
                null,
                null,
                null,
                null);
        while (cursor.moveToNext()) {
            DeviceType deviceType = DeviceType.valueOf(cursor.getString(cursor.getColumnIndex(DevicesDbHelper.DEVICE_TYPE)));
            long deviceId = cursor.getLong(cursor.getColumnIndex(DevicesDbHelper.C_ID));

            if (deviceTypeSet.contains(deviceType)) {
                if (DEBUG) Log.i(TAG, "checking " + deviceType);

                if (deviceType == DeviceType.BIKE_POWER) {  // Ok, a Bike Power Sensor.  Here, we have to check the flags
                    int sensorFlags = DevicesDatabaseManager.getBikePowerSensorFlags(deviceId);
                    switch (sensorType) {
                        case CADENCE:
                            if (!BikePowerSensorsHelper.isCrankRevolutionDataSupported(sensorFlags)) {
                                continue;
                            }
                            break;

                        case DISTANCE_m:
                        case DISTANCE_m_LAP:
                            if (!(BikePowerSensorsHelper.isWheelDistanceDataSupported(sensorFlags)
                                    | BikePowerSensorsHelper.isWheelRevolutionDataSupported(sensorFlags))) {
                                continue;
                            }
                            break;

                        case PACE_spm:
                        case SPEED_mps:
                            if (!(BikePowerSensorsHelper.isWheelSpeedDataSupported(sensorFlags)
                                    | BikePowerSensorsHelper.isWheelRevolutionDataSupported(sensorFlags))) {
                                continue;
                            }
                            break;

                        case PEDAL_POWER_BALANCE:
                            if (!BikePowerSensorsHelper.isPowerBalanceSupported(sensorFlags)) {
                                continue;
                            }
                            break;

                        case PEDAL_SMOOTHNESS_L:
                        case PEDAL_SMOOTHNESS_R:
                        case PEDAL_SMOOTHNESS:
                            if (!BikePowerSensorsHelper.isPedalSmoothnessSupported(sensorFlags)) {
                                continue;
                            }
                            break;

                        case TORQUE:
                            if (!BikePowerSensorsHelper.isTorqueDataSupported(sensorFlags)) {
                                continue;
                            }
                            break;

                        case TORQUE_EFFECTIVENESS_L:
                        case TORQUE_EFFECTIVENESS_R:
                            if (!BikePowerSensorsHelper.isTorqueEffectivenessSupported(sensorFlags)) {
                                continue;
                            }
                            break;

                        case POWER:
                            break; // always add
                    }
                }

                // now, add the deviceId and the name of the device
                if (DEBUG)
                    Log.i(TAG, "adding deviceId: " + deviceId + ", name: " + cursor.getString(cursor.getColumnIndex(DevicesDbHelper.NAME)));
                deviceIdsList.add(deviceId);
                namesList.add(cursor.getString(cursor.getColumnIndex(DevicesDbHelper.NAME)));
            }
        }

        getInstance().closeDatabase();

        return new DeviceIdAndNameLists(deviceIdsList, namesList);
    }

    /**
     * @return the id of the device within the database or -1 if the device is already in the database or -2 when an SQLException was caught.
     */
    public static long insertNewAntDeviceIntoDB(DeviceType deviceType, String name, int antDeviceNumber, boolean paired, String manufacturer, int batteryPercentage) {
        if (DEBUG)
            Log.d(TAG, "insertNewAntDeviceIntoDB: " + deviceType + " " + name + " " + antDeviceNumber);

        long result = -1;

        DevicesDatabaseManager databaseManager = getInstance();
        SQLiteDatabase db = databaseManager.getOpenDatabase();

        // first, check whether device is already in database
        Cursor cursor = db.query(DevicesDbHelper.DEVICES,
                null,
                DevicesDbHelper.DEVICE_TYPE + "=? AND " + DevicesDbHelper.ANT_DEVICE_NUMBER + "=?",
                new String[]{deviceType.name(), Integer.toString(antDeviceNumber)},
                null,
                null,
                null);

        if (cursor.getCount() > 0) {
            // device already known
            // => do nothing
            if (DEBUG) Log.d(TAG, "initDB: device already known");
            result = -1;
        } else {  // device not yet known (that should be the case)
            try {
                ContentValues values = new ContentValues();
                values.put(DevicesDbHelper.PROTOCOL, Protocol.ANT_PLUS.name());
                values.put(DevicesDbHelper.ANT_DEVICE_NUMBER, antDeviceNumber);
                values.put(DevicesDbHelper.DEVICE_TYPE, deviceType.name());
                values.put(DevicesDbHelper.PAIRED, paired);
                values.put(DevicesDbHelper.NAME, name);
                values.put(DevicesDbHelper.MANUFACTURER_NAME, manufacturer);
                values.put(DevicesDbHelper.CALIBRATION_FACTOR, deviceType.getDefaultCalibrationFactor());
                values.put(DevicesDbHelper.LAST_ACTIVE, getLastActiveString());
                values.put(DevicesDbHelper.LAST_BATTERY_PERCENTAGE, batteryPercentage);

                result = db.insert(DevicesDbHelper.DEVICES, null, values);
            } catch (SQLException e) {
                Log.e(TAG, "Error while writing" + e);
                result = -2;
            }
        }

        databaseManager.closeDatabase(); // db.close();
        cursor.close();

        return result;
    }

    public static long insertNewBluetoothDeviceIntoDB(DeviceType deviceType,
                                                      String bluetoothMACAddress,
                                                      String name,
                                                      String manufacturer,
                                                      int batteryPercentage,
                                                      boolean paired) {
        if (DEBUG)
            Log.d(TAG, "insertNewBluetoothDeviceIntoDB: " + deviceType + " " + name + " " + bluetoothMACAddress);

        long result = -1;

        DevicesDatabaseManager databaseManager = getInstance();
        SQLiteDatabase db = databaseManager.getOpenDatabase();

        // first, check whether device is already in database
        Cursor cursor = db.query(DevicesDbHelper.DEVICES,
                null,
                DevicesDbHelper.DEVICE_TYPE + "=? AND " + DevicesDbHelper.BT_ADDRESS + "=?",
                new String[]{deviceType.name(), bluetoothMACAddress},
                null,
                null,
                null);

        if (cursor.getCount() > 0) {
            // device already known
            // => do nothing
            if (DEBUG) Log.d(TAG, "initDB: device already known");
            result = -1;
        } else {  // device not yet known (that should be the case)
            try {
                ContentValues values = new ContentValues();
                values.put(DevicesDbHelper.PROTOCOL, Protocol.BLUETOOTH_LE.name());
                values.put(DevicesDbHelper.BT_ADDRESS, bluetoothMACAddress);
                values.put(DevicesDbHelper.DEVICE_TYPE, deviceType.name());
                values.put(DevicesDbHelper.PAIRED, paired);
                values.put(DevicesDbHelper.NAME, name);
                values.put(DevicesDbHelper.MANUFACTURER_NAME, manufacturer);
                values.put(DevicesDbHelper.CALIBRATION_FACTOR, deviceType.getDefaultCalibrationFactor());
                values.put(DevicesDbHelper.LAST_ACTIVE, getLastActiveString());
                if (batteryPercentage >= 0) {
                    values.put(DevicesDbHelper.LAST_BATTERY_PERCENTAGE, batteryPercentage);
                }

                result = db.insert(DevicesDbHelper.DEVICES, null, values);
            } catch (SQLException e) {
                Log.e(TAG, "Error while writing" + e);
                result = -2;
            }
        }

        databaseManager.closeDatabase(); // db.close();
        cursor.close();

        return result;
    }

    /**
     * get the calibration factor from the database
     **/
    public static double getCalibrationFactor(long deviceID) {
        if (DEBUG) Log.d(TAG, "readCalibrationFactor");

        double calibrationFactor = 1;

        DevicesDatabaseManager databaseManager = getInstance();
        SQLiteDatabase db = databaseManager.getOpenDatabase();
        Cursor cursor = getCursor(db, deviceID, DevicesDbHelper.CALIBRATION_FACTOR);

        if (cursor.getCount() > 0) {
            cursor.moveToFirst();
            calibrationFactor = cursor.getDouble(0);
        } else {  // device not yet known (should never happen?) => get default calibration factor?
            if (DEBUG) Log.d(TAG, "readCalibrationFactor: device is not yet known");
            cursor = getCursor(db, deviceID, DevicesDbHelper.DEVICE_TYPE);
            if (cursor.getCount() > 0) {
                cursor.moveToFirst();
                DeviceType deviceType = DeviceType.valueOf(cursor.getString(0));
                calibrationFactor = deviceType.getDefaultCalibrationFactor();
            }
        }

        databaseManager.closeDatabase(); // db.close();
        cursor.close();

        return calibrationFactor;
    }

    public static boolean isPaired(long deviceID) {
        boolean result = false;

        DevicesDatabaseManager databaseManager = getInstance();
        SQLiteDatabase db = databaseManager.getOpenDatabase();
        Cursor cursor = getCursor(db, deviceID, DevicesDbHelper.PAIRED);
        if (cursor.getCount() > 0) {
            cursor.moveToFirst();
            result = cursor.getLong(0) > 0;
        }

        // TODO: which order???
        databaseManager.closeDatabase(); // db.close();
        cursor.close();

        return result;
    }

    public static DeviceType getDeviceType(long deviceId) {
        return DeviceType.valueOf(getString(deviceId, DevicesDbHelper.DEVICE_TYPE));
    }

    public static String getManufacturerName(long deviceId) {
        return getString(deviceId, DevicesDbHelper.MANUFACTURER_NAME);
    }

    public static String getDeviceName(long deviceId) {
        return getString(deviceId, DevicesDbHelper.NAME);
    }

    public static int getAntDeviceNumber(long deviceId) {
        return getInt(deviceId, DevicesDbHelper.ANT_DEVICE_NUMBER);
    }

    public static String getBluetoothMACAddress(long deviceId) {
        return getString(deviceId, DevicesDbHelper.BT_ADDRESS);
    }

    public static Protocol getProtocol(long deviceId) {
        return Protocol.valueOf(getString(deviceId, DevicesDbHelper.PROTOCOL));
    }

    public static void setManufacturerName(long deviceId, String manufacturerName) {
        setString(deviceId, DevicesDbHelper.MANUFACTURER_NAME, manufacturerName);
    }

    public static void setLastActive(long deviceId) {
        setString(deviceId, DevicesDbHelper.LAST_ACTIVE, getLastActiveString());
    }

    public static void setBatteryPercentage(long deviceId, int percentage) {
        setInt(deviceId, DevicesDbHelper.LAST_BATTERY_PERCENTAGE, percentage);
    }

    public static void putBikePowerSensorFlags(long deviceId, int sensorFlags) {
        if (DEBUG)
            Log.i(TAG, "putBikePowerSensorFlags: deviceId=" + deviceId + ", sensorFlags=" + sensorFlags);

        SQLiteDatabase db = getInstance().getOpenDatabase();

        ContentValues values = new ContentValues();
        values.put(DevicesDbHelper.DEVICE_ID, deviceId);
        values.put(DevicesDbHelper.BIKE_POWER_SENSOR_FLAGS, sensorFlags);

        int updates = db.update(DevicesDbHelper.BIKE_POWER_SENSOR_FLAGS_TABLE,
                values,
                DevicesDbHelper.DEVICE_ID + "=?",
                new String[]{deviceId + ""});

        if (updates == 0) { // not known yet
            db.insert(DevicesDbHelper.BIKE_POWER_SENSOR_FLAGS_TABLE,
                    null,
                    values);
        }

        getInstance().closeDatabase();
    }

    public static int getBikePowerSensorFlags(long deviceId) {
        if (DEBUG) Log.i(TAG, "getBikePowerSensorFlags: deviceId=" + deviceId);

        int result = 0;

        SQLiteDatabase db = getInstance().getOpenDatabase();
        Cursor cursor = db.query(DevicesDbHelper.BIKE_POWER_SENSOR_FLAGS_TABLE,
                null,
                DevicesDbHelper.DEVICE_ID + "=?",
                new String[]{deviceId + ""},
                null, null, null);
        if (cursor.moveToFirst()) {
            result = cursor.getInt(cursor.getColumnIndex(DevicesDbHelper.BIKE_POWER_SENSOR_FLAGS));
        }
        cursor.close();

        getInstance().closeDatabase();

        return result;
    }

    private static String getString(long deviceID, String key) {
        if (DEBUG) Log.i(TAG, "getString: deviceID=" + deviceID + ", key=" + key);

        // TODO: list with allowed/valid keys
        String result = null;

        DevicesDatabaseManager databaseManager = getInstance();
        SQLiteDatabase db = databaseManager.getOpenDatabase();
        Cursor cursor = getCursor(db, deviceID, key);
        if (cursor.getCount() > 0) {
            cursor.moveToFirst();
            result = cursor.getString(0);
        }
        // TODO: which order???
        cursor.close();
        databaseManager.closeDatabase(); // db.close();

        return result;
    }

    private static int getInt(long deviceID, String key) {
        if (DEBUG) Log.i(TAG, "getInt: deviceID=" + deviceID + ", key=" + key);

        int result = 0;

        // TODO: check list with allowed/valid keys?

        DevicesDatabaseManager databaseManager = getInstance();
        SQLiteDatabase db = databaseManager.getOpenDatabase();
        Cursor cursor = getCursor(db, deviceID, key);
        if (cursor.getCount() > 0) {
            cursor.moveToFirst();
            result = cursor.getInt(0);
        }
        // TODO: which order???
        cursor.close();
        databaseManager.closeDatabase(); // db.close();

        return result;
    }

    public static List<NameAndBatteryPercentage> getCriticalBatteryDevices(int batteryPercentage) {
        LinkedList<NameAndBatteryPercentage> result = new LinkedList<>();

        SQLiteDatabase db = getInstance().getOpenDatabase();
        Cursor cursor = db.query(DevicesDbHelper.DEVICES,
                null,
                DevicesDbHelper.LAST_BATTERY_PERCENTAGE + " >= 0 AND " + DevicesDbHelper.LAST_BATTERY_PERCENTAGE + " <= ?",
                new String[]{batteryPercentage + ""},
                null, null, null);
        while (cursor.moveToNext()) {
            result.add(new NameAndBatteryPercentage(cursor.getString(cursor.getColumnIndex(DevicesDbHelper.NAME)),
                    cursor.getInt(cursor.getColumnIndex(DevicesDbHelper.LAST_BATTERY_PERCENTAGE)),
                    cursor.getLong(cursor.getColumnIndex(DevicesDbHelper.C_ID))));
        }

        getInstance().closeDatabase();

        return result;
    }

    private static void setString(long deviceID, String key, String value) {
        if (DEBUG)
            Log.i(TAG, "setString: deviceID=" + deviceID + ", key=" + key + ", value=" + value);

        // TODO: list with allowed keys?

        ContentValues values = new ContentValues();
        values.put(key, value);

        DevicesDatabaseManager databaseManager = getInstance();
        SQLiteDatabase db = databaseManager.getOpenDatabase();
        int updates = db.update(DevicesDbHelper.DEVICES,
                values,
                DevicesDbHelper.C_ID + "=?",
                new String[]{Long.toString(deviceID)});
        databaseManager.closeDatabase();

        if (updates == 0) { // not known yet
            if (DEBUG) Log.d(TAG, "set: there should already be an entry in the database");
        }
    }

    private static void setInt(long deviceID, String key, int value) {
        if (DEBUG) Log.i(TAG, "setInt: deviceID=" + deviceID + ", key=" + key + ", value=" + value);

        // check list with allowed keys
        if (DevicesDbHelper.LAST_BATTERY_PERCENTAGE.equals(key)) {

            ContentValues values = new ContentValues();
            values.put(key, value);

            DevicesDatabaseManager databaseManager = getInstance();
            SQLiteDatabase db = databaseManager.getOpenDatabase();
            int updates = db.update(DevicesDbHelper.DEVICES,
                    values,
                    DevicesDbHelper.C_ID + "=?",
                    new String[]{Long.toString(deviceID)});
            databaseManager.closeDatabase();

            if (updates == 0) { // not known yet
                if (DEBUG) Log.d(TAG, "set: there should already be an entry in the database");
            }
        } else {
            if (DEBUG) Log.d(TAG, "setInt: called with wrong/unknown key: " + key);
        }
    }

    private static String getLastActiveString() {
        String currentTime = DateFormat.getDateTimeInstance().format(new Date());
        if (DEBUG) Log.i(TAG, "currentTime=" + currentTime);
        return currentTime;
        // return (new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)).format(new Date());  // TODO: localize?
    }

    protected static Cursor getCursor(SQLiteDatabase db, long deviceID, String column) {
        if (DEBUG) Log.d(TAG, "deviceID=" + deviceID + ", column=" + column);
        return db.query(DevicesDbHelper.DEVICES,
                new String[]{column},
                DevicesDbHelper.C_ID + "=?",
                new String[]{Long.toString(deviceID)},
                null,
                null,
                null);
    }

    public synchronized SQLiteDatabase getOpenDatabase() {
        mOpenCounter++;
        if (mOpenCounter == 1) {
            // Opening new database
            mDatabase = cDevicesDbHelper.getWritableDatabase();
        }
        return mDatabase;
    }

    public synchronized void closeDatabase() {
        mOpenCounter--;
        if (mOpenCounter == 0) {
            // Closing database
            mDatabase.close();

        }
    }

    public static class DeviceIdAndNameLists {
        public final LinkedList<Long> deviceIds;
        public final LinkedList<String> names;

        DeviceIdAndNameLists(LinkedList<Long> deviceIds, LinkedList<String> names) {
            this.deviceIds = deviceIds;
            this.names = names;
        }
    }

    public static class NameAndBatteryPercentage {
        public String name;
        public int batteryPercentage;
        public long deviceId;

        public NameAndBatteryPercentage(String name, int batteryPercentage, long deviceId) {
            this.name = name;
            this.batteryPercentage = batteryPercentage;
            this.deviceId = deviceId;
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // the databaseHelper itself
    ////////////////////////////////////////////////////////////////////////////////////////////////
    public static class DevicesDbHelper extends SQLiteOpenHelper {
        public static final String C_ID = BaseColumns._ID;
        // DB name
        public static final String DEVICES = "Devices" + 4;  // starting with version 4, we add the version number to the table
        public static final String PROTOCOL = "protocol";
        // ANT+ specific
        public static final String ANT_DEVICE_NUMBER = "deviceNumber";
        // Bluetooth specific
        public static final String BT_ADDRESS = "BT_adress";
        // General device info
        public static final String DEVICE_TYPE = "deviceType";
        public static final String PAIRED = "paired";
        public static final String NAME = "name";
        public static final String CALIBRATION_FACTOR = "calibrationFactor";
        public static final String LAST_ACTIVE = "lastActive";
        // new in (Version 4)
        public static final String MANUFACTURER_NAME = "manufacturerName";
        // public static final String ANT_C_WHERE_DEVICE      = ANT_DEVICE_NUMBER + "=? AND " + ANT_DEVICE_TYPE_BYTE + "=?";
        public static final String LAST_BATTERY_PERCENTAGE = "lastBatteryPercentage";
        // new in version 5
        @Deprecated
        public static final String BT_FEATURE = "btFeature";
        // new in version 6
        public static final String BIKE_POWER_SENSOR_FLAGS_TABLE = "bikePowerSensorFlagsTable";
        public static final String DEVICE_ID = "deviceId";
        public static final String BIKE_POWER_SENSOR_FLAGS = "bikePowerSensorFlags";
        static final String DB_NAME = "Devices.db";
        // static final int DB_VERSION = 4;
        // static final int DB_VERSION = 5;    // upgraded at 30.12.2016 to support storing of BT features
        // static final int DB_VERSION = 6;    // upgraded at 30.01.2018 to replace storing of BT features (for Bike Power) to all Bike Power Devices
        static final int DB_VERSION = 7;    // upgraded at 20.03.2018: added the location devices to this database
        private static final String TAG = "DeviceDbHelper";
        private static final boolean DEBUG = BANALService.getDebug(false);
        private static final String DEVICES_V3 = "Devices";               // in version 3, the table was simply called Devices
        @Deprecated
        private static final String TABLE_BT_SPECIFIC = "BTSpecific";
        // Strings to create the tables
        private static final String CREATE_DEVICE_DB_4 = "create table " + DEVICES + " ("
                + C_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + PROTOCOL + " text, "
                + ANT_DEVICE_NUMBER + " int, "
                + BT_ADDRESS + " text, "
                + DEVICE_TYPE + " text, "
                + PAIRED + " int, "
                + NAME + " text, "
                + MANUFACTURER_NAME + " text, "
                + CALIBRATION_FACTOR + " real,"
                + LAST_ACTIVE + " text,"
                + LAST_BATTERY_PERCENTAGE + " int)";
        @Deprecated
        private static final String CREATE_BT_SPECIFIC_5 = "create table " + TABLE_BT_SPECIFIC + " ("
                + C_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + BT_ADDRESS + " text, "
                + BT_FEATURE + " int)";
        private static final String CREATE_BIKE_POWER_SENSORS_TABLE_6 = "create table " + BIKE_POWER_SENSOR_FLAGS_TABLE + " ("
                + C_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + DEVICE_ID + " int, "
                + BIKE_POWER_SENSOR_FLAGS + " int)";
        // old (Version 3) stuff
        private static final String ANT_DEVICE_TYPE_BYTE = "deviceType";          // changed to general device type
        private static final String OWNER = "owner";               // removed
        private static final String MANUFACTURER_ID = "manufacturerID";      // changed to manufacturer string (in version 3, this is the manufacturer id as defined by ANT+)
        private static final String LAST_BATTERY_STATUS = "lastBatteryStatus";   // changed to battery percentage  (in version 3, this is an integer that represents the BatteryStatus as defined by the ANT+ plugin API)
        // old (Version 3)
        private static final String CREATE_DEVICE_DB_3 = "create table " + DEVICES_V3 + " ("
                //+ C_ID + " int primary key autoincrement, "
                + C_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + ANT_DEVICE_NUMBER + " int, "       // ANT+ specific
                + ANT_DEVICE_TYPE_BYTE + " int, "       // ANT+ specific
                + PAIRED + " int, "
                + NAME + " text, "
                + OWNER + " text, "      // not needed, remove
                + MANUFACTURER_ID + " int, "       // ANT+ specific, change to string (MANUFACTURER_NAME)
                + CALIBRATION_FACTOR + " real,"
                + LAST_ACTIVE + " text,"
                + LAST_BATTERY_STATUS + " int)";       // currently ANT+ specific, change to battery percentage as in bluetooth
        private final Context mContext;

        // Constructor
        public DevicesDbHelper(Context context) {
            super(context, DB_NAME, null, DB_VERSION);
            mContext = context;
        }

        // Called only once, first time the DB is created
        @Override
        public void onCreate(SQLiteDatabase db) {

            switch (DB_VERSION) {
                case 3:
                    db.execSQL(CREATE_DEVICE_DB_3);
                    if (DEBUG) Log.d(TAG, "onCreated sql: " + CREATE_DEVICE_DB_3);
                    break;

                case 4:
                    db.execSQL(CREATE_DEVICE_DB_4);
                    if (DEBUG) Log.d(TAG, "onCreated sql: " + CREATE_DEVICE_DB_4);
                    break;

                case 5:
                    db.execSQL(CREATE_DEVICE_DB_4);
                    if (DEBUG) Log.d(TAG, "onCreated sql: " + CREATE_DEVICE_DB_4);
                    db.execSQL(CREATE_BT_SPECIFIC_5);
                    if (DEBUG) Log.d(TAG, "onCreated sql: " + CREATE_BT_SPECIFIC_5);
                    break;

                case 6:
                    db.execSQL(CREATE_DEVICE_DB_4);
                    if (DEBUG) Log.d(TAG, "onCreated sql: " + CREATE_DEVICE_DB_4);
                    db.execSQL(CREATE_BIKE_POWER_SENSORS_TABLE_6);
                    if (DEBUG) Log.d(TAG, "onCreated sql: " + CREATE_BIKE_POWER_SENSORS_TABLE_6);
                    break;

                case 7:
                    db.execSQL(CREATE_DEVICE_DB_4);
                    if (DEBUG) Log.d(TAG, "onCreated sql: " + CREATE_DEVICE_DB_4);
                    db.execSQL(CREATE_BIKE_POWER_SENSORS_TABLE_6);
                    if (DEBUG) Log.d(TAG, "onCreated sql: " + CREATE_BIKE_POWER_SENSORS_TABLE_6);

                    addLocationDevicesToDb(db);
                    break;
            }

        }

        //Called whenever newVersion != oldVersion
        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            Log.i(TAG, "onUpgrade: oldVersion=" + oldVersion + ", newVersion=" + newVersion);

            if (oldVersion <= 3) {
                Log.i(TAG, "onUpgrade: upgrading from Version 3");

                // first, create the new table (might be problematic when we update more than one version!)
                onCreate(db);

                // copy the old data to the new one
                // thereby, change the manufacturer, battery status, and device type to the new format
                Cursor cursor = db.query(DEVICES_V3, null, null, null, null, null, null);
                ContentValues contentValues = new ContentValues();
                while (cursor.moveToNext()) {

                    contentValues.clear();

                    String name = cursor.getString(cursor.getColumnIndex(NAME));
                    contentValues.put(NAME, name);
                    Log.i(TAG, "onUpgrade: upgrade device: " + name);

                    contentValues.put(PROTOCOL, Protocol.ANT_PLUS.name());
                    contentValues.put(ANT_DEVICE_NUMBER, cursor.getInt(cursor.getColumnIndex(ANT_DEVICE_NUMBER)));
                    contentValues.put(PAIRED, cursor.getInt(cursor.getColumnIndex(PAIRED)));
                    contentValues.put(CALIBRATION_FACTOR, cursor.getDouble(cursor.getColumnIndex(CALIBRATION_FACTOR)));
                    contentValues.put(LAST_ACTIVE, cursor.getString(cursor.getColumnIndex(LAST_ACTIVE)));

                    // get manufacturer name from ANT+ id
                    int manufacturerId = cursor.getInt(cursor.getColumnIndex(MANUFACTURER_ID));
                    String manufacturerName = Manufacturer.getName(manufacturerId);
                    contentValues.put(MANUFACTURER_NAME, manufacturerName);
                    Log.i(TAG, "onUpgrade: manufacturer id -> name: " + manufacturerId + " -> " + manufacturerName);

                    // get battery percentage from ANT+ battery status
                    BatteryStatus batteryStatus = BatteryStatus.getValueFromInt(cursor.getInt(cursor.getColumnIndexOrThrow(LAST_BATTERY_STATUS)));
                    int batteryPercentage = BatteryStatusHelper.getBatterPercentage(batteryStatus);
                    contentValues.put(LAST_BATTERY_PERCENTAGE, batteryPercentage);
                    Log.i(TAG, "onUpgrade: batteryStatus -> percentage: " + batteryStatus.name() + " -> " + batteryPercentage);

                    // get device type from ant device type
                    byte antDeviceTypeByte = (byte) cursor.getInt(cursor.getColumnIndex(ANT_DEVICE_TYPE_BYTE));
                    DeviceType deviceType = getDeviceTypeFromANTDeviceTypeByte(antDeviceTypeByte);
                    contentValues.put(DEVICE_TYPE, deviceType.name());  // TODO: deviceType might be null?

                    db.insert(DEVICES, null, contentValues);
                }
                cursor.close();
            }

            if (oldVersion <= 4) {
                if (DEBUG) Log.i(TAG, "onUpgrade: upgrading from Version 4");
                db.execSQL(CREATE_BT_SPECIFIC_5);
            }

            if (oldVersion <= 5) {
                if (DEBUG) Log.i(TAG, "onUpgrade: upgrading from Version 5");

                db.execSQL(CREATE_BIKE_POWER_SENSORS_TABLE_6);

                // copy stuff form the old Bluetooth specific features table to the new bike power specific one...'

                // get a cursor with all rows
                Cursor cursor = db.query(TABLE_BT_SPECIFIC,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                );
                while (cursor.moveToNext()) {
                    String btAddress = cursor.getString(cursor.getColumnIndex(BT_ADDRESS));
                    int btFeature = cursor.getInt(cursor.getColumnIndex(BT_FEATURE));
                    int sensorFlags = BTLEBikePowerDevice.btFeature2BikePowerSensorFlags(btFeature);  // translate BT feature to new Bike Power sensors flag

                    if (DEBUG)
                        Log.i(TAG, "migrating btAddress=" + btAddress + ", btFeature=" + btFeature + ", flags=" + sensorFlags);

                    Cursor cursor1 = db.query(DEVICES,
                            null,
                            BT_ADDRESS + " = ?",
                            new String[]{btAddress},
                            null, null, null);
                    if (cursor1.moveToFirst()) {
                        int deviceId = cursor1.getInt(cursor.getColumnIndex(C_ID));

                        if (DEBUG) Log.i(TAG, "migrating deviceId=" + deviceId);

                        ContentValues values = new ContentValues();
                        values.put(DevicesDbHelper.DEVICE_ID, deviceId);
                        values.put(DevicesDbHelper.BIKE_POWER_SENSOR_FLAGS, sensorFlags);

                        db.insert(DevicesDbHelper.BIKE_POWER_SENSOR_FLAGS_TABLE,
                                null,
                                values);
                    }
                    cursor1.close();
                }
                cursor.close();

                // finally, remove the unnecessary table
                db.execSQL("DROP TABLE IF EXISTS " + TABLE_BT_SPECIFIC);

            }

            if (oldVersion <= 6) {
                addLocationDevicesToDb(db);
            }
        }

        // necessary to upgrade the database
        private DeviceType getDeviceTypeFromANTDeviceTypeByte(byte antDeviceTypeByte) {
            switch (antDeviceTypeByte) {
                case (byte) 0x7A:
                    return DeviceType.BIKE_CADENCE;

                case (byte) 0x0B:
                    return DeviceType.BIKE_POWER;

                case (byte) 0x79:
                    return DeviceType.BIKE_SPEED_AND_CADENCE;

                case (byte) 0x7B:
                    return DeviceType.BIKE_SPEED;

                case (byte) 0x78:
                    return DeviceType.HRM;

                case (byte) 0x7C:
                    return DeviceType.RUN_SPEED;

                case (byte) 0x19:
                    return DeviceType.ENVIRONMENT;

                default:
                    return null;
                // return DUMMY;
            }
        }

        private void addLocationDevicesToDb(SQLiteDatabase db) {
            ContentValues contentValues = new ContentValues();
            contentValues.put(PROTOCOL, Protocol.SMARTPHONE.name());
            contentValues.put(DEVICE_TYPE, DeviceType.SPEED_AND_LOCATION_GPS.name());
            contentValues.put(PAIRED, TrainingApplication.useLocationSourceGPS());
            contentValues.put(NAME, mContext.getString(R.string.prefsLocationSourceGPS));
            contentValues.put(MANUFACTURER_NAME, Build.BRAND);
            db.insert(DEVICES, null, contentValues);

            contentValues = new ContentValues();
            contentValues.put(PROTOCOL, Protocol.SMARTPHONE.name());
            contentValues.put(DEVICE_TYPE, DeviceType.SPEED_AND_LOCATION_NETWORK.name());
            contentValues.put(PAIRED, TrainingApplication.useLocationSourceNetwork());
            contentValues.put(NAME, mContext.getString(R.string.prefsLocationSourceNetwork));
            contentValues.put(MANUFACTURER_NAME, Build.BRAND);
            db.insert(DEVICES, null, contentValues);

            contentValues = new ContentValues();
            contentValues.put(PROTOCOL, Protocol.SMARTPHONE.name());
            contentValues.put(DEVICE_TYPE, DeviceType.SPEED_AND_LOCATION_GOOGLE_FUSED.name());
            contentValues.put(PAIRED, TrainingApplication.useLocationSourceGoogleFused());
            contentValues.put(NAME, mContext.getString(R.string.prefsLocationSourceGoogleFused));
            contentValues.put(MANUFACTURER_NAME, Build.BRAND);
            db.insert(DEVICES, null, contentValues);

            if (HavePressureSensor.havePressureSensor(mContext)) {
                contentValues = new ContentValues();
                contentValues.put(PROTOCOL, Protocol.SMARTPHONE.name());
                contentValues.put(DEVICE_TYPE, DeviceType.ALTITUDE_FROM_PRESSURE.name());
                contentValues.put(PAIRED, true);
                contentValues.put(NAME, mContext.getString(R.string.AltitudeFromPressureDevice_name));
                contentValues.put(MANUFACTURER_NAME, Build.BRAND);
                db.insert(DEVICES, null, contentValues);
            }
        }
    }
}
