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

package com.atrainingtracker.banalservice.sensor.formater

import java.util.Locale
import java.util.concurrent.TimeUnit

class TimeFormatter : MyFormatter<Number> {
    override fun format(value: Number?): String {
        if (value == null) {
            return "--:--:--"
        }
        val s = value.toInt()
        val hours = s / SECONDS_PER_HOUR
        val minutes = (s % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
        val seconds = s % SECONDS_PER_MINUTE
        return String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    }

    @Suppress("FunctionNaming", "ReturnCount")
    fun format_with_units(value: Number?): String {
        if (value == null) {
            return "--:--:--"
        }
        var seconds = value.toLong()
        return if (seconds < SECONDS_PER_MINUTE) {
            String.format(Locale.getDefault(), "%d sec", seconds)
        } else if (seconds < SECONDS_PER_HOUR) {
            val minutes = TimeUnit.SECONDS.toMinutes(seconds)
            seconds -= TimeUnit.MINUTES.toSeconds(minutes)
            String.format(Locale.getDefault(), "%d:%02d min", minutes, seconds)
        } else if (seconds < SECONDS_PER_100_HOURS) {
            val hours = TimeUnit.SECONDS.toHours(seconds)
            val minutes = TimeUnit.SECONDS.toMinutes(seconds) - TimeUnit.HOURS.toMinutes(hours)
            seconds = seconds - TimeUnit.HOURS.toSeconds(hours) - TimeUnit.MINUTES.toSeconds(minutes)
            String.format(Locale.getDefault(), "%d:%02d:%02d h", hours, minutes, seconds)
        } else {
            val hours = TimeUnit.SECONDS.toHours(seconds)
            String.format(Locale.getDefault(), "%d h", hours)
        }
    }

    companion object {
        private const val SECONDS_PER_MINUTE = 60
        private const val SECONDS_PER_HOUR = 3600
        private const val SECONDS_PER_100_HOURS = 100 * 3600
    }
}
