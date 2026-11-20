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
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.atrainingtracker.banalservice.BANALService;
import com.atrainingtracker.banalservice.devices.DeviceType;
import com.atrainingtracker.banalservice.devices.ProtocolType;
import com.atrainingtracker.banalservice.devices.RrBeatEvent;
import com.atrainingtracker.banalservice.devices.RrBeatListener;
import com.atrainingtracker.banalservice.devices.RrBeatNormalization;
import com.atrainingtracker.banalservice.devices.RrBeatSource;
import com.atrainingtracker.banalservice.devices.bluetooth_le.polar.PolarOfflineCapabilityStore;
import com.atrainingtracker.banalservice.devices.bluetooth_le.polar.PolarOfflineGattCoordinator;
import com.atrainingtracker.banalservice.devices.hrv.HrvSignalQualityResult;
import com.atrainingtracker.banalservice.devices.hrv.HrvTelemetryProvider;
import com.atrainingtracker.banalservice.devices.hrv.PerDeviceHrvController;
import com.atrainingtracker.banalservice.sensor.MySensor;
import com.atrainingtracker.banalservice.sensor.MySensorManager;
import com.atrainingtracker.banalservice.sensor.SensorType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Bluetooth Low Energy Heart Rate Device (GATT Service 0x180D, Characteristic 0x2A37).
 * Parses instant heart rate and beat-to-beat RR intervals with cumulative timestamping (REQ-CON-027, ATT-3540).
 */
public class BTLEHeartRateDevice extends MyBTLEDevice implements RrBeatSource, HrvTelemetryProvider {
    private static final boolean DEBUG = BANALService.getDebug(false);
    private static final String TAG = "BTLEHeartRateDevice";

    protected MySensor<Integer> mHeartRateSensor;
    protected MySensor<Integer> mRrIntervalSensor = null;
    private final PerDeviceHrvController mPerDeviceHrvController;

    private final List<RrBeatListener> mRrBeatListeners = new CopyOnWriteArrayList<>();
    private double mLastBeatTimestampMs = 0.0;
    private double mLastNormalRrMs = RrBeatNormalization.DEFAULT_REFERENCE_RR_MS;
    private int mPacketCounter = 0;

    /**
     * Constructor
     */
    public BTLEHeartRateDevice(Context context, MySensorManager mySensorManager, long deviceID, String address) {
        super(context, mySensorManager, DeviceType.HRM, deviceID, address);
        mPerDeviceHrvController = createPerDeviceHrvController();
        if (DEBUG) {
            Log.d(TAG, "creating HR device");
        }
    }

    protected PerDeviceHrvController createPerDeviceHrvController() {
        return new PerDeviceHrvController(mContext, this, createHrvHostCallbacks());
    }

    protected final PerDeviceHrvController.HostCallbacks createHrvHostCallbacks() {
        return new PerDeviceHrvController.HostCallbacks() {
            @Override
            @SuppressWarnings({"unchecked", "rawtypes"})
            public void addAndRegisterSensors(@NonNull List<MySensor<?>> sensors) {
                if (mMySensorManager != null) {
                    BTLEHeartRateDevice.this.addAndRegisterSensors(new ArrayList<MySensor>(sensors));
                } else {
                    for (MySensor<?> sensor : sensors) {
                        addSensor(sensor);
                        sensor.activateSensor();
                    }
                    notifySensorsChanged();
                }
            }

            @Override
            public void unregisterSensor(@NonNull MySensor<?> sensor) {
                if (mMySensorManager != null) {
                    mMySensorManager.unregisterSensor(sensor);
                }
            }

            @Override
            public void removeSensor(@NonNull SensorType sensorType) {
                BTLEHeartRateDevice.this.removeSensor(sensorType);
            }
        };
    }

    @VisibleForTesting
    public PerDeviceHrvController getPerDeviceHrvController() {
        return mPerDeviceHrvController;
    }

    @NonNull
    @Override
    public HrvSignalQualityResult getSignalQualityResult() {
        return mPerDeviceHrvController.getSignalQualityResult();
    }

    @Nullable
    @Override
    public Double getLatestArtifactRatePercent() {
        return mPerDeviceHrvController.getLatestArtifactRatePercent();
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
        if (mPerDeviceHrvController != null) {
            mPerDeviceHrvController.onRrBeat(event);
        }
        for (RrBeatListener listener : mRrBeatListeners) {
            try {
                listener.onRrBeat(event);
            } catch (Exception e) {
                Log.e(TAG, "Error in RrBeatListener callback", e);
            }
        }
    }

    @Override
    protected void configureServices(BluetoothGatt gatt) {
        if (gatt != null) {
            boolean hasPmd = gatt.getService(BluetoothConstants.UUID_SERVICE_POLAR_PMD) != null;
            boolean hasPftp = gatt.getService(BluetoothConstants.UUID_SERVICE_POLAR_PFTP) != null;
            if (hasPmd || hasPftp) {
                PolarOfflineCapabilityStore.recordGattServiceCapabilities(mContext, getAddress(), hasPmd, hasPftp);
                BluetoothGattService disService = gatt.getService(BluetoothConstants.UUID_SERVICE_DEVICE_INFORMATION);
                if (disService != null) {
                    BluetoothGattCharacteristic modelChar =
                            disService.getCharacteristic(BluetoothConstants.UUID_CHARACTERISTIC_MODEL_NUMBER);
                    if (modelChar != null) {
                        enqueueReadCharacteristic(modelChar);
                    }
                    BluetoothGattCharacteristic serialChar =
                            disService.getCharacteristic(BluetoothConstants.UUID_CHARACTERISTIC_SERIAL_NUMBER);
                    if (serialChar != null) {
                        enqueueReadCharacteristic(serialChar);
                    }
                }
            }
        }
        super.configureServices(gatt);
    }

    @Override
    protected void onOtherCharacteristicUpdate(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        if (characteristic == null || !PolarOfflineCapabilityStore.hasPolarServiceCapability(mContext, getAddress())) {
            return;
        }
        UUID uuid = characteristic.getUuid();
        if (BluetoothConstants.UUID_CHARACTERISTIC_MODEL_NUMBER.equals(uuid)) {
            String modelNumber = characteristic.getStringValue(0);
            PolarOfflineCapabilityStore.recordDisMetadata(mContext, getAddress(), modelNumber, null);
        } else if (BluetoothConstants.UUID_CHARACTERISTIC_SERIAL_NUMBER.equals(uuid)) {
            String serialNumber = characteristic.getStringValue(0);
            if (PolarOfflineCapabilityStore.isValidCanonicalPolarId(serialNumber)) {
                PolarOfflineCapabilityStore.recordDisMetadata(mContext, getAddress(), null, serialNumber);
                PolarOfflineGattCoordinator.registerDeviceAliases(serialNumber, getAddress());
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

                List<Double> rrList = new ArrayList<>();
                while (nextOffset + 2 <= value.length) {
                    int rawRr = (value[nextOffset] & 0xFF) | ((value[nextOffset + 1] & 0xFF) << 8);
                    nextOffset += 2;
                    double rrMs = rawRr * 1000.0 / 1024.0;
                    rrList.add(rrMs);
                }

                if (!rrList.isEmpty()) {
                    RrBeatNormalization.NormalizedBundle bundle = RrBeatNormalization.reconstructBundle(
                            rrList,
                            packetArrivalMs,
                            mLastBeatTimestampMs,
                            mLastNormalRrMs,
                            mPacketCounter,
                            ProtocolType.BLE
                    );
                    for (RrBeatEvent event : bundle.getEvents()) {
                        long roundedRrMs = Math.round(event.getRrMs());
                        if (mRrIntervalSensor != null) {
                            mRrIntervalSensor.newValue((int) roundedRrMs);
                        }
                        dispatchRrBeat(event);
                    }
                    mLastBeatTimestampMs = bundle.getLastBeatTimestampMs();
                    mLastNormalRrMs = bundle.getLastNormalRrMs();
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Error parsing HR characteristic notification", e);
        }
    }

    @Override
    protected void onAccumulatorsReset() {
        super.onAccumulatorsReset();
        if (mPerDeviceHrvController != null) {
            mPerDeviceHrvController.onAccumulatorsReset();
        }
    }

    @Override
    protected void unregisterSensors() {
        if (mPerDeviceHrvController != null) {
            mPerDeviceHrvController.onDisconnect();
        }
        if (mRrIntervalSensor != null) {
            if (mMySensorManager != null) {
                mMySensorManager.unregisterSensor(mRrIntervalSensor);
            }
            removeSensor(SensorType.RR_INTERVAL);
            mRrIntervalSensor = null;
        }
        super.unregisterSensors();
        mLastBeatTimestampMs = 0.0;
        mLastNormalRrMs = RrBeatNormalization.DEFAULT_REFERENCE_RR_MS;
        mPacketCounter = 0;
    }

    @Override
    public void shutDown() {
        if (mPerDeviceHrvController != null) {
            mPerDeviceHrvController.shutDown();
        }
        if (mRrIntervalSensor != null) {
            if (mMySensorManager != null) {
                mMySensorManager.unregisterSensor(mRrIntervalSensor);
            }
            removeSensor(SensorType.RR_INTERVAL);
            mRrIntervalSensor = null;
        }
        super.shutDown();
        mLastBeatTimestampMs = 0.0;
        mLastNormalRrMs = RrBeatNormalization.DEFAULT_REFERENCE_RR_MS;
        mPacketCounter = 0;
        mRrBeatListeners.clear();
    }
}
