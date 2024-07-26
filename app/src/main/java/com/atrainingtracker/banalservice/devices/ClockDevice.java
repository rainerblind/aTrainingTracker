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

package com.atrainingtracker.banalservice.devices;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.atrainingtracker.banalservice.BANALService;
import com.atrainingtracker.banalservice.sensor.MyIntegerAccumulatorSensor;
import com.atrainingtracker.banalservice.sensor.MySensor;
import com.atrainingtracker.banalservice.sensor.MySensorManager;
import com.atrainingtracker.banalservice.sensor.SensorType;
import com.atrainingtracker.banalservice.sensor.formater.TimeFormatter;
import com.atrainingtracker.trainingtracker.TrainingApplication;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Timer;
import java.util.TimerTask;

public class ClockDevice extends MyDevice {
    private static final String TAG = "ClockDevice";
    private static final boolean DEBUG = BANALService.getDebug(false);

    // protected final IntentFilter mStartTimerFilter = new IntentFilter(BANALService.START_TIMER_INTENT);
    // protected final IntentFilter mStopTimerFilter  = new IntentFilter(BANALService.STOP_TIMER_INTENT);

    // protected int mTotalTime, mActiveTime, mLapTime;
    // protected int mLapNr = BANALService.INIT_LAP_NR;

    protected MyIntegerAccumulatorSensor mLapSensor;
    protected MyIntegerAccumulatorSensor mActiveTimeSensor_s;
    protected MyIntegerAccumulatorSensor mTotalTimeSensor_s;
    protected MyIntegerAccumulatorSensor mLapTimeSensor_s;
    protected MySensor<String> mTimeOfDaySensor;

    protected int mTotalTime, mActiveTime, mLapTime;
    protected int mLaps = BANALService.INIT_LAP_NR;

    private Timer timer;
    private boolean timerRunning = false;
    private final DateFormat df = SimpleDateFormat.getTimeInstance();

    public ClockDevice(Context context, MySensorManager mySensorManager) {
        super(context, mySensorManager, DeviceType.CLOCK);
        if (DEBUG) Log.i(TAG, "begin of constructor: mLaps=" + mLaps);

        // ContextCompat.registerReceiver(context, mStartTimerReceiver, mStartTimerFilter, ContextCompat.RECEIVER_NOT_EXPORTED);
        // ContextCompat.registerReceiver(context, mStopTimerReceiver, mStopTimerFilter, ContextCompat.RECEIVER_NOT_EXPORTED);

        // final SimpleDateFormat seconds2StringFormat = new SimpleDateFormat("HH:mm:ss");
        // seconds2StringFormat.getTimeZone().setRawOffset(0);
        TimeFormatter timeFormatter = new TimeFormatter();

        registerSensors();
        startTimer();
        if (DEBUG) Log.i(TAG, "end of constructor: mLaps=" + mLaps);
    }

    @Override
    public String getName() {
        // return mContext.getString(R.string.ClockDevice_name);
        return null;
    }

    @Override
    protected void addSensors() {
        mLapSensor = new MyIntegerAccumulatorSensor(this, SensorType.LAP_NR, BANALService.INIT_LAP_NR);
        mActiveTimeSensor_s = new MyIntegerAccumulatorSensor(this, SensorType.TIME_ACTIVE);
        mTotalTimeSensor_s = new MyIntegerAccumulatorSensor(this, SensorType.TIME_TOTAL);
        mLapTimeSensor_s = new MyIntegerAccumulatorSensor(this, SensorType.TIME_LAP);
        mTimeOfDaySensor = new MySensor(this, SensorType.TIME_OF_DAY);

        addSensor(mLapSensor);
        addSensor(mActiveTimeSensor_s);
        addSensor(mTotalTimeSensor_s);
        addSensor(mLapTimeSensor_s);
        addSensor(mTimeOfDaySensor);
    }

    // private final BroadcastReceiver mStartTimerReceiver = new BroadcastReceiver()
    // {
    //	public void onReceive(Context context, Intent intent)
    //	{
    //		startTimer();
    //	}
    // };

    // private final BroadcastReceiver mStopTimerReceiver = new BroadcastReceiver()
    // {
    // 	public void onReceive(Context context, Intent intent)
    // 	{
    // 		stopTimer();
    // 	}
    // };

    private synchronized void startTimer() {
        mLapSensor.newValue(mLaps);  // TODO: is this really the right place?

        // timer might be already running?
        if (!timerRunning) {
            timer = new Timer();
            timer.scheduleAtFixedRate(new ClockTimeTask(), 0, 1000);
            timerRunning = true;
        }
    }

    private synchronized void stopTimer() {
        timer.cancel();
        timer.purge();
        timer = null;
        timerRunning = false;
    }


    @Override
    public void shutDown() {
        // Log.d(TAG, "shutDown");
        super.shutDown();

        if (timer != null) {
            stopTimer();
        }

        // mContext.unregisterReceiver(mStartTimerReceiver);
        // mContext.unregisterReceiver(mStopTimerReceiver);
    }

    @Override
    public void newLap() {
        if (DEBUG) Log.i(TAG, "newLap: increment " + mLaps + " to " + (mLaps + 1));

        mLaps++;

        mLapSensor.newValue(mLaps);

        mLapTimeSensor_s.reset();
    }

    private class ClockTimeTask extends TimerTask {
        @Override
        public void run() {
            mTotalTime++;
            mTotalTimeSensor_s.newValue(mTotalTime);
            // mLapSensor.newValue(mLapNr);

            if (DEBUG) Log.i(TAG, "in TimerTask: mLaps=" + mLaps);

            if (!TrainingApplication.isPaused()) {
                // Log.d(TAG, "run started");
                mActiveTime++;
                mActiveTimeSensor_s.newValue(mActiveTime);

                mLapTime++;
                mLapTimeSensor_s.newValue(mLapTime);
            } else {
                if (DEBUG) Log.i(TAG, "paused");
                // Log.d(TAG, "run not started");
            }

            mTimeOfDaySensor.newValue(df.format(Calendar.getInstance().getTime()));

            // send broadcast
            if (DEBUG) Log.d(TAG, "sending new time event broadcast");
            mContext.sendBroadcast(new Intent(BANALService.NEW_TIME_EVENT_INTENT)
                    .setPackage(mContext.getPackageName()));
        }
    }

//    private final TimerTask timerClock = new TimerTask()
//    {   
//        @Override
//        public void run() 
//        {
//            mTotalTime++;
//            mTotalTimeSensor_s.newValue(mTotalTime + 0.0);
//            mLapSensor.newValue(mLapNr);
//            
//            if (!cPaused) {
//                // Log.d(TAG, "run started");
//                mActiveTime++;
//                mActiveTimeSensor_s.newValue(mActiveTime + 0.0);
//                
//                mLapTime++;
//                mLapTimeSensor_s.newValue(mLapTime + 0.0);
//            }
//            else {
//                // Log.d(TAG, "run not started");
//            }
//            
//            // send broadcast
//            if (DEBUG) Log.d(TAG,  "sending new time event broadcast");
//            mContext.sendBroadcast(new Intent(BANALService.NEW_TIME_EVENT_INTENT).setPackage(getPackageName()));
//        }
//    };

}
