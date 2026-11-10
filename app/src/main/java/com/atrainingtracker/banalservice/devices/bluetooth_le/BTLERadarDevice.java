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

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;
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
import com.atrainingtracker.banalservice.devices.radar.RadarWindowAggregator;
import com.atrainingtracker.banalservice.devices.radar.ThreatLevel;
import com.atrainingtracker.banalservice.devices.radar.VehicleSizeCategory;
import com.atrainingtracker.banalservice.sensor.MySensor;
import com.atrainingtracker.banalservice.sensor.MySensorManager;
import com.atrainingtracker.banalservice.sensor.SensorType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Bluetooth Low Energy cycling radar protocol driver for Garmin Varia (REQ-CON-028.4).
 *
 * Subscribes to V1 characteristic 6a4e3203, demuxes threat packets from heartbeats/sector frames,
 * publishes high-rate (4–8 Hz) multi-target streams to [RadarStateRepository], maintains a 3.5s
 * fail-safe disconnect watchdog, and latches 1 Hz scalar metrics via [RadarWindowAggregator].
 */
public class BTLERadarDevice extends MyBTLEDevice {
    private static final String TAG = "BTLERadarDevice";
    private static final boolean DEBUG = BANALService.getDebug(false);

    public static final byte PACKET_TYPE_THREAT = 0x00;
    public static final byte PACKET_TYPE_V2_THREAT = 0x01;
    public static final long WATCHDOG_TIMEOUT_MS = 3500L;

    protected MySensor<Integer> mThreatCountSensor;
    protected MySensor<Double> mClosestDistanceSensor;
    protected MySensor<Double> mApproachSpeedSensor;
    protected MySensor<Integer> mVehicleCountSensor;
    protected MySensor<Double> mPassingDistanceSensor;
    protected MySensor<Integer> mLargeVehicleCountSensor;

    private boolean mIsV2Stream = false;
    private BluetoothGattCharacteristic mLightControlCharacteristic;
    private volatile boolean mIsLightControlSupported = false;

    private final RadarWindowAggregator mWindowAggregator = new RadarWindowAggregator();

    private final Runnable mWatchdogRunnable = new Runnable() {
        @Override
        public void run() {
            if (DEBUG) Log.w(TAG, "Radar packet starvation (3.5s timeout); transitioning to DISCONNECTED_ERROR");
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
        }
    };

    public BTLERadarDevice(Context context, MySensorManager mySensorManager, long deviceID, String address) {
        super(context, mySensorManager, DeviceType.BIKE_RADAR, deviceID, address);
        if (DEBUG) Log.d(TAG, "Creating BTLERadarDevice for " + address);
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

        BluetoothGattCharacteristic v2Char = btGattService.getCharacteristic(BluetoothConstants.UUID_CHARACTERISTIC_RADAR_V2);
        if (v2Char != null) {
            if (DEBUG) Log.i(TAG, "Garmin Varia V2 characteristic 6a4e3204 discovered. Initiating V2 handshake.");
            mIsV2Stream = true;
            try {
                gatt.requestMtu(247);
            } catch (SecurityException e) {
                Log.w(TAG, "Missing BLUETOOTH_CONNECT permission requesting MTU 247", e);
            } catch (Exception e) {
                if (DEBUG) Log.w(TAG, "Error requesting MTU 247", e);
            }

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
            fallbackToV1(gatt, btGattService);
        }

        // Tail light control characteristic discovery (ATT-5102 / REQ-CON-031.1)
        BluetoothGattCharacteristic lightChar = btGattService.getCharacteristic(BluetoothConstants.UUID_CHARACTERISTIC_RADAR_LIGHT_CONTROL);
        if (lightChar != null) {
            if (DEBUG) Log.i(TAG, "Garmin Varia light control characteristic 6a4e3202 discovered (REQ-CON-031.1).");
            mLightControlCharacteristic = lightChar;
            mIsLightControlSupported = true;
            RadarLightController.registerRadarDevice(this, true, false);
        } else {
            mLightControlCharacteristic = null;
            mIsLightControlSupported = false;
            RadarLightController.registerRadarDevice(this, false, false);
        }

        // Chained battery CCCD write (REQ-CON-007, ATT-3312) via queue
        if (mBatteryCharacteristic != null && (mBatteryCharacteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
            BluetoothGattDescriptor batteryDescriptor = mBatteryCharacteristic.getDescriptor(BluetoothConstants.UUID_CHARACTERISTIC_CLIENT_CONFIG);
            if (batteryDescriptor != null) {
                enqueueWriteDescriptor(batteryDescriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            }
        }
    }

    @android.annotation.SuppressLint("MissingPermission")
    public void fallbackToV1(BluetoothGatt gatt, BluetoothGattService btGattService) {
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

    @Override
    protected void measurementCharacteristicUpdate(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        if (characteristic == null || characteristic.getValue() == null) {
            return;
        }

        if (BluetoothConstants.UUID_CHARACTERISTIC_RADAR_V2.equals(characteristic.getUuid())) {
            byte[] payload = characteristic.getValue();
            List<RadarTarget> targets = parseV2ThreatPacket(payload);
            if (targets != null) {
                processTargets(targets);
            }
        } else if (BluetoothConstants.UUID_CHARACTERISTIC_RADAR_V1.equals(characteristic.getUuid())) {
            byte[] payload = characteristic.getValue();
            List<RadarTarget> targets = parseV1ThreatPacket(payload);
            if (targets != null) {
                processTargets(targets);
            }
        }
    }

    private void processTargets(List<RadarTarget> targets) {
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
        if (mClosestDistanceSensor != null && snapshot.getClosestDistanceMeters() != null) {
            mClosestDistanceSensor.newValue(snapshot.getClosestDistanceMeters());
        }
        if (mApproachSpeedSensor != null) {
            mApproachSpeedSensor.newValue(snapshot.getApproachSpeedMps());
        }
        if (mVehicleCountSensor != null) {
            mVehicleCountSensor.newValue(snapshot.getVehicleCount());
        }
        if (mPassingDistanceSensor != null && snapshot.getPassingDistanceMeters() != null) {
            mPassingDistanceSensor.newValue(snapshot.getPassingDistanceMeters());
        }
        if (mLargeVehicleCountSensor != null) {
            mLargeVehicleCountSensor.newValue(snapshot.getLargeVehicleCount());
        }
    }

    @Override
    protected void disconnectFromGatt() {
        if (mHandler != null) {
            mHandler.removeCallbacks(mWatchdogRunnable);
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
                RadarAudioSnoozeState.INACTIVE
        ));
        super.disconnectFromGatt();
    }

    @Override
    public void shutDown() {
        if (mHandler != null) {
            mHandler.removeCallbacks(mWatchdogRunnable);
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
        return true;
    }

    public boolean isLightControlSupported() {
        return mIsLightControlSupported;
    }

    /**
     * Demuxes and parses a Garmin Varia BLE V1 packet (characteristic 6a4e3203).
     *
     * Non-threat packets (heartbeats, sector amplitude frames) or malformed payloads return null.
     * Valid threat frames (length 1 + 3*N, byte[0] == 0x00) return a non-null list of RadarTargets.
     */
    @Nullable
    public static List<RadarTarget> parseV1ThreatPacket(@Nullable byte[] payload) {
        if (payload == null || payload.length < 1) {
            return null;
        }

        // Header byte check: Threat packets use PACKET_TYPE_THREAT (0x00)
        if (payload[0] != PACKET_TYPE_THREAT) {
            return null;
        }

        int targetPayloadLen = payload.length - 1;
        if (targetPayloadLen % 3 != 0) {
            return null; // Malformed or non-threat packet
        }

        int numTargets = targetPayloadLen / 3;
        List<RadarTarget> targets = new ArrayList<>(numTargets);

        for (int i = 0; i < numTargets; i++) {
            int offset = 1 + i * 3;
            int targetId = payload[offset] & 0xFF;
            float rangeMeters = (float) (payload[offset + 1] & 0xFF);
            int speedAndThreat = payload[offset + 2] & 0xFF;

            int threatBits = (speedAndThreat >> 4) & 0x03;
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

            float approachSpeedMps = (float) (speedAndThreat & 0x0F);
            targets.add(new RadarTarget(targetId, rangeMeters, approachSpeedMps, threatLevel));
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
     * Header byte 0x01 indicates a V2 multi-target threat frame.
     * Each target record is 8 bytes:
     * - offset + 0: targetId (1 byte uint8)
     * - offset + 1..2: rangeRaw (uint16 LE, 0.1m precision)
     * - offset + 3..4: speedRaw (int16 LE, 0.1 m/s precision)
     * - offset + 5..6: lateralRaw (int16 LE signed, 0.1m precision, negative=left, positive=right)
     * - offset + 7: sizeAndThreat (1 byte: bits 0..1 threat, bits 2..3 size, bits 4..5 lane)
     */
    @Nullable
    public static List<RadarTarget> parseV2ThreatPacket(@Nullable byte[] payload) {
        if (payload == null || payload.length < 1) {
            return null;
        }

        if (payload[0] != PACKET_TYPE_V2_THREAT) {
            return null;
        }

        int targetPayloadLen = payload.length - 1;
        if (targetPayloadLen % 8 != 0) {
            return null;
        }

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

    public boolean isV2Stream() {
        return mIsV2Stream;
    }

    public RadarWindowAggregator getWindowAggregatorForTesting() {
        return mWindowAggregator;
    }

    public Runnable getWatchdogRunnableForTesting() {
        return mWatchdogRunnable;
    }
}
