package com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist

import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.exporter.FileFormat
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import com.atrainingtracker.trainingtracker.ui.components.export.ExportStatusViewHolder
import com.atrainingtracker.trainingtracker.ui.components.map.MapComponent
import com.atrainingtracker.trainingtracker.ui.components.map.MapContentType
import com.atrainingtracker.trainingtracker.ui.components.workoutdescription.DescriptionViewHolder
import com.atrainingtracker.trainingtracker.ui.components.workoutdescription.EditDescriptionDialogFragment
import com.atrainingtracker.trainingtracker.ui.components.workoutdetails.WorkoutDetailsViewHolder
import com.atrainingtracker.trainingtracker.ui.components.workoutextrema.ExtremaValuesViewHolder
import com.atrainingtracker.trainingtracker.ui.components.workoutheader.ChangeSportAndEquipmentDialogFragment
import com.atrainingtracker.trainingtracker.ui.components.workoutheader.EditWorkoutNameDialogFragment
import com.atrainingtracker.trainingtracker.ui.components.workoutheader.WorkoutHeaderViewHolder
import com.google.android.gms.maps.MapView

/**
 * A modern ListAdapter for the workout summaries RecyclerView.
 * It is decoupled from data creation and relies on a pre-composed WorkoutSummary object.
 *
 * @param activity The host activity.
 * @param fragmentManager The FragmentManager from the hosting fragment, used to show dialogs.
 * @param isPlayServiceAvailable A flag to determine if the map component should be initialized.
 * @param viewModel The ViewModel, used to dispatch user events like 'update name'.
 */
class WorkoutSummariesAdapter(
    private val activity: Activity,
    private val fragmentManager: FragmentManager,
    private val lifecycleOwner: LifecycleOwner,
    private val isPlayServiceAvailable: Boolean,
    private val viewModel: WorkoutSummariesViewModel
) : ListAdapter<WorkoutData, WorkoutSummariesAdapter.SummaryViewHolder>(WorkoutDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SummaryViewHolder {
        val row = LayoutInflater.from(parent.context)
            .inflate(R.layout.workout_summaries_row, parent, false)
        // Create the ViewHolder and inject only the dependencies it needs for UI and event handling.
        return SummaryViewHolder(
            row,
            activity,
            fragmentManager,
            lifecycleOwner,
            isPlayServiceAvailable,
            viewModel
        )
    }

    override fun onBindViewHolder(holder: SummaryViewHolder, position: Int) {
        val workoutSummary = getItem(position)
        holder.bind(workoutSummary)
    }

    /**
     * The ViewHolder for a single workout summary row. It contains all the sub-component
     * ViewHolders and is responsible for setting up listeners and binding data to the components.
     */
    class SummaryViewHolder(
        row: View,
        activity: Activity,
        private val fragmentManager: FragmentManager,
        private val lifecycleOwner: LifecycleOwner,
        isPlayServiceAvailable: Boolean,
        private val viewModel: WorkoutSummariesViewModel
    ) : RecyclerView.ViewHolder(row) {

        // --- Component ViewHolders & Components ---
        private val headerViewHolder: WorkoutHeaderViewHolder?
        private val detailsViewHolder: WorkoutDetailsViewHolder?
        private val descriptionViewHolder: DescriptionViewHolder?
        private val extremaValuesViewHolder: ExtremaValuesViewHolder?
        private val exportStatusViewHolder: ExportStatusViewHolder?
        private val mapComponent: MapComponent?

        // The current data for this specific row, set during bind().
        private lateinit var workoutSummary: WorkoutData

        init {
            // --- Find Views ---
            val headerView = row.findViewById<View>(R.id.workout_header_include)
            val detailsView = row.findViewById<View>(R.id.workout_details_include)
            val descriptionView = row.findViewById<View>(R.id.workout_description_include)
            val extremaView = row.findViewById<View>(R.id.extrema_values_include)
            val exportStatusView = row.findViewById<View>(R.id.export_status_include)
            val mapView = row.findViewById<MapView>(R.id.workout_summaries_mapView)

            // Find the menu button inside the header layout
            val menuButton = headerView?.findViewById<View>(R.id.workout_header_menu_button)
            // Call the setup method, now passing the menu button
            setupMenuButtonClickListeners(menuButton)

            // --- Create Component ViewHolders ---
            headerViewHolder = headerView?.let { WorkoutHeaderViewHolder(it) }
            detailsViewHolder = detailsView?.let { WorkoutDetailsViewHolder(it, activity) }
            descriptionViewHolder = descriptionView?.let { DescriptionViewHolder(it) }
            extremaValuesViewHolder = extremaView?.let { ExtremaValuesViewHolder(it) }
            exportStatusViewHolder = exportStatusView?.let { ExportStatusViewHolder(it) }

            // --- Initialize Map Component ---
            mapComponent = if (isPlayServiceAvailable && mapView != null) {
                MapComponent(mapView, activity) { workoutId ->
                    TrainingApplication.startTrackOnMapAftermathActivity(activity, workoutId)
                }
            } else {
                mapView?.visibility = View.GONE
                null
            }

            // --- Setup Listeners (Event Handling) ---
            setupClickListeners()
        }

        private fun setupClickListeners() {
            // This method is called only once, during ViewHolder creation.

            // --- Long-Click Listeners for Editing ---
            headerViewHolder?.workoutNameView?.setOnLongClickListener {
                // Use the stored workoutSummary object for the current data.
                val dialog = EditWorkoutNameDialogFragment.newInstance(workoutSummary.headerData.workoutName)
                dialog.onWorkoutNameChanged = { newName ->
                    // Instead of updating UI directly, we notify the ViewModel of the event.
                    viewModel.updateWorkoutName(workoutSummary.id, newName)
                }
                dialog.show(fragmentManager, "EditWorkoutNameDialogFragment")
                true // Consume the long-click event
            }

            headerViewHolder?.sportContainerView?.setOnLongClickListener {
                val dialog = ChangeSportAndEquipmentDialogFragment.newInstance(
                    workoutSummary.headerData.sportId,
                    workoutSummary.headerData.equipmentName
                )
                dialog.onSave = { newSportId, newEquipmentId ->
                    viewModel.updateSportAndEquipment(workoutSummary.id, newSportId, newEquipmentId)
                }
                dialog.show(fragmentManager, "ChangeSportAndEquipmentDialogFragment")
                true
            }

            descriptionViewHolder?.rootView?.setOnLongClickListener {
                val dialog = EditDescriptionDialogFragment.newInstance(workoutSummary.descriptionData.description, workoutSummary.descriptionData.goal, workoutSummary.descriptionData.method)
                dialog.onDescriptionChanged = { newDescription, newGoal, newMethod ->
                    viewModel.updateDescription(workoutSummary.id, newDescription, newGoal, newMethod)
                }
                dialog.show(fragmentManager, "EditDescriptionDialogFragment")
                true
            }

            // --- Short-Click Listener for Navigation ---
            val detailsClickListener = View.OnClickListener {
                TrainingApplication.startEditWorkoutActivity(workoutSummary.id, false) // only show the editable fields
            }
            // Attach this listener to multiple views
            headerViewHolder?.view?.setOnClickListener(detailsClickListener)
            headerViewHolder?.sportContainerView?.setOnClickListener(detailsClickListener)
            headerViewHolder?.workoutNameView?.setOnClickListener(detailsClickListener)
            detailsViewHolder?.view?.setOnClickListener(detailsClickListener)
            extremaValuesViewHolder?.view?.setOnClickListener(detailsClickListener)
            descriptionViewHolder?.rootView?.setOnClickListener(detailsClickListener)
        }

        private fun setupMenuButtonClickListeners(menuButton: View?) {
            menuButton?.setOnClickListener { view ->
                // Create a PopupMenu, anchored to the button that was clicked.
                val popup = PopupMenu(view.context, view)
                // Inflate the same menu resource the old fragment used.
                popup.inflate(R.menu.workout_summaries_context)

                // Set a listener for when a menu item is clicked.
                popup.setOnMenuItemClickListener { item ->
                    // Delegate the action to the ViewModel based on the menu item's ID.
                    // This keeps the adapter clean and dumb.
                    when (item.itemId) {
                        R.id.contextDelete -> {
                            // Let the ViewModel handle the deletion logic.
                            viewModel.onDeleteWorkoutClicked(workoutSummary.id)
                            true // Consume the click
                        }
                        R.id.tcxWrite -> {
                            viewModel.onExportWorkoutClicked(workoutSummary.id, FileFormat.TCX)
                            true
                        }
                        R.id.gpxWrite -> {
                            viewModel.onExportWorkoutClicked(workoutSummary.id, FileFormat.GPX)
                            true
                        }
                        R.id.csvWrite -> {
                            viewModel.onExportWorkoutClicked(workoutSummary.id, FileFormat.CSV)
                            true
                        }
                        R.id.jsonWrite -> {
                            viewModel.onExportWorkoutClicked(workoutSummary.id, FileFormat.GC)
                            true
                        }
                        R.id.stravaUpload -> {
                            viewModel.onExportWorkoutClicked(workoutSummary.id, FileFormat.STRAVA)
                            true
                        }
                        // TODO: runkeeper, trainingPeaks, ...
                        else -> false // Let the system handle other cases
                    }
                }
                // Show the menu.
                popup.show()
            }
        }

        /**
         * Binds a pre-composed WorkoutSummary object to the views. This is called for each item.
         */
        fun bind(summary: WorkoutData) {
            // Store the summary for use in the click listeners.
            this.workoutSummary = summary

            // --- Pass the pre-made data objects directly to the components ---
            headerViewHolder?.bind(summary.headerData)
            detailsViewHolder?.bind(summary.detailsData)
            descriptionViewHolder?.bind(summary.descriptionData)
            extremaValuesViewHolder?.bind(summary.extremaData, lifecycleOwner)
            exportStatusViewHolder?.bind(summary.fileBaseName)

            // Bind the map component
            mapComponent?.bind(summary.id, MapContentType.WORKOUT_TRACK)
        }
    }

    /**
     * A DiffUtil.ItemCallback to help ListAdapter determine which items in the list have changed.
     * This enables efficient updates and animations.
     */
    class WorkoutDiffCallback : DiffUtil.ItemCallback<WorkoutData>() {
        override fun areItemsTheSame(oldItem: WorkoutData, newItem: WorkoutData): Boolean {
            // The ID is the unique identifier for an item.
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: WorkoutData, newItem: WorkoutData): Boolean {
            // The data class's generated `equals` method compares all properties.
            // If any property is different, the content has changed.
            return oldItem == newItem
        }
    }
}
