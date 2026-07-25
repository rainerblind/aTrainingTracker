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

package com.atrainingtracker.trainingtracker.ui.aftermath

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.MyHelper
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.database.EquipmentDbHelper
import com.atrainingtracker.trainingtracker.database.ExtremaType
import com.atrainingtracker.trainingtracker.database.WorkoutDeletionHelper
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSamplesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutClusterEngine
import com.atrainingtracker.trainingtracker.database.RouteSource
import com.atrainingtracker.trainingtracker.database.RouteSummary
import com.atrainingtracker.trainingtracker.database.EquipmentAndSportTypeDiscoveryManager
import com.atrainingtracker.trainingtracker.exporter.db.StravaUploadDbHelper
import com.atrainingtracker.trainingtracker.exporter.ExportManager
import com.atrainingtracker.trainingtracker.exporter.ExportStatusChangedBroadcaster
import com.atrainingtracker.trainingtracker.exporter.ExportType
import com.atrainingtracker.trainingtracker.exporter.FileFormat
import com.atrainingtracker.trainingtracker.repositories.RoutesRepository
import com.atrainingtracker.trainingtracker.tracker.TrackerService
import com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.PeriodsRepository
import com.atrainingtracker.trainingtracker.ui.components.export.ExportStatusDataProvider
import com.atrainingtracker.trainingtracker.ui.components.export.ExportStatusGroupData
import com.atrainingtracker.trainingtracker.ui.map.LocationMarker
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.atrainingtracker.trainingtracker.ui.map.TrackType
import com.atrainingtracker.trainingtracker.ui.map.createSensorMarker
import com.atrainingtracker.trainingtracker.ui.theme.TTColor
import com.atrainingtracker.trainingtracker.ui.util.SingleLiveEvent
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


/**
 * A repository that acts as a single source of truth for workout data.
 * It abstracts the data source (database) from the ViewModels.
 */
class WorkoutRepository private constructor(private val application: Application) : CoroutineScope {

    companion object {
        private val TAG = WorkoutRepository::class.java.simpleName
        private val DEBUG = TrainingApplication.getDebug(true)

        // The single, volatile instance of the repository.
        // @Volatile guarantees that writes to this field are immediately visible to other threads.
        @Volatile
        private var INSTANCE: WorkoutRepository? = null

        /**
         * Gets the singleton instance of the WorkoutRepository.
         *
         * @param application The application context, needed to create the instance for the first time.
         * @return The single instance of WorkoutRepository.
         */
        fun getInstance(application: Application): WorkoutRepository {
            // Double-check locking ensures thread safety and performance.
            return INSTANCE ?: synchronized(this) {
                val instance = INSTANCE
                if (instance != null) {
                    instance
                } else {
                    val newInstance = WorkoutRepository(application)
                    INSTANCE = newInstance
                    newInstance
                }
            }
        }
    }

    private val job = SupervisorJob()
    override val coroutineContext = Dispatchers.Main + job

    // Helper instances, initialized lazily
    private val deletionHelper by lazy { WorkoutDeletionHelper(application) }
    private val summariesManager by lazy { WorkoutSummariesDatabaseManager.getInstance(application) }
    private val samplesManager by lazy { WorkoutSamplesDatabaseManager.getInstance(application) }
    private val equipmentDbHelper by lazy { EquipmentDbHelper(application) }
    private val sportTypeDatabaseManager by lazy { SportTypeDatabaseManager.getInstance(application) }
    private val exportManager by lazy { ExportManager(application) }
    private val stravaUploadDbHelper by lazy { StravaUploadDbHelper(application) }

    private val mapper by lazy {
        WorkoutDataMapper(
            context = application,
            workoutSummariesDatabaseManager = summariesManager,
            sportTypeDatabaseManager = sportTypeDatabaseManager,
            equipmentDbHelper = equipmentDbHelper,
            stravaUploadDbHelper = stravaUploadDbHelper
        )
    }

    // --- StateFlow for Data and LiveData for Progress ---

    // StateFlow for all workouts
    private val _allWorkouts = MutableStateFlow<List<WorkoutData>>(emptyList())
    val allWorkouts: StateFlow<List<WorkoutData>> = _allWorkouts.asStateFlow()

    /**
     * Returns a Flow object that contains only the workout with the specified ID.
     * This is derived from the main 'allWorkouts' StateFlow.
     */
    fun getWorkoutById(id: Long): kotlinx.coroutines.flow.Flow<WorkoutData?> {
        return allWorkouts.map { list ->
            list.find { it.id == id }
        }
    }

    // LiveData for the one-time initial load event
    private val _initialWorkoutLoaded = SingleLiveEvent<WorkoutData>()
    val initialWorkoutLoaded: LiveData<WorkoutData> = _initialWorkoutLoaded

    //  LiveData for granular deletion progress ---
    private val _deletionProgress = MutableLiveData<DeletionProgress>(DeletionProgress.Idle)
    val deletionProgress: LiveData<DeletionProgress> = _deletionProgress



    val saveFinishedEvent = SingleLiveEvent<Pair<Long, Boolean>>()
    val deleteFinishedEvent = SingleLiveEvent<Pair<Long, Boolean>>()


    private val workoutUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val workoutId = intent.getLongExtra(TrackerService.WORKOUT_ID, -1L)
            
            when (intent.action) {
                TrackerService.WORKOUT_UPDATED_INTENT, TrackerService.TRACKING_FINISHED_INTENT -> {
                    if (workoutId != -1L) {
                        if (DEBUG) Log.d(TAG, "Workout update broadcast received for workoutId=$workoutId. Reloading.")
                        launch { reloadWorkoutData(workoutId) }
                    }
                }
            }
        }
    }

    private val exportStatusUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val fileName = intent.getStringExtra(ExportStatusChangedBroadcaster.EXTRA_FILE_BASE_NAME)
            if (fileName != null) {
                if (DEBUG) Log.d(TAG, "Export status changed for $fileName. Reloading statuses in memory.")
                reloadExportStatusesFor(fileName)
            }
        }
    }

    private fun reloadExportStatusesFor(fileName: String) {
        launch(Dispatchers.IO) {
            val exportStatuses: MutableList<ExportStatusGroupData> = mutableListOf()
            for (type in orderedExportTypes) {
                val groupData = exportStatusDataProvider.createGroupData(fileName, type)
                if (groupData.hasContent) {
                    exportStatuses.add(groupData)
                }
            }

            val stravaActivityData = stravaUploadDbHelper.getStravaActivityData(fileName)

            val current = allWorkouts.value.find { it.fileBaseName == fileName } ?: return@launch
            Log.i(TAG, "update from reloadExportStatusesFor")
            updateWorkoutInList(current.id, current.copy(
                exportStatuses = exportStatuses,
                stravaActivityData = stravaActivityData
            ))
        }
    }

    suspend fun getWorkoutTrackPoints(
        workoutId: Long,
        trackType: TrackType
    ): List<PathPoint> = withContext(Dispatchers.IO) {

        val points = mutableListOf<PathPoint>()

        // 1. Get the base file name (same as TrackOnMapHelper)
        val baseFileName = summariesManager.getBaseFileName(workoutId)
            ?: return@withContext emptyList()

        // 2. Access the Samples database
        val db = samplesManager.database
        val tableName = WorkoutSamplesDatabaseManager.getTableName(baseFileName)

        if (!samplesManager.existsTable(baseFileName)) {
            return@withContext emptyList()
        }

        val latName = trackType.latitudeColumn
        val lonName = trackType.longitudeColumn


        db.query(tableName, null, null, null, null, null, null).use { cursor ->

            val latIdx = cursor.getColumnIndex(latName)
            val lonIdx = cursor.getColumnIndex(lonName)
            val altIdx = cursor.getColumnIndex(SensorType.ALTITUDE.name)
            val distIdx = cursor.getColumnIndex(SensorType.DISTANCE_m.name)

            // 3. Replicate the Roughness stepSize logic
            while (cursor.moveToNext()) {

                if (latIdx != -1 && lonIdx != -1 && !cursor.isNull(latIdx) && !cursor.isNull(lonIdx)) {
                    points.add(
                        PathPoint(
                            cursor.getDouble(distIdx),
                            LatLng(cursor.getDouble(latIdx), cursor.getDouble(lonIdx)),
                            cursor.getDouble(altIdx)
                        )
                    )
                }
            }
        }
        points
    }

    private val extremaSensorTypes = arrayOf(
        SensorType.ALTITUDE, SensorType.TEMPERATURE,
        SensorType.HR, SensorType.POWER, SensorType.LINE_DISTANCE_m, SensorType.SPEED_mps
    )

    suspend fun getWorkoutMarkers(workoutData: WorkoutData): List<LocationMarker> = withContext(Dispatchers.IO) {
        val workoutId = workoutData.id
        val markerList = mutableListOf<LocationMarker>()

        // 1. Primary spatial markers (Fast, from WorkoutData)
        workoutData.startLatLng?.let {
            markerList.add(LocationMarker(it, R.drawable.control_start, application.getString(R.string.Start)))
        }
        workoutData.endLatLng?.let {
            markerList.add(LocationMarker(it, R.drawable.control_stop, application.getString(R.string.Stop)))
        }
        workoutData.maxDisplacementLatLng?.let {
            markerList.add(LocationMarker(it, R.drawable.ic_distance, application.getString(R.string.max_line_distance)))
        }

        // 2. Sensor Max/Min Markers (from Extremum table)
        extremaSensorTypes.forEach { sensor ->
            // Skip line distance as it's already added as the Apex above
            if (sensor != SensorType.LINE_DISTANCE_m) {
                addExtremaMarkerIfPresent(workoutId, sensor, ExtremaType.MAX, markerList)
                if (sensor == SensorType.ALTITUDE || sensor == SensorType.TEMPERATURE) {
                    addExtremaMarkerIfPresent(workoutId, sensor, ExtremaType.MIN, markerList)
                }
            }
        }
        markerList
    }

    private fun addExtremaMarkerIfPresent(
        workoutId: Long,
        sensor: SensorType,
        extremaType: ExtremaType,
        markerList: MutableList<LocationMarker>
    ) {
        // Try to get both from the summary record first (efficient, new way)
        var value: Double? = summariesManager.getExtremaValue(workoutId, sensor, extremaType)
        var pos: LatLng? = summariesManager.getExtremaPosition(workoutId, sensor, extremaType)

        // Fallback for legacy data if position is missing in the summary table
        if (pos == null) {
            val legacyExtrema = samplesManager.getExtremaPosition(summariesManager, workoutId, sensor, extremaType)
            if (legacyExtrema != null) {
                pos = legacyExtrema.latLng
                value = legacyExtrema.value
            }
        }

        if (value != null && pos != null) {
            val title = application.getString(
                R.string.location_extrema_format,
                extremaType.toString(), // Use localized name ("Max", "Min")
                sensor.getFullName(application),
                sensor.myFormatter.format(value),
                application.getString(MyHelper.getShortUnitsId(sensor))
            )
            markerList.add(LocationMarker(pos, getExtremaIcon(sensor, extremaType), title))
        }
    }

    private fun getExtremaIcon(sensor: SensorType, type: ExtremaType): Int {
        return when (sensor) {
            SensorType.ALTITUDE -> if (type == ExtremaType.MAX) { R.drawable.ic_altitude_max} else { R.drawable.ic_altitude_min }
            SensorType.TEMPERATURE -> if (type == ExtremaType.MAX) R.drawable.ic_temp_max else R.drawable.ic_temp_min
            SensorType.HR -> R.drawable.ic_heart_rate
            SensorType.POWER -> R.drawable.ic_power
            SensorType.LINE_DISTANCE_m -> R.drawable.ic_distance
            SensorType.SPEED_mps -> R.drawable.ic_speed
            else -> -1
        }
    }



    init {
        val filter = IntentFilter()
        filter.addAction(TrackerService.WORKOUT_UPDATED_INTENT)
        filter.addAction(TrackerService.TRACKING_FINISHED_INTENT)
        LocalBroadcastManager.getInstance(application).registerReceiver(workoutUpdateReceiver, filter)

        val exportFilter = IntentFilter(ExportStatusChangedBroadcaster.EXPORT_STATUS_CHANGED_INTENT)
        ContextCompat.registerReceiver(application, exportStatusUpdateReceiver, exportFilter, ContextCompat.RECEIVER_NOT_EXPORTED)

        if (DEBUG) Log.d(TAG, "WorkoutRepository initialized and receivers registered.")
    }

    // --- Public API for ViewModels ---

    /**
     * Loads a single workout by its ID into the repository's LiveData.
     */
    suspend fun loadWorkout(id: Long) {
        withContext(Dispatchers.IO) {
            summariesManager.getWorkoutCursor(id).use { cursor ->
                if (cursor?.moveToFirst() == true) {
                    val workout = mapper.fromCursor(cursor)
                    _initialWorkoutLoaded.postValue(workout)

                    _allWorkouts.update { currentList ->
                        val newList = currentList.toMutableList()
                        val index = newList.indexOfFirst { it.id == id }

                        if (index != -1) {
                            newList[index] = workout
                        } else {
                            newList.add(workout)
                            newList.sortByDescending { it.headerData.startTimeS }
                        }
                        newList
                    }
                }
            }
        }
    }


    val exportStatusDataProvider = ExportStatusDataProvider(application)
    val orderedExportTypes = listOf(ExportType.FILE, ExportType.DROPBOX, ExportType.COMMUNITY)

    private var isListLoading = false

    suspend fun loadAllWorkouts() {
        if (isListLoading) {
            if (DEBUG) Log.d(TAG, "loadAllWorkouts: already in progress, skipping redundant call.")
            return
        }

        Log.i(TAG, "loadAllWorkouts: starting progressive streaming load.")
        isListLoading = true
        try {
            withContext(Dispatchers.IO) {
                val cursor = summariesManager.getCursorForAllWorkouts()
                val allLoadedWorkouts = mutableListOf<WorkoutData>()
                
                cursor.use { c ->
                    var count = 0
                    if (c.moveToFirst()) {
                        do {
                            val workoutData = mapper.fromCursor(c)

                            val exportStatuses: MutableList<ExportStatusGroupData> = mutableListOf()
                            if (workoutData.fileBaseName != null) {
                                for (type in orderedExportTypes) {
                                    val groupData = exportStatusDataProvider.createGroupData(workoutData.fileBaseName, type)
                                    if (groupData.hasContent) {
                                        exportStatuses.add(groupData)
                                    }
                                }
                            }

                            allLoadedWorkouts.add(workoutData.copy(exportStatuses = exportStatuses))
                            count++

                            // PROGRESSIVE UI PUMP (ATT-346 Style)
                            // Emit the first 10 immediately, then every 50
                            if (count == 10 || (count > 10 && count % 50 == 0)) {
                                val currentBatch = allLoadedWorkouts.toList().sortedByDescending { it.headerData.startTimeS }
                                Log.d(TAG, "Streaming batch to UI: $count items.")
                                _allWorkouts.value = currentBatch
                            }

                        } while (c.moveToNext())
                    }
                }
                
                // Final emission with complete list
                Log.i(TAG, "Load complete. Total workouts: ${allLoadedWorkouts.size}")
                _allWorkouts.value = allLoadedWorkouts.sortedByDescending { it.headerData.startTimeS }
            }
        } finally {
            isListLoading = false
        }
    }

    // --- Public API for Direct Memory Updates (Avoids DB Read) ---

    fun setMapPolyline(workoutId: Long, polyline: String) {
        updateWorkoutInMemory(workoutId) { it.copy(mapPolyline = polyline) }
    }

    fun appendMapPolyline(workoutId: Long, polylineSuffix: String) {
        updateWorkoutInMemory(workoutId) { it.copy(mapPolyline = it.mapPolyline + polylineSuffix) }
    }

    fun setElevationStreams(workoutId: Long, altitudes: String, distances: String) {
        updateWorkoutInMemory(workoutId) { it.copy(encodedAltitudes = altitudes, encodedDistances = distances) }
    }

    fun appendElevationStreams(workoutId: Long, altitudeSuffix: String, distanceSuffix: String) {
        updateWorkoutInMemory(workoutId) {
            it.copy(
                encodedAltitudes = it.encodedAltitudes + altitudeSuffix,
                encodedDistances = it.encodedDistances + distanceSuffix
            )
        }
    }

    fun setCommuteAndTrainer(workoutId: Long, commute: Boolean, trainer: Boolean) {
        updateWorkoutInMemory(workoutId) { it.copy(commute = commute, trainer = trainer) }
    }

    fun setWorkoutName(workoutId: Long, name: String) {
        updateWorkoutInMemory(workoutId) { it.copy(workoutName = name) }
    }

    fun updateExtremaValue(workoutId: Long, sensorType: SensorType, extremaType: ExtremaType, value: Double, position: LatLng? = null) {
        val formattedValue = sensorType.myFormatter.format(value)
        updateWorkoutInMemory(workoutId) { workout ->
            var updated = workout

            // 1. Update specific raw fields
            when (sensorType) {
                SensorType.LINE_DISTANCE_m -> if (extremaType == ExtremaType.MAX) updated = updated.copy(maxDisplacement = value)
                SensorType.ALTITUDE -> {
                    if (extremaType == ExtremaType.MIN) updated = updated.copy(minAltitude = value)
                    if (extremaType == ExtremaType.MAX) updated = updated.copy(maxAltitude = value)
                }
                else -> {}
            }

            // 2. Update the ExtremaDataRow in the list
            val sensorLabel = application.getString(sensorType.shortNameId)
            val updatedRows = updated.extremaRows.map { row ->
                if (row.sensorLabel == sensorLabel) {
                    when (extremaType) {
                        ExtremaType.MIN -> row.copy(minValue = formattedValue, minLatLng = position)
                        ExtremaType.AVG -> row.copy(avgValue = formattedValue)
                        ExtremaType.MAX -> row.copy(maxValue = formattedValue, maxLatLng = position)
                        else -> row
                    }
                } else row
            }
            updated.copy(extremaRows = updatedRows)
        }
    }

    private fun updateWorkoutInMemory(workoutId: Long, block: (WorkoutData) -> WorkoutData) {
        _allWorkouts.update { currentList ->
            val index = currentList.indexOfFirst { it.id == workoutId }
            if (index != -1) {
                // Workout is already in the list, update it in place
                currentList.map { if (it.id == workoutId) block(it) else it }
            } else {
                // Workout is NOT in the list yet (e.g. background worker finished before UI load)
                launch(Dispatchers.IO) {
                    summariesManager.getWorkoutCursor(workoutId).use { cursor ->
                        if (cursor?.moveToFirst() == true) {
                            val freshWorkout = mapper.fromCursor(cursor)
                            val updatedWorkout = block(freshWorkout)

                            _allWorkouts.update { list ->
                                if (list.any { it.id == workoutId }) {
                                    list.map { if (it.id == workoutId) updatedWorkout else it }
                                } else {
                                    (list + updatedWorkout).sortedByDescending { it.headerData.startTimeS }
                                }
                            }
                        }
                    }
                }
                currentList
            }
        }
    }

    private fun addOrUpdateWorkout(workout: WorkoutData) {
        _allWorkouts.update { currentList ->
            val index = currentList.indexOfFirst { it.id == workout.id }
            if (index != -1) {
                // UPDATE & MERGE: Preserve background-calculated fields from memory
                currentList.map { existing ->
                    if (existing.id == workout.id) {
                        val mergedExtremaRows = workout.extremaRows.map { rowFromDb ->
                            val existingRow = existing.extremaRows.find { it.sensorLabel == rowFromDb.sensorLabel }
                            if (existingRow != null) {
                                rowFromDb.copy(
                                    minLatLng = if (rowFromDb.minLatLng == null) existingRow.minLatLng else rowFromDb.minLatLng,
                                    maxLatLng = if (rowFromDb.maxLatLng == null) existingRow.maxLatLng else rowFromDb.maxLatLng
                                )
                            } else {
                                rowFromDb
                            }
                        }

                        workout.copy(
                            mapPolyline = if (workout.mapPolyline.isEmpty()) existing.mapPolyline else workout.mapPolyline,
                            encodedAltitudes = if (workout.encodedAltitudes.isEmpty()) existing.encodedAltitudes else workout.encodedAltitudes,
                            encodedDistances = if (workout.encodedDistances.isEmpty()) existing.encodedDistances else workout.encodedDistances,
                            extremaRows = if (mergedExtremaRows.isEmpty()) existing.extremaRows else mergedExtremaRows,
                            exportStatuses = if (workout.exportStatuses.isEmpty()) existing.exportStatuses else workout.exportStatuses
                        )
                    } else existing
                }
            } else {
                // ADD: Append and re-sort
                (currentList + workout).sortedByDescending { it.headerData.startTimeS }
            }
        }
    }

    private fun updateWorkoutInList(workoutId: Long, updatedWorkout: WorkoutData) {
        Log.i(TAG, "updateWorkoutInList: workoutId=$workoutId (${updatedWorkout.fileBaseName}), mapPolyline: ${updatedWorkout.mapPolyline}, extremaRows: ${updatedWorkout.extremaRows}, exportStatuses: ${updatedWorkout.exportStatuses}")
        _allWorkouts.update { currentList ->
            currentList.map {
                if (it.id == workoutId) updatedWorkout else it
            }
        }
    }

    // Function to update the workout data from the database but keep transient metadata
    private suspend fun reloadWorkoutData(workoutId: Long) {
        if (DEBUG) Log.i(TAG, "reloadWorkoutData: workoutId=$workoutId")

        withContext(Dispatchers.IO) {
            summariesManager.getWorkoutCursor(workoutId).use { cursor ->
                if (cursor?.moveToFirst() == true) {
                    // Get the fresh data from the database.
                    val freshWorkoutData = mapper.fromCursor(cursor)
                    
                    // --- SURGICAL PERIOD UPDATE (ATT-346) ---
                    val existing = allWorkouts.value.find { it.id == workoutId }
                    val isNewFinish = (existing == null || !existing.finished) && freshWorkoutData.finished
                    
                    if (isNewFinish) {
                        PeriodsRepository.getInstance(application).onWorkoutFinished(freshWorkoutData)
                    }

                    addOrUpdateWorkout(freshWorkoutData)
                }
            }
        }
    }


    /**
     * Saves the user-editable state of the WorkoutData object to the databases.
     * This method is surgical: it only updates fields the user can actually edit,
     * ensuring that background-calculated data (like map polylines) is preserved.
     */
    fun saveWorkout(userEditedWorkout: WorkoutData?) {
        if (userEditedWorkout == null) return
        val workoutId = userEditedWorkout.id

        // Launch a coroutine in the IO dispatcher to perform database operations off the main thread.
        launch(Dispatchers.IO) {

            // 1. Update the Database (this method already only touches editable columns)
            summariesManager.updateWorkoutData(userEditedWorkout)

            // 2. Trigger export with the new data
            exportManager.exportWorkout(userEditedWorkout)

            // --- LEARNING LOOP (SCRUM-44) ---
            if (userEditedWorkout.startLatLng != null && userEditedWorkout.endLatLng != null && userEditedWorkout.maxDisplacementLatLng != null) {
                val finalName = userEditedWorkout.workoutName.replace(Regex(" #\\d+$"), "").trim()
                // Only learn names that aren't the default timestamp
                if (finalName.isNotEmpty() && finalName != userEditedWorkout.fileBaseName) {
                    val engine = com.atrainingtracker.trainingtracker.database.WorkoutClusterEngine.getInstance(application)
                    val learnedId = engine.learnFromWorkout(
                        userEditedWorkout.startLatLng,
                        userEditedWorkout.endLatLng,
                        userEditedWorkout.maxDisplacementLatLng,
                        userEditedWorkout.totalDistance,
                        finalName,
                        userEditedWorkout.sportId,
                        userEditedWorkout.clusterId
                    )
                    
                    // persist link and increment count if it's a new or changed association (SCRUM-228)
                    if (userEditedWorkout.clusterId != learnedId) {
                        engine.assignClusterToWorkout(application, workoutId, learnedId)
                        // reload from DB to ensure memory and UI are in sync with inferred identity (SCRUM-254)
                        reloadWorkoutData(workoutId)
                        saveFinishedEvent.postValue(Pair(workoutId, true))
                        return@launch
                    }
                }
            }

            // 3. Update memory surgically - perform the merge ATOMICALLY inside update
            Log.i(TAG, "update from saveWorkout")
            var oldWorkout: WorkoutData? = null
            _allWorkouts.update { currentList ->
                currentList.map { current ->
                    if (current.id == workoutId) {
                        oldWorkout = current
                        current.copy(
                            workoutName = userEditedWorkout.workoutName,
                            sportId = userEditedWorkout.sportId,
                            sportName = userEditedWorkout.sportName,
                            bSportType = userEditedWorkout.bSportType,
                            equipmentId = userEditedWorkout.equipmentId,
                            equipmentName = userEditedWorkout.equipmentName,
                            description = userEditedWorkout.description,
                            goal = userEditedWorkout.goal,
                            method = userEditedWorkout.method,
                            commute = userEditedWorkout.commute,
                            trainer = userEditedWorkout.trainer,
                            uploadToStrava = userEditedWorkout.uploadToStrava
                        )
                    } else current
                }
            }

            // --- SURGICAL PERIOD UPDATE (ATT-346) ---
            oldWorkout?.let { old ->
                val periodsRepo = com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.PeriodsRepository.getInstance(application)
                if (old.sportId != userEditedWorkout.sportId) {
                    periodsRepo.onWorkoutSportChanged(userEditedWorkout, old)
                }
            }

            saveFinishedEvent.postValue(Pair(workoutId, true))
        }
    }

    suspend fun exportWorkoutTo(workoutId: Long, fileFormat: FileFormat) {
        withContext(Dispatchers.IO) {
            exportManager.exportWorkoutTo(workoutId, fileFormat)
        }
    }

    /**
     * Deletes a workout from the current list and posts the update.
     */
    fun deleteWorkout(id: Long) {
        launch(Dispatchers.IO) {
            // Find the workout name *before* deleting it.
            val workout = _allWorkouts.value.find { it.id == id }
            val workoutName = workout?.headerData?.workoutName ?: "Workout ID: $id"

            // --- START PROGRESS ---
            // Post the detailed progress to the LiveData.
            _deletionProgress.postValue(DeletionProgress.InProgress(workoutName, id))

            // Perform the actual deletion in the database first
            val success = deletionHelper.deleteWorkout(id)
            if (success) {
                // --- SURGICAL PERIOD UPDATE (ATT-346) ---
                workout?.let { 
                    com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.PeriodsRepository.getInstance(application).onWorkoutDeleted(it)
                }

                // Now, update the in-memory list
                _allWorkouts.update { currentList ->
                    currentList.filterNot { it.id == id }
                }

                // Post event for UI to react (e.g., close screen)
                deleteFinishedEvent.postValue(Pair(id, true))
            } else {
                deleteFinishedEvent.postValue(Pair(id, true))
            }

            // Reset the state to Idle when done or if an error occurs.
            _deletionProgress.postValue(DeletionProgress.Idle)
        }
    }


    suspend fun deleteOldWorkouts(daysToKeep: Int) {
        withContext(Dispatchers.IO) {
            try {
                // The callback lambda that will be executed inside the helper.
                val progressCallback: (Long) -> Unit = { workoutId ->
                    // Find the workout name from the current list to display it.
                    val workout = allWorkouts.value.find { it.id == workoutId }
                    val workoutName = workout?.headerData?.workoutName ?: "Workout ID: $workoutId"

                    // --- SURGICAL PERIOD UPDATE (ATT-346) ---
                    workout?.let { 
                        com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.PeriodsRepository.getInstance(application).onWorkoutDeleted(it)
                    }

                    // Post the detailed progress to the LiveData.
                    _deletionProgress.postValue(DeletionProgress.InProgress(workoutName, workoutId))
                }

                val success = deletionHelper.deleteOldWorkouts(daysToKeep, progressCallback)

                // After deleting, reload the data so the UI updates automatically.
                if (success) {
                    loadAllWorkouts()
                }
            } finally {
                // Reset the state to Idle when done or if an error occurs.
                _deletionProgress.postValue(DeletionProgress.Idle)
            }
        }
    }

    /**
     * Saves a workout's path as a new Route in the system.
     * @return The ID of the newly created route, or null if it failed.
     */
    suspend fun saveAsRoute(workout: WorkoutData): Long? = withContext(Dispatchers.IO) {
        val points = getWorkoutTrackPoints(workout.id, TrackType.BEST)
        if (points.isEmpty()) return@withContext null

        val routeSummary = RouteSummary(
            id = 0, // Auto-increment
            externalId = workout.fileBaseName ?: "",
            name = workout.workoutName,
            description = workout.description ?: "",
            isSelected = false,
            distance = workout.totalDistance,
            elevationGain = workout.ascentMeters.toDouble(),
            bSportType = workout.bSportType,
            source = RouteSource.WORKOUT
        )

        val routesRepo = RoutesRepository.getInstance(application)
        routesRepo.insertRoute(routeSummary, points)
    }

    /**
     * Assigns a cluster to a workout and automatically propagates sport-specific settings (SCRUM-200).
     */
    fun assignClusterToWorkout(workoutId: Long, clusterId: Long) {
        launch(Dispatchers.IO) {
            WorkoutClusterEngine.getInstance(application)
                .assignClusterToWorkout(application, workoutId, clusterId, forceIdentity = true)

            // Reload fresh data from DB to propagate inferred identity to UI
            reloadWorkoutData(workoutId)
            loadWorkout(workoutId)
        }
    }

}
