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

package com.atrainingtracker.trainingtracker.ui.aftermath.crop

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.domain.crop.CropWorkoutMath
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel managing the state and business logic for cropping workout start and end points.
 */
class CropWorkoutViewModel(
    application: Application,
    val workoutId: Long,
    private val repository: WorkoutRepository = WorkoutRepository.getInstance(application)
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(CropWorkoutUiState())
    val uiState: StateFlow<CropWorkoutUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessageResId = null) }
            val workout = repository.allWorkouts.value.find { it.id == workoutId }
            val points = repository.getCropSamplePoints(workoutId)

            if (points.isEmpty()) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessageResId = R.string.crop_export_failed_retry,
                        points = emptyList(),
                        totalPoints = 0
                    )
                }
                return@launch
            }

            val total = points.size
            val startIdx = 0
            val endIdx = total - 1
            val origDist = (points.last().distanceTotalM - points.first().distanceTotalM).coerceAtLeast(0.0)
            val origDur = (points.last().timeTotalS - points.first().timeTotalS).coerceAtLeast(0L)
            val durSec = origDur.toDouble()
            val valid = CropWorkoutMath.isCropValid(startIdx, endIdx, total, durSec)
            val avgSpeed = CropWorkoutMath.calculateAverageSpeed(
                origDist,
                (points.last().timeActiveS - points.first().timeActiveS).coerceAtLeast(0L).toInt()
            )

            _uiState.update {
                it.copy(
                    isLoading = false,
                    workoutName = workout?.workoutName ?: "",
                    totalPoints = total,
                    startIndex = startIdx,
                    endIndex = endIdx,
                    originalDistanceM = origDist,
                    originalDurationS = origDur,
                    croppedDistanceM = origDist,
                    croppedDurationS = origDur,
                    croppedAvgSpeedMps = avgSpeed,
                    points = points,
                    isValid = valid
                )
            }
        }
    }

    fun updateRange(start: Int, end: Int) {
        val current = _uiState.value
        val points = current.points
        if (points.isEmpty()) return

        val clampedStart = start.coerceIn(0, points.size - 1)
        val clampedEnd = end.coerceIn(clampedStart, points.size - 1)
        val startPt = points[clampedStart]
        val endPt = points[clampedEnd]

        val croppedDist = (endPt.distanceTotalM - startPt.distanceTotalM).coerceAtLeast(0.0)
        val croppedDur = (endPt.timeTotalS - startPt.timeTotalS).coerceAtLeast(0L)
        val croppedActiveTime = (endPt.timeActiveS - startPt.timeActiveS).coerceAtLeast(0L).toInt()
        val valid = CropWorkoutMath.isCropValid(clampedStart, clampedEnd, current.totalPoints, croppedDur.toDouble())
        val avgSpeed = CropWorkoutMath.calculateAverageSpeed(croppedDist, croppedActiveTime)

        _uiState.update {
            it.copy(
                startIndex = clampedStart,
                endIndex = clampedEnd,
                croppedDistanceM = croppedDist,
                croppedDurationS = croppedDur,
                croppedAvgSpeedMps = avgSpeed,
                isValid = valid
            )
        }
    }

    fun executeCrop(onSuccess: () -> Unit, onError: (Int) -> Unit) {
        val current = _uiState.value
        if (!current.isValid || current.isCropping) return

        viewModelScope.launch {
            _uiState.update { it.copy(isCropping = true) }
            val result = repository.cropWorkout(workoutId, current.startIndex, current.endIndex)
            _uiState.update { it.copy(isCropping = false) }

            result.fold(
                onSuccess = {
                    onSuccess()
                },
                onFailure = { error ->
                    val errorRes = when (error) {
                        is CropStorageException -> error.stringResId
                        else -> R.string.crop_export_failed_retry
                    }
                    _uiState.update { it.copy(errorMessageResId = errorRes) }
                    onError(errorRes)
                }
            )
        }
    }

    class Factory(
        private val application: Application,
        private val workoutId: Long,
        private val repository: WorkoutRepository = WorkoutRepository.getInstance(application)
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return CropWorkoutViewModel(application, workoutId, repository) as T
        }
    }
}
