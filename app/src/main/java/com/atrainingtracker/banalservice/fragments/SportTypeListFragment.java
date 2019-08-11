package com.atrainingtracker.banalservice.fragments;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ListFragment;
import android.support.v4.widget.SimpleCursorAdapter;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.TextView;
import android.widget.Toast;

import com.atrainingtracker.R;
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager;
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager.SportType;
import com.atrainingtracker.banalservice.dialogs.EditSportTypeDialog;
import com.atrainingtracker.trainingtracker.MyHelper;
import com.atrainingtracker.trainingtracker.TrainingApplication;

/**
 * Created by rainer on 05.06.16.
 */
public class SportTypeListFragment
        extends ListFragment {
    public static final String TAG = SportTypeListFragment.class.getName();
    protected static final String[] FROM = {SportType.UI_NAME, SportType.MIN_AVG_SPEED, SportType.MAX_AVG_SPEED};
    protected static final String[] FROM_WITH_C_ID = {SportType.C_ID, SportType.MIN_AVG_SPEED, SportType.MAX_AVG_SPEED, SportType.UI_NAME};
    protected static final int[] TO = {R.id.st_tvName, R.id.st_tvSpeed, R.id.st_tvSpeed};
    private static final boolean DEBUG = TrainingApplication.DEBUG && false;
    protected Cursor mCursor;
    protected SimpleCursorAdapter mAdapter;
    protected String mSpeedUnit;

    // onAttach

    // onCreate

    // onCreateView

    // onActivityCreated

    // onStart

    // onResume

    // onPause

    // onStop

    // onDestroyView

    // onDestroy

    // onDetach

    protected IntentFilter mSportTypeChangedFilter = new IntentFilter(EditSportTypeDialog.SPORT_TYPE_CHANGED_INTENT);
    BroadcastReceiver mSportTypeChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateView();
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        if (DEBUG) Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);

        mSpeedUnit = getString(MyHelper.getSpeedUnitNameId());

        SQLiteDatabase db = SportTypeDatabaseManager.getInstance().getOpenDatabase();

        mCursor = db.query(SportType.TABLE,
                FROM_WITH_C_ID,
                null,
                null,
                null,
                null,
                null);

        mAdapter = new SimpleCursorAdapter(getContext(), R.layout.sport_type_row, mCursor, FROM, TO);
        mAdapter.setViewBinder(new SimpleCursorAdapter.ViewBinder() {
            @Override
            public boolean setViewValue(View view, Cursor cursor, int columnIndex) {

                TextView tv = (TextView) view;

                if (view.getId() == R.id.st_tvName) {
                    String name = cursor.getString(cursor.getColumnIndex(SportType.UI_NAME));
                    tv.setText(name);
                    long sportTypeId = cursor.getLong(cursor.getColumnIndex(SportType.C_ID));
                    Drawable icon = SportTypeDatabaseManager.getBSportTypeIcon(getContext(), sportTypeId, 0.75);
                    int h = icon.getIntrinsicHeight();
                    int w = icon.getIntrinsicWidth();
                    icon.setBounds(0, 0, w, h);
                    tv.setCompoundDrawables(icon, null, null, null);

                    return true;
                } else if (view.getId() == R.id.st_tvSpeed) {
                    tv.setText(getString(R.string.average_speed_range_format, MyHelper.mps2userUnit(cursor.getDouble(cursor.getColumnIndex(SportType.MIN_AVG_SPEED))),
                            MyHelper.mps2userUnit(cursor.getDouble(cursor.getColumnIndex(SportType.MAX_AVG_SPEED))), mSpeedUnit));
                    return true;
                }

                return false;
            }
        });
        setListAdapter(mAdapter);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.sport_type_list_layout, null);

        FloatingActionButton fab = view.findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showEditSportTypeDialog(-1);
            }
        });

        return view;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        getListView().setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                // Log.i(TAG, "onItemClick: postion=" + position + ", id=" + id);
                showEditSportTypeDialog(id);
            }
        });

        registerForContextMenu(getListView());
    }

    @Override
    public void onResume() {
        super.onResume();

        getContext().registerReceiver(mSportTypeChangedReceiver, mSportTypeChangedFilter);
    }

    @Override
    public void onPause() {
        super.onPause();

        getContext().unregisterReceiver(mSportTypeChangedReceiver);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        SportTypeDatabaseManager.getInstance().closeDatabase();
        mCursor.close();
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
                                    ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        if (DEBUG) Log.i(TAG, "onCreateContextMenu");

        MenuInflater inflater = this.getActivity().getMenuInflater();
        inflater.inflate(R.menu.delete, menu);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        // int position = info.position;
        long id = info.id;

        if (DEBUG) Log.i(TAG, "onContextItemSelected: id=" + id);


        switch (item.getItemId()) {
            case R.id.itemDelete:
                if (!SportTypeDatabaseManager.canDelete(id)) {
                    Toast.makeText(getContext(), R.string.you_can_not_delete_this_sport_type, Toast.LENGTH_LONG).show();
                } else {
                    showReallyDeleteDialog(id);
                }
                return true;
        }

        return false;
    }

    private void updateView() {
        mCursor.requery();
        mAdapter.notifyDataSetChanged();
    }


    private void showReallyDeleteDialog(final long id) {
        if (DEBUG) Log.i(TAG, "showReallyDeleteDialog, id=" + id);
        String sportTypeUiName = SportTypeDatabaseManager.getUIName(id);

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(R.string.delete)
                .setMessage(getContext().getString(R.string.really_delete_workout_name_scheme, sportTypeUiName))
                .setIcon(android.R.drawable.ic_menu_delete)
                .setPositiveButton(R.string.delete, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        SportTypeDatabaseManager.delete(id);
                        updateView();

                        dialog.dismiss();
                    }
                })
                .setNegativeButton(R.string.Cancel, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
        // Create the AlertDialog object and return it
        builder.create().show();
    }

    private void showEditSportTypeDialog(final long id) {
        EditSportTypeDialog editSportTypeDialog = EditSportTypeDialog.newInstance(id);
        editSportTypeDialog.show(getFragmentManager(), EditSportTypeDialog.TAG);
        // FragmentTransaction ft = getFragmentManager().beginTransaction();
        // ft.replace(R.id.list, editSportTypeDialog, EditSportTypeDialog.TAG);
        // ft.addToBackStack(EditSportTypeDialog.TAG);
        // ft.commit();
    }
}
