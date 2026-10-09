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

import android.content.res.Resources
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.google.accompanist.drawablepainter.rememberDrawablePainter

/**
 * Defensive Compose painter loader enforcing REQ-UI-312:
 * Safely resolves a drawable resource ID without crashing when [Resources.NotFoundException] occurs.
 *
 * If the requested resource ID cannot be resolved (e.g. on density-split APKs, custom DPI scaling,
 * or obsolete asset references), this helper gracefully catches the exception and falls back to
 * rendering [fallback] (or a transparent Painter if no fallback is provided) instead of crashing
 * the UI thread.
 *
 * @param id The Android drawable resource ID to load.
 * @param fallback Optional [ImageVector] to render if the drawable resource is not found or invalid.
 * @return A valid [Painter] instance that will not throw on draw.
 */
@Composable
fun safePainterResource(
    @DrawableRes id: Int,
    fallback: ImageVector? = null
): Painter {
    val context = LocalContext.current
    val drawable = remember(id, context) {
        if (id <= 0) null
        else {
            try {
                ContextCompat.getDrawable(context, id)
            } catch (_: Resources.NotFoundException) {
                null
            } catch (_: Exception) {
                null
            }
        }
    }

    return when {
        drawable != null -> rememberDrawablePainter(drawable = drawable)
        fallback != null -> rememberVectorPainter(image = fallback)
        else -> remember { ColorPainter(Color.Transparent) }
    }
}
