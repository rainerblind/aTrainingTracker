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
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import com.atrainingtracker.R
import java.io.File
import java.io.FileOutputStream

/**
 * Shares a summary consisting of a Header and a Map.
 */
fun combineAndShare(context: Context, header: Bitmap, map: Bitmap) {
    val sHeader = ensureSoftwareBitmap(header)
    val sMap = ensureSoftwareBitmap(map)

    val footerHeight = 125
    val totalWidth = sHeader.width.coerceAtLeast(sMap.width)
    val totalHeight = sHeader.height + sMap.height + footerHeight

    val combined = createBitmap(totalWidth, totalHeight)
    val canvas = Canvas(combined)
    canvas.drawColor(Color.WHITE)

    // Draw Sections
    canvas.drawBitmap(sHeader, 0f, 0f, null)
    canvas.drawBitmap(sMap, 0f, sHeader.height.toFloat(), null)

    // Draw Branding
    val footerTop = (sHeader.height + sMap.height).toFloat()
    drawFooter(context, canvas, totalWidth, footerTop, footerHeight)

    saveAndShare(context, combined, "period_summary.png")
}

/**
 * Shares a detailed workout consisting of a Header, Map, and Elevation profile.
 */
fun combineWorkoutAndShare(context: Context, header: Bitmap, map: Bitmap, elevation: Bitmap) {
    val sHeader = ensureSoftwareBitmap(header)
    val sMap = ensureSoftwareBitmap(map)
    val sElevation = ensureSoftwareBitmap(elevation)

    val footerHeight = 125
    val totalWidth = sMap.width // Use map width as the base
    val totalHeight = sHeader.height + sMap.height + sElevation.height + footerHeight

    val combined = createBitmap(totalWidth, totalHeight)
    val canvas = Canvas(combined)
    canvas.drawColor(Color.WHITE)

    var currentY = 0f

    // 1. Header
    canvas.drawBitmap(sHeader, 0f, currentY, null)
    currentY += sHeader.height

    // 2. Map
    canvas.drawBitmap(sMap, 0f, currentY, null)
    currentY += sMap.height

    // 3. Elevation
    canvas.drawBitmap(sElevation, 0f, currentY, null)
    currentY += sElevation.height

    // 4. Branding
    drawFooter(context, canvas, totalWidth, currentY, footerHeight)

    saveAndShare(context, combined, "workout_summary.png")
}

// --- PRIVATE HELPERS ---

private fun ensureSoftwareBitmap(bitmap: Bitmap): Bitmap {
    return if (bitmap.config == Bitmap.Config.HARDWARE) {
        bitmap.copy(Bitmap.Config.ARGB_8888, false)
    } else bitmap
}

private fun drawFooter(context: Context, canvas: Canvas, width: Int, top: Float, height: Int) {
    // Background
    val bgPaint = Paint().apply {
        color = Color.parseColor("#F5F5F5")
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
        color = Color.DKGRAY
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

private fun saveAndShare(context: Context, bitmap: Bitmap, fileName: String) {
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
    context.startActivity(Intent.createChooser(intent, "Share with"))
}
