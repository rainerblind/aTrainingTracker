package com.atrainingtracker.banalservice.ui.devices.devicetabs

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.Protocol
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.helpers.UIHelper

class DeviceTypeChoiceArrayAdapter(
    context: Context,
    private val deviceTypes: List<DeviceType>,
    private val protocol: Protocol
) : ArrayAdapter<DeviceType>(context, R.layout.device_choice_row, deviceTypes) {

    private val inflater: LayoutInflater = LayoutInflater.from(context)

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val rowView = convertView ?: inflater.inflate(R.layout.device_choice_row, parent, false)

        val deviceType = deviceTypes[position]

        val textView = rowView.findViewById<TextView>(R.id.title)
        val iconView = rowView.findViewById<ImageView>(R.id.icon)

        textView.setText(UIHelper.getNameId(deviceType))
        iconView.setImageResource(UIHelper.getIconId(deviceType, protocol))

        return rowView
    }
}