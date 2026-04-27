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

import android.content.Context;
import android.content.Intent;

public class ExportStatusChangedBroadcaster {

    public static final String EXPORT_STATUS_CHANGED_INTENT = "com.trainingtracker.EXPORT_STATUS_CHANGED_INTENT";
    public static final String EXTRA_FILE_BASE_NAME = "extra_file_base_name";

    public static void broadcastExportStatusChanged(Context context, String fileBaseName) {
        Intent intent = new Intent(EXPORT_STATUS_CHANGED_INTENT)
                .setPackage(context.getPackageName());

        if (fileBaseName != null) {
            intent.putExtra(EXTRA_FILE_BASE_NAME, fileBaseName);
        }

        context.sendBroadcast(intent);
    }
}
