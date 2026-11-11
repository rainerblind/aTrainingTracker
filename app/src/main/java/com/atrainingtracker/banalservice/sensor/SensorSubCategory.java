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

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.atrainingtracker.R;

/**
 * Authoritative secondary domain categorization for {@link SensorType} telemetry metrics (ATT-6726, REQ-TRK-070.2).
 * Groups specialized telemetry metrics into structured sub-categories within {@link SensorCategory}.
 */
public enum SensorSubCategory {
    POWER_LOAD(R.string.sensor_sub_category_power_load),
    POWER_ROLLING(R.string.sensor_sub_category_power_rolling),
    POWER_PEAK(R.string.sensor_sub_category_power_peak),
    PEDALING_DYNAMICS(R.string.sensor_sub_category_pedaling_dynamics),
    HRV_AND_DRIFT(R.string.sensor_sub_category_hrv_and_drift),
    LAP_METRICS(R.string.sensor_sub_category_lap_metrics),
    RADAR_SAFETY(R.string.sensor_sub_category_radar_safety),
    AMBIENT_TEMPERATURE(R.string.sensor_sub_category_ambient_temperature),
    PHONE_BATTERY_GROUP(R.string.sensor_sub_category_phone_battery);

    @StringRes
    private final int titleResId;

    SensorSubCategory(@StringRes int titleResId) {
        this.titleResId = titleResId;
    }

    /**
     * Returns the localized title string resource ID for this sub-category.
     */
    @StringRes
    public int getTitleResId() {
        return titleResId;
    }

    /**
     * Returns the localized display title for this sub-category using the provided {@link Context}.
     */
    @NonNull
    public String getTitle(@NonNull Context context) {
        return context.getString(titleResId);
    }
}
