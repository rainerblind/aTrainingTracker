package com.atrainingtracker.banalservice;

import android.os.Environment;
import android.util.Log;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MyBanalDebugHelper {
    protected static final String TAG = "MyBanalDebugHelper";
    static final boolean DEBUG = false;
    static String dateString;
    static BufferedWriter bufferedWriter;

    public MyBanalDebugHelper() {
        if (DEBUG && dateString == null) {
            try {
                dateString = (new SimpleDateFormat("yyyy-MM-dd_HHmm")).format(new Date());

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
                bufferedWriter.append((new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")).format(new Date()) + ": " + tag + ", " + text + "\n");
                bufferedWriter.flush();
            } catch (IOException e) {
                Log.e(TAG, "IOException in MyBanalDebugHelper()");
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }
}
