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

package com.atrainingtracker.trainingtracker.Exporter;

import com.atrainingtracker.R;

public enum FileFormat {
    //             Dir              ending   UI name                FileExporter                     Uploder
    CSV("CSV", ".csv", R.string.CSV),//                 new CSVFileExporter(),           null),
    GC("GC", ".json", R.string.GC),// new GCFileExporter(),            null),
    TCX("TCX", ".tcx", R.string.TCX),//                 new TCXFileExporter(),           null),
    GPX("GPX", ".gpx", R.string.GPX),//                 new GPXFileExporter(),           null),

    // not the best solution but should work.
    STRAVA("Strava", ".tcx", R.string.Strava),//              new TCXFileExporter(),           new StravaUploader()),
    RUNKEEPER("RunKeeper", ".json", R.string.Runkeeper),//           new RunkeeperFileExporter(),     new RunkeeperUploader()),
    TRAINING_PEAKS("TrainingPeaks", ".tcx", R.string.TrainingPeaks);//       new TrainingPeaksFileExporter(), new TrainingPeaksUploader());
    // TRAINING_PEAKS("TrainingPeaks", ".pwx",  "TrainingPeaks");//       new TrainingPeaksFileExporter(), new TrainingPeaksUploader());

    public static FileFormat[] STANDARD_FILE_FORMATS = new FileFormat[]{CSV, GC, TCX, GPX};
    public static FileFormat[] ONLINE_COMMUNITIES = new FileFormat[]{STRAVA, RUNKEEPER, TRAINING_PEAKS};

    private final String mDirName;
    private final String mFileEnding;
    private final int mUiNameId;
    // private final BaseExporter mFileExporter;
    // private final BaseExporter mUploader;

    FileFormat(String dirName, String fileEnding, int uiNameId)//, BaseExporter fileExporter, BaseExporter uploader)
    {
        mDirName = dirName;
        mFileEnding = fileEnding;
        mUiNameId = uiNameId;
        // mFileExporter = fileExporter;
        // mUploader     = uploader;
    }

    public String getDirName() {
        return mDirName;
    }

    public String getFileEnding() {
        return mFileEnding;
    }

    public int getUiNameId() {
        return mUiNameId;
    }

    // public BaseExporter getFileExporter() { return mFileExporter; }
    // public BaseExporter getUploader() { return mUploader; }
}

