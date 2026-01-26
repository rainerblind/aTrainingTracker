package com.atrainingtracker.trainingtracker.ui.components.extrema


import android.content.Context
import android.util.Log
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.MyHelper
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.database.ExtremaType
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager

/**
 * A reusable data provider responsible for fetching and preparing extrema data for any workout.
 * This class contains all business logic for displaying sensor data.
 */
class ExtremaDataProvider(context: Context) {

    // Companion object for static-like properties
    companion object {
        private val TAG = ExtremaDataProvider::class.java.simpleName
        private val DEBUG = TrainingApplication.getDebug(true)
    }

    // Use application context to avoid leaks
    private val appContext: Context = context.applicationContext

    /**
     * Fetches and prepares a list of ExtremaData for a given workout.
     */
    fun getExtremaDataList(workoutId: Long, bSportType: BSportType): List<ExtremaData> {
        if (DEBUG) Log.d(TAG, "getExtremaDataList() for workoutId: $workoutId")

        // Define all sensors to check
        val sensorsToCheck = arrayOf(
            SensorType.HR,
            SensorType.SPEED_mps,
            SensorType.PACE_spm,
            SensorType.CADENCE,
            SensorType.POWER,
            SensorType.TORQUE,
            SensorType.PEDAL_POWER_BALANCE,
            SensorType.PEDAL_SMOOTHNESS_L,
            SensorType.PEDAL_SMOOTHNESS,
            SensorType.PEDAL_SMOOTHNESS_R,
            SensorType.ALTITUDE,
            SensorType.TEMPERATURE
        )

        // Use Kotlin's functional style to build the list
        return sensorsToCheck.mapNotNull { sensorType ->
            // Business logic: do not show speed for running activities
            if (bSportType == BSportType.RUN && sensorType == SensorType.SPEED_mps) {
                return@mapNotNull null // Skip this sensor
            }
            // Business logic: show pace only for running activities
            if (bSportType != BSportType.RUN && sensorType == SensorType.PACE_spm) {
                return@mapNotNull null // Skip this sensor
            }

            val min = getFormattedValue(workoutId, sensorType, ExtremaType.MIN)
            val avg = getFormattedValue(workoutId, sensorType, ExtremaType.AVG)
            val max = getFormattedValue(workoutId, sensorType, ExtremaType.MAX)

            val data = ExtremaData(
                sensorLabel = appContext.getString(sensorType.shortNameId),
                unitLabel = appContext.getString(MyHelper.getUnitsId(sensorType)),
                minValue = min,
                avgValue = avg,
                maxValue = max
            )

            // Only return the data object if it's not empty, otherwise return null
            if (data.hasAnyData()) data else null
        }
    }

    private fun getFormattedValue(workoutId: Long, sensorType: SensorType, extremaType: ExtremaType): String? {
        val value = WorkoutSummariesDatabaseManager.getExtremaValue(workoutId, sensorType, extremaType)
        if (DEBUG) Log.d(TAG, "${sensorType.name} ${extremaType.name} extremaValue=$value")
        // Use Kotlin's scope function 'let' for safe handling of nullable values
        return value?.let { sensorType.myFormatter.format(it) }
    }
}