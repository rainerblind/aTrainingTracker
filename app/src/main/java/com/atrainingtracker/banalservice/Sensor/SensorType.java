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

package com.atrainingtracker.banalservice.Sensor;

import android.os.Parcel;
import android.os.Parcelable;

import com.atrainingtracker.R;
import com.atrainingtracker.banalservice.Sensor.formater.CadenceFormater;
import com.atrainingtracker.banalservice.Sensor.formater.DefaultNumberFormater;
import com.atrainingtracker.banalservice.Sensor.formater.DefaultStringFormater;
import com.atrainingtracker.banalservice.Sensor.formater.DistanceFormater;
import com.atrainingtracker.banalservice.Sensor.formater.IntegerFormater;
import com.atrainingtracker.banalservice.Sensor.formater.MyFormater;
import com.atrainingtracker.banalservice.Sensor.formater.PaceFormater;
import com.atrainingtracker.banalservice.Sensor.formater.SpeedFormater;
import com.atrainingtracker.banalservice.Sensor.formater.TimeFormater;
import com.atrainingtracker.trainingtracker.TrainingApplication;

public enum SensorType
        implements Parcelable {
    ACCUMULATED_SENSORS(R.string.accumulated_sensors, R.string.accumulated_sensors_short, R.string.units_none, SensorValueType.STRING, new DefaultStringFormater(), false),
    ACCURACY(R.string.accuracy, R.string.accuracy_short, R.string.units_distance_basic, SensorValueType.DOUBLE, new DefaultNumberFormater(), true),  // TODO: special formater?
    // ACCURACY_gps           (R.string.accuracy,               R.string.accuracy_short,               R.string.units_distance_basic,  SensorValueType.DOUBLE,  new DefaultNumberFormater()),  // TODO: special formater?
    // ACCURACY_network       (R.string.accuracy,               R.string.accuracy_short,               R.string.units_distance_basic,  SensorValueType.DOUBLE,  new DefaultNumberFormater()),  // TODO: special formater?
    // ACCURACY_google_fused  (R.string.accuracy,               R.string.accuracy_short,               R.string.units_distance_basic,  SensorValueType.DOUBLE,  new DefaultNumberFormater()),  // TODO: special formater?
    ALTITUDE(R.string.altitude, R.string.altitude_short, R.string.units_distance_basic, SensorValueType.DOUBLE, new DefaultNumberFormater(), true),
    // ALTITUDE_gps           (R.string.altitude,               R.string.altitude_short,               R.string.units_distance_basic,  SensorValueType.DOUBLE,  new DefaultNumberFormater()),
    // ALTITUDE_network       (R.string.altitude,               R.string.altitude_short,               R.string.units_distance_basic,  SensorValueType.DOUBLE,  new DefaultNumberFormater()),
    // ALTITUDE_google_fused  (R.string.altitude,               R.string.altitude_short,               R.string.units_distance_basic,  SensorValueType.DOUBLE,  new DefaultNumberFormater()),
    BEARING(R.string.bearing, R.string.bearing_short, R.string.units_degree, SensorValueType.DOUBLE, new DefaultNumberFormater(), true),
    // BEARING_gps            (R.string.bearing,                R.string.bearing_short,                R.string.units_degree,          SensorValueType.DOUBLE,  new DefaultNumberFormater()),
    // BEARING_network        (R.string.bearing,                R.string.bearing_short,                R.string.units_degree,          SensorValueType.DOUBLE,  new DefaultNumberFormater()),
    // BEARING_google_fused   (R.string.bearing,                R.string.bearing_short,                R.string.units_degree,          SensorValueType.DOUBLE,  new DefaultNumberFormater()),
    CADENCE(R.string.cadence, R.string.cadence_short, R.string.units_none, SensorValueType.DOUBLE, new CadenceFormater(), true),
    CALORIES(R.string.calories, R.string.calories_short, R.string.units_calories, SensorValueType.INTEGER, new IntegerFormater(), false),
    DISTANCE_m(R.string.distance, R.string.distance_short, R.string.units_distance_basic, SensorValueType.DOUBLE, new DistanceFormater(), false),
    // DISTANCE_m_gps         (R.string.distance,               R.string.distance_short,               R.string.units_distance_basic,  SensorValueType.DOUBLE,  new DistanceFormater()),
    // DISTANCE_m_network     (R.string.distance,               R.string.distance_short,               R.string.units_distance_basic,  SensorValueType.DOUBLE,  new DistanceFormater()),
    // DISTANCE_m_google_fused(R.string.distance,               R.string.distance_short,               R.string.units_distance_basic,  SensorValueType.DOUBLE,  new DistanceFormater()),
    DISTANCE_m_LAP(R.string.distance_lap, R.string.distance_lap_short, R.string.units_distance_basic, SensorValueType.DOUBLE, new DistanceFormater(), false),
    LINE_DISTANCE_m(R.string.line_distance, R.string.line_distance_short, R.string.units_distance_basic, SensorValueType.DOUBLE, new DistanceFormater(), false),
    HR(R.string.heart_rate, R.string.heart_rate_short, R.string.units_heart_rate, SensorValueType.INTEGER, new IntegerFormater(), true),
    LAP_NR(R.string.lap_number, R.string.lap_number_short, R.string.units_none, SensorValueType.INTEGER, new IntegerFormater(), false),
    LATITUDE(R.string.latitude, R.string.latitude_short, R.string.units_none, SensorValueType.DOUBLE, new DefaultNumberFormater(), true),
    // LATITUDE_gps           (R.string.latitude,               R.string.latitude_short,               R.string.units_none,            SensorValueType.DOUBLE,  new DefaultNumberFormater()),
    // LATITUDE_network       (R.string.latitude,               R.string.latitude_short,               R.string.units_none,            SensorValueType.DOUBLE,  new DefaultNumberFormater()),
    // LATITUDE_google_fused  (R.string.latitude,               R.string.latitude_short,               R.string.units_none,            SensorValueType.DOUBLE,  new DefaultNumberFormater()),
    LONGITUDE(R.string.longitude, R.string.longitude_short, R.string.units_none, SensorValueType.DOUBLE, new DefaultNumberFormater(), true),
    // LONGITUDE_gps          (R.string.longitude,              R.string.longitude_short,              R.string.units_none,            SensorValueType.DOUBLE,  new DefaultNumberFormater()),
    // LONGITUDE_network      (R.string.longitude,              R.string.longitude_short,              R.string.units_none,            SensorValueType.DOUBLE,  new DefaultNumberFormater()),
    // LONGITUDE_google_fused (R.string.longitude,              R.string.longitude_short,              R.string.units_none,            SensorValueType.DOUBLE,  new DefaultNumberFormater()),
    PACE_spm(R.string.pace, R.string.pace_short, R.string.units_pace_basic, SensorValueType.DOUBLE, new PaceFormater(), true),
    PEDAL_POWER_BALANCE(R.string.pedal_power_balance, R.string.pedal_power_balance_short, R.string.units_percent, SensorValueType.INTEGER, new IntegerFormater(), true),
    PEDAL_SMOOTHNESS_L(R.string.pedal_smoothness_l, R.string.pedal_smoothness_l_short, R.string.units_percent, SensorValueType.INTEGER, new IntegerFormater(), true),
    PEDAL_SMOOTHNESS_R(R.string.pedal_smoothness_r, R.string.pedal_smoothness_r_short, R.string.units_percent, SensorValueType.INTEGER, new IntegerFormater(), true),
    PEDAL_SMOOTHNESS(R.string.pedal_smoothness, R.string.pedal_smoothness_short, R.string.units_percent, SensorValueType.INTEGER, new IntegerFormater(), true),
    POWER(R.string.power, R.string.power_short, R.string.units_power, SensorValueType.DOUBLE, new DefaultNumberFormater(), true),
    SPEED_mps(R.string.speed, R.string.speed_short, R.string.units_speed_basic, SensorValueType.DOUBLE, new SpeedFormater(), true),
    // SPEED_mps_gps          (R.string.speed,                  R.string.speed_short,                  R.string.units_speed_basic,     SensorValueType.DOUBLE,  new SpeedFormater()),
    // SPEED_mps_network      (R.string.speed,                  R.string.speed_short,                  R.string.units_speed_basic,     SensorValueType.DOUBLE,  new SpeedFormater()),
    // SPEED_mps_google_fused (R.string.speed,                  R.string.speed_short,                  R.string.units_speed_basic,     SensorValueType.DOUBLE,  new SpeedFormater()),
    SENSORS(R.string.current_sensors, R.string.current_sensors_short, R.string.units_none, SensorValueType.STRING, new DefaultStringFormater(), false),
    STRIDES(R.string.strides, R.string.strides_short, R.string.units_none, SensorValueType.INTEGER, new IntegerFormater(), true),
    TEMPERATURE(R.string.temperature, R.string.temperature_short, R.string.units_temperature, SensorValueType.DOUBLE, new DefaultNumberFormater(), true),
    TEMPERATURE_MAX(R.string.temperature_max, R.string.temperature_max_short, R.string.units_temperature, SensorValueType.DOUBLE, new DefaultNumberFormater(), false),
    TEMPERATURE_MIN(R.string.temperature_min, R.string.temperature_min_short, R.string.units_temperature, SensorValueType.DOUBLE, new DefaultNumberFormater(), false),
    TIME_ACTIVE(R.string.time_active, R.string.time_active_short, R.string.units_time_basic, SensorValueType.INTEGER, new TimeFormater(), false),
    TIME_LAP(R.string.time_lap, R.string.time_lap_short, R.string.units_time_basic, SensorValueType.INTEGER, new TimeFormater(), false),
    TIME_OF_DAY(R.string.time_of_day, R.string.time_of_day_short, R.string.units_none, SensorValueType.STRING, new DefaultStringFormater(), false),
    TIME_TOTAL(R.string.time_total, R.string.time_total_short, R.string.units_time_basic, SensorValueType.INTEGER, new TimeFormater(), false),
    TORQUE(R.string.torque, R.string.torque_short, R.string.units_torque, SensorValueType.DOUBLE, new DefaultNumberFormater(), true),
    TORQUE_EFFECTIVENESS_L(R.string.torque_effectiveness_l, R.string.torque_effectiveness_l_short, R.string.units_percent, SensorValueType.INTEGER, new IntegerFormater(), true),
    TORQUE_EFFECTIVENESS_R(R.string.torque_effectiveness_r, R.string.torque_effectiveness_r_short, R.string.units_percent, SensorValueType.INTEGER, new IntegerFormater(), true);

    // max bit = 118

    // RR_INTERVAL     (0x0011, "RR intervall",           "RR",                 "",     SensorValueType.DOUBLE,  new DefaultNumberFormater()),
    // POWER_DIFF      (0x0022, "power differentiation",  "power diff",         "",     SensorValueType.DOUBLE,  new DefaultNumberFormater()),

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
    private final int fullNameId;
    private final int shortNameId;
    private final int unitId;
    private final SensorValueType sensorValueType;
    private final MyFormater myFormater;

    SensorType(int fullNameId, int shortNameId, int unitId, SensorValueType sensorValueType, MyFormater myFormater, boolean filteringPossible) {
        this.fullNameId = fullNameId;
        this.shortNameId = shortNameId;
        this.unitId = unitId;
        this.sensorValueType = sensorValueType;
        this.myFormater = myFormater;
        this.filteringPossible = filteringPossible;
    }

    @Override
    public String toString() {
        return TrainingApplication.getAppContext().getString(fullNameId);
    }

    public int getFullNameId() {
        return fullNameId;
    }

    public int getShortNameId() {
        return shortNameId;
    }

    public int getUnitId() {
        return unitId;
    }

    public SensorValueType getSensorValueType() {
        return sensorValueType;
    }

    public MyFormater getMyFormater() {
        return myFormater;
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
