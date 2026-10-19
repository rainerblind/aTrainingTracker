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

package com.atrainingtracker.trainingtracker.exporter

/**
 * Centralized operational parameters and timeout bounds for export and upload workflows.
 * Traceable to REQ-DAT-029.
 */
object ExportConfig {
    /** Maximum number of retry attempts for transient worker failures. */
    const val MAX_RETRIES: Int = 3

    /** Periodic liveness heartbeat interval for executing export workers. */
    const val HEARTBEAT_INTERVAL_MS: Long = 15_000L // 15 seconds

    /** Maximum expected active export duration before considered stranded. */
    const val MAX_EXPORT_DURATION_MS: Long = 15 * 60 * 1000L // 15 minutes

    /** Safety grace period padding applied before executing lazy recovery on stranded rows. */
    const val RECOVERY_GRACE_PERIOD_MS: Long = 30_000L // 30 seconds
}
