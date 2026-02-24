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