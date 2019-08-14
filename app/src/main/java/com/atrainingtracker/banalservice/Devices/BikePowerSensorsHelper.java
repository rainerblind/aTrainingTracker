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

package com.atrainingtracker.banalservice.Devices;

/**
 * Created by rainer on 29.01.18.
 */

public class BikePowerSensorsHelper {

    private static final int POWER_BALANCE = 1;
    private static final int WHEEL_REVOLUTION_DATA = 1 << 1;
    private static final int CRANK_REVOLUTION_DATA = 1 << 2;
    private static final int EXTREME_MAGNITUDES = 1 << 3;
    private static final int EXTREME_ANGLES = 1 << 4;
    private static final int DEAD_SPOT_ANGLES = 1 << 5;
    private static final int ACCUMULATED_TORQUE = 1 << 6;
    private static final int ACCUMULATED_ENERGY = 1 << 7;

    private static final int TORQUE_DATA = 1 << 8;
    private static final int WHEEL_SPEED_DATA = 1 << 9;
    private static final int WHEEL_DISTANCE_DATA = 1 << 10;
    private static final int PEDAL_SMOOTHNESS = 1 << 11;
    private static final int TORQUE_EFFECTIVENESS = 1 << 12;

    // set by the user
    private static final int DOUBLE_POWER_BALANCE_VALUES = 1 << 13;
    private static final int INVERT_POWER_BALANCE_VALUES = 1 << 14;


    private static boolean isSensorSupported(int sensorFlags, int mask) {
        return (sensorFlags & mask) != 0;
    }

    public static boolean isPowerBalanceSupported(int sensorFlags) {
        return isSensorSupported(sensorFlags, POWER_BALANCE);
    }

    public static boolean isWheelRevolutionDataSupported(int sensorFlags) {
        return isSensorSupported(sensorFlags, WHEEL_REVOLUTION_DATA);
    }

    public static boolean isCrankRevolutionDataSupported(int sensorFlags) {
        return isSensorSupported(sensorFlags, CRANK_REVOLUTION_DATA);
    }

    public static boolean isExtremeMagnitudesSupported(int sensorFlags) {
        return isSensorSupported(sensorFlags, EXTREME_MAGNITUDES);
    }

    public static boolean isExtremeAnglesSupported(int sensorFlags) {
        return isSensorSupported(sensorFlags, EXTREME_ANGLES);
    }

    public static boolean isDeadSpotAnglesSupported(int sensorFlags) {
        return isSensorSupported(sensorFlags, DEAD_SPOT_ANGLES);
    }

    public static boolean isAccumulatedTorqueSupported(int sensorFlags) {
        return isSensorSupported(sensorFlags, ACCUMULATED_TORQUE);
    }

    public static boolean isAccumulatedEnergySupported(int sensorFlags) {
        return isSensorSupported(sensorFlags, ACCUMULATED_ENERGY);
    }

    public static boolean isTorqueDataSupported(int sensorFlags) {
        return isSensorSupported(sensorFlags, TORQUE_DATA);
    }

    public static boolean isWheelSpeedDataSupported(int sensorFlags) {
        return isSensorSupported(sensorFlags, WHEEL_SPEED_DATA);
    }

    public static boolean isWheelDistanceDataSupported(int sensorFlags) {
        return isSensorSupported(sensorFlags, WHEEL_DISTANCE_DATA);
    }

    public static boolean isPedalSmoothnessSupported(int sensorFlags) {
        return isSensorSupported(sensorFlags, PEDAL_SMOOTHNESS);
    }

    public static boolean isTorqueEffectivenessSupported(int sensorFlags) {
        return isSensorSupported(sensorFlags, TORQUE_EFFECTIVENESS);
    }

    public static boolean doublePowerBalanceValues(int sensorFlags) {
        return isSensorSupported(sensorFlags, DOUBLE_POWER_BALANCE_VALUES);
    }

    public static boolean invertPowerBalanceValues(int sensorFlags) {
        return isSensorSupported(sensorFlags, INVERT_POWER_BALANCE_VALUES);
    }


    private static int addSensorFlags(int leftSensorFlags, int rightSensorFlags) {
        return leftSensorFlags | rightSensorFlags;
    }

    public static int addPowerBalanceFlag(int sensorFlags) {
        return addSensorFlags(sensorFlags, POWER_BALANCE);
    }

    public static int addWheelRevolutionDataFlag(int sensorFlags) {
        return addSensorFlags(sensorFlags, WHEEL_REVOLUTION_DATA);
    }

    public static int addCrankRevolutionDataFlag(int sensorFlags) {
        return addSensorFlags(sensorFlags, CRANK_REVOLUTION_DATA);
    }

    public static int addExtremeMagnitudesFlag(int sensorFlags) {
        return addSensorFlags(sensorFlags, EXTREME_MAGNITUDES);
    }

    public static int addExtremeAnglesFlag(int sensorFlags) {
        return addSensorFlags(sensorFlags, EXTREME_ANGLES);
    }

    public static int addDeadSpotAnglesFlag(int sensorFlags) {
        return addSensorFlags(sensorFlags, DEAD_SPOT_ANGLES);
    }

    public static int addAccumulatedTorqueFlag(int sensorFlags) {
        return addSensorFlags(sensorFlags, ACCUMULATED_TORQUE);
    }

    public static int addAccumulatedEnergyFlag(int sensorFlags) {
        return addSensorFlags(sensorFlags, ACCUMULATED_ENERGY);
    }

    public static int addTorqueDataFlag(int sensorFlags) {
        return addSensorFlags(sensorFlags, TORQUE_DATA);
    }

    public static int addWheelSpeedDataFlag(int sensorFlags) {
        return addSensorFlags(sensorFlags, WHEEL_SPEED_DATA);
    }

    public static int addWheelDistanceDataFlag(int sensorFlags) {
        return addSensorFlags(sensorFlags, WHEEL_DISTANCE_DATA);
    }

    public static int addPedalSmoothnessFlag(int sensorFlags) {
        return addSensorFlags(sensorFlags, PEDAL_SMOOTHNESS);
    }

    public static int addTorqueEffectivenessFlag(int sensorFlags) {
        return addSensorFlags(sensorFlags, TORQUE_EFFECTIVENESS);
    }

    public static int addDoublePowerBalanceValues(int sensorFlags) {
        return addSensorFlags(sensorFlags, DOUBLE_POWER_BALANCE_VALUES);
    }

    public static int addInvertPowerBalanceValues(int sensorFlags) {
        return addSensorFlags(sensorFlags, INVERT_POWER_BALANCE_VALUES);
    }


    private static int removeSensorFlags(int leftSensorFlags, int rightSensorFlags) {
        return leftSensorFlags & ~rightSensorFlags;
    }

    public static int removeDoublePowerBalanceValues(int sensorFlags) {
        return removeSensorFlags(sensorFlags, DOUBLE_POWER_BALANCE_VALUES);
    }

    public static int removeInvertPowerBalanceValues(int sensorFlags) {
        return removeSensorFlags(sensorFlags, INVERT_POWER_BALANCE_VALUES);
    }

}
