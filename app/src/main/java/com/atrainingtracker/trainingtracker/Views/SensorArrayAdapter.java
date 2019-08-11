package com.atrainingtracker.trainingtracker.Views;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.atrainingtracker.banalservice.Sensor.SensorType;

// TODO: show an image that represents the SensorType
// TODO: it would be awesome to show whether or not this sensor is currently available/active and its current value
public class SensorArrayAdapter extends ArrayAdapter<SensorType> {
    private static final String TAG = "SensorSpinnerAdapter";

    protected Context mContext;
    protected SensorType[] mSensorTypeArray;

    public SensorArrayAdapter(Context context, int textViewResourceId, SensorType[] sensorTypeArray) {
        super(context, textViewResourceId, sensorTypeArray);
        mContext = context;
        mSensorTypeArray = sensorTypeArray;
    }

    // This is for the "passive" state of the spinner
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            LayoutInflater inflater = LayoutInflater.from(mContext);
            convertView = inflater.inflate(android.R.layout.simple_spinner_item, parent, false);
        }

        // android.R.id.text1 is default text view in resource of the android.
        // android.R.layout.simple_spinner_item is default layout in resources of android.

        TextView label = convertView.findViewById(android.R.id.text1);
        // label.setTextColor(Color.BLACK);
        label.setText(mSensorTypeArray[position].getFullNameId());

        return label;
    }

    // And here is when the "chooser" is popped up
    @Override
    public View getDropDownView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            LayoutInflater inflater = LayoutInflater.from(mContext);
            convertView = inflater.inflate(android.R.layout.simple_spinner_item, parent, false);
        }

        TextView label = (TextView) convertView;
        // label.setTextColor(Color.BLACK);
        label.setText(mSensorTypeArray[position].getFullNameId());

        return label;
    }

}
