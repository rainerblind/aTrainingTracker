package com.atrainingtracker.trainingtracker.ui.components.workoutdetails

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.sensor.formater.DistanceFormatter
import com.atrainingtracker.banalservice.sensor.formater.PaceFormatter
import com.atrainingtracker.banalservice.sensor.formater.SpeedFormatter
import com.atrainingtracker.banalservice.sensor.formater.TimeFormatter
import com.atrainingtracker.trainingtracker.MyHelper

/**
 * A "dumb" ViewHolder responsible only for displaying pre-processed WorkoutDetailsData.
 * It has no knowledge of the database or data source.
 * Written in idiomatic Kotlin.
 */
class WorkoutDetailsViewHolder(
    val view: View,
    private val context: Context,
) {

    // Formatters
    private val distanceFormatter = DistanceFormatter()
    private val timeFormatter = TimeFormatter()
    private val speedFormatter = SpeedFormatter()
    private val paceFormatter = PaceFormatter()

    // Views
    private val tvDistance: TextView = view.findViewById(R.id.detail_distance)
    private val tvMaxDisplacement: TextView = view.findViewById(R.id.detail_max_displacement)
    private val tvTimeActive: TextView = view.findViewById(R.id.detail_time_active)
    private val tvTimeTotal: TextView = view.findViewById(R.id.detail_time_total)
    private val rowSpeed: LinearLayout = view.findViewById(R.id.workout_details_rowSpeed)
    private val tvAvgSpeed: TextView = view.findViewById(R.id.detail_avg_speed)
    private val rowAltitude: LinearLayout = view.findViewById(R.id.workout_details_rowAltitude)
    private val tvAscent: TextView = view.findViewById(R.id.detail_ascent)
    private val tvDescent: TextView = view.findViewById(R.id.detail_descent)
    private val tvMinAltitude: TextView = view.findViewById(R.id.detail_min_altitude)
    private val tvMaxAltitude: TextView = view.findViewById(R.id.detail_max_altitude)

    /**
     * Main binding method. Accepts a data object, completely decoupling it from the data source.
     */
    fun bind(data: WorkoutDetailsData) {
        // The ViewHolder becomes a simple coordinator for its private helper methods.
        bindDistanceAndTime(data)
        bindSpeedAndPace(data)
        bindAltitude(data)
        view.isVisible = true
    }

    /**
     * Binds data using the pre-filled data object.
     */
    private fun bindDistanceAndTime(data: WorkoutDetailsData) {
        val distanceUnit = context.getString(MyHelper.getDistanceUnitNameId())

        tvDistance.text = context.getString(
            R.string.value_unit_string_string,
            distanceFormatter.format(data.totalDistance),
            distanceUnit
        )

        data.maxDisplacement?.let {
            val maxDisplacementFormatted = distanceFormatter.format(it)
            val maxDisplacementString = context.getString(
                R.string.value_unit_string_string,
                maxDisplacementFormatted,
                distanceUnit
            )
            tvMaxDisplacement.text = context.getString(R.string.format_max_displacement, maxDisplacementString)
            tvMaxDisplacement.isVisible = true
        } ?: run {
            tvMaxDisplacement.isVisible = false
        }

        tvTimeActive.text = timeFormatter.format(data.activeTimeSec)
        tvTimeTotal.text = context.getString(R.string.total_time_format, timeFormatter.format(data.totalTimeSec))
    }

    /**
     * Binds speed/pace data using the pre-filled data object.
     */
    private fun bindSpeedAndPace(data: WorkoutDetailsData) {
        val formattedValue: String
        val unit: String

        if (data.sportType == BSportType.RUN) {
            val paceSpm = if (data.avgSpeedMps > 0) 1 / data.avgSpeedMps else 0f
            formattedValue = paceFormatter.format(paceSpm.toDouble())
            unit = context.getString(MyHelper.getPaceUnitNameId())
        } else {
            formattedValue = speedFormatter.format(data.avgSpeedMps.toDouble())
            unit = context.getString(MyHelper.getSpeedUnitNameId())
        }

        tvAvgSpeed.text = context.getString(R.string.value_unit_string_string, formattedValue, unit)
        rowSpeed.isVisible = true
    }

    /**
     * Binds altitude data using the pre-filled data object.
     */
    private fun bindAltitude(data: WorkoutDetailsData) {
        var hasAltitudeData = false

        // Using takeIf makes this more concise
        data.ascentMeters.takeIf { it > 0 }?.let {
            tvAscent.setTextViewWithIntValue(it, "m")
            hasAltitudeData = true
        } ?: run { tvAscent.isVisible = false }

        data.descentMeters.takeIf { it > 0 }?.let {
            tvDescent.setTextViewWithIntValue(it, "m")
            hasAltitudeData = true
        } ?: run { tvDescent.isVisible = false }


        if (tvMinAltitude.setTextViewWithDoubleValue(data.minAltitude, "m")) {
            hasAltitudeData = true
        }
        if (tvMaxAltitude.setTextViewWithDoubleValue(data.maxAltitude, "m")) {
            hasAltitudeData = true
        }

        rowAltitude.isVisible = hasAltitudeData
    }

    /**

     * Helper to format an integer value with a unit and set it on a TextView.
     * Defined as an extension function for conciseness.
     */
    private fun TextView.setTextViewWithIntValue(value: Int, unit: String) {
        text = "$value $unit" // String template
        isVisible = true
    }

    /**
     * Helper to format a Double value with a unit and set it on a TextView.
     * Handles null values by hiding the view.
     * @return True if the view was made visible, false otherwise.
     */
    private fun TextView.setTextViewWithDoubleValue(value: Double?, unit: String): Boolean {
        return value?.let {
            text = "%.0f %s".format(it, unit) // String.format
            isVisible = true
            true
        } ?: run {
            isVisible = false
            false
        }
    }
}