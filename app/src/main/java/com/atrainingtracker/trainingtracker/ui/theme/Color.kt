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
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.atrainingtracker.banalservice.devices.radar.ThreatLevel
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
    /**
     * Zone 6 (Anaerobic Capacity / Extreme Grade): Warm Earthy Brown (#8B5A2B).
     * Selected for WCAG 2.1 AA non-text contrast (3.44:1 >= 3.0:1) against [Zone7] Pure Black (#000000)
     * on OLED displays (REQ-UI-392).
     */
    val Zone6 = Color(0xFF8B5A2B) // Warm Earthy Brown
    val Zone7 = Color(0xFF000000) // Pure Black

    // High-contrast theme-adaptive zone stroke tokens (>= 3.4:1 WCAG 2.1 AA on Light, Dark & AMOLED; REQ-UI-2798.1 / ATT-9164)
    val Zone1StrokeLight = Color(0xFF4D9900) // Deep Chartreuse-Lime (3.49:1 on Light)
    val Zone2StrokeLight = Zone2             // Forest Green (5.01:1 on Light)
    val Zone3StrokeLight = Color(0xFFC97100) // Rich Amber-Orange (3.50:1 on Light)
    val Zone4StrokeLight = Color(0xFFE51919) // Crisp Red (4.57:1 on Light)
    val Zone5StrokeLight = Zone5             // Dark Violet (6.40:1 on Light)
    val Zone6StrokeLight = Zone6             // Warm Earthy Brown (5.70:1 on Light)
    val Zone7StrokeLight = Color(0xFF111111) // Near-Black Charcoal (18.42:1 on Light)

    val Zone1StrokeDark = Zone1              // Vibrant Chartreuse (14.46:1 Dark / 16.21:1 AMOLED)
    val Zone2StrokeDark = Color(0xFF2EBD59)  // Vivid Emerald-Green (7.62:1 Dark / 8.54:1 AMOLED)
    val Zone3StrokeDark = Zone3              // Bright Orange (9.49:1 Dark / 10.63:1 AMOLED)
    val Zone4StrokeDark = Color(0xFFFF3B30)  // Vivid Red (5.28:1 Dark / 5.92:1 AMOLED)
    val Zone5StrokeDark = Color(0xFFC65CFF)  // Bright Violet (5.69:1 Dark / 6.38:1 AMOLED)
    val Zone6StrokeDark = Color(0xFFC88A52)  // Warm Golden-Brown (6.43:1 Dark / 7.21:1 AMOLED)
    val Zone7StrokeDark = Color(0xFFE2E8F0)  // High-Contrast Silver-White (15.20:1 Dark / 17.03:1 AMOLED)

    /**
     * Resolves a WCAG 2.1 AA high-contrast telemetry curve stroke color for 1-based [zoneIndex] (`1..7`)
     * adapted to Light vs. Dark/AMOLED surfaces (`REQ-UI-2798.1` / `ATT-9164`).
     */
    fun zoneStrokeColor(zoneIndex: Int, isDarkTheme: Boolean): Color = if (isDarkTheme) {
        when (zoneIndex) {
            1 -> Zone1StrokeDark
            2 -> Zone2StrokeDark
            3 -> Zone3StrokeDark
            4 -> Zone4StrokeDark
            5 -> Zone5StrokeDark
            6 -> Zone6StrokeDark
            else -> Zone7StrokeDark
        }
    } else {
        when (zoneIndex) {
            1 -> Zone1StrokeLight
            2 -> Zone2StrokeLight
            3 -> Zone3StrokeLight
            4 -> Zone4StrokeLight
            5 -> Zone5StrokeLight
            6 -> Zone6StrokeLight
            else -> Zone7StrokeLight
        }
    }

    /**
     * Color mapping for Borg CR10 RPE scale (1–10) across all 7 physiological zones (REQ-WKO-038 / REQ-UI-529).
     * Maps 1–2 -> Zone1, 3–4 -> Zone2, 5–6 -> Zone3, 7 -> Zone4, 8 -> Zone5, 9 -> Zone6, 10 -> Zone7.
     * Values <= 0 clamp to Zone1; values >= 11 clamp to Zone7.
     */
    fun rpeColor(rpe: Int): Color = when {
        rpe <= 2 -> Zone1 // Chartreuse / Active Recovery
        rpe in 3..4 -> Zone2 // Green / Endurance
        rpe in 5..6 -> Zone3 // Orange / Tempo / Sweet Spot
        rpe == 7 -> Zone4 // Red / Threshold
        rpe == 8 -> Zone5 // Dark Violet / VO2max
        rpe == 9 -> Zone6 // Warm Earthy Brown / Anaerobic Capacity
        else -> Zone7 // Pure Black / Neuromuscular / Max (>= 10)
    }

    /**
     * Computes the WCAG 2.1 relative luminance of [color] in the range [0.0, 1.0].
     */
    fun relativeLuminance(color: Color): Double {
        fun srgbToLinear(c: Float): Double {
            val v = c.toDouble()
            return if (v <= 0.03928) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * srgbToLinear(color.red) +
            0.7152 * srgbToLinear(color.green) +
            0.0722 * srgbToLinear(color.blue)
    }

    /**
     * Computes the WCAG 2.1 contrast ratio between [foreground] and [background] in the range [1.0, 21.0].
     */
    fun calculateContrastRatio(foreground: Color, background: Color): Double {
        val l1 = relativeLuminance(foreground)
        val l2 = relativeLuminance(background)
        return (Math.max(l1, l2) + 0.05) / (Math.min(l1, l2) + 0.05)
    }

    // Branding / Status
    val StravaOrange = Color(0xFFFC4C02)
    val ConnectionStatusGreen = Color(0xFF4CAF50)

    // Sensor Source Availability (REQ-UI-051, ATT-4374)
    /**
     * Primary active sensor source section header color ("Quelle" / R.string.source_device).
     * Material Green 800 (#2E7D32) provides strong contrast (>= 3.5:1 on dark, >= 5.1:1 on light)
     * and aligns with [StartPoint], [ConnectionConnected], and [PmcFreshGreen].
     */
    val SensorSourceActive = Color(0xFF2E7D32)

    /**
     * Active standby backup sensor source section header color ("Aktive Backups" / R.string.source_active_backups).
     * Material Green 600 (#43A047) provides a visibly lighter green tone while meeting WCAG AA
     * bold contrast (>= 3.3:1 on light, >= 5.4:1 on dark) and harmonizing with [ConnectionStatusGreen].
     */
    val SensorBackupActive = Color(0xFF43A047)

    // Route Visualization
    val RouteSelected = Color(0xFF1565C0) // Material Blue 800 (Royal Blue)
    val RouteUnselected = Color(0xFF90CAF9) // Material Blue 200 (Soft Steel Blue)
    val RouteActiveNavigation = Color(0xFF1E88E5) // Material Blue 600 (Vibrant Sapphire Blue for actively navigated routes)
    val RouteActiveNavigationOverlay = Color(0xFF0D47A1) // Material Blue 900 (Deep Midnight Navy for active route dashed overlay)
    val Cluster = Color(0xFF8E24AA) // Material Purple 600 (Favorite Tracks / Workout Clusters)

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
    val BikeBellContainer = Color(0xFFFF9800) // Distinct Warm Orange (REQ-UI-421.2, ATT-5283)
    val BikeBellContent = Color(0xFF000000)   // High-Contrast Pure Black on Orange (WCAG AAA > 10:1) (REQ-UI-421.2, ATT-5283)

    // Radar Tail Light Cockpit Button Tokens (REQ-CON-043.2, ATT-7972)
    val RadarLightContainer = Color(0xFF263238)           // Slate Blue Grey container
    val RadarLightContentOff = Color(0xFF90A4AE)          // Subtle Slate Grey for OFF
    val RadarLightContentSolid = Color(0xFFE53935)        // High-visibility Red for SOLID
    val RadarLightContentDayFlash = Color(0xFFFFB300)     // Amber / Gold for DAY FLASH
    val RadarLightContentNightFlash = Color(0xFFFF8F00)   // Deep Amber for NIGHT FLASH
    val RadarLightContentPeloton = Color(0xFFEF5350)      // Soft Red for PELOTON
    val RadarLightBadgeAuto = Color(0xFF64B5F6)           // Clear Light Blue for AUTO micro-badge
    val SensorActiveBorder = Color(0xFF2196F3)
    val SensorBorderDark = Color(0xFF383838)
    val SensorBorderLight = Color(0xFFD6D6D6)
    val BalanceGaugeTrack = Color(0xFF1B1B1F)
    val ZoneGaugeTrack = Color(0xFF1B1B1F)
    val WarningBorderLight = Color(0xFFE0E0E0)
    val WPrimeBalGreen = Color(0xFF10B981)

    // Performance Management Chart (PMC) Colors (REQ-UI-401)
    val PmcCtlBlue = Color(0xFF1E88E5)
    val PmcAtlMagenta = Color(0xFFD81B60)
    val PmcTsbAmber = Color(0xFFFB8C00)
    val PmcFreshGreen = Color(0xFF2E7D32)
    val PmcOptimalBlue = Color(0xFF1565C0)
    val PmcCautionOrange = Color(0xFFEF6C00)

    // Period Stacked Bar Sport Palette (REQ-PER-018.4, ATT-4478)
    val BikeSportBarColor = Color(0xFF1E88E5)
    val RunSportBarColor = Color(0xFFFF7043)
    val OtherSportBarColor = Color(0xFF26A69A)

    // Warm Heatmap "Hot" Sequential Palette (REQ-MAP-057.2, ATT-4933)
    val HeatmapHotYellow = Color(0xFFFFF176)
    val HeatmapHotOrange = Color(0xFFFF9800)
    val HeatmapHotRed = Color(0xFFE53935)
    val HeatmapHotDarkRed = Color(0xFF880E4F)

    // Supporter Launcher Icon Preview Surface (REQ-UI-445, ATT-1406, ATT-5372)
    val SupporterIconPreviewContainer = Color(0xFFF1F5F9)

    // Cluster Variant Sub-Corridor Palette (REQ-SET-096.3, ATT-5900)
    val ClusterVariantBlue = Color(0xFF2196F3)       // Primary: Royal Blue
    val ClusterVariantOrange = Color(0xFFFF9800)     // Variant 2: Vibrant Amber / Orange
    val ClusterVariantGreen = Color(0xFF4CAF50)      // Variant 3: Vibrant Green
    val ClusterVariantPurple = Color(0xFF9C27B0)     // Variant 4: Vibrant Purple
    val ClusterVariantPink = Color(0xFFE91E63)       // Variant 5: Vibrant Pink
    val ClusterVariantCyan = Color(0xFF00BCD4)       // Variant 6: Vibrant Cyan
    val ClusterVariantDeepOrange = Color(0xFFFF5722) // Variant 7: Deep Orange
    val ClusterVariantBrown = Color(0xFF795548)      // Variant 8: Brown

    val ClusterVariantPalette = listOf(
        ClusterVariantBlue,
        ClusterVariantOrange,
        ClusterVariantGreen,
        ClusterVariantPurple,
        ClusterVariantPink,
        ClusterVariantCyan,
        ClusterVariantDeepOrange,
        ClusterVariantBrown
    )

    // Rearview Radar Threat Tokens (REQ-TRK-062.2, ATT-5085)
    val RadarClearGreen = Color(0xFF2E7D32)
    val RadarThreatAmber = Color(0xFFFF8F00)
    val RadarThreatRed = Color(0xFFD32F2F)
    val RadarDisconnectedAmber = Color(0xFFE65100)
    val RadarRailTrack = Color(0x33000000)
    val RadarRailTrackBorder = Color(0x22FFFFFF)

    // Full-Screen Radar Vehicle Marker & Lane Guide Tokens (REQ-TRK-089.2, ATT-8733)
    val RadarFullScreenLaneGuideLight = Color(0x2437474F)
    val RadarFullScreenLaneGuideDark = Color(0x2E90A4AE)
    val RadarFullScreenMarkerScrim = Color(0xE0121417)
    val RadarFullScreenMarkerInnerBorder = Color(0xF2FFFFFF)
    val RadarFullScreenApproachingContent = Color(0xFF1A1200)
    val RadarFullScreenFastContent = Color(0xFFFFFFFF)
    val RadarFullScreenMapBackdrop = Color(0xFFE8EFE2)
    val RadarFullScreenMapPark = Color(0xFFD0E3C4)
    val RadarFullScreenMapRoad = Color(0xFFFFFFFF)

    /**
     * Resolves the authoritative threat color token for a given [ThreatLevel] (REQ-TRK-062.2).
     */
    fun getRadarThreatColor(threatLevel: ThreatLevel): Color = when (threatLevel) {
        ThreatLevel.NONE -> RadarClearGreen
        ThreatLevel.APPROACHING -> RadarThreatAmber
        ThreatLevel.FAST_APPROACHING -> RadarThreatRed
    }

    // Morning HRV Readiness & Guided Measurement Palette (REQ-PER-032, ATT-3556, ATT-6719)
    val ReadinessOptimal = Color(0xFF2E7D32)
    val ReadinessFatigued = Color(0xFFC62828)
    val ReadinessElevated = Color(0xFF1565C0)
    val ReadinessCalibrating = Color(0xFF6A1B9A)
    val ReadinessOnStatus = Color(0xFFFFFFFF)
    val HeartRateRed = Color(0xFFE53935)
    val ArtifactWarningContainer = Color(0xFFFFF3E0)
    val ArtifactWarningBorder = Color(0xFFFFB74D)
    val ArtifactWarningTitle = Color(0xFFE65100)
    val ArtifactWarningDesc = Color(0xFFBF360C)
    val HrvQualityHigh = Color(0xFF2E7D32)
    val HrvQualityModerate = Color(0xFFF57F17)
    val HrvHeartRateRed = Color(0xFFD32F2F)
    val HrvSuccessGreen = Color(0xFF2E7D32)

    // The Trophy Vault & Sporting Journey Tokens (REQ-SPT-001..004, ATT-7022)
    val VaultGold = Color(0xFFFFD700)
    val VaultAmber = Color(0xFFF59E0B)
    val VaultCardBackground = Color(0x1AF59E0B)
    val VaultCardBorder = Color(0x4DF59E0B)
    val VaultHeroStart = Color(0x26F59E0B)
    val VaultHeroEnd = Color(0x05F59E0B)
    val VaultBiologicalRed = Color(0xFFF43F5E)
    val VaultBiologicalRedContainer = Color(0x26F43F5E)
    val VaultElectricAmber = Color(0xFFF59E0B)
    val VaultElectricAmberContainer = Color(0x26F59E0B)
    val VaultCircadianSky = Color(0xFF0EA5E9)
    val VaultCircadianSkyContainer = Color(0x260EA5E9)
    val VaultExplorerEmerald = Color(0xFF10B981)
    val VaultExplorerEmeraldContainer = Color(0x2610B981)
    val VaultPassportBgStart = Color(0xFF0F172A)
    val VaultPassportBgEnd = Color(0xFF1E293B)

    // Phone Carriage & Activity Timeline Palette (ATT-8916, L in [0.196, 0.233] -> >=3.2:1 UI contrast on Light/Dark/AMOLED)
    val CarriageBikeHandlebar = Color(0xFF0284C7)
    val CarriageBikeJerseyBack = Color(0xFF6B6FF5)
    val CarriageBikeFrontPocket = Color(0xFF0D9488)
    val CarriageRunWaistHip = Color(0xFFC96A05)
    val CarriageRunBicepArm = Color(0xFFEC3B5B)
    val CarriageRunHandheld = Color(0xFFA855F7)
    val CarriageWalkingTransition = Color(0xFF159947)
    val CarriageStationary = Color(0xFF6B7C93)
    val CarriageUnknown = Color(0xFF827B75)

    // Special Distinction: Epic Workout Badge & Tile Tokens (REQ-REV-018, ATT-9022)
    // Calibrated for WCAG AAA text contrast (> 9:1) on Light, Dark, and AMOLED surfaces,
    // clearly distinguished from Race's Royal Blue tertiaryContainer accent.
    val EpicContainerLight = Color(0xFFFFF3CD)
    val EpicOnContainerLight = Color(0xFF4E3000)
    val EpicAccentLight = Color(0xFFB45309)
    val EpicContainerDark = Color(0xFF422A00)
    val EpicOnContainerDark = Color(0xFFFFE082)
    val EpicAccentDark = Color(0xFFF59E0B)

    /**
     * Theme-aware color triple for the prominent `Epic` workout badge and Special Distinction
     * classification tile (`REQ-REV-018.1`, `REQ-REV-018.2`, `ATT-9022`).
     */
    data class EpicBadgeColors(
        val container: Color,
        val onContainer: Color,
        val accent: Color
    )

    /**
     * Pure theme-aware resolver for [EpicBadgeColors] supporting Light, Dark, and AMOLED surfaces.
     */
    fun resolveEpicBadgeColors(isDark: Boolean): EpicBadgeColors = if (isDark) {
        EpicBadgeColors(
            container = EpicContainerDark,
            onContainer = EpicOnContainerDark,
            accent = EpicAccentDark
        )
    } else {
        EpicBadgeColors(
            container = EpicContainerLight,
            onContainer = EpicOnContainerLight,
            accent = EpicAccentLight
        )
    }

    /**
     * Composable convenience returning [EpicBadgeColors] derived from the active [MaterialTheme.colorScheme]
     * surface luminance (works seamlessly across Light, Dark, and AMOLED themes).
     */
    @Composable
    fun epicBadgeColors(): EpicBadgeColors {
        val isDark = relativeLuminance(MaterialTheme.colorScheme.surface) < 0.5
        return resolveEpicBadgeColors(isDark)
    }

    fun getCarriageModeColor(
        mode: com.atrainingtracker.banalservice.odometry.PhoneCarriageMode
    ): Color = when (mode) {
        com.atrainingtracker.banalservice.odometry.PhoneCarriageMode.BIKE_HANDLEBAR -> CarriageBikeHandlebar
        com.atrainingtracker.banalservice.odometry.PhoneCarriageMode.BIKE_JERSEY_BACK -> CarriageBikeJerseyBack
        com.atrainingtracker.banalservice.odometry.PhoneCarriageMode.BIKE_FRONT_POCKET -> CarriageBikeFrontPocket
        com.atrainingtracker.banalservice.odometry.PhoneCarriageMode.RUN_WAIST_HIP -> CarriageRunWaistHip
        com.atrainingtracker.banalservice.odometry.PhoneCarriageMode.RUN_BICEP_ARM -> CarriageRunBicepArm
        com.atrainingtracker.banalservice.odometry.PhoneCarriageMode.RUN_HANDHELD -> CarriageRunHandheld
        com.atrainingtracker.banalservice.odometry.PhoneCarriageMode.WALKING_TRANSITION -> CarriageWalkingTransition
        com.atrainingtracker.banalservice.odometry.PhoneCarriageMode.STATIONARY -> CarriageStationary
        com.atrainingtracker.banalservice.odometry.PhoneCarriageMode.UNKNOWN -> CarriageUnknown
    }
}

/**
 * Centralized Light/Dark-aware fixed telemetry color palette and theme-default option
 * for Workout Detail charts and Cockpit sparklines (REQ-UI-530.1, ATT-7759).
 *
 * Every fixed palette token is calibrated to achieve WCAG 2.1 non-text contrast (>= 3.0:1)
 * against Light (#FFFFFF), Dark (#16161A), and AMOLED (#000000) surfaces.
 */
@Suppress("MagicNumber")
enum class TelemetryGraphColor(
    val lightColor: Color,
    val darkColor: Color
) {
    THEME_DEFAULT(
        lightColor = LightPrimary,
        darkColor = DarkPrimary
    ),
    CRIMSON(
        lightColor = Color(0xFFD32F2F),
        darkColor = Color(0xFFFF5252)
    ),
    VIOLET(
        lightColor = Color(0xFF7C3AED),
        darkColor = Color(0xFFC084FC)
    ),
    EMERALD(
        lightColor = Color(0xFF059669),
        darkColor = TTColor.WPrimeBalGreen
    ),
    AMBER(
        lightColor = Color(0xFFD97706),
        darkColor = Color(0xFFFBBF24)
    ),
    TEAL(
        lightColor = Color(0xFF0D9488),
        darkColor = Color(0xFF2DD4BF)
    ),
    MAGENTA(
        lightColor = Color(0xFFDB2777),
        darkColor = Color(0xFFF472B6)
    ),
    CYAN(
        lightColor = Color(0xFF0891B2),
        darkColor = Color(0xFF22D3EE)
    ),
    CORAL(
        lightColor = Color(0xFFEA580C),
        darkColor = Color(0xFFFB923C)
    );

    /**
     * Resolves the theme-appropriate stroke/accent [Color] for [colorScheme].
     *
     * Defaults [isDarkTheme] from [colorScheme]'s surface luminance so forced-dark themes
     * (such as Cockpit ALWAYS_DARK or AMOLED) resolve high-contrast dark tokens even when the
     * host system theme is Light.
     */
    fun resolveColor(
        colorScheme: ColorScheme,
        isDarkTheme: Boolean = isDarkSurface(colorScheme)
    ): Color = when (this) {
        THEME_DEFAULT -> colorScheme.primary
        else -> if (isDarkTheme) darkColor else lightColor
    }

    companion object {
        private const val DARK_SURFACE_LUMINANCE_THRESHOLD = 0.5

        /**
         * All 8 fixed non-theme-default palette colors in canonical display order.
         */
        val PALETTE_COLORS: List<TelemetryGraphColor> = entries.filter { it != THEME_DEFAULT }

        /**
         * Returns true when [colorScheme] uses a dark surface based on relative luminance.
         */
        fun isDarkSurface(colorScheme: ColorScheme): Boolean =
            TTColor.relativeLuminance(colorScheme.surface) < DARK_SURFACE_LUMINANCE_THRESHOLD
    }
}
