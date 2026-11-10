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

package com.atrainingtracker.trainingtracker.ui.settings.supporter

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atrainingtracker.BuildConfig
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.monetization.SupporterBasePlan
import com.atrainingtracker.trainingtracker.monetization.SupporterTier
import com.atrainingtracker.trainingtracker.settings.SettingsDataStore
import com.atrainingtracker.trainingtracker.ui.branding.LauncherIconVariant
import kotlinx.coroutines.launch

/**
 * Modular subcomposables supporting [SupporterScreen] (REQ-SET-086, Rule 23).
 */
@Composable
internal fun PioneerStatusBanner(sinceYear: Int? = null) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_supporter_emblem_pioneer),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                val bannerTitle = if (sinceYear != null) {
                    stringResource(R.string.pioneer_badge_label_with_year, sinceYear)
                } else {
                    stringResource(R.string.pioneer_badge_label)
                }
                Text(
                    text = bannerTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.pioneer_thank_you_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                )
            }
        }
    }
}

@Composable
internal fun ActiveTierBanner(tier: SupporterTier) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val emblemRes = LauncherIconVariant.fromTier(tier).emblemRes
            if (emblemRes != null) {
                Icon(
                    painter = painterResource(id = emblemRes),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(36.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(36.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = stringResource(R.string.supporter_active_tier_prefix, stringResource(tier.titleRes)),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.supporter_active_tier_thank_you),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                )
            }
        }
    }
}

/**
 * Visual hierarchy states for supporter subscription plan cards (REQ-UI-501.1).
 */
internal enum class PlanCardHierarchyState {
    LOWER_TIER,
    CURRENT_TIER,
    UPGRADE_TIER
}

internal fun getPlanCardHierarchyState(
    plan: SupporterBasePlan,
    effectiveTier: SupporterTier
): PlanCardHierarchyState {
    return when {
        plan.tier == effectiveTier -> PlanCardHierarchyState.CURRENT_TIER
        effectiveTier.isPro && effectiveTier != SupporterTier.PIONEER && plan.tier.rank < effectiveTier.rank -> PlanCardHierarchyState.LOWER_TIER
        else -> PlanCardHierarchyState.UPGRADE_TIER
    }
}

@Composable
internal fun SupporterPlanCard(
    plan: SupporterBasePlan,
    isCurrentTier: Boolean = false,
    effectiveTier: SupporterTier = if (isCurrentTier) plan.tier else SupporterTier.FREE,
    isPreSelected: Boolean = false,
    onSelectPlan: () -> Unit
) {
    val state = getPlanCardHierarchyState(plan, effectiveTier)
    val showRecommendation = plan.isRecommended && effectiveTier.rank < SupporterTier.SILVER.rank

    Card(
        colors = CardDefaults.cardColors(
            containerColor = when {
                isPreSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                state == PlanCardHierarchyState.CURRENT_TIER -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                state == PlanCardHierarchyState.UPGRADE_TIER && showRecommendation -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                else -> MaterialTheme.colorScheme.surface
            }
        ),
        shape = RoundedCornerShape(12.dp),
        border = when {
            isPreSelected -> BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
            state == PlanCardHierarchyState.CURRENT_TIER -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
            state == PlanCardHierarchyState.UPGRADE_TIER && showRecommendation -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
            else -> BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val emblemRes = LauncherIconVariant.fromTier(plan.tier).emblemRes
                if (emblemRes != null) {
                    Icon(
                        painter = painterResource(id = emblemRes),
                        contentDescription = null,
                        tint = Color.Unspecified,
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                }
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .alpha(if (state == PlanCardHierarchyState.LOWER_TIER) 0.85f else 1f)
                ) {
                    if (showRecommendation) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Text(
                                text = stringResource(R.string.supporter_recommended_badge),
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    Text(
                        text = stringResource(plan.tier.titleRes),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val periodString = if (plan.period == "month") {
                        stringResource(R.string.supporter_period_month)
                    } else {
                        stringResource(R.string.supporter_period_year)
                    }
                    Text(
                        text = stringResource(R.string.supporter_price_per_period, plan.priceFormatted, periodString),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                val buttonContentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)

                when (state) {
                    PlanCardHierarchyState.CURRENT_TIER -> {
                        OutlinedButton(
                            onClick = {},
                            enabled = false,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = buttonContentPadding
                        ) {
                            Text(
                                text = stringResource(R.string.supporter_plan_button_active),
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                    PlanCardHierarchyState.LOWER_TIER -> {
                        OutlinedButton(
                            onClick = onSelectPlan,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = buttonContentPadding
                        ) {
                            Text(
                                text = stringResource(R.string.supporter_plan_button_switch),
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                    PlanCardHierarchyState.UPGRADE_TIER -> {
                        Button(
                            onClick = onSelectPlan,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = buttonContentPadding
                        ) {
                            Text(
                                text = stringResource(R.string.supporter_plan_button_support),
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }
    }
}

internal fun handlePlanSelection(
    context: Context,
    plan: SupporterBasePlan,
    settingsDataStore: SettingsDataStore
) {
    if (BuildConfig.DEBUG) {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
            settingsDataStore.setSupporterTier(plan.tier)
            Toast.makeText(
                context,
                "Debug: Switched to ${plan.tier.name} tier",
                Toast.LENGTH_SHORT
            ).show()
        }
    } else {
        try {
            val intent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://play.google.com/store/account/subscriptions")
            )
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Google Play Store unavailable", Toast.LENGTH_SHORT).show()
        }
    }
}

@Preview(name = "SupporterPlanCard - Lower Tier (Bronze with Active Silver)")
@Composable
private fun SupporterPlanCardLowerTierPreview() {
    MaterialTheme {
        SupporterPlanCard(
            plan = SupporterBasePlan.PLANS[1], // bronze-yearly
            effectiveTier = SupporterTier.SILVER,
            onSelectPlan = {}
        )
    }
}

@Preview(name = "SupporterPlanCard - Current Tier (Silver Active)")
@Composable
private fun SupporterPlanCardCurrentTierPreview() {
    MaterialTheme {
        SupporterPlanCard(
            plan = SupporterBasePlan.PLANS[2], // silver-yearly
            effectiveTier = SupporterTier.SILVER,
            onSelectPlan = {}
        )
    }
}

@Preview(name = "SupporterPlanCard - Upgrade Tier (Gold with Active Silver)")
@Composable
private fun SupporterPlanCardUpgradeTierPreview() {
    MaterialTheme {
        SupporterPlanCard(
            plan = SupporterBasePlan.PLANS[3], // gold-yearly
            effectiveTier = SupporterTier.SILVER,
            onSelectPlan = {}
        )
    }
}

