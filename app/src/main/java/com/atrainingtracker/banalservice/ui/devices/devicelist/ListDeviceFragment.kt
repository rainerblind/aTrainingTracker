package com.atrainingtracker.banalservice.ui.devices.devicelist


import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.ui.devices.DevicesViewModel
import com.atrainingtracker.banalservice.ui.devices.editdevice.EditBikeDeviceFragment
import com.atrainingtracker.banalservice.ui.devices.editdevice.BaseEditDeviceFragment
import com.atrainingtracker.banalservice.ui.devices.editdevice.EditRunDeviceFragment
import com.atrainingtracker.databinding.FragmentDeviceListBinding

class ListDeviceFragment : Fragment() {

    private val viewModel: DevicesViewModel by viewModels()


    private lateinit var filterSpec: DeviceFilterSpec
    private lateinit var binding: FragmentDeviceListBinding
    private lateinit var deviceAdapter: ListDeviceAdapter

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
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        deviceAdapter = ListDeviceAdapter(
            onPairClick = { device ->
                // TODO: viewModel.setDevicePaired(device.id, !device.isPaired)
            },
            onItemClick = { device ->
                viewModel.onDeviceSelected(device.id)
            }
        )
        binding.recyclerViewDevices.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = deviceAdapter
        }
    }

    private fun observeViewModel() {
        // This is the magic! Get the specific, filtered LiveData stream from the shared ViewModel.
        viewModel.getFilteredDevices(filterSpec).observe(viewLifecycleOwner) { devices ->
            // The LiveData provides a list that is already perfectly filtered.
            // Just submit it to the adapter.
            deviceAdapter.submitList(devices)

            // Optional: Show a "no devices" message if the list is empty
            binding.textViewEmptyList.visibility = if (devices.isEmpty()) View.VISIBLE else View.GONE
        }

        // Observer for the navigation event
        viewModel.navigateToEditDevice.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { navEvent ->
                // Decide which fragment to show based on the device type
                val editDeviceDialog: DialogFragment = when (navEvent.deviceType) {
                    DeviceType.BIKE_POWER,
                    DeviceType.BIKE_SPEED,
                    DeviceType.BIKE_CADENCE,
                    DeviceType.BIKE_SPEED_AND_CADENCE -> {
                        EditBikeDeviceFragment.newInstance(navEvent.deviceId)
                    }

                    DeviceType.RUN_SPEED -> { // Assuming you might have this
                        EditRunDeviceFragment.newInstance(navEvent.deviceId)
                    }

                    else -> {
                        // A fallback for generic or unsupported types
                        EditRunDeviceFragment.newInstance(navEvent.deviceId)
                    }
                }

                // Show the correct dialog. The tag can be generic.
                editDeviceDialog.show(parentFragmentManager, "EditDeviceDialog")
            }
        }
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