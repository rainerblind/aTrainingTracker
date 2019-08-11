package com.atrainingtracker.trainingtracker.Exporter;

import com.atrainingtracker.R;
import com.atrainingtracker.trainingtracker.TrainingApplication;

public enum ExportType {
    FILE(R.string.SD_card, FileFormat.values()),
    DROPBOX(R.string.Dropbox, new FileFormat[]{FileFormat.CSV, FileFormat.GC, FileFormat.GPX, FileFormat.TCX}),
    COMMUNITY(R.string.Community, new FileFormat[]{FileFormat.RUNKEEPER, FileFormat.STRAVA, FileFormat.TRAINING_PEAKS});

    private int uiId;
    private FileFormat[] exportToFileFormats;

    ExportType(int uiId, FileFormat[] exportToFileFormats) {
        this.uiId = uiId;
        this.exportToFileFormats = exportToFileFormats;
    }

    @Override
    public String toString() {
        return TrainingApplication.getAppContext().getString(uiId);
    }

    public int getUiId() {
        return uiId;
    }

    public FileFormat[] getExportToFileFormats() {
        return exportToFileFormats;
    }
}
