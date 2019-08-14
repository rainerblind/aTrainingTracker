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

package com.atrainingtracker.trainingtracker.interfaces;

import com.atrainingtracker.trainingtracker.Exporter.FileFormat;

/**
 * Created by rainer on 19.01.16.
 */
public interface ShowWorkoutDetailsInterface {
    void exportWorkout(long id, FileFormat fileFormat);

    // implemented as static method in TrainingApplication
    // void startWorkoutDetailsActivity(long workoutId, WorkoutDetailsActivity.SelectedFragment selectedFragment);
    void showExportStatusDialog(long workoutId);
}
