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

package com.atrainingtracker.banalservice.odometry

import com.atrainingtracker.banalservice.BSportType
import java.util.ArrayDeque

/**
 * Snapshot of live session telemetry at a 1-Hz boundary used for retroactive multisport lap
 * anchoring (`REQ-FIL-031.1`, `ATT-8917`).
 */
data class MultisportTelemetryAnchor(
    val timestampMs: Long,
    val activeTimeS: Int,
    val totalTimeS: Int,
    val totalDistanceM: Double
)

/**
 * Live activity flags from external ANT+/BLE hardware sensors distinguishing actively producing
 * cycling/running sensors from parked/silent sensors left on a bike rack in `T2` (`REQ-FIL-031.1`).
 */
data class MultisportHardwareSignals(
    val hasActiveBikeHardware: Boolean = false,
    val hasActiveRunHardware: Boolean = false,
    val hardwareSportType: BSportType = BSportType.UNKNOWN
)

/**
 * Confirmed macro-sport transition event emitted after sustained continuous unpaused confirmation
 * of the opposite sport (`REQ-FIL-031.1`, `ATT-8917`).
 */
data class MultisportTransitionEvent(
    val fromSport: BSportType,
    val toSport: BSportType,
    val exitAnchor: MultisportTelemetryAnchor,
    val enterAnchor: MultisportTelemetryAnchor,
    val confirmAnchor: MultisportTelemetryAnchor
) {
    val transitionWallClockDurationMs: Long
        get() = (enterAnchor.timestampMs - exitAnchor.timestampMs).coerceAtLeast(0L)
}

/**
 * Pure-Kotlin 1-Hz multisport transition state machine (`BIKE <-> TRANSITION <-> RUN`) with
 * retroactive dismount/mount boundary anchoring (`REQ-FIL-031.1`, `ATT-8917`).
 *
 * Fuses:
 * 1. 1-Hz [PhoneCarriageMode] classifications (`REQ-FIL-028`),
 * 2. [KinematicSportTypeDiscriminator] inferred [BSportType] (including permission-free 50-Hz
 *    accelerometer footstrike periodicity countering `PhoneCarriageClassifier` `cyclingPrior`
 *    bias when a parked bike sensor stays connected in `T2` without `TYPE_STEP_DETECTOR`), and
 * 3. Live non-zero external hardware telemetry (`BIKE_POWER`, `BIKE_SPEED_AND_CADENCE`, `RUN_SPEED`).
 *
 * Enforces:
 * - Active unpaused tracking gate (`isTracking && !isPaused && !isAutoPaused`) on the `15 s`
 *   confirmation window.
 * - Strict Tier 1 user sport lock immunity (`isUserSportLocked == true` emits zero transitions).
 * - Same-sport stop immunity (e.g., `160 s` bakery stop `BIKE -> WALK/STAT -> BIKE` emits `0`
 *   transitions) and `< 15 s` opposite-sport blip rejection.
 */
@Suppress("MagicNumber", "ComplexMethod", "ReturnCount")
class MultisportTransitionDetector(
    private val confirmationWindowMs: Long = DEFAULT_CONFIRMATION_WINDOW_MS,
    private val establishWindowMs: Long = DEFAULT_ESTABLISH_WINDOW_MS,
    private val maxAnchorHistory: Int = DEFAULT_MAX_ANCHOR_HISTORY
) {
    companion object {
        const val DEFAULT_CONFIRMATION_WINDOW_MS = 15_000L
        const val DEFAULT_ESTABLISH_WINDOW_MS = 3_000L
        const val MIN_TRANSITION_LAP_DURATION_MS = 10_000L
        const val DEFAULT_MAX_ANCHOR_HISTORY = 600
        const val MIN_RUN_SPEED_MPS = 1.60
        private const val MAX_TICK_DT_MS = 5_000L
    }

    private enum class TickClassification {
        BIKE,
        RUN,
        TRANSITION_NEUTRAL,
        UNKNOWN
    }

    private data class CandidateWindowSample(
        val activeMs: Long,
        val totalDistanceM: Double,
        val cumulativeCorroboratedMs: Long,
        val anchor: MultisportTelemetryAnchor
    )

    private val anchorHistory = ArrayDeque<MultisportTelemetryAnchor>(maxAnchorHistory.coerceAtLeast(16))
    private val initialWindow = ArrayDeque<CandidateWindowSample>()
    private val candidateWindow = ArrayDeque<CandidateWindowSample>()

    private var establishedSport: BSportType = BSportType.UNKNOWN
    private var pendingInitialSport: BSportType = BSportType.UNKNOWN
    private var pendingInitialActiveMs: Long = 0L
    private var pendingInitialCorroboratedMs: Long = 0L
    private var lastInitialTickMs: Long = Long.MIN_VALUE

    private var exitAnchor: MultisportTelemetryAnchor? = null
    private var candidateTargetSport: BSportType = BSportType.UNKNOWN
    private var enterAnchor: MultisportTelemetryAnchor? = null
    private var candidateConfirmedActiveMs: Long = 0L
    private var candidateCorroboratedMs: Long = 0L
    private var lastCandidateTickMs: Long = Long.MIN_VALUE
    private var lastTickTimestampMs: Long = Long.MIN_VALUE

    val currentEstablishedSport: BSportType
        @Synchronized get() = establishedSport

    val pendingExitAnchor: MultisportTelemetryAnchor?
        @Synchronized get() = exitAnchor

    val pendingEnterAnchor: MultisportTelemetryAnchor?
        @Synchronized get() = enterAnchor

    @Synchronized
    fun anchorHistorySize(): Int = anchorHistory.size

    /**
     * Processes one 1-Hz observation tick and returns a [MultisportTransitionEvent] strictly when
     * an opposite macro-sport (`BIKE <-> RUN`) has been continuously confirmed for
     * [confirmationWindowMs] of active unpaused tracking (`REQ-FIL-031.1`, `REQ-FIL-044.3`).
     */
    @JvmOverloads
    @Synchronized
    fun onTick(
        carriageMode: PhoneCarriageMode,
        kinematicSport: BSportType,
        hardwareSignals: MultisportHardwareSignals,
        anchor: MultisportTelemetryAnchor,
        isTracking: Boolean,
        isPausedOrAutoPaused: Boolean,
        isUserSportLocked: Boolean,
        hasActiveRunFootstrike: Boolean? = null
    ): MultisportTransitionEvent? {
        val timestampMs = anchor.timestampMs
        if (timestampMs < 0L) return null
        if (lastTickTimestampMs != Long.MIN_VALUE && timestampMs < lastTickTimestampMs) {
            return null
        }
        lastTickTimestampMs = timestampMs

        if (isUserSportLocked || !isTracking) {
            clearPendingTransition()
            if (!isTracking) {
                establishedSport = BSportType.UNKNOWN
                clearInitialCandidate()
                anchorHistory.clear()
            }
            return null
        }

        recordAnchor(anchor)

        val isActiveUnpaused = !isPausedOrAutoPaused
        val tickClass = classifyTick(carriageMode, kinematicSport, hardwareSignals)
        val isRunCorroboratedTick = hardwareSignals.hasActiveRunHardware ||
            kinematicSport == BSportType.RUN ||
            hasActiveRunFootstrike == true

        if (!isActiveUnpaused) {
            // Freeze initial/confirmation timers during PAUSED / AUTO_PAUSED; never confirm while paused.
            if (lastInitialTickMs != Long.MIN_VALUE) {
                lastInitialTickMs = timestampMs
            }
            if (lastCandidateTickMs != Long.MIN_VALUE) {
                lastCandidateTickMs = timestampMs
            }
            // If we already established a sport and just entered pause/auto-pause during dismount,
            // preserve or seed the dismount exit anchor without advancing confirmation.
            if (establishedSport != BSportType.UNKNOWN && exitAnchor == null &&
                tickClass == TickClassification.TRANSITION_NEUTRAL
            ) {
                exitAnchor = anchor
            }
            return null
        }

        // Phase 1: Establish initial sport1 (BIKE or RUN)
        if (establishedSport == BSportType.UNKNOWN) {
            val observedSport = when (tickClass) {
                TickClassification.BIKE -> BSportType.BIKE
                TickClassification.RUN -> BSportType.RUN
                else -> BSportType.UNKNOWN
            }
            if (observedSport == BSportType.UNKNOWN) {
                clearInitialCandidate()
                return null
            }
            if (pendingInitialSport != observedSport) {
                pendingInitialSport = observedSport
                pendingInitialActiveMs = 0L
                pendingInitialCorroboratedMs = 0L
                lastInitialTickMs = timestampMs
                initialWindow.clear()
                initialWindow.addLast(CandidateWindowSample(0L, anchor.totalDistanceM, 0L, anchor))
            } else {
                val dt = (timestampMs - lastInitialTickMs).coerceIn(0L, MAX_TICK_DT_MS)
                pendingInitialActiveMs += dt
                if (observedSport == BSportType.RUN && isRunCorroboratedTick) {
                    pendingInitialCorroboratedMs += dt
                }
                lastInitialTickMs = timestampMs
                initialWindow.addLast(
                    CandidateWindowSample(pendingInitialActiveMs, anchor.totalDistanceM, pendingInitialCorroboratedMs, anchor)
                )
                pruneSlidingWindow(initialWindow, pendingInitialActiveMs, establishWindowMs)
            }
            if (observedSport == BSportType.RUN) {
                val minInitialCorroborationMs = minOf(establishWindowMs, (establishWindowMs * 2L) / 3L)
                if (evaluateRunWindow(
                        window = initialWindow,
                        currentActiveMs = pendingInitialActiveMs,
                        currentCorroboratedMs = pendingInitialCorroboratedMs,
                        currentAnchor = anchor,
                        spanMs = establishWindowMs,
                        minCorroborationMs = minInitialCorroborationMs,
                        hasActiveRunHardware = hardwareSignals.hasActiveRunHardware
                    ) != null
                ) {
                    establishedSport = BSportType.RUN
                    clearInitialCandidate()
                }
            } else if (pendingInitialActiveMs >= establishWindowMs) {
                establishedSport = observedSport
                clearInitialCandidate()
            }
            return null
        }

        // Phase 2: Monitor establishedSport -> TRANSITION -> oppositeSport
        val targetSport = oppositeSport(establishedSport)
        when (tickClass) {
            toTickClass(establishedSport) -> {
                // Same-sport activity resumed (e.g., bakery stop -> back on bike): reset cleanly.
                clearPendingTransition()
                return null
            }

            TickClassification.TRANSITION_NEUTRAL -> {
                if (exitAnchor == null) {
                    exitAnchor = anchor
                }
                // Drop any unconfirmed < 15 s opposite-sport blip while keeping exitAnchor intact.
                clearTargetCandidateOnly()
                return null
            }

            TickClassification.UNKNOWN -> {
                // Transient unknown frame during confirmation resets unconfirmed blip cleanly.
                clearTargetCandidateOnly()
                return null
            }

            toTickClass(targetSport) -> {
                if (exitAnchor == null) {
                    // Direct sport1 -> sport2 switch without explicit walking/stationary gap.
                    exitAnchor = anchor
                }
                if (candidateTargetSport != targetSport) {
                    candidateTargetSport = targetSport
                    enterAnchor = anchor
                    candidateConfirmedActiveMs = 0L
                    candidateCorroboratedMs = 0L
                    lastCandidateTickMs = timestampMs
                    candidateWindow.clear()
                    candidateWindow.addLast(CandidateWindowSample(0L, anchor.totalDistanceM, 0L, anchor))
                } else {
                    val dt = (timestampMs - lastCandidateTickMs).coerceIn(0L, MAX_TICK_DT_MS)
                    candidateConfirmedActiveMs += dt
                    if (targetSport == BSportType.RUN && isRunCorroboratedTick) {
                        candidateCorroboratedMs += dt
                    }
                    lastCandidateTickMs = timestampMs
                    candidateWindow.addLast(
                        CandidateWindowSample(candidateConfirmedActiveMs, anchor.totalDistanceM, candidateCorroboratedMs, anchor)
                    )
                    pruneSlidingWindow(candidateWindow, candidateConfirmedActiveMs, confirmationWindowMs)
                    if (targetSport == BSportType.RUN) {
                        enterAnchor = candidateWindow.peekFirst()?.anchor ?: enterAnchor
                    }
                }

                if (targetSport == BSportType.RUN) {
                    val minConfirmCorroborationMs = minOf(confirmationWindowMs, (confirmationWindowMs * 3L) / 5L)
                    val qualifyingEnter = evaluateRunWindow(
                        window = candidateWindow,
                        currentActiveMs = candidateConfirmedActiveMs,
                        currentCorroboratedMs = candidateCorroboratedMs,
                        currentAnchor = anchor,
                        spanMs = confirmationWindowMs,
                        minCorroborationMs = minConfirmCorroborationMs,
                        hasActiveRunHardware = hardwareSignals.hasActiveRunHardware
                    )
                    if (qualifyingEnter != null) {
                        val confirmedExit = exitAnchor ?: qualifyingEnter
                        val event = MultisportTransitionEvent(
                            fromSport = establishedSport,
                            toSport = targetSport,
                            exitAnchor = confirmedExit,
                            enterAnchor = qualifyingEnter,
                            confirmAnchor = anchor
                        )
                        establishedSport = targetSport
                        clearPendingTransition()
                        return event
                    }
                } else if (candidateConfirmedActiveMs >= confirmationWindowMs) {
                    val confirmedExit = exitAnchor ?: anchor
                    val confirmedEnter = enterAnchor ?: anchor
                    val event = MultisportTransitionEvent(
                        fromSport = establishedSport,
                        toSport = targetSport,
                        exitAnchor = confirmedExit,
                        enterAnchor = confirmedEnter,
                        confirmAnchor = anchor
                    )
                    establishedSport = targetSport
                    clearPendingTransition()
                    return event
                }
                return null
            }

            else -> return null
        }
    }

    @Synchronized
    fun reset() {
        anchorHistory.clear()
        establishedSport = BSportType.UNKNOWN
        clearInitialCandidate()
        clearPendingTransition()
        lastTickTimestampMs = Long.MIN_VALUE
    }

    private fun classifyTick(
        carriageMode: PhoneCarriageMode,
        kinematicSport: BSportType,
        hw: MultisportHardwareSignals
    ): TickClassification {
        // 1. Live non-zero hardware telemetry takes top priority when unambiguous.
        if (hw.hasActiveBikeHardware && !hw.hasActiveRunHardware) {
            return TickClassification.BIKE
        }
        if (hw.hasActiveRunHardware && !hw.hasActiveBikeHardware) {
            return TickClassification.RUN
        }

        // 2. Running carriage modes always signal RUN (unless active non-zero bike hardware above).
        if (carriageMode.isRunningMode()) {
            return TickClassification.RUN
        }

        // 3. Cycling carriage modes signal BIKE unless a silent/parked bike sensor
        //    (hardwareSportType == BIKE, !hasActiveBikeHardware) biased PhoneCarriageClassifier
        //    (cyclingPrior += 2.5) while permission-free 50-Hz accel footstrikes confirmed RUN.
        if (carriageMode.isCyclingMode()) {
            if (hw.hardwareSportType == BSportType.BIKE &&
                !hw.hasActiveBikeHardware &&
                kinematicSport == BSportType.RUN
            ) {
                return TickClassification.RUN
            }
            return TickClassification.BIKE
        }

        // 4. Walking/Stationary is neutral transition unless permission-free 50-Hz accel footstrikes
        //    in KinematicSportTypeDiscriminator actively confirmed the opposite sport (e.g., RUN
        //    during T2 while cyclingPrior suppressed RUN_* into WALKING_TRANSITION).
        if (carriageMode.isWalkingOrStationary()) {
            if (carriageMode == PhoneCarriageMode.WALKING_TRANSITION &&
                establishedSport == BSportType.BIKE &&
                !hw.hasActiveBikeHardware &&
                kinematicSport == BSportType.RUN
            ) {
                return TickClassification.RUN
            }
            return TickClassification.TRANSITION_NEUTRAL
        }

        // 5. Fallback when carriageMode is UNKNOWN: trust kinematic discriminator if committed.
        return when (kinematicSport) {
            BSportType.BIKE -> TickClassification.BIKE
            BSportType.RUN -> TickClassification.RUN
            else -> TickClassification.UNKNOWN
        }
    }

    private fun recordAnchor(anchor: MultisportTelemetryAnchor) {
        if (anchorHistory.size >= maxAnchorHistory) {
            anchorHistory.removeFirst()
        }
        anchorHistory.addLast(anchor)
    }

    private fun pruneSlidingWindow(
        window: ArrayDeque<CandidateWindowSample>,
        currentActiveMs: Long,
        spanMs: Long
    ) {
        val effectiveSpanMs = spanMs.coerceAtLeast(0L)
        while (window.size >= 2) {
            val first = window.removeFirst()
            val second = window.peekFirst()!!
            if (currentActiveMs - second.activeMs < effectiveSpanMs) {
                window.addFirst(first)
                break
            }
        }
    }

    private fun evaluateRunWindow(
        window: ArrayDeque<CandidateWindowSample>,
        currentActiveMs: Long,
        currentCorroboratedMs: Long,
        currentAnchor: MultisportTelemetryAnchor,
        spanMs: Long,
        minCorroborationMs: Long,
        hasActiveRunHardware: Boolean
    ): MultisportTelemetryAnchor? {
        val head = window.peekFirst() ?: return null
        val windowActiveMs = currentActiveMs - head.activeMs
        if (windowActiveMs < spanMs) return null
        if (hasActiveRunHardware || spanMs <= 0L) return head.anchor

        val windowActiveSec = windowActiveMs / 1000.0
        if (windowActiveSec <= 0.0) return null
        val windowDistM = safeDistanceDeltaM(head.totalDistanceM, currentAnchor.totalDistanceM)
        val minWindowDistM = MIN_RUN_SPEED_MPS * (spanMs / 1000.0)
        val windowAvgSpeedMps = windowDistM / windowActiveSec
        if (windowDistM < minWindowDistM || windowAvgSpeedMps < MIN_RUN_SPEED_MPS) return null

        val windowCorroboratedMs = (currentCorroboratedMs - head.cumulativeCorroboratedMs).coerceAtLeast(0L)
        return if (windowCorroboratedMs >= minCorroborationMs) head.anchor else null
    }

    private fun safeDistanceDeltaM(fromDistanceM: Double, toDistanceM: Double): Double {
        if (!fromDistanceM.isFinite() || !toDistanceM.isFinite()) return 0.0
        val delta = toDistanceM - fromDistanceM
        return if (delta.isFinite() && delta > 0.0) delta else 0.0
    }

    private fun clearInitialCandidate() {
        pendingInitialSport = BSportType.UNKNOWN
        pendingInitialActiveMs = 0L
        pendingInitialCorroboratedMs = 0L
        lastInitialTickMs = Long.MIN_VALUE
        initialWindow.clear()
    }

    private fun clearTargetCandidateOnly() {
        candidateTargetSport = BSportType.UNKNOWN
        enterAnchor = null
        candidateConfirmedActiveMs = 0L
        candidateCorroboratedMs = 0L
        lastCandidateTickMs = Long.MIN_VALUE
        candidateWindow.clear()
    }

    private fun clearPendingTransition() {
        exitAnchor = null
        clearTargetCandidateOnly()
    }

    private fun oppositeSport(sport: BSportType): BSportType = when (sport) {
        BSportType.BIKE -> BSportType.RUN
        BSportType.RUN -> BSportType.BIKE
        else -> BSportType.UNKNOWN
    }

    private fun toTickClass(sport: BSportType): TickClassification = when (sport) {
        BSportType.BIKE -> TickClassification.BIKE
        BSportType.RUN -> TickClassification.RUN
        else -> TickClassification.UNKNOWN
    }
}
