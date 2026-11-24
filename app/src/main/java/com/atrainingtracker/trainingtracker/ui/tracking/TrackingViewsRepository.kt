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
import android.database.Cursor
import android.util.Log
import com.atrainingtracker.banalservice.ActivityType
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager
import com.atrainingtracker.banalservice.filters.FilterData
import com.atrainingtracker.banalservice.filters.FilterType
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.database.TrackingViewsDatabaseManager
import com.atrainingtracker.util.safeEnumValueOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

enum class ElevationProfileScope(val dbValue: Int) {
    FULL_ROUTE(0),
    ROLLING_WINDOW(1);

    companion object {
        fun fromDbValue(value: Int): ElevationProfileScope =
            entries.firstOrNull { it.dbValue == value } ?: FULL_ROUTE
    }
}

data class TrackingViewInfo(
    val tabViewId: Long,
    val name: String,
    val showMap: Boolean,
    val showLapButton: Boolean,
    val showLiveSegments: Boolean,
    val showElevationProfile: Boolean,
    val showLiveClimbs: Boolean = true,
    val showNavigationHints: Boolean = true,
    val elevationProfileScope: ElevationProfileScope = ElevationProfileScope.FULL_ROUTE,
    val elevationProfileLookahead: Int = 2000,
    val showBikeBell: Boolean = false,
    val showMmpCurve: Boolean = false,
    val showRadarLight: Boolean = false
)

/**
 * Data class representing a horizontal section divider configuration for Cockpit rows (REQ-UI-411).
 */
data class SectionDividerInfo(
    val id: Long = 0,
    val viewId: Long,
    val rowNr: Int,
    val title: String? = null
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
    val sourceDeviceName: String? = null,
    val showValue: Boolean = true,
    val showBar: Boolean = false,
    val showGraph: Boolean = false,
    val graphHorizonSeconds: Int = 300,
    val colWeight: Float = 1.0f,
    val graphColor: String = "GLOBAL_DEFAULT"
)

/**
 * Domain DTO encapsulating paired device identifier and name lists (REQ-ARC-009.1).
 */
data class DeviceIdAndNameLists(
    val deviceIds: List<Long>?,
    val names: List<String>?
)

enum class ScreenMode {
    /** The screen is used for actively tracking a workout. Long-clicks are handled. */
    TRACKING,
    /** The screen is used for configuring the layout. Normal clicks are handled for editing. */
    CONFIGURATION,
    PREVIEW
}


/**
 * A singleton repository that acts as the single source of truth for all tracking-related view data.
 * It connects to the local database to provide a clean data source for all ViewModels.
 */
class TrackingViewsRepository private constructor(private val context: Context) {

    private val appContext: Context = context.applicationContext ?: context
    private val viewsDbManager = TrackingViewsDatabaseManager.getInstance(context)
    // Add a member for the DevicesDatabaseManager
    private val devicesDbManager = DevicesDatabaseManager.getInstance(context)

    // This StateFlow acts as a signal. Changing its value will trigger a refresh of the sensor field data
    private val configUpdateTrigger = MutableStateFlow(0)

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
            VIEW_PROJECTION,
            "${TrackingViewsDatabaseManager.TrackingViewsDbHelper.C_ID}=?",
            arrayOf(tabViewId.toString()),
            null, null, null
        )
        return cursor.use {
            if (it.moveToFirst()) it.toTrackingViewInfo(appContext) else null
        }
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
            VIEW_PROJECTION,
            "${TrackingViewsDatabaseManager.TrackingViewsDbHelper.ACTIVITY_TYPE}=?",
            arrayOf(activityType.name),
            null, null,
            "${TrackingViewsDatabaseManager.TrackingViewsDbHelper.LAYOUT_NR} ASC"
        )
        cursor.use {
            while (it.moveToNext()) {
                viewList.add(it.toTrackingViewInfo(appContext))
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
                val showValueIndex = c.getColumnIndex(TrackingViewsDatabaseManager.TrackingViewsDbHelper.SHOW_VALUE)
                val showBarIndex = c.getColumnIndex(TrackingViewsDatabaseManager.TrackingViewsDbHelper.SHOW_BAR)
                val colWeightIndex = c.getColumnIndex(TrackingViewsDatabaseManager.TrackingViewsDbHelper.COL_WEIGHT)
                val showGraphIndex = c.getColumnIndex(TrackingViewsDatabaseManager.TrackingViewsDbHelper.SHOW_GRAPH)
                val graphHorizonIndex = c.getColumnIndex(TrackingViewsDatabaseManager.TrackingViewsDbHelper.GRAPH_HORIZON_S)
                val graphColorIndex = c.getColumnIndex(TrackingViewsDatabaseManager.TrackingViewsDbHelper.GRAPH_COLOR)

                do {
                    val viewSize = safeEnumValueOf(c.getString(viewSizeIndex), ViewSize.NORMAL)

                    val sourceDeviceId = c.getLong(deviceIdIndex)
                    val deviceName = if (sourceDeviceId > 0) {
                        devicesDbManager.getDeviceName(sourceDeviceId)
                    }
                    else {
                        null
                    }

                    val sensorType = safeEnumValueOf(c.getString(sensorTypeIndex), SensorType.TIME_ACTIVE)
                    val filterType = safeEnumValueOf(c.getString(filterTypeIndex), FilterType.INSTANTANEOUS)
                    val showValue = if (showValueIndex != -1 && !c.isNull(showValueIndex)) c.getInt(showValueIndex) == 1 else true
                    val showBar = if (showBarIndex != -1 && !c.isNull(showBarIndex)) {
                        c.getInt(showBarIndex) == 1
                    } else {
                        TrackingViewsDatabaseManager.TrackingViewsDbHelper.isBarSupportedSensorType(sensorType)
                    }
                    val colWeight = if (colWeightIndex != -1 && !c.isNull(colWeightIndex)) {
                        val w = c.getFloat(colWeightIndex)
                        if (w > 0f) w else 1.0f
                    } else {
                        1.0f
                    }
                    val showGraph = if (showGraphIndex != -1 && !c.isNull(showGraphIndex)) c.getInt(showGraphIndex) == 1 else false
                    val graphHorizonSeconds = if (graphHorizonIndex != -1 && !c.isNull(graphHorizonIndex)) c.getInt(graphHorizonIndex) else 300
                    val graphColor = if (graphColorIndex != -1 && !c.isNull(graphColorIndex)) {
                        c.getString(graphColorIndex) ?: "GLOBAL_DEFAULT"
                    } else {
                        "GLOBAL_DEFAULT"
                    }

                    fieldList.add(
                        SensorFieldConfig(
                            sensorFieldId = c.getLong(sensorFieldIndex),
                            rowNr = c.getInt(rowNrIndex),
                            colNr = c.getInt(colNrIndex),
                            viewSize = viewSize,
                            sensorType = sensorType,
                            filterType = filterType,
                            filterConstant = c.getDouble(filterConstantIndex),
                            sourceDeviceId = sourceDeviceId,
                            sourceDeviceName = deviceName,
                            showValue = showValue,
                            showBar = showBar,
                            showGraph = showGraph,
                            graphHorizonSeconds = graphHorizonSeconds,
                            colWeight = colWeight,
                            graphColor = graphColor
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
                    val viewSize = safeEnumValueOf(sizeString, ViewSize.NORMAL)
                    val sourceDeviceId = c.getLong(c.getColumnIndexOrThrow(TrackingViewsDatabaseManager.TrackingViewsDbHelper.SOURCE_DEVICE_ID))
                    val deviceName = if (sourceDeviceId > 0) devicesDbManager.getDeviceName(sourceDeviceId) else null
                    val sensorTypeString = c.getString(c.getColumnIndexOrThrow(TrackingViewsDatabaseManager.TrackingViewsDbHelper.SENSOR_TYPE))
                    val sensorType = safeEnumValueOf(sensorTypeString, SensorType.TIME_ACTIVE)
                    val filterTypeString = c.getString(c.getColumnIndexOrThrow(TrackingViewsDatabaseManager.TrackingViewsDbHelper.FILTER_TYPE))
                    val filterType = safeEnumValueOf(filterTypeString, FilterType.INSTANTANEOUS)
                    val showValueIndex = c.getColumnIndex(TrackingViewsDatabaseManager.TrackingViewsDbHelper.SHOW_VALUE)
                    val showBarIndex = c.getColumnIndex(TrackingViewsDatabaseManager.TrackingViewsDbHelper.SHOW_BAR)
                    val colWeightIndex = c.getColumnIndex(TrackingViewsDatabaseManager.TrackingViewsDbHelper.COL_WEIGHT)
                    val showGraphIndex = c.getColumnIndex(TrackingViewsDatabaseManager.TrackingViewsDbHelper.SHOW_GRAPH)
                    val graphHorizonIndex = c.getColumnIndex(TrackingViewsDatabaseManager.TrackingViewsDbHelper.GRAPH_HORIZON_S)
                    val graphColorIndex = c.getColumnIndex(TrackingViewsDatabaseManager.TrackingViewsDbHelper.GRAPH_COLOR)
                    val showValue = if (showValueIndex != -1 && !c.isNull(showValueIndex)) c.getInt(showValueIndex) == 1 else true
                    val showBar = if (showBarIndex != -1 && !c.isNull(showBarIndex)) {
                        c.getInt(showBarIndex) == 1
                    } else {
                        TrackingViewsDatabaseManager.TrackingViewsDbHelper.isBarSupportedSensorType(sensorType)
                    }
                    val colWeight = if (colWeightIndex != -1 && !c.isNull(colWeightIndex)) {
                        val w = c.getFloat(colWeightIndex)
                        if (w > 0f) w else 1.0f
                    } else {
                        1.0f
                    }
                    val showGraph = if (showGraphIndex != -1 && !c.isNull(showGraphIndex)) c.getInt(showGraphIndex) == 1 else false
                    val graphHorizonSeconds = if (graphHorizonIndex != -1 && !c.isNull(graphHorizonIndex)) c.getInt(graphHorizonIndex) else 300
                    val graphColor = if (graphColorIndex != -1 && !c.isNull(graphColorIndex)) {
                        c.getString(graphColorIndex) ?: "GLOBAL_DEFAULT"
                    } else {
                        "GLOBAL_DEFAULT"
                    }

                    return@withContext SensorFieldConfig(
                        sensorFieldId = sensorFieldId,
                        rowNr = c.getInt(c.getColumnIndexOrThrow(TrackingViewsDatabaseManager.TrackingViewsDbHelper.ROW_NR)),
                        colNr = c.getInt(c.getColumnIndexOrThrow(TrackingViewsDatabaseManager.TrackingViewsDbHelper.COL_NR)),
                        viewSize = viewSize,
                        sensorType = sensorType,
                        filterType = filterType,
                        filterConstant = c.getDouble(c.getColumnIndexOrThrow(TrackingViewsDatabaseManager.TrackingViewsDbHelper.FILTER_CONSTANT)),
                        sourceDeviceId = sourceDeviceId,
                        sourceDeviceName = deviceName,
                        showValue = showValue,
                        showBar = showBar,
                        showGraph = showGraph,
                        graphHorizonSeconds = graphHorizonSeconds,
                        colWeight = colWeight,
                        graphColor = graphColor
                    )
                } else {
                    return@withContext null // Return null if not found
                }
            }
        }
    }

    suspend fun getDeviceLists(sensorType: SensorType): DeviceIdAndNameLists? {
        return withContext(Dispatchers.IO) {
            val raw = devicesDbManager.getDeviceIdAndNameLists(sensorType) ?: return@withContext null
            DeviceIdAndNameLists(raw.deviceIds, raw.names)
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
     * Executes a view-configuration database mutation on [Dispatchers.IO] and then increments
     * [configUpdateTrigger] on [Dispatchers.Main] to notify UI collectors ({@code REQ-DAT-047.3}).
     */
    private suspend fun <T> mutateViewConfig(block: () -> T): T {
        val result = withContext(Dispatchers.IO) {
            block()
        }
        withContext(Dispatchers.Main) {
            configUpdateTrigger.value++
        }
        return result
    }

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
        newFilterConstant: Double,
        showValue: Boolean = true,
        showBar: Boolean = false,
        colWeight: Float = 1.0f,
        showGraph: Boolean = false,
        graphHorizonSeconds: Int = 300,
        graphColor: String = "GLOBAL_DEFAULT"
    ) = mutateViewConfig {
        viewsDbManager.updateSensorView(
            sensorFieldId,
            newSensorType,
            newViewSize,
            newSourceDeviceId,
            newFilterType,
            newFilterConstant,
            showValue,
            showBar,
            colWeight,
            showGraph,
            graphHorizonSeconds,
            graphColor
        )
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
        newFilterConstant: Double,
        showValue: Boolean = true,
        showBar: Boolean = false,
        colWeight: Float = 1.0f,
        showGraph: Boolean = false,
        graphHorizonSeconds: Int = 300,
        graphColor: String = "GLOBAL_DEFAULT"
    ) {
        Log.i("TrackingRepository", "insertSensorFieldConfig: $tabViewId, $rowNr, $colNr")
        mutateViewConfig {
            viewsDbManager.insertSensorFieldAt(
                tabViewId,
                rowNr,
                colNr,
                newSensorType,
                newViewSize,
                newSourceDeviceId,
                newFilterType,
                newFilterConstant,
                showValue,
                showBar,
                colWeight,
                showGraph,
                graphHorizonSeconds,
                graphColor
            )
        }
    }

    suspend fun updateColWeight(sensorFieldId: Long, colWeight: Float) = mutateViewConfig {
        viewsDbManager.updateColWeight(sensorFieldId, colWeight)
    }

    suspend fun updateRowSplit(tabViewId: Long, rowNr: Int, leftWeight: Float, rightWeight: Float) = mutateViewConfig {
        viewsDbManager.updateRowSplit(tabViewId, rowNr, leftWeight, rightWeight)
    }

    suspend fun deleteSensorField(sensorFieldId: Long) = mutateViewConfig {
        viewsDbManager.deleteSensorField(sensorFieldId)
    }

    suspend fun swapSensorFields(fieldIdA: Long, fieldIdB: Long) = mutateViewConfig {
        viewsDbManager.swapSensorFields(fieldIdA, fieldIdB)
    }

    suspend fun moveSensorField(sensorFieldId: Long, targetRow: Int, targetCol: Int) = mutateViewConfig {
        viewsDbManager.moveSensorField(sensorFieldId, targetRow, targetCol)
    }


    /*******************************************************************
     * Configure Tabs
    **/
    suspend fun updateTabName(tabViewId: Long, name: String) = mutateViewConfig {
        viewsDbManager.updateNameOfTabView(tabViewId, name)
    }

    suspend fun updateShowMap(tabViewId: Long, showMap: Boolean) = mutateViewConfig {
        viewsDbManager.updateShowMap(tabViewId, showMap)
    }

    suspend fun updateShowLapButton(tabViewId: Long, showLapButton: Boolean) = mutateViewConfig {
        viewsDbManager.updateShowLapButton(tabViewId, showLapButton)
    }

    suspend fun updateShowLiveSegments(tabViewId: Long, showLiveSegments: Boolean) = mutateViewConfig {
        viewsDbManager.updateShowLiveSegments(tabViewId, showLiveSegments)
    }

    suspend fun updateShowElevationProfile(tabViewId: Long, showElevationProfile: Boolean) = mutateViewConfig {
        viewsDbManager.updateShowElevationProfile(tabViewId, showElevationProfile)
    }

    suspend fun updateShowLiveClimbs(tabViewId: Long, showLiveClimbs: Boolean) = mutateViewConfig {
        viewsDbManager.updateShowLiveClimbs(tabViewId, showLiveClimbs)
    }

    suspend fun updateShowNavigationHints(tabViewId: Long, showNavigationHints: Boolean) = mutateViewConfig {
        viewsDbManager.updateShowNavigationHints(tabViewId, showNavigationHints)
    }

    suspend fun updateShowBikeBell(tabViewId: Long, showBikeBell: Boolean) = mutateViewConfig {
        viewsDbManager.updateShowBikeBell(tabViewId, showBikeBell)
    }

    suspend fun updateShowMmpCurve(tabViewId: Long, showMmpCurve: Boolean) = mutateViewConfig {
        viewsDbManager.updateShowMmpCurve(tabViewId, showMmpCurve)
    }

    suspend fun updateShowRadarLight(tabViewId: Long, showRadarLight: Boolean) = mutateViewConfig {
        viewsDbManager.updateShowRadarLight(tabViewId, showRadarLight)
    }

    suspend fun updateElevationProfileScope(tabViewId: Long, scope: ElevationProfileScope) = mutateViewConfig {
        viewsDbManager.updateElevationProfileScope(tabViewId, scope.dbValue)
    }

    suspend fun updateElevationProfileLookahead(tabViewId: Long, lookaheadMeters: Int) = mutateViewConfig {
        viewsDbManager.updateElevationProfileLookahead(tabViewId, lookaheadMeters)
    }

    suspend fun addEmptyTabView(tabViewId: Long, addAfter: Boolean) = mutateViewConfig {
        viewsDbManager.addEmptyTabView(tabViewId, addAfter)
    }

    suspend fun deleteTab(tabViewId: Long) = mutateViewConfig {
        viewsDbManager.deleteTabView(tabViewId)
    }

    suspend fun moveTabView(tabViewId: Long, moveLeft: Boolean): Int = mutateViewConfig {
        viewsDbManager.moveTabView(tabViewId, moveLeft)
    }

    /**
     * Deep-copies the specified tab to the given target ActivityTypes.
     * Executes on Dispatchers.IO and triggers configUpdateTrigger on Dispatchers.Main.
     *
     * @return List of newly created view IDs.
     */
    suspend fun copyTabView(sourceViewId: Long, targetTypes: List<ActivityType>): List<Long> = mutateViewConfig {
        viewsDbManager.copyTabViewToActivityTypes(sourceViewId, targetTypes)
    }

    // --- Section Dividers (REQ-UI-411) ---

    fun getSectionDividersFlow(tabViewId: Long): Flow<List<SectionDividerInfo>> {
        return configUpdateTrigger.map {
            fetchSectionDividers(tabViewId)
        }.flowOn(Dispatchers.IO)
    }

    private fun fetchSectionDividers(tabViewId: Long): List<SectionDividerInfo> {
        val list = viewsDbManager.getSectionDividers(tabViewId)
        return list.map {
            SectionDividerInfo(
                id = it.id(),
                viewId = it.viewId(),
                rowNr = it.rowNr(),
                title = it.title()
            )
        }
    }

    suspend fun setSectionDivider(viewId: Long, rowNr: Int, title: String?) = mutateViewConfig {
        viewsDbManager.setSectionDivider(viewId, rowNr, title)
    }

    suspend fun deleteSectionDivider(viewId: Long, rowNr: Int) = mutateViewConfig {
        viewsDbManager.deleteSectionDivider(viewId, rowNr)
    }

    /**
     * Retrieves all filter configurations for views (REQ-UI-507.5, REQ-ARC-009.1).
     */
    fun getAllFilterData(): List<FilterData> {
        return viewsDbManager.getAllFilterData(devicesDbManager)
    }




    companion object {
        private const val TAG = "TrackingViewsRepo"

        private val VIEW_PROJECTION = arrayOf(
            TrackingViewsDatabaseManager.TrackingViewsDbHelper.C_ID,
            TrackingViewsDatabaseManager.TrackingViewsDbHelper.NAME,
            TrackingViewsDatabaseManager.TrackingViewsDbHelper.SHOW_MAP,
            TrackingViewsDatabaseManager.TrackingViewsDbHelper.SHOW_LAP_BUTTON,
            TrackingViewsDatabaseManager.TrackingViewsDbHelper.SHOW_LIVE_SEGMENTS,
            TrackingViewsDatabaseManager.TrackingViewsDbHelper.SHOW_ELEVATION_PROFILE,
            TrackingViewsDatabaseManager.TrackingViewsDbHelper.SHOW_LIVE_CLIMBS,
            TrackingViewsDatabaseManager.TrackingViewsDbHelper.SHOW_NAVIGATION_HINTS,
            TrackingViewsDatabaseManager.TrackingViewsDbHelper.ELEVATION_PROFILE_SCOPE,
            TrackingViewsDatabaseManager.TrackingViewsDbHelper.ELEVATION_PROFILE_LOOKAHEAD,
            TrackingViewsDatabaseManager.TrackingViewsDbHelper.SHOW_BIKE_BELL,
            TrackingViewsDatabaseManager.TrackingViewsDbHelper.SHOW_MMP_CURVE,
            TrackingViewsDatabaseManager.TrackingViewsDbHelper.SHOW_RADAR_LIGHT
        )

        private fun Cursor.toTrackingViewInfo(context: Context): TrackingViewInfo {
            val id = getLong(getColumnIndexOrThrow(TrackingViewsDatabaseManager.TrackingViewsDbHelper.C_ID))
            val rawName = getString(getColumnIndexOrThrow(TrackingViewsDatabaseManager.TrackingViewsDbHelper.NAME)) ?: ""
            val name = TrackingTabTitleResolver.resolveTitle(context, rawName)
            val showMap = getInt(getColumnIndexOrThrow(TrackingViewsDatabaseManager.TrackingViewsDbHelper.SHOW_MAP)) == 1
            val showLap = getInt(getColumnIndexOrThrow(TrackingViewsDatabaseManager.TrackingViewsDbHelper.SHOW_LAP_BUTTON)) == 1
            val showLiveSeg = getInt(getColumnIndexOrThrow(TrackingViewsDatabaseManager.TrackingViewsDbHelper.SHOW_LIVE_SEGMENTS)) == 1
            val showElev = getInt(getColumnIndexOrThrow(TrackingViewsDatabaseManager.TrackingViewsDbHelper.SHOW_ELEVATION_PROFILE)) == 1
            val liveClimbsIdx = getColumnIndex(TrackingViewsDatabaseManager.TrackingViewsDbHelper.SHOW_LIVE_CLIMBS)
            val showLiveClimbs = if (liveClimbsIdx != -1) getInt(liveClimbsIdx) == 1 else true
            val navHintsIdx = getColumnIndex(TrackingViewsDatabaseManager.TrackingViewsDbHelper.SHOW_NAVIGATION_HINTS)
            val showNavHints = if (navHintsIdx != -1) getInt(navHintsIdx) == 1 else true
            val scopeIdx = getColumnIndex(TrackingViewsDatabaseManager.TrackingViewsDbHelper.ELEVATION_PROFILE_SCOPE)
            val elevScope = if (scopeIdx != -1) {
                ElevationProfileScope.fromDbValue(getInt(scopeIdx))
            } else {
                ElevationProfileScope.FULL_ROUTE
            }
            val lookaheadIdx = getColumnIndex(TrackingViewsDatabaseManager.TrackingViewsDbHelper.ELEVATION_PROFILE_LOOKAHEAD)
            val elevLookahead = if (lookaheadIdx != -1) getInt(lookaheadIdx).coerceAtLeast(500) else 2000
            val bellIdx = getColumnIndex(TrackingViewsDatabaseManager.TrackingViewsDbHelper.SHOW_BIKE_BELL)
            val showBell = if (bellIdx != -1) getInt(bellIdx) == 1 else false
            val mmpIdx = getColumnIndex(TrackingViewsDatabaseManager.TrackingViewsDbHelper.SHOW_MMP_CURVE)
            val showMmp = if (mmpIdx != -1) getInt(mmpIdx) == 1 else false
            val radarLightIdx = getColumnIndex(TrackingViewsDatabaseManager.TrackingViewsDbHelper.SHOW_RADAR_LIGHT)
            val showRadarLight = if (radarLightIdx != -1) getInt(radarLightIdx) == 1 else false
            return TrackingViewInfo(
                tabViewId = id,
                name = name,
                showMap = showMap,
                showLapButton = showLap,
                showLiveSegments = showLiveSeg,
                showElevationProfile = showElev,
                showLiveClimbs = showLiveClimbs,
                showNavigationHints = showNavHints,
                elevationProfileScope = elevScope,
                elevationProfileLookahead = elevLookahead,
                showBikeBell = showBell,
                showMmpCurve = showMmp,
                showRadarLight = showRadarLight
            )
        }

        @JvmStatic
        fun isBarSupported(sensorType: SensorType?, sportType: BSportType?): Boolean =
            TrackingViewsDatabaseManager.TrackingViewsDbHelper.isBarSupported(sensorType, sportType)

        @JvmStatic
        fun isGraphSupported(sensorType: SensorType?, sportType: BSportType? = null): Boolean =
            TrackingViewsDatabaseManager.TrackingViewsDbHelper.isGraphSupported(sensorType)

        @Volatile
        private var INSTANCE: TrackingViewsRepository? = null

        @JvmStatic
        fun getInstance(context: Context): TrackingViewsRepository {
            return INSTANCE ?: synchronized(this) {
                val instance = TrackingViewsRepository(context)
                INSTANCE = instance
                instance
            }
        }

        @JvmStatic
        @androidx.annotation.VisibleForTesting
        fun resetForTesting(newInstance: TrackingViewsRepository? = null) {
            INSTANCE = newInstance
        }
    }
}