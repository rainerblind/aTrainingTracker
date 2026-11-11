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
    BIKE_POWER(BSportType.BIKE, R.string.activity_type_bike_power, R.drawable.bsport_bike_power);


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
                        SensorType.HR,
                        SensorType.CALORIES,
                        SensorType.SPEED_mps,
                        SensorType.PACE_spm,
                        SensorType.DISTANCE_m,
                        SensorType.ALTITUDE,
                        SensorType.ASCENT,
                        SensorType.DESCENT,
                        SensorType.VERTICAL_SPEED,
                        SensorType.SLOPE,
                        SensorType.TIME_ACTIVE,
                        SensorType.TIME_TOTAL,
                        SensorType.TIME_OF_DAY,
                        SensorType.TIME_LAP,
                        SensorType.DISTANCE_m_LAP,
                        SensorType.LAP_NR};
                break;

            case RUN_SPEED_AND_CADENCE:
                sensors = new SensorType[]{
                        SensorType.HR,
                        SensorType.CALORIES,
                        SensorType.SPEED_mps,
                        SensorType.PACE_spm,
                        SensorType.DISTANCE_m,
                        SensorType.CADENCE,
                        SensorType.STRIDES,
                        SensorType.ALTITUDE,
                        SensorType.ASCENT,
                        SensorType.DESCENT,
                        SensorType.VERTICAL_SPEED,
                        SensorType.SLOPE,
                        SensorType.TIME_ACTIVE,
                        SensorType.TIME_TOTAL,
                        SensorType.TIME_OF_DAY,
                        SensorType.TIME_LAP,
                        SensorType.DISTANCE_m_LAP,
                        SensorType.LAP_NR};
                break;

            case BIKE_SPEED_AND_CADENCE:
                sensors = new SensorType[]{
                        SensorType.SPEED_mps,
                        SensorType.CADENCE,
                        SensorType.HR,
                        SensorType.CALORIES,
                        SensorType.DISTANCE_m,
                        SensorType.ALTITUDE,
                        SensorType.ASCENT,
                        SensorType.DESCENT,
                        SensorType.VERTICAL_SPEED,
                        SensorType.SLOPE,
                        SensorType.TIME_ACTIVE,
                        SensorType.TIME_TOTAL,
                        SensorType.TIME_OF_DAY,
                        SensorType.TIME_LAP,
                        SensorType.DISTANCE_m_LAP,
                        SensorType.LAP_NR};
                break;

            case BIKE_POWER:
                sensors = new SensorType[]{
                        SensorType.POWER,
                        SensorType.HR,
                        SensorType.SPEED_mps,
                        SensorType.CADENCE,
                        SensorType.CALORIES,
                        SensorType.NORMALIZED_POWER,
                        SensorType.POWER_TO_WEIGHT,
                        SensorType.INTENSITY_FACTOR,
                        SensorType.TSS,
                        SensorType.WORK_KJ,
                        SensorType.W_PRIME_BAL,
                        SensorType.POWER_5S,
                        SensorType.POWER_1M,
                        SensorType.POWER_5M,
                        SensorType.POWER_20M,
                        SensorType.POWER_PEAK_5S,
                        SensorType.POWER_PEAK_1M,
                        SensorType.POWER_PEAK_5M,
                        SensorType.POWER_PEAK_20M,
                        SensorType.TORQUE,
                        SensorType.PEDAL_POWER_BALANCE,
                        SensorType.PEDAL_SMOOTHNESS,
                        SensorType.PEDAL_SMOOTHNESS_L,
                        SensorType.PEDAL_SMOOTHNESS_R,
                        SensorType.TORQUE_EFFECTIVENESS_L,
                        SensorType.TORQUE_EFFECTIVENESS_R,
                        SensorType.DISTANCE_m,
                        SensorType.ALTITUDE,
                        SensorType.ASCENT,
                        SensorType.DESCENT,
                        SensorType.VERTICAL_SPEED,
                        SensorType.SLOPE,
                        SensorType.LIVE_HR_DRIFT,
                        SensorType.TIME_ACTIVE,
                        SensorType.TIME_TOTAL,
                        SensorType.TIME_OF_DAY,
                        SensorType.TIME_LAP,
                        SensorType.DISTANCE_m_LAP,
                        SensorType.LAP_NR};
                break;

            default:
                sensors = new SensorType[]{
                        SensorType.HR,
                        SensorType.CALORIES,
                        SensorType.SPEED_mps,
                        SensorType.PACE_spm,
                        SensorType.DISTANCE_m,
                        SensorType.ALTITUDE,
                        SensorType.ASCENT,
                        SensorType.DESCENT,
                        SensorType.VERTICAL_SPEED,
                        SensorType.SLOPE,
                        SensorType.TIME_ACTIVE,
                        SensorType.TIME_TOTAL,
                        SensorType.TIME_OF_DAY,
                        SensorType.TIME_LAP,
                        SensorType.DISTANCE_m_LAP,
                        SensorType.LAP_NR};
                break;
        }

        if (context != null && DevicesDatabaseManager.getInstance(context).haveTemperatureDevice()) {
            sensors = Arrays.copyOf(sensors, sensors.length + 3);
            sensors[sensors.length - 3] = SensorType.TEMPERATURE;
            sensors[sensors.length - 2] = SensorType.TEMPERATURE_MIN;
            sensors[sensors.length - 1] = SensorType.TEMPERATURE_MAX;
        }

        boolean hasHrCapability = false;
        for (SensorType s : sensors) {
            if (s == SensorType.HR) {
                hasHrCapability = true;
                break;
            }
        }
        if (!hasHrCapability && context != null && DevicesDatabaseManager.getInstance(context).haveHeartRateDevice()) {
            hasHrCapability = true;
        }

        sensors = Arrays.copyOf(sensors, sensors.length + 2);
        sensors[sensors.length - 2] = SensorType.PHONE_BATTERY;
        sensors[sensors.length - 1] = SensorType.BATTERY_REMAINING_TIME;

        if (hasHrCapability) {
            sensors = Arrays.copyOf(sensors, sensors.length + 5);
            sensors[sensors.length - 5] = SensorType.HRV_DFA_A1;
            sensors[sensors.length - 4] = SensorType.HRV_RMSSD;
            sensors[sensors.length - 3] = SensorType.RR_INTERVAL;
            sensors[sensors.length - 2] = SensorType.HRV_ARTIFACT_RATE;
            sensors[sensors.length - 1] = SensorType.HRV_SDNN;
        }

        if (activityType != null && activityType.getSportType() == BSportType.BIKE) {
            sensors = Arrays.copyOf(sensors, sensors.length + 6);
            sensors[sensors.length - 6] = SensorType.RADAR_THREAT_COUNT;
            sensors[sensors.length - 5] = SensorType.RADAR_CLOSEST_DISTANCE_m;
            sensors[sensors.length - 4] = SensorType.RADAR_APPROACH_SPEED_mps;
            sensors[sensors.length - 3] = SensorType.RADAR_VEHICLE_COUNT;
            sensors[sensors.length - 2] = SensorType.RADAR_PASSING_DISTANCE_m;
            sensors[sensors.length - 1] = SensorType.RADAR_LARGE_VEHICLE_COUNT;
        }

        return sensors;
    }
}
