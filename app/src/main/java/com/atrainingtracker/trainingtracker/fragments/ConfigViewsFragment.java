/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (C) 2011 - 2019 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see https://www.gnu.org/licenses/gpl-3.0
 */

package com.atrainingtracker.trainingtracker.fragments;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import com.atrainingtracker.R;
import com.atrainingtracker.banalservice.ActivityType;
import com.atrainingtracker.trainingtracker.activities.ConfigViewsActivity;
import com.atrainingtracker.trainingtracker.TrainingApplication;

import java.util.HashMap;
import java.util.LinkedList;

/**
 * Created by rainer on 20.01.16.
 */

public abstract class ConfigViewsFragment extends Fragment {

    public static final String TAG = ConfigViewsFragment.class.getSimpleName();
    private static final boolean DEBUG = TrainingApplication.DEBUG && true;
    private static final String CURRENT_ITEM = "CURRENT_ITEM";
    private final IntentFilter mNameChangedFilter = new IntentFilter(ConfigViewsActivity.NAME_CHANGED_INTENT);
    protected ActivityType mActivityType = ActivityType.getDefaultActivityType();
    protected long mViewId = -1;
    protected int mCurrentItem = -1;
    protected LinkedList<Long> mViewIdList = new LinkedList<>();
    protected LinkedList<String> mTitleList = new LinkedList<>();
    protected HashMap<Long, Integer> mViewId2Position = new HashMap<>();
    protected HashMap<Integer, Long> mPosition2ViewId = new HashMap<>();
    /**
     * The {@link PagerAdapter} that will provide
     * fragments for each of the sections. We use a
     * {@link FragmentPagerAdapter} derivative, which
     * will keep every loaded fragment in memory. If this becomes too memory
     * intensive, it may be best to switch to a
     * {@link FragmentStatePagerAdapter}.
     */
    SectionsPagerAdapter mSectionsPagerAdapter;


    /**
     * The {@link ViewPager} that will host the section contents.
     */
    ViewPager mViewPager;
    Button mDeleteButton;
    BroadcastReceiver mNameChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            long viewId = intent.getLongExtra(ConfigViewsActivity.VIEW_ID, -1);
            String name = intent.getStringExtra(ConfigViewsActivity.NAME);

            if (DEBUG)
                Log.i(TAG, "nameChangedReceiver.onReceive() viewId=" + viewId + ", name=" + name);

            if (viewId >= 0 && name != null && mViewId2Position.containsKey(viewId)) {  // with the test containsKey, there is sometimes? no update of the titles.  but without it, we get nullPointer Exceptions...
                mTitleList.set(mViewId2Position.get(viewId), name);  // throws null pointer exceptions
                mSectionsPagerAdapter.notifyDataSetChanged();

            } else {
                Log.i(TAG, "WTF: we can not change it");
            }
        }
    };
    private ViewSetChangedListener mViewSetChangedListener = null;

    protected abstract void ensureEntryForActivityTypeExists();

    protected abstract LinkedList<Long> getViewIdList();

    protected abstract LinkedList<String> getTitleList();

    protected abstract void deleteView(long viewId);

    protected abstract long addView(long viewId, boolean addAfterCurrentLayout);  // subclasses are encouraged to show a dialog to support several variations of new like empty, default, copy, ...

    protected abstract Fragment getNewChildFragment(long viewId);

//	public static ConfigViewsFragment newInstance(ActivityType activityType, long viewId) {
//
//        ConfigViewsFragment fragment = new ConfigViewsFragment();
//
//        Bundle args  = new Bundle();
//        if (activityType == null) {
//            args.putString(ConfigViewsActivity.ACTIVITY_TYPE, ActivityType.getDefaultActivityType().name());
//        }
//        else {
//            args.putString(ConfigViewsActivity.ACTIVITY_TYPE, activityType.name());
//        }
//        args.putLong(ConfigViewsActivity.VIEW_ID, viewId);
//        fragment.setArguments(args);
//
//		return fragment;
//	}

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (DEBUG) Log.i(TAG, "onAttach()");

        try {
            mViewSetChangedListener = (ViewSetChangedListener) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context.toString() + " must implement ViewSetChangedListener");
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mActivityType = ActivityType.valueOf(getArguments().getString(ConfigViewsActivity.ACTIVITY_TYPE));
        mViewId = getArguments().getLong(ConfigViewsActivity.VIEW_ID);
        if (savedInstanceState != null && savedInstanceState.containsKey(CURRENT_ITEM)) {
            mCurrentItem = savedInstanceState.getInt(CURRENT_ITEM);
        }

        if (DEBUG)
            Log.i(TAG, "onCreate() with arguments: activityType=" + mActivityType + ", viewId=" + mViewId);
    }

//    @Override
//    public void onAttach(Context context)
//    {
//        super.onAttach(context);
//        if (DEBUG) Log.i(TAG, "onAttach");
//    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        if (DEBUG) Log.i(TAG, "onCreateView()");

        View v = inflater.inflate(R.layout.tabbed_config_views, container, false);

        mSectionsPagerAdapter = new SectionsPagerAdapter(getChildFragmentManager());

        mViewPager = v.findViewById(R.id.pager);
        mViewPager.setAdapter(mSectionsPagerAdapter);

        Button button = v.findViewById(R.id.buttonAddViewBefore);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i(TAG, "add before button pressed");
                showAddViewDialog(false);
            }
        });


        button = v.findViewById(R.id.buttonAddViewAfter);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i(TAG, "add after button pressed");
                showAddViewDialog(true);
            }
        });

        // configure delete button
        mDeleteButton = v.findViewById(R.id.buttonDeleteView);
        mDeleteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i(TAG, "delete button pressed");
                showReallyDeleteDialog();
            }
        });
        configureDeleteButton();

        return v;
    }

    private void configureDeleteButton() {
        if (DEBUG) Log.i(TAG, "configureDeleteButton");

        if (mSectionsPagerAdapter.getCount() <= 1) {
            mDeleteButton.setEnabled(false);
            mDeleteButton.setClickable(false);
        } else {
            mDeleteButton.setEnabled(true);
            mDeleteButton.setClickable(true);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (DEBUG) Log.i(TAG, "onResume()");

        // create list of views
        createViewIdList();
        getActivity().registerReceiver(mNameChangedReceiver, mNameChangedFilter);

        if (mCurrentItem >= 0) {
            if (DEBUG) Log.i(TAG, "setCurrentItem based on mCurrentItem=" + mCurrentItem);
            mViewPager.setCurrentItem(mCurrentItem);
        } else if (mViewId >= 0 && mViewId2Position != null && mViewId2Position.get(mViewId) != null) {
            if (DEBUG)
                Log.i(TAG, "setCurrentItem based on mViewId2Position and mViewId=" + mViewId);
            mViewPager.setCurrentItem(mViewId2Position.get(mViewId));
        } else {
            if (DEBUG) Log.i(TAG, "unable to show currentItem");
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (DEBUG) Log.i(TAG, "onPause()");

        mCurrentItem = mViewPager.getCurrentItem();

        try {
            getActivity().unregisterReceiver(mNameChangedReceiver);
        } catch (Exception e) {
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        if (mViewPager != null) {
            outState.putInt(CURRENT_ITEM, mViewPager.getCurrentItem());
        }

        super.onSaveInstanceState(outState);
    }

    protected void createViewIdList() {
        if (DEBUG) Log.i(TAG, "createViewIdList()");

        ensureEntryForActivityTypeExists();

        mTitleList = getTitleList();
        mViewIdList = getViewIdList();

        mViewId2Position.clear();
        mPosition2ViewId.clear();

        int position = 0;
        for (Long viewId : mViewIdList) {
            if (DEBUG) Log.i(TAG, "position=" + position + ", viewId=" + viewId);
            mViewId2Position.put(viewId, position);
            mPosition2ViewId.put(position, viewId);
            position++;
        }

        mSectionsPagerAdapter.notifyDataSetChanged();
        // mViewPager.
        configureDeleteButton();
    }

    protected void showReallyDeleteDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle(R.string.delete_view)
                .setMessage(getString(R.string.really_delete_view_format, mTitleList.get(mViewPager.getCurrentItem())))
                .setIcon(android.R.drawable.ic_menu_delete)
                .setPositiveButton(R.string.delete_view, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        int position = mViewPager.getCurrentItem();
                        long viewId = mPosition2ViewId.get(position);
                        if (DEBUG) Log.i(TAG, "delete position=" + position);

                        deleteView(viewId);

                        long newViewId;
                        if (mPosition2ViewId.containsKey(position + 1)) {
                            newViewId = mPosition2ViewId.get(position + 1);
                        } else {
                            newViewId = mPosition2ViewId.get(position - 1);
                        }

                        mViewSetChangedListener.viewSetChanged(mActivityType, newViewId);

                        // recreateViewIdList();

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

    protected void showAddViewDialog(boolean addAfterCurrentLayout) {
        int position = mViewPager.getCurrentItem();
        if (DEBUG) Log.i(TAG, "showAddViewDialog position=" + position);

        long newViewId = addView(mPosition2ViewId.get(position), addAfterCurrentLayout);
        mViewSetChangedListener.viewSetChanged(mActivityType, newViewId);

        // if (addAfterCurrentLayout) { position++; }
        // recreateViewIdList();

        // mViewPager.setCurrentItem(position);
    }

    public interface ViewSetChangedListener {
        void viewSetChanged(ActivityType activityType, long viewId);
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * A {@link FragmentPagerAdapter} that returns a fragment corresponding to
     * one of the sections/tabs/pages.
     */
    public class SectionsPagerAdapter extends FragmentPagerAdapter {

        public SectionsPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        // see http://stackoverflow.com/questions/7263291/viewpager-pageradapter-not-updating-the-view
        // @Override
        // public int getItemPosition(Object object) {
        // return POSITION_NONE;
        //}

        // see http://stackoverflow.com/questions/10849552/update-viewpager-dynamically
        @Override
        public int getItemPosition(Object object) {
            if (DEBUG) Log.i(TAG, "SectionsPagerAdapter.getItemPosition");

            ConfigViewFragment configViewFragment = (ConfigViewFragment) object;

            long viewId = configViewFragment.getViewId();
            if (mViewId2Position.containsKey(viewId)) {
                if (DEBUG)
                    Log.i(TAG, "position known: viewId=" + viewId + ", position = " + mViewId2Position.get(viewId));
                return mViewId2Position.get(viewId);
            } else {
                if (DEBUG) Log.i(TAG, "position of view not known: viewId=" + viewId);
                return POSITION_NONE;
            }
        }

        @Override
        public Fragment getItem(int position) {
            // getItem is called to instantiate the fragment for the given page.
            if (DEBUG) Log.i(TAG, "SectionsPagerAdapter.getItem(" + position + ")");

            return getNewChildFragment(mViewIdList.get(position));
        }

        @Override
        public int getCount() {
            // if (DEBUG) Log.i(TAG, "SectionsPagerAdapter.getCount(): returning " + mViewIdList.size());

            return mViewIdList.size();
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return mTitleList.get(position);
        }
    }
}
