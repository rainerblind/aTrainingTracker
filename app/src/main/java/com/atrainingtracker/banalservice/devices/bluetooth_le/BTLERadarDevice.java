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
import android.content.Context;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.atrainingtracker.banalservice.BANALService;
import com.atrainingtracker.banalservice.devices.DeviceType;
import com.atrainingtracker.banalservice.devices.radar.LaneState;
import com.atrainingtracker.banalservice.devices.radar.RadarAudioSnoozeState;
import com.atrainingtracker.banalservice.devices.radar.RadarConnectionState;
import com.atrainingtracker.banalservice.devices.radar.RadarLatchedSnapshot;
import com.atrainingtracker.banalservice.devices.radar.RadarLightController;
import com.atrainingtracker.banalservice.devices.radar.RadarLightMode;
import com.atrainingtracker.banalservice.devices.radar.RadarState;
import com.atrainingtracker.banalservice.devices.radar.RadarStateRepository;
import com.atrainingtracker.banalservice.devices.radar.RadarTarget;
import com.atrainingtracker.banalservice.devices.radar.RadarTrafficSimulator;
import com.atrainingtracker.banalservice.devices.radar.RadarWindowAggregator;
import com.atrainingtracker.banalservice.devices.radar.ThreatLevel;
import com.atrainingtracker.banalservice.devices.radar.VehicleSizeCategory;
import com.atrainingtracker.banalservice.sensor.MySensor;
import com.atrainingtracker.banalservice.sensor.MySensorManager;
import com.atrainingtracker.banalservice.sensor.SensorType;

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

    public static final byte PACKET_TYPE_THREAT = 0x00;
    public static final byte PACKET_TYPE_V2_THREAT = 0x01;
    public static final long WATCHDOG_TIMEOUT_MS = 3500L;
    public static final long STALE_TRACK_TIMEOUT_MS = 2000L;
    public static final long STALE_TRACK_MOVING_MS = 800L;
    public static final long STALE_TRACK_PARKED_MS = 5000L;
    public static final long V2_SILENCE_TIMEOUT_MS = 1500L;

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
    private BluetoothGattCharacteristic mLightControlCharacteristic;
    private volatile boolean mIsLightControlSupported = false;
    private volatile boolean mIsStvzo = false;
    private volatile String mModelNumber = null;
    private volatile RadarLightMode mLastConfiguredLightMode = null;

    private RadarWindowAggregator mWindowAggregator = new RadarWindowAggregator();

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

    private final Runnable mWatchdogRunnable = new Runnable() {
        @Override
        public void run() {
            if (DEBUG) Log.w(TAG, "Radar packet starvation (3.5s timeout); transitioning to DISCONNECTED_ERROR");
            synchronized (mTrackHistory) {
                mTrackHistory.clear();
            }
            RadarState currentState = RadarStateRepository.getState();
            RadarStateRepository.updateState(new RadarState(
                    RadarConnectionState.DISCONNECTED_ERROR,
                    ThreatLevel.NONE,
                    Collections.emptyList(),
                    SystemClock.elapsedRealtime(),
                    currentState.getLightMode(),
                    mIsLightControlSupported,
                    currentState.getAudioSnoozeState()
            ));
            resetSensorsToClear();
        }
    };

    public BTLERadarDevice(Context context, MySensorManager mySensorManager, long deviceID, String address) {
        super(context, mySensorManager, DeviceType.BIKE_RADAR, deviceID, address);
        if (DEBUG) Log.d(TAG, "Creating BTLERadarDevice for " + address);
    }

    @Override
    public void startSearching() {
        super.startSearching();
        RadarState currentState = RadarStateRepository.getState();
        RadarStateRepository.updateState(new RadarState(
                RadarConnectionState.CONNECTING,
                ThreatLevel.NONE,
                Collections.emptyList(),
                SystemClock.elapsedRealtime(),
                currentState.getLightMode(),
                mIsLightControlSupported,
                currentState.getAudioSnoozeState()
        ));
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

        // Radar service discovered: transition to CONNECTED and arm 3.5s fail-safe watchdog (ATT-7543 / REQ-CON-028.4)
        RadarState currentState = RadarStateRepository.getState();
        RadarStateRepository.updateState(new RadarState(
                RadarConnectionState.CONNECTED,
                ThreatLevel.NONE,
                Collections.emptyList(),
                SystemClock.elapsedRealtime(),
                currentState.getLightMode(),
                mIsLightControlSupported,
                currentState.getAudioSnoozeState()
        ));
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

        // Device Information Service discovery for StVZO model number detection (ATT-6707 / REQ-CON-031.1)
        BluetoothGattService disService = gatt.getService(BluetoothConstants.UUID_SERVICE_DEVICE_INFORMATION);
        if (disService != null) {
            BluetoothGattCharacteristic modelChar = disService.getCharacteristic(BluetoothConstants.UUID_CHARACTERISTIC_MODEL_NUMBER);
            if (modelChar != null) {
                enqueueReadCharacteristic(modelChar);
            }
        }

        BluetoothGattCharacteristic v2Char = btGattService.getCharacteristic(BluetoothConstants.UUID_CHARACTERISTIC_RADAR_V2);
        BluetoothDevice device = gatt.getDevice();
        boolean isBonded = (device != null && device.getBondState() == BluetoothDevice.BOND_BONDED);

        if (v2Char != null && isBonded) {
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
            }
            fallbackToV1(gatt, btGattService);
        }

        // Tail light control characteristic discovery (ATT-5102 / REQ-CON-031.1)
        BluetoothGattCharacteristic lightChar = btGattService.getCharacteristic(BluetoothConstants.UUID_CHARACTERISTIC_RADAR_LIGHT_CONTROL);
        if (lightChar != null) {
            if (DEBUG) Log.i(TAG, "Garmin Varia light control characteristic 6a4e3202 discovered (REQ-CON-031.1).");
            mLightControlCharacteristic = lightChar;
            mIsLightControlSupported = true;
            RadarLightController.registerRadarDevice(this, true, mIsStvzo);

            // Subscribe or read initial physical light mode
            if ((lightChar.getProperties() & (BluetoothGattCharacteristic.PROPERTY_NOTIFY | BluetoothGattCharacteristic.PROPERTY_INDICATE)) != 0) {
                try {
                    gatt.setCharacteristicNotification(lightChar, true);
                    BluetoothGattDescriptor desc = lightChar.getDescriptor(BluetoothConstants.UUID_CHARACTERISTIC_CLIENT_CONFIG);
                    if (desc != null) {
                        byte[] val = (lightChar.getProperties() & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0
                                ? BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                                : BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE;
                        enqueueWriteDescriptor(desc, val);
                    }
                } catch (SecurityException e) {
                    Log.w(TAG, "Missing BLUETOOTH_CONNECT permission enabling light notifications", e);
                }
            } else if ((lightChar.getProperties() & BluetoothGattCharacteristic.PROPERTY_READ) != 0) {
                enqueueReadCharacteristic(lightChar);
            }

            // Mid-ride reconnect: re-apply active workout light mode
            RadarLightMode activeMode = RadarLightController.getCurrentMode().getValue();
            if (activeMode != RadarLightMode.OFF) {
                if (DEBUG) Log.i(TAG, "Re-applying active workout light mode upon reconnect: " + activeMode);
                enqueueWriteCharacteristic(lightChar, new byte[]{activeMode.getCommandByte()});
            }
        } else {
            mLightControlCharacteristic = null;
            mIsLightControlSupported = false;
            RadarLightController.registerRadarDevice(this, false, false);
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
        if (characteristic == null || characteristic.getValue() == null) {
            return;
        }

        if (BluetoothConstants.UUID_CHARACTERISTIC_RADAR_V2.equals(characteristic.getUuid())) {
            if (mHandler != null) {
                mHandler.removeCallbacks(mV2SilenceRunnable);
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
        // Hardware preemption (REQ-CON-029.3): physical data preempts simulator immediately
        RadarTrafficSimulator.INSTANCE.onPhysicalHardwareConnected();

        if (mHandler != null) {
            mHandler.removeCallbacks(mWatchdogRunnable);
            mHandler.postDelayed(mWatchdogRunnable, WATCHDOG_TIMEOUT_MS);
        }

        ThreatLevel overallThreat = computeOverallThreatLevel(targets);
        RadarState currentState = RadarStateRepository.getState();
        RadarState state = new RadarState(
                RadarConnectionState.CONNECTED,
                overallThreat,
                targets,
                SystemClock.elapsedRealtime(),
                currentState.getLightMode(),
                mIsLightControlSupported,
                currentState.getAudioSnoozeState()
        );
        RadarStateRepository.updateState(state);

        // Update 1 Hz aggregator
        mWindowAggregator.onTargetsUpdated(targets);
        RadarLatchedSnapshot snapshot = mWindowAggregator.sampleAndResetWindow();

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

    /**
     * Resets dynamic radar sensors to clear state (0 threats/speed, null distances).
     */
    private void resetSensorsToClear() {
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
        mWindowAggregator.resetSession();
        if (mVehicleCountSensor != null) {
            mVehicleCountSensor.newValue(0);
        }
        if (mLargeVehicleCountSensor != null) {
            mLargeVehicleCountSensor.newValue(0);
        }
    }

    @Override
    protected void disconnectFromGatt() {
        if (mHandler != null) {
            mHandler.removeCallbacks(mWatchdogRunnable);
            mHandler.removeCallbacks(mV2SilenceRunnable);
        }
        mIsV2Stream = false;
        synchronized (mTrackHistory) {
            mTrackHistory.clear();
        }
        RadarLightController.unregisterRadarDevice(this);
        mLightControlCharacteristic = null;
        mIsLightControlSupported = false;
        RadarStateRepository.updateState(new RadarState(
                RadarConnectionState.DISCONNECTED,
                ThreatLevel.NONE,
                Collections.emptyList(),
                SystemClock.elapsedRealtime(),
                RadarLightMode.OFF,
                false,
                RadarStateRepository.getState().getAudioSnoozeState()
        ));
        resetSensorsToClear();
        super.disconnectFromGatt();
    }

    @Override
    public void shutDown() {
        if (mHandler != null) {
            mHandler.removeCallbacks(mWatchdogRunnable);
            mHandler.removeCallbacks(mV2SilenceRunnable);
        }
        mIsV2Stream = false;
        synchronized (mTrackHistory) {
            mTrackHistory.clear();
        }
        RadarLightController.unregisterRadarDevice(this);
        mLightControlCharacteristic = null;
        mIsLightControlSupported = false;
        mWindowAggregator.resetSession();
        super.shutDown();
    }

    /**
     * Dispatches a light mode command to the connected radar peripheral (REQ-CON-031.1).
     *
     * Queues write operation via [enqueueWriteCharacteristic] into the serialized GATT queue.
     *
     * @param mode Target radar tail light mode.
     * @return true if command was successfully enqueued, false if characteristic not supported.
     */
    public boolean setLightMode(@NonNull RadarLightMode mode) {
        BluetoothGattCharacteristic lightChar = mLightControlCharacteristic;
        if (lightChar == null) {
            if (DEBUG) Log.w(TAG, "Cannot set light mode: characteristic not supported or disconnected");
            return false;
        }
        enqueueWriteCharacteristic(lightChar, new byte[]{mode.getCommandByte()});
        mLastConfiguredLightMode = mode;
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
            String model = characteristic.getStringValue(0);
            if (model != null) {
                mModelNumber = model;
                boolean stvzo = isStvzoModel(model);
                boolean hasHardwareBrake = RadarLightController.isRearVue820Model(model);
                mIsStvzo = stvzo;
                if (DEBUG) Log.i(TAG, "Read model number: " + model + " (isStvzo=" + stvzo + ", hasHardwareBrake=" + hasHardwareBrake + ")");
                RadarLightController.registerRadarDevice(this, mIsLightControlSupported, mIsStvzo, hasHardwareBrake);
            }
        } else if (BluetoothConstants.UUID_CHARACTERISTIC_RADAR_LIGHT_CONTROL.equals(characteristic.getUuid())) {
            byte[] val = characteristic.getValue();
            if (val != null && val.length > 0) {
                RadarLightMode mode = RadarLightMode.fromCommandByte(val[0]);
                if (DEBUG) Log.i(TAG, "Read physical tail light mode: " + mode);
                RadarLightController.updateHardwareLightMode(mode);
            }
        }
    }

    public static boolean isV1Heartbeat(@Nullable byte[] payload) {
        if (payload == null || payload.length != 1) return false;
        byte header = payload[0];
        return ((header & 0x0F) == 0x02) || header == PACKET_TYPE_THREAT;
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
     * Supports sequence-framed headers (low nibble 0x02, cycling 0x02..0xF2) and legacy 0x00 frames.
     * Demuxes 1-byte heartbeats, 6-byte sector diagnostics, and multi-target threat triplets.
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
        boolean isV1SeqHeader = (header & 0x0F) == 0x02;
        boolean isLegacyHeader = (header == PACKET_TYPE_THREAT);

        if (!isV1SeqHeader && !isLegacyHeader) {
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
            int flagOrSpeed = payload[offset + 2] & 0xFF;

            if (isV1SeqHeader) {
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
            } else {
                // Legacy synthetic packet parsing (header == 0x00)
                int targetId = vid;
                float rangeMeters = (float) dist;
                int threatBits = (flagOrSpeed >> 4) & 0x03;
                ThreatLevel threatLevel;
                switch (threatBits) {
                    case 1:
                        threatLevel = ThreatLevel.APPROACHING;
                        break;
                    case 2:
                    case 3:
                        threatLevel = ThreatLevel.FAST_APPROACHING;
                        break;
                    default:
                        threatLevel = ThreatLevel.NONE;
                        break;
                }
                float approachSpeedMps = (float) (flagOrSpeed & 0x0F);
                targets.add(new RadarTarget(targetId, rangeMeters, approachSpeedMps, threatLevel));
            }
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
     * - Legacy synthetic format:
     *   - 1-byte clear frame (0x01)
     *   - 1-byte header 0x01 + N * 8-byte target structs
     */
    @Nullable
    public static List<RadarTarget> parseV2ThreatPacket(@Nullable byte[] payload) {
        if (payload == null || payload.length == 0) {
            return null;
        }

        // Legacy synthetic 1-byte clear frame
        if (payload.length == 1) {
            if (payload[0] == PACKET_TYPE_V2_THREAT) {
                return Collections.emptyList();
            }
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

        // Backward compatibility: Legacy synthetic V2 packet (header 0x01, 1 + 8*N bytes)
        if (payload[0] == PACKET_TYPE_V2_THREAT && (payload.length - 1) % 8 == 0) {
            int targetPayloadLen = payload.length - 1;
            int numTargets = targetPayloadLen / 8;
            List<RadarTarget> targets = new ArrayList<>(numTargets);

            for (int i = 0; i < numTargets; i++) {
                int offset = 1 + i * 8;
                int targetId = payload[offset] & 0xFF;
                int rangeRaw = (payload[offset + 1] & 0xFF) | ((payload[offset + 2] & 0xFF) << 8);
                float rangeMeters = rangeRaw / 10.0f;

                short speedRaw = (short) ((payload[offset + 3] & 0xFF) | ((payload[offset + 4] & 0xFF) << 8));
                float approachSpeedMps = speedRaw / 10.0f;

                short lateralRaw = (short) ((payload[offset + 5] & 0xFF) | ((payload[offset + 6] & 0xFF) << 8));
                float lateralOffsetMeters = lateralRaw / 10.0f;

                int sizeAndThreat = payload[offset + 7] & 0xFF;
                int threatBits = sizeAndThreat & 0x03;
                ThreatLevel threatLevel;
                switch (threatBits) {
                    case 1:
                        threatLevel = ThreatLevel.APPROACHING;
                        break;
                    case 2:
                    case 3:
                        threatLevel = ThreatLevel.FAST_APPROACHING;
                        break;
                    default:
                        threatLevel = ThreatLevel.NONE;
                        break;
                }

                int sizeBits = (sizeAndThreat >> 2) & 0x03;
                VehicleSizeCategory sizeCategory;
                switch (sizeBits) {
                    case 1:
                        sizeCategory = VehicleSizeCategory.CAR;
                        break;
                    case 2:
                        sizeCategory = VehicleSizeCategory.TRUCK_OR_BUS;
                        break;
                    default:
                        sizeCategory = VehicleSizeCategory.UNKNOWN_OR_LIGHT;
                        break;
                }

                int laneBits = (sizeAndThreat >> 4) & 0x03;
                LaneState laneState;
                switch (laneBits) {
                    case 1:
                        laneState = LaneState.IN_RIDER_LANE;
                        break;
                    case 2:
                        laneState = LaneState.CHANGING_LANE;
                        break;
                    case 3:
                        laneState = LaneState.PASSING_LANE;
                        break;
                    default:
                        laneState = LaneState.UNKNOWN;
                        break;
                }

                targets.add(new RadarTarget(
                        targetId,
                        rangeMeters,
                        approachSpeedMps,
                        threatLevel,
                        lateralOffsetMeters,
                        approachSpeedMps,
                        0.0f,
                        null,
                        null,
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

    public boolean isV2Stream() {
        return mIsV2Stream;
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
}
