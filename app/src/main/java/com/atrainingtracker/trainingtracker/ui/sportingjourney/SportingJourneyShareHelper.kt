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

package com.atrainingtracker.trainingtracker.ui.sportingjourney

import android.content.Context
import android.content.Intent
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BANALService
import com.atrainingtracker.trainingtracker.MyUnits
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.monetization.SupporterTier
import java.util.Locale
import kotlin.math.roundToLong

/**
 * Formats and shares the Athlete Passport card via Android [Intent.ACTION_SEND] (REQ-SPT-004, REQ-SPT-005).
 */
object SportingJourneyShareHelper {

    private fun getStringSafe(context: Context?, locale: Locale, resId: Int, defaultFormat: String, vararg args: Any): String {
        return try {
            val resolved = if (args.isEmpty()) context?.getString(resId) else context?.getString(resId, *args)
            if (!resolved.isNullOrBlank()) {
                resolved
            } else if (args.isEmpty()) {
                defaultFormat
            } else {
                String.format(locale, defaultFormat, *args)
            }
        } catch (_: Exception) {
            if (args.isEmpty()) {
                defaultFormat
            } else {
                try {
                    String.format(locale, defaultFormat, *args)
                } catch (_: Exception) {
                    defaultFormat
                }
            }
        }
    }

    /**
     * Builds the formatted summary passport text stamped with the user's active tier.
     * Complies with user distance/elevation units (REQ-SPT-005.5) and [Locale.getDefault].
     */
    fun buildShareText(
        context: Context?,
        data: SportingJourneyData,
        tier: SupporterTier,
        isMetric: Boolean = (try { TrainingApplication.getUnit() == MyUnits.METRIC } catch (_: Exception) { true }),
        locale: Locale = Locale.getDefault()
    ): String {
        val tierTitle = when (tier) {
            SupporterTier.PIONEER -> "PIONEER"
            SupporterTier.DIAMOND -> "DIAMOND SPONSOR"
            SupporterTier.GOLD -> "GOLD SPONSOR"
            SupporterTier.SILVER -> "SILVER SUPPORTER"
            SupporterTier.BRONZE -> "BRONZE SUPPORTER"
            SupporterTier.FREE -> "ATHLETE"
        }

        val distStr = if (isMetric) {
            "${String.format(locale, "%,.1f", data.heroTotals.totalDistanceKm)} km"
        } else {
            val distMiles = (data.heroTotals.totalDistanceKm * 1000.0) / BANALService.METER_PER_MILE
            "${String.format(locale, "%,.1f", distMiles)} mi"
        }

        val elevStr = if (isMetric) {
            "${String.format(locale, "%,d", data.heroTotals.totalElevationM)} m"
        } else {
            val elevFeet = (data.heroTotals.totalElevationM / BANALService.METER_PER_FOOT).roundToLong()
            "${String.format(locale, "%,d", elevFeet)} ft"
        }

        val eqPctStr = String.format(locale, "%.1f", data.heroTotals.equatorPercentage)
        val everestStr = String.format(locale, "%.2f", data.heroTotals.everestMultiplier)
        val hoursStr = String.format(locale, "%,.1f", data.heroTotals.totalActiveHours)
        val daysStr = String.format(locale, "%.1f", data.heroTotals.nonstopDays)

        val headerText = getStringSafe(context, locale, R.string.sporting_journey_share_passport_header, "aTrainingTracker • Official Athlete Passport")
        val tierHorizonText = getStringSafe(context, locale, R.string.sporting_journey_share_tier_horizon, "Tier: %1\$s | Horizon: %2\$s", tierTitle, data.horizon.label)
        val distLine = getStringSafe(context, locale, R.string.sporting_journey_share_distance, "Distance: %1\$s (%2\$s%% of Equator)", distStr, eqPctStr)
        val elevLine = getStringSafe(context, locale, R.string.sporting_journey_share_elevation, "Vertical Ascent: %1\$s (%2\$s× Mt. Everest)", elevStr, everestStr)
        val activeLine = getStringSafe(context, locale, R.string.sporting_journey_share_active_time, "Active Motion: %1\$s h (%2\$s days nonstop)", hoursStr, daysStr)

        val unlockedCount = data.achievements.count { it.isUnlocked }
        val trophiesLine = getStringSafe(context, locale, R.string.sporting_journey_share_trophies, "Secret Trophies: %1\$d / %2\$d Unlocked", unlockedCount, data.achievements.size)
        val footerText = getStringSafe(context, locale, R.string.sporting_journey_share_footer, "Recorded 100% offline with aTrainingTracker (2012–2026)")

        return buildString {
            append(headerText).append("\n")
            append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n")
            append(tierHorizonText).append("\n\n")
            append(distLine).append("\n")
            append(elevLine).append("\n")
            append(activeLine).append("\n")

            if (data.hiddenGems.pedalRevolutions > 0) {
                val cranksStr = String.format(locale, "%,d", data.hiddenGems.pedalRevolutions)
                append(getStringSafe(context, locale, R.string.sporting_journey_share_pedal_revs, "Pedal Revolutions: %1\$s", cranksStr)).append("\n")
            }
            if (data.hiddenGems.estimatedHeartbeats > 0) {
                val hrStr = String.format(locale, "%,d", data.hiddenGems.estimatedHeartbeats)
                append(getStringSafe(context, locale, R.string.sporting_journey_share_heartbeats, "Training Heartbeats: %1\$s", hrStr)).append("\n")
            }
            if (data.hiddenGems.mechanicalKwh > 0) {
                val kwhStr = String.format(locale, "%.1f", data.hiddenGems.mechanicalKwh)
                val ebikeRangeStr = if (isMetric) {
                    "${String.format(locale, "%,.0f", data.hiddenGems.ebikeKmEquivalent)} km"
                } else {
                    val ebikeMiles = (data.hiddenGems.ebikeKmEquivalent * 1000.0) / BANALService.METER_PER_MILE
                    "${String.format(locale, "%,.0f", ebikeMiles)} mi"
                }
                append(getStringSafe(context, locale, R.string.sporting_journey_share_mechanical_work, "Mechanical Output: %1\$s kWh (%2\$s e-bike range)", kwhStr, ebikeRangeStr)).append("\n")
            }

            append(trophiesLine).append("\n")
            append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n")
            append(footerText)
        }
    }

    /**
     * Dispatches the share sheet via [Intent.ACTION_SEND].
     */
    fun sharePassport(context: Context, data: SportingJourneyData, tier: SupporterTier) {
        val shareText = buildShareText(context, data, tier)
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.sporting_journey_share_title))
            putExtra(Intent.EXTRA_TEXT, shareText)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(sendIntent, context.getString(R.string.sporting_journey_share_card)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(chooser)
        } catch (_: Throwable) {
            // Safe fallback
        }
    }
}
