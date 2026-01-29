package com.atrainingtracker.trainingtracker.ui.components.workoutdescription

import android.app.Dialog
import android.os.Bundle
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import com.atrainingtracker.R

class EditDescriptionDialogFragment : DialogFragment() {

    // The listener reports back the new data.
    var onDescriptionChanged: ((description: String, goal: String, method: String) -> Unit)? = null

    private val initialDescription: String by lazy { requireArguments().getString(ARG_DESCRIPTION, "") }
    private val initialGoal: String by lazy { requireArguments().getString(ARG_GOAL, "") }
    private val initialMethod: String by lazy { requireArguments().getString(ARG_METHOD, "") }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = requireActivity().layoutInflater.inflate(R.layout.dialog_edit_description, null)
        val etDescription = view.findViewById<EditText>(R.id.edit_text_workout_description)
        val etGoal = view.findViewById<EditText>(R.id.edit_text_workout_goal)
        val etMethod = view.findViewById<EditText>(R.id.edit_text_workout_method)

        etDescription.setText(initialDescription)
        etGoal.setText(initialGoal)
        etMethod.setText(initialMethod)

        return AlertDialog.Builder(requireContext())
            .setTitle(R.string.title_edit_description)
            .setView(view)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                // The dialog's ONLY job is to gather the text and invoke the listener.
                onDescriptionChanged?.invoke(
                    etDescription.text.toString(),
                    etGoal.text.toString(),
                    etMethod.text.toString()
                )
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
    }

    companion object {
        private const val ARG_DESCRIPTION = "description"
        private const val ARG_GOAL = "goal"
        private const val ARG_METHOD = "method"

        @JvmStatic
        fun newInstance(description: String?, goal: String?, method: String?): EditDescriptionDialogFragment {
            return EditDescriptionDialogFragment().apply {
                arguments = bundleOf(
                    ARG_DESCRIPTION to description,
                    ARG_GOAL to goal,
                    ARG_METHOD to method
                )
            }
        }
    }
}