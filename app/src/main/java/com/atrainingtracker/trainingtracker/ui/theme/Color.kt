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

package com.atrainingtracker.trainingtracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.atrainingtracker.trainingtracker.ui.WorkoutNavigationEvents

object TTAlpha {
    const val High = 1.0f
    const val Overlay = 0.9f
    const val Medium = 0.7f
    const val SemiTransparent = 0.5f
    const val Disabled = 0.38f
    const val Subtle = 0.12f
    const val Ghost = 0.1f
    const val Faint = 0.05f
}

// Light Theme Colors from color.xml
val LightPrimary = Color(0xFF1464F4)
val LightOnPrimary = Color(0xFFFFFFFF)
val BabyBlueEye = Color(0xFFA1CAF1) // Baby Blue Eye
val BabyBlueEyeInverse = Color(0xFF001A41) // Suggested Material 3 color
val LightSecondary = Color(0xFF0000CD)
val LightOnSecondary = Color(0xFFFFFFFF)
val LightSecondaryContainer = Color(0xFFDEE1F9) // Suggested Material 3 color
val LightOnSecondaryContainer = Color(0xFF161B2C) // Suggested Material 3 color
val LightTertiary = Color(0xFF4169E1) // Using royal_blue as a tertiary
val LightOnTertiary = Color(0xFFFFFFFF)
val LightTertiaryContainer = Color(0xFFD9E2FF) // Suggested
val LightOnTertiaryContainer = Color(0xFF001C3A) // Suggested
val LightError = Color(0xFFB00020)
val LightOnError = Color(0xFFFFFFFF)
val LightErrorContainer = Color(0xFFFFDAD6)
val LightOnErrorContainer = Color(0xFF410002)
val LightBackground = Color.White
val LightOnBackground = Color(0xFF1B1B1F) // Near black
val LightSurface = Color.White
val LightOnSurface = Color(0xFF1B1B1F) // Standard high-contrast text
val LightSurfaceVariant = Color(0xFFE1E2EC) // Suggested
val LightOnSurfaceVariant = Color(0xFF44474F) // Suggested
val LightOutline = Color(0xFF74777F) // Suggested
val LightInverseOnSurface = Color(0xFFF2F0F4) // Suggested
val LightInverseSurface = Color(0xFF2F3033) // Suggested
val LightPrimaryInverse = Color(0xFFA6C8FF) // Suggested
val surfaceDimLight = Color(0xFFCDE2FB)        // Clear Light Blue
val surfaceBrightLight = Color(0xFFF0F7FF)     // "Ice" - Almost white blue
val surfaceContainerLowestLight = Color(0xFFFFFFFF) // Pure White
val surfaceContainerLowLight = Color(0xFFECF4FF)  // Very soft sky blue
val surfaceContainerLight = Color(0xFFE2EFFF)    // Fresh light blue
val surfaceContainerHighLight = Color(0xFFD6E9FF) // Noticeable light blue
val surfaceContainerHighestLight = Color(0xFFC8E3FF) // <--- Use this for headers

// Dark Theme Colors (These are suggested Material 3 colors, you can customize them)
val DarkPrimary = Color(0xFFA6C8FF)
val DarkOnPrimary = Color(0xFF003060)
val DarkPrimaryContainer = Color(0xFF162032)
val DarkOnPrimaryContainer = Color(0xFFD4E3FF)
val DarkSecondary = Color(0xFFC2C5DD)
val DarkOnSecondary = Color(0xFF2B2F42)
val DarkSecondaryContainer = Color(0xFF424659)
val DarkOnSecondaryContainer = Color(0xFFDEE1F9)
val DarkTertiary = Color(0xFFAEC6FF)
val DarkOnTertiary = Color(0xFF002F67)
val DarkTertiaryContainer = Color(0xFF1E468E)
val DarkOnTertiaryContainer = Color(0xFFD9E2FF)
val DarkError = Color(0xFFFFB4AB)
val DarkOnError = Color(0xFF690005)
val DarkErrorContainer = Color(0xFF93000A)
val DarkOnErrorContainer = Color(0xFFFFDAD6)
val DarkBackground = Color(0xFF121214) // Deep neutral canvas (REQ-UI-190, ATT-1585)
val DarkOnBackground = Color(0xFFE3E2E6)
val DarkSurface = Color(0xFF16161A)
val DarkOnSurface = Color(0xFFE3E2E6)
val DarkSurfaceVariant = Color(0xFF44474F)
val DarkOnSurfaceVariant = Color(0xFFC4C6D0)
val DarkOutline = Color(0xFF8E9099)
val DarkOutlineVariant = Color(0xFF2B3342) // Subtle slate outline for card boundaries (REQ-UI-190)
val DarkInverseOnSurface = Color(0xFF1B1B1F)
val DarkInverseSurface = Color(0xFFE3E2E6)
val DarkPrimaryInverse = Color(0xFF3F81D1)
val surfaceDimDark = Color(0xFF141313)
val surfaceBrightDark = Color(0xFF3A3939)
val surfaceContainerLowestDark = Color(0xFF0E0E10)
val surfaceContainerLowDark = Color(0xFF1C2028) // Elevated card surface with slate tone (REQ-UI-190)
val surfaceContainerDark = Color(0xFF20242E)
val surfaceContainerHighDark = Color(0xFF282F3D)
val surfaceContainerHighestDark = Color(0xFF1B2436) // Coordinated slate-blue tab background (REQ-UI-190)

// AMOLED Pure Black Cockpit Theme Colors (ATT-1263 / ATT-1264 / REQ-UI-169 / REQ-UI-171 / REQ-UI-381)
val AmoledBackground = Color(0xFF000000)
val AmoledSurface = Color(0xFF000000)
val AmoledPrimaryContainer = Color(0xFF000000)
val AmoledOnPrimaryContainer = Color(0xFFFFFFFF)
val AmoledSurfaceBright = Color(0xFF1A1A1A)
val AmoledSurfaceContainerHigh = Color(0xFF121212)
val AmoledSurfaceContainerHighest = Color(0xFF000000)
val AmoledOutlineVariant = Color(0xFF262626) // Subtle tile divider border (ATT-1264 / REQ-UI-171)
val AmoledOutline = Color(0xFF38383A)
val AmoledOnSurfaceVariant = Color(0xFF9E9E9E) // Muted grey for labels and units (ATT-1264 / REQ-UI-171)

/**
 * Semantic Warning Color Tokens for peripheral battery and caution notifications (REQ-UI-356, ATT-3451).
 * Specifically designed and verified for WCAG 2.1 AAA contrast compliance (> 7:1) in both Light and Dark modes.
 */
val LightWarning = Color(0xFFE65100)
val LightWarningContainer = Color(0xFFFFE082)   // Warm Amber 200
val LightOnWarningContainer = Color(0xFF3E2723) // Deep Brown (contrast > 10:1 on LightWarningContainer)
val LightWarningAction = Color(0xFF6D3B00)      // Deep Amber (contrast > 7:1 on LightWarningContainer)
val LightWarningBorder = Color(0xFFFFA000)      // Amber 700 for high-contrast light borders (REQ-UI-381)

val DarkWarning = Color(0xFFFFB74D)
val DarkWarningContainer = Color(0xFF4E2C00)    // Deep warm amber-brown
val DarkOnWarningContainer = Color(0xFFFFE082)  // Soft pale amber (contrast > 9:1 on DarkWarningContainer)
val DarkWarningAction = Color(0xFFFFD54F)       // Vibrant Amber 300 (contrast > 8:1 on DarkWarningContainer)

/**
 * Color tuple containing container, content (body text), and action button colors
 * for battery snackbar presentation in Compose Material 3 (REQ-UI-356, ATT-3451).
 *
 * @property containerColor Background tint for the snackbar container.
 * @property contentColor High-contrast foreground color for text/icons.
 * @property actionColor High-contrast foreground color for the actionable button text.
 */
data class BatterySnackbarColors(
    val containerColor: Color,
    val contentColor: Color,
    val actionColor: Color
)

/**
 * Composable color resolver mapping a [WorkoutNavigationEvents.BatteryAlertSeverity]
 * to high-contrast theme-aware [BatterySnackbarColors] conforming to Material 3 design
 * guidelines (REQ-UI-356, ATT-3451).
 *
 * - [WorkoutNavigationEvents.BatteryAlertSeverity.CRITICAL]: Uses errorContainer / onErrorContainer / error.
 * - [WorkoutNavigationEvents.BatteryAlertSeverity.WARNING]: Uses amber warning tokens with WCAG AAA contrast (> 7:1).
 *
 * @param severity The severity tier of the low battery alert.
 * @return The corresponding [BatterySnackbarColors] tailored to the current dark/light theme.
 */
@Composable
fun getBatterySnackbarColors(severity: WorkoutNavigationEvents.BatteryAlertSeverity): BatterySnackbarColors {
    val isDark = isSystemInDarkTheme()
    return when (severity) {
        WorkoutNavigationEvents.BatteryAlertSeverity.CRITICAL -> BatterySnackbarColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            actionColor = MaterialTheme.colorScheme.error
        )
        WorkoutNavigationEvents.BatteryAlertSeverity.WARNING -> if (isDark) {
            BatterySnackbarColors(
                containerColor = DarkWarningContainer,
                contentColor = DarkOnWarningContainer,
                actionColor = DarkWarningAction
            )
        } else {
            BatterySnackbarColors(
                containerColor = LightWarningContainer,
                contentColor = LightOnWarningContainer,
                actionColor = LightWarningAction
            )
        }
    }
}



object TTColor {
    // Achievements
    val Gold = Color(0xFFFFD700)
    val Silver = Color(0xFFC0C0C0)
    val Bronze = Color(0xFFCD7F32)

    // Zone Colors from color.xml
    val Zone1 = Color(0xFF7FFF00) // Chartreuse
    val Zone2 = Color(0xFF008000) // Green
    val Zone3 = Color(0xFFFFA500) // Orange
    val Zone4 = Color(0xFFFF0000) // Red
    val Zone5 = Color(0xFF9400D3) // Dark Violet
    val Zone6 = Color(0xFF4E342E) // Dark Chestnut Brown
    val Zone7 = Color(0xFF000000) // Pure Black

    // Branding / Status
    val StravaOrange = Color(0xFFFC4C02)
    val ConnectionStatusGreen = Color(0xFF4CAF50)

    // Route Visualization
    val RouteSelected = Color(0xFF1565C0) // Material Blue 800 (Royal Blue)
    val RouteUnselected = Color(0xFF90CAF9) // Material Blue 200 (Soft Steel Blue)
    val RouteActiveNavigation = Color(0xFF1E88E5) // Material Blue 600 (Vibrant Sapphire Blue for actively navigated routes)
    val RouteActiveNavigationOverlay = Color(0xFF0D47A1) // Material Blue 900 (Deep Midnight Navy for active route dashed overlay)

    // Spatial Signature Points
    val StartPoint = Color(0xFF2E7D32) // Material Green 800
    val EndPoint = Color(0xFFC62828)   // Material Red 800
    val ApexPoint = Color(0xFF1565C0)  // Material Blue 800
    val MinAltitude = Color(0xFF1565C0) // Material Blue 800 (same as Apex / Max Line Distance)
    val MaxAltitude = Color(0xFF1565C0) // Material Blue 800 (same as Apex / Max Line Distance)

    // Climb Categories (REQ-UI-381)
    val ClimbCategoryHc = Color(0xFF880E4F)
    val ClimbCategory1 = Color(0xFFC62828)
    val ClimbCategory2 = Color(0xFFEF6C00)
    val ClimbCategory3 = Color(0xFFF9A825)
    val ClimbCategory4 = Color(0xFF2E7D32)
    val ClimbCategoryUncategorized = Color(0xFF757575)

    // Climb Gradient Bands (REQ-UI-381)
    val ClimbGradeFlat = Color(0xFF4CAF50)
    val ClimbGradeMild = Color(0xFF8BC34A)
    val ClimbGradeMedium = Color(0xFFFFEB3B)
    val ClimbGradeSteep = Color(0xFFFF9800)
    val ClimbGradeSevere = Color(0xFFF44336)
    val ClimbGradeExtreme = Color(0xFF9C27B0)

    /**
     * Resolves the authoritative elevation profile color for a given climb slope percentage (REQ-UI-381).
     *
     * @param gradePercent The slope gradient expressed as a percentage (e.g. 7.5 for 7.5%).
     * @return The corresponding [Color] token representing the gradient intensity band.
     */
    fun getClimbGradeColor(gradePercent: Double): Color = when {
        gradePercent < 3.0 -> ClimbGradeFlat
        gradePercent < 6.0 -> ClimbGradeMild
        gradePercent < 9.0 -> ClimbGradeMedium
        gradePercent < 12.0 -> ClimbGradeSteep
        gradePercent < 15.0 -> ClimbGradeSevere
        else -> ClimbGradeExtreme
    }

    // Map Canvas & Track Overlays (REQ-UI-381)
    val DarkMapCanvas = Color(0xFF121212)
    val DarkMapTrackCyan = Color(0xFF00E5FF)

    // Geofences & Location Badges (REQ-UI-381)
    val GeofenceFill = Color(0x332196F3)
    val GeofenceStroke = Color(0x882196F3)
    val GeofencePin = Color(0xFF2196F3)
    val LocationBadgeHomeContainer = Color(0xFFE8F5E9)
    val LocationBadgeHomeContent = Color(0xFF2E7D32)
    val LocationBadgeWorkContainer = Color(0xFFFFF8E1)
    val LocationBadgeWorkContent = Color(0xFFF57F17)
    val LocationBadgeOtherContainer = Color(0xFFFFE0B2)
    val LocationBadgeOtherContent = Color(0xFFE65100)

    // Cloud & Connectivity Status (REQ-UI-381)
    val DropboxBlue = Color(0xFF0061FE)
    val ConnectionConnected = Color(0xFF2E7D32)

    // Cockpit Gauges, Controls & Sensor Borders (REQ-UI-381)
    val PauseAmber = Color(0xFFF59E0B)
    val SensorActiveBorder = Color(0xFF2196F3)
    val SensorBorderDark = Color(0xFF383838)
    val SensorBorderLight = Color(0xFFD6D6D6)
    val BalanceGaugeTrack = Color(0xFF1B1B1F)
    val ZoneGaugeTrack = Color(0xFF1B1B1F)
    val WarningBorderLight = Color(0xFFE0E0E0)
}
