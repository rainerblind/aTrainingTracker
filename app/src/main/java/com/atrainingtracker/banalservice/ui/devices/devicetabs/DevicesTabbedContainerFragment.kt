package com.atrainingtracker.banalservice.ui.devices.devicetabs

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BANALService
import com.atrainingtracker.banalservice.Protocol
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.dialogs.InstallANTShitDialog
import com.atrainingtracker.banalservice.ui.devices.devicetypechoice.DeviceTypeChoiceArrayAdapter
import com.atrainingtracker.databinding.FragmentTabbedDevicesBinding // Import generated ViewBinding class
import com.google.android.material.tabs.TabLayoutMediator

/**
 * A container fragment that hosts a ViewPager with tabs for each several device lists (available, paired, all known).
 */
class DevicesTabbedContainerFragment: Fragment() {

    private var _binding: FragmentTabbedDevicesBinding? = null
    private val binding get() = _binding!!

    private lateinit var protocol: Protocol
    private var deviceType: DeviceType? = null

    private var isShowingDialog = false
    private var isSearching = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (DEBUG) Log.w(TAG, "onCreate()")

        // Extract arguments
        val args = requireArguments()
        protocol = Protocol.valueOf(args.getString(BANALService.PROTOCOL)!!)

        // Restore DeviceType from arguments or savedInstanceState
        deviceType = args.getString(BANALService.DEVICE_TYPE)?.let { DeviceType.valueOf(it) }
            ?: savedInstanceState?.getString(BANALService.DEVICE_TYPE)?.let { DeviceType.valueOf(it) }

        if (DEBUG) Log.w(TAG, "protocol=$protocol, DeviceType=$deviceType")
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        if (DEBUG) Log.w(TAG, "onCreateView()")
        _binding = FragmentTabbedDevicesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Options menu is only for ANT
        if (protocol == Protocol.ANT_PLUS) {
            setHasOptionsMenu(true)
        }
    }

    override fun onResume() {
        super.onResume()
        if (DEBUG) Log.w(TAG, "onResume()")

        if (deviceType != null) {
            // If we already know the device type, set up the UI immediately
            setupViewPagerAndTabs()
            startSearching()
        } else if (!isShowingDialog) {
            // Otherwise, show the dialog to select a device type
            showDeviceTypeSelectionDialog()
        }
    }

    private fun showDeviceTypeSelectionDialog() {
        isShowingDialog = true
        val deviceTypeList = DeviceType.getRemoteDeviceTypes(protocol).toList()

        AlertDialog.Builder(requireContext()).apply {
            setIcon(protocol.getIconId())
            setTitle(R.string.select_device_type)
            setNegativeButton(R.string.Cancel) { dialog, _ ->
                dialog.dismiss()
                isShowingDialog = false
                // TODO: navigate back
                requireActivity().supportFragmentManager.popBackStack()
            }
            val adapter = DeviceTypeChoiceArrayAdapter(requireActivity(), deviceTypeList, protocol)
            setAdapter(adapter) { dialog, which ->
                // User made a selection
                deviceType = deviceTypeList[which]
                isShowingDialog = false
                dialog.dismiss()
                // Now setup the UI and start searching
                setupViewPagerAndTabs()
                startSearching()
            }
        }.show()
    }

    private fun setupViewPagerAndTabs() {
        // Ensure this is only run once and with a valid deviceType
        if (binding.pager.adapter != null || deviceType == null) {
            if (DEBUG) Log.w(TAG, "setupViewPagerAndTabs: Aborting, adapter already set or deviceType is null.")
            return
        }
        if (DEBUG) Log.w(TAG, "setupViewPagerAndTabs for $deviceType")

        val pagerAdapter = DeviceListPagerAdapter(
            childFragmentManager,
            lifecycle,
            requireContext(),
            protocol,
            deviceType!!
        )
        binding.pager.adapter = pagerAdapter

        // Link the TabLayout with the ViewPager
        TabLayoutMediator(binding.tabs, binding.pager) { tab, position ->
            tab.text = pagerAdapter.getPageTitle(position)
        }.attach()
    }


    override fun onPause() {
        super.onPause()
        if (DEBUG) Log.w(TAG, "onPause")
        stopSearching()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (DEBUG) Log.w(TAG, "onSaveInstanceState")
        // No need to save protocol as it's in arguments
        deviceType?.let { outState.putString(BANALService.DEVICE_TYPE, it.name) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null // Important for memory leak prevention
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        if (DEBUG) Log.d(TAG, "onCreateOptionsMenu")
        inflater.inflate(R.menu.remote_devices, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (DEBUG) Log.w(TAG, "onOptionsItemSelected")
        return when (item.itemId) {
            R.id.itemCheckANTInstallation -> {
                InstallANTShitDialog().show(parentFragmentManager, InstallANTShitDialog.TAG)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun startSearching() {
        // Don't start if we are already searching or don't have a deviceType
        if (isSearching || deviceType == null) return
        if (DEBUG) Log.w(TAG, "startSearching for $deviceType")

        val intent = Intent(BANALService.START_SEARCHING_FOR_NEW_DEVICES_INTENT).apply {
            putExtra(BANALService.PROTOCOL, protocol.name)
            putExtra(BANALService.DEVICE_TYPE, deviceType!!.name)
            setPackage(requireActivity().packageName)
        }
        requireActivity().sendBroadcast(intent)
        isSearching = true
    }

    private fun stopSearching() {
        if (!isSearching) return
        if (DEBUG) Log.w(TAG, "stopSearching")

        val intent = Intent(BANALService.STOP_SEARCHING_FOR_NEW_DEVICES_INTENT).apply {
            setPackage(requireActivity().packageName)
        }
        requireActivity().sendBroadcast(intent)
        isSearching = false
    }

    companion object {
        const val TAG = "DevicesTabContainerFragment"
        private const val DEBUG = true // BANALService.getDebug(false)

        @JvmStatic
        fun newInstance(protocol: Protocol, deviceType: DeviceType? = null): DevicesTabbedContainerFragment {
            return DevicesTabbedContainerFragment().apply {
                arguments = Bundle().apply {
                    putString(BANALService.PROTOCOL, protocol.name)
                    // Only add deviceType to arguments if it's not null
                    deviceType?.let { putString(BANALService.DEVICE_TYPE, it.name) }
                }
            }
        }
    }
}