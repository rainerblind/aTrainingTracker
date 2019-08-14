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

package com.atrainingtracker.banalservice.Devices.bluetooth_le;

import com.atrainingtracker.banalservice.Devices.DeviceType;

import java.util.HashMap;
import java.util.UUID;

public class BluetoothConstants {


    private static final String SERVICE_HEART_RATE = "0000180d-0000-1000-8000-00805f9b34fb";
    private static final String SERVICE_RUNNING_SPEED_AND_CADENCE = "00001814-0000-1000-8000-00805f9b34fb";
    private static final String SERVICE_CYCLING_SPEED_AND_CADENCE = "00001816-0000-1000-8000-00805f9b34fb";
    private static final String SERVICE_CYCLING_POWER = "00001818-0000-1000-8000-00805f9b34fb";

    private static final String CHARACTERISTIC_HEART_RATE_MEASUREMENT = "00002a37-0000-1000-8000-00805f9b34fb";
    private static final String CHARACTERISTIC_CYCLING_POWER_MEASUREMENT = "00002a63-0000-1000-8000-00805f9b34fb";
    private static final String CHARACTERISTIC_CYCLING_SPEED_AND_CADENCE_MEASUREMENT = "00002a5b-0000-1000-8000-00805f9b34fb";
    private static final String CHARACTERISTIC_RUNNING_SPEED_AND_CADENCE_MEASUREMENT = "00002a53-0000-1000-8000-00805f9b34fb";
    private static final String SERVICE_DEVICE_INFORMATION = "0000180a-0000-1000-8000-00805f9b34fb";
    public static final UUID UUID_SERVICE_DEVICE_INFORMATION = UUID.fromString(SERVICE_DEVICE_INFORMATION);
    private static final String SERVICE_BATTERY = "0000180f-0000-1000-8000-00805f9b34fb";
    public static final UUID UUID_SERVICE_BATTERY = UUID.fromString(SERVICE_BATTERY);
    private static final String CHARACTERISTIC_CLIENT_CONFIG = "00002902-0000-1000-8000-00805f9b34fb";
    public static final UUID UUID_CHARACTERISTIC_CLIENT_CONFIG = UUID.fromString(CHARACTERISTIC_CLIENT_CONFIG);
    private static final String CHARACTERISTIC_BATTERY = "00002a19-0000-1000-8000-00805f9b34fb";
    public static final UUID UUID_CHARACTERISTIC_BATTERY_LEVEL = UUID.fromString(CHARACTERISTIC_BATTERY);
    private static final String CHARACTERISTIC_MANUFACTURER_NAME = "00002a29-0000-1000-8000-00805f9b34fb";
    public static final UUID UUID_CHARACTERISTIC_MANUFACTURER_NAME = UUID.fromString(CHARACTERISTIC_MANUFACTURER_NAME);
    private static final String CHARACTERISTIC_CYCLING_SPEED_AND_CADENCE_FEATURE = "00002a5c-0000-1000-8000-00805f9b34fb";
    public static final UUID UUID_CHARACTERISTIC_CYCLING_SPEED_AND_CADENCE_FEATURE = UUID.fromString(CHARACTERISTIC_CYCLING_SPEED_AND_CADENCE_FEATURE);
    private static final String CHARACTERISTIC_CYCLING_POWER_FEATURE = "00002a65-0000-1000-8000-00805f9b34fb";
    public static final UUID UUID_CHARACTERISTIC_CYCLING_POWER_FEATURE = UUID.fromString(CHARACTERISTIC_CYCLING_POWER_FEATURE);
    private static HashMap<DeviceType, UUID> SERVICE_UUIDS = new HashMap<DeviceType, UUID>();
    private static HashMap<DeviceType, UUID> CHARACTERISTIC_MEASUREMENT_UUIDS = new HashMap<DeviceType, UUID>();

    static {
        SERVICE_UUIDS.put(DeviceType.HRM, UUID.fromString(SERVICE_HEART_RATE));
        SERVICE_UUIDS.put(DeviceType.RUN_SPEED, UUID.fromString(SERVICE_RUNNING_SPEED_AND_CADENCE));
        SERVICE_UUIDS.put(DeviceType.BIKE_SPEED_AND_CADENCE, UUID.fromString(SERVICE_CYCLING_SPEED_AND_CADENCE));
        SERVICE_UUIDS.put(DeviceType.BIKE_SPEED, UUID.fromString(SERVICE_CYCLING_SPEED_AND_CADENCE));   // in BT_LE, a bike speed sensor is essentially a combined sensor without cadence
        SERVICE_UUIDS.put(DeviceType.BIKE_CADENCE, UUID.fromString(SERVICE_CYCLING_SPEED_AND_CADENCE));   // in BT_LE, a bike cadence sensor is essentially a combined sensor without speed
        SERVICE_UUIDS.put(DeviceType.BIKE_POWER, UUID.fromString(SERVICE_CYCLING_POWER));

        CHARACTERISTIC_MEASUREMENT_UUIDS.put(DeviceType.HRM, UUID.fromString(CHARACTERISTIC_HEART_RATE_MEASUREMENT));
        CHARACTERISTIC_MEASUREMENT_UUIDS.put(DeviceType.RUN_SPEED, UUID.fromString(CHARACTERISTIC_RUNNING_SPEED_AND_CADENCE_MEASUREMENT));
        CHARACTERISTIC_MEASUREMENT_UUIDS.put(DeviceType.BIKE_SPEED_AND_CADENCE, UUID.fromString(CHARACTERISTIC_CYCLING_SPEED_AND_CADENCE_MEASUREMENT));
        CHARACTERISTIC_MEASUREMENT_UUIDS.put(DeviceType.BIKE_SPEED, UUID.fromString(CHARACTERISTIC_CYCLING_SPEED_AND_CADENCE_MEASUREMENT));
        CHARACTERISTIC_MEASUREMENT_UUIDS.put(DeviceType.BIKE_CADENCE, UUID.fromString(CHARACTERISTIC_CYCLING_SPEED_AND_CADENCE_MEASUREMENT));
        CHARACTERISTIC_MEASUREMENT_UUIDS.put(DeviceType.BIKE_POWER, UUID.fromString(CHARACTERISTIC_CYCLING_POWER_MEASUREMENT));
    }

    public static UUID getServiceUUID(DeviceType deviceType) {
        return SERVICE_UUIDS.get(deviceType);
    }

    public static UUID getCharacteristicUUID(DeviceType deviceType) {
        return CHARACTERISTIC_MEASUREMENT_UUIDS.get(deviceType);
    }
}
