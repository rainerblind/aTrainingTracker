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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.atrainingtracker.R;
import com.atrainingtracker.banalservice.sensor.formater.AltitudeFormatter;
import com.atrainingtracker.banalservice.sensor.formater.BatteryRemainingTimeFormatter;
import com.atrainingtracker.banalservice.sensor.formater.CadenceFormatter;
import com.atrainingtracker.banalservice.sensor.formater.DefaultNumberFormatter;
import com.atrainingtracker.banalservice.sensor.formater.DefaultStringFormatter;
import com.atrainingtracker.banalservice.sensor.formater.DfaA1Formatter;
import com.atrainingtracker.banalservice.sensor.formater.DistanceFormatter;
import com.atrainingtracker.banalservice.sensor.formater.IntegerFormatter;
import com.atrainingtracker.banalservice.sensor.formater.IntensityFactorFormatter;
import com.atrainingtracker.banalservice.sensor.formater.MyFormatter;
import com.atrainingtracker.banalservice.sensor.formater.PaceFormatter;
import com.atrainingtracker.banalservice.sensor.formater.PowerToWeightFormatter;
import com.atrainingtracker.banalservice.sensor.formater.SignedIntegerFormatter;
import com.atrainingtracker.banalservice.sensor.formater.SpeedFormatter;
import com.atrainingtracker.banalservice.sensor.formater.TimeFormatter;
import com.atrainingtracker.trainingtracker.TrainingApplication;


/**
 * Canonical telemetry sensor identifiers and unit metadata ({@code REQ-TRK-070}, {@code REQ-TRK-083}).
 *
 * <p>Enum {@link #ordinal()} order is serialized via {@link Parcelable} and {@link #name()} defines
 * SQLite column names in {@code WorkoutSamples.db}; both must remain invariant.
 */
public enum SensorType
        implements Parcelable {
    /**
     * Comma-separated list of accumulated {@link SensorType#name()} tokens.
     * Internal unit: {@code dimensionless} ({@code STRING}); sentinel: {@code null} -> {@code "--"};
     * display: {@link DefaultStringFormatter}; export: CSV raw text.
     */
    ACCUMULATED_SENSORS(SensorCategory.SYSTEM_ENVIRONMENT_AND_DIAGNOSTICS, R.string.accumulated_sensors, R.string.accumulated_sensors_short, R.string.units_none, SensorValueType.STRING, new DefaultStringFormatter(), false, "dimensionless"),
    /**
     * Horizontal location accuracy radius (68% confidence).
     * Internal unit: {@code m} ({@code DOUBLE}, {@code >= 0}); sentinel: {@code null} -> {@code "--"};
     * display: {@link DefaultNumberFormatter} ({@code m}); export: CSV raw {@code m}.
     */
    ACCURACY(SensorCategory.SYSTEM_ENVIRONMENT_AND_DIAGNOSTICS, R.string.accuracy, R.string.accuracy_short, R.string.units_distance_basic, SensorValueType.DOUBLE, new DefaultNumberFormatter(), true, "m"),  // TODO: special formatter?
    // ACCURACY_gps           (R.string.accuracy,               R.string.accuracy_short,               R.string.units_distance_basic,  SensorValueType.DOUBLE,  new DefaultNumberFormatter()),  // TODO: special formatter?
    // ACCURACY_network       (R.string.accuracy,               R.string.accuracy_short,               R.string.units_distance_basic,  SensorValueType.DOUBLE,  new DefaultNumberFormatter()),  // TODO: special formatter?
    // ACCURACY_google_fused  (R.string.accuracy,               R.string.accuracy_short,               R.string.units_distance_basic,  SensorValueType.DOUBLE,  new DefaultNumberFormatter()),  // TODO: special formatter?
    /**
     * Elevation above mean sea level / WGS-84 reference.
     * Internal unit: {@code m} ({@code DOUBLE}, export bounds {@code [-500.0, 9000.0]}); sentinel: {@code null} -> {@code "--"};
     * display: {@link AltitudeFormatter} ({@code m} Metric / {@code ft} Imperial); export: FIT/TCX/GPX/CSV/GC in {@code m}.
     */
    ALTITUDE(SensorCategory.ELEVATION, R.string.altitude, R.string.altitude_short, R.string.units_distance_basic, SensorValueType.DOUBLE, new AltitudeFormatter(), true, "m"),
    // ALTITUDE_gps           (R.string.altitude,               R.string.altitude_short,               R.string.units_distance_basic,  SensorValueType.DOUBLE,  new DefaultNumberFormatter()),
    // ALTITUDE_network       (R.string.altitude,               R.string.altitude_short,               R.string.units_distance_basic,  SensorValueType.DOUBLE,  new DefaultNumberFormatter()),
    // ALTITUDE_google_fused  (R.string.altitude,               R.string.altitude_short,               R.string.units_distance_basic,  SensorValueType.DOUBLE,  new DefaultNumberFormatter()),
    /**
     * Cumulative elevation gain.
     * Internal unit: {@code m} ({@code DOUBLE}, {@code >= 0}); sentinel: {@code null} -> {@code "--"};
     * display: {@link AltitudeFormatter} ({@code m}/{@code ft}); export: FIT session {@code totalAscent} ({@code m}), CSV raw {@code m}.
     */
    ASCENT(SensorCategory.ELEVATION, R.string.ascent, R.string.ascent_short, R.string.units_distance_basic, SensorValueType.DOUBLE, new AltitudeFormatter(), false, "m"),
    /**
     * Compass direction of travel clockwise from true north.
     * Internal unit: {@code deg} ({@code DOUBLE}, {@code [0.0, 360.0)}); sentinel: {@code null} -> {@code "--"};
     * display: {@link DefaultNumberFormatter} ({@code °}); export: CSV raw {@code deg}.
     */
    BEARING(SensorCategory.ELEVATION, R.string.bearing, R.string.bearing_short, R.string.units_degree, SensorValueType.DOUBLE, new DefaultNumberFormatter(), true, "deg"),
    // BEARING_gps            (R.string.bearing,                R.string.bearing_short,                R.string.units_degree,          SensorValueType.DOUBLE,  new DefaultNumberFormatter()),
    // BEARING_network        (R.string.bearing,                R.string.bearing_short,                R.string.units_degree,          SensorValueType.DOUBLE,  new DefaultNumberFormatter()),
    // BEARING_google_fused   (R.string.bearing,                R.string.bearing_short,                R.string.units_degree,          SensorValueType.DOUBLE,  new DefaultNumberFormatter()),
    /**
     * Crank revolutions per minute or running strides per minute.
     * Internal unit: {@code 1/min} ({@code DOUBLE}, {@code [0, 255]}); sentinel: {@code null} -> {@code "--"};
     * display: {@link IntegerFormatter} ({@code /min}); export: FIT/TCX/CSV/GC in {@code 1/min}.
     */
    CADENCE(SensorCategory.SPEED_PACE_AND_DISTANCE, R.string.cadence, R.string.cadence_short, R.string.units_rpm, SensorValueType.DOUBLE, new IntegerFormatter(), true, "1/min"),
    /**
     * Cumulative energy expenditure.
     * Internal unit: {@code kcal} ({@code INTEGER}, {@code >= 0}); sentinel: {@code null} -> {@code "--"};
     * display: {@link IntegerFormatter} ({@code kcal}); export: FIT/TCX session/lap {@code kcal}, CSV raw {@code kcal}.
     */
    CALORIES(SensorCategory.HEART_RATE_AND_ENERGY, R.string.calories, R.string.calories_short, R.string.units_calories, SensorValueType.INTEGER, new IntegerFormatter(), false, "kcal"),
    /**
     * Cumulative elevation loss.
     * Internal unit: {@code m} ({@code DOUBLE}, {@code >= 0}); sentinel: {@code null} -> {@code "--"};
     * display: {@link AltitudeFormatter} ({@code m}/{@code ft}); export: FIT session {@code totalDescent} ({@code m}), CSV raw {@code m}.
     */
    DESCENT(SensorCategory.ELEVATION, R.string.descent, R.string.descent_short, R.string.units_distance_basic, SensorValueType.DOUBLE, new AltitudeFormatter(), false, "m"),
    /**
     * Cumulative workout distance.
     * Internal unit: {@code m} ({@code DOUBLE}, {@code >= 0}); sentinel: {@code null} -> {@code "--"};
     * display: {@link DistanceFormatter} ({@code m}/{@code km} Metric, {@code ft}/{@code mile} Imperial);
     * export: FIT/TCX {@code m}, CSV raw {@code m}, GC {@code km} ({@code / 1000.0}).
     */
    DISTANCE_m(SensorCategory.SPEED_PACE_AND_DISTANCE, R.string.distance, R.string.distance_short, R.string.units_distance_basic, SensorValueType.DOUBLE, new DistanceFormatter(), false, "m"),
    // DISTANCE_m_gps         (R.string.distance,               R.string.distance_short,               R.string.units_distance_basic,  SensorValueType.DOUBLE,  new DistanceFormatter()),
    // DISTANCE_m_network     (R.string.distance,               R.string.distance_short,               R.string.units_distance_basic,  SensorValueType.DOUBLE,  new DistanceFormatter()),
    // DISTANCE_m_google_fused(R.string.distance,               R.string.distance_short,               R.string.units_distance_basic,  SensorValueType.DOUBLE,  new DistanceFormatter()),
    /**
     * Cumulative distance within the current lap.
     * Internal unit: {@code m} ({@code DOUBLE}, {@code >= 0}); sentinel: {@code null} -> {@code "--"};
     * display: {@link DistanceFormatter} ({@code km}/{@code mile}); export: CSV raw {@code m}.
     */
    DISTANCE_m_LAP(SensorCategory.TIME_AND_LAPS, SensorSubCategory.LAP_METRICS, R.string.distance_lap, R.string.distance_lap_short, R.string.units_distance_basic, SensorValueType.DOUBLE, new DistanceFormatter(), false, "m"),
    /**
     * Straight-line geodesic distance from workout origin.
     * Internal unit: {@code m} ({@code DOUBLE}, {@code >= 0}); sentinel: {@code null} -> {@code "--"};
     * display: {@link DistanceFormatter} ({@code km}/{@code mile}); export: CSV raw {@code m}.
     */
    LINE_DISTANCE_m(SensorCategory.ELEVATION, R.string.line_distance, R.string.line_distance_short, R.string.units_distance_basic, SensorValueType.DOUBLE, new DistanceFormatter(), false, "m"),
    /**
     * Instantaneous heart rate.
     * Internal unit: {@code bpm} ({@code INTEGER}, FIT valid {@code [1, 299]}); sentinel: {@code null} -> {@code "--"};
     * display: {@link IntegerFormatter} ({@code bpm}); export: FIT/TCX/CSV/GC in {@code bpm}.
     */
    HR(SensorCategory.HEART_RATE_AND_ENERGY, R.string.heart_rate, R.string.heart_rate_short, R.string.units_heart_rate, SensorValueType.INTEGER, new IntegerFormatter(), true, "bpm"),
    /**
     * 1-based active lap index ({@code BANALService.INIT_LAP_NR = 1}).
     * Internal unit: {@code dimensionless} ({@code INTEGER}, {@code >= 1}); sentinel: {@code null} -> {@code "--"};
     * display: {@link IntegerFormatter}; export: FIT/TCX/GPX lap grouping boundary, CSV raw integer.
     */
    LAP_NR(SensorCategory.TIME_AND_LAPS, SensorSubCategory.LAP_METRICS, R.string.lap_number, R.string.lap_number_short, R.string.units_none, SensorValueType.INTEGER, new IntegerFormatter(), false, "dimensionless"),
    /**
     * WGS-84 geodetic latitude.
     * Internal unit: {@code deg} ({@code DOUBLE}, {@code [-90.0, 90.0]}); sentinel: {@code null} -> {@code "--"}, {@code (0.0, 0.0)} suppressed in FIT;
     * display: {@link DefaultNumberFormatter}; export: FIT {@code semicircles}, TCX/GPX/CSV/GC in {@code deg}.
     */
    LATITUDE(SensorCategory.ELEVATION, R.string.position, R.string.position_short, R.string.units_none, SensorValueType.DOUBLE, new DefaultNumberFormatter(), true, "deg"),
    // LATITUDE_gps           (R.string.latitude,               R.string.latitude_short,               R.string.units_none,            SensorValueType.DOUBLE,  new DefaultNumberFormatter()),
    // LATITUDE_network       (R.string.latitude,               R.string.latitude_short,               R.string.units_none,            SensorValueType.DOUBLE,  new DefaultNumberFormatter()),
    // LATITUDE_google_fused  (R.string.latitude,               R.string.latitude_short,               R.string.units_none,            SensorValueType.DOUBLE,  new DefaultNumberFormatter()),
    /**
     * WGS-84 geodetic longitude.
     * Internal unit: {@code deg} ({@code DOUBLE}, {@code [-180.0, 180.0]}); sentinel: {@code null} -> {@code "--"}, {@code (0.0, 0.0)} suppressed in FIT;
     * display: {@link DefaultNumberFormatter}; export: FIT {@code semicircles}, TCX/GPX/CSV/GC in {@code deg}.
     */
    LONGITUDE(SensorCategory.ELEVATION, R.string.position, R.string.position_short, R.string.units_none, SensorValueType.DOUBLE, new DefaultNumberFormatter(), true, "deg"),
    // LONGITUDE_gps          (R.string.longitude,              R.string.longitude_short,              R.string.units_none,            SensorValueType.DOUBLE,  new DefaultNumberFormatter()),
    // LONGITUDE_network      (R.string.longitude,              R.string.longitude_short,              R.string.units_none,            SensorValueType.DOUBLE,  new DefaultNumberFormatter()),
    // LONGITUDE_google_fused (R.string.longitude,              R.string.longitude_short,              R.string.units_none,            SensorValueType.DOUBLE,  new DefaultNumberFormatter()),
    /**
     * Inverse speed (seconds per meter).
     * Internal unit: {@code s/m} ({@code DOUBLE}); sentinels: {@code > BANALService.MAX_PACE} (10) -> {@code "~~"}, {@code null} -> {@code "--"};
     * display: {@link PaceFormatter} ({@code min/km} Metric, {@code min/mile} Imperial); export: CSV raw {@code s/m}.
     */
    PACE_spm(SensorCategory.SPEED_PACE_AND_DISTANCE, R.string.pace, R.string.pace_short, R.string.units_pace_basic, SensorValueType.DOUBLE, new PaceFormatter(), true, "s/m"),
    /**
     * Right-pedal power balance percentage.
     * Internal unit: {@code %} ({@code INTEGER}, {@code [0, 100]}); sentinel: {@code null} -> {@code "--"};
     * display: {@link IntegerFormatter} ({@code %}); export: FIT bit-packed {@code %} byte, CSV/GC in {@code %}.
     */
    PEDAL_POWER_BALANCE(SensorCategory.POWER_AND_PEDALING, SensorSubCategory.PEDALING_DYNAMICS, R.string.pedal_power_balance, R.string.pedal_power_balance_short, R.string.units_percent, SensorValueType.INTEGER, new IntegerFormatter(), true, "%"),
    /**
     * Left pedal smoothness percentage.
     * Internal unit: {@code %} ({@code INTEGER}, {@code [0, 100]}); sentinel: {@code null} -> {@code "--"};
     * display: {@link IntegerFormatter} ({@code %}); export: FIT/CSV/GC in {@code %}.
     */
    PEDAL_SMOOTHNESS_L(SensorCategory.POWER_AND_PEDALING, SensorSubCategory.PEDALING_DYNAMICS, R.string.pedal_smoothness_l, R.string.pedal_smoothness_l_short, R.string.units_percent, SensorValueType.INTEGER, new IntegerFormatter(), true, "%"),
    /**
     * Right pedal smoothness percentage.
     * Internal unit: {@code %} ({@code INTEGER}, {@code [0, 100]}); sentinel: {@code null} -> {@code "--"};
     * display: {@link IntegerFormatter} ({@code %}); export: FIT/CSV/GC in {@code %}.
     */
    PEDAL_SMOOTHNESS_R(SensorCategory.POWER_AND_PEDALING, SensorSubCategory.PEDALING_DYNAMICS, R.string.pedal_smoothness_r, R.string.pedal_smoothness_r_short, R.string.units_percent, SensorValueType.INTEGER, new IntegerFormatter(), true, "%"),
    /**
     * Combined pedal smoothness percentage.
     * Internal unit: {@code %} ({@code INTEGER}, {@code [0, 100]}); sentinel: {@code null} -> {@code "--"};
     * display: {@link IntegerFormatter} ({@code %}); export: FIT/CSV in {@code %}.
     */
    PEDAL_SMOOTHNESS(SensorCategory.POWER_AND_PEDALING, SensorSubCategory.PEDALING_DYNAMICS, R.string.pedal_smoothness, R.string.pedal_smoothness_short, R.string.units_percent, SensorValueType.INTEGER, new IntegerFormatter(), true, "%"),
    /**
     * Instantaneous mechanical cycling/running power.
     * Internal unit: {@code W} ({@code DOUBLE}, {@code >= 0}); sentinel: {@code null} -> {@code "--"};
     * display: {@link IntegerFormatter} ({@code W}); export: FIT/TCX/CSV/GC in {@code W}.
     */
    POWER(SensorCategory.POWER_AND_PEDALING, R.string.power, R.string.power_short, R.string.units_power, SensorValueType.DOUBLE, new IntegerFormatter(), true, "W"),
    /**
     * Specific power relative to athlete body mass.
     * Internal unit: {@code W/kg} ({@code DOUBLE}, {@code >= 0}); sentinels: {@code < 0}/{@code NaN}/{@code Infinite}/{@code null} -> {@code "--"};
     * display: {@link PowerToWeightFormatter} ({@code W/kg}); export: CSV raw {@code W/kg}.
     */
    POWER_TO_WEIGHT(SensorCategory.POWER_AND_PEDALING, SensorSubCategory.POWER_LOAD, R.string.power_to_weight, R.string.power_to_weight_short, R.string.units_power_to_weight, SensorValueType.DOUBLE, new PowerToWeightFormatter(), true, "W/kg"),
    /**
     * Coggan 30-second rolling 4th-root Normalized Power.
     * Internal unit: {@code W} ({@code DOUBLE}, {@code >= 0}); sentinel: {@code null} -> {@code "--"};
     * display: {@link IntegerFormatter} ({@code W}); export: FIT session {@code normalizedPower} ({@code W}), CSV raw {@code W}.
     */
    NORMALIZED_POWER(SensorCategory.POWER_AND_PEDALING, SensorSubCategory.POWER_LOAD, R.string.normalized_power, R.string.normalized_power_short, R.string.units_power, SensorValueType.DOUBLE, new IntegerFormatter(), true, "W"),
    /**
     * Intensity Factor ({@code NP / FTP}).
     * Internal unit: {@code dimensionless} ({@code DOUBLE}, {@code >= 0}); sentinels: {@code < 0}/{@code NaN}/{@code Infinite}/{@code null} -> {@code "--"};
     * display: {@link IntensityFactorFormatter} ({@code 0.00}); export: FIT session {@code intensityFactor}, CSV raw ratio.
     */
    INTENSITY_FACTOR(SensorCategory.POWER_AND_PEDALING, SensorSubCategory.POWER_LOAD, R.string.intensity_factor, R.string.intensity_factor_short, R.string.units_none, SensorValueType.DOUBLE, new IntensityFactorFormatter(), true, "dimensionless"),
    /**
     * Cumulative mechanical work.
     * Internal unit: {@code kJ} ({@code DOUBLE}, {@code >= 0}); sentinel: {@code null} -> {@code "--"};
     * display: {@link IntegerFormatter} ({@code kJ}); export: FIT session {@code totalWork} in {@code J} ({@code * 1000.0}), CSV raw {@code kJ}.
     */
    WORK_KJ(SensorCategory.POWER_AND_PEDALING, SensorSubCategory.POWER_LOAD, R.string.work_kj, R.string.work_kj_short, R.string.units_work_kj, SensorValueType.DOUBLE, new IntegerFormatter(), false, "kJ"),
    /**
     * Coggan Training Stress Score.
     * Internal unit: {@code dimensionless} ({@code DOUBLE}, {@code >= 0}); sentinel: {@code null} -> {@code "--"};
     * display: {@link IntegerFormatter}; export: FIT session {@code trainingStressScore}, CSV raw score.
     */
    TSS(SensorCategory.POWER_AND_PEDALING, SensorSubCategory.POWER_LOAD, R.string.tss, R.string.tss_short, R.string.units_none, SensorValueType.DOUBLE, new IntegerFormatter(), false, "dimensionless"),
    /**
     * Skiba anaerobic work capacity balance ({@code W'bal}).
     * Internal unit: {@code kJ} ({@code DOUBLE}, emitted by {@code CyclingPowerMetricsDevice});
     * sentinels: {@code < 0}/{@code NaN}/{@code Infinite}/{@code null} -> {@code "--"} in legacy {@link PowerToWeightFormatter}
     * (note: {@code unitId} is {@code units_work_kj} = {@code kJ}, while {@code PowerToWeightFormatter.format_with_units} appends {@code "W/kg"});
     * export: CSV raw {@code kJ}.
     */
    W_PRIME_BAL(SensorCategory.POWER_AND_PEDALING, R.string.w_prime_bal, R.string.w_prime_bal_short, R.string.units_work_kj, SensorValueType.DOUBLE, new PowerToWeightFormatter(), false, "kJ"),
    /**
     * Rolling 5-second mean maximal power.
     * Internal unit: {@code W} ({@code DOUBLE}, {@code >= 0}); sentinel: {@code null} -> {@code "--"};
     * display: {@link IntegerFormatter} ({@code W}); export: CSV raw {@code W}.
     */
    POWER_5S(SensorCategory.POWER_AND_PEDALING, SensorSubCategory.POWER_ROLLING, R.string.power_5s, R.string.power_5s_short, R.string.units_power, SensorValueType.DOUBLE, new IntegerFormatter(), true, "W"),
    /**
     * Rolling 1-minute mean maximal power.
     * Internal unit: {@code W} ({@code DOUBLE}, {@code >= 0}); sentinel: {@code null} -> {@code "--"};
     * display: {@link IntegerFormatter} ({@code W}); export: CSV raw {@code W}.
     */
    POWER_1M(SensorCategory.POWER_AND_PEDALING, SensorSubCategory.POWER_ROLLING, R.string.power_1m, R.string.power_1m_short, R.string.units_power, SensorValueType.DOUBLE, new IntegerFormatter(), true, "W"),
    /**
     * Rolling 5-minute mean maximal power.
     * Internal unit: {@code W} ({@code DOUBLE}, {@code >= 0}); sentinel: {@code null} -> {@code "--"};
     * display: {@link IntegerFormatter} ({@code W}); export: CSV raw {@code W}.
     */
    POWER_5M(SensorCategory.POWER_AND_PEDALING, SensorSubCategory.POWER_ROLLING, R.string.power_5m, R.string.power_5m_short, R.string.units_power, SensorValueType.DOUBLE, new IntegerFormatter(), true, "W"),
    /**
     * Rolling 20-minute mean maximal power.
     * Internal unit: {@code W} ({@code DOUBLE}, {@code >= 0}); sentinel: {@code null} -> {@code "--"};
     * display: {@link IntegerFormatter} ({@code W}); export: CSV raw {@code W}.
     */
    POWER_20M(SensorCategory.POWER_AND_PEDALING, SensorSubCategory.POWER_ROLLING, R.string.power_20m, R.string.power_20m_short, R.string.units_power, SensorValueType.DOUBLE, new IntegerFormatter(), true, "W"),
    /**
     * Session peak 5-second power.
     * Internal unit: {@code W} ({@code DOUBLE}, {@code >= 0}); sentinel: {@code null} -> {@code "--"};
     * display: {@link IntegerFormatter} ({@code W}); export: CSV raw {@code W}.
     */
    POWER_PEAK_5S(SensorCategory.POWER_AND_PEDALING, SensorSubCategory.POWER_PEAK, R.string.power_peak_5s, R.string.power_peak_5s_short, R.string.units_power, SensorValueType.DOUBLE, new IntegerFormatter(), false, "W"),
    /**
     * Session peak 1-minute power.
     * Internal unit: {@code W} ({@code DOUBLE}, {@code >= 0}); sentinel: {@code null} -> {@code "--"};
     * display: {@link IntegerFormatter} ({@code W}); export: CSV raw {@code W}.
     */
    POWER_PEAK_1M(SensorCategory.POWER_AND_PEDALING, SensorSubCategory.POWER_PEAK, R.string.power_peak_1m, R.string.power_peak_1m_short, R.string.units_power, SensorValueType.DOUBLE, new IntegerFormatter(), false, "W"),
    /**
     * Session peak 5-minute power.
     * Internal unit: {@code W} ({@code DOUBLE}, {@code >= 0}); sentinel: {@code null} -> {@code "--"};
     * display: {@link IntegerFormatter} ({@code W}); export: CSV raw {@code W}.
     */
    POWER_PEAK_5M(SensorCategory.POWER_AND_PEDALING, SensorSubCategory.POWER_PEAK, R.string.power_peak_5m, R.string.power_peak_5m_short, R.string.units_power, SensorValueType.DOUBLE, new IntegerFormatter(), false, "W"),
    /**
     * Session peak 20-minute power.
     * Internal unit: {@code W} ({@code DOUBLE}, {@code >= 0}); sentinel: {@code null} -> {@code "--"};
     * display: {@link IntegerFormatter} ({@code W}); export: CSV raw {@code W}.
     */
    POWER_PEAK_20M(SensorCategory.POWER_AND_PEDALING, SensorSubCategory.POWER_PEAK, R.string.power_peak_20m, R.string.power_peak_20m_short, R.string.units_power, SensorValueType.DOUBLE, new IntegerFormatter(), false, "W"),
    /**
     * Horizontal speed.
     * Internal unit: {@code m/s} ({@code DOUBLE}, {@code >= 0}); sentinel: {@code null} -> {@code "--"};
     * display: {@link SpeedFormatter} ({@code km/h} Metric via {@code * 3.6}, {@code mile/h} Imperial via {@code * 2.23693629});
     * export: FIT/TCX {@code m/s}, CSV raw {@code m/s}, GC {@code km/h} ({@code * 3.6}).
     */
    SPEED_mps(SensorCategory.SPEED_PACE_AND_DISTANCE, R.string.speed, R.string.speed_short, R.string.units_speed_basic, SensorValueType.DOUBLE, new SpeedFormatter(), true, "m/s"),
    // SPEED_mps_gps          (R.string.speed,                  R.string.speed_short,                  R.string.units_speed_basic,     SensorValueType.DOUBLE,  new SpeedFormatter()),
    // SPEED_mps_network      (R.string.speed,                  R.string.speed_short,                  R.string.units_speed_basic,     SensorValueType.DOUBLE,  new SpeedFormatter()),
    // SPEED_mps_google_fused (R.string.speed,                  R.string.speed_short,                  R.string.units_speed_basic,     SensorValueType.DOUBLE,  new SpeedFormatter()),
    /**
     * 11-character active sensor bitmask string (e.g. {@code "-DS----AG--"}).
     * Internal unit: {@code dimensionless} ({@code STRING}); sentinel: {@code null} -> {@code "--"};
     * display: {@link DefaultStringFormatter}; export: CSV raw text.
     */
    SENSORS(SensorCategory.SYSTEM_ENVIRONMENT_AND_DIAGNOSTICS, R.string.current_sensors, R.string.current_sensors_short, R.string.units_none, SensorValueType.STRING, new DefaultStringFormatter(), false, "dimensionless"),
    /**
     * Signed terrain gradient percentage.
     * Internal unit: {@code %} ({@code INTEGER}); sentinel: {@code null} -> {@code "--"};
     * display: {@link IntegerFormatter} ({@code %}); export: CSV raw {@code %}.
     */
    SLOPE(SensorCategory.ELEVATION, R.string.slope, R.string.slope_short, R.string.units_percent, SensorValueType.INTEGER, new IntegerFormatter(), true, "%"),
    /**
     * Cumulative running/walking stride count.
     * Internal unit: {@code dimensionless} ({@code INTEGER}, {@code >= 0}); sentinel: {@code null} -> {@code "--"};
     * display: {@link IntegerFormatter}; export: CSV raw integer.
     */
    STRIDES(SensorCategory.SPEED_PACE_AND_DISTANCE, R.string.strides, R.string.strides_short, R.string.units_none, SensorValueType.INTEGER, new IntegerFormatter(), true, "dimensionless"),
    /**
     * Ambient air temperature.
     * Internal unit: {@code degC} ({@code DOUBLE}); sentinel: {@code null} -> {@code "--"};
     * display: {@link DefaultNumberFormatter} ({@code °C}); export: FIT {@code RecordMesg.temperature} ({@code degC}), CSV raw {@code degC}.
     */
    TEMPERATURE(SensorCategory.SYSTEM_ENVIRONMENT_AND_DIAGNOSTICS, SensorSubCategory.AMBIENT_TEMPERATURE, R.string.temperature, R.string.temperature_short, R.string.units_temperature, SensorValueType.DOUBLE, new DefaultNumberFormatter(), true, "degC"),
    /**
     * Maximum ambient temperature observed.
     * Internal unit: {@code degC} ({@code DOUBLE}); sentinel: {@code null} -> {@code "--"};
     * display: {@link DefaultNumberFormatter} ({@code °C}); export: CSV raw {@code degC}.
     */
    TEMPERATURE_MAX(SensorCategory.SYSTEM_ENVIRONMENT_AND_DIAGNOSTICS, SensorSubCategory.AMBIENT_TEMPERATURE, R.string.temperature_max, R.string.temperature_max_short, R.string.units_temperature, SensorValueType.DOUBLE, new DefaultNumberFormatter(), false, "degC"),
    /**
     * Minimum ambient temperature observed.
     * Internal unit: {@code degC} ({@code DOUBLE}); sentinel: {@code null} -> {@code "--"};
     * display: {@link DefaultNumberFormatter} ({@code °C}); export: CSV raw {@code degC}.
     */
    TEMPERATURE_MIN(SensorCategory.SYSTEM_ENVIRONMENT_AND_DIAGNOSTICS, SensorSubCategory.AMBIENT_TEMPERATURE, R.string.temperature_min, R.string.temperature_min_short, R.string.units_temperature, SensorValueType.DOUBLE, new DefaultNumberFormatter(), false, "degC"),
    /**
     * Cumulative active (unpaused) recording duration.
     * Internal unit: {@code s} ({@code INTEGER}, {@code >= 0}); sentinel: {@code null} -> {@code "--:--:--"};
     * display: {@link TimeFormatter} ({@code H:MM:SS}); export: FIT {@code totalTimerTime} ({@code s}), CSV raw {@code s}.
     */
    TIME_ACTIVE(SensorCategory.TIME_AND_LAPS, R.string.time_active, R.string.time_active_short, R.string.units_time_basic, SensorValueType.INTEGER, new TimeFormatter(), false, "s"),
    /**
     * Active duration within the current lap.
     * Internal unit: {@code s} ({@code INTEGER}, {@code >= 0}); sentinel: {@code null} -> {@code "--:--:--"};
     * display: {@link TimeFormatter} ({@code H:MM:SS}); export: CSV raw {@code s}.
     */
    TIME_LAP(SensorCategory.TIME_AND_LAPS, SensorSubCategory.LAP_METRICS, R.string.time_lap, R.string.time_lap_short, R.string.units_time_basic, SensorValueType.INTEGER, new TimeFormatter(), false, "s"),
    /**
     * Localized wall-clock time-of-day string ({@code SimpleDateFormat.getTimeInstance()}).
     * Internal unit: {@code dimensionless} ({@code STRING}); sentinel: {@code null} -> {@code "--"};
     * display: {@link DefaultStringFormatter}; export: CSV raw text.
     */
    TIME_OF_DAY(SensorCategory.TIME_AND_LAPS, R.string.time_of_day, R.string.time_of_day_short, R.string.units_none, SensorValueType.STRING, new DefaultStringFormatter(), false, "dimensionless"),
    /**
     * Total elapsed wall duration including pauses.
     * Internal unit: {@code s} ({@code INTEGER}, {@code >= 0}); sentinel: {@code null} -> {@code "--:--:--"};
     * display: {@link TimeFormatter} ({@code H:MM:SS}); export: FIT {@code totalElapsedTime} ({@code s}), GC {@code SECS} ({@code s}), CSV raw {@code s}.
     */
    TIME_TOTAL(SensorCategory.TIME_AND_LAPS, R.string.time_total, R.string.time_total_short, R.string.units_time_basic, SensorValueType.INTEGER, new TimeFormatter(), false, "s"),
    /**
     * Crank/wheel torque.
     * Internal unit: {@code Nm} ({@code DOUBLE}, {@code >= 0}); sentinel: {@code null} -> {@code "--"};
     * display: {@link DefaultNumberFormatter} ({@code Nm}); export: GC {@code NM} ({@code Nm}), CSV raw {@code Nm}.
     */
    TORQUE(SensorCategory.POWER_AND_PEDALING, SensorSubCategory.PEDALING_DYNAMICS, R.string.torque, R.string.torque_short, R.string.units_torque, SensorValueType.DOUBLE, new DefaultNumberFormatter(), true, "Nm"),
    /**
     * Left pedal torque effectiveness percentage.
     * Internal unit: {@code %} ({@code INTEGER}, {@code [0, 100]}); sentinel: {@code null} -> {@code "--"};
     * display: {@link IntegerFormatter} ({@code %}); export: FIT/CSV/GC in {@code %}.
     */
    TORQUE_EFFECTIVENESS_L(SensorCategory.POWER_AND_PEDALING, SensorSubCategory.PEDALING_DYNAMICS, R.string.torque_effectiveness_l, R.string.torque_effectiveness_l_short, R.string.units_percent, SensorValueType.INTEGER, new IntegerFormatter(), true, "%"),
    /**
     * Right pedal torque effectiveness percentage.
     * Internal unit: {@code %} ({@code INTEGER}, {@code [0, 100]}); sentinel: {@code null} -> {@code "--"};
     * display: {@link IntegerFormatter} ({@code %}); export: FIT/CSV/GC in {@code %}.
     */
    TORQUE_EFFECTIVENESS_R(SensorCategory.POWER_AND_PEDALING, SensorSubCategory.PEDALING_DYNAMICS, R.string.torque_effectiveness_r, R.string.torque_effectiveness_r_short, R.string.units_percent, SensorValueType.INTEGER, new IntegerFormatter(), true, "%"),
    /**
     * Vertical ascent/descent rate (VAM).
     * Internal unit: {@code m/h} ({@code INTEGER}, live devices clamp {@code [-3000, 3000]} {@code m/h}; see {@code ATT-8577} for legacy ERTS {@code m/s} note);
     * sentinel: {@code null} -> {@code "--"}; display: {@link IntegerFormatter} ({@code m/h}); export: CSV raw {@code m/h}.
     */
    VERTICAL_SPEED(SensorCategory.ELEVATION, R.string.vertical_speed, R.string.vertical_speed_short, R.string.units_vertical_speed_basic, SensorValueType.INTEGER, new IntegerFormatter(), true, "m/h"),
    /**
     * Host smartphone battery state of charge.
     * Internal unit: {@code %} ({@code INTEGER}, {@code [0, 100]}); sentinel: {@code null} -> {@code "--"};
     * display: {@link IntegerFormatter} ({@code %}); export: CSV raw {@code %}.
     */
    PHONE_BATTERY(SensorCategory.SYSTEM_ENVIRONMENT_AND_DIAGNOSTICS, SensorSubCategory.PHONE_BATTERY_GROUP, R.string.phone_battery, R.string.phone_battery_short, R.string.units_percent, SensorValueType.INTEGER, new IntegerFormatter(), false, "%"),
    /**
     * Projected remaining smartphone recording duration.
     * Internal unit: {@code s} ({@code INTEGER}, {@code >= 0}); sentinels: {@code -1} ({@code CHARGING_STATUS_CODE} -> {@code "Charging"}),
     * {@code <= -2} ({@code STABILIZING_STATUS_CODE} -> {@code "--:--"}), {@code null} -> {@code "--:--"};
     * display: {@link BatteryRemainingTimeFormatter} ({@code %d:%02d h}); export: CSV raw {@code s}/sentinel.
     */
    BATTERY_REMAINING_TIME(SensorCategory.SYSTEM_ENVIRONMENT_AND_DIAGNOSTICS, SensorSubCategory.PHONE_BATTERY_GROUP, R.string.battery_remaining_time, R.string.battery_remaining_time_short, R.string.units_none, SensorValueType.INTEGER, new BatteryRemainingTimeFormatter(), false, "s"),
    /**
     * Beat-to-beat cardiac R-R interval.
     * Internal unit: {@code ms} ({@code INTEGER}); sentinel: {@code null} -> {@code "--"};
     * display: {@link IntegerFormatter} ({@code ms}); export: FIT {@code HrvMesg} in {@code s} ({@code rrMs / 1000.0f}), CSV raw {@code ms}.
     */
    RR_INTERVAL(SensorCategory.HEART_RATE_AND_ENERGY, SensorSubCategory.HRV_AND_DRIFT, R.string.rr_interval, R.string.rr_interval_short, R.string.units_time_ms, SensorValueType.INTEGER, new IntegerFormatter(), true, "ms"),
    /**
     * 60-second rolling root mean square of successive R-R differences.
     * Internal unit: {@code ms} ({@code DOUBLE}, {@code >= 0}); sentinel: {@code null} -> {@code "--"};
     * display: {@link IntegerFormatter} ({@code ms}); export: CSV raw {@code ms}.
     */
    HRV_RMSSD(SensorCategory.HEART_RATE_AND_ENERGY, SensorSubCategory.HRV_AND_DRIFT, R.string.hrv_rmssd, R.string.hrv_rmssd_short, R.string.units_time_ms, SensorValueType.DOUBLE, new IntegerFormatter(), true, "ms"),
    /**
     * Detrended Fluctuation Analysis short-term scaling exponent (alpha 1).
     * Internal unit: {@code dimensionless} ({@code DOUBLE}); sentinels: {@code < 0}/{@code NaN}/{@code Infinite}/{@code null} -> {@code "--.--"};
     * display: {@link DfaA1Formatter} ({@code 0.00}); export: CSV raw dimensionless.
     */
    HRV_DFA_A1(SensorCategory.HEART_RATE_AND_ENERGY, SensorSubCategory.HRV_AND_DRIFT, R.string.hrv_dfa_a1, R.string.hrv_dfa_a1_short, R.string.units_none, SensorValueType.DOUBLE, new DfaA1Formatter(), true, "dimensionless"),
    /**
     * R-R artifact percentage over the 120-second HRV window.
     * Internal unit: {@code %} ({@code DOUBLE}, {@code [0.0, 100.0]}); sentinel: {@code null} -> {@code "--"};
     * display: {@link DefaultNumberFormatter} ({@code %}); export: CSV raw {@code %}.
     */
    HRV_ARTIFACT_RATE(SensorCategory.HEART_RATE_AND_ENERGY, SensorSubCategory.HRV_AND_DRIFT, R.string.hrv_artifact_rate, R.string.hrv_artifact_rate_short, R.string.units_percent, SensorValueType.DOUBLE, new DefaultNumberFormatter(), true, "%"),
    /**
     * Standard deviation of normal-to-normal R-R intervals.
     * Internal unit: {@code ms} ({@code DOUBLE}, {@code >= 0}); sentinel: {@code null} -> {@code "--"};
     * display: {@link DefaultNumberFormatter} ({@code ms}); export: CSV raw {@code ms}.
     */
    HRV_SDNN(SensorCategory.HEART_RATE_AND_ENERGY, SensorSubCategory.HRV_AND_DRIFT, R.string.hrv_sdnn, R.string.hrv_sdnn_short, R.string.units_time_ms, SensorValueType.DOUBLE, new DefaultNumberFormatter(), true, "ms"),
    /**
     * Active approaching radar threat count.
     * Internal unit: {@code dimensionless} ({@code INTEGER}, {@code >= 0}); sentinel: {@code null} -> {@code "--"};
     * display: {@link IntegerFormatter}; export: FIT DevField {@code radar_current} ({@code targets}), CSV raw count.
     */
    RADAR_THREAT_COUNT(SensorCategory.SYSTEM_ENVIRONMENT_AND_DIAGNOSTICS, SensorSubCategory.RADAR_SAFETY, R.string.radar_threat_count, R.string.radar_threat_count_short, R.string.units_none, SensorValueType.INTEGER, new IntegerFormatter(), false, "dimensionless"),
    /**
     * Longitudinal range to the closest approaching radar target.
     * Internal unit: {@code m} ({@code DOUBLE}, {@code >= 0}); sentinel: {@code null} -> {@code "--"};
     * display: {@link DistanceFormatter} ({@code km}/{@code mile}; {@code MyHelper.getUnitsId} falls back to {@code units_distance_basic}); export: CSV raw {@code m}.
     */
    RADAR_CLOSEST_DISTANCE_m(SensorCategory.SYSTEM_ENVIRONMENT_AND_DIAGNOSTICS, SensorSubCategory.RADAR_SAFETY, R.string.radar_closest_distance, R.string.radar_closest_distance_short, R.string.units_distance_basic, SensorValueType.DOUBLE, new DistanceFormatter(), false, "m"),
    /**
     * Relative closing speed of the closest approaching radar target.
     * Internal unit: {@code m/s} ({@code DOUBLE}, {@code >= 0}, FIT {@code EventMesg} clamped {@code [0.0, 25.4]}); sentinel: {@code null} -> {@code "--"};
     * display: {@link SpeedFormatter} ({@code km/h}/{@code mile/h}; {@code MyHelper.getUnitsId} falls back to {@code units_speed_basic});
     * export: FIT {@code EventMesg} + DevFields ({@code m/s}), CSV raw {@code m/s}.
     */
    RADAR_APPROACH_SPEED_mps(SensorCategory.SYSTEM_ENVIRONMENT_AND_DIAGNOSTICS, SensorSubCategory.RADAR_SAFETY, R.string.radar_approach_speed, R.string.radar_approach_speed_short, R.string.units_speed_basic, SensorValueType.DOUBLE, new SpeedFormatter(), false, "m/s"),
    /**
     * Cumulative count of radar-detected passing vehicles.
     * Internal unit: {@code dimensionless} ({@code INTEGER}, {@code >= 0}); sentinel: {@code null} -> {@code "--"};
     * display: {@link IntegerFormatter}; export: FIT DevField {@code radar_total} ({@code vehicles}), CSV raw count.
     */
    RADAR_VEHICLE_COUNT(SensorCategory.SYSTEM_ENVIRONMENT_AND_DIAGNOSTICS, SensorSubCategory.RADAR_SAFETY, R.string.radar_vehicle_count, R.string.radar_vehicle_count_short, R.string.units_none, SensorValueType.INTEGER, new IntegerFormatter(), false, "dimensionless"),
    /**
     * Lateral passing clearance distance from V2 radar telemetry.
     * Internal unit: {@code m} ({@code DOUBLE}, {@code >= 0}); sentinel: {@code null} -> {@code "--"};
     * display: {@link DistanceFormatter} ({@code km}/{@code mile}; {@code MyHelper.getUnitsId} falls back to {@code units_distance_basic});
     * export: FIT DevField {@code passing_distance} ({@code m}), CSV raw {@code m}.
     */
    RADAR_PASSING_DISTANCE_m(SensorCategory.SYSTEM_ENVIRONMENT_AND_DIAGNOSTICS, SensorSubCategory.RADAR_SAFETY, R.string.radar_passing_distance, R.string.radar_passing_distance_short, R.string.units_distance_basic, SensorValueType.DOUBLE, new DistanceFormatter(), false, "m"),
    /**
     * Cumulative count of large overtaking vehicles from V2 radar telemetry.
     * Internal unit: {@code dimensionless} ({@code INTEGER}, {@code >= 0}); sentinel: {@code null} -> {@code "--"};
     * display: {@link IntegerFormatter}; export: FIT DevField {@code large_vehicle_count} ({@code vehicles}), CSV raw count.
     */
    RADAR_LARGE_VEHICLE_COUNT(SensorCategory.SYSTEM_ENVIRONMENT_AND_DIAGNOSTICS, SensorSubCategory.RADAR_SAFETY, R.string.radar_large_vehicle_count, R.string.radar_large_vehicle_count_short, R.string.units_none, SensorValueType.INTEGER, new IntegerFormatter(), false, "dimensionless"),
    /**
     * Real-time cardiac drift relative to modeled power-HR baseline.
     * Internal unit: {@code bpm} ({@code INTEGER}, signed); sentinels: {@code null}/{@code NaN}/{@code Infinite} -> {@code "--"};
     * display: {@link SignedIntegerFormatter} ({@code bpm} with {@code +} prefix when {@code > 0}); export: CSV raw {@code bpm}.
     */
    LIVE_HR_DRIFT(SensorCategory.HEART_RATE_AND_ENERGY, SensorSubCategory.HRV_AND_DRIFT, R.string.live_hr_drift, R.string.live_hr_drift_short, R.string.units_heart_rate, SensorValueType.INTEGER, new SignedIntegerFormatter(), true, "bpm"),
    /**
     * Projected world vertical-axis yaw rate from gyroscope fusion.
     * Internal unit: {@code rad/s} ({@code DOUBLE}, signed); sentinel: {@code null} -> {@code "--"};
     * display: {@link DefaultNumberFormatter} ({@code rad/s}); export: CSV raw {@code rad/s}.
     */
    WORLD_YAW_RATE_radps(SensorCategory.SYSTEM_ENVIRONMENT_AND_DIAGNOSTICS, null, R.string.world_yaw_rate, R.string.world_yaw_rate_short, R.string.units_rad_per_sec, SensorValueType.DOUBLE, new DefaultNumberFormatter(), false, "rad/s"),
    /**
     * Running vertical oscillation amplitude.
     * Internal unit: {@code cm} ({@code DOUBLE}, FIT export bounds {@code [1.0, 25.0]}); sentinel: {@code null} -> {@code "--"};
     * display: {@link DefaultNumberFormatter} ({@code cm}); export: FIT {@code mm} ({@code voCm * CM_TO_MM}), CSV raw {@code cm}.
     */
    VERTICAL_OSCILLATION(SensorCategory.SPEED_PACE_AND_DISTANCE, R.string.vertical_oscillation, R.string.vertical_oscillation_short, R.string.units_cm, SensorValueType.DOUBLE, new DefaultNumberFormatter(), true, "cm"),
    /**
     * Running foot ground contact time.
     * Internal unit: {@code ms} ({@code DOUBLE}, FIT export bounds {@code [50.0, 1000.0]}); sentinel: {@code null} -> {@code "--"};
     * display: {@link IntegerFormatter} ({@code ms}); export: FIT {@code stanceTime} ({@code ms}), CSV raw {@code ms}.
     */
    GROUND_CONTACT_TIME(SensorCategory.SPEED_PACE_AND_DISTANCE, R.string.ground_contact_time, R.string.ground_contact_time_short, R.string.units_time_ms, SensorValueType.DOUBLE, new IntegerFormatter(), true, "ms");

    // max bit = 118

    public static final java.util.List<SensorType> CORE_METRICS = java.util.Arrays.asList(
            POWER, HR, SPEED_mps, CADENCE, ALTITUDE, TEMPERATURE
    );

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
    @Nullable
    private final SensorSubCategory subCategory;
    private final int fullNameId;
    private final int shortNameId;
    private final int unitId;
    private final SensorValueType sensorValueType;
    private final MyFormatter myFormatter;
    @NonNull
    private final String internalUnit;

    SensorType(SensorCategory category, int fullNameId, int shortNameId, int unitId, SensorValueType sensorValueType, MyFormatter myFormatter, boolean filteringPossible, @NonNull String internalUnit) {
        this(category, null, fullNameId, shortNameId, unitId, sensorValueType, myFormatter, filteringPossible, internalUnit);
    }

    SensorType(SensorCategory category, @Nullable SensorSubCategory subCategory, int fullNameId, int shortNameId, int unitId, SensorValueType sensorValueType, MyFormatter myFormatter, boolean filteringPossible, @NonNull String internalUnit) {
        this.category = category;
        this.subCategory = subCategory;
        this.fullNameId = fullNameId;
        this.shortNameId = shortNameId;
        this.unitId = unitId;
        this.sensorValueType = sensorValueType;
        this.myFormatter = myFormatter;
        this.filteringPossible = filteringPossible;
        this.internalUnit = internalUnit;
    }

    /**
     * Returns the semantic {@link SensorCategory} for this telemetry metric (ATT-3852, ATT-6726, REQ-TRK-070.1).
     */
    public SensorCategory getCategory() {
        return category;
    }

    /**
     * Returns the secondary {@link SensorSubCategory} for this metric, or {@code null} if this is a direct core metric (ATT-6726, REQ-TRK-070.2).
     */
    @Nullable
    public SensorSubCategory getSubCategory() {
        return subCategory;
    }

    public int getIconResId() {
        return switch (this) {
            case TIME_ACTIVE -> R.drawable.ic_time_active;
            case ACCURACY, LONGITUDE, LATITUDE -> R.drawable.ic_location;
            case ALTITUDE -> R.drawable.ic_altitude;
            case DISTANCE_m -> R.drawable.ic_distance;
            case SPEED_mps -> R.drawable.ic_speed;
            case CADENCE, VERTICAL_OSCILLATION, GROUND_CONTACT_TIME -> R.drawable.ic_cadence;
            case HR, RR_INTERVAL, HRV_RMSSD, HRV_DFA_A1, HRV_ARTIFACT_RATE, HRV_SDNN, LIVE_HR_DRIFT -> R.drawable.ic_heart_rate;
            case POWER, POWER_TO_WEIGHT, NORMALIZED_POWER, INTENSITY_FACTOR, WORK_KJ, TSS, W_PRIME_BAL,
                 POWER_5S, POWER_1M, POWER_5M, POWER_20M,
                 POWER_PEAK_5S, POWER_PEAK_1M, POWER_PEAK_5M, POWER_PEAK_20M -> R.drawable.ic_power;
            case PHONE_BATTERY, BATTERY_REMAINING_TIME -> R.drawable.ic_battery_full;
            case RADAR_THREAT_COUNT, RADAR_CLOSEST_DISTANCE_m, RADAR_APPROACH_SPEED_mps,
                 RADAR_VEHICLE_COUNT, RADAR_PASSING_DISTANCE_m, RADAR_LARGE_VEHICLE_COUNT -> R.drawable.ic_radar;
            default -> R.drawable.ic_cross;
        };
    }

    @Override
    public String toString() {
        Context context = TrainingApplication.getAppContext();
        return (context != null) ? context.getString(fullNameId) : name();
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

    /**
     * Returns the canonical internal SQLite and sensor-bus storage unit for this {@link SensorType}
     * (e.g., {@code "m/s"}, {@code "m/h"}, {@code "m"}, {@code "s"}, {@code "ms"}, {@code "kJ"}, {@code "dimensionless"}).
     * See {@code docs/architecture/sensor_units.md} ({@code REQ-TRK-083}).
     */
    @NonNull
    public String getInternalUnit() {
        return internalUnit;
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
