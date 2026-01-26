package com.atrainingtracker.trainingtracker.ui.components.export

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.activities.MainActivityWithNavigation
import com.atrainingtracker.trainingtracker.exporter.ExportInfo
import com.atrainingtracker.trainingtracker.exporter.ExportType
import com.atrainingtracker.trainingtracker.exporter.FileFormat
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

class ExportNotificationManager private constructor(private val context: Context) {

    private val notificationManager = NotificationManagerCompat.from(context)
    private val uiDataProvider = ExportStatusUIDataProvider(context)
    private val pendingIntentStartWorkoutListActivity = createPendingIntentStartWorkoutListActivity()

    // workoutName -> ExportType -> FileFormat
    private val activeExports: MutableMap<String, MutableMap<ExportType, MutableSet<FileFormat>>> =
        ConcurrentHashMap()

    /**********************************************************************************************
     * Public API
     **********************************************************************************************/

    @Synchronized
    fun updateNotification(exportInfo: ExportInfo, isFinished: Boolean) {
        if (DEBUG) Log.i(TAG, "\n-------------------------------------------------------------")
        if (DEBUG) Log.i(TAG, "updateNotification: $exportInfo isFinished=$isFinished")

        updateExportStatus(exportInfo, isFinished)
        showNotification(exportInfo)
    }
    /***********************************************************************************************
     * Private and protected stuff
     **********************************************************************************************/

    @Synchronized
    fun updateExportStatus(exportInfo: ExportInfo, isFinished: Boolean) {

        val (fileBaseName, fileFormat, exportType) = exportInfo

        val exportTypeMap = activeExports.getOrPut(fileBaseName) { ConcurrentHashMap() }
        val fileFormatSet = exportTypeMap.getOrPut(exportType) { Collections.synchronizedSet(mutableSetOf()) }

        if (isFinished) {
            Log.i(TAG, "updateExportStatus: removing fileFormat $fileFormat")
            fileFormatSet.remove(fileFormat)
        } else {
            fileFormatSet.add(fileFormat)
        }

        logActiveExports(exportInfo.fileBaseName)
    }

    /***********************************************************************************************
     * Notification stuff
     **********************************************************************************************/

    @SuppressLint("MissingPermission")
    fun showNotification(exportInfo: ExportInfo) {
        if (isMissingPermission()) return

        val fileBaseName = exportInfo.fileBaseName
        val title = context.getString(R.string.export_notification__title, fileBaseName)

        val isStillRunning = activeExports[fileBaseName]?.values?.any { it.isNotEmpty() } ?: false

        val expandedView = RemoteViews(context.packageName, R.layout.export_notification__expanded)

        // The order of the ExportTypes is defined here.
        val orderedTypes = listOf(ExportType.FILE, ExportType.DROPBOX, ExportType.COMMUNITY)
        orderedTypes.forEach { type ->
            createGroupViewForExportType(fileBaseName, type)?.let { groupView ->
                expandedView.addView(R.id.notification_main_container, groupView)
            }
        }

        val contentText = getContentText(fileBaseName)

        val notification = NotificationCompat.Builder(context, TrainingApplication.NOTIFICATION_CHANNEL__EXPORT)
            .setSmallIcon(R.drawable.logo)
            .setContentTitle(title)       // Fallback für Wear OS etc.
            .setContentText(contentText)  // Fallback für Wear OS etc.
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomBigContentView(expandedView)
            .setOngoing(isStillRunning)
            .setAutoCancel(!isStillRunning)
            .setContentIntent(pendingIntentStartWorkoutListActivity)
            .build()

        notificationManager.notify(generateNotificationId(exportInfo), notification)
    }


    /***********************************************************************************************
     * Method to create one group
     **********************************************************************************************/

    private fun createGroupViewForExportType(fileBaseName: String, exportType: ExportType): RemoteViews? {
        val data = uiDataProvider.createGroupData(fileBaseName, exportType)

        return data.takeIf { it.hasContent }?.let {
            RemoteViews(context.packageName, R.layout.export_notification__group).apply {
                setTextViewText(R.id.group_title, it.groupTitle)
                updateRemoteViewLine(this, R.id.line_waiting, it.waitingLine)
                updateRemoteViewLine(this, R.id.line_running, it.runningLine)
                updateRemoteViewLine(this, R.id.line_succeeded, it.succeededLine)
                updateRemoteViewLine(this, R.id.line_failed, it.failedLine)
            }
        }
    }

    private fun updateRemoteViewLine(remoteViews: RemoteViews, viewId: Int, text: String?) {
        // Die 'let' Scope-Funktion behandelt den Null-Check elegant
        text?.let {
            remoteViews.setTextViewText(viewId, it)
            remoteViews.setViewVisibility(viewId, View.VISIBLE)
        } ?: remoteViews.setViewVisibility(viewId, View.GONE)
    }

    /**********************************************************************************************
     * Short summary of the active Exports
     *********************************************************************************************/
    private fun getContentText(fileBaseName: String): String {
        if (DEBUG) Log.i(TAG, "getContentText: $fileBaseName")

        val exportTypeMap = activeExports[fileBaseName]

        return if (exportTypeMap.isNullOrEmpty()) {
            context.getString(R.string.export_notification__summary__started)
        } else {
            val activeExportsList = exportTypeMap.entries
                .filter { it.value.isNotEmpty() }
                .joinToString(", ") { context.getString(it.key.uiId) }

            if (activeExportsList.isEmpty()) {
                context.getString(R.string.export_notification__summary__finished)
            } else {
                context.getString(R.string.export_notification__summary__in_progress_to, activeExportsList)
            }
        }
    }

    /***********************************************************************************************
     * some simple helpers
     **********************************************************************************************/

    private fun createPendingIntentStartWorkoutListActivity(): PendingIntent {
        val intent = Intent(context, MainActivityWithNavigation::class.java).apply {
            putExtra(MainActivityWithNavigation.SELECTED_FRAGMENT, MainActivityWithNavigation.SelectedFragment.WORKOUT_LIST.name)
            flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        }
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
    }

    private fun isMissingPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    }

    private fun generateNotificationId(exportInfo: ExportInfo): Int {
        return exportInfo.fileBaseName.hashCode()
    }

    /**
     * Companion-Object for the  Singleton-Pattern.
     */
    companion object {
        private const val DEBUG = true
        private const val TAG = "ExportNotificationManager"

        @Volatile
        private var sInstance: ExportNotificationManager? = null

        // Double-Checked Locking for a thread-safe Singleton
        fun getInstance(context: Context): ExportNotificationManager {
            return sInstance ?: synchronized(this) {
                sInstance ?: ExportNotificationManager(context.applicationContext).also { sInstance = it }
            }
        }
    }

    /***********************************************************************************************
     * finally, a method for debugging
     **********************************************************************************************/
    private fun logActiveExports(fileBaseName: String) {
        Log.d(TAG, "vvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv")
        Log.d(TAG, "Current state of mActiveExports:")
        if (activeExports.isEmpty()) {
            Log.d(TAG, "  -> Map is empty")
        } else {
            activeExports.forEach { (file, exportTypeMap) ->
                Log.d(TAG, "  File: $file")
                if (exportTypeMap.isEmpty()) {
                    Log.d(TAG, "    -> No active export types.")
                } else {
                    exportTypeMap.forEach { (type, formatsSet) ->
                        val formats = formatsSet.joinToString(", ") { it.name }
                        Log.d(TAG, "    - ${type.name}: [$formats]")
                    }
                }
            }
        }
        Log.d(TAG, "^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^")
    }
}