/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.atrainingtracker.trainingtracker.ui.settings.tuning

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import com.atrainingtracker.trainingtracker.ui.components.core.AppBottomSheetDialogFragment
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme

/**
 * A DialogFragment that hosts the modern Composable AdvancedTuningDialog.
 * Inherits [AppBottomSheetDialogFragment] to render edge-to-edge transparent system bars without flicker.
 */
class AdvancedTuningDialogFragment : AppBottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                ATrainingTrackerTheme {
                    AdvancedTuningDialog(
                        onDismiss = { dismiss() }
                    )
                }
            }
        }
    }

    companion object {
        @JvmField
        val TAG = "AdvancedTuningDialogFragment"

        @JvmStatic
        fun newInstance() = AdvancedTuningDialogFragment()
    }
}
