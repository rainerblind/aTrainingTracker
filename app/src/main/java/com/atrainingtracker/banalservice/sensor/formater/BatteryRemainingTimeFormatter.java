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

package com.atrainingtracker.banalservice.sensor.formater;

import android.content.Context;

import com.atrainingtracker.R;
import com.atrainingtracker.trainingtracker.TrainingApplication;

import java.util.Locale;

/**
 * Formats smartphone battery remaining duration into hours and minutes (e.g. "8:45 h").
 * Handles special status values:
 * - null or -2: initial stabilization or unknown drain ("--:--")
 * - -1: host device actively connected to charger ("Lädt" / "Charging")
 */
public class BatteryRemainingTimeFormatter implements MyFormatter<Number> {

    public static final int CHARGING_STATUS_CODE = -1;
    public static final int STABILIZING_STATUS_CODE = -2;

    @Override
    public String format(Number value) {
        if (value == null) {
            return "--:--";
        }
        int seconds = value.intValue();
        if (seconds == CHARGING_STATUS_CODE) {
            try {
                Context context = TrainingApplication.getAppContext();
                if (context != null) {
                    return context.getString(R.string.battery_charging);
                }
            } catch (Exception ignored) {
            }
            return "Charging";
        }
        if (seconds <= STABILIZING_STATUS_CODE) {
            return "--:--";
        }
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        return String.format(Locale.getDefault(), "%d:%02d h", hours, minutes);
    }
}
