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

package com.atrainingtracker.trainingtracker.segments;

import androidx.annotation.NonNull;

import com.atrainingtracker.trainingtracker.TrainingApplication;
import com.google.android.gms.maps.model.LatLng;

/**
 * Created by rainer on 21.09.16.
 */

public class SegmentHelper {

    private static final String TAG = SegmentHelper.class.getName();
    private static final boolean DEBUG = TrainingApplication.getDebug(false);

    public static double LatitudeDegreeInMeters(@NonNull LatLng latLng) {
        // from http://gis.stackexchange.com/questions/75528/length-of-a-degree-where-do-the-terms-in-this-formula-come-from
        // Set up "Constants"
        double m1 = 111132.92;     // latitude calculation term 1
        double m2 = -559.82;       // latitude calculation term 2
        double m3 = 1.175;         // latitude calculation term 3
        double m4 = -0.0023;       // latitude calculation term 4

        // Calculate the length of a degree of latitude and longitude in meters
        return m1 + (m2 * Math.cos(Math.toRadians(2 * latLng.latitude))) + (m3 * Math.cos(Math.toRadians(4 * latLng.latitude))) +
                (m4 * Math.cos(Math.toRadians(6 * latLng.latitude)));
    }

    public static double LongitudeDegreeInMeters(@NonNull LatLng latLng) {
        // from http://gis.stackexchange.com/questions/75528/length-of-a-degree-where-do-the-terms-in-this-formula-come-from
        // Set up "Constants"
        double p1 = 111412.84;     // longitude calculation term 1
        double p2 = -93.5;         // longitude calculation term 2
        double p3 = 0.118;         // longitude calculation term 3

        // Calculate the length of a degree of latitude and longitude in meters
        return (p1 * Math.cos(Math.toRadians(latLng.latitude))) + (p2 * Math.cos(Math.toRadians(3 * latLng.latitude))) +
                (p3 * Math.cos(Math.toRadians(5 * latLng.latitude)));
    }

    public enum SegmentType {NONE, RUN, BIKE, ALL}

}
