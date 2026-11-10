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

package com.atrainingtracker.trainingtracker.database.hrv

/**
 * Autonomic training readiness status derived from comparing the rolling 7-day mean ln(rMSSD) (mu_7d)
 * against the 60-day Smallest Worthwhile Change (SWC) corridor [mu_60d - 0.5*sigma_60d, mu_60d + 0.5*sigma_60d]
 * (`REQ-PER-030.4`).
 */
enum class ReadinessStatus {
    /**
     * Insufficient baseline or rolling history (< 14 days in 60-day baseline or < 3 days in 7-day window).
     */
    CALIBRATING,

    /**
     * 7-day rolling average lies within the individual 60-day SWC corridor.
     */
    OPTIMAL,

    /**
     * 7-day rolling average falls below the SWC corridor (mu_7d < mu_60d - 0.5*sigma_60d),
     * indicating sympathetic stress or autonomic fatigue.
     */
    FATIGUED_LOW,

    /**
     * 7-day rolling average rises above the SWC corridor (mu_7d > mu_60d + 0.5*sigma_60d),
     * indicating parasympathetic rebound or acute hyperactivity.
     */
    ABOVE_NORMAL
}
