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

package com.atrainingtracker.banalservice.devices.bluetooth_le;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.atrainingtracker.banalservice.BANALService;
import com.atrainingtracker.banalservice.devices.DeviceType;
import com.atrainingtracker.banalservice.devices.radar.LaneState;
import com.atrainingtracker.banalservice.devices.radar.RadarConnectionState;
import com.atrainingtracker.banalservice.devices.radar.RadarLatchedSnapshot;
import com.atrainingtracker.banalservice.devices.radar.RadarLightController;
import com.atrainingtracker.banalservice.devices.radar.RadarLightMode;
import com.atrainingtracker.banalservice.devices.radar.RadarLightState;
import com.atrainingtracker.banalservice.devices.radar.RadarSource;
import com.atrainingtracker.banalservice.devices.radar.RadarStateRepository;
import com.atrainingtracker.banalservice.devices.radar.RadarTarget;
import com.atrainingtracker.banalservice.devices.radar.RadarThreatState;
import com.atrainingtracker.banalservice.devices.radar.RadarWindowAggregator;
import com.atrainingtracker.banalservice.devices.radar.ThreatLevel;
import com.atrainingtracker.banalservice.devices.radar.VehicleSizeCategory;
import com.atrainingtracker.banalservice.sensor.MySensor;
import com.atrainingtracker.banalservice.sensor.MySensorManager;
import com.atrainingtracker.banalservice.sensor.SensorType;
import com.atrainingtracker.BuildConfig;
import com.atrainingtracker.trainingtracker.settings.SettingsDataStoreJavaHelper;
import com.atrainingtracker.util.LifecycleUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import com.atrainingtracker.banalservice.devices.radar.RadarLightDeviceClient;
import java.util.UUID;

/**
 * Bluetooth Low Energy cycling radar protocol driver for Garmin Varia (REQ-CON-028.4).
 *
 * Subscribes to V1 characteristic 6a4e3203, demuxes threat packets from heartbeats/sector frames,
 * publishes high-rate (4–8 Hz) multi-target streams to [RadarStateRepository], maintains a 3.5s
 * fail-safe disconnect watchdog, and latches 1 Hz scalar metrics via [RadarWindowAggregator].
 */
public class BTLERadarDevice extends MyBTLEDevice implements RadarLightDeviceClient {
    private static final String TAG = "BTLERadarDevice";
    private static final boolean DEBUG = BANALService.getDebug(false);

    public static final long WATCHDOG_TIMEOUT_MS = 3500L;
    public static final long STALE_TRACK_TIMEOUT_MS = 2000L;
    public static final long STALE_TRACK_MOVING_MS = 800L;
    public static final long STALE_TRACK_PARKED_MS = 5000L;
    public static final long V2_SILENCE_TIMEOUT_MS = 1500L;
    public static final long MODEL_NUMBER_READ_TIMEOUT_MS = 3000L;

    public static class TrackState {
        public final float rangeMeters;
        public final float approachSpeedMps;
        public final ThreatLevel threatLevel;
        public final long timestampMs;

        public TrackState(float rangeMeters, float approachSpeedMps, ThreatLevel threatLevel, long timestampMs) {
            this.rangeMeters = rangeMeters;
            this.approachSpeedMps = approachSpeedMps;
            this.threatLevel = threatLevel;
            this.timestampMs = timestampMs;
        }
    }

    private final java.util.Map<Integer, TrackState> mTrackHistory = new java.util.HashMap<>();

    protected MySensor<Integer> mThreatCountSensor;
    protected MySensor<Double> mClosestDistanceSensor;
    protected MySensor<Double> mApproachSpeedSensor;
    protected MySensor<Integer> mVehicleCountSensor;
    protected MySensor<Double> mPassingDistanceSensor;
    protected MySensor<Integer> mLargeVehicleCountSensor;

    private boolean mIsV2Stream = false;
    private final Object mBondReceiverLock = new Object();
    private boolean mBondReceiverRegistered = false;
    private boolean mBondSessionActive = false;
    private boolean mBondDeviceShutDown = false;
    private final BroadcastReceiver mBondStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || !BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(intent.getAction())) {
                return;
            }
            int bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR);
            BluetoothDevice device = getBluetoothDeviceExtra(intent);
            handleBondStateChanged(device != null ? device.getAddress() : null, bondState);
        }
    };
    private volatile BluetoothGattCharacteristic mLightControlCharacteristic;
    private volatile BluetoothGattCharacteristic mRadarControlWriteChar;
    private volatile BluetoothGattCharacteristic mRadarControlNotifyChar;
    private volatile long mLastLightModeCommandTimeMs = 0L;
    public static final long ECHO_GUARD_WINDOW_MS = 1000L;
    private volatile boolean mIsLightControlSupported = false;
    private volatile boolean mIsStvzo = false;
    private volatile boolean mHasHardwareBrakeSensor = false;
    private volatile String mModelNumber = null;
    private final Object mLightRegistrationLock = new Object();
    private boolean mAwaitingModelNumberForRegistration = false;
    private boolean mDisconnectedSinceConfigure = false;
    @Nullable
    private BluetoothGatt mActiveRegistrationGatt = null;
    private volatile RadarLightMode mLastConfiguredLightMode = null;
    private volatile RadarLightMode mPendingLightMode = null;
    private volatile RadarLightMode mConfirmedLightMode = null;
    private long mLastWindowSampleMs = 0L;

    private RadarWindowAggregator mWindowAggregator = new RadarWindowAggregator(SystemClock::elapsedRealtime);

    private void pruneStaleTracks(long nowMs) {
        synchronized (mTrackHistory) {
            java.util.Iterator<java.util.Map.Entry<Integer, TrackState>> it = mTrackHistory.entrySet().iterator();
            while (it.hasNext()) {
                TrackState state = it.next().getValue();
                long timeout = state.approachSpeedMps > 0.0f ? STALE_TRACK_MOVING_MS : STALE_TRACK_PARKED_MS;
                if (nowMs - state.timestampMs > timeout) {
                    it.remove();
                }
            }
        }
    }

    private final Runnable mV2SilenceRunnable = new Runnable() {
        @Override
        public void run() {
            if (DEBUG) Log.w(TAG, "V2 silence watchdog expired (1500ms with 0 notifications). Falling back to V1 (REQ-CON-030.2)");
            final BluetoothGatt gatt = mBluetoothGatt;
            if (gatt != null) {
                BluetoothGattService service = gatt.getService(BluetoothConstants.UUID_SERVICE_RADAR);
                if (service != null) {
                    fallbackToV1(gatt, service);
                }
            }
        }
    };

    private final Runnable mModelNumberFallbackRunnable = () ->
            completeLightControlRegistration(null, null, false, false, true, true);

    private final Runnable mWatchdogRunnable = new Runnable() {
        @Override
        public void run() {
            if (DEBUG) Log.w(TAG, "Radar packet starvation (3.5s timeout); transitioning to DISCONNECTED_ERROR");
            synchronized (mTrackHistory) {
                mTrackHistory.clear();
            }
            deactivateBondSession(false);
            RadarStateRepository.updateThreatState(createBleThreatState(
                    RadarConnectionState.DISCONNECTED_ERROR,
                    ThreatLevel.NONE,
                    Collections.emptyList()
            ));
            syncLightControlSupported();
            resetSensorsToClear();
        }
    };

    @Nullable
    @SuppressWarnings("deprecation")
    private static BluetoothDevice getBluetoothDeviceExtra(@NonNull Intent intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice.class);
        }
        return intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
    }

    private static boolean matchesDeviceAddress(@Nullable String expected, @Nullable String actual) {
        String normExpected = expected != null ? expected.trim() : "";
        String normActual = actual != null ? actual.trim() : "";
        if (normExpected.isEmpty()) {
            return true;
        }
        if (normActual.isEmpty()) {
            return false;
        }
        return normExpected.equalsIgnoreCase(normActual);
    }

    private void registerBondStateReceiverLocked() {
        if (mBondReceiverRegistered || mIsShutDown || mBondDeviceShutDown || mContext == null || Build.VERSION.SDK_INT == 0) {
            return;
        }
        try {
            IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
            ContextCompat.registerReceiver(mContext, mBondStateReceiver, filter, ContextCompat.RECEIVER_EXPORTED);
            mBondReceiverRegistered = true;
        } catch (RuntimeException e) {
            Log.w(TAG, "Failed to register ACTION_BOND_STATE_CHANGED receiver", e);
        }
    }

    private void unregisterBondStateReceiverLocked() {
        if (!mBondReceiverRegistered) {
            return;
        }
        mBondReceiverRegistered = false;
        if (mContext != null) {
            LifecycleUtils.safeUnregisterReceiver(mContext, mBondStateReceiver, TAG);
        }
    }

    private void deactivateBondSession(boolean permanentShutDown) {
        synchronized (mBondReceiverLock) {
            if (permanentShutDown) {
                mBondDeviceShutDown = true;
            }
            mBondSessionActive = false;
            unregisterBondStateReceiverLocked();
        }
        RadarStateRepository.setV2BondingRequired(false, null);
    }

    /**
     * Synchronizes V2 Bluetooth bonding-required status with [RadarStateRepository] (SSoT) and
     * manages lazy [BluetoothDevice.ACTION_BOND_STATE_CHANGED] receiver registration (`REQ-CON-061.1`).
     */
    private void setV2BondingRequiredState(boolean required) {
        if (required) {
            synchronized (mBondReceiverLock) {
                if (mIsShutDown || mBondDeviceShutDown || !mBondSessionActive) {
                    return;
                }
                registerBondStateReceiverLocked();
            }
            RadarStateRepository.setV2BondingRequired(true, mAddress);
            boolean stillActive;
            synchronized (mBondReceiverLock) {
                stillActive = !mIsShutDown && !mBondDeviceShutDown && mBondSessionActive;
                if (!stillActive) {
                    unregisterBondStateReceiverLocked();
                }
            }
            if (!stillActive) {
                RadarStateRepository.setV2BondingRequired(false, null);
            }
        } else {
            synchronized (mBondReceiverLock) {
                unregisterBondStateReceiverLocked();
            }
            RadarStateRepository.setV2BondingRequired(false, null);
        }
    }

    /**
     * Clears V2 bonding-required status when this radar peripheral completes Bluetooth bonding
     * (`REQ-CON-061.1`). Package-visible for deterministic JVM unit testing.
     */
    void handleBondStateChanged(@Nullable String deviceAddress, int bondState) {
        if (mIsShutDown || bondState != BluetoothDevice.BOND_BONDED) {
            return;
        }
        if (matchesDeviceAddress(mAddress, deviceAddress)) {
            if (DEBUG) Log.i(TAG, "Radar peripheral bonded (" + deviceAddress + "); clearing V2 bonding hint.");
            setV2BondingRequiredState(false);
        }
    }

    private static RadarThreatState createBleThreatState(
            RadarConnectionState connectionState,
            ThreatLevel threatLevel,
            List<RadarTarget> targets
    ) {
        return new RadarThreatState(
                connectionState,
                threatLevel,
                targets,
                SystemClock.elapsedRealtime(),
                RadarSource.BLE
        );
    }

    private void syncLightControlSupported() {
        final boolean supported = mIsLightControlSupported;
        RadarStateRepository.updateLightState(current ->
                current.isLightControlSupported() == supported
                        ? current
                        : new RadarLightState(current.getLightMode(), supported, current.isAutoMode())
        );
    }

    public BTLERadarDevice(Context context, MySensorManager mySensorManager, long deviceID, String address) {
        super(context, mySensorManager, DeviceType.BIKE_RADAR, deviceID, address);
        if (DEBUG) Log.d(TAG, "Creating BTLERadarDevice for " + address);
    }

    @Override
    public boolean isReceivingData() {
        return super.isReceivingData() && RadarStateRepository.getThreatState(RadarSource.BLE).getConnectionState() == RadarConnectionState.CONNECTED;
    }

    @Override
    public void startSearching() {
        super.startSearching();
        RadarStateRepository.updateThreatStateIf(currentState -> {
            if (currentState.getConnectionState() == RadarConnectionState.DISCONNECTED_ERROR) {
                return null;
            }
            return createBleThreatState(
                    RadarConnectionState.CONNECTING,
                    ThreatLevel.NONE,
                    Collections.emptyList()
            );
        });
    }

    @Override
    protected void notifyStopSearching(boolean success) {
        super.notifyStopSearching(success);
        if (!success) {
            RadarStateRepository.updateThreatStateIf(currentState -> {
                if (currentState.getConnectionState() == RadarConnectionState.DISCONNECTED_ERROR) {
                    return null;
                }
                return createBleThreatState(
                        RadarConnectionState.DISCONNECTED,
                        ThreatLevel.NONE,
                        Collections.emptyList()
                );
            });
        }
    }

    @Override
    protected void addSensors() {
        mThreatCountSensor = new MySensor<>(this, SensorType.RADAR_THREAT_COUNT);
        addSensor(mThreatCountSensor);

        mClosestDistanceSensor = new MySensor<>(this, SensorType.RADAR_CLOSEST_DISTANCE_m);
        addSensor(mClosestDistanceSensor);

        mApproachSpeedSensor = new MySensor<>(this, SensorType.RADAR_APPROACH_SPEED_mps);
        addSensor(mApproachSpeedSensor);

        mVehicleCountSensor = new MySensor<>(this, SensorType.RADAR_VEHICLE_COUNT);
        addSensor(mVehicleCountSensor);

        mPassingDistanceSensor = new MySensor<>(this, SensorType.RADAR_PASSING_DISTANCE_m);
        addSensor(mPassingDistanceSensor);

        mLargeVehicleCountSensor = new MySensor<>(this, SensorType.RADAR_LARGE_VEHICLE_COUNT);
        addSensor(mLargeVehicleCountSensor);
    }

    @Override
    @android.annotation.SuppressLint("MissingPermission")
    protected void configureServices(BluetoothGatt gatt) {
        BluetoothGattService btGattService = gatt.getService(BluetoothConstants.UUID_SERVICE_RADAR);
        if (btGattService == null) {
            if (DEBUG) Log.w(TAG, "Radar service not found!");
            return;
        }

        // Clear any prior V2 bonding hint/receiver before re-evaluating capabilities on (re-)connect (REQ-CON-061.1)
        deactivateBondSession(false);

        // Radar service discovered: transition to CONNECTED and arm 3.5s fail-safe watchdog (ATT-7543 / REQ-CON-028.4)
        RadarStateRepository.updateThreatState(createBleThreatState(
                RadarConnectionState.CONNECTED,
                ThreatLevel.NONE,
                Collections.emptyList()
        ));
        synchronized (mBondReceiverLock) {
            mBondSessionActive = !mIsShutDown && !mBondDeviceShutDown;
        }
        syncLightControlSupported();
        if (mHandler != null) {
            mHandler.removeCallbacks(mWatchdogRunnable);
            mHandler.postDelayed(mWatchdogRunnable, WATCHDOG_TIMEOUT_MS);
        }

        // Ensure Battery CCCD subscription (REQ-CON-007, ATT-3312, REQ-CON-030.1) is queued before V2
        if (mBatteryCharacteristic == null) {
            BluetoothGattService batteryService = gatt.getService(BluetoothConstants.UUID_SERVICE_BATTERY);
            if (batteryService != null) {
                mBatteryCharacteristic = batteryService.getCharacteristic(BluetoothConstants.UUID_CHARACTERISTIC_BATTERY_LEVEL);
            }
        }
        if (mBatteryCharacteristic != null && (mBatteryCharacteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
            try {
                gatt.setCharacteristicNotification(mBatteryCharacteristic, true);
            } catch (SecurityException e) {
                Log.w(TAG, "Missing BLUETOOTH_CONNECT permission enabling battery notifications", e);
            }
            BluetoothGattDescriptor batteryDescriptor = mBatteryCharacteristic.getDescriptor(BluetoothConstants.UUID_CHARACTERISTIC_CLIENT_CONFIG);
            if (batteryDescriptor != null) {
                enqueueWriteDescriptor(batteryDescriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            }
        }

        if (mHandler != null) {
            mHandler.removeCallbacks(mModelNumberFallbackRunnable);
        }
        synchronized (mLightRegistrationLock) {
            mDisconnectedSinceConfigure = false;
            mActiveRegistrationGatt = gatt;
            mAwaitingModelNumberForRegistration = false;
            mIsStvzo = false;
            mHasHardwareBrakeSensor = false;
            mModelNumber = null;
        }

        // Device Information Service discovery for StVZO model number detection (ATT-6707 / REQ-CON-031.1 / REQ-CON-055.2)
        boolean awaitingModelRead = false;
        BluetoothGattService disService = gatt.getService(BluetoothConstants.UUID_SERVICE_DEVICE_INFORMATION);
        if (disService != null) {
            BluetoothGattCharacteristic modelChar = disService.getCharacteristic(BluetoothConstants.UUID_CHARACTERISTIC_MODEL_NUMBER);
            if (modelChar != null) {
                awaitingModelRead = true;
                enqueueReadCharacteristic(modelChar);
            }
        }

        BluetoothGattCharacteristic v2Char = btGattService.getCharacteristic(BluetoothConstants.UUID_CHARACTERISTIC_RADAR_V2);
        BluetoothDevice device = gatt.getDevice();
        boolean isBonded = (device != null && device.getBondState() == BluetoothDevice.BOND_BONDED);
        boolean isV1Override = BuildConfig.DEBUG && SettingsDataStoreJavaHelper.isRadarV1OnlyOverrideEnabled(mContext);

        if (isV1Override) {
            Log.w(TAG, "Developer override enabled: radar_v1_only_override=true. Bypassing V2 discovery and forcing legacy V1 protocol (REQ-CON-030.8).");
            fallbackToV1(gatt, btGattService);
        } else if (v2Char != null && isBonded) {
            if (DEBUG) Log.i(TAG, "Garmin Varia V2 characteristic 6a4e3204 discovered and device is bonded. Initiating V2 handshake.");
            mIsV2Stream = true;
            enqueueRequestMtu(247);

            try {
                gatt.setCharacteristicNotification(v2Char, true);
            } catch (SecurityException e) {
                Log.w(TAG, "Missing BLUETOOTH_CONNECT permission enabling V2 radar notifications", e);
            }
            BluetoothGattDescriptor descriptor = v2Char.getDescriptor(BluetoothConstants.UUID_CHARACTERISTIC_CLIENT_CONFIG);
            if (descriptor != null) {
                byte[] val = (v2Char.getProperties() & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0
                        ? BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                        : BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE;
                enqueueWriteDescriptor(descriptor, val);
            }
        } else {
            if (v2Char != null && !isBonded) {
                Log.w(TAG, "Garmin Varia V2 requires LESC bonding, but device is not bonded (bondState=" + (device != null ? device.getBondState() : "null") + "). Falling back to V1.");
                setV2BondingRequiredState(true);
            }
            fallbackToV1(gatt, btGattService);
        }

        // RearVue 820 tail light control service discovery (ATT-7545 / REQ-CON-031.1)
        BluetoothGattService controlService = gatt.getService(BluetoothConstants.UUID_SERVICE_RADAR_CONTROL);
        BluetoothGattCharacteristic controlWriteChar = controlService != null
                ? controlService.getCharacteristic(BluetoothConstants.UUID_CHARACTERISTIC_RADAR_CONTROL_WRITE)
                : null;
        BluetoothGattCharacteristic controlNotifyChar = controlService != null
                ? controlService.getCharacteristic(BluetoothConstants.UUID_CHARACTERISTIC_RADAR_CONTROL_NOTIFY)
                : null;

        if (controlWriteChar != null && controlNotifyChar != null) {
            if (DEBUG) Log.i(TAG, "Garmin RearVue 820 control service 6a4e2f00 discovered (REQ-CON-031.1).");
            controlWriteChar.setWriteType(resolveControlWriteType(controlWriteChar));
            mRadarControlWriteChar = controlWriteChar;
            mRadarControlNotifyChar = controlNotifyChar;
            mLightControlCharacteristic = controlWriteChar;
            mIsLightControlSupported = true;

            // Garmin RearVue 820 requires subscribing 6a4e2f11 (WRITE, INDICATE) CCCD before control writes (REQ-CON-031.1).
            enableCharacteristicSubscription(gatt, controlWriteChar, true, "radar control write 6a4e2f11");
            enableCharacteristicSubscription(gatt, controlNotifyChar, false, "radar control notify 6a4e2f14");
        } else {
            mRadarControlWriteChar = null;
            mRadarControlNotifyChar = null;
            // Legacy tail light control characteristic fallback (ATT-5102 / REQ-CON-031.1)
            BluetoothGattCharacteristic lightChar = btGattService.getCharacteristic(BluetoothConstants.UUID_CHARACTERISTIC_RADAR_LIGHT_CONTROL);
            if (lightChar != null) {
                if (DEBUG) Log.i(TAG, "Garmin Varia light control characteristic 6a4e3202 discovered (REQ-CON-031.1).");
                lightChar.setWriteType(resolveControlWriteType(lightChar));
                mLightControlCharacteristic = lightChar;
                mIsLightControlSupported = true;

                // Subscribe or read initial physical light mode
                if ((lightChar.getProperties() & (BluetoothGattCharacteristic.PROPERTY_NOTIFY | BluetoothGattCharacteristic.PROPERTY_INDICATE)) != 0) {
                    enableCharacteristicSubscription(gatt, lightChar, false, "legacy light 6a4e3202");
                } else if ((lightChar.getProperties() & BluetoothGattCharacteristic.PROPERTY_READ) != 0) {
                    enqueueReadCharacteristic(lightChar);
                }
            } else {
                mLightControlCharacteristic = null;
                mIsLightControlSupported = false;
            }
        }

        // Defer RadarLightController registration and initial light mode dispatch until Model Number read resolves (REQ-CON-055.2)
        if (mIsLightControlSupported && awaitingModelRead) {
            synchronized (mLightRegistrationLock) {
                if (!mIsShutDown && !mDisconnectedSinceConfigure) {
                    mAwaitingModelNumberForRegistration = true;
                    if (mHandler != null) mHandler.postDelayed(mModelNumberFallbackRunnable, MODEL_NUMBER_READ_TIMEOUT_MS);
                }
            }
        } else {
            completeLightControlRegistration(gatt, null, false, false, mIsLightControlSupported, false);
        }
    }

    private void completeLightControlRegistration(
            @Nullable BluetoothGatt sourceGatt, @Nullable String model, boolean isStvzo,
            boolean hasHardwareBrake, boolean reapplyActiveMode, boolean requirePending) {
        final RadarLightMode modeToApply;
        final boolean clamped;
        final boolean isLegacyLightOnly;
        synchronized (mLightRegistrationLock) {
            if (mIsShutDown || mDisconnectedSinceConfigure
                    || (sourceGatt != null && mActiveRegistrationGatt != null && sourceGatt != mActiveRegistrationGatt)
                    || (requirePending && !mAwaitingModelNumberForRegistration)) return;
            if (mHandler != null) mHandler.removeCallbacks(mModelNumberFallbackRunnable);
            boolean wasPending = mAwaitingModelNumberForRegistration;
            mAwaitingModelNumberForRegistration = false;
            if (model != null) mModelNumber = model;
            mIsStvzo = isStvzo;
            mHasHardwareBrakeSensor = hasHardwareBrake;
            boolean lightSupported = mIsLightControlSupported;
            isLegacyLightOnly = (mRadarControlWriteChar == null);
            RadarLightController.registerRadarDevice(this, lightSupported, isStvzo, hasHardwareBrake);
            RadarLightMode activeMode = RadarLightController.getCurrentMode().getValue();
            boolean needsStvzoSelfHeal = isStvzo && activeMode != RadarLightMode.OFF && !activeMode.isStvzoCompliant();
            if (lightSupported && activeMode != RadarLightMode.OFF && (reapplyActiveMode || wasPending || needsStvzoSelfHeal)) {
                modeToApply = needsStvzoSelfHeal ? RadarLightMode.SOLID : activeMode;
                clamped = modeToApply != activeMode;
            } else {
                modeToApply = null;
                clamped = false;
            }
        }
        if (modeToApply != null) {
            boolean writeQueued = setLightMode(modeToApply);
            if (writeQueued && clamped && isLegacyLightOnly) {
                synchronized (mLightRegistrationLock) {
                    if (!mIsShutDown && !mDisconnectedSinceConfigure
                            && (sourceGatt == null || mActiveRegistrationGatt == null || sourceGatt == mActiveRegistrationGatt)) {
                        RadarLightController.updateHardwareLightMode(modeToApply);
                    }
                }
            }
        }
    }

    @android.annotation.SuppressLint("MissingPermission")
    public void fallbackToV1(BluetoothGatt gatt, BluetoothGattService btGattService) {
        if (mHandler != null) {
            mHandler.removeCallbacks(mV2SilenceRunnable);
        }
        if (DEBUG) Log.i(TAG, "Falling back to legacy V1 characteristic 6a4e3203 (REQ-CON-030.2)");
        mIsV2Stream = false;
        BluetoothGattCharacteristic v1Char = btGattService.getCharacteristic(BluetoothConstants.UUID_CHARACTERISTIC_RADAR_V1);
        if (v1Char != null) {
            try {
                gatt.setCharacteristicNotification(v1Char, true);
            } catch (SecurityException e) {
                Log.w(TAG, "Missing BLUETOOTH_CONNECT permission enabling V1 radar notifications", e);
            }
            BluetoothGattDescriptor descriptor = v1Char.getDescriptor(BluetoothConstants.UUID_CHARACTERISTIC_CLIENT_CONFIG);
            if (descriptor != null) {
                enqueueWriteDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            }
        } else {
            if (DEBUG) Log.w(TAG, "Neither V2 nor V1 radar characteristics found!");
        }
    }

    @android.annotation.SuppressLint("MissingPermission")
    public void fallbackToV1(@Nullable BluetoothGatt gatt) {
        if (gatt != null) {
            BluetoothGattService service = gatt.getService(BluetoothConstants.UUID_SERVICE_RADAR);
            if (service != null) {
                fallbackToV1(gatt, service);
            }
        }
    }

    @Override
    protected boolean isMeasurementCharacteristic(@NonNull UUID uuid) {
        return BluetoothConstants.UUID_CHARACTERISTIC_RADAR_V1.equals(uuid) ||
               BluetoothConstants.UUID_CHARACTERISTIC_RADAR_V2.equals(uuid);
    }

    @Override
    @android.annotation.SuppressLint("MissingPermission")
    protected void onDescriptorWriteCompleted(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
        super.onDescriptorWriteCompleted(gatt, descriptor, status);
        if (descriptor != null) {
            BluetoothGattCharacteristic parentChar = descriptor.getCharacteristic();
            if (parentChar != null && BluetoothConstants.UUID_CHARACTERISTIC_RADAR_V2.equals(parentChar.getUuid())) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    if (DEBUG) Log.i(TAG, "V2 CCCD write succeeded; arming 1500ms silence watchdog");
                    if (mHandler != null) {
                        mHandler.removeCallbacks(mV2SilenceRunnable);
                        mHandler.postDelayed(mV2SilenceRunnable, V2_SILENCE_TIMEOUT_MS);
                    }
                } else {
                    Log.w(TAG, "V2 CCCD write failed with status " + status + "; falling back to V1 stream");
                    if (mHandler != null) {
                        mHandler.removeCallbacks(mV2SilenceRunnable);
                    }
                    if (gatt != null) {
                        BluetoothGattService service = gatt.getService(BluetoothConstants.UUID_SERVICE_RADAR);
                        if (service != null) {
                            fallbackToV1(gatt, service);
                        }
                    }
                }
            }
        }
    }

    @Override
    protected void measurementCharacteristicUpdate(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        if (mIsShutDown || gatt != mBluetoothGatt || mState != State.CONNECTED_WITH_SERVICE) {
            return;
        }
        if (characteristic == null || characteristic.getValue() == null) {
            return;
        }

        if (BluetoothConstants.UUID_CHARACTERISTIC_RADAR_V2.equals(characteristic.getUuid())) {
            if (mHandler != null) {
                mHandler.removeCallbacks(mV2SilenceRunnable);
            }
            if (RadarStateRepository.getV2BondingStatus().isRequired()) {
                setV2BondingRequiredState(false);
            }
            byte[] payload = characteristic.getValue();
            List<RadarTarget> targets = parseV2ThreatPacket(payload);
            if (targets != null) {
                processTargets(targets);
            }
        } else if (BluetoothConstants.UUID_CHARACTERISTIC_RADAR_V1.equals(characteristic.getUuid())) {
            byte[] payload = characteristic.getValue();
            processV1Notification(payload);
        }
    }

    private void processTargets(List<RadarTarget> targets) {
        pruneStaleTracks(SystemClock.elapsedRealtime());

        if (mHandler != null) {
            mHandler.removeCallbacks(mWatchdogRunnable);
            mHandler.postDelayed(mWatchdogRunnable, WATCHDOG_TIMEOUT_MS);
        }

        ThreatLevel overallThreat = computeOverallThreatLevel(targets);
        RadarStateRepository.updateThreatState(createBleThreatState(
                RadarConnectionState.CONNECTED,
                overallThreat,
                targets
        ));
        if (mIsLightControlSupported) {
            syncLightControlSupported();
        }

        long nowMs = SystemClock.elapsedRealtime();

        // Update 1 Hz aggregator
        mWindowAggregator.onTargetsUpdated(targets, nowMs);

        if (mLastWindowSampleMs == 0L || nowMs - mLastWindowSampleMs >= 1000L) {
            mLastWindowSampleMs = nowMs;
            RadarLatchedSnapshot snapshot = mWindowAggregator.sampleAndResetWindow(nowMs);

            if (mThreatCountSensor != null) {
                mThreatCountSensor.newValue(snapshot.getThreatCount());
            }
            if (mClosestDistanceSensor != null) {
                mClosestDistanceSensor.newValue(snapshot.getClosestDistanceMeters());
            }
            if (mApproachSpeedSensor != null) {
                mApproachSpeedSensor.newValue(snapshot.getApproachSpeedMps());
            }
            if (mVehicleCountSensor != null) {
                mVehicleCountSensor.newValue(snapshot.getVehicleCount());
            }
            if (mPassingDistanceSensor != null) {
                mPassingDistanceSensor.newValue(snapshot.getPassingDistanceMeters());
            }
            if (mLargeVehicleCountSensor != null) {
                mLargeVehicleCountSensor.newValue(snapshot.getLargeVehicleCount());
            }
        }
    }

    /**
     * Resets dynamic radar sensors to clear state (0 threats/speed, null distances).
     */
    private void resetSensorsToClear() {
        mLastWindowSampleMs = 0L;
        if (mThreatCountSensor != null) {
            mThreatCountSensor.newValue(0);
        }
        if (mClosestDistanceSensor != null) {
            mClosestDistanceSensor.newValue(null);
        }
        if (mApproachSpeedSensor != null) {
            mApproachSpeedSensor.newValue(0.0);
        }
        if (mPassingDistanceSensor != null) {
            mPassingDistanceSensor.newValue(null);
        }
    }

    @Override
    protected void onAccumulatorsReset() {
        super.onAccumulatorsReset();
        mLastWindowSampleMs = 0L;
        mWindowAggregator.resetSession();
        if (mVehicleCountSensor != null) {
            mVehicleCountSensor.newValue(0);
        }
        if (mLargeVehicleCountSensor != null) {
            mLargeVehicleCountSensor.newValue(0);
        }
    }

    private void resetConnectionState() {
        if (mHandler != null) {
            mHandler.removeCallbacks(mWatchdogRunnable);
            mHandler.removeCallbacks(mV2SilenceRunnable);
            mHandler.removeCallbacks(mModelNumberFallbackRunnable);
        }
        mIsV2Stream = false;
        deactivateBondSession(false);
        synchronized (mTrackHistory) {
            mTrackHistory.clear();
        }
        synchronized (mLightRegistrationLock) {
            mDisconnectedSinceConfigure = true;
            mActiveRegistrationGatt = null;
            mAwaitingModelNumberForRegistration = false;
            mIsStvzo = false;
            mHasHardwareBrakeSensor = false;
            mModelNumber = null;
            RadarLightController.unregisterRadarDevice(this);
        }
        mLightControlCharacteristic = null;
        mRadarControlWriteChar = null;
        mRadarControlNotifyChar = null;
        mLastLightModeCommandTimeMs = 0L;
        mLastConfiguredLightMode = null;
        mPendingLightMode = null;
        mConfirmedLightMode = null;
        mIsLightControlSupported = false;
    }

    @Override
    protected void unregisterSensors() {
        deactivateBondSession(false);
        super.unregisterSensors();
    }

    @Override
    protected void disconnectFromGatt() {
        resetConnectionState();
        RadarStateRepository.updateThreatState(createBleThreatState(
                RadarConnectionState.DISCONNECTED,
                ThreatLevel.NONE,
                Collections.emptyList()
        ));
        RadarStateRepository.updateLightState(current ->
                new RadarLightState(RadarLightMode.OFF, false, current.isAutoMode())
        );
        resetSensorsToClear();
        super.disconnectFromGatt();
    }

    @Override
    public void shutDown() {
        deactivateBondSession(true);
        resetConnectionState();
        mWindowAggregator.resetSession();
        super.shutDown();
    }

    /**
     * Dispatches a light mode command to the connected radar peripheral (REQ-CON-031.1, REQ-CON-031.2).
     *
     * On RearVue 820 peripherals with dedicated Control Service 6a4e2f00, formats a 4-byte command
     * `06 09 01 TT` on characteristic 6a4e2f11 and immediately performs an optimistic hardware mode update.
     * On legacy units (e.g. RTL515, RTL516), dispatches a 1-byte command on characteristic 6a4e3202.
     *
     * Queues write operation via [enqueueWriteCharacteristic] into the serialized GATT queue.
     *
     * @param mode Target radar tail light mode.
     * @return true if command was successfully enqueued, false if characteristic not supported.
     */
    /**
     * Selects the appropriate GATT write type from peripheral characteristic properties (REQ-CON-031.2).
     * Prefers WRITE_TYPE_DEFAULT whenever PROPERTY_WRITE is advertised (e.g. RearVue 820 6a4e2f11),
     * falling back to WRITE_TYPE_NO_RESPONSE strictly when only PROPERTY_WRITE_NO_RESPONSE is present.
     */
    public static int resolveControlWriteType(@NonNull BluetoothGattCharacteristic characteristic) {
        int properties = characteristic.getProperties();
        if ((properties & BluetoothGattCharacteristic.PROPERTY_WRITE) != 0) {
            return BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT;
        }
        if ((properties & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
            return BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE;
        }
        return BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT;
    }

    /**
     * Enables local GATT notification routing and queues 0x2902 CCCD subscription (REQ-CON-031.1).
     */
    @android.annotation.SuppressLint("MissingPermission")
    private void enableCharacteristicSubscription(@NonNull BluetoothGatt gatt,
                                                @NonNull BluetoothGattCharacteristic characteristic,
                                                boolean defaultToIndication,
                                                @NonNull String logLabel) {
        try {
            gatt.setCharacteristicNotification(characteristic, true);
            BluetoothGattDescriptor desc = characteristic.getDescriptor(BluetoothConstants.UUID_CHARACTERISTIC_CLIENT_CONFIG);
            if (desc != null) {
                int props = characteristic.getProperties();
                byte[] val;
                if ((props & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0) {
                    val = BluetoothGattDescriptor.ENABLE_INDICATION_VALUE;
                } else if ((props & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
                    val = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE;
                } else {
                    val = defaultToIndication
                            ? BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                            : BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE;
                }
                enqueueWriteDescriptor(desc, val);
            }
        } catch (SecurityException e) {
            Log.w(TAG, "Missing BLUETOOTH_CONNECT permission enabling " + logLabel + " notifications", e);
        }
    }

    public boolean setLightMode(@NonNull RadarLightMode mode) {
        final BluetoothGattCharacteristic radarWriteChar = mRadarControlWriteChar;
        if (radarWriteChar != null) {
            radarWriteChar.setWriteType(resolveControlWriteType(radarWriteChar));
            byte type = getRearVueModeType(mode);
            byte[] cmd = new byte[]{0x06, 0x09, 0x01, type};
            mPendingLightMode = mode;
            mLastLightModeCommandTimeMs = SystemClock.elapsedRealtime();
            mLastConfiguredLightMode = mode;
            enqueueWriteCharacteristic(radarWriteChar, cmd);
            RadarLightController.updateHardwareLightMode(mode);
            return true;
        }

        BluetoothGattCharacteristic lightChar = mLightControlCharacteristic;
        if (lightChar == null) {
            if (DEBUG) Log.w(TAG, "Cannot set light mode: characteristic not supported or disconnected");
            return false;
        }
        lightChar.setWriteType(resolveControlWriteType(lightChar));
        mPendingLightMode = mode;
        mLastConfiguredLightMode = mode;
        enqueueWriteCharacteristic(lightChar, new byte[]{mode.getCommandByte()});
        return true;
    }

    public boolean isLightControlSupported() {
        return mIsLightControlSupported;
    }

    public boolean isStvzo() {
        return mIsStvzo;
    }

    @Nullable
    public String getModelNumber() {
        return mModelNumber;
    }

    public static boolean isStvzoModel(@Nullable String model) {
        if (model == null) return false;
        String upper = model.toUpperCase(java.util.Locale.US);
        return upper.contains("010-03180-10") ||
               upper.contains("RTL516") ||
               upper.contains("STVZO") ||
               upper.contains("RVR316");
    }

    @Override
    protected void onOtherCharacteristicUpdate(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        if (characteristic == null || characteristic.getUuid() == null) return;

        if (BluetoothConstants.UUID_CHARACTERISTIC_MODEL_NUMBER.equals(characteristic.getUuid())) {
            String model = BleDisStringSanitizer.readSanitizedString(characteristic);
            if (model != null && !model.isEmpty()) {
                completeLightControlRegistration(gatt, model, isStvzoModel(model), RadarLightController.isRearVue820Model(model), false, false);
            } else {
                completeLightControlRegistration(gatt, null, false, false, true, true);
            }
        } else if (BluetoothConstants.UUID_CHARACTERISTIC_RADAR_LIGHT_CONTROL.equals(characteristic.getUuid())) {
            byte[] val = characteristic.getValue();
            if (val != null && val.length > 0) {
                RadarLightMode mode = RadarLightMode.fromCommandByte(val[0]);
                if (DEBUG) Log.i(TAG, "Read physical tail light mode: " + mode);
                mConfirmedLightMode = mode;
                mPendingLightMode = null;
                RadarLightController.updateHardwareLightMode(mode);
            }
        } else if (BluetoothConstants.UUID_CHARACTERISTIC_RADAR_CONTROL_WRITE.equals(characteristic.getUuid())) {
            byte[] val = characteristic.getValue();
            RadarLightMode confirmed = parseControlIndicationMode(val);
            if (confirmed != null) {
                if (DEBUG) Log.i(TAG, "Received RearVue 820 control indication mode: " + confirmed);
                mConfirmedLightMode = confirmed;
                mPendingLightMode = null;
                RadarLightController.updateHardwareLightMode(confirmed);
            } else if (val != null && DEBUG) {
                Log.d(TAG, "Received RearVue 820 6a4e2f11 indication frame: len=" + val.length);
            }
        } else if (BluetoothConstants.UUID_CHARACTERISTIC_RADAR_CONTROL_NOTIFY.equals(characteristic.getUuid())) {
            byte[] val = characteristic.getValue();
            if (isControlSlotNotification(val)) {
                long nowMs = SystemClock.elapsedRealtime();
                if (nowMs - mLastLightModeCommandTimeMs < ECHO_GUARD_WINDOW_MS) {
                    if (DEBUG) Log.d(TAG, "Ignoring RearVue 820 slot notification within echo guard window");
                    return;
                }
                RadarLightMode mode = parseRearVueModeType(val[3]);
                if (mode != null) {
                    mConfirmedLightMode = mode;
                    if (mPendingLightMode == null || mode == mPendingLightMode) {
                        mPendingLightMode = null;
                        if (DEBUG) Log.i(TAG, "Read RearVue 820 physical light mode: " + mode);
                        RadarLightController.updateHardwareLightMode(mode);
                    }
                }
            }
        }
    }

    /**
     * Handles BLE characteristic write completion for radar tail light control (REQ-CON-031.2, ATT-7737).
     * Promotes confirmed state on GATT_SUCCESS; on non-zero failure, triggers SMP bonding when
     * authentication/encryption is required and reconciles optimistic UI state cleanly.
     */
    @Override
    @android.annotation.SuppressLint("MissingPermission")
    protected void onCharacteristicWriteCompleted(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
        if (characteristic == null || characteristic.getUuid() == null) return;
        java.util.UUID uuid = characteristic.getUuid();
        if (!BluetoothConstants.UUID_CHARACTERISTIC_RADAR_CONTROL_WRITE.equals(uuid)
                && !BluetoothConstants.UUID_CHARACTERISTIC_RADAR_LIGHT_CONTROL.equals(uuid)) {
            return;
        }
        if (status == BluetoothGatt.GATT_SUCCESS) {
            RadarLightMode completed = mPendingLightMode != null ? mPendingLightMode : mLastConfiguredLightMode;
            if (completed != null) {
                mConfirmedLightMode = completed;
            }
            mPendingLightMode = null;
            if (DEBUG) Log.i(TAG, "Confirmed light control GATT write on " + uuid + " mode=" + completed);
            return;
        }

        Log.w(TAG, "Light control GATT write failed on " + uuid + " status=" + status);
        if (status == BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION || status == 15) {
            BluetoothDevice device = gatt != null ? gatt.getDevice() : null;
            if (device != null) {
                try {
                    if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
                        Log.i(TAG, "Initiating BLE bonding after GATT auth/encryption failure (status=" + status + ")");
                        device.createBond();
                    }
                } catch (SecurityException e) {
                    Log.w(TAG, "Missing BLUETOOTH_CONNECT permission initiating BLE bond", e);
                }
            }
        }
        RadarLightMode failedMode = mPendingLightMode != null ? mPendingLightMode : mLastConfiguredLightMode;
        mPendingLightMode = null;
        mLastLightModeCommandTimeMs = 0L;
        if (failedMode != null) {
            RadarLightController.onHardwareLightModeWriteFailed(failedMode, mConfirmedLightMode);
        }
    }

    @Nullable
    private static RadarLightMode parseControlIndicationMode(@Nullable byte[] val) {
        if (val == null || val.length < 4) return null;
        if (isControlSlotNotification(val)) {
            return parseRearVueModeType(val[3]);
        }
        if ((val[0] & 0xFF) == 0x06 && (val[1] & 0xFF) == 0x09 && (val[2] & 0xFF) == 0x01) {
            return parseRearVueModeType(val[3]);
        }
        return null;
    }

    /**
     * Maps [RadarLightMode] to the 1-byte type code used in RearVue 820 4-byte control frames (REQ-CON-031.2).
     */
    public static byte getRearVueModeType(@NonNull RadarLightMode mode) {
        switch (mode) {
            case SOLID:
                return (byte) 0x11;
            case PELOTON:
                return (byte) 0x12;
            case DAY_FLASH:
                return (byte) 0x13;
            case NIGHT_FLASH:
                return (byte) 0x14;
            case OFF:
            default:
                return (byte) 0x1F;
        }
    }

    /**
     * Decodes the RearVue 820 1-byte light type code into [RadarLightMode] (REQ-CON-031.2).
     */
    @Nullable
    public static RadarLightMode parseRearVueModeType(byte type) {
        int val = type & 0xFF;
        switch (val) {
            case 0x11:
            case 0x01:
                return RadarLightMode.SOLID;
            case 0x12:
                return RadarLightMode.PELOTON;
            case 0x13:
                return RadarLightMode.DAY_FLASH;
            case 0x14:
                return RadarLightMode.NIGHT_FLASH;
            case 0x1F:
            case 0x00:
                return RadarLightMode.OFF;
            default:
                return null;
        }
    }

    /**
     * Determines whether a notification on characteristic 6a4e2f14 is a valid 4-byte slot notification (REQ-CON-031.2).
     *
     * Format: 01 [slot] ff [type]
     */
    public static boolean isControlSlotNotification(@Nullable byte[] payload) {
        return payload != null && payload.length == 4 && (payload[0] & 0xFF) == 0x01 && (payload[2] & 0xFF) == 0xFF;
    }

    public static boolean isV1Heartbeat(@Nullable byte[] payload) {
        if (payload == null || payload.length != 1) return false;
        byte header = payload[0];
        return (header & 0x0F) == 0x02;
    }

    public static boolean isV1SectorPacket(@Nullable byte[] payload) {
        return payload != null && payload.length == 6 && payload[0] == 0x06;
    }

    public void processV1Notification(@Nullable byte[] payload) {
        if (payload == null || payload.length < 1) {
            return;
        }

        long nowMs = SystemClock.elapsedRealtime();

        // 1-Byte Heartbeat (~7 Hz when no vehicles present, cycling 0x02..0xF2)
        if (isV1Heartbeat(payload)) {
            synchronized (mTrackHistory) {
                mTrackHistory.clear();
            }
            processTargets(Collections.emptyList());
            return;
        }

        // 6-Byte Sector Amplitude Packet (diagnostic frame emitted alongside heartbeats/threats)
        if (isV1SectorPacket(payload)) {
            pruneStaleTracks(nowMs);
            List<RadarTarget> active = new ArrayList<>();
            synchronized (mTrackHistory) {
                for (RadarTarget target : RadarStateRepository.getState().getTargets()) {
                    if (mTrackHistory.containsKey(target.getTargetId())) {
                        active.add(target);
                    }
                }
            }
            processTargets(active);
            return;
        }

        // Threat Packet (len >= 4, (len - 1) % 3 == 0)
        pruneStaleTracks(nowMs);
        List<RadarTarget> targets = parseV1ThreatPacket(payload, mTrackHistory, nowMs);
        if (targets != null) {
            processTargets(targets);
        }
    }

    /**
     * Demuxes and parses a Garmin Varia BLE V1 packet (characteristic 6a4e3203).
     *
     * Supports authentic sequence-framed headers (low nibble 0x02, cycling 0x02..0xF2).
     * Demuxes 1-byte heartbeats and multi-target threat triplets.
     */
    @Nullable
    public static List<RadarTarget> parseV1ThreatPacket(@Nullable byte[] payload) {
        return parseV1ThreatPacket(payload, null, SystemClock.elapsedRealtime());
    }

    @Nullable
    public static List<RadarTarget> parseV1ThreatPacket(
            @Nullable byte[] payload,
            @Nullable java.util.Map<Integer, TrackState> trackHistory,
            long nowMs
    ) {
        if (payload == null || payload.length < 1) {
            return null;
        }

        byte header = payload[0];
        if ((header & 0x0F) != 0x02) {
            return null;
        }

        // 1-byte clear frame / heartbeat
        if (payload.length == 1) {
            return Collections.emptyList();
        }

        int targetPayloadLen = payload.length - 1;
        if (targetPayloadLen % 3 != 0) {
            return null; // Malformed or non-threat packet
        }

        int numTriplets = targetPayloadLen / 3;
        List<RadarTarget> targets = new ArrayList<>(numTriplets);

        for (int i = 0; i < numTriplets; i++) {
            int offset = 1 + i * 3;
            int vid = payload[offset] & 0xFF;
            int dist = payload[offset + 1] & 0xFF;

            // Real hardware frame: bit 7 (0x80) indicates valid vehicle.
            // Filter out vid < 0x80, vid == 0xFD, and dist == 0xFF.
            if ((vid & 0x80) == 0 || vid == 0xFD || dist == 0xFF) {
                continue;
            }
            int trackId = vid & 0x7F;
            float rangeMeters = (float) dist;
            float approachSpeedMps = 0.0f;
            ThreatLevel threatLevel;

            if (trackHistory != null) {
                synchronized (trackHistory) {
                    TrackState prev = trackHistory.get(trackId);
                    if (prev != null) {
                        long dtMs = nowMs - prev.timestampMs;
                        if (dtMs > 0 && dtMs <= STALE_TRACK_TIMEOUT_MS) {
                            float dt = dtMs / 1000.0f;
                            float dr = prev.rangeMeters - rangeMeters;
                            if (dt > 0.001f && dr > 0.0f) {
                                approachSpeedMps = dr / dt;
                            } else if (dt > 0.001f) {
                                approachSpeedMps = 0.0f;
                            }
                        }
                    }
                    if (approachSpeedMps >= 15.0f) {
                        threatLevel = ThreatLevel.FAST_APPROACHING;
                    } else {
                        threatLevel = ThreatLevel.APPROACHING;
                    }
                    trackHistory.put(trackId, new TrackState(rangeMeters, approachSpeedMps, threatLevel, nowMs));
                }
            } else {
                threatLevel = ThreatLevel.APPROACHING;
            }

            targets.add(new RadarTarget(trackId, rangeMeters, approachSpeedMps, threatLevel));
        }

        return targets;
    }

    @NonNull
    public static ThreatLevel computeOverallThreatLevel(@NonNull List<RadarTarget> targets) {
        ThreatLevel overall = ThreatLevel.NONE;
        for (RadarTarget target : targets) {
            if (target.getThreatLevel() == ThreatLevel.FAST_APPROACHING) {
                return ThreatLevel.FAST_APPROACHING; // Red alert takes highest precedence
            } else if (target.getThreatLevel() == ThreatLevel.APPROACHING) {
                overall = ThreatLevel.APPROACHING;
            }
        }
        return overall;
    }

    /**
     * Demuxes and parses a Garmin Varia BLE V2 high-resolution packet (characteristic 6a4e3204, REQ-CON-030.3).
     *
     * Wire formats:
     * - Authentic RearVue 820:
     *   - len == 2: 2-byte idle heartbeat (02 00, 02 90, or bit 0 set); resets watchdog, prunes stale tracks.
     *   - len == 5 or 6 (bit 2 0x0004 set in LE header): device status frame; resets watchdog.
     *   - len >= 11 && (len - 2) % 9 == 0: 2-byte header + N * 9-byte target structs.
     *     - offset + 0: targetId (uint8)
     *     - offset + 1: targetClass (LARGE=36 -> TRUCK_OR_BUS, MODERATE=23|26 -> CAR, FAINT=13|16 -> UNKNOWN_OR_LIGHT, 4 -> UNKNOWN_OR_LIGHT)
     *     - offset + 2..4: 24-bit LE packed range word (bits 0..10 signed lateralX * 0.1m, bits 11..23 signed rangeY * 0.1m)
     *     - offset + 5: lengthMeters (uint8 * 0.25f)
     *     - offset + 6: widthMeters (uint8 * 0.25f)
     *     - offset + 7: speedY (int8 * 0.5f m/s longitudinal closing speed; negative=approaching -> approachSpeedMps = -speedY)
     *     - offset + 8: speedX (int8 * 0.5f m/s lateral speed; 0x80 = unknown sentinel)
     */
    @Nullable
    public static List<RadarTarget> parseV2ThreatPacket(@Nullable byte[] payload) {
        if (payload == null || payload.length < 2) {
            return null;
        }

        int header = (payload[0] & 0xFF) | ((payload[1] & 0xFF) << 8);

        // Authentic RearVue 820: 2-byte idle heartbeat (e.g. 02 00, 02 90, or bit 0 set)
        if (payload.length == 2) {
            if ((header & 0x0001) != 0 || payload[0] == 0x02) {
                return Collections.emptyList();
            }
            return null;
        }

        // Authentic RearVue 820: 5/6-byte device status frame (bit 2 0x0004 set)
        if ((header & 0x0004) != 0 && (payload.length == 5 || payload.length == 6)) {
            return Collections.emptyList();
        }

        // Authentic RearVue 820: 2 + 9*N multi-target frame
        if (payload.length >= 11 && (payload.length - 2) % 9 == 0) {
            int numTargets = (payload.length - 2) / 9;
            List<RadarTarget> targets = new ArrayList<>(numTargets);

            for (int i = 0; i < numTargets; i++) {
                int offset = 2 + i * 9;
                int targetId = payload[offset] & 0xFF;

                int targetClass = payload[offset + 1] & 0xFF;
                VehicleSizeCategory sizeCategory;
                switch (targetClass) {
                    case 36: // 0x24: LARGE
                        sizeCategory = VehicleSizeCategory.TRUCK_OR_BUS;
                        break;
                    case 23: // 0x17: MODERATE
                    case 26: // 0x1A: MODERATE
                        sizeCategory = VehicleSizeCategory.CAR;
                        break;
                    case 13: // 0x0D: FAINT
                    case 16: // 0x10: FAINT
                    case 4:  // UNKNOWN
                    default:
                        sizeCategory = VehicleSizeCategory.UNKNOWN_OR_LIGHT;
                        break;
                }

                // 24-bit little-endian packed range word
                int b0 = payload[offset + 2] & 0xFF;
                int b1 = payload[offset + 3] & 0xFF;
                int b2 = payload[offset + 4] & 0xFF;
                int packed = b0 | (b1 << 8) | (b2 << 16);

                // Bits 0..10: 11-bit signed lateral X offset (* 0.1m, positive = right)
                int rangeXBits = packed & 0x7FF;
                if ((rangeXBits & 0x400) != 0) {
                    rangeXBits -= 0x800;
                }
                float lateralOffsetMeters = rangeXBits * 0.1f;

                // Bits 11..23: 13-bit signed longitudinal Y range (* 0.1m, positive = behind rider)
                int rangeYBits = (packed >> 11) & 0x1FFF;
                if ((rangeYBits & 0x1000) != 0) {
                    rangeYBits -= 0x2000;
                }
                float rangeMeters = rangeYBits * 0.1f;

                float lengthMeters = (payload[offset + 5] & 0xFF) * 0.25f;
                float widthMeters = (payload[offset + 6] & 0xFF) * 0.25f;

                // Speed Y: signed int8 * 0.5 m/s longitudinal closing speed (negative = approaching)
                byte rawSpeedY = payload[offset + 7];
                float speedYMps = rawSpeedY * 0.5f;
                float approachSpeedMps = -speedYMps;
                if (approachSpeedMps < 0.0f) {
                    approachSpeedMps = 0.0f;
                }

                // Speed X: signed int8 * 0.5 m/s lateral speed (0x80 = unknown sentinel)
                byte rawSpeedX = payload[offset + 8];
                Float speedXMps = null;
                if ((rawSpeedX & 0xFF) != 0x80) {
                    speedXMps = rawSpeedX * 0.5f;
                }

                ThreatLevel threatLevel;
                if (approachSpeedMps >= 15.0f) {
                    threatLevel = ThreatLevel.FAST_APPROACHING;
                } else if (rangeMeters > 0.0f) {
                    threatLevel = ThreatLevel.APPROACHING;
                } else {
                    threatLevel = ThreatLevel.NONE;
                }

                LaneState laneState;
                float absLat = Math.abs(lateralOffsetMeters);
                if (absLat < 1.0f) {
                    laneState = LaneState.IN_RIDER_LANE;
                } else if (absLat <= 2.2f) {
                    laneState = LaneState.CHANGING_LANE;
                } else {
                    laneState = LaneState.PASSING_LANE;
                }

                targets.add(new RadarTarget(
                        targetId,
                        rangeMeters,
                        approachSpeedMps,
                        threatLevel,
                        lateralOffsetMeters,
                        approachSpeedMps,
                        speedXMps,
                        widthMeters,
                        lengthMeters,
                        sizeCategory,
                        laneState
                ));
            }
            return targets;
        }

        return null;
    }

    public Runnable getV2SilenceRunnableForTesting() {
        return mV2SilenceRunnable;
    }

    @androidx.annotation.VisibleForTesting
    public Runnable getModelNumberFallbackRunnableForTesting() {
        return mModelNumberFallbackRunnable;
    }

    public boolean isV2Stream() {
        return mIsV2Stream;
    }

    public void notifyStopSearchingForTesting(boolean success) {
        notifyStopSearching(success);
    }

    public RadarWindowAggregator getWindowAggregatorForTesting() {
        return mWindowAggregator;
    }

    public void setWindowAggregatorForTesting(RadarWindowAggregator aggregator) {
        mWindowAggregator = aggregator;
    }

    public Runnable getWatchdogRunnableForTesting() {
        return mWatchdogRunnable;
    }

    public void processTargetsForTesting(List<RadarTarget> targets) {
        processTargets(targets);
    }

    public void onAccumulatorsResetForTesting() {
        onAccumulatorsReset();
    }

    public void onOtherCharacteristicUpdateForTesting(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        onOtherCharacteristicUpdate(gatt, characteristic);
    }

    public boolean isMeasurementCharacteristicForTesting(@NonNull UUID uuid) {
        return isMeasurementCharacteristic(uuid);
    }

    public java.util.Map<Integer, TrackState> getTrackHistoryForTesting() {
        return mTrackHistory;
    }

    public void processV1NotificationForTesting(@Nullable byte[] payload) {
        processV1Notification(payload);
    }

    public void onDescriptorWriteCompletedForTesting(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
        onDescriptorWriteCompleted(gatt, descriptor, status);
    }

    @androidx.annotation.VisibleForTesting
    public BluetoothGattCharacteristic getRadarControlWriteCharForTesting() {
        return mRadarControlWriteChar;
    }

    @androidx.annotation.VisibleForTesting
    public BluetoothGattCharacteristic getRadarControlNotifyCharForTesting() {
        return mRadarControlNotifyChar;
    }

    @androidx.annotation.VisibleForTesting
    public void setLastLightModeCommandTimeMsForTesting(long timeMs) {
        mLastLightModeCommandTimeMs = timeMs;
    }

    @androidx.annotation.VisibleForTesting
    public void onCharacteristicWriteCompletedForTesting(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
        onCharacteristicWriteCompleted(gatt, characteristic, status);
    }

    @Nullable
    @androidx.annotation.VisibleForTesting
    public RadarLightMode getConfirmedLightModeForTesting() {
        return mConfirmedLightMode;
    }

    @Nullable
    @androidx.annotation.VisibleForTesting
    public RadarLightMode getPendingLightModeForTesting() {
        return mPendingLightMode;
    }

    @Override
    @androidx.annotation.VisibleForTesting
    public void setStateForTesting(State state) {
        super.setStateForTesting(state);
    }

    @androidx.annotation.VisibleForTesting
    public State getStateForTesting() {
        return mState;
    }

    @androidx.annotation.VisibleForTesting
    public void measurementCharacteristicUpdateForTesting(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        measurementCharacteristicUpdate(gatt, characteristic);
    }

    @androidx.annotation.VisibleForTesting
    public boolean isBondReceiverRegisteredForTesting() {
        synchronized (mBondReceiverLock) {
            return mBondReceiverRegistered;
        }
    }
}
