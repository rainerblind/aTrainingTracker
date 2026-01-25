package com.atrainingtracker.trainingtracker.fragments.aftermath;

import android.content.Context;
import android.database.Cursor;
import android.view.View;
import android.widget.TextView;

import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries;
import com.atrainingtracker.R;


public class WorkoutSummaryDescriptionViewHolder {
    public final View rootView;
    private final TextView tvDescription;
    private final TextView tvGoal;
    private final TextView tvMethod;

    public WorkoutSummaryDescriptionViewHolder(View view) {
        rootView = view; // The whole included layout
        tvDescription = view.findViewById(R.id.tv_workout_description);
        tvGoal = view.findViewById(R.id.tv_workout_goal);
        tvMethod = view.findViewById(R.id.tv_workout_method);
    }

    public void bind(String description, String goal, String method) {
        boolean hasContent = false;

        if (description != null && !description.trim().isEmpty()) {
            tvDescription.setText(description);
            tvDescription.setVisibility(View.VISIBLE);
            hasContent = true;
        } else {
            tvDescription.setVisibility(View.GONE);
        }

        if (goal != null && !goal.trim().isEmpty()) {
            tvGoal.setText(goal);
            tvGoal.setVisibility(View.VISIBLE);
            hasContent = true;
        } else {
            tvGoal.setVisibility(View.GONE);
        }

        if (method != null && !method.trim().isEmpty()) {
            tvMethod.setText(method);
            tvMethod.setVisibility(View.VISIBLE);
            hasContent = true;
        } else {
            tvMethod.setVisibility(View.GONE);
        }

        // Set the visibility of the entire included layout
        rootView.setVisibility(hasContent ? View.VISIBLE : View.GONE);

    }
}
