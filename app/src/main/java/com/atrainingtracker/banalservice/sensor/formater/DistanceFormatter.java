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

import com.atrainingtracker.banalservice.BANALService;
import com.atrainingtracker.trainingtracker.TrainingApplication;


public class DistanceFormatter implements MyFormatter<Number> {

    @Override
    public String format(Number distance_m) {

        if (distance_m == null) {
            return "--";
        }

        switch (TrainingApplication.getUnit()) {
            case METRIC:
                return String.format("%.2f", distance_m.doubleValue() / 1000);
            // return NumberFormat.getInstance().format(distance_m.doubleValue()/1000);
            case IMPERIAL:
                return String.format("%.2f", distance_m.doubleValue() / BANALService.METER_PER_MILE);
            // return NumberFormat.getInstance().format(distance_m.doubleValue()/BANALService.METER_PER_MILE);
            default:
                return "--";
        }
    }

    public String format_with_units(Number distance_m) {

        if (distance_m == null) {
            return "--";
        }

        switch (TrainingApplication.getUnit()) {
            case METRIC:
                if (distance_m.intValue() < 1000) {
                    return String.format("%d m", distance_m.intValue());
                } else {
                    return String.format("%.2f km", distance_m.doubleValue() / 1000);
                }
                // return NumberFormat.getInstance().format(distance_m.doubleValue()/1000);
            case IMPERIAL:
                return String.format("%.2f mile", distance_m.doubleValue() / BANALService.METER_PER_MILE);
            // return NumberFormat.getInstance().format(distance_m.doubleValue()/BANALService.METER_PER_MILE);
            default:
                return "--";
        }
    }

    public String format_3(Number distance_m) {

        if (distance_m == null) {
            return "--";
        }

        switch (TrainingApplication.getUnit()) {
            case METRIC:
                return String.format("%.3f", distance_m.doubleValue() / 1000);
            // return NumberFormat.getInstance().format(distance_m.doubleValue()/1000);
            case IMPERIAL:
                return String.format("%.3f", distance_m.doubleValue() / BANALService.METER_PER_MILE);
            // return NumberFormat.getInstance().format(distance_m.doubleValue()/BANALService.METER_PER_MILE);
            default:
                return "--";
        }
    }
}
