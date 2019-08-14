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

import android.content.Context;
import android.util.Log;

import com.atrainingtracker.trainingtracker.TrainingApplication;
import com.dropbox.core.DbxException;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.WriteMode;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

public class DropboxUploader extends BaseExporter {
    private static final String TAG = "DropboxUploader";
    private static final boolean DEBUG = TrainingApplication.DEBUG && false;

    public DropboxUploader(Context context) {
        super(context);
    }

    @Override
    protected ExportResult doExport(ExportInfo exportInfo) throws IOException, IllegalArgumentException {
        String filename = exportInfo.getFileFormat().getDirName() + "/" + exportInfo.getFileBaseName() + exportInfo.getFileFormat().getFileEnding();
        File file = new File(getBaseDir(), filename);


        InputStream inputStream = new FileInputStream(file);
        try {
            String dropboxToken = TrainingApplication.getDropboxToken();                            // TODO: provoke this error.  Unfortunately, the errors will not appear in the app.  Currently, only the string something_strange_happend appears...
            if (dropboxToken == null) {
                Log.e(TAG, "WTF: shall upload to dropbox but there is no token...");
                return new ExportResult(false, "No valid Dropbox token.");
            }
            DropboxClientFactory.init(dropboxToken);
            DbxClientV2 dbxClient = DropboxClientFactory.getClient();

            dbxClient.files().uploadBuilder("/" + filename)
                    .withMode(WriteMode.OVERWRITE)
                    .uploadAndFinish(inputStream);
        } catch (DbxException e) {
            Log.e(TAG, "DropboxException: " + e.getMessage(), e);
            return new ExportResult(false, "DropboxException: " + e.getMessage());
        } catch (IOException e) {
            Log.e(TAG, "IOException: " + e.getMessage(), e);
            return new ExportResult(false, "IOException: " + e.getMessage());

        } finally {
            if (inputStream != null) inputStream.close();
        }


        if (DEBUG) Log.i(TAG, "successfully uploaded " + filename + " to Dropbox");
        return new ExportResult(true, "successfully uploaded " + filename + " to Dropbox");
    }

    @Override
    protected Action getAction() {
        return Action.UPLOAD;
    }

}
