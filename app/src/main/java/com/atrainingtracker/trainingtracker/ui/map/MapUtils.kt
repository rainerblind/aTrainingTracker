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

package com.atrainingtracker.trainingtracker.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Typeface
import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.segments.SegmentHelper
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng

fun createSensorMarker(
    context: Context,
    @DrawableRes iconResId: Int,
    pinColor: Color,
    iconColor: Color = Color.White
): BitmapDescriptor? {
    val density = context.resources.displayMetrics.density
    val size = (36 * density).toInt()
    val iconSize = (18 * density).toInt()

    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val pinDrawable = ContextCompat.getDrawable(context, R.drawable.ic_map_pin_base)?.mutate()
    pinDrawable?.let {
        it.setTint(pinColor.toArgb())
        it.setBounds(0, 0, size, size)
        it.draw(canvas)
    }

    val sensorDrawable = ContextCompat.getDrawable(context, iconResId)?.mutate()
    sensorDrawable?.let {
        it.setTint(iconColor.toArgb())
        val left = (size - iconSize) / 2
        val top = (size - iconSize) / 3
        it.setBounds(left, top, left + iconSize, top + iconSize)
        it.draw(canvas)
    }

    return saveBitmapDescriptorFactoryFromBitmap(bitmap)
}

fun bitmapDescriptorFromVectorInternal(context: Context, resId: Int, sizeDp: Int, tint: Color?): BitmapDescriptor? {
    val drawable = ContextCompat.getDrawable(context, resId)?.mutate() ?: return null
    tint?.let { drawable.setTint(it.toArgb()) }
    val px = (sizeDp * context.resources.displayMetrics.density).toInt()
    drawable.setBounds(0, 0, px, px)
    val bm = createBitmap(px, px, Bitmap.Config.ARGB_8888)
    drawable.draw(Canvas(bm))
    return saveBitmapDescriptorFactoryFromBitmap(bm)
}

fun vectorToBitmap(
    context: Context,
    @DrawableRes resId: Int,
    sizeDp: Int,
    mirror: Boolean = false,
    tint: Color,
): BitmapDescriptor? {
    if (resId == -1) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(tint.toArgb(), hsv)
        return BitmapDescriptorFactory.defaultMarker(hsv[0])
    }

    val drawable = ContextCompat.getDrawable(context, resId)?.mutate()
        ?: return BitmapDescriptorFactory.defaultMarker()

    val density = context.resources.displayMetrics.density
    val sizePx = (sizeDp * density).toInt()

    val bitmap = createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    if (mirror) {
        canvas.scale(-1f, 1f, sizePx / 2f, sizePx / 2f)
    }

    drawable.setBounds(0, 0, sizePx, sizePx)
    drawable.draw(canvas)

    return saveBitmapDescriptorFactoryFromBitmap(bitmap)
}

fun createTextMarkerBitmap(
    context: Context,
    text: String,
    emoji: String,
    textSizeIn: Float,
    @DrawableRes iconResId: Int
): BitmapDescriptor? {
    val density = context.resources.displayMetrics.density
    val scaledTextSize = textSizeIn * density

    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.BLACK
        textSize = scaledTextSize
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    }

    val iconSize = (scaledTextSize * 1.2f).toInt()
    val iconBitmap = iconResId.let { res ->
        ContextCompat.getDrawable(context, res)?.let { drawable ->
            val bm = Bitmap.createBitmap(iconSize, iconSize, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bm)
            drawable.setBounds(0, 0, iconSize, iconSize)
            drawable.draw(canvas)
            bm
        }
    }

    val fullText = "$text $emoji"
    val textWidth = paint.measureText(fullText)
    val iconPadding = if (iconBitmap != null) 3f * density else 0f
    val totalWidth = (iconSize.toFloat() + iconPadding + textWidth + 4f).toInt()

    val fontMetrics = paint.fontMetrics
    val height = (fontMetrics.bottom - fontMetrics.top + 0.5f).toInt()
    val baseline = -fontMetrics.top

    val resultImage = Bitmap.createBitmap(totalWidth, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(resultImage)

    val shadowPaint = Paint(paint).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        color = android.graphics.Color.WHITE
    }

    val textX = if (iconBitmap != null) iconSize + iconPadding else 0f
    canvas.drawText(fullText, textX, baseline, shadowPaint)

    iconBitmap?.let {
        val glowPaint = Paint().apply {
            colorFilter = PorterDuffColorFilter(android.graphics.Color.WHITE, PorterDuff.Mode.SRC_IN)
        }
        canvas.drawBitmap(it, 2f, (height - iconSize) / 2f, glowPaint)
        canvas.drawBitmap(it, 0f, (height - iconSize) / 2f, null)
    }

    canvas.drawText(fullText, textX, baseline, paint)

    return saveBitmapDescriptorFactoryFromBitmap(resultImage)
}

fun calculateOrthogonalLine(point: LatLng, nextPoint: LatLng): List<LatLng> {
    val halfLengthMeters = 10

    val latDegreeInMeters = SegmentHelper.LatitudeDegreeInMeters(point)
    val lonDegreeInMeters = SegmentHelper.LongitudeDegreeInMeters(point)

    val deltaLatM = (nextPoint.latitude - point.latitude) * latDegreeInMeters
    val deltaLonM = (nextPoint.longitude - point.longitude) * lonDegreeInMeters

    val length = Math.sqrt(deltaLatM * deltaLatM + deltaLonM * deltaLonM)
    if (length == 0.0) return emptyList()

    val scaledDeltaLat = halfLengthMeters * deltaLatM / length
    val scaledDeltaLon = halfLengthMeters * deltaLonM / length

    return listOf(
        LatLng(point.latitude + scaledDeltaLon / latDegreeInMeters,
            point.longitude - scaledDeltaLat / lonDegreeInMeters),
        LatLng(point.latitude - scaledDeltaLon / latDegreeInMeters,
            point.longitude + scaledDeltaLat / lonDegreeInMeters)
    )
}

fun calculateBearing(start: LatLng, end: LatLng): Double {
    val lat1 = Math.toRadians(start.latitude)
    val lon1 = Math.toRadians(start.longitude)
    val lat2 = Math.toRadians(end.latitude)
    val lon2 = Math.toRadians(end.longitude)
    val dLon = lon2 - lon1
    val y = Math.sin(dLon) * Math.cos(lat2)
    val x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLon)
    return (Math.toDegrees(Math.atan2(y, x)) + 360.0) % 360.0
}

fun saveBitmapDescriptorFactoryFromBitmap(bm: Bitmap): BitmapDescriptor? {
    try {
        return BitmapDescriptorFactory.fromBitmap(bm)
    } catch (e: Exception) {
        return null
    }
}

/**
 * Adds intermediate points to a path if the distance between consecutive points
 * exceeds [maxDistanceMeters]. This improves heatmap quality for sparse data.
 */
fun densifyPath(path: List<LatLng>, maxDistanceMeters: Double): List<LatLng> {
    if (path.isEmpty()) return emptyList()
    val result = mutableListOf<LatLng>()

    for (i in 0 until path.size - 1) {
        val start = path[i]
        val end = path[i + 1]
        result.add(start)

        val distance = com.google.maps.android.SphericalUtil.computeDistanceBetween(start, end)
        if (distance > maxDistanceMeters) {
            val numSegments = (distance / maxDistanceMeters).toInt()
            for (j in 1..numSegments) {
                val fraction = j.toDouble() / (numSegments + 1)
                result.add(com.google.maps.android.SphericalUtil.interpolate(start, end, fraction))
            }
        }
    }
    result.add(path.last())
    return result
}

/**
 * Creates a HeatmapTileProvider with the standard training heatmap styling.
 */
fun createHeatmapProvider(
    allPaths: List<List<LatLng>>,
    opacity: Double = 0.8,
    radius: Int = 10
): com.google.maps.android.heatmaps.HeatmapTileProvider? {
    if (allPaths.isEmpty()) return null

    val allPoints = allPaths.flatMap { path ->
        densifyPath(path, 5.0).map { com.google.maps.android.heatmaps.WeightedLatLng(it, 2.0) }
    }

    if (allPoints.isEmpty()) return null

    // Modern sequential Blue gradient (Cyan -> Blue -> Deep Indigo)
    val colors = intArrayOf(
        0xFF00E5FF.toInt(), // Low density: Vibrant Cyan
        0xFF0000FF.toInt(), // Medium: The "Identity" Blue
        0xFF311B92.toInt()  // High density: Deep Indigo
    )
    val startPoints = floatArrayOf(0.2f, 0.6f, 1.0f)
    val gradient = com.google.maps.android.heatmaps.Gradient(colors, startPoints)

    return com.google.maps.android.heatmaps.HeatmapTileProvider.Builder()
        .weightedData(allPoints)
        .opacity(opacity)
        .radius(radius)
        .gradient(gradient)
        .build()
}
