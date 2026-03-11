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
                    // The Screen now handles its own state for adding/editing/deleting
                    SportTypeScreen(viewModel = viewModel)
                }
            }
        }
    }

    companion object {
        val TAG: String = SportTypeListFragment::class.java.name

        @JvmStatic
        fun newInstance(): SportTypeListFragment {
            return SportTypeListFragment()
        }
    }
}