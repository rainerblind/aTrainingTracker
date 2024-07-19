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

import java.util.concurrent.TimeUnit;

public class TimeFormatter implements MyFormatter<Number> {
    @Override
    public String format(Number value) {
        if (value == null) {
            return "--:--:--";
        } else {
            // return seconds2StringFormat.format(new Date(value.intValue() * 1000));
            int s = value.intValue();
            return String.format("%d:%02d:%02d", s / 3600, (s % 3600) / 60, (s % 60));
        }
    }

    public String format_with_units(Number value) {
        if (value == null) {
            return "--:--:--";
        } else {
            long seconds = value.longValue();
            if (seconds < 60) {
                return String.format("%02d sec", seconds);
            } else if (seconds < 60 * 60) {
                long minutes = TimeUnit.SECONDS.toMinutes(seconds);
                seconds = seconds - TimeUnit.MINUTES.toSeconds(minutes);

                return String.format("%02d:%02d min", minutes, seconds);
            } else {
                long hours = TimeUnit.SECONDS.toHours(seconds);
                long minutes = TimeUnit.SECONDS.toMinutes(seconds) - TimeUnit.HOURS.toMinutes(hours);
                seconds = seconds - TimeUnit.HOURS.toSeconds(hours) - TimeUnit.MINUTES.toSeconds(minutes);

                return String.format("%02d:%02d:%02d h", hours, minutes, seconds);
            }
        }
    }
}