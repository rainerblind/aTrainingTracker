package com.atrainingtracker.banalservice.ui.devices


import androidx.annotation.StringRes
import com.atrainingtracker.R // Make sure to import your project's R file

/**
 * A sealed interface that defines the specific UI configuration for the
 * EditDeviceDialogFragment based on the type of device being edited.
 *
 * This acts as a "UI blueprint" to keep display logic out of the fragment.
 */
sealed interface DeviceDialogConfig {

    /**
     * Determines if the calibration-related section should be visible.
     */
    val showsCalibrationFactor: Boolean

    /**
     * Provides the correct string resource for the calibration field's title.
     * For example, it returns "Wheel Circumference" for a bike speed sensor,
     * or "Calibration Factor" for a run pod.
     */
    @get:StringRes
    val calibrationFactorTitleRes: Int

    /**
     * Determines if the spinner with preset wheel sizes should be visible.
     * This is typically true only for bike-related speed sensors.
     */
    val showsWheelCircumferenceSpinner: Boolean

    // --- Define the specific configurations for each device category ---

    /**
     * Configuration for a BIKE_POWER meter.
     * Shows power-specific features.
     */
    data object BikePower : DeviceDialogConfig {
        override val showsCalibrationFactor: Boolean = true
        override val calibrationFactorTitleRes: Int = R.string.wheelCircumferenceText
        override val showsWheelCircumferenceSpinner: Boolean = true
    }

    /**
     * Configuration for a BIKE_SPEED sensor.
     */
    data object BikeSpeed : DeviceDialogConfig {
        override val showsCalibrationFactor: Boolean = true
        override val calibrationFactorTitleRes: Int = R.string.wheelCircumferenceText
        override val showsWheelCircumferenceSpinner: Boolean = true
    }

    /**
     * Configuration for a BIKE_SPEED_AND_CADENCE sensor.
     */
    data object BikeSpeedAndCadence : DeviceDialogConfig {
        override val showsCalibrationFactor: Boolean = true
        override val calibrationFactorTitleRes: Int = R.string.wheelCircumferenceText
        override val showsWheelCircumferenceSpinner: Boolean = true
    }

    /**
     * Configuration for a BIKE_CADENCE sensor.
     * It does not have its own calibration factor.
     */
    data object BikeCadence : DeviceDialogConfig {
        override val showsCalibrationFactor: Boolean = false
        override val calibrationFactorTitleRes: Int = R.string.invalid_date
        override val showsWheelCircumferenceSpinner: Boolean = false
    }

    /**
     * Configuration for a RUN_SPEED sensor (like a foot pod).
     * It has a calibration factor, but no wheel presets.
     */
    data object RunSpeed : DeviceDialogConfig {
        override val showsCalibrationFactor: Boolean = true
        override val calibrationFactorTitleRes: Int = R.string.calibrationFactorText
        override val showsWheelCircumferenceSpinner: Boolean = false
    }

    /**
     * Default/fallback configuration for all other devices (like HEART_RATE)
     * that have no special UI requirements.
     */
    data object None : DeviceDialogConfig {
        override val showsCalibrationFactor: Boolean = false
        override val calibrationFactorTitleRes: Int = R.string.invalid_date
        override val showsWheelCircumferenceSpinner: Boolean = false
    }
}