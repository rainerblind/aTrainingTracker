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

package com.atrainingtracker.trainingtracker.monetization

import androidx.annotation.StringRes
import com.atrainingtracker.R

/**
 * Supporter tiers and rank definitions for the aTrainingTracker monetization model (REQ-SET-086).
 */
enum class SupporterTier(val rank: Int, @StringRes val titleRes: Int) {
    FREE(0, R.string.supporter_tier_free),
    PIONEER(1, R.string.supporter_tier_pioneer),
    BRONZE(2, R.string.supporter_tier_bronze),
    SILVER(3, R.string.supporter_tier_silver),
    GOLD(4, R.string.supporter_tier_gold),
    DIAMOND(5, R.string.supporter_tier_diamond);

    val isPro: Boolean get() = this != FREE
}

/**
 * Diagnostic debug overrides for local and UI verification in BuildConfig.DEBUG (REQ-SET-087).
 */
enum class SupporterTierOverride {
    AUTO,
    FORCE_FREE,
    FORCE_PIONEER,
    FORCE_BRONZE,
    FORCE_SILVER,
    FORCE_GOLD,
    FORCE_DIAMOND;

    fun toSupporterTier(): SupporterTier? = when (this) {
        AUTO -> null
        FORCE_FREE -> SupporterTier.FREE
        FORCE_PIONEER -> SupporterTier.PIONEER
        FORCE_BRONZE -> SupporterTier.BRONZE
        FORCE_SILVER -> SupporterTier.SILVER
        FORCE_GOLD -> SupporterTier.GOLD
        FORCE_DIAMOND -> SupporterTier.DIAMOND
    }
}

/**
 * Hardware wishlist categories for voluntary athlete voting (REQ-SET-086.2).
 */
enum class HardwareVoteOption(val id: String, @StringRes val labelRes: Int) {
    BIKE_RADAR("radar", R.string.hardware_vote_bike_radar),
    RUNNING_POWER("running_power", R.string.hardware_vote_running_power),
    SMARTWATCH_WEAROS("smartwatch", R.string.hardware_vote_smartwatch),
    PEBBLE("pebble", R.string.hardware_vote_pebble),
    GENERAL_DEVELOPMENT("general", R.string.hardware_vote_general)
}

/**
 * Google Play Billing base plan configuration for "att_supporter_sub" (REQ-SET-086.1).
 */
data class SupporterBasePlan(
    val basePlanId: String,
    val tier: SupporterTier,
    val priceFormatted: String,
    val period: String,
    val isRecommended: Boolean = false
) {
    companion object {
        const val PRODUCT_ID = "att_supporter_sub"

        val PLANS = listOf(
            SupporterBasePlan("bronze-monthly", SupporterTier.BRONZE, "1 €", "month"),
            SupporterBasePlan("bronze-yearly", SupporterTier.BRONZE, "10 €", "year"),
            SupporterBasePlan("silver-yearly", SupporterTier.SILVER, "25 €", "year", isRecommended = true),
            SupporterBasePlan("gold-yearly", SupporterTier.GOLD, "50 €", "year"),
            SupporterBasePlan("diamond-yearly", SupporterTier.DIAMOND, "100 €", "year")
        )
    }
}
