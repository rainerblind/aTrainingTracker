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

package com.atrainingtracker.banalservice.devices.radar

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Scriptable traffic simulation scenarios for radar verification and demo mode (REQ-CON-029.1).
 */
enum class RadarScenario {
    /** Single vehicle approaching from 140m at 12 m/s, passing and incrementing vehicle count. */
    SINGLE_CAR,
    /** 3 simultaneous vehicles approaching at different distances (85m, 115m, 140m). */
    CONVOY,
    /** Vehicle rapidly closing at 25 m/s (90 km/h) triggering FAST_APPROACHING red threat. */
    HIGH_SPEED,
    /** Mid-ride packet starvation triggering DISCONNECTED_ERROR followed by recovery after 5s. */
    DISCONNECT_RECONNECT,
    /** Continuous 30-second loop with quiet periods, standard overtaking, and high-speed threat. */
    COUNTRY_ROAD_LOOP
}

/**
 * Built-in cycling radar traffic simulator and scenario generator (REQ-CON-029).
 *
 * Generates synthetic multi-target frames at 8 Hz (125ms intervals) feeding [RadarStateRepository]
 * and [RadarWindowAggregator]. Automatically preempts when physical radar hardware streams data.
 */
object RadarTrafficSimulator {

    /** Shared aggregator instance dedicated to the simulator engine. */
    val windowAggregator: RadarWindowAggregator = RadarWindowAggregator()

    var activeScenario: RadarScenario? = null
        private set

    var simulatedLightMode: RadarLightMode = RadarLightMode.OFF
        private set

    val isSimulating: Boolean
        get() = simulationJob?.isActive == true

    fun setSimulatedLightMode(mode: RadarLightMode) {
        simulatedLightMode = mode
        val current = RadarStateRepository.getState()
        RadarStateRepository.updateState(current.copy(lightMode = mode, isLightControlSupported = true))
    }

    private var simulationJob: Job? = null
    private var hardwareActiveProvider: (() -> Boolean)? = null

    /**
     * Registers a callback that returns true if a physical radar peripheral is active/streaming.
     */
    fun registerHardwareActiveProvider(provider: (() -> Boolean)?) {
        hardwareActiveProvider = provider
    }

    /**
     * Deterministic, clock-independent frame generator for scenario testing (REQ-CON-029.1).
     *
     * @param scenario The traffic scenario to evaluate.
     * @param elapsedMs Elapsed time in milliseconds since the scenario started.
     * @param realtimeMs Timestamp assigned to [RadarState.lastUpdateRealtimeMs].
     * @return Immutable [RadarState] representing the radar field at [elapsedMs].
     */
    fun generateFrame(
        scenario: RadarScenario,
        elapsedMs: Long,
        realtimeMs: Long = 0L
    ): RadarState {
        return when (scenario) {
            RadarScenario.SINGLE_CAR -> {
                // Vehicle approaching from 140m at 12 m/s. Passes at ~11.67s.
                val durationMs = 11667L
                val targets = if (elapsedMs < durationMs) {
                    val range = 140f - (elapsedMs / 1000f) * 12f
                    listOf(
                        RadarTarget(
                            targetId = 1,
                            rangeMeters = range.coerceAtLeast(0f),
                            approachSpeedMps = 12f,
                            threatLevel = ThreatLevel.APPROACHING,
                            lateralOffsetMeters = -1.8f,
                            longitudinalSpeedMps = 12f,
                            lateralSpeedMps = 0f,
                            vehicleWidthMeters = 1.9f,
                            vehicleLengthMeters = 4.5f,
                            vehicleSizeCategory = VehicleSizeCategory.CAR,
                            laneState = LaneState.PASSING_LANE
                        )
                    )
                } else {
                    emptyList()
                }
                RadarState(
                    connectionState = RadarConnectionState.CONNECTED,
                    overallThreatLevel = if (targets.isNotEmpty()) ThreatLevel.APPROACHING else ThreatLevel.NONE,
                    targets = targets,
                    lastUpdateRealtimeMs = realtimeMs
                )
            }
            RadarScenario.CONVOY -> {
                // 3 vehicles approaching simultaneously:
                // Target 1: 85m at 10 m/s (passes at 8.5s)
                // Target 2: 115m at 10 m/s (passes at 11.5s)
                // Target 3: 140m at 10 m/s (passes at 14.0s)
                val targets = mutableListOf<RadarTarget>()
                if (elapsedMs < 8500L) {
                    targets.add(
                        RadarTarget(
                            targetId = 1,
                            rangeMeters = (85f - (elapsedMs / 1000f) * 10f).coerceAtLeast(0f),
                            approachSpeedMps = 10f,
                            threatLevel = ThreatLevel.APPROACHING,
                            lateralOffsetMeters = -1.2f,
                            longitudinalSpeedMps = 10f,
                            lateralSpeedMps = 0f,
                            vehicleWidthMeters = 0.9f,
                            vehicleLengthMeters = 2.2f,
                            vehicleSizeCategory = VehicleSizeCategory.UNKNOWN_OR_LIGHT,
                            laneState = LaneState.PASSING_LANE
                        )
                    )
                }
                if (elapsedMs < 11500L) {
                    targets.add(
                        RadarTarget(
                            targetId = 2,
                            rangeMeters = (115f - (elapsedMs / 1000f) * 10f).coerceAtLeast(0f),
                            approachSpeedMps = 10f,
                            threatLevel = ThreatLevel.APPROACHING,
                            lateralOffsetMeters = -2.0f,
                            longitudinalSpeedMps = 10f,
                            lateralSpeedMps = 0f,
                            vehicleWidthMeters = 1.9f,
                            vehicleLengthMeters = 4.5f,
                            vehicleSizeCategory = VehicleSizeCategory.CAR,
                            laneState = LaneState.PASSING_LANE
                        )
                    )
                }
                if (elapsedMs < 14000L) {
                    targets.add(
                        RadarTarget(
                            targetId = 3,
                            rangeMeters = (140f - (elapsedMs / 1000f) * 10f).coerceAtLeast(0f),
                            approachSpeedMps = 10f,
                            threatLevel = ThreatLevel.APPROACHING,
                            lateralOffsetMeters = -1.5f,
                            longitudinalSpeedMps = 10f,
                            lateralSpeedMps = 0f,
                            vehicleWidthMeters = 2.6f,
                            vehicleLengthMeters = 11.5f,
                            vehicleSizeCategory = VehicleSizeCategory.TRUCK_OR_BUS,
                            laneState = LaneState.PASSING_LANE
                        )
                    )
                }
                RadarState(
                    connectionState = RadarConnectionState.CONNECTED,
                    overallThreatLevel = if (targets.isNotEmpty()) ThreatLevel.APPROACHING else ThreatLevel.NONE,
                    targets = targets,
                    lastUpdateRealtimeMs = realtimeMs
                )
            }
            RadarScenario.HIGH_SPEED -> {
                // Vehicle rapidly closing at 25 m/s (90 km/h) from 140m. Passes at 5.6s.
                val durationMs = 5600L
                val targets = if (elapsedMs < durationMs) {
                    val range = 140f - (elapsedMs / 1000f) * 25f
                    listOf(
                        RadarTarget(
                            targetId = 1,
                            rangeMeters = range.coerceAtLeast(0f),
                            approachSpeedMps = 25f,
                            threatLevel = ThreatLevel.FAST_APPROACHING,
                            lateralOffsetMeters = -2.2f,
                            longitudinalSpeedMps = 25f,
                            lateralSpeedMps = 0f,
                            vehicleWidthMeters = 2.0f,
                            vehicleLengthMeters = 4.8f,
                            vehicleSizeCategory = VehicleSizeCategory.CAR,
                            laneState = LaneState.PASSING_LANE
                        )
                    )
                } else {
                    emptyList()
                }
                RadarState(
                    connectionState = RadarConnectionState.CONNECTED,
                    overallThreatLevel = if (targets.isNotEmpty()) ThreatLevel.FAST_APPROACHING else ThreatLevel.NONE,
                    targets = targets,
                    lastUpdateRealtimeMs = realtimeMs
                )
            }
            RadarScenario.DISCONNECT_RECONNECT -> {
                // 0..3000ms: CONNECTED, approaching target
                // 3000..8000ms: DISCONNECTED_ERROR (packet starvation)
                // 8000ms+: CONNECTED, recovered connection
                when {
                    elapsedMs < 3000L -> {
                        val range = 60f - (elapsedMs / 1000f) * 12f
                        val targets = listOf(
                            RadarTarget(
                                targetId = 1,
                                rangeMeters = range.coerceAtLeast(0f),
                                approachSpeedMps = 12f,
                                threatLevel = ThreatLevel.APPROACHING
                            )
                        )
                        RadarState(
                            connectionState = RadarConnectionState.CONNECTED,
                            overallThreatLevel = ThreatLevel.APPROACHING,
                            targets = targets,
                            lastUpdateRealtimeMs = realtimeMs
                        )
                    }
                    elapsedMs < 8000L -> {
                        RadarState(
                            connectionState = RadarConnectionState.DISCONNECTED_ERROR,
                            overallThreatLevel = ThreatLevel.NONE,
                            targets = emptyList(),
                            lastUpdateRealtimeMs = realtimeMs
                        )
                    }
                    else -> {
                        val recoveryElapsed = elapsedMs - 8000L
                        val range = 40f - (recoveryElapsed / 1000f) * 12f
                        val targets = if (range > 0f) {
                            listOf(
                                RadarTarget(
                                    targetId = 2,
                                    rangeMeters = range,
                                    approachSpeedMps = 12f,
                                    threatLevel = ThreatLevel.APPROACHING
                                )
                            )
                        } else {
                            emptyList()
                        }
                        RadarState(
                            connectionState = RadarConnectionState.CONNECTED,
                            overallThreatLevel = if (targets.isNotEmpty()) ThreatLevel.APPROACHING else ThreatLevel.NONE,
                            targets = targets,
                            lastUpdateRealtimeMs = realtimeMs
                        )
                    }
                }
            }
            RadarScenario.COUNTRY_ROAD_LOOP -> {
                // Periodic 30s cycle:
                // 0..10s: peaceful quiet road
                // 10..22s: single overtaking car (140m at 12 m/s)
                // 22..25s: quiet gap
                // 25..30s: high-speed threat (125m at 25 m/s)
                val loopMs = elapsedMs % 30_000L
                when {
                    loopMs < 10_000L -> {
                        RadarState(
                            connectionState = RadarConnectionState.CONNECTED,
                            overallThreatLevel = ThreatLevel.NONE,
                            targets = emptyList(),
                            lastUpdateRealtimeMs = realtimeMs
                        )
                    }
                    loopMs < 22_000L -> {
                        val carElapsed = loopMs - 10_000L
                        val range = 140f - (carElapsed / 1000f) * 12f
                        val targets = if (range > 0f) {
                            listOf(
                                RadarTarget(
                                    targetId = 10,
                                    rangeMeters = range,
                                    approachSpeedMps = 12f,
                                    threatLevel = ThreatLevel.APPROACHING
                                )
                            )
                        } else {
                            emptyList()
                        }
                        RadarState(
                            connectionState = RadarConnectionState.CONNECTED,
                            overallThreatLevel = if (targets.isNotEmpty()) ThreatLevel.APPROACHING else ThreatLevel.NONE,
                            targets = targets,
                            lastUpdateRealtimeMs = realtimeMs
                        )
                    }
                    loopMs < 25_000L -> {
                        RadarState(
                            connectionState = RadarConnectionState.CONNECTED,
                            overallThreatLevel = ThreatLevel.NONE,
                            targets = emptyList(),
                            lastUpdateRealtimeMs = realtimeMs
                        )
                    }
                    else -> {
                        val fastElapsed = loopMs - 25_000L
                        val range = 125f - (fastElapsed / 1000f) * 25f
                        val targets = if (range > 0f) {
                            listOf(
                                RadarTarget(
                                    targetId = 20,
                                    rangeMeters = range,
                                    approachSpeedMps = 25f,
                                    threatLevel = ThreatLevel.FAST_APPROACHING
                                )
                            )
                        } else {
                            emptyList()
                        }
                        RadarState(
                            connectionState = RadarConnectionState.CONNECTED,
                            overallThreatLevel = if (targets.isNotEmpty()) ThreatLevel.FAST_APPROACHING else ThreatLevel.NONE,
                            targets = targets,
                            lastUpdateRealtimeMs = realtimeMs
                        )
                    }
                }
            }
        }
    }

    /**
     * Executes a single simulation step at [elapsedMs] synchronously, pushing to repository and aggregator.
     */
    fun tick(
        scenario: RadarScenario,
        elapsedMs: Long,
        aggregator: RadarWindowAggregator = windowAggregator
    ): RadarState {
        val frame = generateFrame(scenario, elapsedMs).copy(
            lightMode = simulatedLightMode,
            isLightControlSupported = true,
            audioSnoozeState = RadarStateRepository.getState().audioSnoozeState
        )
        RadarStateRepository.updateState(frame)
        aggregator.onTargetsUpdated(frame.targets)
        return frame
    }

    /**
     * Generates a synthetic multi-target V2 radar frame with lateral offsets and vehicle classifications (REQ-CON-030.6).
     */
    fun generateV2Targets(count: Int = 1, includeLargeVehicle: Boolean = false): List<RadarTarget> {
        val targets = mutableListOf<RadarTarget>()
        for (i in 0 until count) {
            val isLarge = includeLargeVehicle && i == 0
            targets.add(
                RadarTarget(
                    targetId = i + 1,
                    rangeMeters = 40.0f + i * 25.0f,
                    approachSpeedMps = 12.0f + i * 2.0f,
                    threatLevel = if (i == 0 && isLarge) ThreatLevel.FAST_APPROACHING else ThreatLevel.APPROACHING,
                    lateralOffsetMeters = if (isLarge) -1.4f else -2.0f,
                    longitudinalSpeedMps = 12.0f + i * 2.0f,
                    lateralSpeedMps = 0.0f,
                    vehicleWidthMeters = if (isLarge) 2.6f else 1.9f,
                    vehicleLengthMeters = if (isLarge) 10.0f else 4.5f,
                    vehicleSizeCategory = if (isLarge) VehicleSizeCategory.TRUCK_OR_BUS else VehicleSizeCategory.CAR,
                    laneState = LaneState.PASSING_LANE
                )
            )
        }
        return targets
    }

    /**
     * Starts continuous 8 Hz background simulation of [scenario] (REQ-CON-029.2).
     */
    fun start(
        scenario: RadarScenario,
        scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
        aggregator: RadarWindowAggregator = windowAggregator,
        tickIntervalMs: Long = 125L
    ) {
        stop()
        activeScenario = scenario
        val startTime = System.currentTimeMillis()
        simulationJob = scope.launch {
            try {
                while (isActive) {
                    if (hardwareActiveProvider?.invoke() == true) {
                        stop()
                        break
                    }
                    val elapsed = System.currentTimeMillis() - startTime
                    val frame = generateFrame(scenario, elapsed).copy(
                        lightMode = simulatedLightMode,
                        isLightControlSupported = true,
                        audioSnoozeState = RadarStateRepository.getState().audioSnoozeState
                    )
                    RadarStateRepository.updateState(frame)
                    aggregator.onTargetsUpdated(frame.targets)
                    delay(tickIntervalMs)
                }
            } finally {
                if (activeScenario == scenario) {
                    activeScenario = null
                }
            }
        }
    }

    /**
     * Halts active simulation and resets [RadarStateRepository] and [windowAggregator] (REQ-CON-029.3).
     */
    fun stop() {
        simulationJob?.cancel()
        simulationJob = null
        activeScenario = null
        simulatedLightMode = RadarLightMode.OFF
        RadarStateRepository.reset()
        windowAggregator.resetSession()
    }

    /**
     * Preemption handler invoked when physical radar hardware connects or streams data.
     */
    fun onPhysicalHardwareConnected() {
        stop()
    }
}
