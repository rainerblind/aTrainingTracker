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

package com.atrainingtracker.trainingtracker.ui.tracking

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.atrainingtracker.R

/**
 * Canonical identity for unmodified default Tracking Cockpit tabs persisted in SQLite (`ViewsTable.Name`).
 * Maps directly to the 9-language string resource used when rendering the tab title in the active locale.
 */
enum class DefaultTrackingTabKey(@StringRes val titleResId: Int) {
    DEFAULT(R.string.text_default),
    POWER(R.string.tab_power),
    MAP(R.string.tab_map),
    LAPS(R.string.tab_laps)
}

/**
 * Resolves unmodified default Tracking Cockpit tab names persisted in SQLite under any of the
 * 9 supported app locales (`en`, `de`, `es`, `fr`, `it`, `ja`, `nl`, `pl`, `pt`) to the active
 * device/app locale while strictly preserving user-renamed custom tab names (`ATT-9511` / `REQ-TRK-097`).
 */
object TrackingTabTitleResolver {

    private val PERSISTED_DEFAULT_NAMES: Map<String, DefaultTrackingTabKey> = mapOf(
        // R.string.text_default across en, de, es, fr, it, ja, nl, pl, pt
        "default" to DefaultTrackingTabKey.DEFAULT,
        "Standard" to DefaultTrackingTabKey.DEFAULT,
        "predeterminado" to DefaultTrackingTabKey.DEFAULT,
        "par défaut" to DefaultTrackingTabKey.DEFAULT,
        "predefinito" to DefaultTrackingTabKey.DEFAULT,
        "デフォルト" to DefaultTrackingTabKey.DEFAULT,
        "standaard" to DefaultTrackingTabKey.DEFAULT,
        "domyślny" to DefaultTrackingTabKey.DEFAULT,
        "padrão" to DefaultTrackingTabKey.DEFAULT,

        // R.string.tab_power across en, de, es, fr, it, ja, nl, pl, pt
        "Power" to DefaultTrackingTabKey.POWER,
        "Leistung" to DefaultTrackingTabKey.POWER,
        "Potencia" to DefaultTrackingTabKey.POWER,
        "Puissance" to DefaultTrackingTabKey.POWER,
        "Potenza" to DefaultTrackingTabKey.POWER,
        "パワー" to DefaultTrackingTabKey.POWER,
        "Vermogen" to DefaultTrackingTabKey.POWER,
        "Moc" to DefaultTrackingTabKey.POWER,
        "Potência" to DefaultTrackingTabKey.POWER,

        // R.string.tab_map across en, de, es, fr, it, ja, nl, pl, pt
        "Map" to DefaultTrackingTabKey.MAP,
        "Karte" to DefaultTrackingTabKey.MAP,
        "Mapa" to DefaultTrackingTabKey.MAP,
        "Carte" to DefaultTrackingTabKey.MAP,
        "Mappa" to DefaultTrackingTabKey.MAP,
        "マップ" to DefaultTrackingTabKey.MAP,
        "Kaart" to DefaultTrackingTabKey.MAP,

        // R.string.tab_laps across en, de, es, fr, it, ja, nl, pl, pt
        "Laps" to DefaultTrackingTabKey.LAPS,
        "Runden" to DefaultTrackingTabKey.LAPS,
        "Vueltas" to DefaultTrackingTabKey.LAPS,
        "Tours" to DefaultTrackingTabKey.LAPS,
        "Giri" to DefaultTrackingTabKey.LAPS,
        "ラップ" to DefaultTrackingTabKey.LAPS,
        "Ronden" to DefaultTrackingTabKey.LAPS,
        "Okrążenia" to DefaultTrackingTabKey.LAPS,
        "Voltas" to DefaultTrackingTabKey.LAPS
    )

    /**
     * Returns the canonical [DefaultTrackingTabKey] if [persistedName] matches an unmodified default
     * tab title from any supported locale, or `null` for user-renamed custom titles.
     */
    @JvmStatic
    fun resolveDefaultTabKey(persistedName: String?): DefaultTrackingTabKey? {
        if (persistedName.isNullOrEmpty()) return null
        return PERSISTED_DEFAULT_NAMES[persistedName]
    }

    /**
     * Resolves [persistedName] against [context] resources when it matches an unmodified default tab,
     * falling back cleanly to [persistedName] for custom titles or unconfigured test mocks.
     */
    @JvmStatic
    fun resolveTitle(context: Context, persistedName: String): String {
        val defaultKey = resolveDefaultTabKey(persistedName) ?: return persistedName
        return try {
            context.getString(defaultKey.titleResId).takeIf { it.isNotEmpty() } ?: persistedName
        } catch (_: Exception) {
            persistedName
        }
    }
}

/**
 * Compose-aware title resolver that recomposes automatically whenever `LocalConfiguration`
 * changes, even across retained `TrackingTabsViewModel` instances (`ATT-9511`).
 */
@Composable
fun resolveTrackingTabTitle(name: String): String {
    val defaultKey = TrackingTabTitleResolver.resolveDefaultTabKey(name)
    return if (defaultKey != null) {
        stringResource(defaultKey.titleResId)
    } else {
        name
    }
}
