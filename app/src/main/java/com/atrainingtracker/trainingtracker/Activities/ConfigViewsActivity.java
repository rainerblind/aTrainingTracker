package com.atrainingtracker.trainingtracker.Activities;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.WindowManager;

import com.atrainingtracker.R;
import com.atrainingtracker.banalservice.ActivityType;
import com.atrainingtracker.trainingtracker.TrainingApplication;
import com.atrainingtracker.trainingtracker.fragments.ConfigViewsFragment;

import java.util.ArrayList;

public abstract class ConfigViewsActivity
        extends AppCompatActivity
        implements ConfigViewsFragment.ViewSetChangedListener {

    public static final String VIEW_ID = "VIEW_ID";
    public static final String ACTIVITY_TYPE = "ACTIVITY_TYPE";
    public static final String NAME = "NAME";
    public static final String NAME_CHANGED_INTENT = "NAME_CHANGED_INTENT";
    private static final String TAG = ConfigViewsActivity.class.getName();
    private static final boolean DEBUG = TrainingApplication.DEBUG & true;
    // public static final String VIEW_CHANGED_INTENT = "VIEW_CHANGED_INTENT";
    long mViewId = -1;
    ActivityType mActivityType = null;

    public abstract ActivityType getActivityType(long viewId);

    public abstract ConfigViewsFragment getNewConfigViewsFragment(ActivityType activityType, long viewId);


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle bundle = this.getIntent().getExtras();

        if (bundle != null) {
            if (bundle.containsKey(VIEW_ID)) {
                mViewId = bundle.getLong(VIEW_ID);
                mActivityType = getActivityType(mViewId);
            }

            if (bundle.containsKey(ACTIVITY_TYPE)) {
                mActivityType = ActivityType.valueOf(bundle.getString(ACTIVITY_TYPE));
            }
        }

        // now, create the UI
        setContentView(R.layout.main_activity_without_navigation);

        Toolbar toolbar = findViewById(R.id.apps_toolbar);
        setSupportActionBar(toolbar);

        // final ActionBar supportAB = getSupportActionBar();
        // supportAB.setHomeAsUpIndicator(R.drawable.abc_ic_ab_back_mtrl_am_alpha);
        // supportAB.setDisplayHomeAsUpEnabled(true);

        showMainFragment();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (TrainingApplication.NoUnlocking()) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        }

        if (mActivityType == null) {
            showSelectActivityTypeDialog();
        }
    }


    protected void showMainFragment() {
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        fragmentTransaction.replace(R.id.content, getNewConfigViewsFragment(mActivityType, mViewId));
        fragmentTransaction.commit();
    }

    @Override
    public void viewSetChanged(ActivityType activityType, long viewId) {
        if (DEBUG) Log.i(TAG, "viewSetChanged(" + activityType + ", " + viewId + ")");

        mActivityType = activityType;
        mViewId = viewId;
        showMainFragment();
    }


    protected void showSelectActivityTypeDialog() {

        ArrayList<String> activityTypes = new ArrayList<>();
        for (ActivityType activityType : ActivityType.values()) {
            activityTypes.add(getString(activityType.getTitleId()));
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.choose_activity_type)
                .setItems(activityTypes.toArray(new String[activityTypes.size()]), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        // The 'which' argument contains the index position of the selected item
                        mActivityType = ActivityType.values()[which];
                        showMainFragment();
                    }
                })
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialogInterface) {
                        Log.i(TAG, "choosing the activityType was canceled");
                        // TODO???
                        finish();
                    }
                });

        builder.create().show();
    }
}
