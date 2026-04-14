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

package com.atrainingtracker.trainingtracker.ui.tracking

import android.content.Context
import android.content.Intent
import android.util.Log
import com.atrainingtracker.banalservice.ActivityType
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager
import com.atrainingtracker.banalservice.filters.FilterType
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.database.TrackingViewsDatabaseManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

// Data class for holding tab info, can be moved to a more common location later.
data class TrackingViewInfo(
    val tabViewId: Long,
    val name: String,
    val showMap: Boolean,
    val showLapButton: Boolean,
    val showLiveSegments: Boolean
)


/**
 * A data class to hold the complete configuration for a single sensor field,
 * as loaded from the TrackingViewsDatabaseManager.
 */
data class SensorFieldConfig(
    val sensorFieldId: Long,
    val rowNr: Int,
    val colNr: Int,
    val viewSize: ViewSize,
    val sensorType: SensorType,
    val filterType: FilterType,
    val filterConstant: Double,
    val sourceDeviceId: Long,
    val sourceDeviceName: String? = null
)

enum class ScreenMode {
    /** The screen is used for actively tracking a workout. Long-clicks are handled. */
    TRACKING,
    /** The screen is used for configuring the layout. Normal clicks are handled for editing. */
    CONFIGURATION
}


/**
 * A singleton repository that acts as the single source of truth for all tracking-related view data.
 * It connects to the local database to provide a clean data source for all ViewModels.
 */
class TrackingViewsRepository private constructor(private val context: Context) {


    private val viewsDbManager = TrackingViewsDatabaseManager.getInstance(context)
    // Add a member for the DevicesDatabaseManager
    private val devicesDbManager = DevicesDatabaseManager.getInstance(context)

    // This StateFlow acts as a signal. Changing its value will trigger a refresh of the sensor field data
    private val configUpdateTrigger = MutableStateFlow(0)


    // -- screen mode
    private val _screenMode = MutableStateFlow(ScreenMode.TRACKING)
    val screenMode: StateFlow<ScreenMode> = _screenMode.asStateFlow()

    fun toggleScreenMode() {
        _screenMode.value = if (_screenMode.value == ScreenMode.TRACKING) {
            ScreenMode.CONFIGURATION
        } else {
            ScreenMode.TRACKING
        }
    }


    /**
     * Retrieves the ActivityType associated with a specific view definition.
     */
    suspend fun getActivityTypeForView(tabViewId: Long): ActivityType {
        return withContext(Dispatchers.IO) {
            val activityType = viewsDbManager.getActivityTypeForTab(tabViewId)
            activityType
        }
    }


    // --- Tracking Views ---
    /**
     * Provides a flow of [TrackingViewInfo] for a specific tab ID.
     * This will emit a new value whenever the [configUpdateTrigger] is incremented
     * (e.g., after calling [updateShowMap]).
     */
    fun getTrackingViewInfoFlow(tabViewId: Long): Flow<TrackingViewInfo?> {
        return configUpdateTrigger.map {
            fetchTrackingViewInfo(tabViewId)
        }.flowOn(Dispatchers.IO)
    }

    /**
     * Helper to fetch a single row from the VIEWS_TABLE.
     */
    private fun fetchTrackingViewInfo(tabViewId: Long): TrackingViewInfo? {
        val cursor = viewsDbManager.database.query(
            TrackingViewsDatabaseManager.TrackingViewsDbHelper.VIEWS_TABLE,
            arrayOf(
                TrackingViewsDatabaseManager.TrackingViewsDbHelper.C_ID,
                TrackingViewsDatabaseManager.TrackingViewsDbHelper.NAME,
                TrackingViewsDatabaseManager.TrackingViewsDbHelper.SHOW_MAP,
                TrackingViewsDatabaseManager.TrackingViewsDbHelper.SHOW_LAP_BUTTON,
                TrackingViewsDatabaseManager.TrackingViewsDbHelper.SHOW_LIVE_SEGMENTS
            ),
            "${TrackingViewsDatabaseManager.TrackingViewsDbHelper.C_ID}=?",
            arrayOf(tabViewId.toString()),
            null, null, null
        )

        cursor.use {
            if (it.moveToFirst()) {
                val id = it.getLong(it.getColumnIndexOrThrow(TrackingViewsDatabaseManager.TrackingViewsDbHelper.C_ID))
                val name = it.getString(it.getColumnIndexOrThrow(TrackingViewsDatabaseManager.TrackingViewsDbHelper.NAME))
                val showMap = it.getInt(it.getColumnIndexOrThrow(TrackingViewsDatabaseManager.TrackingViewsDbHelper.SHOW_MAP)) == 1
                val showLapButton = it.getInt(it.getColumnIndexOrThrow(TrackingViewsDatabaseManager.TrackingViewsDbHelper.SHOW_LAP_BUTTON)) == 1
                val showLiveSegments = it.getInt(it.getColumnIndexOrThrow(TrackingViewsDatabaseManager.TrackingViewsDbHelper.SHOW_LIVE_SEGMENTS)) == 1
                return TrackingViewInfo(id, name, showMap, showLapButton, showLiveSegments)
            }
        }
        return null
    }



    /**
     * A flow that emits the list of tracking views whenever the config changes.
     */
    fun getTrackingViewsFlow(activityType: ActivityType): Flow<List<TrackingViewInfo>> {
        return configUpdateTrigger.map {
            getTrackingViews(activityType)
        }.flowOn(Dispatchers.IO)
    }

    /**
     * Loads the list of available tracking views for a given activity type from the database.
     */
    private fun getTrackingViews(activityType: ActivityType): List<TrackingViewInfo> {
        val dbManager = TrackingViewsDatabaseManager.getInstance(context)
        val viewList = mutableListOf<TrackingViewInfo>()

        val cursor = dbManager.database.query(
            TrackingViewsDatabaseManager.TrackingViewsDbHelper.VIEWS_TABLE,
            arrayOf(
                TrackingViewsDatabaseManager.TrackingViewsDbHelper.C_ID,
                TrackingViewsDatabaseManager.TrackingViewsDbHelper.NAME,
                TrackingViewsDatabaseManager.TrackingViewsDbHelper.SHOW_MAP,
                TrackingViewsDatabaseManager.TrackingViewsDbHelper.SHOW_LAP_BUTTON,
                TrackingViewsDatabaseManager.TrackingViewsDbHelper.SHOW_LIVE_SEGMENTS
            ),
            "${TrackingViewsDatabaseManager.TrackingViewsDbHelper.ACTIVITY_TYPE}=?",
            arrayOf(activityType.name),
            null,
            null,
            "${TrackingViewsDatabaseManager.TrackingViewsDbHelper.LAYOUT_NR} ASC"
        )

        cursor.use {
            if (it.moveToFirst()) {
                do {
                    val id = it.getLong(it.getColumnIndexOrThrow(TrackingViewsDatabaseManager.TrackingViewsDbHelper.C_ID))
                    val name = it.getString(it.getColumnIndexOrThrow(TrackingViewsDatabaseManager.TrackingViewsDbHelper.NAME))
                    val showMap = it.getInt(it.getColumnIndexOrThrow(TrackingViewsDatabaseManager.TrackingViewsDbHelper.SHOW_MAP)) == 1
                    val showLapButton = it.getInt(it.getColumnIndexOrThrow(TrackingViewsDatabaseManager.TrackingViewsDbHelper.SHOW_LAP_BUTTON)) == 1
                    val showLiveSegments = it.getInt(it.getColumnIndexOrThrow(TrackingViewsDatabaseManager.TrackingViewsDbHelper.SHOW_LIVE_SEGMENTS)) == 1

                    viewList.add(TrackingViewInfo(id, name, showMap, showLapButton, showLiveSegments))
                } while (it.moveToNext())
            }
        }
        return viewList
    }

    /**
     * Retrieves a flow of sensor configurations for a given view.
     * This flow will automatically re-emit the latest data whenever the configUpdateTrigger changes.
     */
    fun getSensorFieldConfigsForView(tabViewId: Long): Flow<List<SensorFieldConfig>> {
        // The flow now collects from the trigger.
        return configUpdateTrigger.map {
            // When the trigger changes, this 'map' block re-executes.
            fetchSensorFieldConfigs(tabViewId)
        }.flowOn(Dispatchers.IO)
    }

    private fun fetchSensorFieldConfigs(tabViewId: Long): List<SensorFieldConfig> {

        val dbManager = TrackingViewsDatabaseManager.getInstance(context)
        val devicesDbManager = DevicesDatabaseManager.getInstance(context)

        val fieldList = mutableListOf<SensorFieldConfig>()


        // Query the ROWS_TABLE for all rows belonging to the given viewId
        val cursor = dbManager.database.query(
            TrackingViewsDatabaseManager.TrackingViewsDbHelper.ROWS_TABLE,
            null, // Get all columns
            "${TrackingViewsDatabaseManager.TrackingViewsDbHelper.VIEW_ID}=?",
            arrayOf(tabViewId.toString()),
            null,
            null,
            "${TrackingViewsDatabaseManager.TrackingViewsDbHelper.ROW_NR} ASC, ${TrackingViewsDatabaseManager.TrackingViewsDbHelper.COL_NR} ASC"
        )

        cursor.use { c ->
            if (c.moveToFirst()) {
                // Pre-fetch column indices for efficiency inside the loop
                val sensorFieldIndex = c.getColumnIndexOrThrow(TrackingViewsDatabaseManager.TrackingViewsDbHelper.ROW_ID)
                val rowNrIndex = c.getColumnIndexOrThrow(TrackingViewsDatabaseManager.TrackingViewsDbHelper.ROW_NR)
                val colNrIndex = c.getColumnIndexOrThrow(TrackingViewsDatabaseManager.TrackingViewsDbHelper.COL_NR)
                val sensorTypeIndex = c.getColumnIndexOrThrow(TrackingViewsDatabaseManager.TrackingViewsDbHelper.SENSOR_TYPE)
                val filterTypeIndex = c.getColumnIndexOrThrow(TrackingViewsDatabaseManager.TrackingViewsDbHelper.FILTER_TYPE)
                val filterConstantIndex = c.getColumnIndexOrThrow(TrackingViewsDatabaseManager.TrackingViewsDbHelper.FILTER_CONSTANT)
                val viewSizeIndex = c.getColumnIndexOrThrow(TrackingViewsDatabaseManager.TrackingViewsDbHelper.VIEW_SIZE)
                val deviceIdIndex = c.getColumnIndexOrThrow(TrackingViewsDatabaseManager.TrackingViewsDbHelper.SOURCE_DEVICE_ID)

                do {
                    val sizeString = c.getString(viewSizeIndex)
                    val viewSize = try {
                        ViewSize.valueOf(sizeString)
                    } catch (e: IllegalArgumentException) {
                        ViewSize.NORMAL // Fallback for invalid or null data
                    }

                    val sourceDeviceId = c.getLong(deviceIdIndex)
                    val deviceName = if (sourceDeviceId > 0) {
                        devicesDbManager.getDeviceName(sourceDeviceId)
                    }
                    else {
                        null
                    }

                    fieldList.add(
                        SensorFieldConfig(
                            sensorFieldId = c.getLong(sensorFieldIndex),
                            rowNr = c.getInt(rowNrIndex),
                            colNr = c.getInt(colNrIndex),
                            viewSize = viewSize, // Use the directly parsed enum value
                            sensorType = SensorType.valueOf(c.getString(sensorTypeIndex)),
                            filterType = FilterType.valueOf(c.getString(filterTypeIndex)),
                            filterConstant = c.getDouble(filterConstantIndex),
                            sourceDeviceId = sourceDeviceId,
                            sourceDeviceName = deviceName
                        )
                    )
                } while (c.moveToNext())
            }
        }
        return fieldList
    }

    fun getSensorFieldConfig(sensorFieldId: Long): Flow<SensorFieldConfig?> {
        // This flow is now driven by the same trigger as the list-based flow.
        return configUpdateTrigger.map {
            // When the trigger changes, this block re-executes.
            fetchSingleSensorFieldConfig(sensorFieldId)
        }.flowOn(Dispatchers.IO)
    }

    // --- Renamed the original suspend function to be a private helper ---
    private suspend fun fetchSingleSensorFieldConfig(sensorFieldId: Long): SensorFieldConfig? {
        return withContext(Dispatchers.IO) {
            val cursor = viewsDbManager.database.query(
                TrackingViewsDatabaseManager.TrackingViewsDbHelper.ROWS_TABLE,
                null,
                "${TrackingViewsDatabaseManager.TrackingViewsDbHelper.ROW_ID}=?",
                arrayOf(sensorFieldId.toString()), null, null, null
            )
            cursor.use { c ->
                if (c.moveToFirst()) {
                    val sizeString = c.getString(c.getColumnIndexOrThrow(TrackingViewsDatabaseManager.TrackingViewsDbHelper.VIEW_SIZE))
                    val viewSize = try { ViewSize.valueOf(sizeString) } catch (e: IllegalArgumentException) { ViewSize.NORMAL }
                    val sourceDeviceId = c.getLong(c.getColumnIndexOrThrow(TrackingViewsDatabaseManager.TrackingViewsDbHelper.SOURCE_DEVICE_ID))
                    val deviceName = if (sourceDeviceId > 0) devicesDbManager.getDeviceName(sourceDeviceId) else null
                    return@withContext SensorFieldConfig(
                        sensorFieldId = sensorFieldId,
                        rowNr = c.getInt(c.getColumnIndexOrThrow(TrackingViewsDatabaseManager.TrackingViewsDbHelper.ROW_NR)),
                        colNr = c.getInt(c.getColumnIndexOrThrow(TrackingViewsDatabaseManager.TrackingViewsDbHelper.COL_NR)),
                        viewSize = viewSize,
                        sensorType = SensorType.valueOf(c.getString(c.getColumnIndexOrThrow(TrackingViewsDatabaseManager.TrackingViewsDbHelper.SENSOR_TYPE))),
                        filterType = FilterType.valueOf(c.getString(c.getColumnIndexOrThrow(TrackingViewsDatabaseManager.TrackingViewsDbHelper.FILTER_TYPE))),
                        filterConstant = c.getDouble(c.getColumnIndexOrThrow(TrackingViewsDatabaseManager.TrackingViewsDbHelper.FILTER_CONSTANT)),
                        sourceDeviceId = sourceDeviceId,
                        sourceDeviceName = deviceName
                    )
                } else {
                    return@withContext null // Return null if not found
                }
            }
        }
    }

    suspend fun getDeviceLists(sensorType: SensorType): DevicesDatabaseManager.DeviceIdAndNameLists? {
        return withContext(Dispatchers.IO) {
            devicesDbManager.getDeviceIdAndNameLists(sensorType)
        }
    }

    fun requestNewLap() {
        context.sendBroadcast(
            Intent(TrainingApplication.REQUEST_NEW_LAP)
                .setPackage(context.packageName)
        )
    }

    // -- Configuring Sensor Fields
    /**
     * Updates the configuration of a specific sensor field in the database.
     */
    suspend fun updateSensorFieldConfig(
        sensorFieldId: Long,
        newSensorType: SensorType,
        newViewSize: ViewSize,
        newSourceDeviceId: Long?,
        newSourceDeviceName: String?,
        newFilterType: FilterType,
        newFilterConstant: Double
    ) {
        withContext(Dispatchers.IO) {

            // write the new config to the database
            viewsDbManager.updateSensorView(
                sensorFieldId,
                newSensorType,
                newViewSize,
                newSourceDeviceId,
                newFilterType,
                newFilterConstant
            )
        }

        // notify collectors that the data has changed by incrementing the value
        withContext(Dispatchers.Main) {
            configUpdateTrigger.value++
        }
    }

    suspend fun insertSensorFieldConfig(
        tabViewId: Long,
        rowNr: Int,
        colNr: Int,
        newSensorType: SensorType,
        newViewSize: ViewSize,
        newSourceDeviceId: Long?,
        newSourceDeviceName: String?,
        newFilterType: FilterType,
        newFilterConstant: Double
    ) {
        Log.i("TrackingRepository", "insertSensorFieldConfig: $tabViewId, $rowNr, $colNr")

        withContext(Dispatchers.IO) {
            viewsDbManager.insertSensorFieldAt(
                tabViewId,
                rowNr,
                colNr,
                newSensorType,
                newViewSize,
                newSourceDeviceId,
                newFilterType,
                newFilterConstant)
        }

        // trigger recreation of UI
        withContext(Dispatchers.Main) {
            configUpdateTrigger.value++
        }
    }

    suspend fun deleteSensorField(sensorFieldId: Long) {
        // delete from DB
        withContext(Dispatchers.IO) {
            viewsDbManager.deleteSensorField(sensorFieldId)
        }

        // trigger recreation of UI
        withContext(Dispatchers.Main) {
            configUpdateTrigger.value++
        }
    }

    /*******************************************************************
     * Configure Tabs
    **/
    suspend fun updateTabName(tabViewId: Long, name: String) {
        withContext(Dispatchers.IO) {
            viewsDbManager.updateNameOfTabView(tabViewId, name)
        }

       // trigger recreation of UI
        withContext(Dispatchers.Main) {
            configUpdateTrigger.value++
        }
    }

    suspend fun updateShowMap(tabViewId: Long, showMap: Boolean) {
        withContext(Dispatchers.IO) {
            viewsDbManager.updateShowMap(tabViewId, showMap)
        }

        // trigger recreation of UI
        withContext(Dispatchers.Main) {
            configUpdateTrigger.value++
        }
    }

    suspend fun updateShowLapButton(tabViewId: Long, showLapButton: Boolean) {
        withContext(Dispatchers.IO) {
            viewsDbManager.updateShowLapButton(tabViewId, showLapButton)
        }

        // trigger recreation of UI
        withContext(Dispatchers.Main) {
            configUpdateTrigger.value++
        }
    }

    suspend fun updateShowLiveSegments(tabViewId: Long, showLiveSegments: Boolean) {
        withContext(Dispatchers.IO) {
            viewsDbManager.updateShowLiveSegments(tabViewId, showLiveSegments)
        }

        // trigger recreation of UI
        withContext(Dispatchers.Main) {
            configUpdateTrigger.value++
        }
    }


    suspend fun addEmptyTabView(tabViewId: Long, addAfter: Boolean) {
        withContext(Dispatchers.IO) {
            viewsDbManager.addEmptyTabView(tabViewId, addAfter)
        }

        // trigger recreation of UI
        withContext(Dispatchers.Main) {
            configUpdateTrigger.value++
        }
    }

    suspend fun deleteTab(tabViewId: Long) {
        withContext(Dispatchers.IO) {
            viewsDbManager.deleteTabView(tabViewId)
        }

        // trigger recreation of UI
        withContext(Dispatchers.Main) {
            configUpdateTrigger.value++
        }
    }




    companion object {
        @Volatile
        private var INSTANCE: TrackingViewsRepository? = null

        fun getInstance(context: Context): TrackingViewsRepository {
            return INSTANCE ?: synchronized(this) {
                val instance = TrackingViewsRepository(context)
                INSTANCE = instance
                instance
            }
        }
    }
}