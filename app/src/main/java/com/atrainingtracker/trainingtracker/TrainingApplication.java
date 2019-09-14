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

package com.atrainingtracker.trainingtracker;

import android.app.Application;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.widget.Toast;

import com.atrainingtracker.R;
import com.atrainingtracker.banalservice.BANALService;
import com.atrainingtracker.banalservice.BSportType;
import com.atrainingtracker.banalservice.sensor.SensorData;
import com.atrainingtracker.banalservice.sensor.formater.DistanceFormatter;
import com.atrainingtracker.banalservice.sensor.formater.TimeFormatter;
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager;
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager;
import com.atrainingtracker.trainingtracker.activities.MainActivityWithNavigation;
import com.atrainingtracker.trainingtracker.activities.WorkoutDetailsActivity;
import com.atrainingtracker.trainingtracker.exporter.FileFormat;
import com.atrainingtracker.trainingtracker.tracker.TrackerService;
import com.atrainingtracker.trainingtracker.database.KnownLocationsDatabaseManager;
import com.atrainingtracker.trainingtracker.database.LapsDatabaseManager;
import com.atrainingtracker.trainingtracker.database.TrackingViewsDatabaseManager;
import com.atrainingtracker.trainingtracker.database.WorkoutSamplesDatabaseManager;
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager;
import com.atrainingtracker.trainingtracker.fragments.mapFragments.TrackOnMapHelper;
import com.atrainingtracker.trainingtracker.onlinecommunities.strava.StravaHelper;
import com.atrainingtracker.trainingtracker.onlinecommunities.strava.StravaSegmentsHelper;
import com.atrainingtracker.trainingtracker.segments.SegmentOnMapHelper;
import com.atrainingtracker.trainingtracker.segments.SegmentsDatabaseManager;
import com.atrainingtracker.trainingtracker.smartwatch.pebble.PebbleDatabaseManager;
import com.atrainingtracker.trainingtracker.smartwatch.pebble.PebbleService;
import com.atrainingtracker.trainingtracker.smartwatch.pebble.PebbleServiceBuildIn;
import com.atrainingtracker.trainingtracker.smartwatch.pebble.Watchapp;

import java.util.HashMap;

public class TrainingApplication extends Application {
    public static final boolean DEBUG = false;
    // some Strings to handle tracking globally
    public static final String REQUEST_START_TRACKING = "com.atrainingtracker.trainingapplication.REQUEST_START_TRACKING";
    public static final String REQUEST_PAUSE_TRACKING = "com.atrainingtracker.trainingapplication.REQUEST_PAUSE_TRACKING";
    public static final String REQUEST_RESUME_FROM_PAUSED = "com.atrainingtracker.trainingapplication.REQUEST_RESUME_FROM_PAUSED";
    public static final String REQUEST_STOP_TRACKING = "com.atrainingtracker.trainingapplication.REQUEST_STOP_TRACKING";
    public static final String TRACKING_STATE_CHANGED = "com.atrainingtracker.trainingapplication.TRACKING_STATE_CHANGED";
    public static final String REQUEST_NEW_LAP = "com.atrainingtracker.trainingapplication.REQUEST_NEW_LAP";
    public static final String REQUEST_START_SEARCH_FOR_PAIRED_DEVICES = "com.atrainingtracker.trainingapplication.REQUEST_START_SEARCH_FOR_PAIRED_DEVICES";
    public static final String REQUEST_CHANGE_SPORT_TYPE = "com.atrainingtracker.trainingapplication.REQUEST_CHANGE_SPORT_TYPE";
    public static final String SPORT_TYPE_ID = "com.atrainingtracker.trainingapplication.SPORT_TYPE_ID";
    // TODO: also move these Strings to string.xml???
    public static final String SP_UNITS = "listUnits";
    public static final String SP_FORCE_PORTRAIT = "forcePortrait";
    public static final String SP_KEEP_SCREEN_ON = "keepScreenOn";
    public static final String SP_NO_UNLOCKING = "noUnlocking";
    public static final String SP_ZOOM_DEPENDING_ON_SPEED = "zoomDependingOnSpeed";
    public static final String SP_SHOW_UNITS = "showUnits";
    // configure search behaviour
    public static final String PREF_KEY_START_SEARCH = "start_search";
    public static final String SP_NUMBER_OF_SEARCH_TRIES = "numberOfSearchTries";
    public static final String PEBBLE_SCREEN = "pebbleScreen";
    public static final String SP_PEBBLE_SUPPORT = "PebbleSupport";
    public static final String SP_PEBBLE_WATCHAPP = "listPebbleWatchapps";
    public static final String SP_SHOW_PEBBLE_INSTALL_DIALOG = "showPebbleInstallDialog";
    public static final String SP_CONFIGURE_PEBBLE_DISPLAY = "configurePebbleDisplays";
    public static final String FILE_EXPORT = "fileExport";
    public static final String CLOUD_UPLOAD = "cloudUpload";
    public static final String LOCATION_SOURCES = "prefsLocationSources";
    public static final String SHOW_ALL_LOCATION_SOURCES_ON_MAP = "showAllLocationsSourcesOnMap";
    //    protected static final String SP_DROPBOX_KEY       = "dropboxKey";
//    protected static final String SP_DROPBOX_SECRET    = "dropboxSecret";
    public static final String SP_UPLOAD_TO_DROPBOX = "uploadToDropbox";
    public static final String SP_DEFAULT_TO_PRIVATE = "defaultToPrivate";
    public static final String PREFERENCE_SCREEN_EMAIL_UPLOAD = "psSendEmail";
    public static final String SP_SEND_EMAIL = "spSendWorkoutEmail";
    public static final String SP_EMAIL_ADDRESS = "spEmailAddress";
    public static final String SP_EMAIL_SUBJECT = "spEmailSubject";
    public static final String SP_SEND_TCX_EMAIL = "spSendTCXEmail";
    public static final String SP_SEND_GPX_EMAIL = "spSendGPXEmail";
    public static final String SP_SEND_CSV_EMAIL = "spSendCSVEmail";
    public static final String SP_SEND_GC_EMAIL = "spSendGCEmail";
    public static final String PREFERENCE_SCREEN_STRAVA = "psUploadToStrava";
    public static final String SP_UPLOAD_TO_STRAVA = "uploadToStrava";
    public static final String SP_STRAVA_TOKEN = "stravaToken";
    public static final String UPDATE_STRAVA_EQUIPMENT = "updateStravaEquipment";
    public static final String SP_LAST_UPDATE_TIME_OF_STRAVA_EQUIPMENT = "lastUpdateTimeOfStravaEquipment";
    public static final String SP_STRAVA_ATHLETE_ID = "stravaAthleteId";
    public static final String PREFERENCE_SCREEN_RUNKEEPER = "psUploadToRunkeeper";
    public static final String SP_UPLOAD_TO_RUNKEEPER = "uploadToRunkeeper";
    public static final String SP_RUNKEEPER_TOKEN = "runkeeperToken";
    public static final String PREFERENCE_SCREEN_TRAINING_PEAKS = "psUploadToTrainingPeaks";
    //     protected final static AccessType DROPBOX_ACCESS_TYPE = AccessType.APP_FOLDER;
    public static final String SP_UPLOAD_TO_TRAINING_PEAKS = "uploadToTrainingPeaks";
    public static final String SP_TRAINING_PEAKS_ACCESS_TOKEN = "trainingPeaksAccessToken";
    public static final String SP_TRAINING_PEAKS_REFRESH_TOKEN = "trainingPeaksRefreshToken";
    public static final String SP_SAMPLING_TIME = "samplingTime";
    public static final String SP_ATHLETE_NAME = "athleteName";
    // public static final String SP_DISPLAY_UPDATE_TIME     = "displayUpdateTime";
    public static final String SP_LACTATE_THRESHOLD_POWER = "lactateThresholdPower";
    public static final String SP_LOCATION_SOURCE_GPS = "locationSourceGPS";
    public static final String SP_LOCATION_SOURCE_GOOGLE_FUSED = "locationSourceGoogleFused";
    public static final String SP_LOCATION_SOURCE_NETWORK = "locationSourceNetwork";
    public static final String SP_EXPORT_TO_CSV = "exportToCSV";
    public static final String SP_EXPORT_TO_TCX = "exportToGarminTCX";
    public static final String SP_EXPORT_TO_GPX = "exportToGPX";
    public static final String SP_EXPORT_TO_GC_JSON = "exportToGCJson";
    public static final String SP_CHECK_ANT_INSTALLATION = "checkANTInstallation";
    public static final String MIN_WALK_SPEED = "minWalkSpeed";
    public static final String MAX_WALK_SPEED = "maxWalkSpeed";
    public static final String MAX_RUN_SPEED = "maxRunSpeed";
    public static final String MAX_MTB_SPEED = "maxMTBSpeed";
    public static final String SPORT = "sport";
    public static final String SENSOR_NAMES = "sensorNames";
    public static final String GC_SENSORS = "GCSensors";

    protected static final String NOTIFICATION_CHANNEL__TRACKING = "NOTIFICATION_CHANNEL__TRACKING";
    public static final String NOTIFICATION_CHANNEL__EXPORT = "NOTIFICATION_CHANNEL__EXPORT";
    public static final int TRACKING_NOTIFICATION_ID = 1;
    public static final int EXPORT_PROGRESS_NOTIFICATION_ID = 2;
    public static final int EXPORT_RESULT_NOTIFICATION_ID = 3;
    public static final int SEND_EMAIL_NOTIFICATION_ID = 4;

    public static final int DEFAULT_SAMPLING_TIME = 1;
    public static final float MIN_DISTANCE_BETWEEN_START_AND_STOP = 100;
    public static final double DISTANCE_TO_MAX_THRESHOLD_FOR_TRAINER = 200;
    public static final double DISTANCE_TO_MAX_RATIO_FOR_COMMUTE = Math.PI / 2; // probably the best value ;-)
    protected final static String DROPBOX_APP_KEY = "iknmdmr31sf64r0";
    protected final static String DROPBOX_APP_SECRET = "w7jkk5ri8sl7sd7";
    protected static final String SP_DROPBOX_TOKEN = "dropboxToken";
    private static final String TAG = "TrainingApplication";
    private static final String SP_PLAY_SERVICE_INSTALLATION_TRIES = "playServiceInstallationTries";
    private static final int MAX_PLAY_SERVICE_INSTALLATION_TRIES = 10;
    private static final int DEFAULT_NUMBER_OF_SEARCH_TRIES = 3;
    private static final String SP_START_SEARCH_WHEN_APP_STARTS = "startSearchWhenAppStarts";
    private static final boolean START_SEARCH_WHEN_APP_STARTS_DEFAULT = true;
    private static final String SP_START_SEARCH_WHEN_TRACKING_STARTS = "startSearchWhenTrackingStarts";
    private static final boolean START_SEARCH_WHEN_TRACKING_STARTS_DEFAULT = false;
    private static final String SP_START_SEARCH_WHEN_RESUME_FROM_PAUSED = "startSearchWhenResumeFromPaused";
    private static final boolean START_SEARCH_WHEN_RESUME_FROM_PAUSED_DEFAULT = true;
    private static final String SP_START_SEARCH_WHEN_NEW_LAP = "startSearchWhenNewLap";
    private static final boolean START_SEARCH_WHEN_NEW_LAP_DEFAULT = false;
    private static final String SP_START_SEARCH_WHEN_USER_CHANGES_SPORT = "startSearchWhenUserChangesSport";
    private static final boolean START_SEARCH_WHEN_USER_CHANGES_SPORT_DEFAULT = true;
    private static final String SP_CHANGE_SPORT_WHEN_DEVICE_GETS_LOST = "changeSportWhenDeviceGetsLost";
    private static final boolean CHANGE_SPORT_WHEN_DEVICE_GETS_LOST_DEFAULT = true;
    private static final String SP_SEARCH_ONLY_FOR_SPORT_SPECIFIC_DEVICES = "searchOnlyForSportSpecificDevices";
    private static final boolean SEARCH_ONLY_FOR_SPORT_SPECIFIC_DEVICES_DEFAULT = true;
    private static final String SP_UPLOAD_STRAVA_GPS = "uploadStravaGPS";
    private static final String SP_UPLOAD_STRAVA_ALTITUDE = "uploadStravaAltitude";
    private static final String SP_UPLOAD_STRAVA_HR = "uploadStravaHR";
    private static final String SP_UPLOAD_STRAVA_POWER = "uploadStravaPower";
    private static final String SP_UPLOAD_STRAVA_CADENCE = "uploadStravaCadence";
    private static final String SP_UPLOAD_RUNKEEPER_GPS = "uploadRunkeeperGPS";
    private static final String SP_UPLOAD_RUNKEEPER_ALTITUDE = "uploadRunkeeperAltitude";
    private static final String SP_UPLOAD_RUNKEEPER_HR = "uploadRunkeeperHR";
    private static final String SP_UPLOAD_RUNKEEPER_POWER = "uploadRunkeeperPower";
    private static final String SP_UPLOAD_RUNKEEPER_CADENCE = "uploadRunkeeperCadence";
    private static final String SP_UPLOAD_TRAINING_PEAKS_GPS = "uploadTrainingPeaksGPS";
    private static final String SP_UPLOAD_TRAINING_PEAKS_ALTITUDE = "uploadTrainingPeaksAltitude";
    private static final String SP_UPLOAD_TRAINING_PEAKS_HR = "uploadTrainingPeaksHR";
    private static final String SP_UPLOAD_TRAINING_PEAKS_POWER = "uploadTrainingPeaksPower";
    private static final String SP_UPLOAD_TRAINING_PEAKS_CADENCE = "uploadTrainingPeaksCadence";
    //public static final int UPLOAD_PROGRESS_NOTIFICATION_ID       = 3;

    // public static final int UPLOAD_RESULT_BASE_NOTIFICATION_ID    = 8;
    private static final double DEFAULT_MIN_WALK_SPEED_mps = 0.1;
    private static final double DEFAULT_MAX_WALK_SPEED_mps = 2;
    private static final double DEFAULT_MAX_RUN_SPEED_mps = 4;
    private static final double DEFAULT_MAX_MTB_SPEED_mps = 5.5;
    private static final String DEFAULT_ATHLETE_NAME = "Athlete";
    private static final String APPLICATION_NAME = TAG;
    protected static Context cAppContext;
    protected static TrackingMode cTrackingMode = TrackingMode.READY;
    protected static boolean cResumeFromCrash = false;
    private static SharedPreferences cSharedPreferences;
    private final IntentFilter mSearchingFinishedFilter = new IntentFilter(BANALService.SEARCHING_FINISHED_FOR_ALL_INTENT);
    private final IntentFilter mSearchingStartedFilter = new IntentFilter(BANALService.SEARCHING_STARTED_FOR_ONE_INTENT);
    public TrackOnMapHelper trackOnMapHelper;
    public SegmentOnMapHelper segmentOnMapHelper;
    private HashMap<Long, Boolean> mSegmentListUpdating = new HashMap();
    private HashMap<Long, Boolean> mLeaderboardUpdating = new HashMap();
    private long mWorkoutID = -1;
    protected BroadcastReceiver mTrackingStartedReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            setWorkoutID(intent.getLongExtra(WorkoutSummariesDatabaseManager.WorkoutSummaries.WORKOUT_ID, -1));
        }
    };
    private Watchapp startedWatchapp = null;
    protected BroadcastReceiver mTrackingStoppedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            trackingStopped();
        }
    };
    private NotificationManagerCompat mNotificationManager;
    private NotificationCompat.Builder mTrackingAndSearchingNotificationBuilder;
    private PendingIntent mStartMainActivityPendingIntent;
    private String mNotificationSummary = "searching";
    private DistanceFormatter mDistanceFormatter = new DistanceFormatter();

    // protected void showNotification()
    // {
    //     mNotificationManager.notify(TRACKING_NOTIFICATION_ID, mTrackingAndSearchingNotificationBuilder.getNotification());
    // }
    private TimeFormatter mTimeFormatter = new TimeFormatter();
    private String mDeviceCurrentlySearchingFor = null;
    /***********************************************************************************************/

    protected BroadcastReceiver mStartTrackingReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            startTracking();
        }
    };
    protected BroadcastReceiver mStopTrackingReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            stopTracking();
        }
    };
    protected BroadcastReceiver mPauseTrackingReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            pauseTracking();
        }
    };
    protected BroadcastReceiver mResumeFromPaused = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            resumeFromPaused();
        }
    };
    private BroadcastReceiver mSearchingFinishedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            notifySearchingFinished();
        }
    };
    private BroadcastReceiver mSearchingStartedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            notifySearchingStartedFor(intent.getStringExtra(BANALService.DEVICE_NAME));
        }
    };

    public static boolean havePermission(String permission) {
        return ContextCompat.checkSelfPermission(cAppContext, permission) == PackageManager.PERMISSION_GRANTED;
    }


    public static boolean isAppInstalled(String uri) {
        PackageManager pm = cAppContext.getPackageManager();
        try {
            pm.getPackageInfo(uri, PackageManager.GET_ACTIVITIES);
            if (DEBUG) Log.i(TAG, uri + "is installed");
            return true;
        } catch (PackageManager.NameNotFoundException e) {
        }

        if (DEBUG) Log.i(TAG, uri + "is NOT installed");
        return false;
    }

    public static Context getAppContext() {
        return cAppContext;
    }

    public static boolean showInstallPlayServicesDialog() {
        int tries = cSharedPreferences.getInt(SP_PLAY_SERVICE_INSTALLATION_TRIES, 0);

        // increase this value by one
        SharedPreferences.Editor editor = cSharedPreferences.edit();
        editor.putInt(SP_PLAY_SERVICE_INSTALLATION_TRIES, tries + 1);
        editor.apply();

        return (tries < MAX_PLAY_SERVICE_INSTALLATION_TRIES);
    }

    public static int getNumberOfSearchTries() {
        String numberOfSearchTries = cSharedPreferences.getString(SP_NUMBER_OF_SEARCH_TRIES, null);
        if (DEBUG) Log.i(TAG, "number of search tries=" + numberOfSearchTries);
        if (numberOfSearchTries == null || numberOfSearchTries.equals("")) {
            return DEFAULT_NUMBER_OF_SEARCH_TRIES;
        } else {
            try {
                return Integer.valueOf(numberOfSearchTries);
            } catch (Exception e) {
                return DEFAULT_NUMBER_OF_SEARCH_TRIES;
            }
        }
    }

    public static boolean startSearchWhenAppStarts() {
        return cSharedPreferences.getBoolean(SP_START_SEARCH_WHEN_APP_STARTS, START_SEARCH_WHEN_APP_STARTS_DEFAULT);
    }

    public static boolean startSearchWhenTrackingStarts() {
        return cSharedPreferences.getBoolean(SP_START_SEARCH_WHEN_TRACKING_STARTS, START_SEARCH_WHEN_TRACKING_STARTS_DEFAULT);
    }

    public static boolean startSearchWhenResumeFromPaused() {
        return cSharedPreferences.getBoolean(SP_START_SEARCH_WHEN_RESUME_FROM_PAUSED, START_SEARCH_WHEN_RESUME_FROM_PAUSED_DEFAULT);
    }

    public static boolean startSearchWhenNewLap() {
        return cSharedPreferences.getBoolean(SP_START_SEARCH_WHEN_NEW_LAP, START_SEARCH_WHEN_NEW_LAP_DEFAULT);
    }

    public static boolean startSearchWhenUserChangesSport() {
        return cSharedPreferences.getBoolean(SP_START_SEARCH_WHEN_USER_CHANGES_SPORT, START_SEARCH_WHEN_USER_CHANGES_SPORT_DEFAULT);
    }

    public static boolean changeSportWhenDeviceGetsLost() {
        return cSharedPreferences.getBoolean(SP_CHANGE_SPORT_WHEN_DEVICE_GETS_LOST, CHANGE_SPORT_WHEN_DEVICE_GETS_LOST_DEFAULT);
    }

    public static boolean searchOnlyForSportSpecificDevices() {
        return cSharedPreferences.getBoolean(SP_SEARCH_ONLY_FOR_SPORT_SPECIFIC_DEVICES, SEARCH_ONLY_FOR_SPORT_SPECIFIC_DEVICES_DEFAULT);
    }

    public static boolean sendEmail() {
        return cSharedPreferences.getBoolean(SP_SEND_EMAIL, false);
    }

    public static String getSpEmailAddress() {
        return cSharedPreferences.getString(SP_EMAIL_ADDRESS, "first.last@example.com");
    }

    public static String getSpEmailSubject() {
        return cSharedPreferences.getString(SP_EMAIL_SUBJECT, getAppName());
    }

    public static boolean sendTCXEmail() {
        return cSharedPreferences.getBoolean(SP_SEND_TCX_EMAIL, false);
    }

    public static boolean sendGPXEmail() {
        return cSharedPreferences.getBoolean(SP_SEND_GPX_EMAIL, false);
    }

    public static boolean sendCSVEmail() {
        return cSharedPreferences.getBoolean(SP_SEND_CSV_EMAIL, false);
    }

    public static boolean sendGCEmail() {
        return cSharedPreferences.getBoolean(SP_SEND_GC_EMAIL, false);
    }

    public static boolean checkANTInstallation() {
        return cSharedPreferences.getBoolean(SP_CHECK_ANT_INSTALLATION, true);
    }

    public static void setCheckANTInstallation(boolean value) {
        cSharedPreferences.edit().putBoolean(SP_CHECK_ANT_INSTALLATION, value).apply();
    }

    public static boolean useLocationSourceGPS() {
        return cSharedPreferences.getBoolean(SP_LOCATION_SOURCE_GPS, true);
    }

    public static boolean useLocationSourceGoogleFused() {
        return cSharedPreferences.getBoolean(SP_LOCATION_SOURCE_GOOGLE_FUSED, false);
    }

    public static boolean useLocationSourceNetwork() {
        return cSharedPreferences.getBoolean(SP_LOCATION_SOURCE_NETWORK, false);
    }

    public static boolean trackLocation() {
        return (useLocationSourceGPS() || useLocationSourceGoogleFused() || useLocationSourceNetwork());
    }

    public static boolean showAllLocationSourcesOnMap() {
        return cSharedPreferences.getBoolean(SHOW_ALL_LOCATION_SOURCES_ON_MAP, false);
    }

    public static MyUnits getUnit() {
        return MyUnits.valueOf(cSharedPreferences.getString(SP_UNITS, MyUnits.METRIC.name()));
    }

    public static boolean pebbleSupport() {
        return cSharedPreferences.getBoolean(SP_PEBBLE_SUPPORT, false);
    }

    public static Watchapp getPebbleWatchapp() {
        return Watchapp.valueOf(cSharedPreferences.getString(SP_PEBBLE_WATCHAPP, Watchapp.BUILD_IN.name()));
    }

    public static boolean showPebbleInstallDialog() {
        return cSharedPreferences.getBoolean(SP_SHOW_PEBBLE_INSTALL_DIALOG, true);
    }

    public static void setShowPebbleInstallDialog(boolean value) {
        cSharedPreferences.edit().putBoolean(SP_SHOW_PEBBLE_INSTALL_DIALOG, value).apply();
    }

    public static boolean forcePortrait() {
        return cSharedPreferences.getBoolean(SP_FORCE_PORTRAIT, true);
    }

    public static boolean keepScreenOn() {
        return cSharedPreferences.getBoolean(SP_KEEP_SCREEN_ON, true);
    }

    public static boolean NoUnlocking() {
        return cSharedPreferences.getBoolean(SP_NO_UNLOCKING, true);
    }

    public static boolean zoomDependsOnSpeed() {
        return cSharedPreferences.getBoolean(SP_ZOOM_DEPENDING_ON_SPEED, true);
    }  // TODO should default to false?

    public static boolean showUnits() {
        return cSharedPreferences.getBoolean(SP_SHOW_UNITS, true);
    }

    public static int getSamplingTime() {
        String samplingTimePref = cSharedPreferences.getString(SP_SAMPLING_TIME, null);
        if (samplingTimePref == null || samplingTimePref.equals("")) {
            return DEFAULT_SAMPLING_TIME;
        } else {
            try {
                return Integer.valueOf(samplingTimePref);
            } catch (Exception e) {
                return DEFAULT_SAMPLING_TIME;
            }
        }
    }

    public static String getAppName() {
        return cAppContext.getString(R.string.application_name);
    }

    public static String getAthleteName() {
        return cSharedPreferences.getString(SP_ATHLETE_NAME, DEFAULT_ATHLETE_NAME);
    }

    public static double getMinWalkSpeed_UserUnits() {
        return MyHelper.string2Double(cSharedPreferences.getString(MIN_WALK_SPEED, String.format("%f", MyHelper.mps2userUnit(DEFAULT_MIN_WALK_SPEED_mps))));
    }

    public static double getMaxWalkSpeed_UserUnits() {
        return MyHelper.string2Double(cSharedPreferences.getString(MAX_WALK_SPEED, String.format("%f", MyHelper.mps2userUnit(DEFAULT_MAX_WALK_SPEED_mps))));
    }

    public static double getMaxRunSpeed_UserUnits() {
        return MyHelper.string2Double(cSharedPreferences.getString(MAX_RUN_SPEED, String.format("%f", MyHelper.mps2userUnit(DEFAULT_MAX_RUN_SPEED_mps))));
    }

    public static double getMaxMTBSpeed_UserUnits() {
        return MyHelper.string2Double(cSharedPreferences.getString(MAX_MTB_SPEED, String.format("%f", MyHelper.mps2userUnit(DEFAULT_MAX_MTB_SPEED_mps))));
    }

    public static double getMinWalkSpeed_mps() {
        return MyHelper.UserUnit2mps(getMinWalkSpeed_UserUnits());
    }

    // public void setTracking(boolean tracking) {	cTracking = tracking; }  // TODO: in TrackerService???

    public static double getMaxWalkSpeed_mps() {
        return MyHelper.UserUnit2mps(getMaxWalkSpeed_UserUnits());
    }

    public static double getMaxRunSpeed_mps() {
        return MyHelper.UserUnit2mps(getMaxRunSpeed_UserUnits());
    }

    public static double getMaxMTBSpeed_mps() {
        return MyHelper.UserUnit2mps(getMaxMTBSpeed_UserUnits());
    }

    public static double getMaxBikeSpeed_mps() {
        return 12.0;
    }

    /*
     * Dropbox helpers
     */
    public static boolean uploadToDropbox() {
        return cSharedPreferences.getBoolean(SP_UPLOAD_TO_DROPBOX, false);
    }

    public static void setUploadToDropbox(boolean value) {
        cSharedPreferences.edit().putBoolean(SP_UPLOAD_TO_DROPBOX, value).apply();
    }

    public static String getDropboxAppKey() {
        return TrainingApplication.DROPBOX_APP_KEY;
    }

    public static void storeDropboxToken(String token) {
        cSharedPreferences.edit().putString(SP_DROPBOX_TOKEN, token).apply();
    }

    public static boolean hasDropboxToken() {
        return cSharedPreferences.getString(SP_DROPBOX_TOKEN, null) != null;
    }

    public static String getDropboxToken() {
        return cSharedPreferences.getString(SP_DROPBOX_TOKEN, null);
    }

    public static void deleteDropboxToken() {
        cSharedPreferences.edit().remove(SP_DROPBOX_TOKEN).apply();
        cSharedPreferences.edit().putBoolean(SP_UPLOAD_TO_DROPBOX, false).apply();
    }

    /*
     * Strava helpers
     */
    public static boolean uploadToStrava() {
        return cSharedPreferences.getBoolean(SP_UPLOAD_TO_STRAVA, false);
    }

    public static void setUploadToStrava(boolean value) {
        cSharedPreferences.edit().putBoolean(SP_UPLOAD_TO_STRAVA, value).apply();
    }

    public static String getStravaToken() {
        return cSharedPreferences.getString(SP_STRAVA_TOKEN, null);
    }

    public static void setStravaToken(String token) {
        cSharedPreferences.edit().putString(SP_STRAVA_TOKEN, token).apply();
    }

    public static void deleteStravaToken() {
        if (DEBUG) Log.i(TAG, "deleteStravaToken");
        cSharedPreferences.edit().remove(TrainingApplication.SP_STRAVA_TOKEN).apply();
        cSharedPreferences.edit().putBoolean(SP_UPLOAD_TO_STRAVA, false).apply();
        cSharedPreferences.edit().remove(SP_STRAVA_ATHLETE_ID).apply();
        if (DEBUG) Log.i(TAG, "end of deleteStravaToken");
    }

    public static String getLastUpdateTimeOfStravaEquipment() {
        return cSharedPreferences.getString(SP_LAST_UPDATE_TIME_OF_STRAVA_EQUIPMENT, cAppContext.getString(R.string.lastUpdateOfEquipmentNever));
    }

    public static void setLastUpdateTimeOfStravaEquipment(String updateTime) {
        cSharedPreferences.edit().putString(SP_LAST_UPDATE_TIME_OF_STRAVA_EQUIPMENT, updateTime).apply();
    }

    public static int getStravaAthleteId() {
        return cSharedPreferences.getInt(SP_STRAVA_ATHLETE_ID, 0);
    }

    public static void setStravaAthleteId(int stravaAthleteId) {
        cSharedPreferences.edit().putInt(SP_STRAVA_ATHLETE_ID, stravaAthleteId).apply();
    }

    public static boolean uploadStravaGPS() {
        return cSharedPreferences.getBoolean(SP_UPLOAD_STRAVA_GPS, true);
    }

    public static boolean uploadStravaAltitude() {
        return cSharedPreferences.getBoolean(SP_UPLOAD_STRAVA_ALTITUDE, true);
    }

    public static boolean uploadStravaHR() {
        return cSharedPreferences.getBoolean(SP_UPLOAD_STRAVA_HR, true);
    }

    public static boolean uploadStravaPower() {
        return cSharedPreferences.getBoolean(SP_UPLOAD_STRAVA_POWER, true);
    }

    public static boolean uploadStravaCadence() {
        return cSharedPreferences.getBoolean(SP_UPLOAD_STRAVA_CADENCE, true);
    }

    /*
     * Runkeeper helpers
     */
    public static boolean uploadToRunKeeper() {
        return cSharedPreferences.getBoolean(SP_UPLOAD_TO_RUNKEEPER, false);
    }

    public static void setUploadToRunkeeper(boolean value) {
        cSharedPreferences.edit().putBoolean(SP_UPLOAD_TO_RUNKEEPER, value).apply();
    }

    public static String getRunkeeperToken() {
        return cSharedPreferences.getString(SP_RUNKEEPER_TOKEN, null);
    }

    public static void setRunkeeperToken(String token) {
        cSharedPreferences.edit().putString(SP_RUNKEEPER_TOKEN, token).apply();
    }

    public static void deleteRunkeeperToken() {
        cSharedPreferences.edit().remove(SP_RUNKEEPER_TOKEN).apply();
        cSharedPreferences.edit().putBoolean(SP_UPLOAD_TO_RUNKEEPER, false).apply();
    }

    public static boolean uploadRunkeeperGPS() {
        return cSharedPreferences.getBoolean(SP_UPLOAD_RUNKEEPER_GPS, true);
    }

    public static boolean uploadRunkeeperAltitude() {
        return cSharedPreferences.getBoolean(SP_UPLOAD_RUNKEEPER_ALTITUDE, true);
    }

    public static boolean uploadRunkeeperHR() {
        return cSharedPreferences.getBoolean(SP_UPLOAD_RUNKEEPER_HR, true);
    }

    public static boolean uploadRunkeeperPower() {
        return cSharedPreferences.getBoolean(SP_UPLOAD_RUNKEEPER_POWER, true);
    }

    public static boolean uploadRunkeeperCadence() {
        return cSharedPreferences.getBoolean(SP_UPLOAD_RUNKEEPER_CADENCE, true);
    }

    /*
     * TrainingPeaks helpers
     */
    public static boolean uploadToTrainingPeaks() {
        return cSharedPreferences.getBoolean(SP_UPLOAD_TO_TRAINING_PEAKS, false);
    }

    public static void setUploadToTrainingPeaks(boolean value) {
        cSharedPreferences.edit().putBoolean(SP_UPLOAD_TO_RUNKEEPER, false).apply();
    }

    public static String getTrainingPeaksAccessToken() {
        return cSharedPreferences.getString(SP_TRAINING_PEAKS_ACCESS_TOKEN, null);
    }

    public static void setTrainingPeaksAccessToken(String token) {
        cSharedPreferences.edit().putString(SP_TRAINING_PEAKS_ACCESS_TOKEN, token).apply();
    }

    public static String getTrainingPeaksRefreshToken() {
        return cSharedPreferences.getString(SP_TRAINING_PEAKS_REFRESH_TOKEN, null);
    }

    public static void setTrainingPeaksRefreshToken(String token) {
        cSharedPreferences.edit().putString(SP_TRAINING_PEAKS_REFRESH_TOKEN, token).apply();
    }

    public static void deleteTrainingPeaksToken() {
        cSharedPreferences.edit().remove(SP_TRAINING_PEAKS_ACCESS_TOKEN).apply();
        cSharedPreferences.edit().remove(SP_TRAINING_PEAKS_REFRESH_TOKEN).apply();
        cSharedPreferences.edit().putBoolean(SP_UPLOAD_TO_RUNKEEPER, false).apply();
    }

    public static boolean uploadTrainingPeaksGPS() {
        return cSharedPreferences.getBoolean(SP_UPLOAD_TRAINING_PEAKS_GPS, true);
    }

    public static boolean uploadTrainingPeaksAltitude() {
        return cSharedPreferences.getBoolean(SP_UPLOAD_TRAINING_PEAKS_ALTITUDE, true);
    }

    public static boolean uploadTrainingPeaksHR() {
        return cSharedPreferences.getBoolean(SP_UPLOAD_TRAINING_PEAKS_HR, true);
    }

    public static boolean uploadTrainingPeaksPower() {
        return cSharedPreferences.getBoolean(SP_UPLOAD_TRAINING_PEAKS_POWER, true);
    }

    public static boolean uploadTrainingPeaksCadence() {
        return cSharedPreferences.getBoolean(SP_UPLOAD_TRAINING_PEAKS_CADENCE, true);
    }

    public static boolean defaultToPrivate() {
        return cSharedPreferences.getBoolean(SP_DEFAULT_TO_PRIVATE, false);
    }

    public static boolean exportToTCX() {
        return cSharedPreferences.getBoolean(SP_EXPORT_TO_TCX, false);
    }

    public static boolean exportToGPX() {
        return cSharedPreferences.getBoolean(SP_EXPORT_TO_GPX, false);
    }

    public static boolean exportToCSV() {
        return cSharedPreferences.getBoolean(SP_EXPORT_TO_CSV, false);
    }

    public static boolean exportToGCJson() {
        return cSharedPreferences.getBoolean(SP_EXPORT_TO_GC_JSON, false);
    }

    public static void setExportToTCX(boolean value) {
        cSharedPreferences.edit().putBoolean(SP_EXPORT_TO_TCX, value).apply();
    }

    public static void setExportToGPX(boolean value) {
        cSharedPreferences.edit().putBoolean(SP_EXPORT_TO_GPX, value).apply();
    }

    public static void setExportToCSV(boolean value) {
        cSharedPreferences.edit().putBoolean(SP_EXPORT_TO_CSV, value).apply();
    }

    public static void setExportToGCJson(boolean value) {
        cSharedPreferences.edit().putBoolean(SP_EXPORT_TO_GC_JSON, value).apply();
    }

    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public static boolean exportToFile(FileFormat fileFormat) {
        switch (fileFormat) {
            case CSV:
                return exportToCSV();
            case GC:
                return exportToGCJson();
            case TCX:
                return exportToTCX();
            case GPX:
                return exportToGPX();
            case STRAVA:
                return uploadToStrava();
            case RUNKEEPER:
                return uploadToRunKeeper();
            case TRAINING_PEAKS:
                return uploadToTrainingPeaks();
            default:
                return false;
        }
    }

    public static boolean exportViaEmail(FileFormat fileFormat) {
        if (sendEmail()) {
            switch (fileFormat) {
                case CSV:
                    return sendCSVEmail();
                case GC:
                    return sendGCEmail();
                case TCX:
                    return sendTCXEmail();
                case GPX:
                    return sendGPXEmail();
                default:
                    return false;
            }
        } else {
            return false;
        }
    }

    public static boolean uploadToCommunity(FileFormat fileFormat) {
        switch (fileFormat) {
            case STRAVA:
                return uploadToStrava();
            case RUNKEEPER:
                return uploadToRunKeeper();
            case TRAINING_PEAKS:
                return uploadToTrainingPeaks();
            default:
                return false;
        }
    }

    /***********************************************************************************************/

    public static boolean isPaused() {
        return cTrackingMode != TrackingMode.TRACKING;
    }

    public static boolean isTracking() {
        return cTrackingMode != TrackingMode.READY;
    }  // correct?

    public static TrackingMode getTrackingMode() {
        return cTrackingMode;
    }

    public static void setResumeFromCrash(boolean resumeFromCrash) {
        cResumeFromCrash = resumeFromCrash;
    }

    public static void startWorkoutDetailsActivity(long workoutId, WorkoutDetailsActivity.SelectedFragment selectedFragment) {
        if (DEBUG) Log.i(TAG, "startWorkoutDetailsActivity(" + workoutId + ")");

        Bundle bundle = new Bundle();
        bundle.putLong(WorkoutSummariesDatabaseManager.WorkoutSummaries.WORKOUT_ID, workoutId);
        bundle.putString(WorkoutDetailsActivity.SELECTED_FRAGMENT, selectedFragment.name());
        Intent intent = new Intent(cAppContext, WorkoutDetailsActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtras(bundle);
        cAppContext.startActivity(intent);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (DEBUG) Log.i(TAG, "onCreate");

        cAppContext = getApplicationContext();

        trackOnMapHelper = new TrackOnMapHelper();
        segmentOnMapHelper = new SegmentOnMapHelper();

        mNotificationSummary = getString(R.string.searching);

        // initialize DatabaseManagers
        LapsDatabaseManager.initializeInstance(new LapsDatabaseManager.LapsDbHelper(this));
        WorkoutSummariesDatabaseManager.initializeInstance(new WorkoutSummariesDatabaseManager.WorkoutSummariesDbHelper(this));
        WorkoutSamplesDatabaseManager.initializeInstance(new WorkoutSamplesDatabaseManager.WorkoutSamplesDbHelper(this));
        DevicesDatabaseManager.initializeInstance(new DevicesDatabaseManager.DevicesDbHelper(this));
        TrackingViewsDatabaseManager.initializeInstance(new TrackingViewsDatabaseManager.TrackingViewsDbHelper(this));
        PebbleDatabaseManager.initializeInstance(new PebbleDatabaseManager.PebbleDbHelper(this));
        KnownLocationsDatabaseManager.initializeInstance(new KnownLocationsDatabaseManager.KnownLocationsDbHelper(this));
        SegmentsDatabaseManager.initializeInstance(new SegmentsDatabaseManager.SegmentsDbHelper(this));
        SportTypeDatabaseManager.initializeInstance(new SportTypeDatabaseManager.SportTypeDbHelper(this));

        PreferenceManager.setDefaultValues(this, R.xml.prefs, false);
        cSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        // cSharedPreferences.registerOnSharedPreferenceChangeListener(mSharedPreferencesChangeListener);
        // setPowerSmoothing(0.5);

        createNotificationChannel();
        mNotificationManager = NotificationManagerCompat.from(this);
        mTrackingAndSearchingNotificationBuilder = getNewNotificationBuilder();
        addActionsToNotificationBuilder();

        registerReceiver(mSearchingFinishedReceiver, mSearchingFinishedFilter);
        registerReceiver(mSearchingStartedReceiver, mSearchingStartedFilter);

        registerReceiver(mStartTrackingReceiver, new IntentFilter(REQUEST_START_TRACKING));
        registerReceiver(mTrackingStartedReceiver, new IntentFilter(TrackerService.TRACKING_STARTED_INTENT));
        registerReceiver(mStopTrackingReceiver, new IntentFilter(REQUEST_STOP_TRACKING));
        registerReceiver(mTrackingStoppedReceiver, new IntentFilter(TrackerService.TRACKING_FINISHED_INTENT));
        registerReceiver(mPauseTrackingReceiver, new IntentFilter(REQUEST_PAUSE_TRACKING));
        registerReceiver(mResumeFromPaused, new IntentFilter(REQUEST_RESUME_FROM_PAUSED));

        // eventually get the starred segments
        // TODO: do this in the main activity???
        if (new StravaHelper().getAthleteId(this) != 0 // the athlete is registered to strava
                && !SegmentsDatabaseManager.doesDatabaseExist(this)) {  // but there is not yet a database for the segments
            StravaSegmentsHelper stravaSegmentsHelper = new StravaSegmentsHelper(this);
            stravaSegmentsHelper.getStarredStravaSegments(SportTypeDatabaseManager.getSportTypeId(BSportType.BIKE));
            stravaSegmentsHelper.getStarredStravaSegments(SportTypeDatabaseManager.getSportTypeId(BSportType.RUN));
        }


    }

    // helper method to create the Notification Builder
    protected NotificationCompat.Builder getNewNotificationBuilder() {
        Bundle bundle = new Bundle();
        bundle.putString(MainActivityWithNavigation.SELECTED_FRAGMENT, MainActivityWithNavigation.SelectedFragment.START_OR_TRACKING.name());

        Intent newIntent = new Intent(this, MainActivityWithNavigation.class);
        newIntent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        newIntent.putExtras(bundle);
        newIntent.setAction("TrackerService");
        mStartMainActivityPendingIntent = PendingIntent.getActivity(this, 0, newIntent, 0);  // TODO: correct???

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL__TRACKING)
                .setSmallIcon(R.drawable.logo)
                .setContentTitle(getString(R.string.TrainingTracker))
                .setContentText(getString(R.string.notification_tracking))
                .setContentIntent(mStartMainActivityPendingIntent);

        return notificationBuilder;
    }

    protected void addActionsToNotificationBuilder() {
        if (DEBUG) Log.i(TAG, "addActionsToNotificationBuilder");

        // first, remove all previously added actions
        // therefore, it seems to be best to use mActions.clear() as suggested in https://stackoverflow.com/questions/23922992/android-remove-action-button-from-notification
        if (mTrackingAndSearchingNotificationBuilder.mActions != null) {
            mTrackingAndSearchingNotificationBuilder.mActions.clear();
        }

        if (!BANALService.isSearching()) {
            mTrackingAndSearchingNotificationBuilder.addAction((new NotificationCompat.Action.Builder(R.drawable.research_icon, getString(R.string.research),
                    PendingIntent.getBroadcast(this, 0, new Intent(REQUEST_START_SEARCH_FOR_PAIRED_DEVICES), 0))).build());
        }

        if (isPaused()) {
            mTrackingAndSearchingNotificationBuilder.addAction((new NotificationCompat.Action.Builder(R.drawable.control_start, getString(R.string.Resume),
                    PendingIntent.getBroadcast(this, 0, new Intent(REQUEST_RESUME_FROM_PAUSED), 0))).build());

            mTrackingAndSearchingNotificationBuilder.addAction((new NotificationCompat.Action.Builder(R.drawable.control_stop, getString(R.string.Stop),
                    PendingIntent.getBroadcast(this, 0, new Intent(REQUEST_STOP_TRACKING), 0))).build());
        } else {
            mTrackingAndSearchingNotificationBuilder.addAction((new NotificationCompat.Action.Builder(R.drawable.control_pause, getString(R.string.Pause),
                    PendingIntent.getBroadcast(this, 0, new Intent(REQUEST_PAUSE_TRACKING), 0))).build());
        }
    }

    public void updateTimeAndDistanceToNotification(SensorData<Integer> time, SensorData<Number> distance, String sportType) {
        if (DEBUG) Log.i(TAG, "updateTimeAndDistanceToNotification(" + time + ", " + distance);

        String sTime = mTimeFormatter.format_with_units(time == null ? null : time.getValue());
        String sDistance = mDistanceFormatter.format_with_units(distance == null ? null : distance.getValue());

        // mTrackingAndSearchingNotificationBuilder.setDefaults(Notification.DEFAULT_ALL) // requires VIBRATE permission
        mTrackingAndSearchingNotificationBuilder.setStyle(new NotificationCompat.BigTextStyle()
                .bigText(getString(R.string.tracking_details_format, mNotificationSummary, sDistance, sportType, sTime)));

        // showNotification();
        mNotificationManager.notify(TRACKING_NOTIFICATION_ID, mTrackingAndSearchingNotificationBuilder.build());
    }

    private void updateNotificationSummary() {
        if (mDeviceCurrentlySearchingFor != null) {
            if (isPaused()) {
                mNotificationSummary = getString(R.string.notification_pause_and_searching_format, mDeviceCurrentlySearchingFor);
            } else { // probably tracking
                mNotificationSummary = getString(R.string.notification_tracking_and_searching_format, mDeviceCurrentlySearchingFor);
            }
        } else {
            if (isPaused()) {
                mNotificationSummary = getString(R.string.notification_paused);
            } else { // probably tracking
                mNotificationSummary = getString(R.string.notification_tracking);
            }
        }

        mTrackingAndSearchingNotificationBuilder.setContentText(mNotificationSummary);
    }

    /***********************************************************************************************/
    /* Worker methods for handling tracking                                                        */
    private void notifySearchingStartedFor(String deviceName) {
        mDeviceCurrentlySearchingFor = deviceName;
        updateNotificationSummary();

        if (isTracking()) {
            addActionsToNotificationBuilder();
            // showNotification();
        }
    }

    private void notifySearchingFinished() {
        mDeviceCurrentlySearchingFor = null;
        updateNotificationSummary();

        if (isTracking()) {
            addActionsToNotificationBuilder();
            //  showNotification();
        }
    }

    /**
     * only used in emulated environment, never in real life!
     */
    @Override
    public void onTerminate() {
        super.onTerminate();
        if (DEBUG) Log.i(TAG, "onTerminated");

        unregisterReceiver(mStopTrackingReceiver);
        unregisterReceiver(mStartTrackingReceiver);
        unregisterReceiver(mTrackingStartedReceiver);
        unregisterReceiver(mTrackingStoppedReceiver);

        unregisterReceiver(mSearchingFinishedReceiver);
        unregisterReceiver(mSearchingStartedReceiver);

    }

    public Notification getSearchingAndTrackingNotification() {
        return mTrackingAndSearchingNotificationBuilder.getNotification();
    }

    public void startPebbleWatchapp() {
        if (DEBUG) Log.d(TAG, "startPebbleWatchapp()");
        if (pebbleSupport() && startedWatchapp == null) {
            switch (getPebbleWatchapp()) {
                case BUILD_IN:
                    if (DEBUG) Log.d(TAG, "starting build in Pebble Watchapp Service");
                    startService(new Intent(this, PebbleServiceBuildIn.class));
                    break;

                case TRAINING_TRACKER:
                    if (DEBUG) Log.d(TAG, "starting Training Tracker Pebble Watchapp Service");
                    startService(new Intent(this, PebbleService.class));
                    break;
            }
            startedWatchapp = getPebbleWatchapp();
        }
    }

    public void stopPebbleWatchapp() {
        if (DEBUG) Log.d(TAG, "stopPebbleWatchapp()");
        if (startedWatchapp != null) {
            switch (startedWatchapp) {
                case BUILD_IN:
                    stopService(new Intent(this, PebbleServiceBuildIn.class));
                    break;

                case TRAINING_TRACKER:
                    stopService(new Intent(this, PebbleService.class));
                    break;
            }
        }
        startedWatchapp = null;
    }

    public void setIsSegmentListUpdating(long sportTypeId, boolean isUpdating) {
        if (DEBUG)
            Log.i(TAG, "setIsSegmentListUpdating, sportTypeId=" + sportTypeId + ", isUpdating=" + isUpdating);

        mSegmentListUpdating.put(sportTypeId, isUpdating);
    }

    public boolean isSegmentListUpdating(long sportTypeId) {
        if (mSegmentListUpdating.containsKey(sportTypeId)) {
            if (DEBUG)
                Log.i(TAG, "isSegmentListUpdating(" + sportTypeId + "):  key exists: " + mSegmentListUpdating.get(sportTypeId));
            return mSegmentListUpdating.get(sportTypeId);
        } else {
            if (DEBUG) Log.i(TAG, "isSegmentListUpdating(" + sportTypeId + "):  no key exists");
            return false;
        }
    }

    public void setIsLeaderboardUpdating(Long segmentId, boolean isUpdating) {
        mLeaderboardUpdating.put(segmentId, isUpdating);
    }

    public boolean isLeaderboardUpdating(Long segmentId) {
        if (mLeaderboardUpdating.containsKey(segmentId)) {
            return mLeaderboardUpdating.get(segmentId);
        } else {
            return false;
        }
    }

    public long getWorkoutID() {
        return mWorkoutID;
    }

    public void setWorkoutID(long workoutID) {
        mWorkoutID = workoutID;
    }

    public void todo(Context context, String text) {
        Toast.makeText(context, "TODO: " + text, Toast.LENGTH_SHORT).show();
    }

    public void setTracking() {
        cTrackingMode = TrackingMode.TRACKING;
    }


    /***********************************************************************************************/
    /*  Broadcast Receivers for handling the tracking state                                        */
    protected void startTracking() {
        if (!cResumeFromCrash) {
            sendBroadcast(new Intent(BANALService.RESET_ACCUMULATORS_INTENT));
        }

        if (startSearchWhenTrackingStarts()) {
            sendBroadcast(new Intent(REQUEST_START_SEARCH_FOR_PAIRED_DEVICES));
        }

        Intent intent = new Intent(this, TrackerService.class);
        if (cResumeFromCrash) {
            intent.putExtra(TrackerService.START_TYPE, TrackerService.StartType.RESUME_BY_USER.name());
        } else {
            intent.putExtra(TrackerService.START_TYPE, TrackerService.StartType.START_NORMAL.name());
        }
        startService(intent);

        startPebbleWatchapp();

        cTrackingMode = TrackingMode.TRACKING;
        notifyTrackingStateChanged();
    }

    protected void pauseTracking() {
        if (DEBUG) Log.d(TAG, "pause tracking");

        sendBroadcast(new Intent(REQUEST_NEW_LAP));

        cTrackingMode = TrackingMode.PAUSED;
        notifyTrackingStateChanged();
    }

    protected void resumeFromPaused() {
        if (DEBUG) Log.d(TAG, "resume tracking");

        if (TrainingApplication.startSearchWhenResumeFromPaused()) {
            sendBroadcast(new Intent(REQUEST_START_SEARCH_FOR_PAIRED_DEVICES));
        }

        sendBroadcast(new Intent(REQUEST_NEW_LAP));

        cTrackingMode = TrackingMode.TRACKING;
        notifyTrackingStateChanged();
    }

    protected void stopTracking() {
        stopPebbleWatchapp();

        stopService(new Intent(this, TrackerService.class));

        cTrackingMode = TrackingMode.READY;
        notifyTrackingStateChanged();
    }

    protected void trackingStopped() {
        sendBroadcast(new Intent(BANALService.RESET_ACCUMULATORS_INTENT));
        startWorkoutDetailsActivity(mWorkoutID, WorkoutDetailsActivity.SelectedFragment.EDIT_DETAILS);
        mNotificationManager.cancel(TRACKING_NOTIFICATION_ID);
    }

    protected void notifyTrackingStateChanged() {
        if (DEBUG) Log.i(TAG, "notifyTrackingStateChanged");
        addActionsToNotificationBuilder();
        updateNotificationSummary();

        sendBroadcast(new Intent(TRACKING_STATE_CHANGED));
    }


    private void createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager notificationManager = getSystemService(NotificationManager.class);

            // Channel for Tracking
            NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL__TRACKING,
                    getString(R.string.notification_channel_name__tracking),
                    NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription(getString(R.string.notification_channel_description__tracking));
            notificationManager.createNotificationChannel(channel); // Register the channel with the system;

            channel = new NotificationChannel(NOTIFICATION_CHANNEL__EXPORT,
                    getString(R.string.notification_channel_name__export),
                    NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription(getString(R.string.notification_channel_description__export));
            notificationManager.createNotificationChannel(channel);
        }
    }



}
