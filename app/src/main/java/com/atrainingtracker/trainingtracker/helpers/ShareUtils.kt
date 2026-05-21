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

fun combineAndShare(context: Context, header: Bitmap, map: Bitmap) {
    // 1. Handle Hardware Bitmaps
    val softwareHeader = if (header.config == Bitmap.Config.HARDWARE) {
        header.copy(Bitmap.Config.ARGB_8888, false)
    } else header

    val softwareMap = if (map.config == Bitmap.Config.HARDWARE) {
        map.copy(Bitmap.Config.ARGB_8888, false)
    } else map

    // 2. Define Footer Dimensions
    val footerHeight = 125
    val totalWidth = softwareHeader.width.coerceAtLeast(softwareMap.width)
    val totalHeight = softwareHeader.height + softwareMap.height + footerHeight

    // 3. Create the Bitmap and Canvas
    val combined = createBitmap(totalWidth, totalHeight)
    val canvas = Canvas(combined)
    canvas.drawColor(Color.WHITE)

    // 4. Draw Header and Map
    canvas.drawBitmap(softwareHeader, 0f, 0f, null)
    canvas.drawBitmap(softwareMap, 0f, softwareHeader.height.toFloat(), null)

    // 5. DRAW THE WATERMARK FOOTER
    val footerTop = (softwareHeader.height + softwareMap.height).toFloat()

    // Footer Background
    val bgPaint = Paint().apply {
        color = Color.parseColor("#F5F5F5")
        isAntiAlias = true
    }
    canvas.drawRect(0f, footerTop, totalWidth.toFloat(), totalHeight.toFloat(), bgPaint)

    // Draw Logo (using android.graphics.Bitmap)
    val logo = BitmapFactory.decodeResource(context.resources, R.drawable.logo_512)
    val logoSize = 80
    val scaledLogo = logo.scale(logoSize, logoSize)

    val margin = 24f
    canvas.drawBitmap(scaledLogo, margin, footerTop + (footerHeight - logoSize) / 2f, null)

    // Draw App Name (using android.graphics.Paint)
    val textPaint = Paint().apply {
        color = Color.DKGRAY
        textSize = 42f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        isAntiAlias = true
    }

    // Calculate vertical center for text
    val textBounds = android.graphics.Rect()
    val appName = "aTrainingTracker"
    textPaint.getTextBounds(appName, 0, appName.length, textBounds)
    val textY = footerTop + (footerHeight / 2f) + (textBounds.height() / 2f)

    canvas.drawText(
        appName,
        margin + logoSize + 20f,
        textY,
        textPaint
    )

    // 6. Save and Share
    val imagesFolder = File(context.cacheDir, "images")
    if (!imagesFolder.exists()) imagesFolder.mkdirs()

    val file = File(imagesFolder, "period_summary.png")
    FileOutputStream(file).use { combined.compress(Bitmap.CompressFormat.PNG, 100, it) }

    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newRawUri("Training Summary", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share Training Stats"))
}
