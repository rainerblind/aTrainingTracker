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

import android.content.Context
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.Protocol
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.helpers.UIHelper
import com.atrainingtracker.banalservice.ui.devices.devicelist.DeviceFilterSpec
import com.atrainingtracker.banalservice.ui.devices.devicelist.DeviceFilterType
import com.atrainingtracker.banalservice.ui.devices.devicelist.ListDeviceFragment


/**
 * An adapter that provides a ListDeviceFragment for each filter type (Available, Paired, All Known).
 */
class DeviceListPagerAdapter(
    fragmentManager: FragmentManager,
    lifecycle: Lifecycle,
    private val context: Context,
    private val protocol: Protocol,
    private val deviceType: DeviceType
) : FragmentStateAdapter(fragmentManager, lifecycle) {

    // Define the filters for our tabs
    private val filters = listOf(
        DeviceFilterType.AVAILABLE,
        DeviceFilterType.PAIRED,
        DeviceFilterType.ALL_KNOWN
    )

    override fun getItemCount(): Int = filters.size

    override fun createFragment(position: Int): Fragment {
        val filterType = filters[position]
        val filterSpec = DeviceFilterSpec(filterType, protocol, deviceType)
        return ListDeviceFragment.newInstance(filterSpec)
    }

    fun getPageTitle(position: Int): CharSequence? {
        return if (deviceType == DeviceType.ALL) {
            when (filters[position]) {
                DeviceFilterType.AVAILABLE -> context.getString(R.string.devices_all_available_devices)
                DeviceFilterType.PAIRED -> context.getString(R.string.devices_all_paired_devices)
                DeviceFilterType.ALL_KNOWN -> context.getString(R.string.devices_all_known_devices)
            }
        }
        else {
            val deviceTypeName = context.getString(UIHelper.getNameId(deviceType))
            when (filters[position]) {
                DeviceFilterType.AVAILABLE -> context.getString(R.string.devices_available_devices_format, deviceTypeName)
                DeviceFilterType.PAIRED -> context.getString(R.string.devices_paired_devices_format, deviceTypeName)
                DeviceFilterType.ALL_KNOWN -> context.getString(R.string.devices_known_devices_format, deviceTypeName)
            }
        }
    }
}