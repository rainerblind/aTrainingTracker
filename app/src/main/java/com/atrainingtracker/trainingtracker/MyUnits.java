package com.atrainingtracker.trainingtracker;

import com.atrainingtracker.R;

public enum MyUnits {
    METRIC(R.string.units_type_metric),
    IMPERIAL(R.string.units_type_imperial);

    private int nameId;

    MyUnits(int nameId) {
        this.nameId = nameId;
    }

    public int getNameId() {
        return nameId;
    }

    @Override
    public String toString() {
        return TrainingApplication.getAppContext().getString(nameId);
    }
}
