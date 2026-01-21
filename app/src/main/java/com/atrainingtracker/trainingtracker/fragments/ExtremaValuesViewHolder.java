package com.atrainingtracker.trainingtracker.fragments;

import android.content.Context;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.atrainingtracker.R;
import com.atrainingtracker.banalservice.BSportType;
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager;
import com.atrainingtracker.banalservice.sensor.SensorType;
import com.atrainingtracker.trainingtracker.TrainingApplication;
import com.atrainingtracker.trainingtracker.database.ExtremaType;
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager;

import java.util.List;

/**
 * A ViewHolder responsible for binding sensor extrema data (min/max/avg) to the UI
 * in the workout summaries list.
 * This is based on the logic from EditWorkoutFragment.
 */
public class ExtremaValuesViewHolder {
    private static final String TAG = ExtremaValuesViewHolder.class.getName();
    private static final boolean DEBUG = TrainingApplication.getDebug(false);

    private final Context mContext;
    private final TableLayout mContainer;
    private final LayoutInflater mInflater;
    private final long mWorkoutId;
    private final BSportType mBsportType;


    public ExtremaValuesViewHolder(@NonNull Context context, @NonNull TableLayout container, long workoutId, BSportType bSportType) {
        this.mContext = context;
        this.mContainer = container; // The view to inflate into
        this.mWorkoutId = workoutId;
        this.mBsportType = bSportType;
        this.mInflater = LayoutInflater.from(context);
    }

    /**
     * Fetches the sensor data and populates the container.
     * It inflates the base layout and then dynamically adds a row for each available sensor.
     * If no sensor data is found, the container's visibility is set to GONE.
     */
    public void bind() {
        if (DEBUG) Log.d(TAG, "build() ExtremaValues data for workoutId: " + mWorkoutId);

        mContainer.removeAllViews(); // Clear any previous views

        TableRow header = (TableRow) mInflater.inflate(R.layout.workout_summary__extrema_header, mContainer, false);
        mContainer.addView(header);

        boolean hasAnyData = false;
        // Use non-short-circuiting OR to ensure all are checked.
        hasAnyData |= addExtremaRow(mContainer, SensorType.HR);
        hasAnyData |= addExtremaRow(mContainer, SensorType.SPEED_mps);
        hasAnyData |= addExtremaRow(mContainer, SensorType.PACE_spm);
        hasAnyData |= addExtremaRow(mContainer, SensorType.CADENCE);
        hasAnyData |= addExtremaRow(mContainer, SensorType.POWER);
        hasAnyData |= addExtremaRow(mContainer, SensorType.TORQUE);
        hasAnyData |= addExtremaRow(mContainer, SensorType.PEDAL_POWER_BALANCE);
        hasAnyData |= addExtremaRow(mContainer, SensorType.PEDAL_SMOOTHNESS_L);
        hasAnyData |= addExtremaRow(mContainer, SensorType.PEDAL_SMOOTHNESS);
        hasAnyData |= addExtremaRow(mContainer, SensorType.PEDAL_SMOOTHNESS_R);
        hasAnyData |= addExtremaRow(mContainer, SensorType.ALTITUDE);
        hasAnyData |= addExtremaRow(mContainer, SensorType.TEMPERATURE);

        // Only show the container if we actually added any rows.
        mContainer.setVisibility(hasAnyData ? View.VISIBLE : View.GONE);
    }

    /**
     * Creates a TableRow for a given sensor and adds it to the parent table.
     * Returns true if the row was added, false otherwise.
     */
    private boolean addExtremaRow(@NonNull TableLayout table, @NonNull SensorType sensorType) {

        // show the pace only when the sportType is Run
        if (sensorType == SensorType.PACE_spm &&
                mBsportType != BSportType.RUN) {
            return false;
        }

        TableRow row = new TableRow(mContext);

        // Column 1: sensor name (including the unit)
        // String labelText = mContext.getString(R.string.extrema_value__unit_format, mContext.getString(sensorType.getShortNameId()), mContext.getString(sensorType.getUnitId()));
        // Column 1: sensor name
        String labelText = mContext.getString(sensorType.getShortNameId());
        row.addView(createTextView(labelText, Gravity.START, (float) 2.5));

        //  Add Min, Avg, Max (Column 2, 3, 4)
        boolean valuesAvailable = false;
        TextView textViewExtremaValue;
        for (ExtremaType extremaType : List.of(ExtremaType.MIN, ExtremaType.AVG, ExtremaType.MAX)) {
            textViewExtremaValue = createValueTextView(sensorType, extremaType, 2);
            if (textViewExtremaValue != null) {
                valuesAvailable = true;
                row.addView(textViewExtremaValue);
            } else {
                row.addView(createTextView("", Gravity.END, 2));
            }
        }
        // Column 5: Add the unit
        String unitText = mContext.getString(sensorType.getUnitId());
        row.addView(createTextView(unitText, Gravity.END, (float) 1.5));

        if (valuesAvailable) {
            table.addView(row);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Creates a TextView for a formatted sensor value (avg, max, or min).
     * returns null when there are are not values for the sensor.
     */
    private TextView createValueTextView(SensorType sensorType, ExtremaType extremaType, float weight) {
        Double extremaValue = WorkoutSummariesDatabaseManager.getExtremaValue(mWorkoutId, sensorType, extremaType);
        Log.d(TAG, sensorType.name() + " " + extremaType.name() + " extremaValue=" + extremaValue);
        if (extremaValue != null) {
            return createTextView(sensorType.getMyFormatter().format(extremaValue), Gravity.END, weight);
        } else {
            return null;
        }
    }

    /**
     * General purpose helper to create a TextView with standard properties.
     */
    private TextView createTextView(String text, int gravity, float weight) {
        // We shouldn't use the application context for creating views,
        // but since we are not using complex styles, it should be okay here.
        TextView tv = new TextView(mContext);
        tv.setText(text);
        tv.setGravity(gravity);
        TableRow.LayoutParams params = new TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, weight);
        tv.setLayoutParams(params);
        return tv;
    }
}
