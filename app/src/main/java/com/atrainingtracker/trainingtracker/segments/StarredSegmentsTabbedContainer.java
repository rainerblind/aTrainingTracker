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

package com.atrainingtracker.trainingtracker.segments;

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

import com.atrainingtracker.R;
import com.atrainingtracker.banalservice.BSportType;
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager;
import com.atrainingtracker.trainingtracker.TrainingApplication;


/**
 * Created by rainer on 20.01.16.
 */
public class StarredSegmentsTabbedContainer extends Fragment {

    public static final String TAG = StarredSegmentsTabbedContainer.class.getSimpleName();
    private static final boolean DEBUG = TrainingApplication.getDebug(false);

    private static final String SELECTED_ITEM = "SELECTED_ITEM";
    /**
     * The {@link ViewPager} that will host the section contents.
     */
    protected ViewPager mViewPager;
    /**
     * The {@link PagerAdapter} that will provide
     * fragments for each of the sections. We use a
     * {@link FragmentPagerAdapter} derivative, which
     * will keep every loaded fragment in memory. If this becomes too memory
     * intensive, it may be best to switch to a
     * {@link FragmentStatePagerAdapter}.
     */
    SectionsPagerAdapter mSectionsPagerAdapter;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        if (DEBUG) Log.i(TAG, "onCreateView(), savedInstanceState=" + savedInstanceState);

        View view = inflater.inflate(R.layout.tabbed_remote_devices_container_fragment, container, false);

        // get the views
        mViewPager = view.findViewById(R.id.pager);

        mSectionsPagerAdapter = new SectionsPagerAdapter(getChildFragmentManager());
        mViewPager.setAdapter(mSectionsPagerAdapter);

        return view;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        if (DEBUG) Log.i(TAG, "onActivityCreated, savedInstanceState=" + savedInstanceState);

        if (savedInstanceState != null) {
            mViewPager.setCurrentItem(savedInstanceState.getInt(SELECTED_ITEM));
            if (DEBUG)
                Log.i(TAG, "got selected item from last time: " + savedInstanceState.getInt(SELECTED_ITEM));
        }
    }


    @Override
    public void onResume() {
        super.onResume();

        if (DEBUG) Log.i(TAG, "onResume");

    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putInt(SELECTED_ITEM, mViewPager.getCurrentItem());
        if (DEBUG)
            Log.i(TAG, "onSaveInstanceState: saved selected item:" + mViewPager.getCurrentItem());

        super.onSaveInstanceState(outState);
    }


    /**
     * A {@link FragmentPagerAdapter} that returns a fragment corresponding to
     * one of the sections/tabs/pages.
     */
    public class SectionsPagerAdapter extends FragmentPagerAdapter {

        public SectionsPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            // getItem is called to instantiate the fragment for the given page.
            if (DEBUG) Log.i(TAG, "SectionsPagerAdapter.getItem(" + position + ")");

            return switch (position) {
                case 0 ->
                        StarredSegmentsListFragment.newInstance(SportTypeDatabaseManager.getSportTypeId(BSportType.BIKE));
                case 1 ->
                        StarredSegmentsListFragment.newInstance(SportTypeDatabaseManager.getSportTypeId(BSportType.RUN));
                default -> new Fragment();
            };

        }

        @Override
        public int getCount() {
            return 2;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return switch (position) {
                case 0 -> getString(R.string.starred_bike_segments);
                case 1 -> getString(R.string.starred_run_segments);
                default -> null;
            };

        }
    }
}
