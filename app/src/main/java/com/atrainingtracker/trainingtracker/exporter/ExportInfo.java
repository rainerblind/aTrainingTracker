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

import java.io.File;

public class ExportInfo {
    protected final String mFileBaseName;
    protected final FileFormat mFileFormat;
    protected final ExportType mExportType;

    public ExportInfo(String fileBaseName, FileFormat fileFormat, ExportType exportType) {
        mFileBaseName = fileBaseName;
        mFileFormat = fileFormat;
        mExportType = exportType;
    }

    public String getFileBaseName() {
        return mFileBaseName;
    }

    public String getShortPath() {
        return mFileFormat.getDirName() + File.separator + getFileName();
    }

    public String getFileName() {
        return mFileBaseName + mFileFormat.getFileEnding();
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