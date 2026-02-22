package com.atrainingtracker.banalservice.ui.devices.devicelist

import androidx.recyclerview.widget.DiffUtil
import com.atrainingtracker.banalservice.ui.devices.DeviceUiData

sealed class DeviceUiUpdatePayload {
    data class AvailabilityChanged(val isAvailable: Boolean, val lastSeen: String?): DeviceUiUpdatePayload()
    data class MainValueChanged(val mainValue: String?): DeviceUiUpdatePayload()
}

/**
 * The DiffUtil.ItemCallback implementation.
 * This is the magic that allows ListAdapter to efficiently update the list.
 */
class DeviceDiffCallback : DiffUtil.ItemCallback<DeviceUiData>() {
    override fun areItemsTheSame(oldItem: DeviceUiData, newItem: DeviceUiData): Boolean {
        // IDs are unique, so this is the perfect way to check if two items
        // represent the same object.
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: DeviceUiData, newItem: DeviceUiData): Boolean {
        // Check if the content of the items has changed. If this returns false,
        // onBindViewHolder will be called to redraw the item.
        // The 'data class' automatically generates a correct .equals() for this.
        return oldItem == newItem
    }

    override fun getChangePayload(oldItem: DeviceUiData, newItem: DeviceUiData): Any? {
        val payloads = mutableListOf<DeviceUiUpdatePayload>()

        // isAvailable or lastSeen
        if (oldItem.isAvailable != newItem.isAvailable
            || oldItem.lastSeen != newItem.lastSeen) {
            payloads.add(DeviceUiUpdatePayload.AvailabilityChanged(newItem.isAvailable, newItem.lastSeen))
        }

        // mainValue
        if (oldItem.mainValue != newItem.mainValue) {
            payloads.add(DeviceUiUpdatePayload.MainValueChanged(newItem.mainValue))
        }

        // If the list of payloads is not empty, return it.
        // Otherwise, return null to trigger a full re-bind as a fallback.
        return payloads.ifEmpty { null }
    }
}