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

package com.atrainingtracker.trainingtracker.activities

import android.Manifest
import android.annotation.SuppressLint
import androidx.annotation.VisibleForTesting
import android.app.Dialog
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceScreen
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.ActivityType
import com.atrainingtracker.banalservice.BANALService
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.Protocol
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.dialogs.InstallANTShitDialog
import com.atrainingtracker.banalservice.helpers.BatteryStatusHelper
import com.atrainingtracker.banalservice.ui.devices.devicetabs.DevicesTabbedContainerFragment
import com.atrainingtracker.banalservice.ui.devices.devicetabs.DevicesTabbedViewModel
import com.atrainingtracker.banalservice.ui.devices.editdevice.EditDeviceFragmentFactory
import androidx.lifecycle.ViewModelProvider
import com.atrainingtracker.banalservice.ui.sporttype.SportTypeListFragment
import com.atrainingtracker.trainingtracker.MyPreferenceManager
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.database.TrackingViewsDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.dialogs.GPSDisabledDialog
import com.atrainingtracker.trainingtracker.dialogs.StartOrResumeDialog
import com.atrainingtracker.trainingtracker.interfaces.StartOrResumeInterface
import com.atrainingtracker.trainingtracker.migration.BackupRestoreFragment
import com.atrainingtracker.trainingtracker.onlinecommunities.strava.StravaHelper
import com.atrainingtracker.trainingtracker.repositories.BANALServiceRepository
import com.atrainingtracker.trainingtracker.tracker.TrackerService
import com.atrainingtracker.trainingtracker.ui.WorkoutNavigationEvents
import com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.PeriodSummary
import com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.PeriodsFragment
import com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist.WorkoutFilterCriteria
import com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist.WorkoutSummariesTabbedFragment
import com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist.WorkoutSummariesViewModel
import com.atrainingtracker.trainingtracker.ui.clusters.WorkoutClustersFragment
import com.atrainingtracker.trainingtracker.ui.components.stats.StatsData
import com.atrainingtracker.trainingtracker.ui.equipment.EquipmentFragment
import com.atrainingtracker.trainingtracker.ui.map.MapFragmentWithTrack
import com.atrainingtracker.trainingtracker.ui.navigation.ATrainingTrackerApp
import com.atrainingtracker.trainingtracker.ui.navigation.NavRoutes
import com.atrainingtracker.trainingtracker.ui.navigation.NavigationDrawerController
import com.atrainingtracker.trainingtracker.ui.navigation.SettingsBottomSheetType
import com.atrainingtracker.trainingtracker.ui.navigation.setupComposeNavigationDrawer
import com.atrainingtracker.trainingtracker.ui.routes.RoutesFragment
import com.atrainingtracker.trainingtracker.ui.segments.segmentlist.StarredSegmentsFragment
import com.atrainingtracker.trainingtracker.ui.settings.display.DisplaySettingsDialogFragment
import com.atrainingtracker.trainingtracker.ui.settings.dropbox.DropboxSettingsDialogFragment
import com.atrainingtracker.trainingtracker.ui.settings.export.ExportSettingsDialogFragment
import com.atrainingtracker.trainingtracker.ui.settings.search.SearchSettingsDialogFragment
import com.atrainingtracker.trainingtracker.ui.settings.strava.StravaSettingsDialogFragment
import com.atrainingtracker.trainingtracker.ui.settings.trackingtabs.ActivityTypeSelectionHelper
import com.atrainingtracker.trainingtracker.ui.settings.units.UnitsSettingsDialogFragment
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.atrainingtracker.trainingtracker.ui.tracking.trackingtabs.TrackingTabsFragment
import com.dsi.ant.plugins.antplus.pccbase.AntPluginPcc
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GooglePlayServicesUtil
import com.google.android.gms.maps.MapsInitializer
import java.util.LinkedList

/**
 * Primary navigation activity and lifecycle cockpit for aTrainingTracker.
 *
 * Coordinates top-level navigation, Jetpack Compose navigation drawer hosting,
 * fragment backstack transactions, BANALService connection lifecycle,
 * system broadcast reception, and workout crash recovery.
 *
 * Migrated to Kotlin under ATT-657 (fulfills REQ-UI-124, verified by TST-NAV-008).
 */
class MainActivityWithNavigation :
    AppCompatActivity(),
    BANALService.GetBanalServiceInterface,
    PreferenceFragmentCompat.OnPreferenceStartScreenCallback,
    StartOrResumeInterface {

    companion object {
        @JvmField
        val SELECTED_FRAGMENT_ID = "SELECTED_FRAGMENT_ID"

        @JvmField
        val SELECTED_FRAGMENT = "SELECTED_FRAGMENT"

        @JvmField
        val EXTRA_RESUME_INTERRUPTED_WORKOUT = "com.atrainingtracker.EXTRA_RESUME_INTERRUPTED_WORKOUT"

        private val DEBUG: Boolean
            get() = TrainingApplication.getDebug(true)

        private const val TAG = "MainActivityWithNavigat"
        private const val DEFAULT_SELECTED_FRAGMENT_ID = R.id.drawer_start_tracking
        private const val REQUEST_INSTALL_GOOGLE_PLAY_SERVICE = 2
        private const val MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 1
        private const val WAITING_TIME_BEFORE_DISCONNECTING = 5L * 60 * 1000 // 5 min
        private const val CRITICAL_BATTERY_LEVEL = 30
    }

    enum class SelectedFragment {
        START_OR_TRACKING,
        WORKOUT_LIST
    }

    protected lateinit var mTrainingApplication: TrainingApplication
    protected var mSelectedFragmentId: Int = DEFAULT_SELECTED_FRAGMENT_ID
    protected var mDrawerLayout: DrawerLayout? = null
    protected val mDrawerController: NavigationDrawerController =
        NavigationDrawerController(DEFAULT_SELECTED_FRAGMENT_ID, R.string.tab_start)
    protected var mFragment: Fragment? = null
    var navController: NavHostController? = null
    var pendingActivityType: ActivityType? = null
    protected val mHandler: Handler = Handler(Looper.getMainLooper())
    protected var mStartAndNotResume: Boolean = true
    private var mResumingFromInterruptedNotification: Boolean = false
    protected var mBanalServiceComm: BANALService.BANALServiceComm? = null
    internal val mConnectionStatusListeners: LinkedList<BANALService.GetBanalServiceInterface.ConnectionStatusListener> = LinkedList()

    /* Broadcast Receivers to adapt title based on tracking state */
    internal val mStartTrackingReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            mDrawerController.startTrackingTitleRes = R.string.Tracking
        }
    }

    internal val mPauseTrackingReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            mDrawerController.startTrackingTitleRes = R.string.Pause
        }
    }

    internal val mStopTrackingReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            mDrawerController.startTrackingTitleRes = R.string.Start
            checkBatteryStatus()
        }
    }

    protected val mTrackingStoppedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            mSelectedFragmentId = R.id.drawer_workouts
            navigateToDrawerItem(mSelectedFragmentId)
        }
    }

    private val mAntDependencyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            showSpecificInstallANTDialog()
        }
    }

    private val mAntAdapterMissingReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            showANTAdapterMissingDialog()
        }
    }

    private var showingSpecificInstallANTDialog: Boolean = false
    fun showSpecificInstallANTDialog() {
        if (showingSpecificInstallANTDialog) {
            return
        } else {
            showingSpecificInstallANTDialog = true
        }

        val alertDialogBuilder = AlertDialog.Builder(this)
        alertDialogBuilder.setTitle(R.string.ant_missing_dependency_title)
        alertDialogBuilder.setMessage(getString(R.string.ant_missing_dependency_message, AntPluginPcc.getMissingDependencyName()))
        alertDialogBuilder.setCancelable(true)
        alertDialogBuilder.setPositiveButton(R.string.go_to_store) { _, _ ->
            val startStore = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + AntPluginPcc.getMissingDependencyPackageName()))
            startStore.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(startStore)
        }
        alertDialogBuilder.setNegativeButton(R.string.cancel) { dialog, _ ->
            dialog.dismiss()
        }

        val waitDialog = alertDialogBuilder.create()
        waitDialog.show()
    }

    private var isShowingANTAdapterMissingDialog: Boolean = false
    fun showANTAdapterMissingDialog() {
        if (isShowingANTAdapterMissingDialog) {
            return
        } else {
            isShowingANTAdapterMissingDialog = true
        }

        val alertDialogBuilder = AlertDialog.Builder(this)
        alertDialogBuilder.setTitle(R.string.ant_missing_adapter_title)
        alertDialogBuilder.setMessage(R.string.ant_missing_adapter_message)
        alertDialogBuilder.setCancelable(true)
        alertDialogBuilder.setNeutralButton(R.string.OK) { dialog, _ ->
            dialog.dismiss()
        }

        val waitDialog = alertDialogBuilder.create()
        waitDialog.show()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (DEBUG) Log.d(TAG, "onNewIntent")
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) {
            return
        }
        if (intent.getBooleanExtra(EXTRA_RESUME_INTERRUPTED_WORKOUT, false)) {
            intent.removeExtra(EXTRA_RESUME_INTERRUPTED_WORKOUT)
            mResumingFromInterruptedNotification = true
            try {
                NotificationManagerCompat.from(this).cancel(TrackerService.TRACKING_INTERRUPTED_NOTIFICATION_ID)
            } catch (e: Exception) {
                Log.e(TAG, "Error cancelling tracking interrupted notification: " + e.message, e)
            }
            mSelectedFragmentId = R.id.drawer_start_tracking
            navigateToDrawerItem(mSelectedFragmentId)
            chooseResume()
        } else if (intent.hasExtra(SELECTED_FRAGMENT)) {
            try {
                val selectedName = intent.getStringExtra(SELECTED_FRAGMENT)
                if (selectedName != null) {
                    val selected = SelectedFragment.valueOf(selectedName)
                    if (selected == SelectedFragment.WORKOUT_LIST) {
                        mSelectedFragmentId = R.id.drawer_workouts
                        navigateToDrawerItem(mSelectedFragmentId)
                    } else if (selected == SelectedFragment.START_OR_TRACKING) {
                        mSelectedFragmentId = R.id.drawer_start_tracking
                        navigateToDrawerItem(mSelectedFragmentId)
                    }
                }
            } catch (ignored: IllegalArgumentException) {
            }
        }
    }

    private lateinit var mStartTrackingFilter: IntentFilter
    private var mAlreadyTriedToRequestDropboxToken: Boolean = false

    private val mBanalConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            if (DEBUG) Log.i(TAG, "onServiceConnected")

            mBanalServiceComm = service as? BANALService.BANALServiceComm

            // create all the filters
            mBanalServiceComm?.let { comm ->
                val devicesDatabaseManager = DevicesDatabaseManager.getInstance(applicationContext)
                for (filterData in TrackingViewsDatabaseManager.getInstance(applicationContext).getAllFilterData(devicesDatabaseManager)) {
                    comm.createFilter(filterData)
                }
            }

            // inform listeners
            for (connectionStatusListener in mConnectionStatusListeners) {
                connectionStatusListener.connectedToBanalService()
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            if (DEBUG) Log.i(TAG, "onServiceDisconnected")

            mBanalServiceComm = null

            // inform listeners
            for (connectionStatusListener in mConnectionStatusListeners) {
                connectionStatusListener.disconnectedFromBanalService()
            }
        }
    }

    protected val mDisconnectFromBANALServiceRunnable: Runnable = Runnable {
        disconnectFromBANALService()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (DEBUG) Log.d(TAG, "onCreate")

        enableEdgeToEdge()

        // Initialize Google Maps SDK explicitly to prevent IBitmapDescriptorFactory errors in Compose
        MapsInitializer.initialize(applicationContext, MapsInitializer.Renderer.LATEST) { renderer ->
            when (renderer) {
                MapsInitializer.Renderer.LATEST -> Log.d(TAG, "The latest version of the Google Maps renderer is in use.")
                MapsInitializer.Renderer.LEGACY -> Log.d(TAG, "The legacy version of the Google Maps renderer is in use.")
            }
        }

        // initialization
        mTrainingApplication = application as TrainingApplication

        mStartTrackingFilter = IntentFilter(TrainingApplication.REQUEST_START_TRACKING).apply {
            addAction(TrainingApplication.REQUEST_RESUME_FROM_PAUSED)
        }

        if (savedInstanceState != null) {
            mSelectedFragmentId = savedInstanceState.getInt(SELECTED_FRAGMENT_ID, DEFAULT_SELECTED_FRAGMENT_ID)
            mDrawerController.selectedItemId = mSelectedFragmentId
        }

        setContent {
            ATrainingTrackerTheme {
                ATrainingTrackerApp(
                    activity = this,
                    drawerController = mDrawerController
                )
            }
        }

        // getPermissions
        getPermissions(true)

        // check ANT+ installation
        if (TrainingApplication.checkANTInstallation() && !BANALService.areAllANTServicesInstalled(this)) {
            showInstallANTShitDialog()
        }

        checkBatteryOptimizations()

        handleIntent(intent)

        checkGpsEnabledIfPermitted()

        val dialog = GooglePlayServicesUtil.getErrorDialog(
            GooglePlayServicesUtil.isGooglePlayServicesAvailable(this),
            this,
            REQUEST_INSTALL_GOOGLE_PLAY_SERVICE
        )
        if (dialog != null) {
            if (TrainingApplication.showInstallPlayServicesDialog()) {
                dialog.show()
            }
        }

        observeNavigationEvents()
    }

    private fun checkBatteryOptimizations() {
        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && pm != null) {
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                AlertDialog.Builder(this)
                    .setTitle(R.string.battery_optimization_title)
                    .setMessage(R.string.battery_optimization_text)
                    .setPositiveButton(R.string.OK) { _, _ ->
                        val intent = Intent().apply {
                            action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                            data = Uri.parse("package:$packageName")
                        }
                        startActivity(intent)
                    }
                    .setNegativeButton(R.string.Cancel) { dialog, _ -> dialog.dismiss() }
                    .show()
            }
        }
    }

    private fun observeNavigationEvents() {
        WorkoutNavigationEvents.navigateToEditLiveData.observe(this) { workoutId: Long? ->
            if (workoutId == null) return@observe

            mSelectedFragmentId = R.id.drawer_workouts
            mDrawerController.selectedItemId = mSelectedFragmentId
            navigateToDrawerItem(R.id.drawer_workouts)
        }

        WorkoutNavigationEvents.navigateToClusterLiveData.observe(this) { clusterId: Long? ->
            if (clusterId == null || clusterId <= 0) return@observe

            mSelectedFragmentId = R.id.drawer_my_locations
            mDrawerController.selectedItemId = mSelectedFragmentId
            navigateToDrawerItem(R.id.drawer_my_locations)

            WorkoutNavigationEvents.resetCluster()
        }
    }

    private fun getPermissions(): List<String> {
        val requiredPerms = ArrayList<String>()
        requiredPerms.add(Manifest.permission.ACCESS_FINE_LOCATION)
        requiredPerms.add(Manifest.permission.ACCESS_COARSE_LOCATION)

        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
            requiredPerms.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            (packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE) ||
             packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH))) {
            requiredPerms.add(Manifest.permission.BLUETOOTH_CONNECT)
            requiredPerms.add(Manifest.permission.BLUETOOTH_SCAN)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requiredPerms.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        return requiredPerms
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION) {
            var foregroundLocationGranted = false
            for (i in permissions.indices) {
                if ((permissions[i] == Manifest.permission.ACCESS_FINE_LOCATION || permissions[i] == Manifest.permission.ACCESS_COARSE_LOCATION) &&
                    grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    foregroundLocationGranted = true
                    break
                }
            }

            if (foregroundLocationGranted) {
                checkGpsEnabledIfPermitted()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        showBackgroundLocationDialog()
                    }
                }
            }
        }
    }

    private fun showBackgroundLocationDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.background_location_permission_title)
            .setMessage(R.string.background_location_permission_text)
            .setPositiveButton(R.string.OK) { _, _ ->
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION), 0)
            }
            .setNegativeButton(R.string.Cancel) { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun getPermissions(popup: Boolean) {
        var missingAnyPermission = false
        val requiredPerms = getPermissions()
        val requestPerms = ArrayList<String>()

        for (perm in requiredPerms) {
            if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                missingAnyPermission = true
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, perm)) {
                    val s = "Permission $perm is explicitly denied"
                    Log.i(javaClass.name, s)
                } else {
                    requestPerms.add(perm)
                }
            }
        }

        if (missingAnyPermission) {
            val permissions = requestPerms.toTypedArray()

            if (popup || requestPerms.isNotEmpty()) {
                val baseMessage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    getString(R.string.location_permission_text_Android12)
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    getString(R.string.location_permission_text)
                } else {
                    getString(R.string.location_permission_text_pre_Android10)
                }

                val builder = AlertDialog.Builder(this)
                    .setTitle(R.string.location_permission_required)
                    .setNegativeButton(R.string.Cancel) { dialog, _ -> dialog.dismiss() }

                if (requestPerms.isNotEmpty()) {
                    builder.setPositiveButton(R.string.OK) { _, _ ->
                        ActivityCompat.requestPermissions(this, permissions, MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION)
                    }.setMessage(baseMessage + "\n" + getString(R.string.Request_permission_text))
                } else {
                    val intent = Intent()
                        .setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(Uri.fromParts("package", packageName, null))
                    builder.setPositiveButton(R.string.OK) { _, _ -> startActivity(intent) }
                        .setMessage(baseMessage + "\n\n" + getString(R.string.Request_permission_text))
                }
                builder.show()
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    if (popup) {
                        showBackgroundLocationDialog()
                    }
                }
            }
        }
    }

    @SuppressLint("SourceLockedOrientationActivity")
    override fun onResume() {
        super.onResume()
        if (DEBUG) Log.d(TAG, "onResume")

        val banalServiceIntent = Intent(this, BANALService::class.java)
        startService(banalServiceIntent)

        if (mBanalServiceComm == null) {
            bindService(banalServiceIntent, mBanalConnection, Context.BIND_AUTO_CREATE)
        }

        BANALServiceRepository.getInstance(this).bindToBANALService()

        mHandler.removeCallbacks(mDisconnectFromBANALServiceRunnable)

        checkPreferences()

        applyDisplaySettings()

        // register receivers
        ContextCompat.registerReceiver(this, mStartTrackingReceiver, mStartTrackingFilter, ContextCompat.RECEIVER_NOT_EXPORTED)
        ContextCompat.registerReceiver(this, mPauseTrackingReceiver, IntentFilter(TrainingApplication.REQUEST_PAUSE_TRACKING), ContextCompat.RECEIVER_NOT_EXPORTED)
        ContextCompat.registerReceiver(this, mStopTrackingReceiver, IntentFilter(TrainingApplication.REQUEST_STOP_TRACKING), ContextCompat.RECEIVER_NOT_EXPORTED)
        ContextCompat.registerReceiver(this, mTrackingStoppedReceiver, IntentFilter(TrackerService.TRACKING_FINISHED_INTENT), ContextCompat.RECEIVER_NOT_EXPORTED)
        ContextCompat.registerReceiver(this, mAntDependencyReceiver, IntentFilter("com.atrainingtracker.ANT_DEPENDENCY_MISSING"), ContextCompat.RECEIVER_NOT_EXPORTED)
        ContextCompat.registerReceiver(this, mAntAdapterMissingReceiver, IntentFilter("com.atrainingtracker.ADAPTER_NOT_DETECTED"), ContextCompat.RECEIVER_NOT_EXPORTED)

        checkUnfinishedWorkout()
    }

    private fun checkUnfinishedWorkout() {
        if (mResumingFromInterruptedNotification) {
            mResumingFromInterruptedNotification = false
            return
        }
        if (!TrainingApplication.isTracking()) {
            if (WorkoutSummariesDatabaseManager.getInstance(this).hasUnfinishedWorkout()) {
                if (supportFragmentManager.findFragmentByTag(StartOrResumeDialog.TAG) == null) {
                    showStartOrResumeDialog()
                }
            }
        }
    }

    protected fun checkPreferences() {
        if (TrainingApplication.uploadToStrava() && TrainingApplication.getStravaAccessToken() == null) {
            TrainingApplication.setUploadToStrava(false)
        }

        if (TrainingApplication.uploadToStrava() && TrainingApplication.getStravaTokenExpiresAt() == 0) {
            Log.i(TAG, "migrating to new Strava OAuth")
            StravaHelper.requestAccessToken(this)
        }

        if (TrainingApplication.uploadToRunKeeper() && TrainingApplication.getRunkeeperToken() == null) {
            TrainingApplication.setUploadToRunkeeper(false)
        }

        if (TrainingApplication.uploadToTrainingPeaks() && TrainingApplication.getTrainingPeaksRefreshToken() == null) {
            TrainingApplication.setUploadToTrainingPeaks(false)
        }
    }

    fun applyDisplaySettings() {
        window.decorView.keepScreenOn = TrainingApplication.keepScreenOn()

        if (TrainingApplication.NoUnlocking()) {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        } else {
            @Suppress("DEPRECATION")
            window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        }

        if (TrainingApplication.forcePortrait()) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (DEBUG) Log.i(TAG, "onActivityResult: requestCode=$requestCode, resultCode=$resultCode")

        when (requestCode) {
            REQUEST_INSTALL_GOOGLE_PLAY_SERVICE -> {
                if (GooglePlayServicesUtil.isGooglePlayServicesAvailable(this) == ConnectionResult.SUCCESS) {
                    // Google Play Services available
                }
            }
            else -> {
                if (DEBUG) Log.i(TAG, "requestCode not handled")
                super.onActivityResult(requestCode, resultCode, data)
            }
        }
    }

    override fun onSaveInstanceState(savedInstanceState: Bundle) {
        if (DEBUG) Log.i(TAG, "onSaveInstanceState")

        val fragment = mFragment
        if (fragment != null && fragment.isAdded) {
            supportFragmentManager.putFragment(savedInstanceState, "mFragment", fragment)
        }

        savedInstanceState.putInt(SELECTED_FRAGMENT_ID, mSelectedFragmentId)
        super.onSaveInstanceState(savedInstanceState)
    }

    override fun onPause() {
        super.onPause()
        if (DEBUG) Log.d(TAG, "onPause")

        try {
            unregisterReceiver(mStartTrackingReceiver)
        } catch (ignored: IllegalArgumentException) {
        }
        try {
            unregisterReceiver(mPauseTrackingReceiver)
        } catch (ignored: IllegalArgumentException) {
        }
        try {
            unregisterReceiver(mStopTrackingReceiver)
        } catch (ignored: IllegalArgumentException) {
        }
        try {
            unregisterReceiver(mTrackingStoppedReceiver)
        } catch (ignored: IllegalArgumentException) {
        }

        try {
            unregisterReceiver(mAntDependencyReceiver)
        } catch (ignored: IllegalArgumentException) {
        }
        try {
            unregisterReceiver(mAntAdapterMissingReceiver)
        } catch (ignored: IllegalArgumentException) {
        }

        if (!isChangingConfigurations) {
            mHandler.postDelayed(mDisconnectFromBANALServiceRunnable, WAITING_TIME_BEFORE_DISCONNECTING)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (DEBUG) Log.d(TAG, "onDestroy")

        if (!isChangingConfigurations) {
            disconnectFromBANALService()
        }
    }

    @Deprecated("Deprecated in Java")
    fun onNavigationItemSelected(menuItem: MenuItem?): Boolean {
        if (menuItem == null) {
            return false
        }
        return navigateToDrawerItem(menuItem.itemId)
    }

    fun navigateToDrawerItem(itemId: Int): Boolean {
        if (DEBUG) Log.i(TAG, "navigateToDrawerItem: $itemId")

        mDrawerController.closeDrawer()

        val route = NavRoutes.fromDrawerItemId(itemId)
        if (route != null) {
            if (itemId == R.id.drawer_start_tracking || (mSelectedFragmentId == R.id.drawer_workouts && itemId != R.id.drawer_workouts)) {
                MyPreferenceManager(applicationContext).clearWorkoutFilterCriteria()
            }
            if (itemId == R.id.drawer_start_tracking || (mSelectedFragmentId == R.id.drawer_routes && itemId != R.id.drawer_routes)) {
                MyPreferenceManager(applicationContext).clearRouteFilterCriteria()
            }
            if (itemId == R.id.drawer_start_tracking || (mSelectedFragmentId == R.id.drawer_my_locations && itemId != R.id.drawer_my_locations)) {
                MyPreferenceManager(applicationContext).clearClusterFilterCriteria()
            }
            mSelectedFragmentId = itemId
            mDrawerController.selectedItemId = itemId

            navController?.let { controller ->
                controller.navigate(route) {
                    popUpTo(controller.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            }
            return true
        }

        val sheetType = NavRoutes.toSettingsBottomSheetType(itemId)
        if (sheetType != null) {
            mDrawerController.activeBottomSheet = sheetType
            return false
        }

        if (itemId == R.id.drawer_privacy_policy) {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.url_privacy)))
            startActivity(browserIntent)
            return true
        }

        Log.d(TAG, "setting a new content destination not yet implemented: $itemId")
        Toast.makeText(this, "setting a new content destination not yet implemented", Toast.LENGTH_SHORT).show()
        return false
    }

    fun openDrawer() {
        mDrawerController.openDrawer()
    }

    fun closeDrawer() {
        mDrawerController.closeDrawer()
    }

    fun onActivityTypeSelected(activityType: ActivityType) {
        pendingActivityType = activityType
        navigateToDrawerItem(R.id.drawer_start_tracking)
    }

    fun navigateToFilteredWorkouts(stats: StatsData) {
        val criteria = WorkoutFilterCriteria(
            startDateS = stats.startTimeS?.takeIf { it > 0 },
            endDateS = stats.endTimeS?.takeIf { it > 0 },
            sportTypeId = stats.filterSportTypeId?.takeIf { it != -1L },
            equipmentId = stats.filterEquipmentId?.takeIf { it != -1L }
        )
        val summariesViewModel = androidx.lifecycle.ViewModelProvider(this)[WorkoutSummariesViewModel::class.java]
        summariesViewModel.setFilterCriteria(criteria)
        navigateToDrawerItem(R.id.drawer_workouts)
    }

    fun startWorkoutSummaryListFromPeriod(
        periodSummary: PeriodSummary,
        bSportType: BSportType?,
        scrollToWorkoutId: Long?
    ) {
        val criteria = WorkoutFilterCriteria(
            startDateS = periodSummary.startTimestampS.takeIf { it > 0 },
            endDateS = periodSummary.endTimestampS.takeIf { it > 0 }
        )
        val summariesViewModel = androidx.lifecycle.ViewModelProvider(this)[WorkoutSummariesViewModel::class.java]
        summariesViewModel.setFilterCriteria(criteria)
        navigateToDrawerItem(R.id.drawer_workouts)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (DEBUG) Log.i(TAG, "onOptionsItemSelected")
        return when (item.itemId) {
            android.R.id.home -> {
                openDrawer()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    protected fun getActivityType(): ActivityType {
        val comm = mBanalServiceComm
        return if (comm == null) {
            ActivityType.getDefaultActivityType()
        } else {
            comm.activityType
        }
    }

    fun startPairing(protocol: Protocol, deviceType: DeviceType?) {
        if (DEBUG) Log.d(TAG, "startPairing: $protocol, deviceType: $deviceType")
        try {
            val tabViewModel: DevicesTabbedViewModel = ViewModelProvider(this)[DevicesTabbedViewModel::class.java]
            tabViewModel.updateFilters(protocol, deviceType)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to pre-configure DevicesTabbedViewModel filters for pairing", e)
        }
        navigateToDrawerItem(R.id.drawer_my_sensors)
    }

    protected fun checkBatteryStatus() {
        val criticalBatteryDevices = DevicesDatabaseManager.getInstance(applicationContext).getCriticalBatteryDevices(CRITICAL_BATTERY_LEVEL)
        if (criticalBatteryDevices.isNotEmpty()) {
            val stringList = LinkedList<String>()
            for (device in criticalBatteryDevices) {
                stringList.add(
                    getString(
                        R.string.critical_battery_message_format,
                        device.name,
                        getString(BatteryStatusHelper.getBatteryStatusNameId(device.batteryPercentage))
                    )
                )
            }

            val builder = AlertDialog.Builder(this)
            builder.setTitle(if (criticalBatteryDevices.size == 1) R.string.check_battery_status_title_1 else R.string.check_battery_status_title_many)
            builder.setItems(stringList.toTypedArray()) { _, which ->
                val deviceId = criticalBatteryDevices[which].deviceId
                val devicesDatabaseManager = DevicesDatabaseManager.getInstance(applicationContext)
                val deviceType = devicesDatabaseManager.getDeviceType(deviceId)
                val editDeviceDialogFragment = EditDeviceFragmentFactory.create(deviceId, deviceType)
                editDeviceDialogFragment.show(supportFragmentManager, "EditDeviceDialogFragment")
            }
            builder.create().show()
        }
    }

    override fun onPreferenceStartScreen(preferenceFragmentCompat: PreferenceFragmentCompat, preferenceScreen: PreferenceScreen): Boolean {
        if (DEBUG) Log.i(TAG, "onPreferenceStartScreen: " + preferenceScreen.key)
        val key = preferenceScreen.key
        when (key) {
            "sportTypes" -> {
                navigateToDrawerItem(R.id.drawer_sport_types)
                return true
            }
            "cloudUpload" -> {
                mDrawerController.activeBottomSheet = SettingsBottomSheetType.DROPBOX
                return true
            }
            TrainingApplication.PREFERENCE_SCREEN_STRAVA -> {
                mDrawerController.activeBottomSheet = SettingsBottomSheetType.STRAVA
                return true
            }
            "search_settings" -> {
                mDrawerController.activeBottomSheet = SettingsBottomSheetType.SEARCH
                return true
            }
            else -> {
                Log.d(TAG, "unknown key: $key")
                return false
            }
        }
    }

    override fun registerConnectionStatusListener(connectionStatusListener: BANALService.GetBanalServiceInterface.ConnectionStatusListener?) {
        if (connectionStatusListener != null) {
            mConnectionStatusListeners.add(connectionStatusListener)
        }
    }

    override fun getBanalServiceComm(): BANALService.BANALServiceComm? {
        return mBanalServiceComm
    }

    private fun disconnectFromBANALService() {
        if (DEBUG) Log.i(TAG, "disconnectFromBANALService")

        if (mBanalServiceComm != null) {
            try {
                unbindService(mBanalConnection)
            } catch (e: Exception) {
                Log.w(TAG, "Error unbinding from BANALService: " + e.message)
            }
            mBanalServiceComm = null
        }

        if (!TrainingApplication.isTracking()) {
            if (DEBUG) Log.i(TAG, "Stopping BANALService process (not tracking)")
            BANALServiceRepository.getInstance(this).unbindFromBANALService()
            stopService(Intent(this, BANALService::class.java))
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal fun checkGpsEnabledIfPermitted() {
        if (!TrainingApplication.trackLocation()) {
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        val locationManager = getSystemService(LOCATION_SERVICE) as? LocationManager ?: return
        try {
            val provider = locationManager.getProvider(LocationManager.GPS_PROVIDER)
            if (provider != null && !locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                showGPSDisabledAlertToUser()
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException while checking GPS provider: ${e.message}")
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "IllegalArgumentException while checking GPS provider: ${e.message}")
        }
    }

    private fun showGPSDisabledAlertToUser() {
        if (isFinishing || isDestroyed || supportFragmentManager.isStateSaved) {
            return
        }
        val gpsDisabledDialog = GPSDisabledDialog()
        gpsDisabledDialog.show(supportFragmentManager, GPSDisabledDialog.TAG)
    }

    private fun showInstallANTShitDialog() {
        val installANTShitDialog = InstallANTShitDialog()
        installANTShitDialog.show(supportFragmentManager, InstallANTShitDialog.TAG)
    }

    override fun showStartOrResumeDialog() {
        val startOrResumeDialog = StartOrResumeDialog()
        startOrResumeDialog.show(supportFragmentManager, StartOrResumeDialog.TAG)
    }

    override fun chooseStart() {
        try {
            NotificationManagerCompat.from(this).cancel(TrackerService.TRACKING_INTERRUPTED_NOTIFICATION_ID)
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling tracking interrupted notification: " + e.message, e)
        }
        TrainingApplication.setResumeFromCrash(false)
        WorkoutSummariesDatabaseManager.getInstance(this).discardOrFinishUnfinishedWorkout()
    }

    override fun chooseResume() {
        try {
            NotificationManagerCompat.from(this).cancel(TrackerService.TRACKING_INTERRUPTED_NOTIFICATION_ID)
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling tracking interrupted notification: " + e.message, e)
        }
        TrainingApplication.setResumeFromCrash(true)
        sendBroadcast(Intent(TrainingApplication.REQUEST_START_TRACKING).setPackage(packageName))
    }
}
