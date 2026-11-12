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
import com.atrainingtracker.trainingtracker.monetization.SupporterTier
import java.util.Locale

/**
 * Formats and shares the Athlete Passport card via Android [Intent.ACTION_SEND] (REQ-SPT-004).
 */
object SportingJourneyShareHelper {

    /**
     * Builds the formatted summary passport text stamped with the user's active tier.
     */
    fun buildShareText(context: Context, data: SportingJourneyData, tier: SupporterTier): String {
        val tierTitle = when (tier) {
            SupporterTier.PIONEER -> "PIONEER"
            SupporterTier.DIAMOND -> "DIAMOND SPONSOR"
            SupporterTier.GOLD -> "GOLD SPONSOR"
            SupporterTier.SILVER -> "SILVER SUPPORTER"
            SupporterTier.BRONZE -> "BRONZE SUPPORTER"
            SupporterTier.FREE -> "ATHLETE"
        }

        return buildString {
            append("🏅 aTrainingTracker • Official Athlete Passport\n")
            append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n")
            append("🏆 Tier: $tierTitle | Horizon: ${data.horizon.label}\n\n")
            append("🌍 Distance: ${String.format(Locale.US, "%,.1f", data.heroTotals.totalDistanceKm)} km (${String.format(Locale.US, "%.1f", data.heroTotals.equatorPercentage)}% of Equator)\n")
            append("🏔️ Vertical Ascent: ${String.format(Locale.US, "%,d", data.heroTotals.totalElevationM)} m (${String.format(Locale.US, "%.2f", data.heroTotals.everestMultiplier)}× Mt. Everest)\n")
            append("⏱️ Active Motion: ${String.format(Locale.US, "%,.1f", data.heroTotals.totalActiveHours)} h (${String.format(Locale.US, "%.1f", data.heroTotals.nonstopDays)} days nonstop)\n")
            if (data.hiddenGems.pedalRevolutions > 0) {
                append("🚴 Pedal Revolutions: ${String.format(Locale.US, "%,d", data.hiddenGems.pedalRevolutions)}\n")
            }
            if (data.hiddenGems.estimatedHeartbeats > 0) {
                append("❤️ Training Heartbeats: ${String.format(Locale.US, "%,d", data.hiddenGems.estimatedHeartbeats)}\n")
            }
            if (data.hiddenGems.mechanicalKwh > 0) {
                append("⚡ Mechanical Output: ${String.format(Locale.US, "%.1f", data.hiddenGems.mechanicalKwh)} kWh (${String.format(Locale.US, "%,.0f", data.hiddenGems.ebikeKmEquivalent)} km e-bike range)\n")
            }
            append("🏆 Secret Trophies: ${data.achievements.count { it.isUnlocked }} / ${data.achievements.size} Unlocked\n")
            append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n")
            append("Recorded 100% offline with aTrainingTracker (2012–2026)")
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
