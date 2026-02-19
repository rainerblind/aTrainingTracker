package com.atrainingtracker.banalservice.ui.devices.devicelist

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.ui.devices.DeviceUiData
import com.atrainingtracker.databinding.ItemDeviceListBinding




class ListDeviceAdapter(
    private val onPairClick: (DeviceUiData) -> Unit,
    private val onItemClick: (DeviceUiData) -> Unit
) : ListAdapter<DeviceUiData, ListDeviceAdapter.DeviceViewHolder>(DeviceDiffCallback()) {

    /**
     * Called when RecyclerView needs a new ViewHolder. It inflates the item layout.
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val binding = ItemDeviceListBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return DeviceViewHolder(binding)
    }

    /**
     * Called by RecyclerView to display the data at the specified position.
     * This method updates the contents of the ViewHolder to reflect the item at the given position.
     */
    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        val device = getItem(position)
        holder.bind(device, onPairClick, onItemClick)
    }

    /**
     * The ViewHolder class. It holds references to the views for a single item
     * and binds the data to those views.
     */
    class DeviceViewHolder(private val binding: ItemDeviceListBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(
            device: DeviceUiData,
            onPairClick: (DeviceUiData) -> Unit,
            onItemClick: (DeviceUiData) -> Unit
        ) {
            // Bind all the data from the ListDeviceData object to the views
            binding.deviceName.text = device.deviceName
            binding.deviceManufacturer.text = device.manufacturer
            binding.deviceTypeIcon.setImageResource(device.deviceTypeIconRes)
            binding.batteryStatusIcon.setImageResource(device.batteryStatusIconRes)
            binding.mainValue.text = device.mainValue
            if (device.linkedEquipment.isEmpty()) {
                binding.linkedEquipment.visibility = View.GONE
            } else {
                binding.linkedEquipment.text = itemView.context.getString(
                    R.string.devices_on_short_format,
                    device.linkedEquipment.joinToString(", ")
                )
            }

            // show when device was seen the last time
            if (device.lastSeen.isNullOrEmpty()) {
                // If there's no date, hide the TextView completely
                binding.lastSeen.visibility = View.GONE
            } else {
                // If there is a date, make it visible and set the formatted text
                binding.lastSeen.visibility = View.VISIBLE
                val lastSeenText = if (device.isAvailable) {
                    itemView.context.getString(R.string.devices_now)
                }
                else {
                    // Split the string by space and take the first part (the date)
                    device.lastSeen.split(" ").firstOrNull() ?: device.lastSeen
                }
                binding.lastSeen.text = lastSeenText
            }

            // Set the button text and style based on the isPaired property
            if (device.isPaired) {
                binding.buttonPair.text = itemView.context.getString(R.string.devices_button_unpair)
                binding.buttonPair.isActivated = true // Activate the "paired" state in our selectors

            } else {
                binding.buttonPair.text = itemView.context.getString(R.string.devices_button_pair)
                binding.buttonPair.isActivated = false // Use the default state in our selectors
            }

            // adding the click listeners
            itemView.setOnClickListener {
                onItemClick(device)
            }

            binding.buttonPair.setOnClickListener {
                onPairClick(device)
            }

        }
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
    }
}