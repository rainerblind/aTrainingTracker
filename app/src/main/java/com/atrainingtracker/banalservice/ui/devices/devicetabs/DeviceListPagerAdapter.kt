package com.atrainingtracker.banalservice.ui.devices.devicetabs

import android.content.Context
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.Protocol
import com.atrainingtracker.banalservice.devices.DeviceType
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
        return when (filters[position]) {
            DeviceFilterType.AVAILABLE -> context.getString(R.string.available_devices)
            DeviceFilterType.PAIRED -> context.getString(R.string.paired_devices)
            DeviceFilterType.ALL_KNOWN -> context.getString(R.string.known_devices)
            else -> null
        }
    }
}