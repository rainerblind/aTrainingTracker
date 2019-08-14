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

package com.atrainingtracker.trainingtracker.database;

import com.atrainingtracker.R;
import com.atrainingtracker.trainingtracker.TrainingApplication;

import java.util.LinkedList;
import java.util.List;

public enum ExtremumType {
    MAX(R.string.max),
    MIN(R.string.min),
    AVG(R.string.average),
    START(R.string.start),
    MAX_LINE_DISTANCE(R.string.max_line_distance),
    END(R.string.end);

    public static final ExtremumType[] LOCATION_EXTREMUM_TYPES = new ExtremumType[]{START, MAX_LINE_DISTANCE, END};
    private final int nameId;

    ExtremumType(int nameId) {
        this.nameId = nameId;
    }

    public static List<String> getLocationNameList() {
        List<String> result = new LinkedList<>();
        for (ExtremumType extremumType : LOCATION_EXTREMUM_TYPES) {
            result.add(extremumType.toString());
        }

        return result;
    }

    @Override
    public String toString() {
        return TrainingApplication.getAppContext().getString(nameId);
    }
}
