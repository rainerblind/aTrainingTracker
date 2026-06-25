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

package com.atrainingtracker.trainingtracker.ui.util

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Interface for sort orders used in mappable lists.
 */
interface MappableSortOrder {
    @get:StringRes val labelResId: Int
}

/**
 * Base ViewModel for lists that support sorting and filtering.
 * Unifies common logic like sort order management and scroll-to-top tracking.
 */
abstract class BaseMappableListViewModel<T, S>(
    application: Application,
    initialSortOrder: S,
    private val distanceSortOrder: S? = null
) : AndroidViewModel(application) where S : Enum<S>, S : MappableSortOrder {

    protected val _sortOrder = MutableStateFlow(initialSortOrder)
    val sortOrder: StateFlow<S> = _sortOrder.asStateFlow()

    private var lastScrolledOrder: S? = null
    private var lastLocationWasAvailable: Boolean = false

    /**
     * Subclasses should override this if they support location-based sorting.
     */
    open val isLocationAvailable: StateFlow<Boolean> = MutableStateFlow(false)

    fun setSortOrder(order: S) {
        _sortOrder.value = order
    }

    /**
     * Logic to determine if the list should scroll to top.
     * Triggers when the sort order changes or when location becomes available in distance mode.
     */
    fun shouldScrollToTop(currentOrder: S): Boolean {
        val available = isLocationAvailable.value
        val orderChanged = lastScrolledOrder != currentOrder
        val locBecameAvailable = distanceSortOrder != null && currentOrder == distanceSortOrder &&
                !lastLocationWasAvailable && available

        if (orderChanged || locBecameAvailable) {
            lastScrolledOrder = currentOrder
            lastLocationWasAvailable = available
            return true
        }
        lastLocationWasAvailable = available
        return false
    }

    /**
     * Utility to calculate distance between two points.
     */
    protected fun calculateDistance(uLat: Double, uLon: Double, sLat: Double, sLon: Double): Float {
        val results = FloatArray(1)
        android.location.Location.distanceBetween(uLat, uLon, sLat, sLon, results)
        return results[0]
    }
}
