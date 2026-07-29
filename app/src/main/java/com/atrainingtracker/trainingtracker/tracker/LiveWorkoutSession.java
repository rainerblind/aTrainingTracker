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

package com.atrainingtracker.trainingtracker.tracker;

import android.util.Log;

import com.atrainingtracker.banalservice.sensor.SensorType;
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries;
import com.atrainingtracker.trainingtracker.ui.utils.NumericalEncodingUtils;
import com.google.android.gms.maps.model.LatLng;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Helper class to maintain the live state of a workout session.
 * It tracks running statistics (Min, Max, Avg) and samples points for map and elevation streams.
 */
public class LiveWorkoutSession {
    private final long workoutId;
    private final Map<SensorType, RunningStats> sensorStats = new HashMap<>();
    private final Set<SensorType> sensorsForAverage;
    
    private int stepCounter = 0;
    private final List<LatLng> sampledLatLngs = new ArrayList<>();
    private final List<Double> sampledAltitudes = new ArrayList<>();
    private final List<Double> sampledDistances = new ArrayList<>();

    private long lastLatE5 = 0;
    private long lastLngE5 = 0;
    private long lastAltE2 = 0;
    private long lastDistE2 = 0;

    private LatLng startLatLng = null;
    private LatLng lastLatLng = null;

    public LiveWorkoutSession(long workoutId, Set<SensorType> sensorsForAverage) {
        this.workoutId = workoutId;
        this.sensorsForAverage = sensorsForAverage;
    }

    public int addSample(SensorType type, double value, LatLng position) {

        if (position != null) {
            if (startLatLng == null) startLatLng = position;
            lastLatLng = position;
        }

        RunningStats stats = sensorStats.get(type);
        if (stats == null) {
            stats = new RunningStats();
            sensorStats.put(type, stats);
        }
        return stats.addValue(value, position, sensorsForAverage != null && sensorsForAverage.contains(type));
    }

    public LatLng getStartLatLng() {
        return startLatLng;
    }

    public LatLng getLastLatLng() {
        return lastLatLng;
    }

    /**
     * Records a point for the map and elevation streams if the step interval is reached.
     * @return the incremental encoded strings if a point was added, null otherwise.
     */
    public StreamIncrement recordStreamPoint(LatLng latLng, Double altitude, Double distance) {
        stepCounter++;
        if (stepCounter >= WorkoutSummaries.ENCODING_STEP_SIZE) {
            stepCounter = 0;
            StreamIncrement increment = new StreamIncrement();

            if (latLng != null) {
                sampledLatLngs.add(latLng);
                increment.polylineIncrement = NumericalEncodingUtils.INSTANCE.encodeLatLng(latLng.latitude, latLng.longitude, lastLatE5, lastLngE5);
                lastLatE5 = Math.round(latLng.latitude * 1e5);
                lastLngE5 = Math.round(latLng.longitude * 1e5);
            }
            if (altitude != null) {
                sampledAltitudes.add(altitude);
                long currentAltE2 = Math.round(altitude * 100);
                StringBuilder sb = new StringBuilder();
                NumericalEncodingUtils.INSTANCE.encodeSingle(currentAltE2 - lastAltE2, sb);
                increment.altitudeIncrement = sb.toString();
                lastAltE2 = currentAltE2;
            }
            if (distance != null) {
                sampledDistances.add(distance);
                long currentDistE2 = Math.round(distance * 100);
                StringBuilder sb = new StringBuilder();
                NumericalEncodingUtils.INSTANCE.encodeSingle(currentDistE2 - lastDistE2, sb);
                increment.distanceIncrement = sb.toString();
                lastDistE2 = currentDistE2;
            }
            return increment;
        }
        return null;
    }

    public static class StreamIncrement {
        public String polylineIncrement = "";
        public String altitudeIncrement = "";
        public String distanceIncrement = "";

        public boolean hasData() {
            return !polylineIncrement.isEmpty() || !altitudeIncrement.isEmpty() || !distanceIncrement.isEmpty();
        }
    }

    public long getWorkoutId() {
        return workoutId;
    }

    public List<LatLng> getSampledLatLngs() {
        return sampledLatLngs;
    }

    public List<Double> getSampledAltitudes() {
        return sampledAltitudes;
    }

    public List<Double> getSampledDistances() {
        return sampledDistances;
    }

    public Map<SensorType, RunningStats> getSensorStats() {
        return Collections.unmodifiableMap(sensorStats);
    }

    /**
     * ATT-38: Shifts all altitude-related data by the given offset.
     * This is used when a barometric altitude correction is triggered mid-workout.
     */
    public void applyAltitudeCorrection(double offset) {
        // 1. Shift RunningStats
        RunningStats altStats = sensorStats.get(SensorType.ALTITUDE);
        if (altStats != null) {
            if (altStats.min != Double.MAX_VALUE) altStats.min += offset;
            if (altStats.max != -Double.MAX_VALUE) altStats.max += offset;
            altStats.sum += (offset * altStats.count);
        }

        // 2. Shift Sampled Altitudes
        for (int i = 0; i < sampledAltitudes.size(); i++) {
            sampledAltitudes.set(i, sampledAltitudes.get(i) + offset);
        }

        // 3. Update Anchor for next delta encoding
        lastAltE2 += Math.round(offset * 100);
    }

    /**
     * Inner class to track running stats for a single sensor.
     */
    public static class RunningStats {
        public static final int CHANGED_MIN = 1;
        public static final int CHANGED_MAX = 2;
        public static final int CHANGED_AVG = 4;

        public double min = Double.MAX_VALUE;
        public LatLng minPos = null;
        public double max = -Double.MAX_VALUE;
        public LatLng maxPos = null;
        public double sum = 0;
        public int count = 0;

        public int addValue(double value, LatLng position, boolean calcAverage) {
            int changed = 0;
            if (value < min) {
                min = value;
                minPos = position;
                changed |= CHANGED_MIN;
            }
            if (value > max) {
                max = value;
                maxPos = position;
                changed |= CHANGED_MAX;
            }
            if (calcAverage) {
                sum += value;
                count++;
                changed |= CHANGED_AVG;
            }
            return changed;
        }

        public double getAverage() {
            return count > 0 ? sum / count : 0;
        }
    }
}
