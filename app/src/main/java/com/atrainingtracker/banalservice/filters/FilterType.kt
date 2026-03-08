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


    /**
     * Generates a short, human-readable summary for the filter type and its constant.
     * This logic was previously in TrackingFragmentClassic.
     *
     * @param context The context needed to resolve string resources.
     * @param filterConstant The numeric constant associated with the filter (e.g., time, samples, alpha).
     * @return A short string describing the filter, e.g., "5 min avg."
     */
    fun getShortSummary(context: Context, filterConstant: Double): String {
        return when (this) {
            INSTANTANEOUS -> context.getString(R.string.filter_instantaneous_short)

            AVERAGE -> context.getString(R.string.filter_average_short)

            MOVING_AVERAGE_TIME -> {
                if (filterConstant % 60 == 0.0) { // 5 min moving average
                    "${filterConstant.toInt() / 60} ${context.getString(R.string.units_minutes)} ${context.getString(R.string.filter_moving_average_short)}"
                } else { // 5 sec moving average
                    "${filterConstant.toInt()} ${context.getString(R.string.units_seconds)} ${context.getString(R.string.filter_moving_average_short)}"
                }
            }

            MOVING_AVERAGE_NUMBER -> // 5 samples moving average
                "${filterConstant.toInt()} ${context.getString(R.string.units_samples)} ${context.getString(R.string.filter_moving_average_short)}"

            EXPONENTIAL_SMOOTHING -> // exponential smoothing with α = 0.9
                context.getString(R.string.filter_exponential_smoothing_short_format, filterConstant)

            MAX_VALUE -> context.getString(R.string.max)
        }
    }

    fun getDisplayName(context: Context): String {
        val id = when (this) {
            INSTANTANEOUS -> R.string.filter_instantaneous
            AVERAGE -> R.string.filter_average
            MOVING_AVERAGE_TIME, FilterType.MOVING_AVERAGE_NUMBER -> R.string.filter_moving_average
            EXPONENTIAL_SMOOTHING -> R.string.filter_exponential_smoothing
            MAX_VALUE -> R.string.filter_max
        }
        return context.getString(id)
    }

    fun getDetails(context: Context, constant: Double): String {
        return when (this) {
            INSTANTANEOUS -> context.getString(R.string.filter_details__instantaneous)
            AVERAGE -> context.getString(R.string.filter_details__average)
            MAX_VALUE -> context.getString(R.string.filter_details__max)
            EXPONENTIAL_SMOOTHING -> context.getString(R.string.filter_details__exponential_smoothing)
            MOVING_AVERAGE_NUMBER -> context.getString(R.string.filter_details__moving_average_number, constant.toInt())
            MOVING_AVERAGE_TIME -> {
                if (constant < 60) {
                    context.getString(R.string.filter_details__moving_average_time, constant.toInt(), context.getString(R.string.units_seconds_long))
                } else {
                    context.getString(R.string.filter_details__moving_average_time, (constant / 60).toInt(), context.getString(R.string.units_minutes_long))
                }
            }
        }
    }


}