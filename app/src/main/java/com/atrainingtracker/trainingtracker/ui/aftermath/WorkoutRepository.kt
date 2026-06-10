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
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.database.EquipmentDbHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext



import com.atrainingtracker.trainingtracker.database.WorkoutDeletionHelper
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSamplesDatabaseManager
import com.atrainingtracker.trainingtracker.exporter.ExportManager
import com.atrainingtracker.trainingtracker.exporter.ExportStatusChangedBroadcaster
import com.atrainingtracker.trainingtracker.exporter.ExportType
import com.atrainingtracker.trainingtracker.exporter.FileFormat
import com.atrainingtracker.trainingtracker.helpers.CalcExtremaWorker
import com.atrainingtracker.trainingtracker.tracker.TrackerService
import com.atrainingtracker.trainingtracker.ui.components.export.ExportStatusDataProvider
import com.atrainingtracker.trainingtracker.ui.components.export.ExportStatusGroupData
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.atrainingtracker.trainingtracker.ui.map.TrackType
import com.atrainingtracker.trainingtracker.ui.util.SingleLiveEvent
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch


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

    private val mapper by lazy {
        WorkoutDataMapper(
            context = application,
            workoutSummariesDatabaseManager = summariesManager,
            sportTypeDatabaseManager = sportTypeDatabaseManager,
            equipmentDbHelper = equipmentDbHelper
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
                        reloadWorkoutData(workoutId)
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

            val current = allWorkouts.value.find { it.fileBaseName == fileName } ?: return@launch
            Log.i(TAG, "update from reloadExportStatusesFor")
            updateWorkoutInList(current.id, current.copy(exportStatuses = exportStatuses))
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


    /*
    Observer stuff for the extrema calculation
     */

    // Keep track of the observers we create so we can remove them later if needed.
    private val activeObservers = mutableMapOf<Long, Observer<List<WorkInfo>>>()

    private fun observeExtremaCalculation(workoutId: Long) {
        if (DEBUG) Log.i(TAG, "observeExtremaCalculation: workoutId=$workoutId")

        // Create a new, dedicated observer for this specific workoutId.
        val newObserver = object : Observer<List<WorkInfo>> {
            private var lastProgressSequence = -1
            override fun onChanged(workInfos: List<WorkInfo>) {
                // This observer now uses its own 'workoutId', which is captured
                // from the function's scope and will never change.
                Log.d(TAG, "Observer called for its specific workoutId=$workoutId")

                val workInfo = workInfos.firstOrNull() ?: return

                if (workInfo.state.isFinished) {
                    Log.d(TAG, "Finished calculation for workout $workoutId")
                    // --- When finished, clear the calculation message, reload the data, and remove the observer.

                    // Find the current workout in the list.
                    val workout = _allWorkouts.value.find { it.id == workoutId }
                    // If it has a calculation message, clear it.
                    if (workout != null && workout.extremaCalculationMessage != null) {
                        updateWorkoutInList(workoutId, workout.copy(extremaCalculationMessage = null))
                    }
                    // TODO: Currently, we update the workout list twice.  This should be avoided.

                    reloadWorkoutData(workoutId)

                    // Once finished, we can clean up this specific observer.
                    WorkManager.getInstance(application).getWorkInfosByTagLiveData("extrema_calc_$workoutId").removeObserver(this)
                    activeObservers.remove(workoutId)
                } else {
                    val currentProgress =
                        workInfo.progress.getInt(CalcExtremaWorker.KEY_PROGRESS_SEQUENCE, -1)
                    if (currentProgress > lastProgressSequence) {
                        lastProgressSequence = currentProgress
                        val message = workInfo.progress.getString(CalcExtremaWorker.KEY_STARTING_MESSAGE)
                        if (message != null) {
                            // update the message in the list
                            val workoutToUpdate = _allWorkouts.value.find { it.id == workoutId }
                            if (workoutToUpdate != null) {
                                // Create a new WorkoutData with the updated message.
                                val updatedWorkout = workoutToUpdate.copy(extremaCalculationMessage = message)
                                // Post the update. DiffUtil will see that extremaData has changed.
                                updateWorkoutInList(workoutId, updatedWorkout)
                            }

                        } else {
                            val updateType = workInfo.progress.getString(CalcExtremaWorker.KEY_FINISHED_MESSAGE)
                            if (updateType != null) {
                                reloadWorkoutData(workoutId)
                            }
                        }
                    }
                }
            }
        }

        // Attach the new, dedicated observer.
        launch(Dispatchers.Main) {
            val workTag = "extrema_calc_${workoutId}"
            val workManager = WorkManager.getInstance(application)

            // Remove any old observer for this ID before adding a new one.
            activeObservers[workoutId]?.let { oldObserver ->
                workManager.getWorkInfosByTagLiveData(workTag).removeObserver(oldObserver)
            }

            // Store and observe with the new one.
            activeObservers[workoutId] = newObserver
            workManager.getWorkInfosByTagLiveData(workTag).observeForever(newObserver)
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

                    if (workout.extremaData.isCalculating) {
                        observeExtremaCalculation(id)
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

        Log.i(TAG, "loadAllWorkouts: starting full database scan.")
        isListLoading = true
        try {
            withContext(Dispatchers.IO) {
                val cursor = summariesManager.getCursorForAllWorkouts()
                cursor.use { c ->
                    if (c.moveToFirst()) {
                        do {
                            val workoutData = mapper.fromCursor(c)

                            if (workoutData.extremaData.isCalculating) {
                                observeExtremaCalculation(workoutData.id)
                            }

                            val exportStatuses: MutableList<ExportStatusGroupData> = mutableListOf()
                            if (workoutData.fileBaseName != null) {
                                for (type in orderedExportTypes) {
                                    val groupData = exportStatusDataProvider.createGroupData(workoutData.fileBaseName, type)
                                    if (groupData.hasContent) {
                                        exportStatuses.add(groupData)
                                    }
                                }
                            }

                            addOrUpdateWorkout(workoutData.copy(exportStatuses = exportStatuses))
                        } while (c.moveToNext())
                    }
                }
            }
        } finally {
            isListLoading = false
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
                            isCalculatingExtrema = workout.isCalculatingExtrema && existing.isCalculatingExtrema,
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
    private fun reloadWorkoutData(workoutId: Long) {
        if (DEBUG) Log.i(TAG, "reloadWorkoutData: workoutId=$workoutId")

        launch(Dispatchers.IO) {
            summariesManager.getWorkoutCursor(workoutId).use { cursor ->
                if (cursor?.moveToFirst() == true) {
                    // Get the fresh data from the database.
                    val freshWorkoutData = mapper.fromCursor(cursor)
                    
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

            // 3. Update memory surgically - perform the merge ATOMICALLY inside update
            Log.i(TAG, "update from saveWorkout")
            _allWorkouts.update { currentList ->
                currentList.map { current ->
                    if (current.id == workoutId) {
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


}