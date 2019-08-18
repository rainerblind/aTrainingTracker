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

public enum ExtremaType {
    MAX(R.string.max),
    MIN(R.string.min),
    AVG(R.string.average),
    START(R.string.start),
    MAX_LINE_DISTANCE(R.string.max_line_distance),
    END(R.string.end);

    public static final ExtremaType[] LOCATION_EXTREMA_TYPES = new ExtremaType[]{START, MAX_LINE_DISTANCE, END};
    private final int nameId;

    ExtremaType(int nameId) {
        this.nameId = nameId;
    }

    public static List<String> getLocationNameList() {
        List<String> result = new LinkedList<>();
        for (ExtremaType extremaType : LOCATION_EXTREMA_TYPES) {
            result.add(extremaType.toString());
        }

        return result;
    }

    @Override
    public String toString() {
        return TrainingApplication.getAppContext().getString(nameId);
    }
}
