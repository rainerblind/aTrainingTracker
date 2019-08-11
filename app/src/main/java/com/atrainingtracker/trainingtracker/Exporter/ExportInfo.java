package com.atrainingtracker.trainingtracker.Exporter;

public class ExportInfo {
    protected String mFileBaseName;
    protected FileFormat mFileFormat;
    protected ExportType mExportType;

    public ExportInfo(String fileBaseName, FileFormat fileFormat, ExportType exportType) {
        mFileBaseName = fileBaseName;
        mFileFormat = fileFormat;
        mExportType = exportType;
    }

    public String getFileBaseName() {
        return mFileBaseName;
    }

    public FileFormat getFileFormat() {
        return mFileFormat;
    }

    public ExportType getExportType() {
        return mExportType;
    }

    public String toString() {
        return mExportType.name() + ": " + mFileFormat.name() + ": " + mFileBaseName;
    }
}