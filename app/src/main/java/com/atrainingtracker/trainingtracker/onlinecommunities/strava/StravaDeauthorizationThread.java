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

package com.atrainingtracker.trainingtracker.onlinecommunities.strava;

import android.app.ProgressDialog;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import com.atrainingtracker.R;

public class StravaDeauthorizationThread extends Thread {

    private static final String TAG = "StravaDeauthorizationThread";
    private static final boolean DEBUG = false;

    @NonNull
    private final ProgressDialog progressDialog;
    private final Context mContext;

    public StravaDeauthorizationThread(Context context) {
        progressDialog = new ProgressDialog(context);
        mContext = context;
    }

    @Override
    public void run() {
        new Handler(Looper.getMainLooper()).post(() -> {
            progressDialog.setMessage(mContext.getString(R.string.deauthorization));
            if (!progressDialog.isShowing()) {
                progressDialog.show();
            }
        });

        StravaDataPurgeManager.INSTANCE.purgeAllStravaData(mContext, true, () -> {
            new Handler(Looper.getMainLooper()).post(() -> {
                if (progressDialog.isShowing()) {
                    progressDialog.dismiss();
                }
            });
        });
    }
}
