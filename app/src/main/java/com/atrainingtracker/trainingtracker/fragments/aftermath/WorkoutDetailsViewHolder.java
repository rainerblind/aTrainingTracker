package com.atrainingtracker.trainingtracker.fragments.aftermath;

import android.content.Context;
import android.database.Cursor;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.atrainingtracker.R;
import com.atrainingtracker.trainingtracker.MyHelper;
import com.atrainingtracker.banalservice.BSportType;
import com.atrainingtracker.banalservice.sensor.SensorType;
import com.atrainingtracker.banalservice.sensor.formater.DistanceFormatter;
import com.atrainingtracker.banalservice.sensor.formater.TimeFormatter;
import com.atrainingtracker.banalservice.sensor.formater.SpeedFormatter;
import com.atrainingtracker.banalservice.sensor.formater.PaceFormatter;
import com.atrainingtracker.trainingtracker.database.ExtremaType;
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager;
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries;

import java.util.Locale;

/**
 * A ViewHolder that manages the self-contained 'workout_summary__details.xml' layout.
 */
public class WorkoutDetailsViewHolder {

    private final View mView;
    private final Context mContext;

    // Formatters
    private final DistanceFormatter mDistanceFormatter;
    private final TimeFormatter mTimeFormatter;
    private final SpeedFormatter mSpeedFormatter;
    private final PaceFormatter mPaceFormatter;

    // Views
    private final TextView mTvDistance, mTvMaxDisplacement, mTvTimeActive, mTvTimeTotal;
    private final LinearLayout mRowSpeed, mRowAltitude;
    private final TextView mTvAvgSpeed, mTvAscent, mTvDescent, mTvMinAltitude, mTvMaxAltitude;

    public WorkoutDetailsViewHolder(@NonNull View view, Context context) {
        mView = view;
        mContext = context;

        mDistanceFormatter = new DistanceFormatter();
        mTimeFormatter = new TimeFormatter();
        mSpeedFormatter = new SpeedFormatter();
        mPaceFormatter = new PaceFormatter();

        mTvDistance = view.findViewById(R.id.detail_distance);
        mTvMaxDisplacement = view.findViewById(R.id.detail_max_displacement);
        mTvTimeActive = view.findViewById(R.id.detail_time_active);
        mTvTimeTotal = view.findViewById(R.id.detail_time_total);
        mRowSpeed = view.findViewById(R.id.workout_details_rowSpeed);
        mTvAvgSpeed = view.findViewById(R.id.detail_avg_speed);
        mRowAltitude = view.findViewById(R.id.workout_details_rowAltitude);
        mTvAscent = view.findViewById(R.id.detail_ascent);
        mTvDescent = view.findViewById(R.id.detail_descent);
        mTvMinAltitude = view.findViewById(R.id.detail_min_altitude);
        mTvMaxAltitude = view.findViewById(R.id.detail_max_altitude);
    }

    public View getView() {
        return mView;
    }

    /**
     * Main binding method. Coordinates calls to helper methods to populate the view.
     */
    public void bind(@NonNull Cursor cursor, long workoutId, BSportType bSportType) {
        bindDistanceAndTime(cursor, workoutId);
        bindSpeedAndPace(cursor, bSportType);
        bindAltitude(cursor, workoutId);

        // TODO: Hide the entire container if no data is bound (optional, but good practice)
        mView.setVisibility(View.VISIBLE);
    }

    /**
     * Binds data for the first row: Total Distance, Max Displacement, Active Time, Total Time.
     */
    private void bindDistanceAndTime(@NonNull Cursor cursor, long workoutId) {
        // Total Distance
        double distanceValue = cursor.getDouble(cursor.getColumnIndexOrThrow(WorkoutSummaries.DISTANCE_TOTAL_m));
        String distance = mDistanceFormatter.format(distanceValue);
        String distanceUnit = mContext.getString(MyHelper.getDistanceUnitNameId());
        mTvDistance.setText(mContext.getString(R.string.value_unit_string_string, distance, distanceUnit));

        // Max Displacement
        Double maxDisplacement = WorkoutSummariesDatabaseManager.getExtremaValue(workoutId, SensorType.LINE_DISTANCE_m, ExtremaType.MAX);
        if (maxDisplacement != null) {
            String maxDisplacementFormatted = mDistanceFormatter.format(maxDisplacement);
            String maxDisplacementString = mContext.getString(R.string.value_unit_string_string, maxDisplacementFormatted, distanceUnit);
            mTvMaxDisplacement.setText(mContext.getString(R.string.format_max_displacement, maxDisplacementString));
            mTvMaxDisplacement.setVisibility(View.VISIBLE);
        } else {
            mTvMaxDisplacement.setVisibility(View.GONE);
        }

        // Active Time
        int activeTime = cursor.getInt(cursor.getColumnIndexOrThrow(WorkoutSummaries.TIME_ACTIVE_s));
        mTvTimeActive.setText(mTimeFormatter.format(activeTime));

        // Total Time
        int totalTime = cursor.getInt(cursor.getColumnIndexOrThrow(WorkoutSummaries.TIME_TOTAL_s));
        String totalTimeString = mTimeFormatter.format(totalTime);
        mTvTimeTotal.setText(mContext.getString(R.string.total_time_format, totalTimeString));
    }

    /**
     * Binds data for the speed/pace row, choosing the correct format based on sport type.
     */
    private void bindSpeedAndPace(@NonNull Cursor cursor, BSportType bSportType) {
        float avgSpeed_mps = cursor.getFloat(cursor.getColumnIndexOrThrow(WorkoutSummaries.SPEED_AVERAGE_mps));
        String formattedValue;
        String unit;

        if (bSportType == BSportType.RUN) { // Pace for running
            float pace_spm = (avgSpeed_mps > 0) ? 1 / avgSpeed_mps : 0;
            formattedValue = mPaceFormatter.format(pace_spm);
            unit = mContext.getString(MyHelper.getPaceUnitNameId());
        } else { // Speed for all other sports
            formattedValue = mSpeedFormatter.format(avgSpeed_mps);
            unit = mContext.getString(MyHelper.getSpeedUnitNameId());
        }

        mTvAvgSpeed.setText(mContext.getString(R.string.value_unit_string_string, formattedValue, unit));
        mRowSpeed.setVisibility(View.VISIBLE);
    }

    /**
     * Binds all data for the altitude row: Ascent, Descent, Min/Max Altitude.
     * Manages the visibility of the entire row.
     */
    private void bindAltitude(@NonNull Cursor cursor, long workoutId) {
        boolean hasAltitudeData = false;

        // Ascent
        int ascentM = cursor.getInt(cursor.getColumnIndexOrThrow(WorkoutSummaries.ASCENDING));
        if (ascentM > 0) {
            setTextViewWithIntValue(mTvAscent, ascentM, "m");
            hasAltitudeData = true;
        } else {
            mTvAscent.setVisibility(View.GONE);
        }

        // Descent
        int descentM = cursor.getInt(cursor.getColumnIndexOrThrow(WorkoutSummaries.DESCENDING));
        if (descentM > 0) {
            setTextViewWithIntValue(mTvDescent, descentM, "m");
            hasAltitudeData = true;
        } else {
            mTvDescent.setVisibility(View.GONE);
        }

        // Min Altitude
        Double minAlt = WorkoutSummariesDatabaseManager.getExtremaValue(workoutId, SensorType.ALTITUDE, ExtremaType.MIN);
        if (setTextViewWithDoubleValue(mTvMinAltitude, minAlt, "m")) {
            hasAltitudeData = true;
        }

        // Max Altitude
        Double maxAlt = WorkoutSummariesDatabaseManager.getExtremaValue(workoutId, SensorType.ALTITUDE, ExtremaType.MAX);
        if (setTextViewWithDoubleValue(mTvMaxAltitude, maxAlt, "m")) {
            hasAltitudeData = true;
        }

        mRowAltitude.setVisibility(hasAltitudeData ? View.VISIBLE : View.GONE);
    }

    /**
     * Helper to format an integer value with a unit and set it on a TextView.
     */
    private void setTextViewWithIntValue(@NonNull TextView textView, int value, @NonNull String unit) {
        textView.setText(String.format(Locale.getDefault(), "%d %s", value, unit));
        textView.setVisibility(View.VISIBLE);
    }

    /**
     * Helper to format a Double value with a unit and set it on a TextView.
     * Handles null values by hiding the view.
     * @return True if the view was made visible, false otherwise.
     */
    private boolean setTextViewWithDoubleValue(@NonNull TextView textView, @Nullable Double value, @NonNull String unit) {
        if (value != null) {
            textView.setText(String.format(Locale.getDefault(), "%.0f %s", value, unit));
            textView.setVisibility(View.VISIBLE);
            return true;
        } else {
            textView.setVisibility(View.GONE);
            return false;
        }
    }
}
