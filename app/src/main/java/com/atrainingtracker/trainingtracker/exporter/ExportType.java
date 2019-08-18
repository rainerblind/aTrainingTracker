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

package com.atrainingtracker.trainingtracker.exporter;

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
