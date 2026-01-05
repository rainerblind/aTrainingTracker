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

public class VerticalSpeedFormatter implements MyFormatter<Number> {
    @Override
    public String format(Number speed_mps) {
        if (speed_mps == null) {
            return "--";
        }

        double speed = 0;
        switch (TrainingApplication.getUnit()) {
            case METRIC:
                speed = speed_mps.doubleValue() * 60 * 60;  // convert to meters / hour
                break;

            case IMPERIAL:
                speed = speed_mps.doubleValue() * 60 * 60 * 3.28084; // convert to feed.  TODO: check for consistency.
                break;
        }
        return String.format(Locale.getDefault(), "%.1f", speed);
    }

}
