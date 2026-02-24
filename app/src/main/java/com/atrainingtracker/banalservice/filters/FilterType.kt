package com.atrainingtracker.banalservice.filters

import android.content.Context
import com.atrainingtracker.R

enum class FilterType {
    INSTANTANEOUS,
    AVERAGE,
    MOVING_AVERAGE_TIME,
    MOVING_AVERAGE_NUMBER,
    EXPONENTIAL_SMOOTHING,
    MAX_VALUE;

    /**
     * Generates a human-readable summary of the filter and its configuration.
     * This method was moved from ConfigureFilterDialogFragment for better reusability.
     *
     * @param context The application context, needed to resolve string resources.
     * @param filterConstant The numeric constant associated with the filter (e.g., time, samples, alpha).
     * @return A formatted string describing the filter, e.g., "5 sec moving average".
     */
    fun getSummary(context: Context, filterConstant: Double): String {
        return when (this) {
            INSTANTANEOUS -> context.getString(R.string.filter_instantaneous)
            AVERAGE -> context.getString(R.string.filter_average)
            MAX_VALUE -> context.getString(R.string.max)

            MOVING_AVERAGE_TIME -> {
                val unit = if (filterConstant % 60 == 0.0) {
                    context.getString(R.string.units_minutes)
                } else {
                    context.getString(R.string.units_seconds)
                }
                val value = if (filterConstant % 60 == 0.0) {
                    (filterConstant / 60).toInt()
                } else {
                    filterConstant.toInt()
                }
                "$value $unit ${context.getString(R.string.filter_moving_average)}"
            }

            MOVING_AVERAGE_NUMBER ->
                "${filterConstant.toInt()} ${context.getString(R.string.units_samples)} ${context.getString(R.string.filter_moving_average)}"

            EXPONENTIAL_SMOOTHING ->
                context.getString(R.string.filter_exponential_smoothing_format, filterConstant)
        }
    }
}