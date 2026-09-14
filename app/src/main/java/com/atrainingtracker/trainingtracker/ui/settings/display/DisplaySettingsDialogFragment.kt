/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.atrainingtracker.trainingtracker.ui.settings.display

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.DialogFragment
import com.atrainingtracker.trainingtracker.activities.MainActivityWithNavigation
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme

/**
 * A DialogFragment that hosts the modern Composable DisplaySettingsDialog.
 * This allows triggering the display settings directly from the navigation drawer.
 */
class DisplaySettingsDialogFragment : DialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                ATrainingTrackerTheme {
                    DisplaySettingsDialog(
                        onDismiss = { dismiss() },
                        onSettingsChanged = {
                            (activity as? MainActivityWithNavigation)?.applyDisplaySettings()
                        }
                    )
                }
            }
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        (activity as? MainActivityWithNavigation)?.applyDisplaySettings()
    }

    companion object {
        @JvmField
        val TAG = "DisplaySettingsDialogFragment"
        
        @JvmStatic
        fun newInstance() = DisplaySettingsDialogFragment()
    }
}
