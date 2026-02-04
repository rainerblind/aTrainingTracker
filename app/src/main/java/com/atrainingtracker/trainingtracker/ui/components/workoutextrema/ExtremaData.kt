package com.atrainingtracker.trainingtracker.ui.components.workoutextrema

/**
 * Represents the complete data model for the extrema values section.
 *
 * @param isCalculating True if the background worker is still processing values.
 * @param dataRows The list of individual sensor data rows to display.
 */
data class ExtremaData(
    val workoutId: Long,
    val isCalculating: Boolean,
    val dataRows: List<ExtremaDataRow>
)

/**
 * Represents a single row of extrema data for one sensor.
 *
 * @param sensorLabel The display name of the sensor (e.g., "HR").
 * @param unitLabel The unit of measurement (e.g., "bpm").
 * @param minValue The formatted minimum value, or null if not available.
 * @param avgValue The formatted average value, or null if not available.
 * @param maxValue The formatted maximum value, or null if not available.
 */
data class ExtremaDataRow(
    val sensorLabel: String,
    val unitLabel: String,
    val minValue: String?,
    val avgValue: String?,
    val maxValue: String?
) {
    /**
     * Helper to check if any value is present for this sensor.
     * @return true if at least one of min, avg, or max is not null.
     */
    fun hasAnyData(): Boolean {
        return minValue != null || avgValue != null || maxValue != null
    }
}