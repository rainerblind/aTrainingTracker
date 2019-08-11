package com.atrainingtracker.banalservice.helpers;

import com.atrainingtracker.R;
import com.atrainingtracker.banalservice.Devices.DeviceType;
import com.atrainingtracker.banalservice.Protocol;

public class UIHelper {
    public static int getIconId(DeviceType deviceType, Protocol protocol) {
        // TODO: must also depend on the protocol
        // TODO: remove this functionality from DeviceType!
        switch (protocol) {
            case ANT_PLUS:
                switch (deviceType) {
                    case HRM:
                        return R.drawable.hr;
                    case BIKE_SPEED:
                        return R.drawable.bike_spd;
                    case BIKE_CADENCE:
                        return R.drawable.bike_cad;
                    case BIKE_SPEED_AND_CADENCE:
                        return R.drawable.bike_speed_and_cadence;
                    case BIKE_POWER:
                        return R.drawable.bike_pwr;
                    case RUN_SPEED:
                        return R.drawable.run_spd;
                    case ENVIRONMENT:
                        return R.drawable.temp;
                }
            case BLUETOOTH_LE:
                switch (deviceType) {
                    case HRM:
                        return R.drawable.bt_hr;
                    case BIKE_SPEED:
                        return R.drawable.bt_bike_spd;
                    case BIKE_CADENCE:
                        return R.drawable.bt_bike_cad;
                    case BIKE_SPEED_AND_CADENCE:
                        return R.drawable.bt_bike_speed_and_cadence;
                    case BIKE_POWER:
                        return R.drawable.bt_bike_pwr;
                    case RUN_SPEED:
                        return R.drawable.bt_run;
                }
        }
        return protocol.getIconId();
        // return getIconId(protocol);			 // TODO: other (costum?) icon
    }

//	public static int getLargeIconId(DeviceType deviceType, Protocol protocol) 
//	{
//		// TODO: must also depend on the protocol
//		// TODO: remove this functionality from DeviceType!
//		switch (deviceType) {
//		case HRM:
//			return R.drawable.hr_large;
//		case BIKE_SPEED:
//			return R.drawable.bike_spd_large;
//		case BIKE_CADENCE:
//			return R.drawable.bike_cad_large;
//		case BIKE_SPEED_AND_CADENCE:
//			return R.drawable.bike_speed_and_cadence_large;
//		case BIKE_POWER:
//			return R.drawable.bike_pwr_large;
//		case RUN_SPEED:
//			return R.drawable.run_spd_large;
//		case ENVIRONMENT:
//			return R.drawable.temp_large;
//		default:
//			return R.drawable.ant_logo_large;	// TODO: other (costum?) icon		
//		}
//	}

    public static int getNameId(DeviceType deviceType) {
        switch (deviceType) {
            case ALL:
                return R.string.device_type_all;
            case ALTITUDE_FROM_PRESSURE:
                return R.string.device_type_altitude_from_preasure;
            case BIKE_CADENCE:
                return R.string.device_type_bike_cadence;
            case BIKE_POWER:
                return R.string.device_type_bike_power;
            case BIKE_SPEED:
                return R.string.device_type_bike_speed;
            case BIKE_SPEED_AND_CADENCE:
                return R.string.device_type_bike_speed_and_cadence;
            case CLOCK:
                return R.string.device_type_clock;
            case DUMMY:
                return R.string.device_type_dummy;
            case ENVIRONMENT:
                return R.string.device_type_environment;
            case SPEED_AND_LOCATION_GPS:
            case SPEED_AND_LOCATION_NETWORK:
            case SPEED_AND_LOCATION_GOOGLE_FUSED:
                return R.string.device_type_gps_speed_and_location;
            case HRM:
                return R.string.device_type_heart_rate;
            case RUN_SPEED:
                return R.string.device_type_run_speed;
            case SENSOR_MANAGER:
                return R.string.device_type_sensor_manager;
        }
        return 0;
    }

    public static int getShortNameId(DeviceType deviceType) {
        switch (deviceType) {
            case ALL:
                return R.string.device_type_short_all;
            case ALTITUDE_FROM_PRESSURE:
                return R.string.device_type_short_altitude_from_preasure;
            case BIKE_CADENCE:
                return R.string.device_type_short_bike_cadence;
            case BIKE_POWER:
                return R.string.device_type_short_bike_power;
            case BIKE_SPEED:
                return R.string.device_type_short_bike_speed;
            case BIKE_SPEED_AND_CADENCE:
                return R.string.device_type_short_bike_speed_and_cadence;
            case CLOCK:
                return R.string.device_type_short_clock;
            case DUMMY:
                return R.string.device_type_short_dummy;
            case ENVIRONMENT:
                return R.string.device_type_short_environment;
            case SPEED_AND_LOCATION_GPS:
            case SPEED_AND_LOCATION_NETWORK:
            case SPEED_AND_LOCATION_GOOGLE_FUSED:
                return R.string.device_type_short_gps_speed_and_location;
            case HRM:
                return R.string.device_type_short_heart_rate;
            case RUN_SPEED:
                return R.string.device_type_short_run_speed;
            case SENSOR_MANAGER:
                return R.string.device_type_sensor_manager;
        }
        return 0;
    }

    public static int getNameId(Protocol protocol) {
        switch (protocol) {
            case ANT_PLUS:
                return R.string.protocol_ant_plus;
            case BLUETOOTH_LE:
                return R.string.protocol_bluetooth;
            case ALL:
                return R.string.protocol_all;
            case SMARTPHONE:
                return R.string.smartphone;
            default:
                return R.string.protocol_unknown;
        }
    }


//	public static int getIconId(Protocol protocol)
//	{
//        switch (protocol) {
//            case ANT_PLUS:
//                return R.drawable.ant_logo;
//            case BLUETOOTH_LE:
//                return R.drawable.logo_protocol_bluetooth;
//            case ALL:
//                return R.drawable.logo;
//            case SMARTPHONE:
//                return R.drawable.ic_phone_android_black_48dp;
//            default:
//                return R.drawable.logo;  // TODO: other icon?
//		}
//	}
}
