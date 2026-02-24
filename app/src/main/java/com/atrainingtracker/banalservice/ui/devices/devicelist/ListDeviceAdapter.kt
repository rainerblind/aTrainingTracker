package com.atrainingtracker.banalservice.ui.devices.devicelist

import android.view.ContextMenu
import android.view.LayoutInflater
import android.view.MenuInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.ui.devices.devicedata.DeviceUiData
import com.atrainingtracker.databinding.ItemDeviceListBinding




class ListDeviceAdapter(
    private val onPairClick: (DeviceUiData) -> Unit,
    private val onItemClick: (DeviceUiData) -> Unit,
    private val onLongClick: (DeviceUiData) -> Unit
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
        holder.bind(device, onPairClick, onItemClick, onLongClick)
    }

    override fun onBindViewHolder(
        holder: DeviceViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.isEmpty()) {
            // If there are no payloads, this is a full bind.
            // Let the default onBindViewHolder handle it.
            super.onBindViewHolder(holder, position, payloads)
        } else {
            // Payloads are present, so we can perform one or more partial updates.
            // The payload from our DiffUtil is expected to be a List<DeviceUiUpdatePayload>.
            payloads.forEach { payload ->
                if (payload is List<*>) {
                    // Iterate through the actual change events inside the list.
                    payload.forEach { item ->
                        when (item) {
                            is DeviceUiUpdatePayload.AvailabilityChanged -> {
                                holder.updateAvailability(item.isAvailable, item.lastSeen)
                            }
                            is DeviceUiUpdatePayload.MainValueChanged -> {
                                holder.updateMainValue(item.mainValue)
                            }
                        }
                    }
                }
            }

        }
    }

    /**
     * The ViewHolder class. It holds references to the views for a single item
     * and binds the data to those views.
     */
    class DeviceViewHolder(private val binding: ItemDeviceListBinding) :
        RecyclerView.ViewHolder(binding.root),
        View.OnCreateContextMenuListener {

        init {
            itemView.setOnCreateContextMenuListener(this)
        }

        override fun onCreateContextMenu(
            menu: ContextMenu,
            v: View,
            menuInfo: ContextMenu.ContextMenuInfo?
        ) {
            // Inflate the menu resource. The Fragment will handle the click event.
            val inflater = MenuInflater(v.context)
            inflater.inflate(R.menu.menu_edit_device_context, menu)
        }

        fun bind(
            device: DeviceUiData,
            onPairClick: (DeviceUiData) -> Unit,
            onItemClick: (DeviceUiData) -> Unit,
            onLongClick: (DeviceUiData) -> Unit
        ) {
            // Bind all the data from the ListDeviceData object to the views
            binding.deviceName.text = device.deviceName
            binding.deviceManufacturer.text = device.manufacturer
            binding.deviceTypeIcon.setImageResource(device.deviceTypeIconRes)
            binding.batteryStatusIcon.setImageResource(device.batteryStatusIconRes)

            updateMainValue(device.mainValue)

            if (device.linkedEquipment.isEmpty()) {
                binding.linkedEquipment.visibility = View.GONE
            } else {
                binding.linkedEquipment.text = itemView.context.getString(
                    R.string.devices_on_short_format,
                    device.linkedEquipment.joinToString(", ")
                )
            }

            // set the availability icon and last seen / activated
            updateAvailability(device.isAvailable, device.lastSeen)

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

            itemView.setOnLongClickListener {
                onLongClick(device)
                // Return false to allow the context menu creation to proceed
                false
            }

            binding.buttonPair.setOnClickListener {
                onPairClick(device)
            }
        }

        fun updateMainValue(mainValue: String?) {
            binding.mainValue.text = itemView.context.getString(
                R.string.devices_main_value_format,
                mainValue ?: itemView.context.getString(R.string.devices_no_main_value)
            )
        }

        fun updateAvailability(isAvailable: Boolean, lastSeen: String?) {
            if (isAvailable) {
                binding.availableIcon.setImageResource(R.drawable.ic_device_available)
                binding.lastSeen.visibility = View.GONE
            }
            else {
                binding.availableIcon.setImageResource(R.drawable.ic_device_not_available)

                // show when device was seen the last time
                if (lastSeen.isNullOrEmpty()) {
                    // If there's no date, hide the TextView completely
                    binding.lastSeen.visibility = View.GONE
                }
                else {
                    // If there is a date, make it visible and set the formatted text
                    binding.lastSeen.visibility = View.VISIBLE

                    // Split the string by space and take the first part (the date)
                    binding.lastSeen.text = lastSeen.split(" ").firstOrNull() ?: lastSeen
                }
            }
        }
    }
}