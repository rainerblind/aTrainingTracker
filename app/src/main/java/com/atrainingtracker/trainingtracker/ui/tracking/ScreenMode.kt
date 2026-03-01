package com.atrainingtracker.trainingtracker.ui.tracking


/**
 * Defines the operational mode of the tracking screen, determining its appearance and behavior.
 */
enum class ScreenMode {
    /** The screen is used for actively tracking a workout. Long-clicks are handled. */
    TRACKING,
    /** The screen is used for configuring the layout. Normal clicks are handled for editing. */
    CONFIGURATION
}