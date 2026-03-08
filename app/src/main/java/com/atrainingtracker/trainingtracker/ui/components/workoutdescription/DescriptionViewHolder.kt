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

package com.atrainingtracker.trainingtracker.ui.components.workoutdescription

import android.view.View
import android.widget.TextView
import androidx.core.view.isVisible
import com.atrainingtracker.R

class DescriptionViewHolder(val rootView: View) {

    private var descriptionData: DescriptionData? = null

    private val tvDescription: TextView = rootView.findViewById(R.id.tv_workout_description)
    private val tvGoal: TextView = rootView.findViewById(R.id.tv_workout_goal)
    private val tvMethod: TextView = rootView.findViewById(R.id.tv_workout_method)

    fun bind(data: DescriptionData) {
        this.descriptionData = data

        bindText(tvDescription, data.description)
        bindText(tvGoal, data.goal)
        bindText(tvMethod, data.method)

        // Use the isVisible extension property for cleaner visibility logic
        rootView.isVisible = tvDescription.isVisible || tvGoal.isVisible || tvMethod.isVisible
    }

    private fun bindText(textView: TextView, text: String?) {
        // Use Kotlin's isNullOrEmpty() and the isVisible extension property
        val hasText = !text.isNullOrEmpty()
        textView.isVisible = hasText
        if (hasText) {
            textView.text = text
        }
    }

    fun getDescription(): String? {
        return descriptionData?.description
    }

    fun getGoal(): String? {
        return descriptionData?.goal
    }

    fun getMethod(): String? {
        return descriptionData?.method
    }
}