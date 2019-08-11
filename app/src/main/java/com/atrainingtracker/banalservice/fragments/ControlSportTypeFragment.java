package com.atrainingtracker.banalservice.fragments;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import com.atrainingtracker.R;
import com.atrainingtracker.banalservice.BANALService;
import com.atrainingtracker.banalservice.BANALService.BANALServiceComm;
import com.atrainingtracker.banalservice.BSportType;
import com.atrainingtracker.banalservice.dialogs.SelectSportTypeDialog;

public class ControlSportTypeFragment extends Fragment {
    private static final String TAG = ControlSportTypeFragment.class.getName();
    private static final boolean DEBUG = BANALService.DEBUG && false;

    private ImageButton mIbRun, mIbBike, mIbOther;
    private TextView mTvRun, mTvBike, mTvOther;

    private boolean mViewCreated = false;

    private BANALService.GetBanalServiceInterface mGetBanalServiceInterface;
    BroadcastReceiver mUpdateViewReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateView();
        }
    };
    private IntentFilter mUpdateViewFilter = new IntentFilter(BANALService.NEW_DEVICE_FOUND_INTENT);

    // onCreate()

    @Override
    public void onAttach(final Context context) {
        super.onAttach(context);
        if (DEBUG) Log.i(TAG, "onAttach");

        try {
            mGetBanalServiceInterface = (BANALService.GetBanalServiceInterface) context;
            mGetBanalServiceInterface.registerConnectionStatusListener(new BANALService.GetBanalServiceInterface.ConnectionStatusListener() {
                @Override
                public void connectedToBanalService() {
                    updateView();
                }

                @Override
                public void disconnectedFromBanalService() {
                    updateView();
                }
            });
        } catch (ClassCastException e) {
            throw new ClassCastException(context.toString() + " must implement GetBanalServiceInterface");
        }

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        if (DEBUG) Log.d(TAG, "onCreateView");

        View view = inflater.inflate(R.layout.control_sport_type_layout, container, false);

        mTvRun = view.findViewById(R.id.cst_tvRun);
        mTvBike = view.findViewById(R.id.cst_tvBike);
        mTvOther = view.findViewById(R.id.cst_tvOther);

        mIbRun = view.findViewById(R.id.cst_ibRun);
        mIbBike = view.findViewById(R.id.cst_ibBike);
        mIbOther = view.findViewById(R.id.cst_ibOther);

        mIbRun.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (DEBUG) Log.i(TAG, "RUN selected by user");
                setUserSelectedSportType(BSportType.RUN);
            }
        });
        mIbRun.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                showSelectSportTypeDialog(BSportType.RUN);
                return true;
            }
        });

        mIbBike.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (DEBUG) Log.i(TAG, "BIKE selected by user");
                setUserSelectedSportType(BSportType.BIKE);
            }
        });
        mIbBike.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                showSelectSportTypeDialog(BSportType.BIKE);
                return true;
            }
        });

        mIbOther.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (DEBUG) Log.i(TAG, "OTHER (UNKNOWN) selected by user");
                setUserSelectedSportType(BSportType.UNKNOWN);
            }
        });
        mIbOther.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                showSelectSportTypeDialog(BSportType.UNKNOWN);
                return true;
            }
        });

        mViewCreated = true;

        return view;
    }

    private void setUserSelectedSportType(BSportType bSportType) {
        BANALServiceComm banalServiceComm = mGetBanalServiceInterface.getBanalServiceComm();
        if (banalServiceComm != null) {
            banalServiceComm.setUserSelectedSportType(bSportType);
            updateView();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (DEBUG) Log.i(TAG, "onResume");

        updateView();

        getActivity().registerReceiver(mUpdateViewReceiver, mUpdateViewFilter);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (DEBUG) Log.i(TAG, "onPause");

        getActivity().unregisterReceiver(mUpdateViewReceiver);
    }

    private void showSelectSportTypeDialog(BSportType bsportType) {
        SelectSportTypeDialog selectSportTypeDialog = SelectSportTypeDialog.newInstance(bsportType);
        selectSportTypeDialog.show(getFragmentManager(), SelectSportTypeDialog.TAG);
    }


    public void updateView() {
        if (DEBUG) Log.d(TAG, "updateView");

        if (!mViewCreated | !isAdded()) {
            return;
        }

        BANALServiceComm banalServiceComm = mGetBanalServiceInterface.getBanalServiceComm();
        if (banalServiceComm != null) {
            switch (banalServiceComm.getBSportType()) {
                case RUN:
                    mTvRun.setTextColor(getResources().getColor(R.color.my_black));
                    mTvBike.setTextColor(getResources().getColor(R.color.bright_grey));
                    mTvOther.setTextColor(getResources().getColor(R.color.bright_grey));

                    mIbRun.setImageDrawable(getResources().getDrawable(R.drawable.bsport_run));
                    mIbBike.setImageDrawable(getResources().getDrawable(R.drawable.bsport_bike_gray));
                    mIbOther.setImageDrawable(getResources().getDrawable(R.drawable.bsport_other_gray));

                    break;

                case BIKE:
                    mTvRun.setTextColor(getResources().getColor(R.color.bright_grey));
                    mTvBike.setTextColor(getResources().getColor(R.color.my_black));
                    mTvOther.setTextColor(getResources().getColor(R.color.bright_grey));

                    mIbRun.setImageDrawable(getResources().getDrawable(R.drawable.bsport_run_gray));
                    mIbBike.setImageDrawable(getResources().getDrawable(R.drawable.bsport_bike));
                    mIbOther.setImageDrawable(getResources().getDrawable(R.drawable.bsport_other_gray));

                    break;

                case UNKNOWN:
                    mTvRun.setTextColor(getResources().getColor(R.color.bright_grey));
                    mTvBike.setTextColor(getResources().getColor(R.color.bright_grey));
                    mTvOther.setTextColor(getResources().getColor(R.color.my_black));

                    mIbRun.setImageDrawable(getResources().getDrawable(R.drawable.bsport_run_gray));
                    mIbBike.setImageDrawable(getResources().getDrawable(R.drawable.bsport_bike_gray));
                    mIbOther.setImageDrawable(getResources().getDrawable(R.drawable.bsport_other));

                    break;

                default:
                    mTvRun.setTextColor(getResources().getColor(R.color.bright_grey));
                    mTvBike.setTextColor(getResources().getColor(R.color.bright_grey));
                    mTvOther.setTextColor(getResources().getColor(R.color.bright_grey));

                    mIbRun.setImageDrawable(getResources().getDrawable(R.drawable.bsport_run_gray));
                    mIbBike.setImageDrawable(getResources().getDrawable(R.drawable.bsport_bike_gray));
                    mIbOther.setImageDrawable(getResources().getDrawable(R.drawable.bsport_other_gray));

                    break;
            }
        } else {
            mTvRun.setTextColor(getResources().getColor(R.color.bright_grey));
            mTvBike.setTextColor(getResources().getColor(R.color.bright_grey));
            mTvOther.setTextColor(getResources().getColor(R.color.bright_grey));

            mIbRun.setImageDrawable(getResources().getDrawable(R.drawable.bsport_run_gray));
            mIbBike.setImageDrawable(getResources().getDrawable(R.drawable.bsport_bike_gray));
            mIbOther.setImageDrawable(getResources().getDrawable(R.drawable.bsport_other_gray));
        }
    }

}
