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
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceScreen
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.ActivityType
import com.atrainingtracker.banalservice.BANALService
import com.atrainingtracker.banalservice.Protocol
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.dialogs.InstallANTShitDialog
import com.atrainingtracker.banalservice.helpers.BatteryStatusHelper
import com.atrainingtracker.banalservice.ui.devices.devicetabs.DevicesTabbedContainerFragment
import com.atrainingtracker.banalservice.ui.devices.editdevice.EditDeviceFragmentFactory
import com.atrainingtracker.banalservice.ui.sporttype.SportTypeListFragment
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
import com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.PeriodsFragment
import com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist.WorkoutSummariesTabbedFragment
import com.atrainingtracker.trainingtracker.ui.clusters.WorkoutClustersFragment
import com.atrainingtracker.trainingtracker.ui.equipment.EquipmentFragment
import com.atrainingtracker.trainingtracker.ui.map.MapFragmentWithTrack
import com.atrainingtracker.trainingtracker.ui.navigation.NavigationDrawerController
import com.atrainingtracker.trainingtracker.ui.navigation.setupComposeNavigationDrawer
import com.atrainingtracker.trainingtracker.ui.routes.RoutesFragment
import com.atrainingtracker.trainingtracker.ui.segments.segmentlist.StarredSegmentsFragment
import com.atrainingtracker.trainingtracker.ui.settings.display.DisplaySettingsDialogFragment
import com.atrainingtracker.trainingtracker.ui.settings.dropbox.CloudUploadFragment
import com.atrainingtracker.trainingtracker.ui.settings.export.ExportSettingsDialogFragment
import com.atrainingtracker.trainingtracker.ui.settings.search.SearchSettingsFragment
import com.atrainingtracker.trainingtracker.ui.settings.strava.StravaUploadFragment
import com.atrainingtracker.trainingtracker.ui.settings.trackingtabs.ActivityTypeSelectionHelper
import com.atrainingtracker.trainingtracker.ui.settings.units.UnitsSettingsDialogFragment
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
    protected lateinit var mDrawerLayout: DrawerLayout
    protected val mDrawerController: NavigationDrawerController =
        NavigationDrawerController(DEFAULT_SELECTED_FRAGMENT_ID, R.string.tab_start)
    protected var mFragment: Fragment? = null
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

        // create UI
        setContentView(R.layout.main_activity_with_navigation)

        mDrawerLayout = findViewById(R.id.drawer_layout)

        val composeNavView: ComposeView = findViewById(R.id.compose_nav_view)
        setupComposeNavigationDrawer(
            composeView = composeNavView,
            controller = mDrawerController,
            onItemSelected = { itemId: Int ->
                navigateToDrawerItem(itemId)
            }
        )

        // getPermissions
        getPermissions(true)

        // check ANT+ installation
        if (TrainingApplication.checkANTInstallation() && !BANALService.areAllANTServicesInstalled(this)) {
            showInstallANTShitDialog()
        }

        checkBatteryOptimizations()

        if (savedInstanceState != null) {
            mSelectedFragmentId = savedInstanceState.getInt(SELECTED_FRAGMENT_ID, DEFAULT_SELECTED_FRAGMENT_ID)
            mDrawerController.selectedItemId = mSelectedFragmentId
            mFragment = supportFragmentManager.getFragment(savedInstanceState, "mFragment")
        } else {
            navigateToDrawerItem(mSelectedFragmentId)
        }

        handleIntent(intent)

        if (TrainingApplication.trackLocation()) {
            val locationManager = getSystemService(LOCATION_SERVICE) as? LocationManager
            if (locationManager != null && locationManager.getProvider(LocationManager.GPS_PROVIDER) != null) {
                try {
                    if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                        showGPSDisabledAlertToUser()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to check if GPS provider is enabled: " + e.message)
                }
            }
        }

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

        ViewCompat.setOnApplyWindowInsetsListener(mDrawerLayout) { v, windowInsets ->
            val systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, 0, systemBars.right, 0)

            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.isAppearanceLightStatusBars = true

            val composeNav = findViewById<View>(R.id.compose_nav_view)
            if (composeNav != null) {
                ViewCompat.dispatchApplyWindowInsets(composeNav, windowInsets)
            }

            windowInsets
        }

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (DEBUG) Log.i(TAG, "onBackPressed, entryCount=" + supportFragmentManager.backStackEntryCount)

                    if (mDrawerLayout.isDrawerOpen(GravityCompat.START) || mDrawerLayout.isDrawerVisible(GravityCompat.START)) {
                        mDrawerLayout.closeDrawer(GravityCompat.START)
                        return
                    }
                    if (supportFragmentManager.backStackEntryCount > 0) {
                        supportFragmentManager.popBackStack()
                    } else if (supportFragmentManager.backStackEntryCount == 0 && mSelectedFragmentId != R.id.drawer_start_tracking) {
                        navigateToDrawerItem(R.id.drawer_start_tracking)
                    } else {
                        finish()
                    }
                }
            }
        )

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
            val fragment = WorkoutSummariesTabbedFragment()
            mFragment = fragment

            val tag = WorkoutSummariesTabbedFragment.TAG

            supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
            supportFragmentManager.beginTransaction()
                .replace(R.id.content, fragment, tag)
                .commit()
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

            if (foregroundLocationGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    showBackgroundLocationDialog()
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

        window.decorView.keepScreenOn = TrainingApplication.keepScreenOn()

        if (TrainingApplication.NoUnlocking()) {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        }

        if (TrainingApplication.forcePortrait()) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

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

        mHandler.postDelayed(mDisconnectFromBANALServiceRunnable, WAITING_TIME_BEFORE_DISCONNECTING)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (DEBUG) Log.d(TAG, "onDestroy")

        disconnectFromBANALService()
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

        mDrawerLayout.closeDrawers()
        mFragment = null
        var tag: String? = null

        when (itemId) {
            R.id.drawer_start_tracking -> {
                mFragment = TrackingTabsFragment.newInstance()
                tag = TrackingTabsFragment.TAG
            }

            R.id.drawer_map -> {
                mFragment = MapFragmentWithTrack.newInstance()
                tag = MapFragmentWithTrack.TAG
            }

            R.id.drawer_segments -> {
                mFragment = StarredSegmentsFragment.newInstance()
                tag = StarredSegmentsFragment.TAG
            }

            R.id.drawer_routes -> {
                mFragment = RoutesFragment.newInstance()
                tag = RoutesFragment.TAG
            }

            R.id.drawer_workouts -> {
                mFragment = WorkoutSummariesTabbedFragment()
                tag = WorkoutSummariesTabbedFragment.TAG
            }

            R.id.drawer_periods -> {
                mFragment = PeriodsFragment.newInstance()
                tag = PeriodsFragment.TAG
            }

            R.id.drawer_my_sensors -> {
                mFragment = DevicesTabbedContainerFragment.newInstance(Protocol.ALL, DeviceType.ALL, 2)
                tag = DevicesTabbedContainerFragment.TAG
            }

            R.id.drawer_bikes -> {
                mFragment = EquipmentFragment.newInstance(0)
                tag = EquipmentFragment.TAG
            }

            R.id.drawer_shoes -> {
                mFragment = EquipmentFragment.newInstance(1)
                tag = EquipmentFragment.TAG
            }

            R.id.drawer_my_locations -> {
                mFragment = WorkoutClustersFragment.newInstance()
                tag = WorkoutClustersFragment.TAG
            }

            R.id.drawer_sport_types -> {
                mFragment = SportTypeListFragment.newInstance()
                tag = SportTypeListFragment.TAG
            }

            R.id.drawer_training_zones -> {
                mFragment = ZoneSettingsFragment.newInstance()
                tag = ZoneSettingsFragment.TAG
            }

            R.id.drawer_strava -> {
                mFragment = StravaUploadFragment()
                tag = StravaUploadFragment::class.java.name
            }

            R.id.drawer_dropbox -> {
                mFragment = CloudUploadFragment()
                tag = CloudUploadFragment::class.java.name
            }

            R.id.drawer_export -> {
                mDrawerLayout.closeDrawer(GravityCompat.START)
                ExportSettingsDialogFragment.newInstance().show(supportFragmentManager, ExportSettingsDialogFragment.TAG)
                return false
            }

            R.id.drawer_tracking_layouts -> {
                mDrawerLayout.closeDrawer(GravityCompat.START)
                ActivityTypeSelectionHelper.showSelectionDialog(
                    supportFragmentManager,
                    onTypeSelected = { activityType ->
                        val fragment = TrackingTabsFragment.newInstance(activityType)
                        mFragment = fragment
                        supportFragmentManager.beginTransaction()
                            .replace(R.id.content, fragment, TrackingTabsFragment.TAG)
                            .addToBackStack(null)
                            .commit()
                    }
                )
                return false
            }

            R.id.drawer_units -> {
                mDrawerLayout.closeDrawer(GravityCompat.START)
                UnitsSettingsDialogFragment.newInstance().show(supportFragmentManager, UnitsSettingsDialogFragment.TAG)
                return false
            }

            R.id.drawer_display_settings -> {
                mDrawerLayout.closeDrawer(GravityCompat.START)
                DisplaySettingsDialogFragment.newInstance().show(supportFragmentManager, DisplaySettingsDialogFragment.TAG)
                return false
            }

            R.id.drawer_search_settings -> {
                mFragment = SearchSettingsFragment.newInstance()
                tag = SearchSettingsFragment.TAG
            }

            R.id.drawer_backup_restore -> {
                mFragment = BackupRestoreFragment.newInstance()
                tag = "BackupRestoreFragment"
            }

            R.id.drawer_privacy_policy -> {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.url_privacy)))
                startActivity(browserIntent)
                return true
            }

            else -> {
                Log.d(TAG, "setting a new content fragment not yet implemented: $itemId")
                Toast.makeText(this, "setting a new content fragment not yet implemented", Toast.LENGTH_SHORT).show()
            }
        }

        val fragment = mFragment
        if (fragment != null) {
            mSelectedFragmentId = itemId
            mDrawerController.selectedItemId = itemId

            supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
            val fragmentTransaction = supportFragmentManager.beginTransaction()
            fragmentTransaction.replace(R.id.content, fragment, tag)
            fragmentTransaction.commit()
        }

        mDrawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (DEBUG) Log.i(TAG, "onOptionsItemSelected")
        return when (item.itemId) {
            android.R.id.home -> {
                mDrawerLayout.openDrawer(GravityCompat.START)
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
        if (DEBUG) Log.d(TAG, "startPairingActivity: $protocol, deviceType: $deviceType")

        val fragment = DevicesTabbedContainerFragment.newInstance(protocol, deviceType, 0)
        mFragment = fragment
        val tag = DevicesTabbedContainerFragment.TAG

        val fragmentTransaction = supportFragmentManager.beginTransaction()
        fragmentTransaction.replace(R.id.content, fragment, tag)
        fragmentTransaction.addToBackStack(null)
        fragmentTransaction.commit()
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
        var fragment: Fragment? = null
        when (key) {
            "sportTypes" -> fragment = SportTypeListFragment()
            "cloudUpload" -> fragment = CloudUploadFragment()
            TrainingApplication.PREFERENCE_SCREEN_STRAVA -> fragment = StravaUploadFragment()
            "search_settings" -> fragment = SearchSettingsFragment()
            else -> Log.d(TAG, "WTF: unknown key")
        }

        if (fragment != null) {
            val args = Bundle().apply {
                putString(PreferenceFragmentCompat.ARG_PREFERENCE_ROOT, preferenceScreen.key)
            }
            fragment.arguments = args
            val ft = supportFragmentManager.beginTransaction()
            ft.replace(R.id.content, fragment, preferenceScreen.key)
            ft.addToBackStack(preferenceScreen.key)
            ft.commit()
            return true
        } else {
            return false
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

    private fun showGPSDisabledAlertToUser() {
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

        val tv = findViewById<TextView>(R.id.tvStart)
        if (tv != null) {
            tv.setText(R.string.start_new_workout)
        }
    }

    override fun chooseResume() {
        try {
            NotificationManagerCompat.from(this).cancel(TrackerService.TRACKING_INTERRUPTED_NOTIFICATION_ID)
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling tracking interrupted notification: " + e.message, e)
        }
        TrainingApplication.setResumeFromCrash(true)
        sendBroadcast(Intent(TrainingApplication.REQUEST_START_TRACKING).setPackage(packageName))

        val tv = findViewById<TextView>(R.id.tvStart)
        if (tv != null) {
            tv.setText(R.string.resume_workout)
        }
    }
}
