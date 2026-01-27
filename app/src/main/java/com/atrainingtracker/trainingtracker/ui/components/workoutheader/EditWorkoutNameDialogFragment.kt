package com.atrainingtracker.trainingtracker.ui.components.workoutheader

import android.app.Dialog
import android.os.Bundle
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import com.atrainingtracker.R

class EditWorkoutNameDialogFragment : DialogFragment() {

    // The listener
    var onWorkoutNameChanged: ((newName: String) -> Unit)? = null

    private val initialWorkoutName: String by lazy { requireArguments().getString(ARG_WORKOUT_NAME, "") }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = requireActivity().layoutInflater.inflate(R.layout.dialog_edit_name, null)
        val editTextWorkoutName = view.findViewById<EditText>(R.id.edit_text_workout_name)
        editTextWorkoutName.setText(initialWorkoutName)

        return AlertDialog.Builder(requireContext())
            .setTitle(R.string.edit_workout_name)
            .setView(view)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val newName = editTextWorkoutName.text.toString()
                if (newName.isNotBlank() && newName != initialWorkoutName) {
                    onWorkoutNameChanged?.invoke(newName)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
    }

    companion object {
        @JvmStatic
        fun newInstance(workoutName: String): EditWorkoutNameDialogFragment {
            return EditWorkoutNameDialogFragment().apply {
                arguments = bundleOf(
                    ARG_WORKOUT_NAME to workoutName
                )
            }
        }

        private const val ARG_WORKOUT_NAME = "workoutName"
    }
}