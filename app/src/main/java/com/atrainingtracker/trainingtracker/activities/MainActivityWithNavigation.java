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

package com.atrainingtracker.trainingtracker.activities;

import android.Manifest;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;

import com.atrainingtracker.banalservice.ui.SportTypeListFragment;
import com.atrainingtracker.banalservice.ui.devices.devicetabs.DevicesTabbedContainerFragment;
import com.atrainingtracker.banalservice.ui.devices.editdevice.EditDeviceFragmentFactory;
import com.atrainingtracker.trainingtracker.onlinecommunities.strava.StravaHelper;
import com.atrainingtracker.trainingtracker.segments.StarredSegmentsTabbedContainer;
import com.atrainingtracker.trainingtracker.tracker.TrackerService;
import com.google.android.material.navigation.NavigationView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;
import androidx.appcompat.widget.Toolbar;

import android.provider.Settings;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import com.atrainingtracker.R;
import com.atrainingtracker.banalservice.ActivityType;
import com.atrainingtracker.banalservice.BANALService;
import com.atrainingtracker.banalservice.devices.DeviceType;
import com.atrainingtracker.banalservice.Protocol;
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager;
import com.atrainingtracker.banalservice.dialogs.InstallANTShitDialog;
import com.atrainingtracker.banalservice.filters.FilterData;
import com.atrainingtracker.banalservice.helpers.BatteryStatusHelper;
import com.atrainingtracker.trainingtracker.TrainingApplication;
import com.atrainingtracker.trainingtracker.database.TrackingViewsDatabaseManager;
import com.atrainingtracker.trainingtracker.dialogs.EnableBluetoothDialog;
import com.atrainingtracker.trainingtracker.dialogs.GPSDisabledDialog;
import com.atrainingtracker.trainingtracker.dialogs.StartOrResumeDialog;
import com.atrainingtracker.trainingtracker.fragments.StartAndTrackingFragmentTabbedContainer;
import com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist.WorkoutSummariesListFragment;
import com.atrainingtracker.trainingtracker.fragments.mapFragments.MyLocationsFragment;
import com.atrainingtracker.trainingtracker.fragments.mapFragments.TrackOnMapTrackingFragment;
import com.atrainingtracker.trainingtracker.fragments.preferences.AltitudeCorrectionFragment;
import com.atrainingtracker.trainingtracker.fragments.preferences.CloudUploadFragment;
import com.atrainingtracker.trainingtracker.fragments.preferences.DisplayFragment;
import com.atrainingtracker.trainingtracker.fragments.preferences.EmailUploadFragment;
import com.atrainingtracker.trainingtracker.fragments.preferences.FancyWorkoutNameListFragment;
import com.atrainingtracker.trainingtracker.fragments.preferences.FileExportFragment;
import com.atrainingtracker.trainingtracker.fragments.preferences.LocationSourcesFragment;
import com.atrainingtracker.trainingtracker.fragments.preferences.PebbleScreenFragment;
import com.atrainingtracker.trainingtracker.fragments.preferences.RootPrefsFragment;
import com.atrainingtracker.trainingtracker.fragments.preferences.RunkeeperUploadFragment;
import com.atrainingtracker.trainingtracker.fragments.preferences.SearchFragment;
import com.atrainingtracker.trainingtracker.fragments.preferences.StartSearchFragment;
import com.atrainingtracker.trainingtracker.fragments.preferences.StravaUploadFragment;
import com.atrainingtracker.trainingtracker.fragments.preferences.TrainingpeaksUploadFragment;
import com.atrainingtracker.trainingtracker.interfaces.RemoteDevicesSettingsInterface;
import com.atrainingtracker.trainingtracker.interfaces.StartOrResumeInterface;
import com.atrainingtracker.trainingtracker.segments.SegmentsDatabaseManager;
import com.atrainingtracker.trainingtracker.segments.StarredSegmentsListFragment;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

// import android.support.v7.app.AlertDialog;


/**
 * Created by rainer on 14.01.16.
 */
public class MainActivityWithNavigation
        extends AppCompatActivity
        implements
        NavigationView.OnNavigationItemSelectedListener,
        RemoteDevicesSettingsInterface,
        BANALService.GetBanalServiceInterface,
        PreferenceFragmentCompat.OnPreferenceStartScreenCallback,
        StartAndTrackingFragmentTabbedContainer.UpdateActivityTypeInterface,
        StarredSegmentsListFragment.StartSegmentDetailsActivityInterface,
        StartOrResumeInterface {
    public static final String SELECTED_FRAGMENT_ID = "SELECTED_FRAGMENT_ID";
    public static final String SELECTED_FRAGMENT = "SELECTED_FRAGMENT";
    private static final boolean DEBUG = TrainingApplication.getDebug(false);
    private static final String TAG = "MainActivityWithNavigat";
    private static final int DEFAULT_SELECTED_FRAGMENT_ID = R.id.drawer_start_tracking;
    // private static final int REQUEST_ENABLE_BLUETOOTH            = 1;
    private static final int REQUEST_INSTALL_GOOGLE_PLAY_SERVICE = 2;
    private static final int MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 1;
    // todo new perms
    private static final long WAITING_TIME_BEFORE_DISCONNECTING = 5 * 60 * 1000; // 5 min
    private static final int CRITICAL_BATTERY_LEVEL = 30;
    protected TrainingApplication mTrainingApplication;
    // remember which fragment should be shown
    protected int mSelectedFragmentId = DEFAULT_SELECTED_FRAGMENT_ID;
    // the views
    protected DrawerLayout mDrawerLayout;
    protected NavigationView mNavigationView;
    protected MenuItem mPreviousMenuItem;
    @Nullable
    protected Fragment mFragment;
    protected Handler mHandler;  // necessary to wait some time before we disconnect from the BANALService when the app is paused.
    protected boolean mStartAndNotResume = true;        // start a new workout or continue with the previous one
    @Nullable
    protected BANALService.BANALServiceComm mBanalServiceComm = null;
    final LinkedList<ConnectionStatusListener> mConnectionStatusListeners = new LinkedList<>();
    /* Broadcast Receiver to adapt the title based on the tracking state */
    final BroadcastReceiver mStartTrackingReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            setTitle(R.string.Tracking);
            mNavigationView.getMenu().findItem(R.id.drawer_start_tracking).setTitle(R.string.Tracking);
        }
    };

    // protected ActivityType mActivityType = ActivityType.GENERIC;  // no longer necessary since we have the getActivity() method
    // protected long mWorkoutID = -1;
    final BroadcastReceiver mPauseTrackingReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            setTitle(R.string.Paused);
            mNavigationView.getMenu().findItem(R.id.drawer_start_tracking).setTitle(R.string.Pause);
        }
    };
    final BroadcastReceiver mStopTrackingReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            setTitle(R.string.app_name);
            mNavigationView.getMenu().findItem(R.id.drawer_start_tracking).setTitle(R.string.Start);

            checkBatteryStatus();
        }
    };
    protected final BroadcastReceiver mTrackingStoppedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // show the workout list
            mSelectedFragmentId = R.id.drawer_workouts;
            onNavigationItemSelected(mNavigationView.getMenu().findItem(mSelectedFragmentId));
        }
    };

    private IntentFilter mStartTrackingFilter;
    private boolean mAlreadyTriedToRequestDropboxToken = false;
    // class BANALConnection implements ServiceConnection
    private final ServiceConnection mBanalConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName name, IBinder service) {
            if (DEBUG) Log.i(TAG, "onServiceConnected");

            mBanalServiceComm = (BANALService.BANALServiceComm) service; // IBANALService.Stub.asInterface(service);

            // create all the filters
            DevicesDatabaseManager devicesDatabaseManager = DevicesDatabaseManager.getInstance(getApplicationContext());
            for (FilterData filterData : TrackingViewsDatabaseManager.getInstance(getApplicationContext()).getAllFilterData(devicesDatabaseManager)) {
                mBanalServiceComm.createFilter(filterData);
            }

            // inform listeners
            for (ConnectionStatusListener connectionStatusListener : mConnectionStatusListeners) {
                connectionStatusListener.connectedToBanalService();
            }
        }

        public void onServiceDisconnected(ComponentName name) {
            if (DEBUG) Log.i(TAG, "onServiceDisconnected");

            mBanalServiceComm = null;

            // inform listeners
            for (ConnectionStatusListener connectionStatusListener : mConnectionStatusListeners) {
                connectionStatusListener.disconnectedFromBanalService();
            }
        }
    };
    protected final Runnable mDisconnectFromBANALServiceRunnable = new Runnable() {
        @Override
        public void run() {
            disconnectFromBANALService();
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (DEBUG) Log.d(TAG, "onCreate");

        // some initialization
        mTrainingApplication = (TrainingApplication) getApplication();
        mHandler = new Handler();

        mStartTrackingFilter = new IntentFilter(TrainingApplication.REQUEST_START_TRACKING);
        mStartTrackingFilter.addAction(TrainingApplication.REQUEST_RESUME_FROM_PAUSED);

        // now, create the UI
        setContentView(R.layout.main_activity_with_navigation);

        Toolbar toolbar = findViewById(R.id.apps_toolbar);
        setSupportActionBar(toolbar);

        final ActionBar supportAB = getSupportActionBar();
        // supportAB.setHomeAsUpIndicator(R.drawable.abc_ic_ab_back_mtrl_am_alpha);
        supportAB.setDisplayHomeAsUpEnabled(true);

        mDrawerLayout = findViewById(R.id.drawer_layout);

        ActionBarDrawerToggle actionBarDrawerToggle = new ActionBarDrawerToggle(this, mDrawerLayout, toolbar, R.string.TrainingTracker, R.string.TrainingTracker);
        actionBarDrawerToggle.syncState();

        mNavigationView = findViewById(R.id.nav_view);
        mNavigationView.setItemIconTintList(null);  // avoid converting the icons to black and white or gray and white
        mNavigationView.setNavigationItemSelectedListener(this);

        if (!BANALService.isProtocolSupported(this, Protocol.BLUETOOTH_LE)) {
            MenuItem menuItem = mNavigationView.getMenu().findItem(R.id.drawer_pairing_BTLE);
            menuItem.setEnabled(false);
            menuItem.setCheckable(false);
        }

        if (BANALService.isANTProperlyInstalled(this)) {
            MenuItem menuItem = mNavigationView.getMenu().findItem(R.id.drawer_pairing_ant);
            menuItem.setVisible(false);
        }

        // getPermissions
        getPermissions(true);

        // check ANT+ installation
        if (TrainingApplication.checkANTInstallation() && BANALService.isANTProperlyInstalled(this)) {
            showInstallANTShitDialog();
        }

        if (savedInstanceState != null) {
            mSelectedFragmentId = savedInstanceState.getInt(SELECTED_FRAGMENT_ID, DEFAULT_SELECTED_FRAGMENT_ID);
            mFragment = getSupportFragmentManager().getFragment(savedInstanceState, "mFragment");
        } else {
            if (getIntent().hasExtra(SELECTED_FRAGMENT)) {
                switch (SelectedFragment.valueOf(getIntent().getStringExtra(SELECTED_FRAGMENT))) {
                    case START_OR_TRACKING:
                        mSelectedFragmentId = R.id.drawer_start_tracking;
                        break;

                    case WORKOUT_LIST:
                        mSelectedFragmentId = R.id.drawer_workouts;
                        break;
                }
            }
            // now, create and show the main fragment
            onNavigationItemSelected(mNavigationView.getMenu().findItem(mSelectedFragmentId));
        }


        if (TrainingApplication.trackLocation()) {
            // check whether GPS is enabled
            LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
            if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                showGPSDisabledAlertToUser();
            }
        }

        // TODO: better place for this code?
        // PROBLEM: when play service is not installed, DeviceManager will start the unfiltered GPS.
        //          when then the play service is installed, the DeviceManager will not use the newly available filtered GPS stuff

        // check whether the google play service utils are installed
        Dialog dialog = GooglePlayServicesUtil.getErrorDialog(GooglePlayServicesUtil.isGooglePlayServicesAvailable(this), this, REQUEST_INSTALL_GOOGLE_PLAY_SERVICE);
        if (dialog != null) {  // so there is a problem with the Google Play Service
            // since there is no 'no' and 'do not ask again' button, we show this only several times, see the corresponding function of TrainingApplication
            if (TrainingApplication.showInstallPlayServicesDialog()) {
                dialog.show();
            }
        }
        ViewCompat.setOnApplyWindowInsetsListener(
                mDrawerLayout,
                new OnApplyWindowInsetsListener() {
                    @NonNull
                    @Override
                    public WindowInsetsCompat onApplyWindowInsets(
                            @NonNull View v, @NonNull WindowInsetsCompat windowInsets) {
                        Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
                        v.setPadding(insets.left, 0, insets.right, insets.bottom);
                        ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
                        mlp.topMargin = insets.top;
                        return WindowInsetsCompat.CONSUMED;
                    }
                });

        getOnBackPressedDispatcher().addCallback(this,
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        if (mDrawerLayout.isDrawerOpen(GravityCompat.START)) {
                            mDrawerLayout.closeDrawer(GravityCompat.START);
                        }  if (getSupportFragmentManager().getBackStackEntryCount() > 0) {  // when showing "deeper fragments", we only want to go back one step and not completely to the start_tracking fragment
                            getSupportFragmentManager().popBackStack();
                        } else if (getSupportFragmentManager().getBackStackEntryCount() == 0
                                && mSelectedFragmentId != R.id.drawer_start_tracking) {
                            onNavigationItemSelected(mNavigationView.getMenu().findItem(R.id.drawer_start_tracking));
                        } else {
                            finish();
                        }
                    }
                }
        );
    }

    @NonNull
    private List<String> getPermissions() {
        List<String> requiredPerms = new ArrayList<>();
        requiredPerms.add(Manifest.permission.ACCESS_FINE_LOCATION);
        requiredPerms.add(Manifest.permission.ACCESS_COARSE_LOCATION);

        // if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        //    requiredPerms.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION);
        //}

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S /* Android12, sdk31*/
                && (getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
                || getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH))) {
            requiredPerms.add(Manifest.permission.BLUETOOTH_CONNECT);
            requiredPerms.add(Manifest.permission.BLUETOOTH_SCAN);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requiredPerms.add(Manifest.permission.POST_NOTIFICATIONS);
        }

        return requiredPerms;
    }

    /**
     * Check that required permissions are allowed
     * Snippet borrowed from RunnerUp
     * @param popup
     */
    private void getPermissions(boolean popup) {
        boolean missingAnyPermission = false;
        List<String> requiredPerms = getPermissions();
        List<String> requestPerms = new ArrayList<>();

        for (final String perm : requiredPerms) {
            if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                missingAnyPermission = true;
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, perm)) {
                    // A denied permission, show motivation in a popup
                    String s = "Permission " + perm + " is explicitly denied";
                    Log.i(getClass().getName(), s);
                } else {
                    requestPerms.add(perm);
                }
            }
        }

        if (missingAnyPermission) {
            final String[] permissions = new String[requestPerms.size()];
            requestPerms.toArray(permissions);

            if (popup || !requestPerms.isEmpty()) {
                // Essential or requestable permissions missing
                String baseMessage = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                        ? getString(R.string.location_permission_text_Android12)
                        : Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                        ? getString(R.string.location_permission_text)
                        : getString(R.string.location_permission_text_pre_Android10);

                AlertDialog.Builder builder = new AlertDialog.Builder(this)
                        .setTitle(R.string.location_permission_required)
                        .setNegativeButton(R.string.Cancel, (dialog, which) -> dialog.dismiss());
                if (!requestPerms.isEmpty()) {
                    // Let Android request the permissions
                    builder.setPositiveButton(R.string.OK,
                                    (dialog, id) -> ActivityCompat.requestPermissions(this, permissions, MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION))
                            .setMessage(baseMessage + "\n" + getString(R.string.Request_permission_text));
                }
                else {
                    // Open settings for the app (no direct shortcut to permissions)
                    Intent intent = new Intent()
                            .setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            .setData(Uri.fromParts("package", getPackageName(), null));
                    builder.setPositiveButton(R.string.OK, (dialog, id) -> startActivity(intent))
                            .setMessage(baseMessage + "\n\n" + getString(R.string.Request_permission_text));
                }
                builder.show();
            }
        }
    }

    @SuppressLint("SourceLockedOrientationActivity")
    @Override
    protected void onResume() {
        super.onResume();
        if (DEBUG) Log.d(TAG, "onResume");

        if (mBanalServiceComm == null) {
            bindService(new Intent(this, BANALService.class), mBanalConnection, Context.BIND_AUTO_CREATE);
        }

        mHandler.removeCallbacks(mDisconnectFromBANALServiceRunnable);

        checkPreferences();

        getWindow().getDecorView().setKeepScreenOn(TrainingApplication.keepScreenOn());

        if (TrainingApplication.NoUnlocking()) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        }

        if (TrainingApplication.forcePortrait()) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }

        // register the receivers
        ContextCompat.registerReceiver(this, mStartTrackingReceiver, mStartTrackingFilter, ContextCompat.RECEIVER_NOT_EXPORTED);
        ContextCompat.registerReceiver(this, mPauseTrackingReceiver, new IntentFilter(TrainingApplication.REQUEST_PAUSE_TRACKING), ContextCompat.RECEIVER_NOT_EXPORTED);
        ContextCompat.registerReceiver(this, mStopTrackingReceiver, new IntentFilter(TrainingApplication.REQUEST_STOP_TRACKING), ContextCompat.RECEIVER_NOT_EXPORTED);
        ContextCompat.registerReceiver(this, mTrackingStoppedReceiver, new IntentFilter(TrackerService.TRACKING_FINISHED_INTENT), ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    // method to verify the preferences
    // when we shall upload to a platform there must be a token.
    // TODO: inform user when the settings are not valid?
    protected void checkPreferences() {
        // BUT not Dropbox since this case is part of the Auth procedure...
        // if (TrainingApplication.uploadToDropbox() && TrainingApplication.getDropboxToken() == null) {
        //     TrainingApplication.setUploadToDropbox(false);
        // }

        if (TrainingApplication.uploadToStrava() && TrainingApplication.getStravaAccessToken() == null) {
            TrainingApplication.setUploadToStrava(false);
        }

        if (TrainingApplication.uploadToStrava() && TrainingApplication.getStravaTokenExpiresAt() == 0) {
            Log.i(TAG, "migrating to new Strava OAuth");
            // TrainingApplication.setStravaTokenExpiresAt(1); // avoid starting the StravaGetAccessToken Activity again and again...
            // startActivityForResult(new Intent(this, StravaGetAccessTokenActivity.class), StravaUploadFragment.GET_STRAVA_ACCESS_TOKEN);
            StravaHelper.requestAccessToken(this);
        }

        if (TrainingApplication.uploadToRunKeeper() && TrainingApplication.getRunkeeperToken() == null) {
            TrainingApplication.setUploadToRunkeeper(false);
        }

        if (TrainingApplication.uploadToTrainingPeaks() && TrainingApplication.getTrainingPeaksRefreshToken() == null) {
            TrainingApplication.setUploadToTrainingPeaks(false);
        }

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (DEBUG)
            Log.i(TAG, "onActivityResult: requestCode=" + requestCode + ", resultCode=" + resultCode);

        switch (requestCode) {
            case REQUEST_INSTALL_GOOGLE_PLAY_SERVICE:
                if (GooglePlayServicesUtil.isGooglePlayServicesAvailable(this) == ConnectionResult.SUCCESS) {
                    // TODO: now, google play service is available, inform DeviceManager to change the shit
                } else {
                    // TODO: failed to install google play service
                }
                break;


//            case REQUEST_ENABLE_BLUETOOTH:
//                // TODO: copied code from ControlTrackingFragment
//                BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
//                BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();
//
//                // TODO: some more tries, then write own dialog and enable Bluetooth via enableBluetoothRequest()
//                if (bluetoothAdapter.isEnabled() ) {
//                    if (DEBUG) Log.i(TAG, "Bluetooth is now enabled");
//                    startPairing(Protocol.BLUETOOTH_LE);
//                }
//                else {
//                    if (DEBUG) Log.i(TAG, "Bluetooth is NOT enabled");
//                }
//
//                break;

            default:  // maybe someone else (like fragments) might be able to handle this
                if (DEBUG) Log.i(TAG, "requestCode not handled");
                super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle savedInstanceState) {
        if (DEBUG) Log.i(TAG, "onSaveInstanceState");

        //Save the fragment's instance
        if (mFragment != null && mFragment.isAdded()) {
            getSupportFragmentManager().putFragment(savedInstanceState, "mFragment", mFragment);
        }

        savedInstanceState.putInt(SELECTED_FRAGMENT_ID, mSelectedFragmentId);

        super.onSaveInstanceState(savedInstanceState);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (DEBUG) Log.d(TAG, "onPause");

        try {
            unregisterReceiver(mStartTrackingReceiver);
        } catch (IllegalArgumentException e) {
        }
        try {
            unregisterReceiver(mPauseTrackingReceiver);
        } catch (IllegalArgumentException e) {
        }
        try {
            unregisterReceiver(mStopTrackingReceiver);
        } catch (IllegalArgumentException e) {
        }
        try {
            unregisterReceiver(mTrackingStoppedReceiver);
        } catch (IllegalArgumentException ignored) {}


        mHandler.postDelayed(mDisconnectFromBANALServiceRunnable, WAITING_TIME_BEFORE_DISCONNECTING);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (DEBUG) Log.d(TAG, "onDestroy");

        disconnectFromBANALService();
    }

    @Override
    public boolean onNavigationItemSelected(@Nullable MenuItem menuItem) {
        if (DEBUG) Log.i(TAG, "onNavigationItemSelected");

        if (menuItem == null) {
            return false;
        }

        mDrawerLayout.closeDrawers();

        // uncheck previous menuItem
        if (mPreviousMenuItem != null) {
            mPreviousMenuItem.setChecked(false);
        }
        mPreviousMenuItem = menuItem;
        menuItem.setChecked(true);

        // just for debugging
        // if (DEBUG) Toast.makeText(getApplicationContext(), menuItem.getTitle(), Toast.LENGTH_SHORT).show();

        // save
        mSelectedFragmentId = menuItem.getItemId();

        mFragment = null;
        String tag = null;
        int titleId = R.string.app_name;

        switch (mSelectedFragmentId) {
            case R.id.drawer_start_tracking:
                mFragment = StartAndTrackingFragmentTabbedContainer.newInstance(getActivityType(), StartAndTrackingFragmentTabbedContainer.CONTROL_ITEM);
                tag = StartAndTrackingFragmentTabbedContainer.TAG;
                break;

            case R.id.drawer_map:
                mFragment = TrackOnMapTrackingFragment.newInstance();
                tag = TrackOnMapTrackingFragment.TAG;
                break;

            case R.id.drawer_segments:
                titleId = R.string.segments;
                mFragment = new StarredSegmentsTabbedContainer();
                tag = StarredSegmentsTabbedContainer.TAG;
                break;

            case R.id.drawer_workouts:
                titleId = R.string.tab_workouts;
                mFragment = new WorkoutSummariesListFragment();
                tag = WorkoutSummariesListFragment.TAG;
                break;

            case R.id.drawer_pairing_ant:
                titleId = R.string.pairing_ANT;
                // fragment = DeviceTypeChoiceFragment.newInstance(Protocol.ANT_PLUS);
                // tag = DeviceTypeChoiceFragment.TAG;

                // Log.i(TAG, "PluginVersionString=" + AntPluginPcc.getInstalledPluginsVersionString(this));
                // Log.i(TAG, "MissingDependencyName=" + AntPluginPcc.getMissingDependencyName());
                // Log.i(TAG, "MissingDependencyPackageName=" + AntPluginPcc.getMissingDependencyPackageName());
                // Log.i(TAG, "PATH_ANTPLUS_PLUGIN_PKG=" + AntPluginPcc.PATH_ANTPLUS_PLUGINS_PKG);

                mFragment = DevicesTabbedContainerFragment.newInstance(Protocol.ANT_PLUS, null);
                tag = DevicesTabbedContainerFragment.TAG;
                break;

            case R.id.drawer_pairing_BTLE:
                titleId = R.string.pairing_bluetooth;
                // fragment = DeviceTypeChoiceFragment.newInstance(Protocol.BLUETOOTH_LE);
                // tag = DeviceTypeChoiceFragment.TAG;
                mFragment = DevicesTabbedContainerFragment.newInstance(Protocol.BLUETOOTH_LE, null);
                tag = DevicesTabbedContainerFragment.TAG;
                break;

            case R.id.drawer_my_sensors:
                titleId = R.string.devices_myRemoteDevices;
                mFragment = DevicesTabbedContainerFragment.newInstance(Protocol.ALL, DeviceType.ALL);
                tag = DevicesTabbedContainerFragment.TAG;
                break;

            case R.id.drawer_my_locations:
                titleId = R.string.my_locations;
                mFragment = new MyLocationsFragment();
                tag = MyLocationsFragment.TAG;
                break;

            case R.id.drawer_settings:
                titleId = R.string.drawer__settings;
                mFragment = new RootPrefsFragment();
                tag = RootPrefsFragment.TAG;
                break;

            case R.id.drawer_privacy_policy:
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.url_privacy)));
                startActivity(browserIntent);
                return true;

            default:
                Log.d(TAG, "setting a new content fragment not yet implemented");
                Toast.makeText(this, "setting a new content fragment not yet implemented", Toast.LENGTH_SHORT).show();
        }

        if (mFragment != null) {
            FragmentTransaction fragmentTransaction = getSupportFragmentManager().beginTransaction();
            fragmentTransaction.replace(R.id.content, mFragment, tag);
            // if (addToBackStack) { fragmentTransaction.addToBackStack(null); }
            fragmentTransaction.commit();
        }
        setTitle(titleId);

        return true;
    }

    /* Called when an options item is clicked */
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (DEBUG) Log.i(TAG, "onOptionsItemSelected");

        // Log.d(TAG, "onOptionsItemSelected");
        switch (item.getItemId()) {
            case android.R.id.home:
                mDrawerLayout.openDrawer(GravityCompat.START);
                return true;

            default:
                return super.onOptionsItemSelected(item);

        }
    }

    protected ActivityType getActivityType() {
        if (mBanalServiceComm == null) {
            return ActivityType.getDefaultActivityType();
        } else {
            return mBanalServiceComm.getActivityType();
        }
    }

    @Override
    public void updateActivityType(int selectedItem) {
        FragmentTransaction fragmentTransaction = getSupportFragmentManager().beginTransaction();
        fragmentTransaction.replace(R.id.content, StartAndTrackingFragmentTabbedContainer.newInstance(getActivityType(), selectedItem));
        // if (addToBackStack) { fragmentTransaction.addToBackStack(null); }
        fragmentTransaction.commit();
    }

    @Override
    public void enableBluetoothRequest() {
        if (DEBUG) Log.i(TAG, "enableBluetoothRequest");

        showEnableBluetoothDialog();

//        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
//        enableBtIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//        startActivityForResult(enableBtIntent, REQUEST_ENABLE_BLUETOOTH);


    }

    @Override
    public void startPairing(@NonNull Protocol protocol) {
        if (DEBUG) Log.d(TAG, "startPairingActivity: " + protocol);
        switch (protocol) {
            case ANT_PLUS:
                onNavigationItemSelected(mNavigationView.getMenu().findItem(R.id.drawer_pairing_ant));
                // changeContentFragment(R.id.drawer_pairing_ant);
                return;

            case BLUETOOTH_LE:
                onNavigationItemSelected(mNavigationView.getMenu().findItem(R.id.drawer_pairing_BTLE));
                // changeContentFragment(R.id.drawer_pairing_BTLE);
                return;
        }

        Toast.makeText(getApplicationContext(), "TODO: must implement the startPairing for" + protocol.name(), Toast.LENGTH_SHORT).show();
    }

    // @Override
    // public void startWorkoutDetailsActivity(long workoutId, WorkoutDetailsActivity.SelectedFragment selectedFragment)
    // {
    //     if (DEBUG) Log.i(TAG, "startWorkoutDetailsActivity(" + workoutId + ")");

    //     Bundle bundle = new Bundle();
    //     bundle.putLong(WorkoutSummaries.WORKOUT_ID, workoutId);
    //     bundle.putString(WorkoutDetailsActivity.SELECTED_FRAGMENT, selectedFragment.name());
    //     Intent workoutDetailsIntent = new Intent(this, WorkoutDetailsActivity.class);
    //     workoutDetailsIntent.putExtras(bundle);
    //     startActivity(workoutDetailsIntent);
    // }

    protected void checkBatteryStatus() {
        final List<DevicesDatabaseManager.NameAndBatteryPercentage> criticalBatteryDevices = DevicesDatabaseManager.getInstance(getApplicationContext()).getCriticalBatteryDevices(CRITICAL_BATTERY_LEVEL);
        if (!criticalBatteryDevices.isEmpty()) {

            final List<String> stringList = new LinkedList<>();
            for (DevicesDatabaseManager.NameAndBatteryPercentage device : criticalBatteryDevices) {
                stringList.add(getString(R.string.critical_battery_message_format,
                        device.name, getString(BatteryStatusHelper.getBatteryStatusNameId(device.batteryPercentage))));
            }

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(criticalBatteryDevices.size() == 1 ? R.string.check_battery_status_title_1 : R.string.check_battery_status_title_many);
            builder.setItems(stringList.toArray(new String[stringList.size()]), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    long deviceId = criticalBatteryDevices.get(which).deviceId;
                    DevicesDatabaseManager devicesDatabaseManager = DevicesDatabaseManager.getInstance(getApplicationContext());
                    DeviceType deviceType = devicesDatabaseManager.getDeviceType(deviceId);
                    DialogFragment editDeviceDialogFragment = EditDeviceFragmentFactory.create(deviceId, deviceType);
                    editDeviceDialogFragment.show(getSupportFragmentManager(), "EditDeviceDialogFragment");
                }
            });
            builder.create().show();
        }
    }

    @Override
    public void startSegmentDetailsActivity(int segmentId) {
        if (DEBUG) Log.i(TAG, "startSegmentDetailsActivity: segmentId=" + segmentId);

        Bundle bundle = new Bundle();
        bundle.putLong(SegmentsDatabaseManager.Segments.SEGMENT_ID, segmentId);
        Intent segmentDetailsIntent = new Intent(this, SegmentDetailsActivity.class);
        segmentDetailsIntent.putExtras(bundle);
        startActivity(segmentDetailsIntent);
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////
    // the connection to the BANALService
    ////////////////////////////////////////////////////////////////////////////////////////////////

    // stolen from http://stackoverflow.com/questions/32487206/inner-preferencescreen-not-opens-with-preferencefragmentcompat
    @Override
    public boolean onPreferenceStartScreen(PreferenceFragmentCompat preferenceFragmentCompat, @NonNull PreferenceScreen preferenceScreen) {
        if (DEBUG) Log.i(TAG, "onPreferenceStartScreen: " + preferenceScreen.getKey());
        String key = preferenceScreen.getKey();
        PreferenceFragmentCompat fragment = null;
        switch (key) {
            case "root" -> fragment = new RootPrefsFragment();
            case "display" -> fragment = new DisplayFragment();

            // else if (key.equals("smoothing")) {
            //     fragment = new SmoothingFragment();
            // }
            case "search_settings" -> fragment = new SearchFragment();
            case TrainingApplication.PREF_KEY_START_SEARCH -> fragment = new StartSearchFragment();
            case "fileExport" -> fragment = new FileExportFragment();
            case "cloudUpload" -> fragment = new CloudUploadFragment();
            case TrainingApplication.PREFERENCE_SCREEN_EMAIL_UPLOAD ->
                    fragment = new EmailUploadFragment();
            case TrainingApplication.PREFERENCE_SCREEN_STRAVA ->
                    fragment = new StravaUploadFragment();
            case TrainingApplication.PREFERENCE_SCREEN_RUNKEEPER ->
                    fragment = new RunkeeperUploadFragment();
            case TrainingApplication.PREFERENCE_SCREEN_TRAINING_PEAKS ->
                    fragment = new TrainingpeaksUploadFragment();
            case "pebbleScreen" -> fragment = new PebbleScreenFragment();
            case "prefsLocationSources" -> fragment = new LocationSourcesFragment();
            case "altitudeCorrection" -> fragment = new AltitudeCorrectionFragment();
            case "sportTypes" -> {
                FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
                ft.replace(R.id.content, new SportTypeListFragment(), preferenceScreen.getKey());
                ft.addToBackStack(preferenceScreen.getKey());
                ft.commit();
                return true;
            }
            case "fancyWorkoutNames" -> {
                FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
                ft.replace(R.id.content, new FancyWorkoutNameListFragment(), preferenceScreen.getKey());
                ft.addToBackStack(preferenceScreen.getKey());
                ft.commit();
                return true;
            }
            default -> Log.d(TAG, "WTF: unknown key");
        }


        if (fragment != null) {
            Bundle args = new Bundle();
            args.putString(PreferenceFragmentCompat.ARG_PREFERENCE_ROOT, preferenceScreen.getKey());
            fragment.setArguments(args);
            FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
            ft.replace(R.id.content, fragment, preferenceScreen.getKey());
            ft.addToBackStack(preferenceScreen.getKey());
            ft.commit();

            return true;
        } else {
            return false;
        }
    }

    @Override
    public void registerConnectionStatusListener(ConnectionStatusListener connectionStatusListener) {
        mConnectionStatusListeners.add(connectionStatusListener);
    }

    @Nullable
    @Override
    public BANALService.BANALServiceComm getBanalServiceComm() {
        return mBanalServiceComm;
    }

    private void disconnectFromBANALService() {
        if (DEBUG) Log.i(TAG, "disconnectFromBANALService");

        if (mBanalServiceComm != null) {
            unbindService(mBanalConnection);                                                        // TODO: on some devices, an exception is thrown here
            mBanalServiceComm = null;
        }
    }

    private void showGPSDisabledAlertToUser() {
        GPSDisabledDialog gpsDisabledDialog = new GPSDisabledDialog();
        gpsDisabledDialog.show(getSupportFragmentManager(), GPSDisabledDialog.TAG);
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////
    // showing several dialogs
    ////////////////////////////////////////////////////////////////////////////////////////////////

    private void showEnableBluetoothDialog() {
        EnableBluetoothDialog enableBluetoothDialog = new EnableBluetoothDialog();
        enableBluetoothDialog.show(getSupportFragmentManager(), EnableBluetoothDialog.TAG);
    }

    private void showInstallANTShitDialog() {
        InstallANTShitDialog installANTShitDialog = new InstallANTShitDialog();
        installANTShitDialog.show(getSupportFragmentManager(), InstallANTShitDialog.TAG);
    }

    /***********************************************************************************************/

    @Override
    public void showStartOrResumeDialog() {
        StartOrResumeDialog startOrResumeDialog = new StartOrResumeDialog();
        startOrResumeDialog.show(getSupportFragmentManager(), StartOrResumeDialog.TAG);
    }


    /***********************************************************************************************/
    /* Implementation of the StartOrResumeInterface                                                */
    @Override
    public void chooseStart() {
        TrainingApplication.setResumeFromCrash(false);

        TextView tv = findViewById(R.id.tvStart);
        if (tv != null) {
            tv.setText(R.string.start_new_workout);
        }
    }

    @Override
    public void chooseResume() {
        TrainingApplication.setResumeFromCrash(true);

        TextView tv = findViewById(R.id.tvStart);
        if (tv != null) {
            tv.setText(R.string.resume_workout);
        }
    }

    public enum SelectedFragment {START_OR_TRACKING, WORKOUT_LIST}

}