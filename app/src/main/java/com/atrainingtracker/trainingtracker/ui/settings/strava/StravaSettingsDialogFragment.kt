/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see https://www.gnu.org/licenses/gpl-3.0
 */

package com.atrainingtracker.trainingtracker.ui.settings.strava

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.DialogFragment
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme

/**
 * DialogFragment hosting the modernized [StravaSettingsDialog] composable modal bottom sheet.
 *
 * Architectural Role:
 * - Bridges Android Fragment navigation hierarchy with the Material 3 Compose dialog.
 * - Handles translucent window styling to preserve the underlying activity screen under the modal scrim.
 */
class StravaSettingsDialogFragment : DialogFragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, android.R.style.Theme_Translucent_NoTitleBar)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                ATrainingTrackerTheme {
                    StravaSettingsDialog(
                        onDismiss = { dismiss() }
                    )
                }
            }
        }
    }

    companion object {
        @JvmField
        val TAG = "StravaSettingsDialogFragment"

        @JvmStatic
        fun newInstance() = StravaSettingsDialogFragment()
    }
}
