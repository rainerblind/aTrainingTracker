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

/**
 * A specialized DialogFragment for editing a general device with no special fields.
 * Its primary purpose is to rely entirely on the implementation
 * provided by its parent, BaseEditDeviceFragment, while hiding specialized UI groups.
 */
class EditGeneralDeviceFragment : BaseEditDeviceFragment() {

    companion object {
        const val TAG = "EditGeneralDeviceFragment"

        @JvmStatic
        fun newInstance(deviceId: Long): EditGeneralDeviceFragment {
            return EditGeneralDeviceFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_DEVICE_ID, deviceId)
                }
            }
        }
    }

    // --- Overriding Methods from Base Class ---
    // nothing to do here...
}