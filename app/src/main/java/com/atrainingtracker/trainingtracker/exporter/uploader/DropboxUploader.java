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

package com.atrainingtracker.trainingtracker.exporter.uploader;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.atrainingtracker.BuildConfig;
import com.atrainingtracker.banalservice.devices.raw.RawSensorSidecarFiles;
import com.atrainingtracker.trainingtracker.TrainingApplication;
import com.atrainingtracker.trainingtracker.exporter.BaseExporter;
import com.atrainingtracker.trainingtracker.exporter.ExportInfo;
import com.atrainingtracker.trainingtracker.exporter.FileFormat;
import com.dropbox.core.DbxException;
import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.oauth.DbxCredential;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.WriteMode;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;

public class DropboxUploader extends BaseExporter {
    private static final String TAG = "DropboxUploader";
    private static final boolean DEBUG = TrainingApplication.getDebug(true);

    public DropboxUploader(@NonNull Context context) {
        super(context);
    }

    protected DbxClientV2 createDbxClient(@NonNull DbxCredential credential) {
        return new DbxClientV2(new DbxRequestConfig(BuildConfig.DROPBOX_APP_KEY), credential);
    }

    @NonNull
    @Override
    protected ExportResult doExport(@NonNull ExportInfo exportInfo) throws IOException, IllegalArgumentException {
        final List<File> filesToUpload;
        if (exportInfo.getFileFormat() == FileFormat.RAW_SENSOR) {
            filesToUpload = RawSensorSidecarFiles.resolveExportableSidecars(mContext, exportInfo.getFileBaseName());
            if (filesToUpload.isEmpty()) {
                Log.i(TAG, "Raw sensor sidecar does not exist for " + exportInfo.getFileBaseName() + " (graceful skip)");
                return new ExportResult(true, false, "Raw sensor sidecar does not exist (graceful skip)");
            }
        } else {
            String filename = exportInfo.getShortPath();
            File file = new File(getBaseDirFile(mContext), filename);
            if (!file.exists()) {
                return new ExportResult(false, false, "Dropbox file does not exist: " + file);
            }
            filesToUpload = Collections.singletonList(file);
        }

        DbxCredential credential = TrainingApplication.readDropboxCredential();
        if (credential == null) {
            return new ExportResult(false, false, "Dropbox credential is null or not linked");
        }

        DbxClientV2 dbxClientV2 = createDbxClient(credential);
        String lastUploadedPath = exportInfo.getShortPath();
        for (File file : filesToUpload) {
            String remotePath = exportInfo.getFileFormat() == FileFormat.RAW_SENSOR
                    ? RawSensorSidecarFiles.shortPathFor(file)
                    : exportInfo.getShortPath();
            try (InputStream inputStream = new FileInputStream(file)) {
                dbxClientV2.files().uploadBuilder("/" + remotePath)
                        .withMode(WriteMode.OVERWRITE)
                        .uploadAndFinish(inputStream);
                lastUploadedPath = remotePath;
            } catch (DbxException e) {
                Log.e(TAG, "DropboxException: " + e.getMessage(), e);
                boolean shallRetry = isTransientDbxException(e);
                return new ExportResult(false, shallRetry, "DropboxException: " + e.getMessage());
            } catch (IOException e) {
                Log.e(TAG, "IOException: " + e.getMessage(), e);
                return new ExportResult(false, true, "IOException: " + e.getMessage());
            }
        }

        TrainingApplication.setDropboxLastSyncTimestamp(System.currentTimeMillis());
        if (DEBUG) Log.i(TAG, "successfully uploaded " + lastUploadedPath + " to Dropbox");
        return new ExportResult(true, false, "successfully uploaded " + lastUploadedPath + " to Dropbox");
    }

    private boolean isTransientDbxException(DbxException e) {
        return e instanceof com.dropbox.core.NetworkIOException
                || e instanceof com.dropbox.core.RetryException
                || e instanceof com.dropbox.core.ServerException
                || e instanceof com.dropbox.core.RateLimitException;
    }

}
