/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
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

package com.atrainingtracker.banalservice.devices;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.LocationManager;
import android.os.Build;
import android.util.Log;

import com.atrainingtracker.R;
import com.atrainingtracker.util.EnumUtils;
import com.atrainingtracker.util.LifecycleUtils;
import com.atrainingtracker.banalservice.BANALService;
import com.atrainingtracker.banalservice.BSportType;
import com.atrainingtracker.banalservice.devices.ant_plus.ANTBikeCadenceDevice;
import com.atrainingtracker.banalservice.devices.ant_plus.ANTBikePowerDevice;
import com.atrainingtracker.banalservice.devices.ant_plus.ANTBikeSpeedAndCadenceDevice;
import com.atrainingtracker.banalservice.devices.ant_plus.ANTBikeSpeedDevice;
import com.atrainingtracker.banalservice.devices.ant_plus.ANTEnvironmentDevice;
import com.atrainingtracker.banalservice.devices.ant_plus.ANTHeartRateDevice;
import com.atrainingtracker.banalservice.devices.ant_plus.ANTRunSpeedDevice;
import com.atrainingtracker.banalservice.devices.ant_plus.MyANTDevice;
import com.atrainingtracker.banalservice.devices.ant_plus.search_new.ANTSearchForNewDevicesEngineMultiDeviceSearch;
import com.atrainingtracker.banalservice.devices.ant_plus.search_new.ANTSearchForNewDevicesEngineMultiDeviceSearch.IANTAsyncSearchEngineInterface;
import com.atrainingtracker.banalservice.devices.bluetooth_le.BTLEBikeCadenceDevice;
import com.atrainingtracker.banalservice.devices.bluetooth_le.BTLEBikePowerDevice;
import com.atrainingtracker.banalservice.devices.bluetooth_le.BTLEBikeSpeedAndCadenceDevice;
import com.atrainingtracker.banalservice.devices.bluetooth_le.BTLEBikeSpeedDevice;
import com.atrainingtracker.banalservice.devices.RrBeatEvent;
import com.atrainingtracker.banalservice.devices.RrBeatListener;
import com.atrainingtracker.banalservice.devices.RrBeatSource;
import com.atrainingtracker.banalservice.devices.hrv.HRVMetricsDevice;
import com.atrainingtracker.banalservice.devices.bluetooth_le.BTLEHeartRateDevice;
import com.atrainingtracker.banalservice.devices.bluetooth_le.BTLERunSpeedDevice;
import com.atrainingtracker.banalservice.devices.bluetooth_le.MyBTLEDevice;
import com.atrainingtracker.banalservice.devices.bluetooth_le.polar.PolarOnlineStreamingBridge;
import com.atrainingtracker.banalservice.devices.bluetooth_le.search_new.BTSearchForNewDevicesEngine;
import com.atrainingtracker.banalservice.devices.bluetooth_le.search_new.BTSearchForNewDevicesEngine.IBTSearchForNewDevicesEngineInterface;
import com.atrainingtracker.banalservice.Protocol;
import com.atrainingtracker.banalservice.sensor.MyAccumulatorSensor;
import com.atrainingtracker.banalservice.sensor.MySensor;
import com.atrainingtracker.banalservice.sensor.MySensorManager;
import com.atrainingtracker.banalservice.sensor.SensorType;
import com.atrainingtracker.banalservice.ui.devices.devicedata.DeviceDataRepository;
import com.atrainingtracker.banalservice.ui.devices.devicedata.PairedRemoteDeviceDto;
import com.atrainingtracker.banalservice.helpers.HavePressureSensor;
import com.atrainingtracker.trainingtracker.TrainingApplication;
import com.atrainingtracker.trainingtracker.TrackingMode;
import com.dsi.ant.plugins.antplus.pccbase.MultiDeviceSearch;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;

import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.LongSupplier;

import static com.atrainingtracker.banalservice.BSportType.UNKNOWN;

import androidx.annotation.VisibleForTesting;
import androidx.core.content.ContextCompat;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;


public class DeviceManager {
    private static final String TAG = "DeviceManager";
    private static final boolean DEBUG = BANALService.getDebug(false);
    protected static volatile MyRemoteDevice cMyRemoteDeviceCurrentlySearchingFor = null;
    protected Context mContext;
    protected volatile ClockDevice mClockDevice;
    protected volatile BatteryDevice mBatteryDevice;
    protected volatile SpeedAndLocationDevice mSpeedAndLocationDevice_GPS, mSpeedAndLocationDevice_GoogleFused;
    protected volatile SpeedAndLocationDevice_ATTFused mSpeedAndLocationDevice_ATTFused;
    protected volatile boolean mIsIndoorMode = false;
    protected volatile AltitudeFromPressureDevice mAltitudeFromPressureDevice;
    protected volatile AltitudeFromPositionDevice mAltitudeFromPositionDevice;
    protected volatile AltitudeAndSlopeDevice_ATTFused mAltitudeAndSlopeDevice_ATTFused;
    protected volatile VerticalSpeedAndSlopeDevice mVerticalSpeedAndSlopeDevice;
    protected volatile CyclingPowerMetricsDevice mCyclingPowerMetricsDevice;
    protected volatile HRVMetricsDevice mHrvMetricsDevice;
    protected boolean mHavePressureSensor = false;
    protected IntentFilter mPairingChangedFilter = new IntentFilter(BANALService.PAIRING_CHANGED);
    // protected IntentFilter mRemoveDeviceFilter            = new IntentFilter(BANALService.REMOVE_DEVICE);
    // protected IntentFilter mCreateNewDeviceFilter    = new IntentFilter(BANALService.CREATE_NEW_DEVICE);
    protected IntentFilter mSearchingStoppedForOneFilter = new IntentFilter(BANALService.SEARCHING_STOPPED_FOR_ONE_INTENT);
    protected IntentFilter mStartSearchingForNewDevicesFilter = new IntentFilter(BANALService.START_SEARCHING_FOR_NEW_DEVICES_INTENT);
    protected IntentFilter mStopSearchingForNewDevicesFilter = new IntentFilter(BANALService.STOP_SEARCHING_FOR_NEW_DEVICES_INTENT);
    protected MySensorManager mSensorManager;
    protected final Map<Long, MyRemoteDevice> mMyRemoteDevices = new ConcurrentHashMap<>();
    protected final Map<MyRemoteDevice, Integer> mMyRemoteDeviceTries = new ConcurrentHashMap<>();
    protected final Deque<MyRemoteDevice> mSearchQueue = new ConcurrentLinkedDeque<>();
    protected final List<Long> mNewlyFoundDevices = new CopyOnWriteArrayList<>();
    protected volatile ANTSearchForNewDevicesEngineMultiDeviceSearch mAntAsyncSearchEngine = null;
    protected volatile BTSearchForNewDevicesEngine mBTSearchForNewDevicesEngine = null;
    private final DeviceDataRepository mDeviceDataRepository;
    private final Object mDeviceDbLock = new Object();
    private final BroadcastReceiver mStopSearchingForNewDevicesReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            if (DEBUG) Log.d(TAG, "Received StopSearchingForNewDevices Broadcast");

            stopSearchForNewRemoteDevices();
        }
    };
    BANALService mBanalService = null;
    private final BroadcastReceiver mSearchingStoppedForOneReceiver = new BroadcastReceiver() {
        // it was observed that this might be called from Remote Devices that are currently tracking
        // => not only from the device we are currently searching for
        // thus, we must be a little bit careful here.
        public void onReceive(Context context, Intent intent) {
            boolean success = intent.getBooleanExtra(BANALService.SEARCHING_FINISHED_SUCCESS, false);
            long deviceID = intent.getLongExtra(BANALService.DEVICE_ID, -1);
            if (DEBUG)
                Log.i(TAG, "finished searching for a remote device, success=" + success + ", deviceID=" + deviceID);
            synchronized (mSearchQueue) {
                MyRemoteDevice current = cMyRemoteDeviceCurrentlySearchingFor;
                if (current != null
                        && current.getDeviceId() == deviceID) {  // it is indeed the device that we are currently searching
                    cMyRemoteDeviceCurrentlySearchingFor = null;
                    if (!success) {                                                     // it was not found
                        Integer tries = mMyRemoteDeviceTries.get(current);
                        if (tries == null || tries <= 1) {  // no longer try to search for this device
                            if (DEBUG)
                                Log.i(TAG, "max number of tries <= 1 -> give up searching for this device");
                            detachIfRrBeatSource(current);
                            current.shutDown();                // so we shut it down
                            mMyRemoteDevices.remove(deviceID);                              // and remove it
                        } else {  // continue searching
                            if (DEBUG)
                                Log.i(TAG, "max number of tries > 1 -> give this device another chance");
                            mMyRemoteDeviceTries.put(current, tries - 1);
                            mSearchQueue.addLast(current);
                        }

                    }
                    searchForNextRemoteDevice();
                } else {
                    if (DEBUG) Log.d(TAG, "Ignoring search stop broadcast from non-active peripheral device ID: " + deviceID + " (0x" + Long.toHexString(deviceID) + ")");
                }
            }
        }
    };

    BroadcastReceiver getSearchingStoppedForOneReceiver() {
        return mSearchingStoppedForOneReceiver;
    }

    public java.util.Deque<MyRemoteDevice> getSearchQueue() {
        return mSearchQueue;
    }
    private final BroadcastReceiver mPairingChangedReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            if (DEBUG) Log.d(TAG, "pairing changed!");

            if (!(intent.hasExtra(BANALService.PAIRED) && intent.hasExtra(BANALService.DEVICE_ID))) {
                Log.d(TAG, "no extras passed, returning");
                return;
            }

            long deviceId = intent.getLongExtra(BANALService.DEVICE_ID, -1);
            boolean paired = intent.getBooleanExtra(BANALService.PAIRED, true);

            pairingChanged(deviceId, paired);
        }
    };
    protected IntentFilter mTrackingStateChangedFilter = new IntentFilter(TrainingApplication.TRACKING_STATE_CHANGED);

    private final BroadcastReceiver mTrackingStateChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            TrackingMode mode = TrainingApplication.getTrackingMode();
            if (mode == TrackingMode.READY || mode == TrackingMode.IDLE) {
                // Direct state check protects against broadcast timing delays
                startSearchForPairedDevices();
            }
        }
    };
    // Callback interface for searching for ANT+
    IANTAsyncSearchEngineInterface mANTAsyncSearchEngineInterface = new IANTAsyncSearchEngineInterface() {
        public void onSearchStopped() {
            // TODO:inform user with a toast?
        }

        public void onNewDeviceFound(DeviceType deviceType, MultiDeviceSearch.MultiDeviceSearchResult deviceFound, boolean pairingRecommendation, String manufacturer, int batteryPercentage) {
            onNewDeviceFound(deviceType, deviceFound.getAntDeviceNumber(), deviceFound.getDeviceDisplayName(), pairingRecommendation, manufacturer, batteryPercentage);
        }

        @Override
        public void onNewDeviceFound(DeviceType deviceType, int antDeviceNumber, String deviceDisplayName, boolean pairingRecommendation, String manufacturer, int batteryPercentage) {
            if (DEBUG)
                Log.d(TAG, "onNewANTDeviceFound: " + deviceType.name() + ", Ant Nr:" + antDeviceNumber);

            long deviceId = -1;
            boolean isNew = false;
            boolean isWorkoutActive = false;

            synchronized (mDeviceDbLock) {
                isWorkoutActive = (TrainingApplication.getTrackingMode() == TrackingMode.TRACKING);
                if (!mDeviceDataRepository.isANTDeviceInDB(deviceType, antDeviceNumber)) { // not yet known
                    if (DEBUG)
                        Log.d(TAG, "device is not yet in the database, so we have to add it to the database");

                    deviceId = mDeviceDataRepository.insertNewAntDeviceIntoDB(deviceType, deviceDisplayName, antDeviceNumber, true, manufacturer, batteryPercentage);
                    isNew = true;
                } else {
                    if (DEBUG)
                        Log.i(TAG, "Device already in database, so we do not add it to the database");
                    deviceId = mDeviceDataRepository.getDeviceId(deviceType, antDeviceNumber);

                    if (manufacturer != null) {
                        if (DEBUG) Log.i(TAG, "wow, we got a manufacturer: " + manufacturer);
                        mDeviceDataRepository.setManufacturerName(deviceId, manufacturer);
                    }

                    if (batteryPercentage >= 0) {
                        if (DEBUG) Log.i(TAG, "wow, we got a battery percentage: " + batteryPercentage);
                        mDeviceDataRepository.setBatteryPercentage(deviceId, batteryPercentage);
                    }
                }

                if (deviceId > 0) {
                    mNewlyFoundDevices.add(deviceId);
                } else {
                    Log.w(TAG, "Failed to insert or locate ANT device in DB: " + antDeviceNumber);
                }
            }

            if (deviceId > 0) {
                if (isNew && !isWorkoutActive) {
                    pairingChanged(deviceId, true);
                }
                sendNewDeviceFoundBroadcast(deviceId);
            }
        }
    };
    // stuff for searching for Bluetooth le
    IBTSearchForNewDevicesEngineInterface mBTSearchInterface = new IBTSearchForNewDevicesEngineInterface() {
        public void onSearchStopped() {
            // TODO:inform user with a toast?
        }

        public void onNewDeviceFound(DeviceType deviceType,
                                     String BluetoothMACAddress,
                                     String name,
                                     String manufacturer,
                                     int batteryPercentage) {
            if (DEBUG)
                Log.d(TAG, "onNewBluetoothDeviceFound: " + deviceType.name() + ", BT address: " + BluetoothMACAddress);

            long deviceId = -1;
            boolean isNew = false;
            boolean isWorkoutActive = false;

            synchronized (mDeviceDbLock) {
                isWorkoutActive = (TrainingApplication.getTrackingMode() == TrackingMode.TRACKING);
                if (!mDeviceDataRepository.isBluetoothDeviceInDB(deviceType, BluetoothMACAddress)) {
                    if (DEBUG) Log.d(TAG, "device is not yet in the database, so we have to add it");

                    deviceId = mDeviceDataRepository.insertNewBluetoothDeviceIntoDB(deviceType, BluetoothMACAddress, name, manufacturer, batteryPercentage, true);
                    isNew = true;
                } else {
                    if (DEBUG) Log.d(TAG, "Device already in database, so we do not add it");
                    deviceId = mDeviceDataRepository.getDeviceId(deviceType, BluetoothMACAddress);
                }

                if (deviceId > 0) {
                    mNewlyFoundDevices.add(deviceId);
                } else {
                    Log.w(TAG, "Failed to insert or locate Bluetooth device in DB: " + BluetoothMACAddress);
                }
            }

            if (deviceId > 0) {
                if (isNew && !isWorkoutActive) {
                    pairingChanged(deviceId, true);
                }
                sendNewDeviceFoundBroadcast(deviceId);
            }
        }
    };
    // TODO: start search fo new
    private final BroadcastReceiver mStartSearchingForNewDevicesReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            if (DEBUG) Log.d(TAG, "Received StartSearchingForNewDevices Broadcast");

            if (!(intent.hasExtra(BANALService.PROTOCOL) && intent.hasExtra(BANALService.DEVICE_TYPE))) {
                Log.d(TAG, "no extras passed, returning");
                return;
            }

            Protocol protocol = EnumUtils.safeValueOfOrNull(Protocol.class, intent.getStringExtra(BANALService.PROTOCOL), TAG);
            DeviceType deviceType = EnumUtils.safeValueOfOrNull(DeviceType.class, intent.getStringExtra(BANALService.DEVICE_TYPE), TAG);
            if (protocol == null || deviceType == null) {
                Log.e(TAG, "Cannot start device search: invalid or missing Protocol / DeviceType in intent extra");
                Intent stoppedIntent = new Intent(BANALService.SEARCHING_STOPPED_FOR_ONE_INTENT);
                stoppedIntent.setPackage(mContext.getPackageName());
                mContext.sendBroadcast(stoppedIntent);
                return;
            }
            startSearchForNewRemoteDevices(protocol, deviceType);
        }
    };

    private String getStringSafely(int resId, String fallback) {
        try {
            return mContext != null ? mContext.getString(resId) : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    /**
     * Constructor
     **/
    public DeviceManager(BANALService banalService, MySensorManager mySensorManager) {
        this(banalService, mySensorManager, DeviceDataRepository.Companion.getInstance(banalService));
    }

    public DeviceManager(BANALService banalService, MySensorManager mySensorManager, DeviceDataRepository deviceDataRepository) {
        mContext = banalService;
        mBanalService = banalService;
        mDeviceDataRepository = deviceDataRepository != null ? deviceDataRepository : DeviceDataRepository.Companion.getInstance(mContext);
        
        // ATT-353: Eager registration of the pressure sensor to ensure ID resolution works 
        // on the very first run (Breaks the circular dependency).
        mHavePressureSensor = HavePressureSensor.havePressureSensor(mContext);
        if (mHavePressureSensor) {
            mDeviceDataRepository.ensureSmartphoneDeviceExists(
                    DeviceType.ALTITUDE_FROM_PRESSURE,
                    true,
                    getStringSafely(R.string.devices_altitude_from_pressure, "Barometer")
            );
        }

        mSensorManager = mySensorManager;

        mClockDevice = new ClockDevice(mContext, mSensorManager, false);
        if (banalService != null && banalService.getClass() == BANALService.class) {
            mClockDevice.startTimer();
        }
        mBatteryDevice = new BatteryDevice(mContext, mSensorManager);
        
        long altitudePressureId = mDeviceDataRepository.getSmartphoneDeviceId(DeviceType.ALTITUDE_FROM_PRESSURE);
        if (mHavePressureSensor && mDeviceDataRepository.isPaired(altitudePressureId)) {
            mAltitudeFromPressureDevice = new AltitudeFromPressureDevice(mContext, mSensorManager);
        }

        mDeviceDataRepository.ensureSmartphoneDeviceExists(
                DeviceType.ALTITUDE_FROM_POSITION,
                false,
                getStringSafely(R.string.devices_altitude_from_position, "GPS Altitude")
        );
        long altitudePositionId = mDeviceDataRepository.getSmartphoneDeviceId(DeviceType.ALTITUDE_FROM_POSITION);
        if (mDeviceDataRepository.isPaired(altitudePositionId)) {
            mAltitudeFromPositionDevice = new AltitudeFromPositionDevice(mContext, mSensorManager);
        }

        long attFusedAltitudeId = mDeviceDataRepository.getAltitudeAndSlopeAttFusedDeviceId();
        if (mDeviceDataRepository.isPaired(attFusedAltitudeId)) {
            try {
                mAltitudeAndSlopeDevice_ATTFused = new AltitudeAndSlopeDevice_ATTFused(mContext, mSensorManager);
            } catch (Exception e) {
                Log.w(TAG, "Could not initialize ATT Fused altitude device: " + e.getMessage());
            }
        }

        // create the location devices when they are paired.
        LocationManager locationManager = (LocationManager) mContext.getSystemService(Context.LOCATION_SERVICE);

        long gpsDeviceId = mDeviceDataRepository.getSpeedAndLocationGPSDeviceId();
        if (mDeviceDataRepository.isPaired(gpsDeviceId)
                && TrainingApplication.havePermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            if (isProviderAvailableSafely(locationManager, LocationManager.GPS_PROVIDER)) {
                if (DEBUG) Log.i(TAG, "creating GPS location device");
                try {
                    mSpeedAndLocationDevice_GPS = new SpeedAndLocationDevice_GPS(mContext, mSensorManager);
                    applyIndoorMode(mSpeedAndLocationDevice_GPS);
                } catch (Exception e) {
                    Log.w(TAG, "Could not initialize GPS location device: " + e.getMessage());
                }
            } else {
                if (DEBUG) Log.i(TAG, "Skipping GPS location device: provider unavailable");
            }
        }

        long fusedDeviceId = mDeviceDataRepository.getSpeedAndLocationGoogleFusedDeviceId();
        if(mDeviceDataRepository.isPaired(fusedDeviceId)
                && TrainingApplication.havePermission(Manifest.permission.ACCESS_FINE_LOCATION)
                && GooglePlayServicesUtil.isGooglePlayServicesAvailable(mContext) == ConnectionResult.SUCCESS) {
            if (DEBUG) Log.i(TAG, "creating google fused location device");
            try {
                mSpeedAndLocationDevice_GoogleFused = new SpeedAndLocationDevice_GoogleFused(mContext, mSensorManager);
                applyIndoorMode(mSpeedAndLocationDevice_GoogleFused);
            } catch (Exception e) {
                Log.w(TAG, "Could not initialize Google Fused location device: " + e.getMessage());
            }
        }

        long attFusedDeviceId = mDeviceDataRepository.getSpeedAndLocationAttFusedDeviceId();
        if (mDeviceDataRepository.isPaired(attFusedDeviceId)
                && TrainingApplication.havePermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            if (DEBUG) Log.i(TAG, "creating ATT fused location device");
            try {
                mSpeedAndLocationDevice_ATTFused = new SpeedAndLocationDevice_ATTFused(mContext, mSensorManager);
                applyIndoorMode(mSpeedAndLocationDevice_ATTFused);
            } catch (Exception e) {
                Log.w(TAG, "Could not initialize ATT Fused location device: " + e.getMessage());
            }
        }

        // also create paired remote devices and start searching for them
        if (TrainingApplication.startSearchWhenAppStarts()) {
            startSearchForPairedDevices();
        }

        ContextCompat.registerReceiver(mContext, mPairingChangedReceiver, mPairingChangedFilter, ContextCompat.RECEIVER_NOT_EXPORTED);
        ContextCompat.registerReceiver(mContext, mSearchingStoppedForOneReceiver, mSearchingStoppedForOneFilter, ContextCompat.RECEIVER_NOT_EXPORTED);
        ContextCompat.registerReceiver(mContext, mStartSearchingForNewDevicesReceiver, mStartSearchingForNewDevicesFilter, ContextCompat.RECEIVER_NOT_EXPORTED);
        ContextCompat.registerReceiver(mContext, mStopSearchingForNewDevicesReceiver, mStopSearchingForNewDevicesFilter, ContextCompat.RECEIVER_NOT_EXPORTED);
        ContextCompat.registerReceiver(mContext, mTrackingStateChangedReceiver, mTrackingStateChangedFilter, ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    public void createVerticalSpeedAndSlopeDevice() {
        mVerticalSpeedAndSlopeDevice = new VerticalSpeedAndSlopeDevice(mContext, mSensorManager);
    }

    public void createCyclingPowerMetricsDevice() {
        mCyclingPowerMetricsDevice = new CyclingPowerMetricsDevice(mContext, mSensorManager);
    }

    public CyclingPowerMetricsDevice getCyclingPowerMetricsDevice() {
        return mCyclingPowerMetricsDevice;
    }

    private static final long RR_SOURCE_STALE_TIMEOUT_MS = 5000L;
    private static final int RR_PRIORITY_ECG = 0;
    private static final int RR_PRIORITY_ANT = 1;
    private static final int RR_PRIORITY_PPG = 2;
    private static final int RR_PRIORITY_OTHER = 3;

    private final List<RrBeatListener> mRrBeatListeners = new CopyOnWriteArrayList<>();
    private final Object mRrArbitrationLock = new Object();
    private final Map<RrBeatSource, RrBeatListener> mRrSourceRelayListeners = new ConcurrentHashMap<>();
    private RrBeatSource mActivePrimaryRrSource = null;
    private long mLastPrimaryRrBeatWallMs = 0L;
    private long mLastForwardedRrTimestampMs = 0L;
    private LongSupplier mRrClockMs = System::currentTimeMillis;

    public void createHrvMetricsDevice() {
        mHrvMetricsDevice = new HRVMetricsDevice(mContext, mSensorManager);
        for (MyRemoteDevice remote : mMyRemoteDevices.values()) {
            attachIfRrBeatSource(remote);
        }
    }

    public void addRrBeatListener(RrBeatListener listener) {
        if (listener != null && !mRrBeatListeners.contains(listener)) {
            mRrBeatListeners.add(listener);
            for (MyRemoteDevice remote : mMyRemoteDevices.values()) {
                attachIfRrBeatSource(remote);
            }
        }
    }

    public void removeRrBeatListener(RrBeatListener listener) {
        if (listener != null) {
            mRrBeatListeners.remove(listener);
        }
    }

    private int getRrSourcePriority(@Nonnull RrBeatSource source) {
        if (source instanceof BTLEHeartRateDevice) {
            return RR_PRIORITY_ECG;
        }
        if (source instanceof PolarOnlineStreamingBridge) {
            return ((PolarOnlineStreamingBridge) source).isVeritySense()
                    ? RR_PRIORITY_PPG
                    : RR_PRIORITY_ECG;
        }
        if (source instanceof ANTHeartRateDevice) {
            return RR_PRIORITY_ANT;
        }
        return RR_PRIORITY_OTHER;
    }

    private void attachIfRrBeatSource(@Nullable Object candidate) {
        if (!(candidate instanceof RrBeatSource)) {
            return;
        }
        RrBeatSource source = (RrBeatSource) candidate;
        RrBeatListener relay = mRrSourceRelayListeners.computeIfAbsent(source, s -> {
            RrBeatListener created = event -> onArbitratedRrBeat(s, event);
            s.addRrBeatListener(created);
            return created;
        });
        if (relay == null) {
            return;
        }
    }

    private void detachIfRrBeatSource(@Nullable Object candidate) {
        if (!(candidate instanceof RrBeatSource)) {
            return;
        }
        RrBeatSource source = (RrBeatSource) candidate;
        RrBeatListener relay = mRrSourceRelayListeners.remove(source);
        if (relay != null) {
            source.removeRrBeatListener(relay);
        }
        synchronized (mRrArbitrationLock) {
            if (mActivePrimaryRrSource == source) {
                mActivePrimaryRrSource = null;
                mLastPrimaryRrBeatWallMs = 0L;
            }
        }
    }

    private void onArbitratedRrBeat(@Nonnull RrBeatSource source, @Nonnull RrBeatEvent event) {
        RrBeatEvent eventToForward = null;
        synchronized (mRrArbitrationLock) {
            if (!mRrSourceRelayListeners.containsKey(source)) {
                return;
            }
            long nowMs = mRrClockMs.getAsLong();
            boolean noPrimary = mActivePrimaryRrSource == null
                    || !mRrSourceRelayListeners.containsKey(mActivePrimaryRrSource);
            boolean primaryStale = !noPrimary
                    && (nowMs - mLastPrimaryRrBeatWallMs) > RR_SOURCE_STALE_TIMEOUT_MS;
            boolean higherPriority = !noPrimary
                    && getRrSourcePriority(source) < getRrSourcePriority(mActivePrimaryRrSource);

            if (source == mActivePrimaryRrSource || noPrimary || primaryStale || higherPriority) {
                mActivePrimaryRrSource = source;
                mLastPrimaryRrBeatWallMs = nowMs;
                long monotonicTs = Math.max(event.getBeatTimestampMs(), mLastForwardedRrTimestampMs);
                mLastForwardedRrTimestampMs = monotonicTs;
                eventToForward = (monotonicTs == event.getBeatTimestampMs())
                        ? event
                        : event.copy(
                                monotonicTs,
                                event.getRrMs(),
                                event.getRawCounter(),
                                event.getProtocol(),
                                event.getGapBeats(),
                                event.getArtifactFlag()
                        );
            }
        }
        if (eventToForward != null) {
            HRVMetricsDevice hrvDevice = mHrvMetricsDevice;
            if (hrvDevice != null) {
                hrvDevice.onRrBeat(eventToForward);
            }
            for (RrBeatListener listener : mRrBeatListeners) {
                try {
                    listener.onRrBeat(eventToForward);
                } catch (Exception e) {
                    Log.e(TAG, "Error forwarding arbitrated RrBeatEvent", e);
                }
            }
        }
    }

    @VisibleForTesting
    public void registerRrBeatSourceForTesting(@Nonnull RrBeatSource source) {
        attachIfRrBeatSource(source);
    }

    @VisibleForTesting
    public void unregisterRrBeatSourceForTesting(@Nonnull RrBeatSource source) {
        detachIfRrBeatSource(source);
    }

    @VisibleForTesting
    public void setRrClockForTesting(@Nonnull LongSupplier clockMs) {
        synchronized (mRrArbitrationLock) {
            mRrClockMs = clockMs;
        }
    }

    @VisibleForTesting
    @Nullable
    public RrBeatSource getActivePrimaryRrSourceForTesting() {
        synchronized (mRrArbitrationLock) {
            return mActivePrimaryRrSource;
        }
    }

    public HRVMetricsDevice getHrvMetricsDevice() {
        return mHrvMetricsDevice;
    }

    public ClockDevice getClockDevice() {
        return mClockDevice;
    }

    public BatteryDevice getBatteryDevice() {
        return mBatteryDevice;
    }

    /**
     * Lazily checks and initializes GPS and Google Fused location devices if they
     * are currently null, paired in database, and ACCESS_FINE_LOCATION permission
     * has now been granted, or re-registers update listeners if already instantiated (REQ-PRI-004, ATT-2357, REQ-TRK-017, ATT-3045).
     */
    public synchronized void checkOrInitializeLocationDevices() {
        LocationManager locationManager = (LocationManager) mContext.getSystemService(Context.LOCATION_SERVICE);

        long gpsDeviceId = mDeviceDataRepository.getSpeedAndLocationGPSDeviceId();
        if (mDeviceDataRepository.isPaired(gpsDeviceId)
                && TrainingApplication.havePermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            if (mSpeedAndLocationDevice_GPS == null) {
                if (isProviderAvailableSafely(locationManager, LocationManager.GPS_PROVIDER)) {
                    if (DEBUG) Log.i(TAG, "checkOrInitializeLocationDevices: creating GPS location device");
                    try {
                        mSpeedAndLocationDevice_GPS = new SpeedAndLocationDevice_GPS(mContext, mSensorManager);
                        applyIndoorMode(mSpeedAndLocationDevice_GPS);
                    } catch (Exception e) {
                        Log.w(TAG, "Could not initialize GPS location device: " + e.getMessage());
                    }
                } else {
                    if (DEBUG) Log.i(TAG, "checkOrInitializeLocationDevices: skipping GPS, provider unavailable");
                }
            } else {
                mSpeedAndLocationDevice_GPS.checkOrReRegisterLocationUpdates();
            }
        }

        long fusedDeviceId = mDeviceDataRepository.getSpeedAndLocationGoogleFusedDeviceId();
        if (mDeviceDataRepository.isPaired(fusedDeviceId)
                && TrainingApplication.havePermission(Manifest.permission.ACCESS_FINE_LOCATION)
                && GooglePlayServicesUtil.isGooglePlayServicesAvailable(mContext) == ConnectionResult.SUCCESS) {
            if (mSpeedAndLocationDevice_GoogleFused == null) {
                if (DEBUG) Log.i(TAG, "checkOrInitializeLocationDevices: creating Google Fused location device");
                try {
                    mSpeedAndLocationDevice_GoogleFused = new SpeedAndLocationDevice_GoogleFused(mContext, mSensorManager);
                    applyIndoorMode(mSpeedAndLocationDevice_GoogleFused);
                } catch (Exception e) {
                    Log.w(TAG, "Could not initialize Google Fused location device: " + e.getMessage());
                }
            } else {
                mSpeedAndLocationDevice_GoogleFused.checkOrReRegisterLocationUpdates();
            }
        }

        long attFusedDeviceId = mDeviceDataRepository.getSpeedAndLocationAttFusedDeviceId();
        if (mDeviceDataRepository.isPaired(attFusedDeviceId)
                && TrainingApplication.havePermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            if (mSpeedAndLocationDevice_ATTFused == null) {
                if (DEBUG) Log.i(TAG, "checkOrInitializeLocationDevices: creating ATT Fused location device");
                try {
                    mSpeedAndLocationDevice_ATTFused = new SpeedAndLocationDevice_ATTFused(mContext, mSensorManager);
                    applyIndoorMode(mSpeedAndLocationDevice_ATTFused);
                } catch (Exception e) {
                    Log.w(TAG, "Could not initialize ATT Fused location device: " + e.getMessage());
                }
            } else {
                mSpeedAndLocationDevice_ATTFused.checkOrReRegisterLocationUpdates();
            }
        }
    }

    public static boolean isSearchingForARemoteDevice() {
        return cMyRemoteDeviceCurrentlySearchingFor != null;
    }

    public boolean isSearchingForNewRemoteDevices() {
        return mAntAsyncSearchEngine != null || mBTSearchForNewDevicesEngine != null;
    }

    public DeviceType getNewRemoteDeviceTypeCurrentlySearchingFor() {
        ANTSearchForNewDevicesEngineMultiDeviceSearch antEngine = mAntAsyncSearchEngine;
        if (antEngine != null) {
            return antEngine.getDeviceType();
        }
        BTSearchForNewDevicesEngine btEngine = mBTSearchForNewDevicesEngine;
        if (btEngine != null) {
            return btEngine.getDeviceType();
        }
        return null;
    }

    public void shutDown() {
        if (DEBUG) Log.d(TAG, "shutDown()");

        stopSearchForNewRemoteDevices();

        for (MyDevice myDevice : getMyDeviceList()) {
            myDevice.shutDown();
        }

        cMyRemoteDeviceCurrentlySearchingFor = null;
        for (RrBeatSource source : new ArrayList<>(mRrSourceRelayListeners.keySet())) {
            detachIfRrBeatSource(source);
        }
        synchronized (mRrArbitrationLock) {
            mActivePrimaryRrSource = null;
            mLastPrimaryRrBeatWallMs = 0L;
            mLastForwardedRrTimestampMs = 0L;
        }
        mMyRemoteDevices.clear();
        mSearchQueue.clear();
        mMyRemoteDeviceTries.clear();
        mNewlyFoundDevices.clear();

        mClockDevice = null;
        mBatteryDevice = null;
        mSpeedAndLocationDevice_GPS = null;
        mSpeedAndLocationDevice_GoogleFused = null;
        mSpeedAndLocationDevice_ATTFused = null;
        mAltitudeFromPressureDevice = null;
        mAltitudeFromPositionDevice = null;
        mAltitudeAndSlopeDevice_ATTFused = null;
        mVerticalSpeedAndSlopeDevice = null;
        mCyclingPowerMetricsDevice = null;
        mHrvMetricsDevice = null;

        LifecycleUtils.safeUnregisterReceiver(mContext, mPairingChangedReceiver, TAG);
        LifecycleUtils.safeUnregisterReceiver(mContext, mSearchingStoppedForOneReceiver, TAG);
        // mContext.unregisterReceiver(mStartSearchingReceiver);
        LifecycleUtils.safeUnregisterReceiver(mContext, mStartSearchingForNewDevicesReceiver, TAG);
        LifecycleUtils.safeUnregisterReceiver(mContext, mStopSearchingForNewDevicesReceiver, TAG);
        LifecycleUtils.safeUnregisterReceiver(mContext, mTrackingStateChangedReceiver, TAG);
    }

    private void pairingChanged(long deviceId, boolean paired) {
        // first, check for the location devices.
        LocationManager locationManager = (LocationManager) mContext.getSystemService(Context.LOCATION_SERVICE);

        long gpsDeviceId = mDeviceDataRepository.getSpeedAndLocationGPSDeviceId();
        if (deviceId == gpsDeviceId) {
            if (paired) {
                if (mSpeedAndLocationDevice_GPS == null // paired and not yet there -> create (if we have the permission)
                        && TrainingApplication.havePermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
                    if (isProviderAvailableSafely(locationManager, LocationManager.GPS_PROVIDER)) {
                        try {
                            mSpeedAndLocationDevice_GPS = new SpeedAndLocationDevice_GPS(mContext, mSensorManager);
                            applyIndoorMode(mSpeedAndLocationDevice_GPS);
                        } catch (Exception e) {
                            Log.w(TAG, "Could not create GPS location device on pairing change: " + e.getMessage());
                        }
                    }
                } else if (mSpeedAndLocationDevice_GPS != null) {
                    mSpeedAndLocationDevice_GPS.checkOrReRegisterLocationUpdates();
                }
            } else if (mSpeedAndLocationDevice_GPS != null) { // destroy if unpaired and exists
                mSpeedAndLocationDevice_GPS.shutDown();
                mSpeedAndLocationDevice_GPS = null;
            }
            return;
        }

        long fusedDeviceId = mDeviceDataRepository.getSpeedAndLocationGoogleFusedDeviceId();
        if (deviceId == fusedDeviceId) {
            if (paired) {
                if (mSpeedAndLocationDevice_GoogleFused == null // if it does not exist
                        && TrainingApplication.havePermission(Manifest.permission.ACCESS_FINE_LOCATION)) { // and we have the permission to do so
                    try {
                        mSpeedAndLocationDevice_GoogleFused = new SpeedAndLocationDevice_GoogleFused(mContext, mSensorManager);
                        applyIndoorMode(mSpeedAndLocationDevice_GoogleFused);
                    } catch (Exception e) {
                        Log.w(TAG, "Could not create Google Fused location device on pairing change: " + e.getMessage());
                    }
                } else if (mSpeedAndLocationDevice_GoogleFused != null) {
                    mSpeedAndLocationDevice_GoogleFused.checkOrReRegisterLocationUpdates();
                }
            } else if (mSpeedAndLocationDevice_GoogleFused != null) { // destroy if unpaired and exists
                mSpeedAndLocationDevice_GoogleFused.shutDown();
                mSpeedAndLocationDevice_GoogleFused = null;
            }
            return;
        }

        long attFusedDeviceId = mDeviceDataRepository.getSpeedAndLocationAttFusedDeviceId();
        if (deviceId == attFusedDeviceId) {
            if (paired) {
                if (mSpeedAndLocationDevice_ATTFused == null
                        && TrainingApplication.havePermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
                    try {
                        mSpeedAndLocationDevice_ATTFused = new SpeedAndLocationDevice_ATTFused(mContext, mSensorManager);
                        applyIndoorMode(mSpeedAndLocationDevice_ATTFused);
                    } catch (Exception e) {
                        Log.w(TAG, "Could not create ATT Fused location device on pairing change: " + e.getMessage());
                    }
                } else if (mSpeedAndLocationDevice_ATTFused != null) {
                    mSpeedAndLocationDevice_ATTFused.checkOrReRegisterLocationUpdates();
                }
            } else if (mSpeedAndLocationDevice_ATTFused != null) {
                mSpeedAndLocationDevice_ATTFused.shutDown();
                mSpeedAndLocationDevice_ATTFused = null;
            }
            return;
        }

        long altitudePressureId = mDeviceDataRepository.getSmartphoneDeviceId(DeviceType.ALTITUDE_FROM_PRESSURE);
        if (deviceId == altitudePressureId) {
            if (paired && mAltitudeFromPressureDevice == null) {
                mAltitudeFromPressureDevice = new AltitudeFromPressureDevice(mContext, mSensorManager);
            } else if (!paired && mAltitudeFromPressureDevice != null) {
                mAltitudeFromPressureDevice.shutDown();
                mAltitudeFromPressureDevice = null;
            }
            return;
        }

        long altitudePositionId = mDeviceDataRepository.getSmartphoneDeviceId(DeviceType.ALTITUDE_FROM_POSITION);
        if (deviceId == altitudePositionId) {
            if (paired && mAltitudeFromPositionDevice == null) {
                mAltitudeFromPositionDevice = new AltitudeFromPositionDevice(mContext, mSensorManager);
            } else if (!paired && mAltitudeFromPositionDevice != null) {
                mAltitudeFromPositionDevice.shutDown();
                mAltitudeFromPositionDevice = null;
            }
            return;
        }

        long attFusedAltitudeId = mDeviceDataRepository.getAltitudeAndSlopeAttFusedDeviceId();
        if (deviceId == attFusedAltitudeId) {
            if (paired && mAltitudeAndSlopeDevice_ATTFused == null) {
                try {
                    mAltitudeAndSlopeDevice_ATTFused = new AltitudeAndSlopeDevice_ATTFused(mContext, mSensorManager);
                } catch (Exception e) {
                    Log.w(TAG, "Could not initialize ATT Fused altitude device on pairing change: " + e.getMessage());
                }
            } else if (!paired && mAltitudeAndSlopeDevice_ATTFused != null) {
                mAltitudeAndSlopeDevice_ATTFused.shutDown();
                mAltitudeAndSlopeDevice_ATTFused = null;
            }
            return;
        }


        if (mMyRemoteDevices.containsKey(deviceId)) {
            if (paired) {  // mMyRemoteDevices should contain only paired devices, so this should never ever happen!
                Log.d(TAG, "BUG: an already paired device became paired");
            } else {
                removeAndStopRemoteDevice(deviceId);
            }
        } else { // device not yet in map
            if (!paired) {
                Log.d(TAG, "BUG: an unpaired device became unpaired");
            } else {
                MyRemoteDevice myRemoteDevice = createRemoteDevice(deviceId);
                if (myRemoteDevice != null) {
                    mMyRemoteDeviceTries.put(myRemoteDevice, 1);   // 1 should be sufficient here.
                    if (!isSearchingForARemoteDevice()) {
                        searchForNextRemoteDevice();
                    }
                }
            }
        }
    }

    protected void searchForNextRemoteDevice() {
        if (DEBUG) Log.d(TAG, "searchForNextRemoteDevice");

        synchronized (mSearchQueue) {
            while (true) {
                MyRemoteDevice next = mSearchQueue.pollFirst();
                cMyRemoteDeviceCurrentlySearchingFor = next;
                if (next == null) {
                    if (DEBUG) Log.d(TAG, "empty search queue");
                    // finished searching, broadcast this
                    mContext.sendBroadcast(new Intent(BANALService.SEARCHING_FINISHED_FOR_ALL_INTENT)
                            .setPackage(mContext.getPackageName()));
                    break;
                } else if (next.isSearching()) {
                    // the device is still searching => probably something went wrong
                    Log.d(TAG, "BUG: should start searching for an already searching device");
                    break;
                } else if (next.isReceivingData()) { // if the device is already receiving data, we do not have to search for it
                    if (DEBUG) Log.i(TAG, "device is already receiving data, so we continue");
                    continue;
                } else {
                    BSportType bSportType = mBanalService.getUserSelectedBSportType();

                    if (!TrainingApplication.searchOnlyForSportSpecificDevices()                                       // either, the sport type is ignored
                            || bSportType == null || bSportType == UNKNOWN                                             // or the sport type is not yet defined
                            || next.getDeviceType().getSportType() == UNKNOWN          // or the device is not sport specific (e.g. a heart rate monitor)
                            || bSportType == next.getDeviceType().getSportType()       // or it is the correct sport type
                    ) {
                        if (DEBUG)
                            Log.i(TAG, "starting to search for the device at the head of the queue");
                        next.startSearching();                                          // then, we start searching for this device.
                        break;
                    } else {
                        if (DEBUG)
                            Log.i(TAG, "Device type at the head of the search queue does not fit to the user selected sport type => this device will be ignored");
                        continue;                                                       // Otherwise, we search for the next device.
                    }
                }
            }
        }
    }

    public MyRemoteDevice getCurrentlySearchingForRemoteDevice() {
        synchronized (mSearchQueue) {
            return cMyRemoteDeviceCurrentlySearchingFor;
        }
    }

    @Nullable
    public String getNameOfSearchingDevice() {
        synchronized (mSearchQueue) {
            MyRemoteDevice current = cMyRemoteDeviceCurrentlySearchingFor;
            return current == null ? null : current.getName();
        }
    }

    public void startSearchForPairedDevices() {
        if (DEBUG) Log.i(TAG, "startSearchForPairedDevices()");

        if (isSearchingForARemoteDevice()) {
            if (DEBUG)
                Log.i(TAG, "in startSearchForPairedDevices(): already searching => stop this search");
            return;  //immediately return when we are already searching
        }


        // first, save all existing devices
        LinkedList<MyRemoteDevice> existingRemoteDevices = getRemoteDeviceList();

        // create all paired devices and add them to the search queue (thereby existing devices will not be recreated)
        createPairedRemoteDevices(Protocol.ANT_PLUS);  // TODO: first check whether ANT_PLUS is available?
        createPairedRemoteDevices(Protocol.BLUETOOTH_LE); // TODO: check whether BT is available,
        if (DEBUG) Log.i(TAG, "in startSearchForPairedDevices(): added all paired remote devices");

        // copy the existing remote devices to the head of the search queue
        // we want to search for existing devices first
        // they were already available
        for (MyRemoteDevice myRemoteDevice : existingRemoteDevices) {
            if (DEBUG)
                Log.i(TAG, "adding existing device with ID " + myRemoteDevice.getDeviceId() + " to the head of the search queue");
            mSearchQueue.addFirst(myRemoteDevice);
        }

        // Defense-in-depth hardware connection clamping on Free tier (REQ-SET-084.7)
        if (isFreeTierQuotaRestricted() && mSearchQueue.size() > com.atrainingtracker.trainingtracker.monetization.SensorQuotaReconciliationManager.MAX_FREE_PAIRED_DEVICES) {
            Log.i(TAG, "startSearchForPairedDevices(): Clamping search queue from " + mSearchQueue.size() + " to " + com.atrainingtracker.trainingtracker.monetization.SensorQuotaReconciliationManager.MAX_FREE_PAIRED_DEVICES + " on Free tier");
            while (mSearchQueue.size() > com.atrainingtracker.trainingtracker.monetization.SensorQuotaReconciliationManager.MAX_FREE_PAIRED_DEVICES) {
                mSearchQueue.removeLast();
            }
        }

        // give all devices the same number of chances ...
        mMyRemoteDeviceTries.clear();
        for (MyRemoteDevice myRemoteDevice : mSearchQueue) {
            mMyRemoteDeviceTries.put(myRemoteDevice, TrainingApplication.getNumberOfSearchTries());
        }

        // now, start searching
        mContext.sendBroadcast(new Intent(BANALService.SEARCHING_STARTED_FOR_ALL_INTENT)
                .setPackage(mContext.getPackageName()));
        searchForNextRemoteDevice();
    }

    @Nonnull
    public BSportType getSportType() {
        if (DEBUG) Log.d(TAG, "getSportType");

        BSportType sportType = BSportType.UNKNOWN;

        for (MyRemoteDevice remoteDevice : getRemoteDeviceList()) {
            if (remoteDevice.isReceivingData()) {
                sportType = sportType.or(remoteDevice.getDeviceType().getSportType());
            }
        }

        if (DEBUG) Log.d(TAG, "getSportType: returning " + sportType);

        return sportType;
    }

    public String getMainSensorStringValue(long deviceId) {
        MyRemoteDevice remoteDevice = mMyRemoteDevices.get(deviceId);
        return (remoteDevice == null) ? getStringSafely(R.string.NoData, "No Data") : remoteDevice.getMainSensorStringValue();
    }

    public List<MyRemoteDevice> getActiveRemoteDevices() {
        List<MyRemoteDevice> result = new LinkedList<>();

        for (MyRemoteDevice remoteDevice : getRemoteDeviceList()) {
            if (remoteDevice.isReceivingData()) {
                result.add(remoteDevice);
            }
        }

        return result;
    }

    public List<SpeedAndLocationDevice> getActiveSpeedAndLocationDevices() {
        List<SpeedAndLocationDevice> result = new LinkedList<>();

        SpeedAndLocationDevice gps = mSpeedAndLocationDevice_GPS;
        if (gps != null) {
            result.add(gps);
        }

        SpeedAndLocationDevice fused = mSpeedAndLocationDevice_GoogleFused;
        if (fused != null) {
            result.add(fused);
        }

        SpeedAndLocationDevice attFused = mSpeedAndLocationDevice_ATTFused;
        if (attFused != null) {
            result.add(attFused);
        }

        return result;
    }

    public SpeedAndLocationDevice getSpeedAndLocationDevice_GPS() {
        return mSpeedAndLocationDevice_GPS;
    }

    public SpeedAndLocationDevice getSpeedAndLocationDevice_GoogleFused() {
        return mSpeedAndLocationDevice_GoogleFused;
    }

    public SpeedAndLocationDevice_ATTFused getSpeedAndLocationDevice_ATTFused() {
        return mSpeedAndLocationDevice_ATTFused;
    }

    /**
     * REQ-TRK-077.1: Sets indoor workout mode on DeviceManager and forwards to all active
     * SpeedAndLocationDevice instances. Newly created SpeedAndLocationDevices also inherit this setting.
     */
    public synchronized void setIndoorMode(boolean indoorMode) {
        mIsIndoorMode = indoorMode;
        for (SpeedAndLocationDevice device : getActiveSpeedAndLocationDevices()) {
            if (device != null) {
                device.setIndoorMode(indoorMode);
            }
        }
    }

    /**
     * REQ-TRK-077.1: Returns whether indoor mode is active on DeviceManager.
     */
    public synchronized boolean isIndoorMode() {
        return mIsIndoorMode;
    }

    private void applyIndoorMode(SpeedAndLocationDevice device) {
        if (device != null && mIsIndoorMode) {
            device.setIndoorMode(true);
        }
    }

    public AltitudeAndSlopeDevice_ATTFused getAltitudeAndSlopeDevice_ATTFused() {
        return mAltitudeAndSlopeDevice_ATTFused;
    }

    public List<MyDevice> getActiveDevicesIncludingSpeedAndLocationDevices() {
        List<MyDevice> result = new LinkedList<>();

        result.addAll(getActiveRemoteDevices());
        result.addAll(getActiveSpeedAndLocationDevices());
        AltitudeFromPressureDevice altDevice = mAltitudeFromPressureDevice;
        if (altDevice != null) {
            result.add(altDevice);
        }
        AltitudeFromPositionDevice altPosDevice = mAltitudeFromPositionDevice;
        if (altPosDevice != null) {
            result.add(altPosDevice);
        }
        AltitudeAndSlopeDevice_ATTFused attFusedAlt = mAltitudeAndSlopeDevice_ATTFused;
        if (attFusedAlt != null) {
            result.add(attFusedAlt);
        }

        return result;
    }

    public AltitudeFromPositionDevice getAltitudeFromPositionDevice() {
        return mAltitudeFromPositionDevice;
    }

    public AltitudeFromPressureDevice getAltitudeFromPressureDevice() {
        return mAltitudeFromPressureDevice;
    }

    public boolean calibrateAltimeter(double referenceAltitude) {
        AltitudeFromPressureDevice altDevice = mAltitudeFromPressureDevice;
        if (altDevice != null) {
            return altDevice.calibrate(referenceAltitude);
        }
        return false;
    }

    public boolean isAltimeterCalibrated() {
        AltitudeFromPressureDevice altDevice = mAltitudeFromPressureDevice;
        return altDevice != null && altDevice.isCalibrated();
    }

    public List<Long> getIdsOfNewlyFoundDevices() {
        if (DEBUG) Log.i(TAG, "getIdsOfNewlyFoundDevices: " + mNewlyFoundDevices.size() + " devices found");
        return new ArrayList<>(mNewlyFoundDevices);
    }

    public List<Long> getDatabaseIdsOfActiveRemoteDevices() {
        List<Long> result = new LinkedList<Long>();

        for (MyRemoteDevice remoteDevice : getRemoteDeviceList()) {
            if (remoteDevice.isReceivingData()) {
                result.add(remoteDevice.getDeviceId());
            }
        }

        return result;
    }

    public LinkedList<MyRemoteDevice> getRemoteDeviceList() {
        if (DEBUG) Log.d(TAG, "getRemoteDeviceList: " + mMyRemoteDevices);
        if (mMyRemoteDevices != null) {
            return new LinkedList<>(mMyRemoteDevices.values());
        }
        else {
            return new LinkedList<>();
        }
    }

    @Nullable
    public MyBTLEDevice getBtleDeviceByAddress(@Nullable String address) {
        if (address == null) {
            return null;
        }
        for (MyRemoteDevice dev : getRemoteDeviceList()) {
            if (dev instanceof MyBTLEDevice) {
                MyBTLEDevice btleDev = (MyBTLEDevice) dev;
                if (address.equalsIgnoreCase(btleDev.getAddress())) {
                    return btleDev;
                }
            }
        }
        return null;
    }

    // TODO: not the best solution/approach
    protected List<MyDevice> getMyDeviceList() {
        if (DEBUG) Log.d(TAG, "getMyDeviceList");

        List<MyDevice> myDeviceList = new ArrayList<MyDevice>();

        ClockDevice clockDevice = mClockDevice;
        if (clockDevice != null) {
            myDeviceList.add(clockDevice);
        }
        BatteryDevice batteryDevice = mBatteryDevice;
        if (batteryDevice != null) {
            myDeviceList.add(batteryDevice);
        }
        SpeedAndLocationDevice gps = mSpeedAndLocationDevice_GPS;
        if (gps != null) {
            myDeviceList.add(gps);
        }
        SpeedAndLocationDevice fused = mSpeedAndLocationDevice_GoogleFused;
        if (fused != null) {
            myDeviceList.add(fused);
        }
        SpeedAndLocationDevice attFused = mSpeedAndLocationDevice_ATTFused;
        if (attFused != null) {
            myDeviceList.add(attFused);
        }
        AltitudeFromPressureDevice altDevice = mAltitudeFromPressureDevice;
        if (altDevice != null) {
            myDeviceList.add(altDevice);
        }
        AltitudeFromPositionDevice altPosDevice = mAltitudeFromPositionDevice;
        if (altPosDevice != null) {
            myDeviceList.add(altPosDevice);
        }
        AltitudeAndSlopeDevice_ATTFused attFusedAlt = mAltitudeAndSlopeDevice_ATTFused;
        if (attFusedAlt != null) {
            myDeviceList.add(attFusedAlt);
        }
        VerticalSpeedAndSlopeDevice vSpeedDevice = mVerticalSpeedAndSlopeDevice;
        if (vSpeedDevice != null) {
            myDeviceList.add(vSpeedDevice);
        }
        CyclingPowerMetricsDevice powerMetricsDevice = mCyclingPowerMetricsDevice;
        if (powerMetricsDevice != null) {
            myDeviceList.add(powerMetricsDevice);
        }
        HRVMetricsDevice hrvMetricsDevice = mHrvMetricsDevice;
        if (hrvMetricsDevice != null) {
            myDeviceList.add(hrvMetricsDevice);
        }
        myDeviceList.addAll(getRemoteDeviceList());

        return myDeviceList;
    }

    public MyDevice getMyDeviceByName(String deviceName) {
        if (deviceName == null) {
            return null;
        }

        for (MyDevice myDevice : getMyDeviceList()) {
            if (deviceName.equals(myDevice.getName())) {
                return myDevice;
            }
        }

        return null;
    }

    public void newLap() {
        if (DEBUG) Log.i(TAG, "newLap()");

        for (MyDevice myDevice : getMyDeviceList()) {
            myDevice.newLap();
        }
    }

    public List<MySensor> getSensorList(SensorType sensorType) {
        List<MySensor> mySensorList = new LinkedList<MySensor>();

        for (MyDevice myDevice : getMyDeviceList()) {
            MySensor mySensor = myDevice.getSensor(sensorType);
            if (mySensor != null) {
                mySensorList.add(mySensor);
            }
        }

        return mySensorList;
    }

    public List<MyAccumulatorSensor> getAccumulatorSensorList(SensorType sensorType) {
        List<MyAccumulatorSensor> myAccumulatorSensorList = new LinkedList<MyAccumulatorSensor>();

        for (MyDevice myDevice : getMyDeviceList()) {
            MySensor mySensor = myDevice.getSensor(sensorType);
            if (mySensor != null && mySensor instanceof MyAccumulatorSensor) {
                myAccumulatorSensorList.add((MyAccumulatorSensor) mySensor);
            }
        }

        return myAccumulatorSensorList;
    }

    protected void removeAndStopRemoteDevice(long deviceId) {
        if (DEBUG) Log.d(TAG, "removeAndStopRemoteDevice(" + deviceId + ")");

        MyRemoteDevice myRemoteDevice = mMyRemoteDevices.remove(deviceId);
        if (myRemoteDevice != null) {
            detachIfRrBeatSource(myRemoteDevice);
            mSearchQueue.remove(myRemoteDevice);
            if (cMyRemoteDeviceCurrentlySearchingFor == myRemoteDevice) {
                searchForNextRemoteDevice();
            }
            myRemoteDevice.shutDown();
        }
    }

    public MyRemoteDevice createNewANTDevice(long deviceID, DeviceType deviceType, int antDeviceNumber) {
        if (DEBUG) Log.d(TAG, "createNewANTDevice: " + deviceType.name());

        if (mMyRemoteDevices.containsKey(deviceID)) {
            if (DEBUG) Log.d(TAG, "device already created");
            return null;
        }

        MyANTDevice myANTDevice = null;
        MyRemoteDevice myRemoteDevice = null;

        switch (deviceType) {
            case HRM:
                if (DEBUG) Log.d(TAG, "create new HR device");
                myANTDevice = new ANTHeartRateDevice(mContext, mSensorManager, deviceID, antDeviceNumber);
                break;
            case RUN_SPEED:
                if (DEBUG) Log.d(TAG, "create new RUN SPEED device");
                myANTDevice = new ANTRunSpeedDevice(mContext, mSensorManager, deviceID, antDeviceNumber);
                break;
            case BIKE_SPEED:
                if (DEBUG) Log.d(TAG, "create new BIKE SPEED device");
                myANTDevice = new ANTBikeSpeedDevice(mContext, mSensorManager, deviceID, antDeviceNumber);
                break;
            case BIKE_CADENCE:
                if (DEBUG) Log.d(TAG, "create new BIKE CADENCE device");
                myANTDevice = new ANTBikeCadenceDevice(mContext, mSensorManager, deviceID, antDeviceNumber);
                break;
            case BIKE_SPEED_AND_CADENCE:
                if (DEBUG)
                    Log.d(TAG, "creating combined BikeSpeedAndCadence device => speed and cadence");
                // MyANTDevice device = new ANTBikeCadenceDevice(mContext, mSensorManager, deviceID, true);
                // device.startSearching();
                // mMyANTDeviceMap.put(deviceIDString + "-combined", device);
                myANTDevice = new ANTBikeSpeedAndCadenceDevice(mContext, mSensorManager, deviceID, antDeviceNumber);
                break;
            case BIKE_POWER:
                if (DEBUG) Log.d(TAG, "create new BIKE POWER device");
                myANTDevice = new ANTBikePowerDevice(mContext, mSensorManager, deviceID, antDeviceNumber);
                break;
            case ENVIRONMENT:
                if (DEBUG) Log.d(TAG, "create new ENVIRONMENT device");
                myANTDevice = new ANTEnvironmentDevice(mContext, mSensorManager, deviceID, antDeviceNumber);
                break;
            case BIKE_RADAR:
                if (DEBUG) Log.d(TAG, "create new BIKE RADAR device");
                myRemoteDevice = new com.atrainingtracker.banalservice.devices.ant_plus.ANTBikeRadarDevice(mContext, mSensorManager, deviceID, antDeviceNumber);
                break;
            default:
                if (DEBUG) Log.d(TAG, "ANT device not supported");
        }

        MyRemoteDevice deviceToRegister = myANTDevice != null ? myANTDevice : myRemoteDevice;
        if (deviceToRegister != null) {
            MyRemoteDevice existing = mMyRemoteDevices.putIfAbsent(deviceID, deviceToRegister);
            if (existing != null) {
                deviceToRegister.shutDown();
                return null;
            }
            attachIfRrBeatSource(deviceToRegister);
            mSearchQueue.addFirst(deviceToRegister);
        }

        return deviceToRegister;
    }

    public MyRemoteDevice createNewBluetoothLEDevice(long deviceID, DeviceType deviceType, String address) {
        if (DEBUG) Log.d(TAG, "createNewBluetoothDevice: " + deviceType.name());

        if (mMyRemoteDevices.containsKey(deviceID)) {
            if (DEBUG) Log.d(TAG, "device already created");
            return null;
        }

        MyBTLEDevice myBTLEDevice = null;
        MyRemoteDevice polarBridgeDevice = null;

        switch (deviceType) {
            case HRM:
                if (DEBUG) Log.d(TAG, "create new HR device");
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                        && mDeviceDataRepository.isPolarLiveImuBridgeEligible(deviceID, address)) {
                    polarBridgeDevice = new PolarOnlineStreamingBridge(mContext, mSensorManager, deviceID, address);
                } else {
                    myBTLEDevice = new BTLEHeartRateDevice(mContext, mSensorManager, deviceID, address);
                }
                break;

            case RUN_SPEED:
                if (DEBUG) Log.d(TAG, "create new bluetooth run speed device");
                myBTLEDevice = new BTLERunSpeedDevice(mContext, mSensorManager, deviceID, address);
                break;

            case BIKE_SPEED:
                if (DEBUG) Log.i(TAG, "create new bluetooth bike speed device");
                myBTLEDevice = new BTLEBikeSpeedDevice(mContext, mSensorManager, deviceID, address);
                break;

            case BIKE_CADENCE:
                if (DEBUG) Log.i(TAG, "create new bluetooth bike cadence device");
                myBTLEDevice = new BTLEBikeCadenceDevice(mContext, mSensorManager, deviceID, address);
                break;

            case BIKE_SPEED_AND_CADENCE:
                if (DEBUG) Log.i(TAG, "create new bluetooth bike speed and cadence device");
                myBTLEDevice = new BTLEBikeSpeedAndCadenceDevice(mContext, mSensorManager, deviceID, address);
                break;

            case BIKE_POWER:
                if (DEBUG) Log.i(TAG, "create new bluetooth bike power device");
                myBTLEDevice = new BTLEBikePowerDevice(mContext, mSensorManager, deviceID, address);
                break;

            case BIKE_RADAR:
                if (DEBUG) Log.i(TAG, "create new bluetooth bike radar device");
                myBTLEDevice = new com.atrainingtracker.banalservice.devices.bluetooth_le.BTLERadarDevice(mContext, mSensorManager, deviceID, address);
                break;

            default:
                Log.i(TAG, "Bluetooth device not yet supported");
        }

        MyRemoteDevice deviceToRegister = polarBridgeDevice != null ? polarBridgeDevice : myBTLEDevice;
        if (deviceToRegister != null) {
            MyRemoteDevice existing = mMyRemoteDevices.putIfAbsent(deviceID, deviceToRegister);
            if (existing != null) {
                deviceToRegister.shutDown();
                return null;
            }
            attachIfRrBeatSource(deviceToRegister);
            mSearchQueue.addFirst(deviceToRegister);
        }

        return deviceToRegister;
    }

    // TODO: code is very similar to createPairedRemoteDevices, so the code might be merged?
    protected MyRemoteDevice createRemoteDevice(long deviceId) {
        Protocol protocol = mDeviceDataRepository.getProtocol(deviceId);
        DeviceType deviceType = mDeviceDataRepository.getDeviceType(deviceId);
        MyRemoteDevice myRemoteDevice = null;

        if (protocol != null) {
            switch (protocol) {
                case ANT_PLUS:
                    int antDeviceNumber = mDeviceDataRepository.getAntDeviceNumber(deviceId);
                    myRemoteDevice = createNewANTDevice(deviceId, deviceType, antDeviceNumber);
                    break;
                case BLUETOOTH_LE:
                    String address = mDeviceDataRepository.getBluetoothMACAddress(deviceId);
                    myRemoteDevice = createNewBluetoothLEDevice(deviceId, deviceType, address);
                    break;
            }
        }

        return myRemoteDevice;
    }

    public void createPairedRemoteDevices(Protocol protocol) {
        if (DEBUG) Log.d(TAG, "createPairedRemoteDevices()");

        List<PairedRemoteDeviceDto> pairedDevices = mDeviceDataRepository.getPairedRemoteDevices(protocol);
        for (PairedRemoteDeviceDto device : pairedDevices) {
            if (device.isPaired()) {
                if (isFreeTierQuotaRestricted() && mMyRemoteDevices.size() >= com.atrainingtracker.trainingtracker.monetization.SensorQuotaReconciliationManager.MAX_FREE_PAIRED_DEVICES) {
                    Log.i(TAG, "createPairedRemoteDevices(): Free tier sensor quota reached (" + mMyRemoteDevices.size() + "), clamping connection");
                    break;
                }

                long deviceID = device.getId();
                if (mMyRemoteDevices.containsKey(deviceID)) {
                    if (DEBUG) Log.i(TAG, "device with ID " + deviceID + " is already created");
                    continue;
                }
                if (DEBUG) Log.d(TAG, "create new device with ID: " + deviceID);

                DeviceType deviceType = device.getDeviceType();
                if (deviceType == null) {
                    Log.w(TAG, "Skipping paired device with invalid DeviceType at ID: " + deviceID);
                    continue;
                }

                switch (protocol) {
                    case ANT_PLUS:
                        int antDeviceNumber = device.getAntDeviceNumber();
                        createNewANTDevice(deviceID, deviceType, antDeviceNumber);
                        break;

                    case BLUETOOTH_LE:
                        String address = device.getBtAddress();
                        createNewBluetoothLEDevice(deviceID, deviceType, address);
                        break;
                }
            } else {
                if (DEBUG) Log.d(TAG, "createPairedDevices(): ignoring unpaired device");
            }
        }

        if (DEBUG) Log.d(TAG, "finished createPairedDevices");
    }

    private boolean isFreeTierQuotaRestricted() {
        try {
            com.atrainingtracker.trainingtracker.monetization.ProFeatureManager proManager =
                    com.atrainingtracker.trainingtracker.monetization.ProFeatureManager.getInstance(mContext);
            return !proManager.isProActive().getValue();
        } catch (Throwable t) {
            return false;
        }
    }

    public void startSearchForNewRemoteDevices(Protocol protocol, DeviceType deviceType) {
        if (DEBUG) Log.i(TAG, "startSearchForNewRemoteDevices");

        if (deviceType == DeviceType.ALL) return;

        mNewlyFoundDevices.clear();

        switch (protocol) {
            case ANT_PLUS:

                ANTSearchForNewDevicesEngineMultiDeviceSearch prevAntEngine = mAntAsyncSearchEngine;
                if (prevAntEngine != null) {
                    prevAntEngine.stopAsyncSearch();
                }

                ANTSearchForNewDevicesEngineMultiDeviceSearch newAntEngine = new ANTSearchForNewDevicesEngineMultiDeviceSearch(mContext, deviceType, mANTAsyncSearchEngineInterface);
                mAntAsyncSearchEngine = newAntEngine;
                newAntEngine.startAsyncSearch();

                break;

            case BLUETOOTH_LE:

                BTSearchForNewDevicesEngine prevBtEngine = mBTSearchForNewDevicesEngine;
                if (prevBtEngine != null) {
                    if (DEBUG) Log.i(TAG, "stopping previous asyncSearchEngine");
                    prevBtEngine.stopAsyncSearch();
                }

                BTSearchForNewDevicesEngine newBtEngine = new BTSearchForNewDevicesEngine(mContext, deviceType, mBTSearchInterface);
                mBTSearchForNewDevicesEngine = newBtEngine;
                newBtEngine.startAsyncSearch();

                break;
        }
    }

    public void stopSearchForNewRemoteDevices() {
        if (DEBUG) Log.i(TAG, "stopSearchForNewRemoteDevices");

        mNewlyFoundDevices.clear();

        // ANT_PLUS:
        ANTSearchForNewDevicesEngineMultiDeviceSearch antEngine = mAntAsyncSearchEngine;
        mAntAsyncSearchEngine = null;
        if (antEngine != null) {
            antEngine.stopAsyncSearch();
        }

        // BLUETOOTH_LE:
        BTSearchForNewDevicesEngine btEngine = mBTSearchForNewDevicesEngine;
        mBTSearchForNewDevicesEngine = null;
        if (btEngine != null) {
            btEngine.stopAsyncSearch();
        }
    }

    protected void sendNewDeviceFoundBroadcast(long deviceId) {
        if (DEBUG) Log.i(TAG, "sendNewDeviceFountBroadcast");

        Intent intent = new Intent(BANALService.NEW_DEVICE_FOUND_INTENT)
                .putExtra(BANALService.DEVICE_ID, deviceId)
                .setPackage(mContext.getPackageName());
        mContext.sendBroadcast(intent);
    }

    private static boolean isProviderAvailableSafely(LocationManager locationManager, String provider) {
        if (locationManager == null || provider == null) {
            return false;
        }
        try {
            return locationManager.getProvider(provider) != null;
        } catch (IllegalArgumentException | SecurityException e) {
            Log.w(TAG, "Failed to query location provider " + provider + ": " + e.getMessage());
            return false;
        }
    }

}
