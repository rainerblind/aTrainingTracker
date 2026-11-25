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
    //             Dir              ending                UI name                         derivedCache
    CSV("CSV", ".csv", R.string.CSV, true),//                 new CSVFileExporter(),           null),
    GC("GC", ".json", R.string.GC, true),// new GCFileExporter(),            null),
    TCX("TCX", ".tcx", R.string.TCX, true),//                 new TCXFileExporter(),           null),
    GPX("GPX", ".gpx", R.string.GPX, true),//                 new GPXFileExporter(),           null),
    FIT("FIT", ".fit", R.string.FIT, true),
    RAW_SENSOR("raw_sensor", ".polar_raw.csv.gz", R.string.file_format_raw_sensor, false),

    // not the best solution but should work.
    STRAVA("Strava", ".fit", R.string.Strava, true),
    HEALTH_CONNECT("HealthConnect", "", R.string.health_connect_title, false);

    public static final FileFormat[] STANDARD_FILE_FORMATS = new FileFormat[]{CSV, GC, TCX, GPX, FIT, RAW_SENSOR};
    public static final FileFormat[] ONLINE_COMMUNITIES = new FileFormat[]{STRAVA, HEALTH_CONNECT};

    private final String mDirName;
    private final String mFileEnding;
    private final int mUiNameId;
    private final boolean mIsDerivedExportFile;
    // private final BaseExporter mFileExporter;
    // private final BaseExporter mUploader;

    FileFormat(String dirName, String fileEnding, int uiNameId, boolean isDerivedExportFile)//, BaseExporter fileExporter, BaseExporter uploader)
    {
        mDirName = dirName;
        mFileEnding = fileEnding;
        mUiNameId = uiNameId;
        mIsDerivedExportFile = isDerivedExportFile;
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
     * Returns {@code true} if this format writes a disposable local cache file derived from SQLite
     * that can be safely deleted and regenerated during export invalidation (REQ-EXP-048.1).
     * Returns {@code false} for irreplaceable primary-capture archives (such as {@link #RAW_SENSOR})
     * and API-only targets with no local cache file (such as {@link #HEALTH_CONNECT}).
     */
    public boolean isDerivedExportFile() {
        return mIsDerivedExportFile;
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
                return "application/vnd.garmin.tcx+xml";
            case FIT:
            case STRAVA:
                return "application/vnd.ant.fit";
            case CSV:
                return "text/csv";
            case GC:
                return "application/json";
            case RAW_SENSOR:
                return "application/gzip";
            default:
                return "application/octet-stream";
        }
    }

    // public BaseExporter getFileExporter() { return mFileExporter; }
    // public BaseExporter getUploader() { return mUploader; }
}

