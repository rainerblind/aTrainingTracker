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

package com.atrainingtracker.banalservice.filters;

import com.atrainingtracker.banalservice.Sensor.SensorType;

public class FilterData {

    public String deviceName;
    public SensorType sensorType;
    public FilterType filterType;
    public double filterConstant;

    public FilterData(String deviceName, SensorType sensorType, FilterType filterType, double filterConstant) {
        this.deviceName = deviceName;
        this.sensorType = sensorType;
        this.filterType = filterType;
        this.filterConstant = filterConstant;
    }

    public String getHashKey() {
        return deviceName + "-" + sensorType.name() + "-" + filterType.name() + "-" + filterConstant;
    }
}
