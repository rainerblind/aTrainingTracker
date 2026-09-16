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

package com.atrainingtracker.trainingtracker.ui.settings.dropbox

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.ui.components.core.AppBottomSheetDialogFragment
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.dropbox.core.android.Auth

/**
 * DialogFragment hosting the modernized [DropboxSettingsDialog] composable modal bottom sheet.
 *
 * Architectural Role:
 * - Bridges the Android Fragment lifecycle and navigation hierarchy with the Material 3 Compose dialog.
 * - Inherits [AppBottomSheetDialogFragment] to render edge-to-edge transparent system bars without flicker.
 * - Captures OAuth PKCE credentials upon resumption via [onResume] when returning from the browser flow.
 */
class DropboxSettingsDialogFragment : AppBottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                ATrainingTrackerTheme {
                    DropboxSettingsDialog(
                        onDismiss = { dismiss() }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val dbxCredential = Auth.getDbxCredential()
        if (dbxCredential != null) {
            TrainingApplication.storeDropboxCredential(dbxCredential)
            TrainingApplication.setUploadToDropbox(true)
        }
    }

    companion object {
        @JvmField
        val TAG = "DropboxSettingsDialogFragment"

        @JvmStatic
        fun newInstance() = DropboxSettingsDialogFragment()
    }
}
