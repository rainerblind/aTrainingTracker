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

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.atrainingtracker.R;

/**
 * Authoritative domain categorization for {@link SensorType} telemetry metrics (ATT-3852, REQ-UI-395).
 * Groups all 43 {@link SensorType} values into 7 semantic training categories for structured
 * selection and filtering in {@code EditSensorFieldDialog}.
 */
public enum SensorCategory {
    TIME_AND_LAPS(
            R.string.sensor_category_time_and_laps,
            R.drawable.ic_time_active
    ),
    SPEED_PACE_AND_DISTANCE(
            R.string.sensor_category_speed_pace_and_distance,
            R.drawable.ic_speed
    ),
    CADENCE_AND_RUNNING(
            R.string.sensor_category_cadence_and_running,
            R.drawable.ic_cadence
    ),
    HEART_RATE_AND_ENERGY(
            R.string.sensor_category_heart_rate_and_energy,
            R.drawable.ic_heart_rate
    ),
    POWER_AND_PEDALING(
            R.string.sensor_category_power_and_pedaling,
            R.drawable.ic_power
    ),
    ELEVATION_AND_NAVIGATION(
            R.string.sensor_category_elevation_and_navigation,
            R.drawable.ic_altitude
    ),
    SYSTEM_ENVIRONMENT_AND_DIAGNOSTICS(
            R.string.sensor_category_system_environment_and_diagnostics,
            R.drawable.ic_battery_full
    );

    @StringRes
    private final int titleResId;
    @DrawableRes
    private final int iconResId;

    SensorCategory(@StringRes int titleResId, @DrawableRes int iconResId) {
        this.titleResId = titleResId;
        this.iconResId = iconResId;
    }

    /**
     * Returns the localized title string resource ID for this category.
     */
    @StringRes
    public int getTitleResId() {
        return titleResId;
    }

    /**
     * Returns the localized display title for this category using the provided {@link Context}.
     */
    @NonNull
    public String getTitle(@NonNull Context context) {
        return context.getString(titleResId);
    }

    /**
     * Returns the vector drawable resource ID representing this category.
     */
    @DrawableRes
    public int getIconResId() {
        return iconResId;
    }
}
