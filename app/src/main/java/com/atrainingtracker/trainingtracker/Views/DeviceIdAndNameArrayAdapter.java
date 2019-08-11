package com.atrainingtracker.trainingtracker.Views;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;


public class DeviceIdAndNameArrayAdapter extends ArrayAdapter<Long> {
    private static final String TAG = "DeviceIdAndNameSpinnerAdapter";

    protected Context mContext;
    protected Long[] mDeviceIdsArray;
    protected String[] mNamesArray;

    public DeviceIdAndNameArrayAdapter(Context context, int textViewResourceId, Long[] deviceIdsArray, String[] namesArray) {
        super(context, textViewResourceId, deviceIdsArray);
        mContext = context;
        mDeviceIdsArray = deviceIdsArray;
        mNamesArray = namesArray;
    }

    // This is for the "passive" state of the spinner
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            LayoutInflater inflater = LayoutInflater.from(mContext);
            convertView = inflater.inflate(android.R.layout.simple_spinner_item, parent, false);
        }

        TextView label = convertView.findViewById(android.R.id.text1);
        label.setText(mNamesArray[position]);

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
        label.setText(mNamesArray[position]);

        return label;
    }

}
