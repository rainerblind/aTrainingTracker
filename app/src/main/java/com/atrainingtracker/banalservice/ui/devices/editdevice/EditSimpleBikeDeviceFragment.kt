package com.atrainingtracker.banalservice.ui.devices.editdevice


import android.os.Bundle

/**
 * A concrete DialogFragment for editing a simple bike device (e.g., speed/cadence)
 * that has wheel circumference but no power features.
 *
 * It inherits all its logic from [EditBikeDeviceFragment].
 */
class EditSimpleBikeDeviceFragment : BaseEditBikeDeviceFragment() {

    companion object {
        const val TAG = "EditSimpleBikeDeviceFragment"

        @JvmStatic
        fun newInstance(deviceId: Long): EditSimpleBikeDeviceFragment {
            return EditSimpleBikeDeviceFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_DEVICE_ID, deviceId)
                }
            }
        }
    }
}