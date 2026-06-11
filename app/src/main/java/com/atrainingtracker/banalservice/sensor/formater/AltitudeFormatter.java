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

import com.atrainingtracker.banalservice.BANALService;
import com.atrainingtracker.trainingtracker.MyHelper;
import com.atrainingtracker.trainingtracker.TrainingApplication;

import java.util.Locale;


public class AltitudeFormatter implements MyFormatter<Number> {

    @Override
    public String format(Number altitude_m) {

        if (altitude_m == null) {
            return "--";
        }

        switch (TrainingApplication.getUnit()) {
            case METRIC:
                return String.format(Locale.getDefault(), "%.0f", altitude_m.doubleValue());
            case IMPERIAL:
                return String.format(Locale.getDefault(), "%.0f", altitude_m.doubleValue() / BANALService.METER_PER_FOOT);
            default:
                return "--";
        }
    }

    public String format_with_units(Number altitude_m) {

        if (altitude_m == null) {
            return "--";
        }

        String units = TrainingApplication.getAppContext().getString(MyHelper.getShortAltitudeUnitNameId());

        switch (TrainingApplication.getUnit()) {
            case METRIC:
                return String.format(Locale.getDefault(), "%.0f %s", altitude_m.doubleValue(), units);
            case IMPERIAL:
                return String.format(Locale.getDefault(), "%.0f %s", altitude_m.doubleValue() / BANALService.METER_PER_FOOT, units);
            default:
                return "--";
        }
    }

    public String format_3(Number altitude_m) {

        if (altitude_m == null) {
            return "--";
        }

        switch (TrainingApplication.getUnit()) {
            case METRIC:
                return String.format(Locale.getDefault(), "%.3f", altitude_m.doubleValue());
            case IMPERIAL:
                return String.format(Locale.getDefault(), "%.3f", altitude_m.doubleValue() / BANALService.METER_PER_FOOT);
            default:
                return "--";
        }
    }
}
