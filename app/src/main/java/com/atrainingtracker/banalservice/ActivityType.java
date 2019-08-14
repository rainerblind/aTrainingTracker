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

package com.atrainingtracker.banalservice;

import android.content.Context;

import com.atrainingtracker.R;
import com.atrainingtracker.banalservice.Sensor.SensorType;
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager;

import java.util.Arrays;

public enum ActivityType {
    //                                                                                                                                              power  HR     cadence
    GENERIC(BSportType.UNKNOWN, R.string.activity_type_multisport, R.string.activity_type_short_multisport, false, false, false),
    GENERIC_HR(BSportType.UNKNOWN, R.string.activity_type_multisport_with_hr, R.string.activity_type_short_multisport_with_hr, false, true, false),
    RUN_SPEED(BSportType.RUN, R.string.activity_type_run_speed, R.string.activity_type_short_run_speed, false, true, false),
    RUN_SPEED_AND_CADENCE(BSportType.RUN, R.string.activity_type_run_speed_and_cadence, R.string.activity_type_short_run_speed_and_cadence, false, true, true),
    BIKE_SPEED(BSportType.BIKE, R.string.activity_type_bike_speed, R.string.activity_type_short_bike_speed, false, true, false),
    BIKE_SPEED_AND_CADENCE(BSportType.BIKE, R.string.activity_type_bike_speed_and_cadence, R.string.activity_type_short_bike_speed_and_cadence, false, true, true),
    BIKE_POWER(BSportType.BIKE, R.string.activity_type_bike_power, R.string.activity_type_short_bike_power, true, true, true);


    private final BSportType sportType;
    private final int titleId;
    private final int titleIdShort;
    private final boolean havePower;
    private final boolean haveHR;
    private final boolean haveCadence;

    ActivityType(BSportType sportType, int titleId, int titleIdShort, boolean havePower, boolean haveHR, boolean haveCadence) {
        this.sportType = sportType;
        this.titleId = titleId;
        this.titleIdShort = titleIdShort;
        this.havePower = havePower;
        this.haveHR = haveHR;
        this.haveCadence = haveCadence;
    }

    public static SensorType[] getSensorTypeArray(ActivityType activityType, Context context) {
        // TODO: might be better done with sets and then somehow sort them?
        SensorType[] sensors;

        switch (activityType) {
            case GENERIC_HR:
                sensors = new SensorType[]{
                        SensorType.ACCUMULATED_SENSORS,
                        SensorType.ACCURACY,
                        SensorType.ALTITUDE,
                        // SensorType.CALORIES,
                        SensorType.DISTANCE_m,
                        SensorType.DISTANCE_m_LAP,
                        SensorType.HR,
                        SensorType.LAP_NR,
                        SensorType.LATITUDE,
                        SensorType.LONGITUDE,
                        SensorType.PACE_spm,
                        SensorType.SPEED_mps,
                        SensorType.SENSORS,
                        SensorType.TIME_OF_DAY,
                        SensorType.TIME_ACTIVE,
                        SensorType.TIME_LAP,
                        SensorType.TIME_TOTAL};
                break;

            case RUN_SPEED:
                sensors = new SensorType[]{
                        SensorType.ACCUMULATED_SENSORS,
                        SensorType.ACCURACY,
                        SensorType.ALTITUDE,
                        // SensorType.CALORIES,
                        SensorType.DISTANCE_m,
                        SensorType.DISTANCE_m_LAP,
                        SensorType.HR,
                        SensorType.LAP_NR,
                        SensorType.LATITUDE,
                        SensorType.LONGITUDE,
                        SensorType.PACE_spm,
                        SensorType.SPEED_mps,
                        SensorType.SENSORS,
                        SensorType.STRIDES,
                        SensorType.TIME_OF_DAY,
                        SensorType.TIME_ACTIVE,
                        SensorType.TIME_LAP,
                        SensorType.TIME_TOTAL};
                break;

            case RUN_SPEED_AND_CADENCE:
                sensors = new SensorType[]{
                        SensorType.ACCUMULATED_SENSORS,
                        SensorType.ACCURACY,
                        SensorType.ALTITUDE,
                        SensorType.CADENCE,
                        SensorType.CALORIES,
                        SensorType.DISTANCE_m,
                        SensorType.DISTANCE_m_LAP,
                        SensorType.HR,
                        SensorType.LAP_NR,
                        SensorType.LATITUDE,
                        SensorType.LONGITUDE,
                        SensorType.PACE_spm,
                        SensorType.SPEED_mps,
                        SensorType.SENSORS,
                        SensorType.STRIDES,
                        SensorType.TIME_OF_DAY,
                        SensorType.TIME_ACTIVE,
                        SensorType.TIME_LAP,
                        SensorType.TIME_TOTAL};
                break;

            case BIKE_SPEED:
                sensors = new SensorType[]{
                        SensorType.ACCUMULATED_SENSORS,
                        SensorType.ACCURACY,
                        SensorType.ALTITUDE,
                        // SensorType.CALORIES,
                        SensorType.DISTANCE_m,
                        SensorType.DISTANCE_m_LAP,
                        SensorType.HR,
                        SensorType.LAP_NR,
                        SensorType.LATITUDE,
                        SensorType.LONGITUDE,
                        // SensorType.PACE_spm,
                        SensorType.SPEED_mps,
                        SensorType.SENSORS,
                        SensorType.TIME_OF_DAY,
                        SensorType.TIME_ACTIVE,
                        SensorType.TIME_LAP,
                        SensorType.TIME_TOTAL};
                break;

            case BIKE_SPEED_AND_CADENCE:
                sensors = new SensorType[]{
                        SensorType.ACCUMULATED_SENSORS,
                        SensorType.ACCURACY,
                        SensorType.ALTITUDE,
                        SensorType.CADENCE,
                        // SensorType.CALORIES,
                        SensorType.DISTANCE_m,
                        SensorType.DISTANCE_m_LAP,
                        SensorType.HR,
                        SensorType.LAP_NR,
                        SensorType.LATITUDE,
                        SensorType.LONGITUDE,
                        // SensorType.PACE_spm,
                        SensorType.SPEED_mps,
                        SensorType.SENSORS,
                        SensorType.TIME_OF_DAY,
                        SensorType.TIME_ACTIVE,
                        SensorType.TIME_LAP,
                        SensorType.TIME_TOTAL};
                break;

            case BIKE_POWER:
                sensors = new SensorType[]{
                        SensorType.ACCUMULATED_SENSORS,
                        SensorType.ACCURACY,
                        SensorType.ALTITUDE,
                        SensorType.CADENCE,
                        // SensorType.CALORIES,
                        SensorType.DISTANCE_m,
                        SensorType.DISTANCE_m_LAP,
                        SensorType.HR,
                        SensorType.LAP_NR,
                        SensorType.LATITUDE,
                        SensorType.LONGITUDE,
                        // SensorType.PACE_spm,
                        SensorType.PEDAL_POWER_BALANCE,
                        SensorType.PEDAL_SMOOTHNESS_L,
                        SensorType.PEDAL_SMOOTHNESS_R,
                        SensorType.PEDAL_SMOOTHNESS,
                        SensorType.POWER,
                        SensorType.SPEED_mps,
                        SensorType.SENSORS,
                        SensorType.TIME_OF_DAY,
                        SensorType.TIME_ACTIVE,
                        SensorType.TIME_LAP,
                        SensorType.TIME_TOTAL,
                        SensorType.TORQUE,
                        SensorType.TORQUE_EFFECTIVENESS_L,
                        SensorType.TORQUE_EFFECTIVENESS_R};
                break;

            case GENERIC:
            default:
                sensors = new SensorType[]{
                        SensorType.ACCUMULATED_SENSORS,
                        SensorType.ACCURACY,
                        SensorType.ALTITUDE,
                        SensorType.DISTANCE_m,
                        SensorType.DISTANCE_m_LAP,
                        SensorType.LAP_NR,
                        SensorType.LATITUDE,
                        SensorType.LONGITUDE,
                        SensorType.PACE_spm,
                        SensorType.SPEED_mps,
                        SensorType.SENSORS,
                        SensorType.TIME_OF_DAY,
                        SensorType.TIME_ACTIVE,
                        SensorType.TIME_LAP,
                        SensorType.TIME_TOTAL};
                break;
        }

        if (context != null && DevicesDatabaseManager.haveTemperatureDevice(context)) {
            sensors = Arrays.copyOf(sensors, sensors.length + 3);
            sensors[sensors.length - 3] = SensorType.TEMPERATURE;
            sensors[sensors.length - 2] = SensorType.TEMPERATURE_MIN;
            sensors[sensors.length - 1] = SensorType.TEMPERATURE_MAX;
        }

        return sensors;
    }

    public static ActivityType getDefaultActivityType() {
        return GENERIC;
    }

    public BSportType getSportType() {
        return sportType;
    }

    public int getTitleId() {
        return titleId;
    }

    public int getShortTitleId() {
        return titleIdShort;
    }

    public boolean havePower() {
        return havePower;
    }

    public boolean haveHR() {
        return haveHR;
    }

    public boolean haveCadence() {
        return haveCadence;
    }
}
