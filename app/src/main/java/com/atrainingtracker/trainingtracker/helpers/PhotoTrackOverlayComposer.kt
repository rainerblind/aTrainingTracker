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

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.sensor.formater.AltitudeFormatter
import com.atrainingtracker.banalservice.sensor.formater.DistanceFormatter
import com.atrainingtracker.banalservice.sensor.formater.SpeedFormatter
import com.atrainingtracker.banalservice.sensor.formater.TimeFormatter
import com.atrainingtracker.trainingtracker.routes.GeoUtils
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.PolyUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Projected track geometry holding scaled canvas coordinates, start/end markers, and bounding envelope.
 * (REQ-UI-447 / ATT-30)
 */
data class ProjectedTrack(
    val path: Path,
    val startX: Float,
    val startY: Float,
    val endX: Float,
    val endY: Float,
    val bounds: RectF
)

/**
 * Pure 2D point representation for JVM unit testing without android.graphics dependencies.
 */
data class ProjectedPoint(val x: Float, val y: Float)

/**
 * Pure geometric projection envelope for JVM unit testing without android.graphics dependencies.
 */
data class TrackProjectionResult(
    val points: List<ProjectedPoint>,
    val startX: Float,
    val startY: Float,
    val endX: Float,
    val endY: Float,
    val boundsLeft: Float,
    val boundsTop: Float,
    val boundsRight: Float,
    val boundsBottom: Float
) {
    val width: Float get() = boundsRight - boundsLeft
    val height: Float get() = boundsBottom - boundsTop
}

/**
 * Engine for composing Strava/Garmin-style photo snapshots with 2D GPS track silhouette,
 * bottom gradient scrim, key workout metrics, and official branding footer (REQ-UI-447 / ATT-30).
 */
object PhotoTrackOverlayComposer {

    private const val MAX_DIMENSION = 1920

    /**
     * Safely decodes a photo from [Uri] applying [BitmapFactory.Options.inSampleSize]
     * to prevent OutOfMemoryError on high-resolution camera photos.
     */
    fun decodeSampledBitmap(context: Context, uri: Uri, maxDim: Int = MAX_DIMENSION): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }

            if (options.outWidth <= 0 || options.outHeight <= 0) return null

            var sampleSize = 1
            while (options.outWidth / sampleSize > maxDim || options.outHeight / sampleSize > maxDim) {
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            }?.let { ensureSoftwareBitmap(it) }
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Calculates equirectangular projection of [points] into the specified bounding envelope
     * without android.graphics dependencies (REQ-UI-447.2).
     */
    fun calculateTrackProjection(
        points: List<LatLng>,
        targetLeft: Float,
        targetTop: Float,
        targetRight: Float,
        targetBottom: Float
    ): TrackProjectionResult? {
        if (points.size < 2) return null

        val meanLatRad = Math.toRadians(points.map { it.latitude }.average())
        val cosMeanLat = cos(meanLatRad).coerceAtLeast(0.01)

        val minLat = points.minOf { it.latitude }
        val maxLat = points.maxOf { it.latitude }
        val minLng = points.minOf { it.longitude }
        val maxLng = points.maxOf { it.longitude }

        val xSpan = ((maxLng - minLng) * cosMeanLat).coerceAtLeast(1e-7)
        val ySpan = (maxLat - minLat).coerceAtLeast(1e-7)

        val targetWidth = (targetRight - targetLeft).coerceAtLeast(1f)
        val targetHeight = (targetBottom - targetTop).coerceAtLeast(1f)

        val scale = min(
            targetWidth / xSpan.toFloat(),
            targetHeight / ySpan.toFloat()
        )

        val projectedWidth = (xSpan * scale).toFloat()
        val projectedHeight = (ySpan * scale).toFloat()

        val offsetX = targetLeft + (targetWidth - projectedWidth) / 2f
        val offsetY = targetTop + (targetHeight - projectedHeight) / 2f

        val projectedPoints = ArrayList<ProjectedPoint>(points.size)
        points.forEach { pt ->
            val px = offsetX + ((pt.longitude - minLng) * cosMeanLat * scale).toFloat()
            val py = offsetY + ((maxLat - pt.latitude) * scale).toFloat()
            projectedPoints.add(ProjectedPoint(px, py))
        }

        val first = projectedPoints.first()
        val last = projectedPoints.last()

        return TrackProjectionResult(
            points = projectedPoints,
            startX = first.x,
            startY = first.y,
            endX = last.x,
            endY = last.y,
            boundsLeft = offsetX,
            boundsTop = offsetY,
            boundsRight = offsetX + projectedWidth,
            boundsBottom = offsetY + projectedHeight
        )
    }

    /**
     * Projects a list of [LatLng] coordinates into [targetRect] applying cosine-latitude
     * equirectangular scaling (Δx = Δλ · cos(φ_mean), Δy = -Δφ) to preserve the geometric
     * aspect ratio across all latitudes without distortion (REQ-UI-447.2).
     */
    fun projectTrack(points: List<LatLng>, targetRect: RectF): ProjectedTrack? {
        val result = calculateTrackProjection(
            points,
            targetRect.left,
            targetRect.top,
            targetRect.right,
            targetRect.bottom
        ) ?: return null

        val path = Path()
        result.points.forEachIndexed { i, pt ->
            if (i == 0) path.moveTo(pt.x, pt.y) else path.lineTo(pt.x, pt.y)
        }

        return ProjectedTrack(
            path = path,
            startX = result.startX,
            startY = result.startY,
            endX = result.endX,
            endY = result.endY,
            bounds = RectF(result.boundsLeft, result.boundsTop, result.boundsRight, result.boundsBottom)
        )
    }

    /**
     * Composes the photo with 2D GPS track silhouette, translucent bottom gradient scrim,
     * high-contrast metrics, and official branding footer.
     */
    fun composeOverlay(
        context: Context,
        photo: Bitmap,
        workoutData: WorkoutData
    ): Bitmap {
        val sPhoto = ensureSoftwareBitmap(photo)
        val width = sPhoto.width
        val height = sPhoto.height

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        // 1. Draw Photo
        canvas.drawBitmap(sPhoto, 0f, 0f, null)

        val scaleRef = width.toFloat() / 1080f

        // 2. Draw 2D GPS Track Silhouette in Upper-Right / Side Quadrant
        val points = try {
            PolyUtil.decode(workoutData.mapPolyline).filter {
                GeoUtils.isValidGpsCoordinate(it.latitude, it.longitude)
            }
        } catch (_: Throwable) {
            emptyList()
        }

        if (points.size >= 2) {
            val trackRect = RectF(
                width * 0.45f,
                height * 0.08f,
                width * 0.92f,
                height * 0.48f
            )
            val projected = projectTrack(points, trackRect)
            if (projected != null) {
                val strokeWidth = (8f * scaleRef).coerceAtLeast(4f)
                val shadowWidth = strokeWidth + (4f * scaleRef)

                // Shadow
                val shadowPaint = Paint().apply {
                    color = 0x88000000.toInt()
                    this.strokeWidth = shadowWidth
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                    isAntiAlias = true
                }
                canvas.save()
                canvas.translate(2f * scaleRef, 3f * scaleRef)
                canvas.drawPath(projected.path, shadowPaint)
                canvas.restore()

                // Foreground Track (Pure White for high visibility across photos)
                val trackPaint = Paint().apply {
                    color = Color.WHITE
                    this.strokeWidth = strokeWidth
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                    isAntiAlias = true
                }
                canvas.drawPath(projected.path, trackPaint)

                // Start Marker Dot (Green with white rim)
                val dotRadius = (7f * scaleRef).coerceAtLeast(5f)
                val rimRadius = dotRadius + (2f * scaleRef)

                val rimPaint = Paint().apply {
                    color = Color.WHITE
                    style = Paint.Style.FILL
                    isAntiAlias = true
                }
                val startPaint = Paint().apply {
                    color = 0xFF4CAF50.toInt()
                    style = Paint.Style.FILL
                    isAntiAlias = true
                }
                canvas.drawCircle(projected.startX, projected.startY, rimRadius, rimPaint)
                canvas.drawCircle(projected.startX, projected.startY, dotRadius, startPaint)

                // End Marker Dot (Orange/Red with white rim)
                val endPaint = Paint().apply {
                    color = 0xFFFF5722.toInt()
                    style = Paint.Style.FILL
                    isAntiAlias = true
                }
                canvas.drawCircle(projected.endX, projected.endY, rimRadius, rimPaint)
                canvas.drawCircle(projected.endX, projected.endY, dotRadius, endPaint)
            }
        }

        // 3. Bottom Gradient Scrim (Transparent -> 85% Dark Black for WCAG AAA compliance)
        val footerHeight = (125 * scaleRef).roundToInt().coerceAtLeast(100)
        val scrimHeight = (height * 0.35f).coerceAtLeast((220 * scaleRef) + footerHeight)
        val scrimTop = height - scrimHeight

        val scrimPaint = Paint().apply {
            shader = LinearGradient(
                0f, scrimTop,
                0f, height.toFloat(),
                Color.TRANSPARENT,
                0xD9000000.toInt(),
                Shader.TileMode.CLAMP
            )
            isAntiAlias = true
        }
        canvas.drawRect(0f, scrimTop, width.toFloat(), height.toFloat(), scrimPaint)

        // 4. Key Metrics Typography (WCAG AAA white text over dark scrim)
        val padX = 48f * scaleRef
        var currentY = scrimTop + (40f * scaleRef)

        // Title (Workout Name)
        val titlePaint = Paint().apply {
            color = Color.WHITE
            textSize = 38f * scaleRef
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val title = workoutData.workoutName.ifEmpty { workoutData.sportName }
        canvas.drawText(title, padX, currentY, titlePaint)

        // Date
        currentY += 32f * scaleRef
        val datePaint = Paint().apply {
            color = 0xCCFFFFFF.toInt()
            textSize = 22f * scaleRef
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }
        val dateText = "${workoutData.formattedDate}  •  ${workoutData.sportName}"
        canvas.drawText(dateText, padX, currentY, datePaint)

        // Metrics Grid (Distance, Duration, Ascent, Avg Speed/Power)
        currentY += 46f * scaleRef
        val colWidth = (width - 2 * padX) / 4f

        val labelPaint = Paint().apply {
            color = 0xB3FFFFFF.toInt()
            textSize = 18f * scaleRef
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }
        val valuePaint = Paint().apply {
            color = Color.WHITE
            textSize = 28f * scaleRef
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val distanceFormatter = DistanceFormatter()
        val timeFormatter = TimeFormatter()
        val altitudeFormatter = AltitudeFormatter()
        val speedFormatter = SpeedFormatter()

        val distanceVal = distanceFormatter.format_with_units(workoutData.totalDistance)
        val durationVal = timeFormatter.format(workoutData.activeTimeSec)
        val ascentVal = altitudeFormatter.format_with_units(workoutData.ascentMeters)
        val speedOrPowerVal = if (workoutData.normalizedPowerWatts != null && workoutData.normalizedPowerWatts > 0) {
            "${workoutData.normalizedPowerWatts.toInt()} W"
        } else {
            speedFormatter.format_with_units(workoutData.avgSpeedMps)
        }

        val statItems = listOf(
            context.getString(R.string.distance) to distanceVal,
            context.getString(R.string.time_active) to durationVal,
            context.getString(R.string.ascent) to ascentVal,
            (if (workoutData.normalizedPowerWatts != null && workoutData.normalizedPowerWatts > 0) context.getString(R.string.normalized_power_short) else context.getString(R.string.speed)) to speedOrPowerVal
        )

        statItems.forEachIndexed { idx, (label, valText) ->
            val colX = padX + (idx * colWidth)
            canvas.drawText(label, colX, currentY, labelPaint)
            canvas.drawText(valText, colX, currentY + (32f * scaleRef), valuePaint)
        }

        // 5. Official Branding Watermark
        val footerTop = (height - footerHeight).toFloat()
        val darkFooterColors = ShareThemeColors(
            canvasBackground = Color.BLACK,
            footerBackground = 0xAA000000.toInt(),
            footerTextColor = Color.WHITE
        )
        drawFooter(context, canvas, width, footerTop, footerHeight, darkFooterColors)

        return result
    }

    /**
     * Composes and dispatches the photo overlay snapshot via Android system share sheet.
     */
    suspend fun composeAndShare(
        context: Context,
        photo: Bitmap,
        workoutData: WorkoutData
    ) = withContext(Dispatchers.Default) {
        val composed = composeOverlay(context, photo, workoutData)
        saveAndShare(context, composed, "workout_photo_${workoutData.id}.png")
    }
}
