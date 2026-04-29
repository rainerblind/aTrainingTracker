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

package com.atrainingtracker.trainingtracker.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object WorkoutNavigationEvents {
    private val _navigateToEdit = MutableSharedFlow<Long>(
        replay = 1, // <--- This makes the event "sticky"
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val navigateToEdit = _navigateToEdit.asSharedFlow()

    @JvmStatic
    val navigateToEditLiveData: LiveData<Long> = _navigateToEdit.asLiveData()

    @JvmStatic
    fun triggerEdit(workoutId: Long) {
        _navigateToEdit.tryEmit(workoutId)
    }

    // Clear the event after it's handled
    fun reset() {
        _navigateToEdit.tryEmit(-1L)
    }
}