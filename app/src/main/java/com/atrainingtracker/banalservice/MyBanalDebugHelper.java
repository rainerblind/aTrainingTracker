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

package com.atrainingtracker.banalservice;

import android.os.Environment;
import android.util.Log;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MyBanalDebugHelper {
    protected static final String TAG = "MyBanalDebugHelper";
    static final boolean DEBUG = false;
    static String dateString;
    static BufferedWriter bufferedWriter;

    public MyBanalDebugHelper() {
        if (DEBUG && dateString == null) {
            try {
                dateString = (new SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US)).format(new Date());

                // TODO: copied from TrainingTracker -> BaseExporter
                File sdCard = Environment.getExternalStorageDirectory();
                File dir = new File(sdCard.getAbsolutePath() + "/Workouts/Debug/banalService");
                dir.mkdirs();

                File file = new File(dir, dateString + ".txt");
                file.createNewFile();
                bufferedWriter = new BufferedWriter(new FileWriter(file));
            } catch (IOException e) {
                if (DEBUG) Log.d(TAG, "IOException in MyBanalDebugHelper()");
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

        }
    }

    public void log(String tag, String text) {
        if (DEBUG) {
            try {
                bufferedWriter.append((new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)).format(new Date()) + ": " + tag + ", " + text + "\n");
                bufferedWriter.flush();
            } catch (IOException e) {
                Log.e(TAG, "IOException in MyBanalDebugHelper()");
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }
}
