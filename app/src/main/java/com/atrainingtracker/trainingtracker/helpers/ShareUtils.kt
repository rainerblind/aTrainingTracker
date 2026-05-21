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
import android.graphics.Canvas
import android.graphics.Color
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import androidx.core.graphics.createBitmap

fun combineAndShare(context: Context, header: Bitmap, map: Bitmap) {
    // 1. Ensure both bitmaps are in a software-compatible format
    // This prevents the "Hardware bitmaps" crash on Android 8.0+
    val softwareHeader = if (header.config == Bitmap.Config.HARDWARE) {
        header.copy(Bitmap.Config.ARGB_8888, false)
    } else header

    val softwareMap = if (map.config == Bitmap.Config.HARDWARE) {
        map.copy(Bitmap.Config.ARGB_8888, false)
    } else map

    // 2. Create a combined bitmap (Software-backed by default)
    val combined = createBitmap(
        softwareHeader.width.coerceAtLeast(softwareMap.width),
        softwareHeader.height + softwareMap.height
    )

    val canvas = Canvas(combined)
    canvas.drawColor(Color.WHITE) // Background color
    canvas.drawBitmap(softwareHeader, 0f, 0f, null)
    canvas.drawBitmap(softwareMap, 0f, softwareHeader.height.toFloat(), null)

    // 3. Save to cache
    val imagesFolder = File(context.cacheDir, "images")
    imagesFolder.mkdirs()
    val file = File(imagesFolder, "period_summary.png")
    FileOutputStream(file).use {
        combined.compress(Bitmap.CompressFormat.PNG, 100, it)
    }

    // 4. Share Intent
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newRawUri("Training Summary", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share Training Stats"))
}