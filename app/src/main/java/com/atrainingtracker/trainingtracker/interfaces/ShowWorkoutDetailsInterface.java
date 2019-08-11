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
