package com.atrainingtracker.trainingtracker.segments;

import com.atrainingtracker.trainingtracker.TrainingApplication;
import com.google.android.gms.maps.model.LatLng;

/**
 * Created by rainer on 21.09.16.
 */

public class SegmentHelper {

    private static final String TAG = SegmentHelper.class.getName();
    private static final boolean DEBUG = TrainingApplication.DEBUG && false;

    public static double LatitudeDegreeInMeters(LatLng latLng) {
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

    public static double LongitudeDegreeInMeters(LatLng latLng) {
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
