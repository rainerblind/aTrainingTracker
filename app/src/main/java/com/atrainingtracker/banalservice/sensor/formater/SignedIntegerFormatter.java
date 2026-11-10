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

/**
 * Formatter for signed integer sensor values (e.g. Live HR Drift).
 * Formats positive numbers with a '+' prefix (e.g. "+5"), zero as "0",
 * and negative numbers with a standard minus sign (e.g. "-2").
 */
public class SignedIntegerFormatter implements MyFormatter<Number> {

    @Override
    public String format(Number value) {
        if (value == null) {
            return "--";
        }
        double d = value.doubleValue();
        if (Double.isNaN(d) || Double.isInfinite(d)) {
            return "--";
        }
        long rounded = Math.round(d);
        if (rounded > 0) {
            return "+" + rounded;
        } else {
            return Long.toString(rounded);
        }
    }
}
