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

package com.atrainingtracker.banalservice.ui.devices.devicetabs

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BANALService
import com.atrainingtracker.banalservice.Protocol
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.dialogs.InstallANTShitDialog
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme

/**
 * A container fragment that hosts the modern Composable Device Management UI.
 */
class DevicesTabbedContainerFragment : Fragment() {

    private val viewModel: DevicesTabbedViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                val startTab = arguments?.getInt(STARTING_TAB, 0) ?: 0
                val deviceType = arguments?.getString(BANALService.DEVICE_TYPE)?.let { DeviceType.valueOf(it) } ?: DeviceType.ALL
                
                // If only a protocol is specified (all device types), default to the "Known" tab (index 2)
                val finalInitialTab = if (deviceType == DeviceType.ALL && startTab == 0) 2 else startTab

                ATrainingTrackerTheme {
                    DevicesTabbedScreen(
                        tabViewModel = viewModel,
                        initialTab = finalInitialTab,
                        onCheckAntInstallation = {
                            InstallANTShitDialog().show(parentFragmentManager, InstallANTShitDialog.TAG)
                        }
                    )
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
    }

    companion object {
        const val TAG = "DevicesTabContainer"
        private const val STARTING_TAB = "starting_tab"

        @JvmStatic
        fun newInstance(protocol: Protocol, deviceType: DeviceType? = null, startingTab: Int = 0): DevicesTabbedContainerFragment {
            return DevicesTabbedContainerFragment().apply {
                arguments = Bundle().apply {
                    putString(BANALService.PROTOCOL, protocol.name)
                    deviceType?.let { putString(BANALService.DEVICE_TYPE, it.name) }
                    putInt(STARTING_TAB, startingTab)
                }
            }
        }
    }
}
