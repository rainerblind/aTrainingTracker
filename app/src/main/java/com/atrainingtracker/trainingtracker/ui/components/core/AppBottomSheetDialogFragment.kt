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

package com.atrainingtracker.trainingtracker.ui.components.core

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.core.view.WindowCompat
import com.atrainingtracker.R
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * Standardized single-window base [BottomSheetDialogFragment] container hosting Jetpack Compose bottom sheets.
 *
 * Architectural Role:
 * - Eliminates double-dialog window nesting by directly extending [BottomSheetDialogFragment] and hosting
 *   [AppBottomSheetContent] inside a single [BottomSheetDialog] window.
 * - Resolves status bar pitch-black rendering by configuring [WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS],
 *   transparent system bars, and disabled contrast enforcement on Android 10+ (API 29+).
 * - Resolves navigation bar dismiss transition flicker by providing seamless edge-to-edge window decor
 *   governed by the single Material bottom sheet window and behavior.
 * - Applies [R.style.ThemeOverlay_aTrainingTracker_BottomSheetDialogFragment] in [onCreate].
 * - In [onStart], expands [BottomSheetBehavior], skips collapsed state, and ensures transparent sheet background.
 *
 * Threading & Lifecycle:
 * - Executed strictly on the Android Main (UI) thread within Fragment lifecycle callbacks.
 */
abstract class AppBottomSheetDialogFragment : BottomSheetDialogFragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.ThemeOverlay_aTrainingTracker_BottomSheetDialogFragment)
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.let { window ->
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.statusBarColor = Color.TRANSPARENT
            window.navigationBarColor = Color.TRANSPARENT
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
                window.isStatusBarContrastEnforced = false
            }
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.isAppearanceLightStatusBars = true
            insetsController.isAppearanceLightNavigationBars = true
        }

        (dialog as? BottomSheetDialog)?.let { bottomSheetDialog ->
            bottomSheetDialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.let { sheet ->
                sheet.setBackgroundColor(Color.TRANSPARENT)
                sheet.setPadding(0, 0, 0, 0)
                androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(sheet) { _, insets -> insets }
            }
            bottomSheetDialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
            bottomSheetDialog.behavior.skipCollapsed = true
        }
    }
}
