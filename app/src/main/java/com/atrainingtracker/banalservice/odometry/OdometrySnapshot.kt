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
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0/>.
 */

package com.atrainingtracker.banalservice.odometry

/**
 * Forward step snapshot containing prior and posterior states, covariances, and transition Jacobian.
 * Preserved for offline / post-workout Rauch-Tung-Striebel (RTS) backward smoothing (ATT-7750).
 *
 * @property timestampMs Timestamp of the forward filter step in milliseconds.
 * @property dtSec Propagation delta time in seconds.
 * @property statePrior 5-element state vector x^-_k before measurement updates.
 * @property covariancePrior 5x5 error covariance matrix P^-_k before measurement updates.
 * @property transitionJacobian 5x5 state transition Jacobian matrix F_{k-1}.
 * @property statePosterior 5-element state vector x^+_k after measurement updates.
 * @property covariancePosterior 5x5 error covariance matrix P^+_k after measurement updates.
 */
data class OdometrySnapshot(
    val timestampMs: Long,
    val dtSec: Double,
    val statePrior: DoubleArray,
    val covariancePrior: Array<DoubleArray>,
    val transitionJacobian: Array<DoubleArray>,
    val statePosterior: DoubleArray,
    val covariancePosterior: Array<DoubleArray>
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as OdometrySnapshot
        if (timestampMs != other.timestampMs) return false
        if (dtSec != other.dtSec) return false
        if (!statePrior.contentEquals(other.statePrior)) return false
        if (!covariancePrior.contentDeepEquals(other.covariancePrior)) return false
        if (!transitionJacobian.contentDeepEquals(other.transitionJacobian)) return false
        if (!statePosterior.contentEquals(other.statePosterior)) return false
        if (!covariancePosterior.contentDeepEquals(other.covariancePosterior)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = timestampMs.hashCode()
        result = 31 * result + dtSec.hashCode()
        result = 31 * result + statePrior.contentHashCode()
        result = 31 * result + covariancePrior.contentDeepHashCode()
        result = 31 * result + transitionJacobian.contentDeepHashCode()
        result = 31 * result + statePosterior.contentHashCode()
        result = 31 * result + covariancePosterior.contentDeepHashCode()
        return result
    }
}
