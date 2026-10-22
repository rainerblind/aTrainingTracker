/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
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

public enum FileFormat {
    //             Dir              ending   UI name                FileExporter                     Uploader
    CSV("CSV", ".csv", R.string.CSV),//                 new CSVFileExporter(),           null),
    GC("GC", ".json", R.string.GC),// new GCFileExporter(),            null),
    TCX("TCX", ".tcx", R.string.TCX),//                 new TCXFileExporter(),           null),
    GPX("GPX", ".gpx", R.string.GPX),//                 new GPXFileExporter(),           null),
    FIT("FIT", ".fit", R.string.FIT),

    // not the best solution but should work.
    STRAVA("Strava", ".tcx", R.string.Strava);

    public static final FileFormat[] STANDARD_FILE_FORMATS = new FileFormat[]{CSV, GC, TCX, GPX, FIT};
    public static final FileFormat[] ONLINE_COMMUNITIES = new FileFormat[]{STRAVA};

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

    /**
     * Returns the standardized MIME type for MediaStore public Downloads insertion
     * and external Intent sharing (REQ-EXP-022, ATT-4013).
     *
     * @return the MIME type string matching the file format standard
     */
    public String getMimeType() {
        switch (this) {
            case GPX:
                return "application/gpx+xml";
            case TCX:
            case STRAVA:
                return "application/vnd.garmin.tcx+xml";
            case FIT:
                return "application/vnd.ant.fit";
            case CSV:
                return "text/csv";
            case GC:
                return "application/json";
            default:
                return "application/octet-stream";
        }
    }

    // public BaseExporter getFileExporter() { return mFileExporter; }
    // public BaseExporter getUploader() { return mUploader; }
}

