package com.atrainingtracker.banalservice.dialogs;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.util.Log;
import android.widget.ArrayAdapter;

import com.atrainingtracker.R;
import com.atrainingtracker.banalservice.BANALService;
import com.atrainingtracker.banalservice.BSportType;
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager;

import java.util.List;

/**
 * Created by rainer on 05.01.17.
 */

public class SelectSportTypeDialog extends DialogFragment {
    public static final String TAG = SelectSportTypeDialog.class.getName();
    private static final boolean DEBUG = BANALService.DEBUG && false;


    private static final String B_SPORT_TYPE = "B_SPORT_TYPE";

    private BSportType mBSportType;
    private BANALService.GetBanalServiceInterface mGetBanalServiceInterface;

    public static SelectSportTypeDialog newInstance(BSportType bSportType) {
        if (DEBUG) Log.i(TAG, "newInstance");

        SelectSportTypeDialog fragment = new SelectSportTypeDialog();

        Bundle args = new Bundle();
        args.putString(B_SPORT_TYPE, bSportType.name());
        fragment.setArguments(args);

        return fragment;
    }

    @Override
    public void onAttach(final Context context) {
        super.onAttach(context);
        if (DEBUG) Log.i(TAG, "onAttach");

        try {
            mGetBanalServiceInterface = (BANALService.GetBanalServiceInterface) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context.toString() + " must implement GetBanalServiceInterface");
        }

    }

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (DEBUG) Log.d(TAG, "onCreate");

        mBSportType = BSportType.valueOf(getArguments().getString(B_SPORT_TYPE));
    }


    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        List<String> sportTypeNames = SportTypeDatabaseManager.getSportTypesUiNameList(mBSportType);
        final List<Long> sportTypeIds = SportTypeDatabaseManager.getSportTypesIdList(mBSportType);

        String bSportTypeName;
        switch (mBSportType) {
            case RUN:
                bSportTypeName = getString(R.string.sport_type_run);
                break;

            case BIKE:
                bSportTypeName = getString(R.string.sport_type_bike);
                break;

            default:
                bSportTypeName = getString(R.string.sport_type_other);
                break;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle(getString(R.string.select_sport_type_format, bSportTypeName));

        ArrayAdapter<String> arrayAdapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_list_item_1);
        arrayAdapter.addAll(sportTypeNames);

        builder.setAdapter(arrayAdapter, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                mGetBanalServiceInterface.getBanalServiceComm().setUserSelectedSportTypeId(sportTypeIds.get(which));
                dialog.cancel();
            }

        });

        return builder.create();
    }
}
