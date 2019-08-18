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

import com.atrainingtracker.banalservice.sensor.SensorData;
import com.atrainingtracker.banalservice.sensor.SensorType;

public class FilteredSensorData<T> extends SensorData<T> {

    protected FilterType mFilterType;
    protected double mFilterConstant;

    public FilteredSensorData(SensorType sensorType, T value, String stringValue, String deviceName, FilterType filterType, double filterConstant) {
        super(sensorType, value, stringValue, deviceName);

        mFilterType = filterType;
        mFilterConstant = filterConstant;
    }

    public FilterType getFilterType() {
        return mFilterType;
    }

    public double getFilterConstant() {
        return mFilterConstant;
    }

    public FilterData getFilterData() {
        return new FilterData(getDeviceName(), getSensorType(), getFilterType(), getFilterConstant());
    }

}
