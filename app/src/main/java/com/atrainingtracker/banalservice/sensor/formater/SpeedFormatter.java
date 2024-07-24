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

package com.atrainingtracker.banalservice.sensor.formater;

// import java.text.NumberFormat;

import com.atrainingtracker.trainingtracker.TrainingApplication;

import java.util.Locale;

public class SpeedFormatter implements MyFormatter<Number> {
    @Override
    public String format(Number speed_mps) {
        if (speed_mps == null) {
            return "--";
        }

        double foo = 0;
        switch (TrainingApplication.getUnit()) {
            case METRIC:
                foo = speed_mps.doubleValue() * 3.6;
                break;

            case IMPERIAL:
                foo = speed_mps.doubleValue() * 2.23693629;
                break;
        }
        return String.format(Locale.getDefault(), "%.1f", foo);
        // return NumberFormat.getInstance().format(foo);
    }

    public String format_with_units(Number speed_mps) {
        String units = "";
        switch (TrainingApplication.getUnit()) {
            case METRIC:
                units = "km/h";
                break;
            case IMPERIAL:
                units = "mile/h";
                break;
        }

        return format(speed_mps) + " " + units;
    }

}
