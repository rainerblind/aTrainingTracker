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

package com.atrainingtracker.banalservice.ui.devices.devicelist

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.atrainingtracker.banalservice.Protocol
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.ui.devices.GetMergedDevicesUseCase
import com.atrainingtracker.banalservice.ui.devices.devicedata.DeviceDataRepository
import com.atrainingtracker.banalservice.ui.devices.devicedata.DeviceUiData
import com.atrainingtracker.trainingtracker.monetization.GarageLeagueDetector
import com.atrainingtracker.trainingtracker.monetization.GarageNudgeEvent
import com.atrainingtracker.trainingtracker.monetization.ProFeatureManager
import com.atrainingtracker.trainingtracker.monetization.SensorQuotaReconciliationManager
import com.atrainingtracker.trainingtracker.repositories.BANALServiceRepository
import com.atrainingtracker.trainingtracker.settings.SettingsDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

data class ZoneOnboardingEvent(
    val initialTab: Int,
    val promptReason: String
)

data class SwapOrUpgradeDialogEvent(
    val candidateDevice: DeviceUiData,
    val currentlyPairedDevices: List<DeviceUiData>,
    val recommendedUnpairDeviceId: Long
)

data class KeepTwoSensorsDialogEvent(
    val currentlyPairedDevices: List<DeviceUiData>,
    val defaultKeepDeviceIds: Set<Long>
)

class DeviceListViewModel @JvmOverloads constructor(
    private val application: Application,
    private val settingsDataStore: SettingsDataStore = SettingsDataStore.getInstance(application),
    private val devicesDBRepository: DeviceDataRepository = DeviceDataRepository.getInstance(application),
    private val banalServiceRepository: BANALServiceRepository = BANALServiceRepository.getInstance(application),
    private val proFeatureManager: ProFeatureManager = ProFeatureManager.getInstance(application),
    private val garageLeagueDetector: GarageLeagueDetector = GarageLeagueDetector(application),
    private val isTrackingProvider: () -> Boolean = {
        try {
            com.atrainingtracker.trainingtracker.TrainingApplication.isTracking()
        } catch (e: Throwable) {
            false
        }
    },
    private val reconciliationManager: SensorQuotaReconciliationManager = SensorQuotaReconciliationManager.getInstance(application)
) : AndroidViewModel(application) {

    private val useCase = GetMergedDevicesUseCase(
        devicesDBRepository,
        banalServiceRepository,
        application
    )

    // the single source of truth: the merged devices
    private val allDevices = useCase.mergedDevices

    private val _zoneNavigationEvent = MutableSharedFlow<ZoneOnboardingEvent>(extraBufferCapacity = 1)
    val zoneNavigationEvent: SharedFlow<ZoneOnboardingEvent> = _zoneNavigationEvent.asSharedFlow()

    private val _swapOrUpgradeDialogEvent = MutableStateFlow<SwapOrUpgradeDialogEvent?>(null)
    val swapOrUpgradeDialogEvent: StateFlow<SwapOrUpgradeDialogEvent?> = _swapOrUpgradeDialogEvent.asStateFlow()

    val keepTwoSensorsDialogEvent: StateFlow<KeepTwoSensorsDialogEvent?> = reconciliationManager.keepTwoSensorsDialogEvent

    private val _garageNudgeEvent = MutableStateFlow<GarageNudgeEvent?>(null)
    val garageNudgeEvent: StateFlow<GarageNudgeEvent?> = _garageNudgeEvent.asStateFlow()

    init {
        checkDowngradeReconciliation()
    }

    fun dismissGarageNudge() {
        _garageNudgeEvent.value = null
    }

    private fun checkGarageNudge() {
        viewModelScope.launch {
            try {
                val event = garageLeagueDetector.evaluateNudge()
                if (event != null) {
                    garageLeagueDetector.markNudgeShown(event.league)
                    _garageNudgeEvent.value = event
                }
            } catch (e: Throwable) {
                // Defensive guard against unmocked Android dependencies during unit testing
            }
        }
    }

    /**
     * This is the key public method.
     * Fragments will call this to get a Flow stream tailored to their specific needs.
     */
    fun getFilteredDevices(
        spec: DeviceFilterSpec,
        criteria: DeviceFilterCriteria = DeviceFilterCriteria()
    ): Flow<List<DeviceUiData>> {
        // We apply another .map transformation to our already-transformed list.
        // This returns a new Flow stream that will re-filter whenever allListDevices changes.
        return allDevices.map { devices ->
            // First, apply the main filter based on the filter type.
            val primaryFiltered = when (spec.filterType) {
                DeviceFilterType.PAIRED -> devices.filter { it.isPaired }
                DeviceFilterType.CONNECTED -> devices.filter { it.isConnected }
                DeviceFilterType.ALL_KNOWN -> devices // No primary filter, return the whole list
            }

            // Then, apply secondary filters for protocol and device type to the result.
            val specFiltered = primaryFiltered.filter { device ->
                val protocolMatch = spec.protocol == Protocol.ALL || device.protocol == spec.protocol
                val deviceTypeMatch = spec.deviceType == DeviceType.ALL || device.deviceType == spec.deviceType
                protocolMatch && deviceTypeMatch
            }

            // Then, apply multi-dimensional filter criteria (protocol, categories, battery)
            val filtered = specFiltered.filter { criteria.matches(it) }

            val newlyFoundIds = banalServiceRepository.newlyFoundDevicesIds.value
            filtered.sortedWith(
                compareByDescending<DeviceUiData> { it.isNewlyDiscovered }
                    .thenByDescending { if (it.isNewlyDiscovered) newlyFoundIds.indexOf(it.id) else -1 }
                    .thenByDescending { it.isConnected }
                    .thenBy { it.deviceName }
            )
        }
    }

    // called from the device list
    // -> immediately update repo.
    fun onPairedChanged(deviceId: Long, isPaired: Boolean) {
        viewModelScope.launch {
            // Find the current state of the device from the repository's cache
            val currentState = devicesDBRepository.getDeviceSnapshotById(deviceId) ?: return@launch

            val wasPaired = currentState.isPaired
            val newPaired = !wasPaired

            // Free-tier Quota Interception (REQ-SET-083, REQ-SET-084)
            if (!wasPaired && newPaired) {
                val pairedDevices = devicesDBRepository.getPairedDevices()
                if (!proFeatureManager.isDevicePairingAllowed(pairedDevices.size)) {
                    val recommendedId = determineRecommendedUnpairDeviceId(pairedDevices)
                    _swapOrUpgradeDialogEvent.value = SwapOrUpgradeDialogEvent(
                        candidateDevice = currentState,
                        currentlyPairedDevices = pairedDevices,
                        recommendedUnpairDeviceId = recommendedId
                    )
                    return@launch
                }
            }

            // Create a new state with the isPaired property flipped
            val newState = currentState.copy(isPaired = newPaired)

            // Tell the repository to save this new state. The repository will handle
            // the database update, sending the broadcast, and updating the LiveData.
            devicesDBRepository.updateDevice(newState)

            // When transitioning from un-paired to paired, evaluate physiological zone onboarding (REQ-UI-374.1, REQ-UI-374.2)
            if (!wasPaired && newPaired) {
                checkZoneOnboarding(currentState)
                checkGarageNudge()
            }
        }
    }

    /**
     * Executes 1-tap atomic swap of pairing status between an active device and a candidate device (REQ-SET-084).
     * Hardened to ensure total paired sensors is strictly <= 2 on Free tier even if pairedCount > 2 previously existed (REQ-SET-084.6).
     */
    fun executeSwapAndPair(unpairDeviceId: Long, candidateDeviceId: Long) {
        viewModelScope.launch {
            devicesDBRepository.swapPairedDevice(unpairDeviceId, candidateDeviceId)
            // Multi-Sensor Hardening (REQ-SET-084.6):
            if (!proFeatureManager.isProActive.value) {
                val paired = devicesDBRepository.getPairedDevices()
                if (paired.size > 2) {
                    val retained = paired.filter { it.id != candidateDeviceId && it.id != unpairDeviceId }
                        .take(1)
                        .map { it.id }
                        .toSet()
                    val keepIds = retained + candidateDeviceId
                    devicesDBRepository.reconcilePairedDevices(keepIds)
                }
            }
            _swapOrUpgradeDialogEvent.value = null
            val candidate = devicesDBRepository.getDeviceSnapshotById(candidateDeviceId)
            if (candidate != null) {
                checkZoneOnboarding(candidate)
            }
            checkGarageNudge()
        }
    }

    fun dismissSwapOrUpgradeDialog() {
        _swapOrUpgradeDialogEvent.value = null
    }

    /**
     * Reconciles sensor pairing quota when returning to Free tier with > 2 paired sensors (REQ-SET-084.5).
     * Strictly gated on !isTracking() to avoid in-ride disruption. Delegated to [SensorQuotaReconciliationManager].
     */
    fun checkDowngradeReconciliation() {
        if (isTrackingProvider()) return
        reconciliationManager.checkDowngradeReconciliation()
    }

    fun confirmKeepTwoSensors(selectedIds: Set<Long>) {
        reconciliationManager.confirmKeepTwoSensors(selectedIds)
    }

    fun dismissKeepTwoSensors() {
        reconciliationManager.dismissKeepTwoSensors()
    }

    fun determineDefaultKeepDeviceIds(pairedDevices: List<DeviceUiData>): Set<Long> {
        return reconciliationManager.determineDefaultKeepDeviceIds(pairedDevices)
    }

    fun determineRecommendedUnpairDeviceId(pairedDevices: List<DeviceUiData>): Long {
        return reconciliationManager.determineRecommendedUnpairDeviceId(pairedDevices)
    }

    private suspend fun checkZoneOnboarding(device: DeviceUiData) {
        when (device.deviceType) {
            DeviceType.HRM -> {
                val isConfigured = settingsDataStore.isHrZonesConfigured.first()
                if (!isConfigured) {
                    _zoneNavigationEvent.emit(
                        ZoneOnboardingEvent(initialTab = 0, promptReason = PROMPT_REASON_HR_PAIRED)
                    )
                }
            }
            DeviceType.BIKE_POWER -> {
                val isConfigured = settingsDataStore.isPwrZonesConfigured.first()
                if (!isConfigured) {
                    _zoneNavigationEvent.emit(
                        ZoneOnboardingEvent(initialTab = 2, promptReason = PROMPT_REASON_PWR_PAIRED)
                    )
                }
            }
            else -> {
                // Non-physiological sensors complete silently (REQ-UI-374.1)
            }
        }
    }

    fun deleteDevice(deviceId: Long) {
        devicesDBRepository.deleteDevice(deviceId)
    }

    companion object {
        const val PROMPT_REASON_HR_PAIRED = "hr_paired"
        const val PROMPT_REASON_PWR_PAIRED = "pwr_paired"
    }
}