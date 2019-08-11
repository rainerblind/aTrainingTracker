package com.atrainingtracker.trainingtracker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;

import com.atrainingtracker.trainingtracker.Exporter.ExportWorkoutIntentService;


public class NetworkReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        boolean isNetworkDown = intent.getBooleanExtra(ConnectivityManager.EXTRA_NO_CONNECTIVITY, false);

        if (!isNetworkDown) {
            context.startService(new Intent(context, ExportWorkoutIntentService.class));
        }

    }
}
