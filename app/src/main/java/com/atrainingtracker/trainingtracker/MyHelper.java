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

package com.atrainingtracker.trainingtracker;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

import androidx.annotation.NonNull;

import com.atrainingtracker.R;
import com.atrainingtracker.banalservice.sensor.SensorType;

import org.jetbrains.annotations.Contract;

import java.text.NumberFormat;
import java.util.Locale;

public class MyHelper {
    private static final String TAG = "MyHelper";
    private static final boolean DEBUG = false;

    public static boolean isOnline() {
        ConnectivityManager cm = (ConnectivityManager) TrainingApplication.getAppContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        return netInfo != null && netInfo.isConnectedOrConnecting();
    }

    public static double string2Double(@NonNull String doubleString) {
        try {
            return Double.parseDouble(doubleString);
        } catch (NumberFormatException e) {
            if (DEBUG) Log.i(TAG, "Failed to parse double, retry with NumberFormat");
            // "sample" was not an integer value
            // You should probably start settings again
        }


        NumberFormat format = NumberFormat.getInstance();
        Number number = 0.0;
        try {
            number = format.parse(doubleString);
        } catch (Exception e) {
            Log.i(TAG, "failed to parse '" + doubleString + "' with the locale format, try to parse with the US format");
            format = NumberFormat.getInstance(Locale.US);
            try {
                number = format.parse(doubleString);
            } catch (Exception ee) {
                Log.i(TAG, "WTF: could not convert '" + doubleString + "' to a number, returning 0");
            }

        }
        return number.doubleValue();
    }

    public static double mps2userUnit(double speed) {
        return mps2userUnit(speed, TrainingApplication.getUnit());
    }

    public static double mps2userUnit(double speed, @NonNull MyUnits myUnits) {
        return switch (myUnits) {
            case METRIC -> speed * 3.6;
            case IMPERIAL -> speed * 2.23693629;
        };
    }

    public static double UserUnit2mps(double speed) {
        return UserUnit2mps(speed, TrainingApplication.getUnit());
    }

    public static double UserUnit2mps(double speed, @NonNull MyUnits myUnits) {
        return switch (myUnits) {
            case METRIC -> speed / 3.6;
            case IMPERIAL -> speed / 2.23693629;
        };
    }

    // TODO: replace with getSpeedUnitName(Context context)
    // TODO: globally!
    public static int getSpeedUnitNameId() {
        return switch (TrainingApplication.getUnit()) {
            case METRIC -> R.string.units_speed_metric;
            case IMPERIAL -> R.string.units_speed_imperial;
        };
    }

    public static int getShortSpeedUnitNameId() {
        return switch (TrainingApplication.getUnit()) {
            case METRIC -> R.string.units_speed_short_metric;
            case IMPERIAL -> R.string.units_speed_short_imperial;
        };
    }

    public static int getPaceUnitNameId() {
        return switch (TrainingApplication.getUnit()) {
            case METRIC -> R.string.units_pace_metric;
            case IMPERIAL -> R.string.units_pace_imperial;
        };
    }

    public static int getShortPaceUnitNameId() {
        return switch (TrainingApplication.getUnit()) {
            case METRIC -> R.string.units_pace_short_metric;
            case IMPERIAL -> R.string.units_pace_short_imperial;
        };
    }

    public static int getDistanceUnitNameId() {
        return switch (TrainingApplication.getUnit()) {
            case METRIC -> R.string.units_distance_metric;
            case IMPERIAL -> R.string.units_distance_imperial;
        };
    }

    public static int getAltitudeUnitNameId() {
        return switch (TrainingApplication.getUnit()) {
            case METRIC -> R.string.units_altitude_metric;
            case IMPERIAL -> R.string.units_altitude_imperial;
        };
    }

    public static int getShortAltitudeUnitNameId() {
        return switch (TrainingApplication.getUnit()) {
            case METRIC -> R.string.units_altitude_short_metric;
            case IMPERIAL -> R.string.units_altitude_short_imperial;
        };
    }

    public static String getDistanceUnitName(Context context) {
        return context.getString(getDistanceUnitNameId());
    }

    public static int getUnitsId(@NonNull SensorType sensorType) {
        return switch (sensorType) {
            case SPEED_mps -> getSpeedUnitNameId();
            case PACE_spm -> getPaceUnitNameId();
            case DISTANCE_m, DISTANCE_m_LAP, LINE_DISTANCE_m -> getDistanceUnitNameId();
            case ALTITUDE, ASCENT, DESCENT -> getAltitudeUnitNameId();
            default -> sensorType.getUnitId();
        };
    }

    public static int getShortUnitsId(@NonNull SensorType sensorType) {
        return switch (sensorType) {
            case SPEED_mps -> getShortSpeedUnitNameId();
            case PACE_spm -> getShortPaceUnitNameId();
            case DISTANCE_m, DISTANCE_m_LAP, LINE_DISTANCE_m ->
                    getDistanceUnitNameId();  // TODO: also short version?
            case ALTITUDE, ASCENT, DESCENT -> getShortAltitudeUnitNameId();
            default -> sensorType.getUnitId();  // TODO: also short version?
        };
    }

    @NonNull
    @Contract(pure = true)
    public static String formatRank(int i) {
        int j = i % 10,
                k = i % 100;
        if (j == 1 && k != 11) {
            return i + "st";
        }
        if (j == 2 && k != 12) {
            return i + "nd";
        }
        if (j == 3 && k != 13) {
            return i + "rd";
        }
        return i + "th";
    }
}
