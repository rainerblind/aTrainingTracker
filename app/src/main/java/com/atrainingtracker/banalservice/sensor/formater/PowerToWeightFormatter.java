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

import androidx.annotation.NonNull;

import java.util.Locale;

/**
 * Formats power-to-weight ratio (W/kg) to 1 decimal place (REQ-TRK-019, TST-TRK-011).
 * Returns "--" for null, NaN, infinite, or negative values.
 */
public class PowerToWeightFormatter implements MyFormatter<Number> {

    @NonNull
    @Override
    public String format(Number value) {
        if (value == null) {
            return "--";
        }
        double val = value.doubleValue();
        if (Double.isNaN(val) || Double.isInfinite(val) || val < 0.0) {
            return "--";
        }
        return String.format(Locale.getDefault(), "%.1f", val);
    }

    public String format_with_units(Number value) {
        return format(value) + " W/kg";
    }
}
