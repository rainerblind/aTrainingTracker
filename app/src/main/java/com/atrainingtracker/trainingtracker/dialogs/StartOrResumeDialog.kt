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

package com.atrainingtracker.trainingtracker.dialogs

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.helpers.ProcessExitReasonHelper
import com.atrainingtracker.trainingtracker.helpers.ProcessExitReasonHelper.KillReason
import com.atrainingtracker.trainingtracker.interfaces.StartOrResumeInterface

/**
 * Modernized recovery dialog that presents forensic process termination diagnosis,
 * progressive escalation on repeat battery kills, and battery optimization settings shortcuts
 * when recovering unfinalized workouts (REQ-STB-012, REQ-STB-003, ATT-2079).
 */
class StartOrResumeDialog : DialogFragment() {

    companion object {
        @JvmField
        val TAG: String = StartOrResumeDialog::class.java.name
    }

    internal var startOrResumeInterface: StartOrResumeInterface? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        attachInterface(context)
    }

    internal fun attachInterface(context: Context) {
        if (context is StartOrResumeInterface) {
            startOrResumeInterface = context
        } else {
            throw ClassCastException("$context must implement StartOrResumeInterface")
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        val diagnosis = ProcessExitReasonHelper.resolveKillReason(context)

        val builder = AlertDialog.Builder(context)
        builder.setTitle(R.string.unfinished_workout_title)

        val message = buildString {
            when (diagnosis.reason) {
                KillReason.BATTERY_KILL -> {
                    when (diagnosis.escalationLevel) {
                        1 -> {
                            append(getString(R.string.kill_reason_battery_stage1))
                            if (diagnosis.isStravaActive) {
                                append("\n\n")
                                append(getString(R.string.kill_reason_battery_stage1_strava))
                            }
                        }
                        2 -> {
                            append(getString(R.string.kill_reason_battery_stage2))
                        }
                        else -> {
                            append(getString(R.string.kill_reason_battery_stage3))
                        }
                    }
                }
                KillReason.LOW_MEMORY -> {
                    append(getString(R.string.kill_reason_low_memory))
                }
                KillReason.PERMISSION_REVOKED -> {
                    append(getString(R.string.kill_reason_permission_revoked))
                }
                KillReason.GENERIC_UNFINISHED -> {
                    append(getString(R.string.start_or_resume_dialog_message))
                }
            }

            if (diagnosis.reason != KillReason.GENERIC_UNFINISHED) {
                append("\n\n")
                append(getString(R.string.start_or_resume_dialog_message))
            }
        }

        builder.setMessage(message)

        if (diagnosis.shouldShowBatteryButton) {
            builder.setNeutralButton(R.string.action_disable_battery_optimization) { dialog, _ ->
                ProcessExitReasonHelper.openBatteryOptimizationSettings(context)
                dialog.dismiss()
            }
        }

        builder.setPositiveButton(R.string.start_new_workout) { dialog, _ ->
            startOrResumeInterface?.chooseStart()
            dialog.dismiss()
        }

        builder.setNegativeButton(R.string.resume_workout) { dialog, _ ->
            startOrResumeInterface?.chooseResume()
            dialog.dismiss()
        }

        return builder.create()
    }
}
