package com.atrainingtracker.banalservice.ui.devices.devicetabs

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
import androidx.fragment.app.viewModels
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BANALService
import com.atrainingtracker.banalservice.Protocol
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.dialogs.InstallANTShitDialog
import com.atrainingtracker.databinding.FragmentTabbedDevicesBinding // Import generated ViewBinding class
import com.google.android.material.tabs.TabLayoutMediator

/**
 * A container fragment that hosts a ViewPager with tabs for each several device lists (available, paired, all known).
 * UI logic is driven by observing state from DevicesTabbedViewModel.
 */
class DevicesTabbedContainerFragment : Fragment() {

    private var _binding: FragmentTabbedDevicesBinding? = null
    private val binding get() = _binding!!

    // Initialize the ViewModel using the KTX delegate. It will be automatically created and retained.
    private val viewModel: DevicesTabbedViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTabbedDevicesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // The options menu is only for ANT+
        if (viewModel.protocol == Protocol.ANT_PLUS) {
            setHasOptionsMenu(true)
        }

        // Observe the UI state from the ViewModel. The UI will automatically react to state changes.
        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is UiState.AwaitingDeviceTypeSelection -> showDeviceTypeSelectionDialog()
                is UiState.DisplayingTabs -> {
                    setupViewPagerAndTabs(state.deviceType)
                    viewModel.startSearching()
                }
            }
        }
    }

    private fun showDeviceTypeSelectionDialog() {
        // Check if a dialog is already showing to prevent duplicates on configuration change
        if (parentFragmentManager.findFragmentByTag("DeviceTypeChoiceDialog") != null) return

        val deviceTypeList = DeviceType.getRemoteDeviceTypes(viewModel.protocol).toList()

        val dialog = AlertDialog.Builder(requireContext()).apply {
            setIcon(viewModel.protocol.getIconId())
            setTitle(R.string.select_device_type)
            setPositiveButton(R.string.devices_all){ _dialog, _ ->
                _dialog.dismiss()
                viewModel.onDeviceTypeSelected(DeviceType.ALL)
            }
            val adapter = DeviceTypeChoiceArrayAdapter(requireActivity(), deviceTypeList, viewModel.protocol)
            setAdapter(adapter) { dialog, which ->
                // User made a selection. Notify the ViewModel. The UI will update automatically.
                viewModel.onDeviceTypeSelected(deviceTypeList[which])
                dialog.dismiss()
            }
        }.create()

        dialog.setOnCancelListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        dialog.show()
    }

    private fun setupViewPagerAndTabs(deviceType: DeviceType) {
        // Prevent re-initializing the adapter if it's already set
        if (binding.pager.adapter != null) return

        if (DEBUG) Log.w(TAG, "setupViewPagerAndTabs for $deviceType")

        val pagerAdapter = DeviceListPagerAdapter(
            childFragmentManager,
            lifecycle,
            requireContext(),
            viewModel.protocol,
            deviceType
        )
        binding.pager.adapter = pagerAdapter

        // Link the TabLayout with the ViewPager
        TabLayoutMediator(binding.tabs, binding.pager) { tab, position ->
            tab.text = pagerAdapter.getPageTitle(position)
        }.attach()
    }

    override fun onPause() {
        super.onPause()
        viewModel.stopSearching()
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

    companion object {
        const val TAG = "DevicesTabContainer"
        private const val DEBUG = true // BANALService.getDebug(true)

        @JvmStatic
        fun newInstance(protocol: Protocol, deviceType: DeviceType? = null): DevicesTabbedContainerFragment {
            return DevicesTabbedContainerFragment().apply {
                arguments = Bundle().apply {
                    putString(BANALService.PROTOCOL, protocol.name)
                    // Pass deviceType via arguments. SavedStateHandle will pick it up automatically.
                    deviceType?.let { putString(BANALService.DEVICE_TYPE, it.name) }
                }
            }
        }
    }
}