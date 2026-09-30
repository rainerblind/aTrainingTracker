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

package com.atrainingtracker.trainingtracker.helpers

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.Matrix
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import com.atrainingtracker.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Theme color tokens for share snapshot composition (ATT-1472 / REQ-UI-177).
 */
data class ShareThemeColors(
    val canvasBackground: Int,
    val footerBackground: Int,
    val footerTextColor: Int
)

/**
 * Resolves theme colors and night mode state for post-workout and period share snapshots (ATT-1472 / REQ-UI-177).
 */
object ShareThemeResolver {
    val Light = ShareThemeColors(
        canvasBackground = 0xFFFFFFFF.toInt(), // Color.WHITE
        footerBackground = 0xFFF5F5F5.toInt(), // #F5F5F5
        footerTextColor = 0xFF444444.toInt()   // Color.DKGRAY
    )

    val Dark = ShareThemeColors(
        canvasBackground = 0xFF000000.toInt(), // Color.BLACK
        footerBackground = 0xFF1E1E1E.toInt(), // #1E1E1E
        footerTextColor = 0xFFE0E0E0.toInt()   // #E0E0E0
    )

    @JvmStatic
    fun resolveColors(isDark: Boolean): ShareThemeColors {
        return if (isDark) Dark else Light
    }

    @JvmStatic
    fun isNightMode(context: Context): Boolean {
        val nightModeFlags = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return nightModeFlags == Configuration.UI_MODE_NIGHT_YES
    }
}

/**
 * Shares a summary consisting of a Header and a Map.
 */
suspend fun combineAndShare(
    context: Context,
    header: Bitmap,
    map: Bitmap,
    isDark: Boolean = ShareThemeResolver.isNightMode(context)
) = withContext(Dispatchers.Default) {
    val colors = ShareThemeResolver.resolveColors(isDark)
    val sHeader = ensureSoftwareBitmap(header)
    val sMap = ensureSoftwareBitmap(map)

    val footerHeight = 125
    val totalWidth = sHeader.width.coerceAtLeast(sMap.width)
    val totalHeight = sHeader.height + sMap.height + footerHeight

    val combined = createBitmap(totalWidth, totalHeight)
    val canvas = Canvas(combined)
    canvas.drawColor(colors.canvasBackground)

    // Draw Sections
    canvas.drawBitmap(sHeader, 0f, 0f, null)
    canvas.drawBitmap(sMap, 0f, sHeader.height.toFloat(), null)

    // Draw Branding
    val footerTop = (sHeader.height + sMap.height).toFloat()
    drawFooter(context, canvas, totalWidth, footerTop, footerHeight, colors)

    saveAndShare(context, combined, "period_summary.png")
}

/**
 * Composes a detailed workout snapshot consisting of a Header, Map, Elevation profile, and optional Analytics.
 *
 * Implementation Logic:
 * 1. **Thread Safety**: Offloads heavy bitmap composition to [Dispatchers.Default] to
 *    prevent UI freezing.
 * 2. **Dynamic Assembly**: Calculates the total height based on which UI components
 *    (Header/Elevation/Analytics) are currently visible.
 * 3. **Branding**: Automatically appends the official application footer and logo.
 * 4. **Persistence**: Saves the final image to a secure cache directory and triggers
 *    the system sharing intent via a [FileProvider].
 */
suspend fun combineWorkoutAndShare(
    context: Context,
    header: Bitmap?,
    map: Bitmap,
    elevation: Bitmap?,
    analytics: Bitmap? = null,
    isDark: Boolean = ShareThemeResolver.isNightMode(context)
) = withContext(Dispatchers.Default) {
    val colors = ShareThemeResolver.resolveColors(isDark)
    val sHeader = header?.let { ensureSoftwareBitmap(it) }
    val sMap = ensureSoftwareBitmap(map)
    val sElevation = elevation?.let { ensureSoftwareBitmap(it) }
    val sAnalytics = analytics?.let { ensureSoftwareBitmap(it) }

    val footerHeight = WorkoutSnapshotLayoutCalculator.DEFAULT_FOOTER_HEIGHT
    val totalWidth = sMap.width // Use map width as the base

    val analyticsScale = if (sAnalytics != null && sAnalytics.width > 0) {
        WorkoutSnapshotLayoutCalculator.calculateScaleFactor(totalWidth, sAnalytics.width)
    } else 1.0f
    val scaledAnalyticsHeight = if (sAnalytics != null) (sAnalytics.height * analyticsScale).toInt() else 0

    val totalHeight = WorkoutSnapshotLayoutCalculator.calculateTotalHeight(
        headerHeight = sHeader?.height ?: 0,
        mapHeight = sMap.height,
        elevationHeight = sElevation?.height ?: 0,
        analyticsHeight = scaledAnalyticsHeight,
        footerHeight = footerHeight
    )

    val combined = createBitmap(totalWidth, totalHeight)
    val canvas = Canvas(combined)
    canvas.drawColor(colors.canvasBackground)

    var currentY = 0f

    // 1. Header
    sHeader?.let {
        canvas.drawBitmap(it, 0f, currentY, null)
        currentY += it.height
    }

    // 2. Map
    canvas.drawBitmap(sMap, 0f, currentY, null)
    currentY += sMap.height

    // 3. Elevation
    sElevation?.let {
        canvas.drawBitmap(it, 0f, currentY, null)
        currentY += it.height
    }

    // 4. Analytics (REQ-UI-205 / ATT-1393)
    sAnalytics?.let {
        if (analyticsScale != 1.0f) {
            val matrix = Matrix().apply {
                postScale(analyticsScale, analyticsScale)
                postTranslate(0f, currentY)
            }
            canvas.drawBitmap(it, matrix, null)
        } else {
            canvas.drawBitmap(it, 0f, currentY, null)
        }
        currentY += scaledAnalyticsHeight
    }

    // 5. Branding
    drawFooter(context, canvas, totalWidth, currentY, footerHeight, colors)

    saveAndShare(context, combined, "workout_summary.png")
}

// --- PRIVATE HELPERS ---

private fun ensureSoftwareBitmap(bitmap: Bitmap): Bitmap {
    return if (bitmap.config == Bitmap.Config.HARDWARE) {
        bitmap.copy(Bitmap.Config.ARGB_8888, false)
    } else bitmap
}

private fun drawFooter(
    context: Context,
    canvas: Canvas,
    width: Int,
    top: Float,
    height: Int,
    colors: ShareThemeColors
) {
    // Background
    val bgPaint = Paint().apply {
        color = colors.footerBackground
        isAntiAlias = true
    }
    canvas.drawRect(0f, top, width.toFloat(), top + height, bgPaint)

    // Logo
    val logo = BitmapFactory.decodeResource(context.resources, R.drawable.logo_512)
    val logoSize = 80
    val scaledLogo = logo.scale(logoSize, logoSize)
    val margin = 24f
    canvas.drawBitmap(scaledLogo, margin, top + (height - logoSize) / 2f, null)

    // Text
    val textPaint = Paint().apply {
        color = colors.footerTextColor
        textSize = 42f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        isAntiAlias = true
    }
    val appName = "aTrainingTracker"
    val textBounds = android.graphics.Rect()
    textPaint.getTextBounds(appName, 0, appName.length, textBounds)
    val textY = top + (height / 2f) + (textBounds.height() / 2f)

    canvas.drawText(appName, margin + logoSize + 20f, textY, textPaint)
}

private suspend fun saveAndShare(context: Context, bitmap: Bitmap, fileName: String) = withContext(Dispatchers.IO) {
    val imagesFolder = File(context.cacheDir, "images")
    if (!imagesFolder.exists()) imagesFolder.mkdirs()

    val file = File(imagesFolder, fileName)
    FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }

    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newRawUri("Summary", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    withContext(Dispatchers.Main) {
        context.startActivity(Intent.createChooser(intent, "Share with"))
    }
}
