package com.atrainingtracker.banalservice.ui.sporttype

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme

class SportTypeListFragment : Fragment() {

    private val viewModel: SportTypeViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireContext()).apply {
            setContent {
                ATrainingTrackerTheme {
                    SportTypeScreen(
                        viewModel = viewModel,
                        onEdit = { id -> showEditSportTypeDialog(id) }
                    )
                }
            }
        }
    }

    private fun showEditSportTypeDialog(id: Long) {
        val editDialog = EditSportTypeDialog.newInstance(id)
        editDialog.show(parentFragmentManager, EditSportTypeDialog.TAG)
    }

    // Keep the Receiver for now if EditSportTypeDialog still uses Broadcasts
    private val sportTypeChangedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            viewModel.loadSportTypes()
        }
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter(EditSportTypeDialog.SPORT_TYPE_CHANGED_INTENT)
        ContextCompat.registerReceiver(requireContext(), sportTypeChangedReceiver, filter,
            ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onPause() {
        super.onPause()
        requireContext().unregisterReceiver(sportTypeChangedReceiver)
    }

    companion object {
        val TAG: String = SportTypeListFragment::class.java.name
    }
}