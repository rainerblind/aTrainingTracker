package com.atrainingtracker.trainingtracker.ui.components.workoutextrema


/**
 * A simple, reusable data class to hold the formatted extrema values for a single sensor.
 * This object is "dumb" and only holds data, making it easy to pass around.
 */
data class ExtremaData(
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