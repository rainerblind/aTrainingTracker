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

package com.atrainingtracker.banalservice.devices.ant_plus;

import android.content.Context;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.Nullable;

import com.atrainingtracker.banalservice.BANALService;
import com.atrainingtracker.banalservice.devices.DeviceType;
import com.atrainingtracker.banalservice.devices.ProtocolType;
import com.atrainingtracker.banalservice.devices.RrBeatEvent;
import com.atrainingtracker.banalservice.devices.RrBeatListener;
import com.atrainingtracker.banalservice.devices.RrBeatSource;
import com.atrainingtracker.banalservice.sensor.MySensor;
import com.atrainingtracker.banalservice.sensor.MySensorManager;
import com.atrainingtracker.banalservice.sensor.SensorType;
import com.dsi.ant.plugins.antplus.pcc.AntPlusHeartRatePcc;
import com.dsi.ant.plugins.antplus.pcc.AntPlusHeartRatePcc.DataState;
import com.dsi.ant.plugins.antplus.pcc.AntPlusHeartRatePcc.IHeartRateDataReceiver;
import com.dsi.ant.plugins.antplus.pcc.defines.EventFlag;
import com.dsi.ant.plugins.antplus.pccbase.AntPluginPcc;
import com.dsi.ant.plugins.antplus.pccbase.PccReleaseHandle;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * ANT+ Heart Rate Device.
 * Parses computed heart rate and beat-to-beat RR intervals with strict beat-count continuity guard (REQ-CON-027, ATT-3540).
 */
public class ANTHeartRateDevice extends MyANTDevice implements RrBeatSource {
    private static final boolean DEBUG = BANALService.getDebug(false);
    private static final String TAG = "ANTHeartRateDevice";

    protected AntPlusHeartRatePcc hrPcc = null;
    protected MySensor<Number> mHeartRateSensor;
    protected MySensor<Integer> mRrIntervalSensor = null;

    private final List<RrBeatListener> mRrBeatListeners = new CopyOnWriteArrayList<>();
    private long mPrevHeartBeatCount = -1L;
    private BigDecimal mPrevHeartBeatEventTime = null;
    private double mLastBeatTimestampMs = 0.0;

    /**
     * Constructor
     */
    public ANTHeartRateDevice(Context context, MySensorManager mySensorManager, long deviceID, int antDeviceNumber) {
        super(context, mySensorManager, DeviceType.HRM, deviceID, antDeviceNumber);
        if (DEBUG) {
            Log.d(TAG, "creating HR device");
        }
    }

    @Override
    protected void addSensors() {
        mHeartRateSensor = new MySensor<>(this, SensorType.HR);
        addSensor(mHeartRateSensor);
    }

    public MySensor<Number> getHeartRateSensor() {
        return mHeartRateSensor;
    }

    @Nullable
    public MySensor<Integer> getRrIntervalSensor() {
        return mRrIntervalSensor;
    }

    @Override
    public void addRrBeatListener(RrBeatListener listener) {
        if (listener != null && !mRrBeatListeners.contains(listener)) {
            mRrBeatListeners.add(listener);
        }
    }

    @Override
    public void removeRrBeatListener(RrBeatListener listener) {
        if (listener != null) {
            mRrBeatListeners.remove(listener);
        }
    }

    protected void dispatchRrBeat(RrBeatEvent event) {
        for (RrBeatListener listener : mRrBeatListeners) {
            try {
                listener.onRrBeat(event);
            } catch (Exception e) {
                Log.e(TAG, "Error in RrBeatListener callback", e);
            }
        }
    }

    @Override
    protected PccReleaseHandle requestAccess() {
        if (DEBUG) {
            Log.d(TAG, "requestAccess(" + getANTDeviceNumber() + ")");
        }

        return AntPlusHeartRatePcc.requestAccess(mContext, getANTDeviceNumber(), 0, new MyResultReceiver<AntPlusHeartRatePcc>(), new MyDeviceStateChangeReceiver());
    }

    @Override
    protected void setSpecificPcc(AntPluginPcc antPluginPcc) {
        hrPcc = (AntPlusHeartRatePcc) antPluginPcc;
    }

    @Override
    protected void subscribeSpecificEvents() {
        if (hrPcc != null) {
            hrPcc.subscribeHeartRateDataEvent(new IHeartRateDataReceiver() {
                @Override
                public void onNewHeartRateData(long estTimestamp,
                                               EnumSet<EventFlag> eventFlags,
                                               int computedHeartRate,
                                               long heartBeatCount,
                                               BigDecimal heartBeatEventTime,
                                               DataState dataState) {
                    parseHeartRateData(estTimestamp, computedHeartRate, heartBeatCount, heartBeatEventTime, dataState);
                }
            });
        }
    }

    /**
     * Converts ANT+ PCC estTimestamp (monotonic SystemClock.elapsedRealtime ms since boot)
     * to UTC Unix epoch milliseconds (System.currentTimeMillis()).
     */
    public long convertEstTimestampToUtcEpochMs(long estTimestamp) {
        if (estTimestamp <= 0L) {
            return System.currentTimeMillis();
        }
        if (estTimestamp >= 1_000_000_000_000L) {
            return estTimestamp;
        }
        try {
            long elapsedBoot = SystemClock.elapsedRealtime();
            if (elapsedBoot > 0L) {
                long epochOffset = System.currentTimeMillis() - elapsedBoot;
                return epochOffset + estTimestamp;
            }
        } catch (Throwable t) {
            // Graceful fallback for unmocked pure JVM tests
        }
        return System.currentTimeMillis();
    }

    /**
     * Processes incoming ANT+ heart rate and beat timing data.
     * Enforces the strict beat-count continuity rule (deltaC == 1) and flags RF multi-beat gaps (deltaC >= 2).
     */
    public void parseHeartRateData(long estTimestamp,
                                   int computedHeartRate,
                                   long heartBeatCount,
                                   BigDecimal heartBeatEventTime,
                                   DataState dataState) {
        if (DEBUG) {
            Log.d(TAG, "got new HR value: " + computedHeartRate);
        }

        if (dataState == DataState.LIVE_DATA) {
            mHeartRateSensor.newValue(computedHeartRate);
        } else if (dataState == DataState.ZERO_DETECTED) {
            mHeartRateSensor.newValue(null);
            mPrevHeartBeatCount = -1L;
            mPrevHeartBeatEventTime = null;
            mLastBeatTimestampMs = 0.0;
            return;
        } else {
            return;
        }

        if (heartBeatEventTime == null) {
            return;
        }

        long packetArrivalUtcMs = convertEstTimestampToUtcEpochMs(estTimestamp);

        if (mPrevHeartBeatCount == -1L) {
            // Baseline reference packet
            mPrevHeartBeatCount = heartBeatCount;
            mPrevHeartBeatEventTime = heartBeatEventTime;
            mLastBeatTimestampMs = packetArrivalUtcMs;
            return;
        }

        // Guard against dropout / strap disconnect > 64s
        if (mLastBeatTimestampMs > 0.0 && (packetArrivalUtcMs - mLastBeatTimestampMs) > 64_000L) {
            mPrevHeartBeatCount = heartBeatCount;
            mPrevHeartBeatEventTime = heartBeatEventTime;
            mLastBeatTimestampMs = packetArrivalUtcMs;
            return;
        }

        long deltaC = (heartBeatCount - mPrevHeartBeatCount) & 0xFF;

        if (deltaC == 0) {
            // Duplicate / cached 4-Hz transmission: ignore
            return;
        }

        double deltaSec = heartBeatEventTime.doubleValue() - mPrevHeartBeatEventTime.doubleValue();
        if (deltaSec < 0.0) {
            // 16-bit 64s event time rollover
            deltaSec += 64.0;
        }
        double totalDeltaMs = deltaSec * 1000.0;

        mPrevHeartBeatCount = heartBeatCount;
        mPrevHeartBeatEventTime = heartBeatEventTime;

        if (deltaC == 1) {
            // Strict single beat continuity
            double rrMs = totalDeltaMs;
            long roundedRrMs = Math.round(rrMs);

            if (mRrIntervalSensor == null) {
                mRrIntervalSensor = new MySensor<>(this, SensorType.RR_INTERVAL);
                addAndRegisterSensor(mRrIntervalSensor);
            }
            mRrIntervalSensor.newValue((int) roundedRrMs);

            double candidateTimestampMs = mLastBeatTimestampMs + rrMs;
            double drift = Math.abs(packetArrivalUtcMs - candidateTimestampMs);
            long beatTimestampMs;
            if (drift > 2000.0) {
                beatTimestampMs = packetArrivalUtcMs;
                mLastBeatTimestampMs = packetArrivalUtcMs;
            } else {
                beatTimestampMs = Math.round(candidateTimestampMs);
                mLastBeatTimestampMs = candidateTimestampMs;
            }

            dispatchRrBeat(new RrBeatEvent(
                    beatTimestampMs,
                    rrMs,
                    (int) (heartBeatCount & 0xFF),
                    ProtocolType.ANT_PLUS,
                    0,
                    RrBeatEvent.ARTIFACT_NORMAL
            ));
        } else {
            // deltaC >= 2: Multi-beat RF packet loss
            int gapBeats = (int) (deltaC - 1);
            double avgRrMs = (totalDeltaMs > 0 && deltaC > 0) ? (totalDeltaMs / deltaC) : 1000.0;

            long beatTimestampMs = packetArrivalUtcMs;
            mLastBeatTimestampMs = packetArrivalUtcMs;

            // Do NOT emit summed delta to mRrIntervalSensor (prevents false bradycardia spike)
            dispatchRrBeat(new RrBeatEvent(
                    beatTimestampMs,
                    avgRrMs,
                    (int) (heartBeatCount & 0xFF),
                    ProtocolType.ANT_PLUS,
                    gapBeats,
                    RrBeatEvent.ARTIFACT_GAP
            ));
        }
    }

    @Override
    protected void subscribeCommonEvents() {
        onNewLegacyCommonPccFound(hrPcc);
    }

    @Override
    protected void unregisterSensors() {
        if (mRrIntervalSensor != null) {
            if (mMySensorManager != null) {
                mMySensorManager.unregisterSensor(mRrIntervalSensor);
            }
            removeSensor(SensorType.RR_INTERVAL);
            mRrIntervalSensor = null;
        }
        super.unregisterSensors();
        mPrevHeartBeatCount = -1L;
        mPrevHeartBeatEventTime = null;
        mLastBeatTimestampMs = 0.0;
    }

    @Override
    public void shutDown() {
        if (mRrIntervalSensor != null) {
            if (mMySensorManager != null) {
                mMySensorManager.unregisterSensor(mRrIntervalSensor);
            }
            removeSensor(SensorType.RR_INTERVAL);
            mRrIntervalSensor = null;
        }
        super.shutDown();
        mPrevHeartBeatCount = -1L;
        mPrevHeartBeatEventTime = null;
        mLastBeatTimestampMs = 0.0;
        mRrBeatListeners.clear();
    }
}
