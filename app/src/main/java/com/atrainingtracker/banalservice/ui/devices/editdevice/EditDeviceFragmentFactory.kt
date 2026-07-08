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

package com.atrainingtracker.banalservice.ui.devices.editdevice

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.DialogFragment
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme

/**
 * A legacy bridge factory that returns a Composable-hosting DialogFragment.
 * This allows legacy Java/Fragment code to show the modern EditDeviceDialog.
 */
object EditDeviceFragmentFactory {
    @JvmStatic
    fun create(deviceId: Long, deviceType: DeviceType): DialogFragment {
        return ComposableEditDeviceDialogFragment.newInstance(deviceId)
    }
}

/**
 * A DialogFragment that hosts the modern Composable EditDeviceDialog.
 */
class ComposableEditDeviceDialogFragment : DialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val deviceId = requireArguments().getLong(ARG_DEVICE_ID)
        return ComposeView(requireContext()).apply {
            setContent {
                ATrainingTrackerTheme {
                    EditDeviceDialog(
                        deviceId = deviceId,
                        onDismiss = { dismiss() }
                    )
                }
            }
        }
    }

    companion object {
        private const val ARG_DEVICE_ID = "device_id"
        
        fun newInstance(deviceId: Long) = ComposableEditDeviceDialogFragment().apply {
            arguments = Bundle().apply {
                putLong(ARG_DEVICE_ID, deviceId)
            }
        }
    }
}
