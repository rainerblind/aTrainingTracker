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

package com.atrainingtracker.trainingtracker.ui.dialogs

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.atrainingtracker.BuildConfig
import com.atrainingtracker.R
import com.google.android.play.core.review.ReviewManagerFactory

/**
 * Celebratory Material 3 dialog presented upon reaching the athlete's very first
 * workout milestone ever (e.g. 100th ride or run) (REQ-REV-003 / ATT-2308).
 *
 * Features:
 * 1. Google Play In-App Review launch with store URI fallback.
 * 2. Direct feedback email draft to Rainer.
 * 3. Authentic indie supporter appeal note for Free-tier athletes (omitted for Pro/Pioneers).
 */
@Composable
fun FirstJubileeCelebrationDialog(
    count: Int,
    sportName: String,
    isProActive: Boolean,
    onRequestInAppReview: () -> Unit,
    onSendFeedbackEmail: () -> Unit,
    onNavigateToSupporter: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        icon = {
            Icon(
                imageVector = Icons.Filled.EmojiEvents,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
        },
        title = {
            Text(
                text = stringResource(R.string.jubilee_dialog_title, count, sportName),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.jubilee_dialog_message, count, sportName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                // Indie Supporter / Pro Appeal Note (Free Tier Only)
                if (!isProActive) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.jubilee_supporter_appeal_title),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Text(
                                text = stringResource(R.string.jubilee_supporter_appeal_message),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            FilledTonalButton(
                                onClick = onNavigateToSupporter,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.VolunteerActivism,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.size(8.dp))
                                Text(text = stringResource(R.string.jubilee_action_upgrade_pro))
                            }
                        }
                    }
                }

                // Primary Review Action
                Button(
                    onClick = onRequestInAppReview,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(text = stringResource(R.string.jubilee_action_review))
                }

                // Secondary Email Feedback Action
                OutlinedButton(
                    onClick = onSendFeedbackEmail,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Filled.Email,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(text = stringResource(R.string.jubilee_action_email_feedback))
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.jubilee_action_dismiss),
                    textAlign = TextAlign.Center
                )
            }
        }
    )
}

/**
 * Unwraps nested [ContextWrapper] instances (e.g. from Compose theming or dependency injection)
 * to locate the hosting [Activity], or returns null if not an Activity context.
 */
internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext?.findActivity()
    else -> null
}

/**
 * Helper object managing Google Play In-App Review requests and feedback email dispatches.
 */
object FirstJubileeActionHelper {

    /**
     * Launches the official Google Play In-App Review flow (ReviewManager).
     * Falls back seamlessly to opening the app's Play Store page if unavailable, rejected,
     * running in BuildConfig.DEBUG, or completing without user UI interaction (< 300 ms).
     */
    @JvmStatic
    fun launchInAppReviewOrStore(context: Context) {
        if (BuildConfig.DEBUG) {
            // Google Play Core ReviewManager returns synthetic no-op tokens in debug/sideloaded builds.
            // In debug builds, guarantee immediate visible feedback by directly opening the Play Store.
            openPlayStorePage(context)
            return
        }

        val activity = context.findActivity()
        if (activity != null) {
            try {
                val manager = ReviewManagerFactory.create(activity)
                val request = manager.requestReviewFlow()
                request.addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val reviewInfo = task.result
                        val launchStartTime = System.currentTimeMillis()
                        val flow = manager.launchReviewFlow(activity, reviewInfo)
                        flow.addOnCompleteListener {
                            val elapsed = System.currentTimeMillis() - launchStartTime
                            // If review flow terminated immediately (< 300 ms), Play Core suppressed UI
                            // (e.g. quota-throttled or sideloaded). Guarantee visible action via store page.
                            if (elapsed < 300) {
                                openPlayStorePage(context)
                            }
                        }
                    } else {
                        openPlayStorePage(context)
                    }
                }
                return
            } catch (t: Throwable) {
                // Fallback to store page
            }
        }
        openPlayStorePage(context)
    }

    /**
     * Fallback opening market://details?id=... or web Play Store URL.
     */
    @JvmStatic
    fun openPlayStorePage(context: Context) {
        val packageName = context.packageName.removeSuffix(".debug")
        val marketUri = Uri.parse("market://details?id=$packageName")
        val intent = Intent(Intent.ACTION_VIEW, marketUri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            val webUri = Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, webUri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            } catch (ignored: Exception) {}
        }
    }

    /**
     * Launches an email draft to Rainer with pre-filled subject and version information.
     */
    @JvmStatic
    fun launchFeedbackEmail(context: Context) {
        val subject = "aTrainingTracker Feedback (${BuildConfig.VERSION_NAME})"
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:rainer.blind@gmail.com")
            putExtra(Intent.EXTRA_SUBJECT, subject)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            // Intent couldn't be resolved
        }
    }
}
