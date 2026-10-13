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

package com.atrainingtracker.trainingtracker.ui.ant

/**
 * State hierarchy representing active ANT+ alert dialogs in the application UI (REQ-UI-323).
 */
sealed class AntDialogState {
    /**
     * Dialog indicating that no ANT+ hardware adapter is present on the device.
     */
    data object MissingAdapter : AntDialogState()

    /**
     * Dialog indicating that a required ANT+ system dependency (e.g. ANT Radio Service) is missing.
     *
     * @property dependencyName User-friendly name of the missing package.
     * @property packageName Android application package name for Google Play redirection.
     */
    data class MissingDependency(
        val dependencyName: String,
        val packageName: String
    ) : AntDialogState()
}
