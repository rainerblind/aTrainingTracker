package com.atrainingtracker.banalservice.ui.devices.devicelist


import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.helpers.UIHelper
import com.atrainingtracker.banalservice.ui.devices.DeviceUiData
import com.atrainingtracker.banalservice.ui.devices.editdevice.EditDeviceFragmentFactory
import com.atrainingtracker.databinding.FragmentDeviceListBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class ListDeviceFragment : Fragment() {

    private val viewModel: DeviceListViewModel by viewModels()


    private lateinit var filterSpec: DeviceFilterSpec
    private lateinit var binding: FragmentDeviceListBinding
    private lateinit var listDeviceAdapter: ListDeviceAdapter
    private var longClickedDevice: DeviceUiData? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Retrieve the filter spec passed in via newInstance()
        arguments?.let {
            filterSpec = it.getParcelable(ARG_FILTER_SPEC)
                ?: throw IllegalStateException("FilterSpec must be provided")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentDeviceListBinding.inflate(inflater, container, false)

        // hide or show the search layout based on the filter spec
        if (filterSpec.deviceType == DeviceType.ALL
            || filterSpec.filterType != DeviceFilterType.AVAILABLE) {
            binding.llSearchLayout.visibility = View.GONE
        }
        else {
            binding.llSearchLayout.visibility = View.VISIBLE
            binding.tvSearchingForRemoteDevice.text = getString(
                R.string.devices_searchingForDevice,
                getString(UIHelper.getNameId(filterSpec.protocol)),
                getString(UIHelper.getNameId(filterSpec.deviceType))
            )
        }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        observeViewModel()

        registerForContextMenu(binding.recyclerViewDevices)
    }

    private fun setupRecyclerView() {
        listDeviceAdapter = ListDeviceAdapter(
            onPairClick = { device ->
                viewModel.onPairedChanged(device.id, !device.isPaired)
            },
            onItemClick = { device ->
                viewModel.onDeviceSelected(device.id)
            },
            onLongClick = { device ->
                longClickedDevice = device
            }
        )
        binding.recyclerViewDevices.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = listDeviceAdapter
        }
    }

    private fun observeViewModel() {
        // This is the magic! Get the specific, filtered LiveData stream from the shared ViewModel.
        viewModel.getFilteredDevices(filterSpec).observe(viewLifecycleOwner) { devices ->
            // The LiveData provides a list that is already perfectly filtered.
            // Just submit it to the adapter.
            listDeviceAdapter.submitList(devices)

            // Optional: Show a "no devices" message if the list is empty
            binding.textViewEmptyList.visibility = if (devices.isEmpty()) View.VISIBLE else View.GONE
        }

        // Observer for the navigation event
        viewModel.navigateToEditDevice.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { navEvent ->
                val editDeviceDialog = EditDeviceFragmentFactory.create(
                    deviceId = navEvent.deviceId,
                    deviceType = navEvent.deviceType
                )

                // Show the dialog returned by the factory
                editDeviceDialog.show(parentFragmentManager, "EditDeviceDialog")
            }
        }
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        // Get the stored device directly
        val deviceToDelete = longClickedDevice ?: return super.onContextItemSelected(item)

        return when (item.itemId) {
            R.id.action_delete_device -> {
                // Show a confirmation dialog before deleting
                showDeleteConfirmationDialog(deviceToDelete)
                true // We have handled this menu item click
            }
            else -> super.onContextItemSelected(item)
        }
    }

    private fun showDeleteConfirmationDialog(device: DeviceUiData) {
        // Use MaterialAlertDialogBuilder for modern styling
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.devices_dialog_delete_device_title)
            .setMessage(getString(R.string.devices_dialog_delete_device_message, device.deviceName))
            .setNegativeButton(R.string.cancel) { dialog, _ ->
                dialog.dismiss()
            }
            .setPositiveButton(R.string.delete) { _, _ ->
                viewModel.deleteDevice(device.id)
            }
            .show()
    }

    companion object {
        private const val ARG_FILTER_SPEC = "filter_spec"

        // The factory method to create new instances of this fragment
        @JvmStatic
        fun newInstance(filterSpec: DeviceFilterSpec) =
            ListDeviceFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_FILTER_SPEC, filterSpec)
                }
            }
    }
}