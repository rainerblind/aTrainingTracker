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

package com.atrainingtracker.banalservice.devices;

import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.atrainingtracker.banalservice.BANALService;
import com.atrainingtracker.banalservice.sensor.MyDoubleAccumulatorSensor;
import com.atrainingtracker.banalservice.sensor.MySensor;
import com.atrainingtracker.banalservice.sensor.MySensorManager;
import com.atrainingtracker.banalservice.sensor.SensorType;
import com.atrainingtracker.trainingtracker.settings.SettingsDataStoreJavaHelper;


public abstract class SpeedAndLocationDevice extends MyDevice {
    public static final double ACCURACY_THRESHOLD = 200;
    public static final long INACTIVITY_TIMEOUT_MS = 10000L;
    public static final long DECAY_INTERVAL_MS = 1000L;
    public static final double DECAY_FACTOR = 0.5;
    public static final double SPEED_ZERO_THRESHOLD = 0.1;

    protected static final int SAMPLING_TIME = 1000;
    protected static final int MIN_DISTANCE = 0;
    private static final String TAG = "GPSSpeedAndLocationDevice";
    private static final boolean DEBUG = BANALService.getDebug(false);
    protected boolean LocationAvailable = false;
    protected MySensor<Double> mLongitudeSensor;
    protected MySensor<Double> mLatitudeSensor;
    protected MySensor<Double> mAccuracySensor;
    protected MySensor<Double> mBearingSensor;
    protected MySensor<Double> mAltitudeSensor;
    protected MySensor<Double> mSpeedSensor;
    protected MySensor<Double> mLineDistanceSensor;
    protected MySensor<Double> mPaceSensor;
    protected MyDoubleAccumulatorSensor mDistanceSensor;
    protected MyDoubleAccumulatorSensor mLapDistanceSensor;
    protected Location mPrevLocation, mStartLocation = null;
    double mDistance, mSpeed;
    protected boolean mIsDecaying = false;

    // REQ-TRK-048.2 & REQ-TRK-048.3: Stationary anchor coordinates and pinning state
    protected Location mStationaryAnchor = null;
    protected boolean mIsStationaryPinned = false;
    protected double mFilteredLatitude = Double.NaN;
    protected double mFilteredLongitude = Double.NaN;

    protected Handler mWatchdogHandler;
    private final Runnable mInactivityRunnable = new Runnable() {
        @Override
        public void run() {
            onInactivityTimeout();
        }
    };
    private final Runnable mDecayRunnable = new Runnable() {
        @Override
        public void run() {
            triggerDecayTick();
        }
    };


    public SpeedAndLocationDevice(Context context, MySensorManager mySensorManager, DeviceType deviceType) {
        super(context, mySensorManager, deviceType);
        if (DEBUG) {
            Log.d(TAG, "constructor");
        }
    }

    protected synchronized Handler getWatchdogHandler() {
        if (mWatchdogHandler == null) {
            try {
                Looper looper = null;
                if (mContext != null) {
                    looper = mContext.getMainLooper();
                }
                if (looper == null) {
                    looper = Looper.getMainLooper();
                }
                if (looper != null) {
                    mWatchdogHandler = new Handler(looper);
                }
            } catch (Throwable t) {
                if (DEBUG) Log.w(TAG, "Cannot initialize Handler in current environment: " + t.getMessage());
            }
        }
        return mWatchdogHandler;
    }

    public synchronized void setWatchdogHandler(Handler handler) {
        mWatchdogHandler = handler;
    }

    private synchronized void resetWatchdog() {
        Handler handler = getWatchdogHandler();
        if (handler != null) {
            try {
                handler.removeCallbacks(mInactivityRunnable);
                handler.removeCallbacks(mDecayRunnable);
                handler.postDelayed(mInactivityRunnable, INACTIVITY_TIMEOUT_MS);
            } catch (Throwable t) {
                if (DEBUG) Log.w(TAG, "Failed to reset watchdog: " + t.getMessage());
            }
        }
    }

    private synchronized void cancelWatchdog() {
        if (mWatchdogHandler != null) {
            try {
                mWatchdogHandler.removeCallbacks(mInactivityRunnable);
                mWatchdogHandler.removeCallbacks(mDecayRunnable);
            } catch (Throwable t) {
                if (DEBUG) Log.w(TAG, "Failed to cancel watchdog: " + t.getMessage());
            }
        }
    }

    protected synchronized void onInactivityTimeout() {
        if (DEBUG) Log.d(TAG, "onInactivityTimeout(): initiating speed decay");
        triggerDecayTick();
    }

    protected synchronized void triggerDecayTick() {
        mIsDecaying = true;
        mSpeed = mSpeed * DECAY_FACTOR;
        if (mSpeed <= SPEED_ZERO_THRESHOLD) {
            mSpeed = 0.0;
            if (mSpeedSensor != null) {
                mSpeedSensor.newValue(0.0);
            }
            if (mPaceSensor != null) {
                mPaceSensor.newValue(null);
            }
            if (DEBUG) Log.d(TAG, "triggerDecayTick(): speed clamped to 0.0, decay stopped");
        } else {
            if (mSpeedSensor != null) {
                mSpeedSensor.newValue(mSpeed);
            }
            if (mPaceSensor != null) {
                mPaceSensor.newValue(1.0 / mSpeed);
            }
            Handler handler = getWatchdogHandler();
            if (handler != null) {
                try {
                    handler.postDelayed(mDecayRunnable, DECAY_INTERVAL_MS);
                } catch (Throwable t) {
                    if (DEBUG) Log.w(TAG, "Failed to schedule next decay tick: " + t.getMessage());
                }
            }
            if (DEBUG) Log.d(TAG, "triggerDecayTick(): speed decayed to " + mSpeed);
        }
    }

    public synchronized void triggerInactivityTimeoutForTesting() {
        onInactivityTimeout();
    }

    public synchronized void triggerDecayTickForTesting() {
        triggerDecayTick();
    }

    public synchronized double getSpeed() {
        return mSpeed;
    }

    public synchronized boolean isDecaying() {
        return mIsDecaying;
    }

    protected void LocationAvailable() {
        if (DEBUG) Log.d(TAG, "LocationAvailable()");

        if (!LocationAvailable) {
            LocationAvailable = true;
            registerSensors();
            mContext.sendBroadcast(new Intent(BANALService.LOCATION_AVAILABLE_INTENT)
                    .setPackage(mContext.getPackageName()));
        }
    }

    protected void LocationUnavailable() {
        if (DEBUG) Log.d(TAG, "LocationUnavailable()");

        if (LocationAvailable) {
            LocationAvailable = false;
            unregisterSensors();
            cancelWatchdog();
            if (mSpeed > 0) {
                triggerDecayTick();
            }
            mContext.sendBroadcast(new Intent(BANALService.LOCATION_UNAVAILABLE_INTENT)
                    .setPackage(mContext.getPackageName()));
        }
    }

    @Override
    protected void addSensors() {
        mLongitudeSensor = new MySensor<Double>(this, SensorType.LONGITUDE);
        mLatitudeSensor = new MySensor<Double>(this, SensorType.LATITUDE);
        mAccuracySensor = new MySensor<Double>(this, SensorType.ACCURACY);
        mBearingSensor = new MySensor<Double>(this, SensorType.BEARING);
        mAltitudeSensor = new MySensor<Double>(this, SensorType.ALTITUDE);
        mSpeedSensor = new MySensor<Double>(this, SensorType.SPEED_mps);
        mPaceSensor = new MySensor<Double>(this, SensorType.PACE_spm);
        mLineDistanceSensor = new MySensor<Double>(this, SensorType.LINE_DISTANCE_m);
        mDistanceSensor = new MyDoubleAccumulatorSensor(this, SensorType.DISTANCE_m, true);
        mLapDistanceSensor = new MyDoubleAccumulatorSensor(this, SensorType.DISTANCE_m_LAP, true);

        addSensor(mLongitudeSensor);
        addSensor(mLatitudeSensor);
        addSensor(mAccuracySensor);
        addSensor(mBearingSensor);
        addSensor(mAltitudeSensor);
        addSensor(mSpeedSensor);
        addSensor(mLineDistanceSensor);
        addSensor(mPaceSensor);
        addSensor(mDistanceSensor);
        addSensor(mLapDistanceSensor);

        mSpeedSensor.newValue(0.0);
        mPaceSensor.newValue(null);
    }

    @Override
    protected void newLap() {
        mLapDistanceSensor.reset();
    }

    @Override
    protected synchronized void onAccumulatorsReset() {
        resetStartLocation();
        cancelWatchdog();
        mIsDecaying = false;
        mSpeed = 0.0;
        if (mSpeedSensor != null) {
            mSpeedSensor.newValue(0.0);
        }
        if (mPaceSensor != null) {
            mPaceSensor.newValue(null);
        }
    }

    @Override
    public synchronized void shutDown() {
        if (DEBUG) Log.i(TAG, "shutDown()");
        cancelWatchdog();
        mIsDecaying = false;
        mStationaryAnchor = null;
        mIsStationaryPinned = false;
        mFilteredLatitude = Double.NaN;
        mFilteredLongitude = Double.NaN;
        super.shutDown();
    }

    public synchronized void resetStartLocation() {
        if (DEBUG) Log.i(TAG, "resetStartLocation()");
        mStartLocation = null;
        mPrevLocation = null;
        mStationaryAnchor = null;
        mIsStationaryPinned = false;
        mFilteredLatitude = Double.NaN;
        mFilteredLongitude = Double.NaN;
    }

    /**
     * REQ-TRK-048.2: Returns true if the device is currently pinned to a stationary GPS anchor coordinate.
     */
    public synchronized boolean isStationaryPinned() {
        return mIsStationaryPinned;
    }

    /**
     * REQ-TRK-048.2: Returns the currently pinned stationary GPS anchor location, or null if unpinned.
     */
    public synchronized Location getStationaryAnchor() {
        return mStationaryAnchor;
    }

    public void onNewLocation(Location location) {
        if (DEBUG) Log.i(TAG, "onNewLocation()");

        if (location != null) {
            double accuracyThreshold = SettingsDataStoreJavaHelper.getGpsAccuracyThreshold(mContext);
            if (DEBUG)
                Log.d(TAG, "new location, provider: " + location.getProvider() + ", accuracy=" + location.getAccuracy() + ", threshold=" + accuracyThreshold);
            if (location.getAccuracy() <= accuracyThreshold) {
                LocationAvailable();
                resetWatchdog();

                // save the first location, i.e., the start location
                if (mStartLocation == null) {
                    mStartLocation = location;
                }

                // REQ-TRK-048.2: Dynamic accuracy-scaled deadband radius R = max(3.0m, 0.6 * accuracy)
                double deadbandRadius = Math.max(3.0, 0.6 * location.getAccuracy());
                // REQ-TRK-056.1: Dynamic accuracy-scaled escape radius for unpinning and movement gating
                double escapeRadius = Math.max(3.0, 1.5 * location.getAccuracy());
                boolean wasPinned = mIsStationaryPinned;
                boolean hasSpeed = location.hasSpeed() || location.getSpeed() > 0.0f;
                float rawSpeed = hasSpeed ? location.getSpeed() : -1.0f;
                boolean isDopplerStationary = hasSpeed && (rawSpeed <= SPEED_ZERO_THRESHOLD);

                double delta_distance = 0.0;
                long dt_ms = 0;
                if (mPrevLocation != null) {
                    dt_ms = location.getTime() - mPrevLocation.getTime();
                }

                double speed = 0.0;

                // REQ-TRK-048.1, REQ-TRK-048.2, REQ-TRK-048.4: Anchor pinning, deadband gating, and Doppler zero preservation
                if (mIsStationaryPinned && mStationaryAnchor != null) {
                    float distFromAnchor = mStationaryAnchor.distanceTo(location);
                    boolean shouldUnpin = false;
                    if (distFromAnchor > deadbandRadius) {
                        // Unpin if Doppler reports motion, provider lacks Doppler, OR displacement escapes accuracy noise floor
                        if (!hasSpeed || rawSpeed > SPEED_ZERO_THRESHOLD || distFromAnchor > escapeRadius) {
                            shouldUnpin = true;
                        }
                    }

                    if (shouldUnpin) {
                        mIsStationaryPinned = false;
                        delta_distance = distFromAnchor;
                        mStationaryAnchor = null;

                        // REQ-TRK-041.1: Guard against implausible multipath teleportation spikes (speed > 70.0 m/s = 252 km/h)
                        double calcSpeed = (dt_ms > 0 && dt_ms < 30000) ? (delta_distance / (dt_ms / 1000.0)) : 0.0;
                        if (calcSpeed > 70.0) {
                            delta_distance = 0.0;
                            calcSpeed = 0.0;
                        }

                        // REQ-TRK-056.2: Fall back to geometric speed if provider reports zero or damped speed (< 0.2 * calcSpeed)
                        if (hasSpeed && rawSpeed > SPEED_ZERO_THRESHOLD && (calcSpeed <= 1.0 || rawSpeed >= 0.2 * calcSpeed)) {
                            speed = rawSpeed;
                        } else if (calcSpeed > 0.0 && calcSpeed <= 70.0) {
                            speed = calcSpeed;
                        }
                    } else {
                        // Suppress distance creep and zero speed while pinned
                        delta_distance = 0.0;
                        speed = 0.0;
                    }
                } else {
                    double rawDelta = (mPrevLocation != null) ? mPrevLocation.distanceTo(location) : 0.0;

                    // REQ-TRK-056.1 & REQ-TRK-048.1: Only pin on Doppler zero if displacement remains within escape radius
                    // or if initial location fix (mPrevLocation == null). If rawDelta > escapeRadius, provider Doppler speed is
                    // damped/suppressed (e.g. Google Fused motionless phone in mock/replay) during active movement.
                    if (isDopplerStationary && (mPrevLocation == null || rawDelta <= escapeRadius)) {
                        mStationaryAnchor = location;
                        mIsStationaryPinned = true;
                        delta_distance = 0.0;
                        speed = 0.0;
                    } else if (!hasSpeed && (mPrevLocation == null || (rawDelta <= deadbandRadius && mSpeed <= SPEED_ZERO_THRESHOLD))) {
                        // REQ-TRK-048.4: Non-Doppler provider stationary; pin anchor
                        mStationaryAnchor = location;
                        mIsStationaryPinned = true;
                        delta_distance = 0.0;
                        speed = 0.0;
                    } else {
                        // Legitimate movement
                        delta_distance = rawDelta;

                        // REQ-TRK-041.1: Guard against implausible multipath teleportation spikes (speed > 70.0 m/s = 252 km/h)
                        double calcSpeed = (dt_ms > 0 && dt_ms < 30000) ? (delta_distance / (dt_ms / 1000.0)) : 0.0;
                        if (calcSpeed > 70.0) {
                            delta_distance = 0.0;
                            calcSpeed = 0.0;
                        }

                        // REQ-TRK-056.2: Fall back to geometric speed if provider reports zero or damped speed (< 0.2 * calcSpeed)
                        if (hasSpeed && rawSpeed > SPEED_ZERO_THRESHOLD && (calcSpeed <= 1.0 || rawSpeed >= 0.2 * calcSpeed)) {
                            speed = rawSpeed;
                        } else if (calcSpeed > 0.0 && calcSpeed <= 70.0) {
                            speed = calcSpeed;
                        }
                    }
                }

                if (delta_distance > 0.0) {
                    mDistanceSensor.increment(delta_distance);
                    mLapDistanceSensor.increment(delta_distance);
                }

                // REQ-TRK-048.3: Stationary bearing lock
                double bearing = location.getBearing();
                if (bearing == 0.0f) {
                    if (!mIsStationaryPinned && mPrevLocation != null && delta_distance > 0.5) {
                        bearing = (mPrevLocation.bearingTo(location) + 360.0) % 360.0;
                    } else if (mBearingSensor.getValue() != null) {
                        bearing = mBearingSensor.getValue();
                    }
                } else {
                    if (mIsStationaryPinned && wasPinned && mBearingSensor.getValue() != null) {
                        // While continuously pinned, lock bearing to prevent jitter noise from spinning the compass
                        bearing = mBearingSensor.getValue();
                    }
                }
                mBearingSensor.newValue((bearing + 360.0) % 360.0);

                // Speed smoothing & pace
                if (mIsStationaryPinned) {
                    mSpeed = 0.0;
                } else if (mIsDecaying || mPrevLocation == null || dt_ms >= 3000) {
                    mSpeed = speed;
                } else {
                    mSpeed = (mSpeed + speed) / 2;
                }
                mIsDecaying = false;
                mSpeedSensor.newValue(mSpeed);
                if (mSpeed > SPEED_ZERO_THRESHOLD) {
                    mPaceSensor.newValue(1.0 / mSpeed);
                } else {
                    mPaceSensor.newValue(null);
                }

                // REQ-TRK-048.2 & REQ-TRK-048.3: Coordinate stabilization & anchor emission
                double latToPublish;
                double lonToPublish;
                if (mIsStationaryPinned && mStationaryAnchor != null) {
                    latToPublish = mStationaryAnchor.getLatitude();
                    lonToPublish = mStationaryAnchor.getLongitude();
                    mFilteredLatitude = latToPublish;
                    mFilteredLongitude = lonToPublish;
                } else {
                    double rawLat = location.getLatitude();
                    double rawLon = location.getLongitude();
                    if (Double.isNaN(mFilteredLatitude) || Double.isNaN(mFilteredLongitude)) {
                        mFilteredLatitude = rawLat;
                        mFilteredLongitude = rawLon;
                    } else {
                        double alpha = Math.min(1.0, Math.max(0.2, mSpeed / 3.0));
                        mFilteredLatitude = mFilteredLatitude + alpha * (rawLat - mFilteredLatitude);
                        mFilteredLongitude = mFilteredLongitude + alpha * (rawLon - mFilteredLongitude);
                    }
                    latToPublish = mFilteredLatitude;
                    lonToPublish = mFilteredLongitude;
                }

                mLongitudeSensor.newValue(lonToPublish);
                mLatitudeSensor.newValue(latToPublish);
                mAccuracySensor.newValue(location.getAccuracy() + 0.0);
                mAltitudeSensor.newValue(location.getAltitude());

                if (mIsStationaryPinned && mStationaryAnchor != null) {
                    mLineDistanceSensor.newValue(mStationaryAnchor.distanceTo(mStartLocation) + 0.0);
                } else {
                    mLineDistanceSensor.newValue(location.distanceTo(mStartLocation) + 0.0);
                }

                // finally, save the location
                mPrevLocation = location;

                // also broadcast that we have a new location
                Intent intent = new Intent(BANALService.NEW_LOCATION_INTENT)
                        .putExtra(BANALService.LATITUDE, latToPublish)
                        .putExtra(BANALService.LONGITUDE, lonToPublish)
                        .putExtra(BANALService.LOCATION_PROVIDER, location.getProvider())
                        .setPackage(mContext.getPackageName());
                mContext.sendBroadcast(intent);
            }
        }
    }

    /**
     * Re-registers location updates if permissions or providers became available after initialization (ATT-3045).
     */
    public void checkOrReRegisterLocationUpdates() {
        // Default no-op, overridden by subclasses that attach location listeners
    }
}
