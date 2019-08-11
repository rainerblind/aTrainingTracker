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
