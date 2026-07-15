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

package com.atrainingtracker.trainingtracker.database;

import androidx.annotation.NonNull;

import com.atrainingtracker.R;
import com.atrainingtracker.trainingtracker.TrainingApplication;


public enum ExtremaType {
    MAX(R.string.max),
    MIN(R.string.min),
    AVG(R.string.average),
    START(R.string.start),
    MAX_LINE_DISTANCE(R.string.max_line_distance),
    END(R.string.end);

    private final int nameId;

    ExtremaType(int nameId) {
        this.nameId = nameId;
    }

    @NonNull
    @Override
    public String toString() {
        return TrainingApplication.getAppContext().getString(nameId);
    }
}
