package com.atrainingtracker.trainingtracker.ui.components.description

import android.view.View
import android.widget.TextView
import androidx.core.view.isVisible
import com.atrainingtracker.R

class DescriptionViewHolder(val rootView: View) {

    private val tvDescription: TextView = rootView.findViewById(R.id.tv_workout_description)
    private val tvGoal: TextView = rootView.findViewById(R.id.tv_workout_goal)
    private val tvMethod: TextView = rootView.findViewById(R.id.tv_workout_method)

    fun bind(data: DescriptionData) {
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
}