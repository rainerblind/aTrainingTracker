package com.atrainingtracker.trainingtracker.ui.components.workoutheader

import android.app.Dialog
import android.os.Bundle
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.compose.ui.semantics.setText
import androidx.compose.ui.semantics.text
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager

class EditWorkoutNameDialogFragment : DialogFragment() {

    // A modern, lambda-based listener. Much cleaner than a Java interface.
    var onWorkoutNameChanged: (() -> Unit)? = null

    private val workoutId: Long by lazy { requireArguments().getLong(ARG_WORKOUT_ID) }
    private val workoutName: String by lazy { requireArguments().getString(ARG_WORKOUT_NAME, "") }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = requireActivity().layoutInflater.inflate(R.layout.dialog_edit_name, null)
        val editTextWorkoutName = view.findViewById<EditText>(R.id.edit_text_workout_name)
        editTextWorkoutName.setText(workoutName)

        return AlertDialog.Builder(requireContext())
            .setTitle(R.string.edit_workout_name)
            .setView(view)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val newName = editTextWorkoutName.text.toString()
                if (newName.isNotBlank() && newName != workoutName) {
                    WorkoutSummariesDatabaseManager.updateWorkoutName(workoutId, newName)
                    // Invoke the lambda listener
                    onWorkoutNameChanged?.invoke()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
    }

    companion object {
        private const val ARG_WORKOUT_ID = "workoutId"
        private const val ARG_WORKOUT_NAME = "workoutName"

        // The 'newInstance' pattern, idiomatic to Kotlin.
        @JvmStatic
        fun newInstance(workoutId: Long, workoutName: String): EditWorkoutNameDialogFragment {
            return EditWorkoutNameDialogFragment().apply {
                arguments = bundleOf(
                    ARG_WORKOUT_ID to workoutId,
                    ARG_WORKOUT_NAME to workoutName
                )
            }
        }
    }
}