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
import android.content.Context;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.atrainingtracker.banalservice.BANALService;
import com.atrainingtracker.banalservice.devices.DeviceType;
import com.atrainingtracker.banalservice.devices.radar.RadarConnectionState;
import com.atrainingtracker.banalservice.devices.radar.RadarLatchedSnapshot;
import com.atrainingtracker.banalservice.devices.radar.RadarState;
import com.atrainingtracker.banalservice.devices.radar.RadarStateRepository;
import com.atrainingtracker.banalservice.devices.radar.RadarTarget;
import com.atrainingtracker.banalservice.devices.radar.RadarWindowAggregator;
import com.atrainingtracker.banalservice.devices.radar.ThreatLevel;
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
    public static final long WATCHDOG_TIMEOUT_MS = 3500L;

    protected MySensor<Integer> mThreatCountSensor;
    protected MySensor<Double> mClosestDistanceSensor;
    protected MySensor<Double> mApproachSpeedSensor;
    protected MySensor<Integer> mVehicleCountSensor;

    private final RadarWindowAggregator mWindowAggregator = new RadarWindowAggregator();

    private final Runnable mWatchdogRunnable = new Runnable() {
        @Override
        public void run() {
            if (DEBUG) Log.w(TAG, "Radar packet starvation (3.5s timeout); transitioning to DISCONNECTED_ERROR");
            RadarStateRepository.updateState(new RadarState(
                    RadarConnectionState.DISCONNECTED_ERROR,
                    ThreatLevel.NONE,
                    Collections.emptyList(),
                    SystemClock.elapsedRealtime()
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
    }

    @Override
    protected void measurementCharacteristicUpdate(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        if (characteristic == null || characteristic.getValue() == null) {
            return;
        }

        if (BluetoothConstants.UUID_CHARACTERISTIC_RADAR_V1.equals(characteristic.getUuid())) {
            byte[] payload = characteristic.getValue();
            List<RadarTarget> targets = parseV1ThreatPacket(payload);
            if (targets != null) {
                // Heartbeat / sector packets return null; valid threat packets (including 0-target clear frames) kick watchdog
                if (mHandler != null) {
                    mHandler.removeCallbacks(mWatchdogRunnable);
                    mHandler.postDelayed(mWatchdogRunnable, WATCHDOG_TIMEOUT_MS);
                }

                ThreatLevel overallThreat = computeOverallThreatLevel(targets);
                RadarState state = new RadarState(
                        RadarConnectionState.CONNECTED,
                        overallThreat,
                        targets,
                        SystemClock.elapsedRealtime()
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
            }
        }
    }

    @Override
    protected void disconnectFromGatt() {
        if (mHandler != null) {
            mHandler.removeCallbacks(mWatchdogRunnable);
        }
        RadarStateRepository.updateState(new RadarState(
                RadarConnectionState.DISCONNECTED,
                ThreatLevel.NONE,
                Collections.emptyList(),
                SystemClock.elapsedRealtime()
        ));
        super.disconnectFromGatt();
    }

    @Override
    public void shutDown() {
        if (mHandler != null) {
            mHandler.removeCallbacks(mWatchdogRunnable);
        }
        mWindowAggregator.resetSession();
        super.shutDown();
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

    public RadarWindowAggregator getWindowAggregatorForTesting() {
        return mWindowAggregator;
    }

    public Runnable getWatchdogRunnableForTesting() {
        return mWatchdogRunnable;
    }
}
