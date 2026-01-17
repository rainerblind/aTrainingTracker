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

import androidx.annotation.NonNull;

import com.atrainingtracker.R;
import com.atrainingtracker.trainingtracker.TrainingApplication;

public enum ExportType {
    FILE(R.string.SD_card, FileFormat.values(), Action.WRITE),
    DROPBOX(R.string.Dropbox, new FileFormat[]{FileFormat.CSV, FileFormat.GC, FileFormat.GPX, FileFormat.TCX}, Action.UPLOAD),
    COMMUNITY(R.string.Community, new FileFormat[]{FileFormat.STRAVA, /* FileFormat.RUNKEEPER, FileFormat.TRAINING_PEAKS */}, Action.UPLOAD);

    private final int uiId;
    private final FileFormat[] exportToFileFormats;
    private final Action action;


    ExportType(int uiId, FileFormat[] exportToFileFormats, Action action) {
        this.uiId = uiId;
        this.exportToFileFormats = exportToFileFormats;
        this.action = action;
    }

    @NonNull
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

    public Action getAction() {
        return action;
    }

    public enum Action {

        UPLOAD(R.string.uploading, R.string.uploaded),
        WRITE(R.string.writing, R.string.wrote);

        private final int mIngId;
        private final int mPastId;

        Action(int ingId, int pastId) {
            mIngId = ingId;
            mPastId = pastId;
        }

        public int getIngId() {
            return mIngId;
        }

        public int getPastId() {
            return mPastId;
        }
    }
}
