package com.atrainingtracker.trainingtracker.ui.components.workoutheader

import android.content.Context
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.MyHelper


class WorkoutHeaderViewHolder(val view: View) {

    private val context: Context = view.context

    // Views
    private val tvName: TextView = view.findViewById(R.id.tv_workout_summaries_name)
    private val tvDate: TextView = view.findViewById(R.id.tv_workout_summaries__date)
    private val tvTime: TextView = view.findViewById(R.id.tv_workout_summaries__time)
    private val ivSportIcon: ImageView = view.findViewById(R.id.iv_workout_summaries__sport_icon)
    private val tvSportName: TextView = view.findViewById(R.id.tv_workout_summaries__sport_name)
    private val tvEquipment: TextView = view.findViewById(R.id.tv_workout_summaries__equipment)

    fun bind(data: WorkoutHeaderData) {
        tvName.text = data.workoutName
        tvDate.text = data.formattedDate
        tvTime.text = data.formattedTime

        // TODO: move to some general helper class
        val iconResId: Int = when (data.sportType) {
            BSportType.RUN -> R.drawable.bsport_run
            BSportType.BIKE -> R.drawable.bsport_bike
            else -> R.drawable.bsport_other
        }

        ivSportIcon.setImageResource(iconResId)
        tvSportName.text = data.sportName

        tvEquipment.isVisible = data.equipmentName != null
        tvEquipment.text = data.equipmentName
    }
}