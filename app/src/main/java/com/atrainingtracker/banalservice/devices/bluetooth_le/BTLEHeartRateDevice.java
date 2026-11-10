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
import android.util.Log;

import androidx.annotation.Nullable;

import com.atrainingtracker.banalservice.BANALService;
import com.atrainingtracker.banalservice.devices.DeviceType;
import com.atrainingtracker.banalservice.devices.ProtocolType;
import com.atrainingtracker.banalservice.devices.RrBeatEvent;
import com.atrainingtracker.banalservice.devices.RrBeatListener;
import com.atrainingtracker.banalservice.devices.RrBeatSource;
import com.atrainingtracker.banalservice.sensor.MySensor;
import com.atrainingtracker.banalservice.sensor.MySensorManager;
import com.atrainingtracker.banalservice.sensor.SensorType;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Bluetooth Low Energy Heart Rate Device (GATT Service 0x180D, Characteristic 0x2A37).
 * Parses instant heart rate and beat-to-beat RR intervals with cumulative timestamping (REQ-CON-027, ATT-3540).
 */
public class BTLEHeartRateDevice extends MyBTLEDevice implements RrBeatSource {
    private static final boolean DEBUG = BANALService.getDebug(false);
    private static final String TAG = "BTLEHeartRateDevice";
    private static final long DRIFT_THRESHOLD_MS = 2000L;

    protected MySensor<Integer> mHeartRateSensor;
    protected MySensor<Integer> mRrIntervalSensor = null;

    private final List<RrBeatListener> mRrBeatListeners = new CopyOnWriteArrayList<>();
    private long mLastBeatTimestampMs = 0L;
    private int mPacketCounter = 0;

    /**
     * Constructor
     */
    public BTLEHeartRateDevice(Context context, MySensorManager mySensorManager, long deviceID, String address) {
        super(context, mySensorManager, DeviceType.HRM, deviceID, address);
        if (DEBUG) {
            Log.d(TAG, "creating HR device");
        }
    }

    @Override
    protected void addSensors() {
        mHeartRateSensor = new MySensor<>(this, SensorType.HR);
        addSensor(mHeartRateSensor);
    }

    public MySensor<Integer> getHeartRateSensor() {
        return mHeartRateSensor;
    }

    @Nullable
    public MySensor<Integer> getRrIntervalSensor() {
        return mRrIntervalSensor;
    }

    @Override
    public void addRrBeatListener(RrBeatListener listener) {
        if (listener != null && !mRrBeatListeners.contains(listener)) {
            mRrBeatListeners.add(listener);
        }
    }

    @Override
    public void removeRrBeatListener(RrBeatListener listener) {
        if (listener != null) {
            mRrBeatListeners.remove(listener);
        }
    }

    protected void dispatchRrBeat(RrBeatEvent event) {
        for (RrBeatListener listener : mRrBeatListeners) {
            try {
                listener.onRrBeat(event);
            } catch (Exception e) {
                Log.e(TAG, "Error in RrBeatListener callback", e);
            }
        }
    }

    @Override
    protected void measurementCharacteristicUpdate(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        if (characteristic == null) {
            return;
        }
        byte[] value = characteristic.getValue();
        parseHeartRateMeasurement(value, System.currentTimeMillis());
    }

    /**
     * Parses a Heart Rate Measurement characteristic notification payload (0x2A37).
     *
     * @param value Raw byte array from characteristic.
     * @param packetArrivalMs Wall-clock arrival timestamp in milliseconds.
     */
    public void parseHeartRateMeasurement(byte[] value, long packetArrivalMs) {
        if (value == null || value.length < 2) {
            if (DEBUG) Log.w(TAG, "measurementCharacteristicUpdate: packet too short or null");
            return;
        }

        try {
            int flag = value[0] & 0xFF;
            int nextOffset;
            int requiredLength;

            if ((flag & 0x01) != 0) {
                // UINT16 Heart Rate format
                requiredLength = 3;
                nextOffset = 3;
            } else {
                // UINT8 Heart Rate format
                requiredLength = 2;
                nextOffset = 2;
            }

            // Bit 3: Energy Expended field present (UINT16 = 2 bytes)
            if ((flag & 0x08) != 0) {
                requiredLength += 2;
                nextOffset += 2;
            }

            if (value.length < requiredLength) {
                Log.w(TAG, "Heart rate characteristic truncated: length=" + value.length + ", required=" + requiredLength);
                return;
            }

            int heartRate;
            if ((flag & 0x01) != 0) {
                heartRate = (value[1] & 0xFF) | ((value[2] & 0xFF) << 8);
            } else {
                heartRate = value[1] & 0xFF;
            }

            if (DEBUG) Log.i(TAG, String.format("Received heart rate: %d", heartRate));
            mHeartRateSensor.newValue(heartRate);

            // Bit 4: RR-Interval values present
            if ((flag & 0x10) != 0) {
                if (mRrIntervalSensor == null) {
                    mRrIntervalSensor = new MySensor<>(this, SensorType.RR_INTERVAL);
                    addAndRegisterSensor(mRrIntervalSensor);
                }

                mPacketCounter++;

                while (nextOffset + 2 <= value.length) {
                    int rawRr = (value[nextOffset] & 0xFF) | ((value[nextOffset + 1] & 0xFF) << 8);
                    nextOffset += 2;

                    // Convert raw 1/1024 s unit to milliseconds
                    double rrMs = rawRr * 1000.0 / 1024.0;
                    long roundedRrMs = Math.round(rrMs);

                    if (mRrIntervalSensor != null) {
                        mRrIntervalSensor.newValue((int) roundedRrMs);
                    }

                    long beatTimestampMs;
                    int gapBeats = 0;
                    int artifactFlag = RrBeatEvent.ARTIFACT_NORMAL;

                    if (mLastBeatTimestampMs == 0L) {
                        // First beat seen: anchor to packet arrival time
                        beatTimestampMs = packetArrivalMs;
                        mLastBeatTimestampMs = beatTimestampMs;
                    } else {
                        long candidateTimestampMs = mLastBeatTimestampMs + roundedRrMs;
                        long drift = Math.abs(packetArrivalMs - candidateTimestampMs);

                        if (drift > DRIFT_THRESHOLD_MS) {
                            // Wall-clock drift or dropout > 2000 ms: compute gap beats and re-anchor
                            long elapsedSinceLast = packetArrivalMs - mLastBeatTimestampMs;
                            if (roundedRrMs > 0 && elapsedSinceLast > roundedRrMs) {
                                gapBeats = (int) Math.max(0, Math.round((double) elapsedSinceLast / rrMs) - 1);
                            }
                            beatTimestampMs = packetArrivalMs;
                            mLastBeatTimestampMs = beatTimestampMs;
                            if (gapBeats > 0) {
                                artifactFlag = RrBeatEvent.ARTIFACT_GAP;
                            }
                        } else {
                            // Continuous tracking: cumulative monotonic timestamp
                            beatTimestampMs = candidateTimestampMs;
                            mLastBeatTimestampMs = beatTimestampMs;
                        }
                    }

                    dispatchRrBeat(new RrBeatEvent(beatTimestampMs, rrMs, mPacketCounter, ProtocolType.BLE, gapBeats, artifactFlag));
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Error parsing HR characteristic notification", e);
        }
    }

    @Override
    protected void unregisterSensors() {
        super.unregisterSensors();
        mLastBeatTimestampMs = 0L;
        mPacketCounter = 0;
    }

    @Override
    public void shutDown() {
        super.shutDown();
        mLastBeatTimestampMs = 0L;
        mPacketCounter = 0;
        mRrBeatListeners.clear();
    }
}
