package com.atrainingtracker.banalservice.ui.devices.devicelist

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.atrainingtracker.databinding.ItemDeviceListBinding




class ListDeviceAdapter(
    private val onDeleteClick: (ListDeviceData) -> Unit,
    private val onPairClick: (ListDeviceData) -> Unit
) : ListAdapter<ListDeviceData, ListDeviceAdapter.DeviceViewHolder>(DeviceDiffCallback()) {

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
        holder.bind(device, onDeleteClick)
    }

    /**
     * The ViewHolder class. It holds references to the views for a single item
     * and binds the data to those views.
     */
    class DeviceViewHolder(private val binding: ItemDeviceListBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(
            device: ListDeviceData,
            onPairClick: (ListDeviceData) -> Unit
        ) {
            // Bind all the data from the ListDeviceData object to the views
            binding.deviceName.text = device.deviceName
            binding.deviceManufacturer.text = device.manufacturer
            binding.deviceTypeIcon.setImageResource(device.deviceTypeIconRes)
            binding.batteryStatusIcon.setImageResource(device.batteryStatusIconRes)
            binding.mainValue.text = device.mainValue
            binding.linkedEquipment.text = device.linkedEquipment

            binding.buttonPair.setOnClickListener {
                onPairClick(device)
            }

            // Example of how to change the pair button's appearance
            // binding.buttonPair.text = if (device.isPaired) "Unpair" else "Pair"
        }
    }

    /**
     * The DiffUtil.ItemCallback implementation.
     * This is the magic that allows ListAdapter to efficiently update the list.
     */
    class DeviceDiffCallback : DiffUtil.ItemCallback<ListDeviceData>() {
        override fun areItemsTheSame(oldItem: ListDeviceData, newItem: ListDeviceData): Boolean {
            // IDs are unique, so this is the perfect way to check if two items
            // represent the same object.
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ListDeviceData, newItem: ListDeviceData): Boolean {
            // Check if the content of the items has changed. If this returns false,
            // onBindViewHolder will be called to redraw the item.
            // The 'data class' automatically generates a correct .equals() for this.
            return oldItem == newItem
        }
    }
}