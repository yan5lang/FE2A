package com.szetn.fibereye;

import android.content.Intent;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import com.szetn.activity.AboutActivity;
import com.szetn.activity.SettingActivity;
import com.szetn.activity.VersionActivity;


/**
 * Created by yan5l on 9/6/2016.
 */
public class OthersFragment extends BaseFragment  implements View.OnClickListener {

    private static final String TAG = BaseFragment.class.getCanonicalName();
    Intent aboutUs;
    Intent settings;
    Intent aboutVersion;



    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View othersLayout = inflater.inflate(R.layout.others_layout,
                container, false);
        Button setwp = (Button)othersLayout.findViewById(R.id.setWP); setwp.setOnClickListener(this);
        Button aboutus = (Button)othersLayout.findViewById(R.id.aboutUs); aboutus.setOnClickListener(this);
        Button aboutversion = (Button)othersLayout.findViewById(R.id.aboutVersion); aboutversion.setOnClickListener(this);

        aboutUs = new Intent(getActivity(),AboutActivity.class);
        settings = new Intent(getActivity(),SettingActivity.class);
        aboutVersion = new Intent(getActivity(),VersionActivity.class);

//        testScreen();

        return othersLayout;
    }

    private void testScreen() {
    // 获取屏幕密度（方法2）
        DisplayMetrics dm = new DisplayMetrics();
        dm = getResources().getDisplayMetrics();

        float density  = dm.density;        // 屏幕密度（像素比例：0.75/1.0/1.5/2.0）
        int densityDPI = dm.densityDpi;     // 屏幕密度（每寸像素：120/160/240/320）
        float xdpi = dm.xdpi;
        float ydpi = dm.ydpi;

        Log.e(TAG + "  DisplayMetrics", "xdpi=" + xdpi + "; ydpi=" + ydpi);
        Log.e(TAG + "  DisplayMetrics", "density=" + density + "; densityDPI=" + densityDPI);

        int screenWidth  = dm.widthPixels;      // 屏幕宽（像素，如：480px）
        int screenHeight = dm.heightPixels;     // 屏幕高（像素，如：800px）

        Log.e(TAG + "  DisplayMetrics(111)", "screenWidth=" + screenWidth + "; screenHeight=" + screenHeight);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.setWP:
                getActivity().startActivity(settings);
                break;
            case R.id.aboutUs:
                getActivity().startActivity(aboutUs);
                break;
            case R.id.aboutVersion:
                getActivity().startActivity(aboutVersion);
                break;
            default:
                break;
        }
    }
}