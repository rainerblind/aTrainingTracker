package com.atrainingtracker.trainingtracker.ui.tracking.tracking

import com.atrainingtracker.trainingtracker.ui.tracking.SensorFieldState

/**
 * A sealed interface to represent items in our configuration grid.
 * It can be either a real sensor field or a placeholder for an "Add" button.
 */
sealed interface GridItem {
    data class Field(val state: SensorFieldState) : GridItem
    data class Adder(
        val row: Int,
        val col: Int,
        val isRowAdder: Boolean // True if it's for adding a row, false for a column
    ) : GridItem
}