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

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Formatter for Detrended Fluctuation Analysis (DFA) alpha 1 metrics conforming to REQ-TRK-061.2 and TST-TRK-061.2.
 * Formats dimensionless DFA alpha 1 scaling exponents to 2 decimal places ("0.00", e.g. "0.75" or "1.00").
 * Returns "--.--" for null, NaN, infinite, negative, or artifact-gated values.
 */
public class DfaA1Formatter implements MyFormatter<Number> {

    public static final String BLANK_VALUE = "--.--";

    @NonNull
    @Override
    public String format(Number value) {
        if (value == null) {
            return BLANK_VALUE;
        }
        double d = value.doubleValue();
        if (Double.isNaN(d) || Double.isInfinite(d) || d < 0.0) {
            return BLANK_VALUE;
        }
        DecimalFormat df = new DecimalFormat("0.00", DecimalFormatSymbols.getInstance(Locale.getDefault()));
        return df.format(d);
    }
}
