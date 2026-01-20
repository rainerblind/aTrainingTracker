package com.atrainingtracker.trainingtracker.exporter;

import android.content.Context;
import android.content.Intent;

public class ExportStatusChangedBroadcaster {

    public static final String EXPORT_STATUS_CHANGED_INTENT = "com.trainingtracker.EXPORT_STATUS_CHANGED_INTENT";

    public static void broadcastExportStatusChanged(Context context) {
        context.sendBroadcast(new Intent(EXPORT_STATUS_CHANGED_INTENT)
                .setPackage(context.getPackageName()));
    }
}
