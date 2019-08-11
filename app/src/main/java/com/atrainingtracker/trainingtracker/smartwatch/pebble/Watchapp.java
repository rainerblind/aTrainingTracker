package com.atrainingtracker.trainingtracker.smartwatch.pebble;

import com.atrainingtracker.R;

public enum Watchapp {
    BUILD_IN(R.string.build_in),
    TRAINING_TRACKER(R.string.TrainingTracker);

    private int uiId;

    Watchapp(int uiId) {
        this.uiId = uiId;
    }

    public int getUiId() {
        return uiId;
    }

}
