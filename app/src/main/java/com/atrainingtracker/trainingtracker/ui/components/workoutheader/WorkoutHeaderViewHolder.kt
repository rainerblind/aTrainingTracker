package com.atrainingtracker.trainingtracker.ui.components.workoutheader

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager


class WorkoutHeaderViewHolder(val view: View) {

    private var currentData: WorkoutHeaderData? = null

    // public menuButton. Others should add a click listener to it.
    val menuButton: View = view.findViewById(R.id.workout_header_menu_button)

    // Views
    private val tvName: TextView = view.findViewById(R.id.tv_workout_summaries_name)
    private val tvDate: TextView = view.findViewById(R.id.tv_workout_summaries__date)
    private val tvTime: TextView = view.findViewById(R.id.tv_workout_summaries__time)
    private val tvTrainerOrCommute: TextView = view.findViewById(R.id.tv_workout_summaries__trainer_or_commute)
    private val ivSportIcon: ImageView = view.findViewById(R.id.iv_workout_summaries__sport_icon)
    private val tvSportName: TextView = view.findViewById(R.id.tv_workout_summaries__sport_name)
    private val tvEquipment: TextView = view.findViewById(R.id.tv_workout_summaries__equipment)

    fun bind(data: WorkoutHeaderData) {
        this.currentData = data
        tvName.text = data.workoutName
        tvDate.text = data.formattedDate
        tvTime.text = data.formattedTime

        // set trainer or commute
        if (data.commute) {
            tvTrainerOrCommute.text = view.context.getString(R.string.commute)
            tvTrainerOrCommute.visibility = View.VISIBLE
        } else if (data.trainer) {
            tvTrainerOrCommute.text = when (data.bSportType) {
                BSportType.RUN -> view.context.getString(R.string.trainer_run)
                BSportType.BIKE -> view.context.getString(R.string.trainer_bike)
                else -> view.context.getString(R.string.trainer_general)
            }
            tvTrainerOrCommute.visibility = View.VISIBLE
        } else {
            tvTrainerOrCommute.visibility = View.GONE
        }

        // TODO: move to some general helper class
        val iconSportId: Int = when (data.bSportType) {
            BSportType.RUN -> R.drawable.bsport_run
            BSportType.BIKE -> R.drawable.bsport_bike
            else -> R.drawable.bsport_other
        }

        ivSportIcon.setImageResource(iconSportId)
        tvSportName.text = data.sportName

        tvEquipment.isVisible = data.equipmentName != null
        tvEquipment.text = data.equipmentName

        menuButton.isVisible = data.finished
    }
}