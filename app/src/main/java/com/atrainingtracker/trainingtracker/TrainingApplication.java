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

import android.annotation.SuppressLint;
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
import android.os.Bundle;
import android.preference.PreferenceManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
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
import com.atrainingtracker.trainingtracker.ui.aftermath.editworkout.EditWorkoutActivity;
import com.atrainingtracker.trainingtracker.ui.aftermath.TrackOnMapAftermathActivity;
import com.dropbox.core.json.JsonReadException;
import com.dropbox.core.oauth.DbxCredential;

import java.util.HashMap;
import java.util.Locale;

public class TrainingApplication extends Application {
    private static final boolean DEBUG = true;
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
    public static final String SP_STRAVA_REFRESH_TOKEN = "stravaRefreshToken";
    public static final String SP_STRAVA_TOKEN_EXPIRES_AT = "stravaTokenExpiresAt";
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
    protected static final String NOTIFICATION_CHANNEL__TRACKING_2 = "NOTIFICATION_CHANNEL__TRACKING_2";
    public static final String NOTIFICATION_CHANNEL__EXPORT = "NOTIFICATION_CHANNEL__EXPORT";
    public static final int TRACKING_NOTIFICATION_ID = 1;
    public static final int EXPORT_PROGRESS_NOTIFICATION_ID = 2;
    public static final int EXPORT_RESULT_NOTIFICATION_ID = 3;
    public static final int SEND_EMAIL_NOTIFICATION_ID = 4;

    public static final float MIN_DISTANCE_BETWEEN_START_AND_STOP = 100;
    public static final double DISTANCE_TO_MAX_THRESHOLD_FOR_TRAINER = 200;
    public static final double DISTANCE_TO_MAX_RATIO_FOR_COMMUTE = Math.PI / 2; // probably the best value ;-)
    protected static final String SP_DROPBOX_CREDENTIAL = "dropboxCredential";
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
    private static final String APPLICATION_NAME = TAG;
    protected static Context cAppContext;
    @NonNull
    protected static TrackingMode cTrackingMode = TrackingMode.READY;
    protected static boolean cResumeFromCrash = false;
    private static SharedPreferences cSharedPreferences;
    private final IntentFilter mSearchingFinishedFilter = new IntentFilter(BANALService.SEARCHING_FINISHED_FOR_ALL_INTENT);
    private final IntentFilter mSearchingStartedFilter = new IntentFilter(BANALService.SEARCHING_STARTED_FOR_ONE_INTENT);
    public TrackOnMapHelper trackOnMapHelper;
    public SegmentOnMapHelper segmentOnMapHelper;
    private final HashMap<Long, Boolean> mSegmentListUpdating = new HashMap<>();
    private long mWorkoutID = -1;
    protected final BroadcastReceiver mTrackingStartedReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, @NonNull Intent intent) {
            setWorkoutID(intent.getLongExtra(WorkoutSummariesDatabaseManager.WorkoutSummaries.WORKOUT_ID, -1));
        }
    };
    @Nullable
    private Watchapp startedWatchapp = null;
    protected final BroadcastReceiver mTrackingStoppedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            trackingStopped();
        }
    };
    private NotificationManagerCompat mNotificationManager;
    private NotificationCompat.Builder mTrackingAndSearchingNotificationBuilder;
    @NonNull
    private String mNotificationSummary = "searching";
    private final DistanceFormatter mDistanceFormatter = new DistanceFormatter();

    // protected void showNotification()
    // {
    //     mNotificationManager.notify(TRACKING_NOTIFICATION_ID, mTrackingAndSearchingNotificationBuilder.getNotification());
    // }
    private final TimeFormatter mTimeFormatter = new TimeFormatter();
    @Nullable
    private String mDeviceCurrentlySearchingFor = null;
    /***********************************************************************************************/

    public static boolean getDebug(boolean defaultVal) {
        return DEBUG && defaultVal;
    }

    protected final BroadcastReceiver mStartTrackingReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            startTracking();
        }
    };
    protected final BroadcastReceiver mStopTrackingReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            stopTracking();
        }
    };
    protected final BroadcastReceiver mPauseTrackingReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            pauseTracking();
        }
    };
    protected final BroadcastReceiver mResumeFromPaused = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            resumeFromPaused();
        }
    };
    private final BroadcastReceiver mSearchingFinishedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            notifySearchingFinished();
        }
    };
    private final BroadcastReceiver mSearchingStartedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, @NonNull Intent intent) {
            notifySearchingStartedFor(intent.getStringExtra(BANALService.DEVICE_NAME));
        }
    };

    public static boolean havePermission(@NonNull String permission) {
        return ActivityCompat.checkSelfPermission(cAppContext, permission) == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean isAppInstalled(@NonNull String uri) {
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
        if (numberOfSearchTries == null || numberOfSearchTries.isEmpty()) {
            return DEFAULT_NUMBER_OF_SEARCH_TRIES;
        } else {
            try {
                return Integer.parseInt(numberOfSearchTries);
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

    @NonNull
    public static String getSpEmailAddress() {
        return cSharedPreferences.getString(SP_EMAIL_ADDRESS, "first.last@example.com");
    }

    @NonNull
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

    @NonNull
    public static MyUnits getUnit() {
        return MyUnits.valueOf(cSharedPreferences.getString(SP_UNITS, MyUnits.METRIC.name()));
    }

    public static boolean pebbleSupport() {
        return cSharedPreferences.getBoolean(SP_PEBBLE_SUPPORT, false);
    }

    @NonNull
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

    @NonNull
    public static String getAppName() {
        return cAppContext.getString(R.string.application_name);
    }

    public static double getMinWalkSpeed_UserUnits() {
        return MyHelper.string2Double(cSharedPreferences.getString(MIN_WALK_SPEED, String.format(Locale.getDefault(), "%f", MyHelper.mps2userUnit(DEFAULT_MIN_WALK_SPEED_mps))));
    }

    public static double getMaxWalkSpeed_UserUnits() {
        return MyHelper.string2Double(cSharedPreferences.getString(MAX_WALK_SPEED, String.format(Locale.getDefault(), "%f", MyHelper.mps2userUnit(DEFAULT_MAX_WALK_SPEED_mps))));
    }

    public static double getMaxRunSpeed_UserUnits() {
        return MyHelper.string2Double(cSharedPreferences.getString(MAX_RUN_SPEED, String.format(Locale.getDefault(), "%f", MyHelper.mps2userUnit(DEFAULT_MAX_RUN_SPEED_mps))));
    }

    public static double getMaxMTBSpeed_UserUnits() {
        return MyHelper.string2Double(cSharedPreferences.getString(MAX_MTB_SPEED, String.format(Locale.getDefault(), "%f", MyHelper.mps2userUnit(DEFAULT_MAX_MTB_SPEED_mps))));
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

    public static void storeDropboxCredential(DbxCredential dbxCredential) {
        cSharedPreferences.edit().putString(SP_DROPBOX_CREDENTIAL, DbxCredential.Writer.writeToString(dbxCredential)).apply();
    }

    public static DbxCredential readDropboxCredential() {
        String credential = cSharedPreferences.getString(SP_DROPBOX_CREDENTIAL, null);
        DbxCredential dbxCredential = null;
        try {
            dbxCredential = DbxCredential.Reader.readFully(credential);
        } catch (JsonReadException e) {
            // do nothing
        }
        return dbxCredential;
    }

    public static void deleteDropboxCredential() {
        cSharedPreferences.edit().remove(SP_DROPBOX_CREDENTIAL).apply();
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

    @Nullable
    public static String getStravaAccessToken() {
        return cSharedPreferences.getString(SP_STRAVA_TOKEN, null);
    }

    public static void setStravaAccessToken(String token) {
        cSharedPreferences.edit().putString(SP_STRAVA_TOKEN, token).apply();
    }

    public static void setStravaRefreshToken(String refreshToken) {
        cSharedPreferences.edit().putString(SP_STRAVA_REFRESH_TOKEN, refreshToken).apply();
    }
    @Nullable
    public static String getStravaRefreshToken() {
        return cSharedPreferences.getString(SP_STRAVA_REFRESH_TOKEN, null);
    }

    public static void setStravaTokenExpiresAt(int expiresAt) {
        cSharedPreferences.edit().putInt(SP_STRAVA_TOKEN_EXPIRES_AT, expiresAt).apply();
    }
    public static int getStravaTokenExpiresAt() {
        return cSharedPreferences.getInt(SP_STRAVA_TOKEN_EXPIRES_AT, 0);
    }

    public static void deleteStravaToken() {
        if (DEBUG) Log.i(TAG, "deleteStravaToken");
        cSharedPreferences.edit().remove(TrainingApplication.SP_STRAVA_TOKEN).apply();
        cSharedPreferences.edit().putBoolean(SP_UPLOAD_TO_STRAVA, false).apply();
        cSharedPreferences.edit().remove(SP_STRAVA_ATHLETE_ID).apply();
        if (DEBUG) Log.i(TAG, "end of deleteStravaToken");
    }

    @NonNull
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

    @Nullable
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
        cSharedPreferences.edit().putBoolean(SP_UPLOAD_TO_RUNKEEPER, value).apply();
    }

    @Nullable
    public static String getTrainingPeaksAccessToken() {
        return cSharedPreferences.getString(SP_TRAINING_PEAKS_ACCESS_TOKEN, null);
    }

    public static void setTrainingPeaksAccessToken(String token) {
        cSharedPreferences.edit().putString(SP_TRAINING_PEAKS_ACCESS_TOKEN, token).apply();
    }

    @Nullable
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

    @Deprecated
    public static boolean defaultToPrivate() {  // in the past, it was possible to mark activities in Strava as private via the API.  Unfortunately, this is no longer possible.
        return false;
        // cSharedPreferences.getBoolean(SP_DEFAULT_TO_PRIVATE, false);
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

    public static boolean exportToFile(@NonNull FileFormat fileFormat) {
        return switch (fileFormat) {
            case CSV -> exportToCSV();
            case GC -> exportToGCJson();
            case TCX -> exportToTCX();
            case GPX -> exportToGPX();
            case STRAVA -> uploadToStrava();
            /* case RUNKEEPER:
                return uploadToRunKeeper(); */
            /* case TRAINING_PEAKS:
                return uploadToTrainingPeaks(); */
        };
    }

    public static boolean exportViaEmail(FileFormat fileFormat) {
        if (sendEmail()) {
            return switch (fileFormat) {
                case CSV -> sendCSVEmail();
                case GC -> sendGCEmail();
                case TCX -> sendTCXEmail();
                case GPX -> sendGPXEmail();
                default -> false;
            };
        } else {
            return false;
        }
    }

    public static boolean uploadToCommunity(@NonNull FileFormat fileFormat) {
        switch (fileFormat) {
            case STRAVA:
                return uploadToStrava();
            /* case RUNKEEPER:
                return uploadToRunKeeper(); */
            /* case TRAINING_PEAKS:
                return uploadToTrainingPeaks(); */
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

    @NonNull
    public static TrackingMode getTrackingMode() {
        return cTrackingMode;
    }

    public static void setResumeFromCrash(boolean resumeFromCrash) {
        cResumeFromCrash = resumeFromCrash;
    }

    public static void startTrackOnMapAftermathActivity(Context context, long workoutId) {
        if (DEBUG) Log.i(TAG, "startTrackOnMapAftermathActivity(" + workoutId + ")");

        TrackOnMapAftermathActivity.start(context, workoutId);
    }

    // TODO: remove cAppContext and FLAG_ACTIVITY_NEW_TASK from here
    public static void startEditWorkoutActivity(long workoutId, boolean showAllDetails) {
        if (DEBUG) Log.i(TAG, "startEditWorkoutActivity(" + workoutId + ")");

        Bundle bundle = new Bundle();
        bundle.putLong(WorkoutSummariesDatabaseManager.WorkoutSummaries.WORKOUT_ID, workoutId);

        bundle.putBoolean(EditWorkoutActivity.EXTRA_SHOW_DETAILS, showAllDetails);
        bundle.putBoolean(EditWorkoutActivity.EXTRA_SHOW_EXTREMA, showAllDetails);
        bundle.putBoolean(EditWorkoutActivity.EXTRA_SHOW_MAP, showAllDetails);

        Intent intent = new Intent(cAppContext, EditWorkoutActivity.class);
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

        ContextCompat.registerReceiver(this, mSearchingFinishedReceiver, mSearchingFinishedFilter, ContextCompat.RECEIVER_NOT_EXPORTED);
        ContextCompat.registerReceiver(this, mSearchingStartedReceiver, mSearchingStartedFilter, ContextCompat.RECEIVER_NOT_EXPORTED);

        ContextCompat.registerReceiver(this, mStartTrackingReceiver, new IntentFilter(REQUEST_START_TRACKING), ContextCompat.RECEIVER_NOT_EXPORTED);
        ContextCompat.registerReceiver(this, mTrackingStartedReceiver, new IntentFilter(TrackerService.TRACKING_STARTED_INTENT), ContextCompat.RECEIVER_NOT_EXPORTED);
        ContextCompat.registerReceiver(this, mStopTrackingReceiver, new IntentFilter(REQUEST_STOP_TRACKING), ContextCompat.RECEIVER_NOT_EXPORTED);
        ContextCompat.registerReceiver(this, mTrackingStoppedReceiver, new IntentFilter(TrackerService.TRACKING_FINISHED_INTENT), ContextCompat.RECEIVER_NOT_EXPORTED);
        ContextCompat.registerReceiver(this, mPauseTrackingReceiver, new IntentFilter(REQUEST_PAUSE_TRACKING), ContextCompat.RECEIVER_NOT_EXPORTED);
        ContextCompat.registerReceiver(this, mResumeFromPaused, new IntentFilter(REQUEST_RESUME_FROM_PAUSED), ContextCompat.RECEIVER_NOT_EXPORTED);

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
    @NonNull
    protected NotificationCompat.Builder getNewNotificationBuilder() {
        Bundle bundle = new Bundle();
        bundle.putString(MainActivityWithNavigation.SELECTED_FRAGMENT, MainActivityWithNavigation.SelectedFragment.START_OR_TRACKING.name());

        Intent newIntent = new Intent(this, MainActivityWithNavigation.class);
        newIntent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        newIntent.putExtras(bundle);
        newIntent.setAction("TrackerService");
        PendingIntent mStartMainActivityPendingIntent = PendingIntent.getActivity(this, 0, newIntent, PendingIntent.FLAG_IMMUTABLE);  // TODO: correct???

        return new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL__TRACKING_2)
                .setSmallIcon(R.drawable.logo)
                .setContentTitle(getString(R.string.TrainingTracker))
                .setContentText(getString(R.string.notification_tracking))
                .setContentIntent(mStartMainActivityPendingIntent);
    }

    @SuppressLint("RestrictedApi")
    protected void addActionsToNotificationBuilder() {
        if (DEBUG) Log.i(TAG, "addActionsToNotificationBuilder");

        // first, remove all previously added actions
        // therefore, it seems to be best to use mActions.clear() as suggested in https://stackoverflow.com/questions/23922992/android-remove-action-button-from-notification
        if (mTrackingAndSearchingNotificationBuilder.mActions != null) {
            mTrackingAndSearchingNotificationBuilder.mActions.clear();
        }

        if (!BANALService.isSearching()) {
            mTrackingAndSearchingNotificationBuilder.addAction(
                    (new NotificationCompat.Action.Builder(R.drawable.research_icon, getString(R.string.research),
                        PendingIntent.getBroadcast(this, 0,
                                new Intent(REQUEST_START_SEARCH_FOR_PAIRED_DEVICES).setPackage(getPackageName()), PendingIntent.FLAG_IMMUTABLE)))
                    .build());
        }

        if (isPaused()) {
            mTrackingAndSearchingNotificationBuilder.addAction(
                    (new NotificationCompat.Action.Builder(R.drawable.control_start, getString(R.string.Resume),
                        PendingIntent.getBroadcast(this, 0,
                                new Intent(REQUEST_RESUME_FROM_PAUSED).setPackage(getPackageName()), PendingIntent.FLAG_IMMUTABLE)))
                    .build());

            mTrackingAndSearchingNotificationBuilder.addAction(
                    (new NotificationCompat.Action.Builder(R.drawable.control_stop, getString(R.string.Stop),
                        PendingIntent.getBroadcast(this, 0,
                                new Intent(REQUEST_STOP_TRACKING).setPackage(getPackageName()), PendingIntent.FLAG_IMMUTABLE)))
                    .build());
        } else {
            mTrackingAndSearchingNotificationBuilder.addAction(
                    (new NotificationCompat.Action.Builder(R.drawable.control_pause, getString(R.string.Pause),
                        PendingIntent.getBroadcast(this, 0,
                                new Intent(REQUEST_PAUSE_TRACKING).setPackage(getPackageName()), PendingIntent.FLAG_IMMUTABLE)))
                    .build());
        }
    }

    public void updateTimeAndDistanceToNotification(@Nullable SensorData<Integer> time, @Nullable SensorData<Number> distance, String sportType) {
        if (DEBUG) Log.i(TAG, "updateTimeAndDistanceToNotification(" + time + ", " + distance);

        if (mNotificationManager.areNotificationsEnabled()) {
            String sTime = mTimeFormatter.format_with_units(time == null ? null : time.getValue());
            String sDistance = mDistanceFormatter.format_with_units(distance == null ? null : distance.getValue());

            // mTrackingAndSearchingNotificationBuilder.setDefaults(Notification.DEFAULT_ALL) // requires VIBRATE permission
            mTrackingAndSearchingNotificationBuilder.setStyle(new NotificationCompat.BigTextStyle()
                    .bigText(getString(R.string.tracking_details_format, mNotificationSummary, sDistance, sportType, sTime)));

            // showNotification();
            mNotificationManager.notify(TRACKING_NOTIFICATION_ID, mTrackingAndSearchingNotificationBuilder.build());
        }
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

    @NonNull
    public Notification getSearchingAndTrackingNotification() {
        return mTrackingAndSearchingNotificationBuilder.build();
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
            sendBroadcast(new Intent(BANALService.RESET_ACCUMULATORS_INTENT)
                    .setPackage(getPackageName()));
        }

        if (startSearchWhenTrackingStarts()) {
            sendBroadcast(new Intent(REQUEST_START_SEARCH_FOR_PAIRED_DEVICES)
                    .setPackage(getPackageName()));
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

        sendBroadcast(new Intent(REQUEST_NEW_LAP)
                .setPackage(getPackageName()));

        cTrackingMode = TrackingMode.PAUSED;
        notifyTrackingStateChanged();
    }

    protected void resumeFromPaused() {
        if (DEBUG) Log.d(TAG, "resume tracking");

        if (TrainingApplication.startSearchWhenResumeFromPaused()) {
            sendBroadcast(new Intent(REQUEST_START_SEARCH_FOR_PAIRED_DEVICES)
                    .setPackage(getPackageName()));
        }

        sendBroadcast(new Intent(REQUEST_NEW_LAP)
                .setPackage(getPackageName()));

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
        sendBroadcast(new Intent(BANALService.RESET_ACCUMULATORS_INTENT)
                .setPackage(getPackageName()));
        startEditWorkoutActivity(mWorkoutID, true); // here, the EditWorkoutActivity shall show the details, extrema values and the map.
        mNotificationManager.cancel(TRACKING_NOTIFICATION_ID);
    }

    protected void notifyTrackingStateChanged() {
        if (DEBUG) Log.i(TAG, "notifyTrackingStateChanged");
        addActionsToNotificationBuilder();
        updateNotificationSummary();

        sendBroadcast(new Intent(TRACKING_STATE_CHANGED)
                .setPackage(getPackageName()));
    }


    private void createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        NotificationManager notificationManager = getSystemService(NotificationManager.class);

        notificationManager.deleteNotificationChannel(NOTIFICATION_CHANNEL__TRACKING);

        // Channel for Tracking
        NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL__TRACKING_2,
                getString(R.string.notification_channel_name__tracking),
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(getString(R.string.notification_channel_description__tracking));
        notificationManager.createNotificationChannel(channel); // Register the channel with the system;

        channel = new NotificationChannel(NOTIFICATION_CHANNEL__EXPORT,
                getString(R.string.notification_channel_name__export),
                NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription(getString(R.string.notification_channel_description__export));
        notificationManager.createNotificationChannel(channel);
    }



}