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

