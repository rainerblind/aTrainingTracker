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

package com.atrainingtracker.banalservice.sensor;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;

import com.atrainingtracker.R;
import com.atrainingtracker.banalservice.sensor.formater.AltitudeFormatter;
import com.atrainingtracker.banalservice.sensor.formater.BatteryRemainingTimeFormatter;
import com.atrainingtracker.banalservice.sensor.formater.CadenceFormatter;
import com.atrainingtracker.banalservice.sensor.formater.DefaultNumberFormatter;
import com.atrainingtracker.banalservice.sensor.formater.DefaultStringFormatter;
import com.atrainingtracker.banalservice.sensor.formater.DistanceFormatter;
import com.atrainingtracker.banalservice.sensor.formater.IntegerFormatter;
import com.atrainingtracker.banalservice.sensor.formater.IntensityFactorFormatter;
import com.atrainingtracker.banalservice.sensor.formater.MyFormatter;
import com.atrainingtracker.banalservice.sensor.formater.PaceFormatter;
import com.atrainingtracker.banalservice.sensor.formater.PowerToWeightFormatter;
import com.atrainingtracker.banalservice.sensor.formater.SpeedFormatter;
import com.atrainingtracker.banalservice.sensor.formater.TimeFormatter;
import com.atrainingtracker.trainingtracker.TrainingApplication;


public enum SensorType
        implements Parcelable {
    ACCUMULATED_SENSORS(SensorCategory.SYSTEM_ENVIRONMENT_AND_DIAGNOSTICS, R.string.accumulated_sensors, R.string.accumulated_sensors_short, R.string.units_none, SensorValueType.STRING, new DefaultStringFormatter(), false),
    ACCURACY(SensorCategory.SYSTEM_ENVIRONMENT_AND_DIAGNOSTICS, R.string.accuracy, R.string.accuracy_short, R.string.units_distance_basic, SensorValueType.DOUBLE, new DefaultNumberFormatter(), true),  // TODO: special formatter?
    // ACCURACY_gps           (R.string.accuracy,               R.string.accuracy_short,               R.string.units_distance_basic,  SensorValueType.DOUBLE,  new DefaultNumberFormatter()),  // TODO: special formatter?
    // ACCURACY_network       (R.string.accuracy,               R.string.accuracy_short,               R.string.units_distance_basic,  SensorValueType.DOUBLE,  new DefaultNumberFormatter()),  // TODO: special formatter?
    // ACCURACY_google_fused  (R.string.accuracy,               R.string.accuracy_short,               R.string.units_distance_basic,  SensorValueType.DOUBLE,  new DefaultNumberFormatter()),  // TODO: special formatter?
    ALTITUDE(SensorCategory.ELEVATION_AND_NAVIGATION, R.string.altitude, R.string.altitude_short, R.string.units_distance_basic, SensorValueType.DOUBLE, new AltitudeFormatter(), true),
    // ALTITUDE_gps           (R.string.altitude,               R.string.altitude_short,               R.string.units_distance_basic,  SensorValueType.DOUBLE,  new DefaultNumberFormatter()),
    // ALTITUDE_network       (R.string.altitude,               R.string.altitude_short,               R.string.units_distance_basic,  SensorValueType.DOUBLE,  new DefaultNumberFormatter()),
    // ALTITUDE_google_fused  (R.string.altitude,               R.string.altitude_short,               R.string.units_distance_basic,  SensorValueType.DOUBLE,  new DefaultNumberFormatter()),
    ASCENT(SensorCategory.ELEVATION_AND_NAVIGATION, R.string.ascent, R.string.ascent_short, R.string.units_distance_basic, SensorValueType.DOUBLE, new AltitudeFormatter(), false),
    BEARING(SensorCategory.ELEVATION_AND_NAVIGATION, R.string.bearing, R.string.bearing_short, R.string.units_degree, SensorValueType.DOUBLE, new DefaultNumberFormatter(), true),
    // BEARING_gps            (R.string.bearing,                R.string.bearing_short,                R.string.units_degree,          SensorValueType.DOUBLE,  new DefaultNumberFormatter()),
    // BEARING_network        (R.string.bearing,                R.string.bearing_short,                R.string.units_degree,          SensorValueType.DOUBLE,  new DefaultNumberFormatter()),
    // BEARING_google_fused   (R.string.bearing,                R.string.bearing_short,                R.string.units_degree,          SensorValueType.DOUBLE,  new DefaultNumberFormatter()),
    CADENCE(SensorCategory.CADENCE_AND_RUNNING, R.string.cadence, R.string.cadence_short, R.string.units_rpm, SensorValueType.DOUBLE, new IntegerFormatter(), true),
    CALORIES(SensorCategory.HEART_RATE_AND_ENERGY, R.string.calories, R.string.calories_short, R.string.units_calories, SensorValueType.INTEGER, new IntegerFormatter(), false),
    DESCENT(SensorCategory.ELEVATION_AND_NAVIGATION, R.string.descent, R.string.descent_short, R.string.units_distance_basic, SensorValueType.DOUBLE, new AltitudeFormatter(), false),
    DISTANCE_m(SensorCategory.SPEED_PACE_AND_DISTANCE, R.string.distance, R.string.distance_short, R.string.units_distance_basic, SensorValueType.DOUBLE, new DistanceFormatter(), false),
    // DISTANCE_m_gps         (R.string.distance,               R.string.distance_short,               R.string.units_distance_basic,  SensorValueType.DOUBLE,  new DistanceFormatter()),
    // DISTANCE_m_network     (R.string.distance,               R.string.distance_short,               R.string.units_distance_basic,  SensorValueType.DOUBLE,  new DistanceFormatter()),
    // DISTANCE_m_google_fused(R.string.distance,               R.string.distance_short,               R.string.units_distance_basic,  SensorValueType.DOUBLE,  new DistanceFormatter()),
    DISTANCE_m_LAP(SensorCategory.SPEED_PACE_AND_DISTANCE, R.string.distance_lap, R.string.distance_lap_short, R.string.units_distance_basic, SensorValueType.DOUBLE, new DistanceFormatter(), false),
    LINE_DISTANCE_m(SensorCategory.ELEVATION_AND_NAVIGATION, R.string.line_distance, R.string.line_distance_short, R.string.units_distance_basic, SensorValueType.DOUBLE, new DistanceFormatter(), false),
    HR(SensorCategory.HEART_RATE_AND_ENERGY, R.string.heart_rate, R.string.heart_rate_short, R.string.units_heart_rate, SensorValueType.INTEGER, new IntegerFormatter(), true),
    LAP_NR(SensorCategory.TIME_AND_LAPS, R.string.lap_number, R.string.lap_number_short, R.string.units_none, SensorValueType.INTEGER, new IntegerFormatter(), false),
    LATITUDE(SensorCategory.ELEVATION_AND_NAVIGATION, R.string.position, R.string.position_short, R.string.units_none, SensorValueType.DOUBLE, new DefaultNumberFormatter(), true),
    // LATITUDE_gps           (R.string.latitude,               R.string.latitude_short,               R.string.units_none,            SensorValueType.DOUBLE,  new DefaultNumberFormatter()),
    // LATITUDE_network       (R.string.latitude,               R.string.latitude_short,               R.string.units_none,            SensorValueType.DOUBLE,  new DefaultNumberFormatter()),
    // LATITUDE_google_fused  (R.string.latitude,               R.string.latitude_short,               R.string.units_none,            SensorValueType.DOUBLE,  new DefaultNumberFormatter()),
    LONGITUDE(SensorCategory.ELEVATION_AND_NAVIGATION, R.string.position, R.string.position_short, R.string.units_none, SensorValueType.DOUBLE, new DefaultNumberFormatter(), true),
    // LONGITUDE_gps          (R.string.longitude,              R.string.longitude_short,              R.string.units_none,            SensorValueType.DOUBLE,  new DefaultNumberFormatter()),
    // LONGITUDE_network      (R.string.longitude,              R.string.longitude_short,              R.string.units_none,            SensorValueType.DOUBLE,  new DefaultNumberFormatter()),
    // LONGITUDE_google_fused (R.string.longitude,              R.string.longitude_short,              R.string.units_none,            SensorValueType.DOUBLE,  new DefaultNumberFormatter()),
    PACE_spm(SensorCategory.SPEED_PACE_AND_DISTANCE, R.string.pace, R.string.pace_short, R.string.units_pace_basic, SensorValueType.DOUBLE, new PaceFormatter(), true),
    PEDAL_POWER_BALANCE(SensorCategory.POWER_AND_PEDALING, R.string.pedal_power_balance, R.string.pedal_power_balance_short, R.string.units_percent, SensorValueType.INTEGER, new IntegerFormatter(), true),
    PEDAL_SMOOTHNESS_L(SensorCategory.POWER_AND_PEDALING, R.string.pedal_smoothness_l, R.string.pedal_smoothness_l_short, R.string.units_percent, SensorValueType.INTEGER, new IntegerFormatter(), true),
    PEDAL_SMOOTHNESS_R(SensorCategory.POWER_AND_PEDALING, R.string.pedal_smoothness_r, R.string.pedal_smoothness_r_short, R.string.units_percent, SensorValueType.INTEGER, new IntegerFormatter(), true),
    PEDAL_SMOOTHNESS(SensorCategory.POWER_AND_PEDALING, R.string.pedal_smoothness, R.string.pedal_smoothness_short, R.string.units_percent, SensorValueType.INTEGER, new IntegerFormatter(), true),
    POWER(SensorCategory.POWER_AND_PEDALING, R.string.power, R.string.power_short, R.string.units_power, SensorValueType.DOUBLE, new IntegerFormatter(), true),
    POWER_TO_WEIGHT(SensorCategory.POWER_AND_PEDALING, R.string.power_to_weight, R.string.power_to_weight_short, R.string.units_power_to_weight, SensorValueType.DOUBLE, new PowerToWeightFormatter(), true),
    NORMALIZED_POWER(SensorCategory.POWER_AND_PEDALING, R.string.normalized_power, R.string.normalized_power_short, R.string.units_power, SensorValueType.DOUBLE, new IntegerFormatter(), true),
    INTENSITY_FACTOR(SensorCategory.POWER_AND_PEDALING, R.string.intensity_factor, R.string.intensity_factor_short, R.string.units_none, SensorValueType.DOUBLE, new IntensityFactorFormatter(), true),
    WORK_KJ(SensorCategory.POWER_AND_PEDALING, R.string.work_kj, R.string.work_kj_short, R.string.units_work_kj, SensorValueType.DOUBLE, new IntegerFormatter(), false),
    TSS(SensorCategory.POWER_AND_PEDALING, R.string.tss, R.string.tss_short, R.string.units_none, SensorValueType.DOUBLE, new IntegerFormatter(), false),
    SPEED_mps(SensorCategory.SPEED_PACE_AND_DISTANCE, R.string.speed, R.string.speed_short, R.string.units_speed_basic, SensorValueType.DOUBLE, new SpeedFormatter(), true),
    // SPEED_mps_gps          (R.string.speed,                  R.string.speed_short,                  R.string.units_speed_basic,     SensorValueType.DOUBLE,  new SpeedFormatter()),
    // SPEED_mps_network      (R.string.speed,                  R.string.speed_short,                  R.string.units_speed_basic,     SensorValueType.DOUBLE,  new SpeedFormatter()),
    // SPEED_mps_google_fused (R.string.speed,                  R.string.speed_short,                  R.string.units_speed_basic,     SensorValueType.DOUBLE,  new SpeedFormatter()),
    SENSORS(SensorCategory.SYSTEM_ENVIRONMENT_AND_DIAGNOSTICS, R.string.current_sensors, R.string.current_sensors_short, R.string.units_none, SensorValueType.STRING, new DefaultStringFormatter(), false),
    SLOPE(SensorCategory.ELEVATION_AND_NAVIGATION, R.string.slope, R.string.slope_short, R.string.units_percent, SensorValueType.INTEGER, new IntegerFormatter(), true),
    STRIDES(SensorCategory.CADENCE_AND_RUNNING, R.string.strides, R.string.strides_short, R.string.units_none, SensorValueType.INTEGER, new IntegerFormatter(), true),
    TEMPERATURE(SensorCategory.SYSTEM_ENVIRONMENT_AND_DIAGNOSTICS, R.string.temperature, R.string.temperature_short, R.string.units_temperature, SensorValueType.DOUBLE, new DefaultNumberFormatter(), true),
    TEMPERATURE_MAX(SensorCategory.SYSTEM_ENVIRONMENT_AND_DIAGNOSTICS, R.string.temperature_max, R.string.temperature_max_short, R.string.units_temperature, SensorValueType.DOUBLE, new DefaultNumberFormatter(), false),
    TEMPERATURE_MIN(SensorCategory.SYSTEM_ENVIRONMENT_AND_DIAGNOSTICS, R.string.temperature_min, R.string.temperature_min_short, R.string.units_temperature, SensorValueType.DOUBLE, new DefaultNumberFormatter(), false),
    TIME_ACTIVE(SensorCategory.TIME_AND_LAPS, R.string.time_active, R.string.time_active_short, R.string.units_time_basic, SensorValueType.INTEGER, new TimeFormatter(), false),
    TIME_LAP(SensorCategory.TIME_AND_LAPS, R.string.time_lap, R.string.time_lap_short, R.string.units_time_basic, SensorValueType.INTEGER, new TimeFormatter(), false),
    TIME_OF_DAY(SensorCategory.TIME_AND_LAPS, R.string.time_of_day, R.string.time_of_day_short, R.string.units_none, SensorValueType.STRING, new DefaultStringFormatter(), false),
    TIME_TOTAL(SensorCategory.TIME_AND_LAPS, R.string.time_total, R.string.time_total_short, R.string.units_time_basic, SensorValueType.INTEGER, new TimeFormatter(), false),
    TORQUE(SensorCategory.POWER_AND_PEDALING, R.string.torque, R.string.torque_short, R.string.units_torque, SensorValueType.DOUBLE, new DefaultNumberFormatter(), true),
    TORQUE_EFFECTIVENESS_L(SensorCategory.POWER_AND_PEDALING, R.string.torque_effectiveness_l, R.string.torque_effectiveness_l_short, R.string.units_percent, SensorValueType.INTEGER, new IntegerFormatter(), true),
    TORQUE_EFFECTIVENESS_R(SensorCategory.POWER_AND_PEDALING, R.string.torque_effectiveness_r, R.string.torque_effectiveness_r_short, R.string.units_percent, SensorValueType.INTEGER, new IntegerFormatter(), true),
    VERTICAL_SPEED(SensorCategory.ELEVATION_AND_NAVIGATION, R.string.vertical_speed, R.string.vertical_speed_short, R.string.units_vertical_speed_basic, SensorValueType.INTEGER, new IntegerFormatter(), true),
    PHONE_BATTERY(SensorCategory.SYSTEM_ENVIRONMENT_AND_DIAGNOSTICS, R.string.phone_battery, R.string.phone_battery_short, R.string.units_percent, SensorValueType.INTEGER, new IntegerFormatter(), false),
    BATTERY_REMAINING_TIME(SensorCategory.SYSTEM_ENVIRONMENT_AND_DIAGNOSTICS, R.string.battery_remaining_time, R.string.battery_remaining_time_short, R.string.units_none, SensorValueType.INTEGER, new BatteryRemainingTimeFormatter(), false);

    // max bit = 118

    public static final java.util.List<SensorType> CORE_METRICS = java.util.Arrays.asList(
            POWER, HR, SPEED_mps, CADENCE, ALTITUDE, TEMPERATURE
    );

    // RR_INTERVAL     (0x0011, "RR interval",            "RR",                 "",     SensorValueType.DOUBLE,  new DefaultNumberFormatter()),
    // POWER_DIFF      (0x0022, "power differentiation",  "power diff",         "",     SensorValueType.DOUBLE,  new DefaultNumberFormatter()),

    public static final Creator<SensorType> CREATOR = new Creator<SensorType>() {
        @Override
        public SensorType createFromParcel(final Parcel source) {
            return SensorType.values()[source.readInt()];
        }

        @Override
        public SensorType[] newArray(final int size) {
            return new SensorType[size];
        }
    };
    public final boolean filteringPossible;
    private final SensorCategory category;
    private final int fullNameId;
    private final int shortNameId;
    private final int unitId;
    private final SensorValueType sensorValueType;
    private final MyFormatter myFormatter;

    SensorType(SensorCategory category, int fullNameId, int shortNameId, int unitId, SensorValueType sensorValueType, MyFormatter myFormatter, boolean filteringPossible) {
        this.category = category;
        this.fullNameId = fullNameId;
        this.shortNameId = shortNameId;
        this.unitId = unitId;
        this.sensorValueType = sensorValueType;
        this.myFormatter = myFormatter;
        this.filteringPossible = filteringPossible;
    }

    /**
     * Returns the semantic {@link SensorCategory} for this telemetry metric (ATT-3852, REQ-UI-395.1).
     */
    public SensorCategory getCategory() {
        return category;
    }

    public int getIconResId() {
        return switch (this) {
            case TIME_ACTIVE -> R.drawable.ic_time_active;
            case ACCURACY, LONGITUDE, LATITUDE -> R.drawable.ic_location;
            case ALTITUDE -> R.drawable.ic_altitude;
            case DISTANCE_m -> R.drawable.ic_distance;
            case SPEED_mps -> R.drawable.ic_speed;
            case CADENCE -> R.drawable.ic_cadence;
            case HR -> R.drawable.ic_heart_rate;
            case POWER, POWER_TO_WEIGHT, NORMALIZED_POWER, INTENSITY_FACTOR, WORK_KJ, TSS -> R.drawable.ic_power;
            case PHONE_BATTERY, BATTERY_REMAINING_TIME -> R.drawable.ic_battery_full;
            default -> R.drawable.ic_cross;
        };
    }

    @Override
    public String toString() {
        return TrainingApplication.getAppContext().getString(fullNameId);
    }

    @Deprecated // use getFullName(Context context) instead
    public int getFullNameId() {
        return fullNameId;
    }

    public String getFullName(Context context) {
        return context.getString(fullNameId);
    }

    @Deprecated // use getShortName(Context context) instead
    public int getShortNameId() {
        return shortNameId;
    }

    public String getShortName(Context context) {
        return context.getString(shortNameId);
    }

    @Deprecated // since the unit depends on the metric/imperial settings, use methods of MyHelper instead.
    public int getUnitId() {
        return unitId;
    }

    public SensorValueType getSensorValueType() {
        return sensorValueType;
    }

    public MyFormatter getMyFormatter() {
        return myFormatter;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(ordinal());
    }

}
