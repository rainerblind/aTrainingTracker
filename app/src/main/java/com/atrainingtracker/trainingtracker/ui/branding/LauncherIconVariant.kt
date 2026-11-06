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

package com.atrainingtracker.trainingtracker.ui.branding

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.monetization.SupporterTier

/**
 * Supported homescreen launcher icon variants with Option A badges & Variant C stadium overlays (REQ-UI-445 / ATT-1406 / ATT-5496).
 *
 * Every variant combines authentic Cervélo P3C and classic stopwatch artwork with tier badges:
 * 1. Standard (Free): Modernized classic Cervélo P3C & stopwatch without badge overlays.
 * 2. Pioneer Edition: Emerald & Gold Heritage Star emblem + emerald PIONEER capsule on white background.
 * 3. Bronze Supporter: Variant B5 Participant Rosette + bronze SUPPORTER capsule on white background.
 * 4. Silver Pro Supporter: Variant S4-A Single-Rim 3D Silver Coins + slate PRO SUPPORTER capsule on white background.
 * 5. Gold Pro+ Supporter: Variant G6 Single-Rim 3D Gold Bar + Coin + amber PRO+ SUPPORTER capsule on white background.
 * 6. Diamond Sponsor: Faceted Brilliant-Cut Cyan Diamond + cyan SPONSOR capsule on white background.
 */
enum class LauncherIconVariant(
    val id: String,
    val aliasName: String,
    val tier: SupporterTier,
    @StringRes val titleRes: Int,
    @DrawableRes val iconRes: Int,
    @DrawableRes val foregroundRes: Int,
    @DrawableRes val emblemRes: Int?
) {
    STANDARD(
        id = "standard",
        aliasName = "com.atrainingtracker.trainingtracker.activities.MainActivityStandard",
        tier = SupporterTier.FREE,
        titleRes = R.string.launcher_icon_standard,
        iconRes = R.mipmap.ic_launcher,
        foregroundRes = R.drawable.ic_launcher_foreground,
        emblemRes = null
    ),
    PIONEER(
        id = "pioneer",
        aliasName = "com.atrainingtracker.trainingtracker.activities.MainActivityPioneer",
        tier = SupporterTier.PIONEER,
        titleRes = R.string.launcher_icon_pioneer,
        iconRes = R.mipmap.ic_launcher_pioneer,
        foregroundRes = R.drawable.ic_launcher_foreground_pioneer,
        emblemRes = R.drawable.ic_supporter_emblem_pioneer
    ),
    BRONZE(
        id = "bronze",
        aliasName = "com.atrainingtracker.trainingtracker.activities.MainActivityBronze",
        tier = SupporterTier.BRONZE,
        titleRes = R.string.launcher_icon_bronze,
        iconRes = R.mipmap.ic_launcher_bronze,
        foregroundRes = R.drawable.ic_launcher_foreground_bronze,
        emblemRes = R.drawable.ic_supporter_emblem_bronze
    ),
    SILVER(
        id = "silver",
        aliasName = "com.atrainingtracker.trainingtracker.activities.MainActivitySilver",
        tier = SupporterTier.SILVER,
        titleRes = R.string.launcher_icon_silver,
        iconRes = R.mipmap.ic_launcher_silver,
        foregroundRes = R.drawable.ic_launcher_foreground_silver,
        emblemRes = R.drawable.ic_supporter_emblem_silver
    ),
    GOLD(
        id = "gold",
        aliasName = "com.atrainingtracker.trainingtracker.activities.MainActivityGold",
        tier = SupporterTier.GOLD,
        titleRes = R.string.launcher_icon_gold,
        iconRes = R.mipmap.ic_launcher_gold,
        foregroundRes = R.drawable.ic_launcher_foreground_gold,
        emblemRes = R.drawable.ic_supporter_emblem_gold
    ),
    DIAMOND(
        id = "diamond",
        aliasName = "com.atrainingtracker.trainingtracker.activities.MainActivityDiamond",
        tier = SupporterTier.DIAMOND,
        titleRes = R.string.launcher_icon_diamond,
        iconRes = R.mipmap.ic_launcher_diamond,
        foregroundRes = R.drawable.ic_launcher_foreground_diamond,
        emblemRes = R.drawable.ic_supporter_emblem_diamond
    );

    /**
     * Evaluates whether this launcher icon is unlocked for the athlete:
     * - Standard is available to everyone.
     * - Pioneer is available to verified Pioneer early adopters and Diamond sponsors.
     * - Tiered icons are unlocked whenever the athlete's effective supporter tier rank >= icon tier rank.
     */
    fun isUnlocked(effectiveTier: SupporterTier, isPioneer: Boolean): Boolean {
        if (this == STANDARD) return true
        if (this == PIONEER) {
            return isPioneer || effectiveTier == SupporterTier.PIONEER || effectiveTier == SupporterTier.DIAMOND
        }
        return effectiveTier.rank >= this.tier.rank
    }

    companion object {
        fun fromId(id: String?): LauncherIconVariant =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: STANDARD

        fun fromTier(tier: SupporterTier): LauncherIconVariant = when (tier) {
            SupporterTier.FREE -> STANDARD
            SupporterTier.PIONEER -> PIONEER
            SupporterTier.BRONZE -> BRONZE
            SupporterTier.SILVER -> SILVER
            SupporterTier.GOLD -> GOLD
            SupporterTier.DIAMOND -> DIAMOND
        }
    }
}
