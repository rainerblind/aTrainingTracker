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

package com.atrainingtracker.banalservice;

import android.content.Context;

import com.atrainingtracker.R;
import com.atrainingtracker.banalservice.sensor.SensorType;
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager;

import java.util.Arrays;

public enum ActivityType {
    // Note that in the pase, we had some more ActivityTypes.  They have been removed on 07.03.2026.
    // They most not be renamed since these names are uses as keys in the database for the tracking tabs.

    // GENERIC(BSportType.UNKNOWN, R.string.activity_type_multisport),
    GENERIC_HR(BSportType.UNKNOWN, R.string.activity_type_multisport_with_hr, R.drawable.bsport_other),
    // RUN_SPEED(BSportType.RUN, R.string.activity_type_run_speed, R.string.activity_type_short_run_speed),
    RUN_SPEED_AND_CADENCE(BSportType.RUN, R.string.activity_type_run_speed_and_cadence, R.drawable.bsport_run),
    // BIKE_SPEED(BSportType.BIKE, R.string.activity_type_bike_speed),
    BIKE_SPEED_AND_CADENCE(BSportType.BIKE, R.string.activity_type_bike_speed_and_cadence, R.drawable.bsport_bike),
    BIKE_POWER(BSportType.BIKE, R.string.activity_type_bike_power, R.drawable.bsport_bike);


    private final BSportType sportType;
    private final int titleId;
    private final int logoId;

    ActivityType(BSportType sportType, int titleId, int logoId) {
        this.sportType = sportType;
        this.titleId = titleId;
        this.logoId = logoId;
    }

    public static ActivityType getDefaultActivityType() {
        return GENERIC_HR;
    }

    public BSportType getSportType() {
        return sportType;
    }

    @Deprecated // use getUIName instead
    public int getTitleId() {
        return titleId;
    }
    public String getUIName(Context context) {
        return context.getString(titleId);
    }

    public int getLogoId() {
        return logoId;
    }

    public static SensorType[] getSensorTypeArray(ActivityType activityType, Context context) {
        // TODO: might be better done with sets and then somehow sort them?
        SensorType[] sensors;

        switch (activityType) {
            case GENERIC_HR:
                sensors = new SensorType[]{
                        SensorType.ALTITUDE,
                        SensorType.ASCENT,
                        SensorType.DESCENT,
                        SensorType.VERTICAL_SPEED,
                        SensorType.SLOPE,
                        // SensorType.CALORIES,
                        SensorType.HR,
                        SensorType.DISTANCE_m,
                        SensorType.PACE_spm,
                        SensorType.SPEED_mps,
                        SensorType.TIME_OF_DAY,
                        SensorType.TIME_ACTIVE,
                        SensorType.TIME_TOTAL,
                        SensorType.LAP_NR,
                        SensorType.TIME_LAP,
                        SensorType.DISTANCE_m_LAP};
                break;

            case RUN_SPEED_AND_CADENCE:
                sensors = new SensorType[]{
                        SensorType.ALTITUDE,
                        SensorType.ASCENT,
                        SensorType.DESCENT,
                        SensorType.VERTICAL_SPEED,
                        SensorType.SLOPE,
                        SensorType.HR,
                        SensorType.CADENCE,
                        SensorType.CALORIES,
                        SensorType.DISTANCE_m,
                        SensorType.PACE_spm,
                        SensorType.SPEED_mps,
                        SensorType.STRIDES,
                        SensorType.TIME_OF_DAY,
                        SensorType.TIME_ACTIVE,
                        SensorType.TIME_TOTAL,
                        SensorType.LAP_NR,
                        SensorType.TIME_LAP,
                        SensorType.DISTANCE_m_LAP};
                break;

            case BIKE_SPEED_AND_CADENCE:
                sensors = new SensorType[]{
                        SensorType.ALTITUDE,
                        SensorType.ASCENT,
                        SensorType.DESCENT,
                        SensorType.VERTICAL_SPEED,
                        SensorType.SLOPE,
                        SensorType.HR,
                        SensorType.CADENCE,
                        // SensorType.CALORIES,
                        SensorType.DISTANCE_m,
                        // SensorType.PACE_spm,
                        SensorType.SPEED_mps,
                        SensorType.TIME_OF_DAY,
                        SensorType.TIME_ACTIVE,
                        SensorType.TIME_TOTAL,
                        SensorType.LAP_NR,
                        SensorType.TIME_LAP,
                        SensorType.DISTANCE_m_LAP};
                break;

            case BIKE_POWER:
                sensors = new SensorType[]{
                        SensorType.ALTITUDE,
                        SensorType.ASCENT,
                        SensorType.DESCENT,
                        SensorType.VERTICAL_SPEED,
                        SensorType.SLOPE,
                        SensorType.HR,
                        SensorType.CADENCE,
                        // SensorType.CALORIES,
                        // SensorType.PACE_spm,
                        SensorType.PEDAL_POWER_BALANCE,
                        SensorType.PEDAL_SMOOTHNESS_L,
                        SensorType.PEDAL_SMOOTHNESS_R,
                        SensorType.PEDAL_SMOOTHNESS,
                        SensorType.POWER,
                        SensorType.DISTANCE_m,
                        SensorType.SPEED_mps,
                        SensorType.TIME_OF_DAY,
                        SensorType.TIME_ACTIVE,
                        SensorType.TIME_TOTAL,
                        SensorType.TORQUE,
                        SensorType.TORQUE_EFFECTIVENESS_L,
                        SensorType.TORQUE_EFFECTIVENESS_R,
                        SensorType.LAP_NR,
                        SensorType.TIME_LAP,
                        SensorType.DISTANCE_m_LAP};
                break;

            default:
                sensors = new SensorType[]{
                        SensorType.ALTITUDE,
                        SensorType.ASCENT,
                        SensorType.DESCENT,
                        SensorType.VERTICAL_SPEED,
                        SensorType.SLOPE,
                        SensorType.DISTANCE_m,
                        SensorType.PACE_spm,
                        SensorType.SPEED_mps,
                        SensorType.TIME_OF_DAY,
                        SensorType.TIME_ACTIVE,
                        SensorType.TIME_TOTAL,
                        SensorType.LAP_NR,
                        SensorType.TIME_LAP,
                        SensorType.DISTANCE_m_LAP};
                break;
        }

        if (context != null && DevicesDatabaseManager.getInstance(context).haveTemperatureDevice()) {
            sensors = Arrays.copyOf(sensors, sensors.length + 3);
            sensors[sensors.length - 3] = SensorType.TEMPERATURE;
            sensors[sensors.length - 2] = SensorType.TEMPERATURE_MIN;
            sensors[sensors.length - 1] = SensorType.TEMPERATURE_MAX;
        }

        return sensors;
    }
}
