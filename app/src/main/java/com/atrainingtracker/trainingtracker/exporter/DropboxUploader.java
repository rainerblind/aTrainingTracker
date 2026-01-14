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

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.atrainingtracker.BuildConfig;
import com.atrainingtracker.trainingtracker.TrainingApplication;
import com.dropbox.core.DbxException;
import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.WriteMode;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

public class DropboxUploader extends BaseExporter {
    private static final String TAG = "DropboxUploader";
    private static final boolean DEBUG = TrainingApplication.getDebug(true);

    public DropboxUploader(@NonNull Context context) {
        super(context);
    }

    @NonNull
    @Override
    protected ExportResult doExport(@NonNull ExportInfo exportInfo, @NonNull IExportProgressListener progressListener) throws IOException, IllegalArgumentException {
        String filename = exportInfo.getShortPath();
        File file = new File(getBaseDirFile(mContext), filename);
        if (!file.exists()) {
            return new ExportResult(false, false,"Dropbox file does not exist: " + file);
        }

        try (InputStream inputStream = new FileInputStream(file)) {
            DbxClientV2 dbxClientV2 = new DbxClientV2(new DbxRequestConfig(BuildConfig.DROPBOX_APP_KEY), TrainingApplication.readDropboxCredential());
            dbxClientV2.files().uploadBuilder("/" + filename)
                    .withMode(WriteMode.OVERWRITE)
                    .uploadAndFinish(inputStream);
        } catch (DbxException e) {
            Log.e(TAG, "DropboxException: " + e.getMessage(), e);
            return new ExportResult(false, false, "DropboxException: " + e.getMessage());
        } catch (IOException e) {
            Log.e(TAG, "IOException: " + e.getMessage(), e);
            return new ExportResult(false, false, "IOException: " + e.getMessage());
        }


        if (DEBUG) Log.i(TAG, "successfully uploaded " + filename + " to Dropbox");
        return new ExportResult(true, false, "successfully uploaded " + filename + " to Dropbox");
    }

    @NonNull
    @Override
    protected Action getAction() {
        return Action.UPLOAD;
    }

}
