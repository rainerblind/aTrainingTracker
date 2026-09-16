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

import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import androidx.core.view.WindowCompat
import androidx.fragment.app.DialogFragment
import com.atrainingtracker.R

/**
 * Standardized base [DialogFragment] container hosting Jetpack Compose modal bottom sheets ([AppModalBottomSheet]).
 *
 * Architectural Role:
 * - Solves the navigation bar flicker and color flashes when dismissing bottom popups via system back button
 *   or predictive back gesture by guaranteeing edge-to-edge transparent system bar decor across the hosting
 *   [android.app.Dialog] window.
 * - Applies [R.style.ThemeOverlay_aTrainingTracker_BottomSheetDialogFragment] in [onCreate] so the Android
 *   WindowManager creates the dialog surface with transparent system bars and zero window animations from frame zero.
 * - In [onStart], defensively configures the window to [ViewGroup.LayoutParams.MATCH_PARENT], transparent background,
 *   [WindowCompat.setDecorFitsSystemWindows] false, transparent status and navigation bars, disabled contrast
 *   enforcement on Android 10+ (API 29+), and disables window-level transition animations so Compose's
 *   [androidx.compose.material3.ModalBottomSheet] sheet animator remains the sole authoritative transition.
 *
 * Threading & Lifecycle:
 * - Executed strictly on the Android Main (UI) thread within Fragment lifecycle callbacks.
 */
abstract class AppBottomSheetDialogFragment : DialogFragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.ThemeOverlay_aTrainingTracker_BottomSheetDialogFragment)
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.let { window ->
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
                window.isStatusBarContrastEnforced = false
            }
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.isAppearanceLightStatusBars = true
            insetsController.isAppearanceLightNavigationBars = true
            window.setWindowAnimations(0)
        }
    }
}
