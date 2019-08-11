package com.atrainingtracker.trainingtracker.interfaces;

/**
 * Created by rainer on 19.01.16.
 */
public interface ReallyDeleteDialogInterface {
    void confirmDeleteWorkout(long workoutId);

    void reallyDeleteWorkout(long workoutId);
}
