package com.atrainingtracker.banalservice.ui.devices.devicetypechoice

/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (C) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
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
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ListView
import androidx.fragment.app.ListFragment
import com.atrainingtracker.banalservice.BANALService
import com.atrainingtracker.banalservice.Protocol
import com.atrainingtracker.banalservice.devices.DeviceType

class DeviceTypeChoiceFragment : ListFragment() {

    // The container Activity must implement this interface
    interface OnDeviceTypeSelectedListener {
        /**
         * Called by DeviceTypeChoiceFragment when a list item is selected
         */
        fun onDeviceTypeSelected(deviceType: DeviceType, protocol: Protocol)
    }

    private lateinit var callback: OnDeviceTypeSelectedListener
    private lateinit var protocol: Protocol
    private lateinit var deviceTypes: List<DeviceType>

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (DEBUG) Log.i(TAG, "onAttach()")

        // This makes sure that the container activity has implemented the callback interface.
        callback = try {
            context as OnDeviceTypeSelectedListener
        } catch (e: ClassCastException) {
            throw ClassCastException("$context must implement OnDeviceTypeSelectedListener")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (DEBUG) Log.i(TAG, "onCreate()")

        // requireArguments() is a safer way to get arguments, it throws an exception if they are null.
        val protocolName = requireArguments().getString(BANALService.PROTOCOL)
        protocol = Protocol.valueOf(protocolName!!)
        deviceTypes = DeviceType.getRemoteDeviceTypes(protocol).toList()
    }

    override fun onResume() {
        super.onResume()
        if (DEBUG) Log.d(TAG, "onResume")

        listAdapter = DeviceTypeChoiceArrayAdapter(requireActivity(), deviceTypes, protocol)
    }

    override fun onListItemClick(listView: ListView, view: View, position: Int, id: Long) {
        // Notify the hosting activity of the selected item
        callback.onDeviceTypeSelected(deviceTypes[position], protocol)
    }

    companion object {
        const val TAG = "DeviceTypeChoiceFragment"
        private val DEBUG = BANALService.getDebug(false)

        @JvmStatic
        fun newInstance(protocol: Protocol): DeviceTypeChoiceFragment {
            return DeviceTypeChoiceFragment().apply {
                arguments = Bundle().apply {
                    putString(BANALService.PROTOCOL, protocol.name)
                }
            }
        }
    }
}