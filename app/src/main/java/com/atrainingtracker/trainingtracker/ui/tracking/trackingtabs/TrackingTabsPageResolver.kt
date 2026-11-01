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

package com.atrainingtracker.trainingtracker.ui.tracking.trackingtabs

import com.atrainingtracker.trainingtracker.ui.tracking.ScreenMode

/**
 * Pure domain resolver for initial pager page calculation across screen mode transitions,
 * activity type switching, and tab list mutations (REQ-UI-435, TST-UI-435, ATT-4838).
 *
 * Pager Indexing Contracts:
 * 1. [ScreenMode.TRACKING]: Page 0 is the Control Tab, Page k (k >= 1) is Sensor Tab k - 1.
 * 2. [ScreenMode.CONFIGURATION] & [ScreenMode.PREVIEW]: No Control Tab. Page k is Sensor Tab k.
 * 3. Entering CONFIGURATION from TRACKING or changing configured sport MUST always reset to 0 (1st configured tab).
 * 4. Toggling CONFIGURATION <-> PREVIEW MUST preserve the active sensor tab index.
 * 5. Exiting PREVIEW -> TRACKING maps sensor tab p to tracking cockpit page p + 1 (or 0 if tabCount <= 0).
 */
object TrackingTabsPageResolver {

    /**
     * Resolves the target initial page index for `rememberPagerState`.
     *
     * @param previousMode The previous [ScreenMode] before recomposition/transition.
     * @param newMode The current [ScreenMode].
     * @param lastKnownPage The last known settled page index.
     * @param tabCount The number of configured sensor tabs (`trackingViews.size`).
     * @param activityTypeChanged Whether the active or configured activity type changed.
     * @return The resolved initial page index clamped to valid pager bounds.
     */
    fun resolveInitialPage(
        previousMode: ScreenMode,
        newMode: ScreenMode,
        lastKnownPage: Int,
        tabCount: Int,
        activityTypeChanged: Boolean = false
    ): Int {
        if (activityTypeChanged && (newMode == ScreenMode.CONFIGURATION || newMode == ScreenMode.PREVIEW)) {
            return 0
        }

        return when {
            // TRACKING -> CONFIGURATION: Always open 1st configured sensor tab
            previousMode == ScreenMode.TRACKING && newMode == ScreenMode.CONFIGURATION -> 0

            // TRACKING -> PREVIEW: Always open 1st configured sensor tab
            previousMode == ScreenMode.TRACKING && newMode == ScreenMode.PREVIEW -> 0

            // CONFIGURATION <-> PREVIEW: Preserve active sensor tab index
            (previousMode == ScreenMode.CONFIGURATION && newMode == ScreenMode.PREVIEW) ||
            (previousMode == ScreenMode.PREVIEW && newMode == ScreenMode.CONFIGURATION) -> {
                lastKnownPage.coerceIn(0, (tabCount - 1).coerceAtLeast(0))
            }

            // PREVIEW -> TRACKING (or CONFIGURATION -> TRACKING): Map sensor tab p to tracking page p + 1
            (previousMode == ScreenMode.PREVIEW || previousMode == ScreenMode.CONFIGURATION) &&
            newMode == ScreenMode.TRACKING -> {
                if (tabCount <= 0) {
                    0
                } else {
                    (lastKnownPage + 1).coerceIn(0, tabCount)
                }
            }

            // Same mode (e.g. initial launch or tab addition/deletion): clamp to valid page bounds
            newMode == ScreenMode.TRACKING -> {
                lastKnownPage.coerceIn(0, tabCount)
            }
            else -> {
                lastKnownPage.coerceIn(0, (tabCount - 1).coerceAtLeast(0))
            }
        }
    }
}
