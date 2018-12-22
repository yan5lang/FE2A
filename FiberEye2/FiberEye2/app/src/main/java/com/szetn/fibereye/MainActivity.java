package com.szetn.fibereye;

import android.Manifest;
import android.support.annotation.NonNull;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.szetn.util.permission.AfterPermissionGranted;
import com.szetn.util.permission.PermissionUtils;

import java.util.List;


public class MainActivity  extends FragmentActivity implements View.OnClickListener, PermissionUtils.PermissionCallbacks  {

    private static final String TAG = MainActivity.class.getCanonicalName();
    private static final int PERMANENTLY_DENIED_REQUEST_CODE = 428;
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        PermissionUtils.onRequestPermissionsResult(requestCode, permissions, grantResults, this);
    }
    @Override
    public void onPermissionGranted(int requestCode, List<String> perms) {
        Log.d(TAG, perms.size() + " permissions granted.");
    }
    @Override
    public void onPermissionDenied(int requestCode, List<String> perms) {
        Log.e(TAG, perms.size() + " permissions denied.");
        if (PermissionUtils.somePermissionsPermanentlyDenied(this, perms)) {
            PermissionUtils.onPermissionsPermanentlyDenied(this,
                    getString(R.string.rationale),
                    getString(R.string.rationale_title),
                    getString(android.R.string.ok),
                    getString(android.R.string.cancel),
                    PERMANENTLY_DENIED_REQUEST_CODE);
        }
    }
    private static final int REQUEST_STORAGE_AND_LOCATION_AND_RECORD = 0x05;
    public void needAuthority(View view) {
        String[] perms = new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,  Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.RECORD_AUDIO};
        if (PermissionUtils.hasPermissions(this, perms)) {
            threePermissionsGranted();
        } else {
            PermissionUtils.requestPermissions(this, "需要相应权限", REQUEST_STORAGE_AND_LOCATION_AND_RECORD, perms);
        }
    }
    @AfterPermissionGranted(REQUEST_STORAGE_AND_LOCATION_AND_RECORD)
    private void threePermissionsGranted() {
//        Toast.makeText(this, "授权成功", Toast.LENGTH_SHORT).show();

    }

    /**
     * 用于展示文件列表的Fragment
     */
    private FilesFragment messageFragment;
    private OthersFragment settingFragment;
    private VideoFragment videoFragment;

    /**
     * 消息界面布局
     */
    private View messageLayout;
    private View settingLayout;
    private View videoLayout;
    /**
     * 在Tab布局上显示消息图标的控件
     */
    private ImageView messageImage;
    private ImageView settingImage;
    private ImageView videoImage;

    /**
     * 在Tab布局上显示消息标题的控件
     */
    private TextView messageText;
    private TextView settingText;
    private TextView videoText;

    /**
     * 用于对Fragment进行管理
     */
    private FragmentManager fragmentManager;

    private FrameLayout frameLayout;
    private ImageView front_page;
    boolean barsFlag = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);// 横屏

        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);   //应用运行时，保持屏幕高亮，不锁屏
        // 初始化布局元素
        initViews();
        fragmentManager = getSupportFragmentManager();
        //前台执行时响应第0个tab.解决了VideoFragment中被多次调用的问题,但是在8寸平板上就只会调用一次
        if(savedInstanceState!=null){
            System.out.println("dddddddddddddddddddddddddd");
            // 第一次启动时选中第0个tab
            setTabSelection(0);
        }
        //触发权限申请
        needAuthority(videoLayout);
    }


    /**
     * 在这里获取到每个需要用到的控件的实例，并给它们设置好必要的点击事件。
     */
    private void initViews() {
        videoLayout = findViewById(R.id.video_layout);
        messageLayout = findViewById(R.id.message_layout);
        settingLayout = findViewById(R.id.setting_layout);

        videoImage = (ImageView) findViewById(R.id.video_image);
        messageImage = (ImageView) findViewById(R.id.message_image);
        settingImage = (ImageView) findViewById(R.id.setting_image);
        front_page = (ImageView)findViewById(R.id.front_page);

        videoText = (TextView) findViewById(R.id.video_text);
        messageText = (TextView) findViewById(R.id.message_text);
        settingText = (TextView) findViewById(R.id.setting_text);

        videoLayout.setOnClickListener(this);
        messageLayout.setOnClickListener(this);
        settingLayout.setOnClickListener(this);

        front_page.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                front_page.setVisibility(View.GONE);
            }
        });

        frameLayout = (FrameLayout) findViewById(R.id.content);
        frameLayout.setOnTouchListener(new OnDoubleClickListener(new OnDoubleClickListener.DoubleClickCallback() {
            @Override
            public void onDoubleClick() {
                if(barsFlag)
                    findViewById(R.id.fragementBars).setVisibility(View.VISIBLE);
                else
                    findViewById(R.id.fragementBars).setVisibility(View.GONE);
                barsFlag = !barsFlag;
            }
        }));
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.video_layout:
                setTabSelection(0);
                Log.d("onClick", "On click 0 frame");
                break;
            case R.id.message_layout:
                // 当点击了消息tab时，选中第1个tab
                setTabSelection(1);
                Log.d("onClick", "On click 1 frame");
                break;
//            case R.id.contacts_layout:
//                // 当点击了同心圆tab时，选中第2个tab
//                setTabSelection(2);
//                break;
            case R.id.setting_layout:
                // 当点击了设置tab时，选中第4个tab
                setTabSelection(2);
                break;
            default:
                break;
        }
    }

    /**
     * 根据传入的index参数来设置选中的tab页。
     *
     * @param index
     *            每个tab页对应的下标。0表示消息，1表示同心圆，2表示动态，3表示设置。
     */
    private void setTabSelection(int index) {
        // 每次选中之前先清楚掉上次的选中状态
        clearSelection();
        // 开启一个Fragment事务
        FragmentTransaction transaction = fragmentManager.beginTransaction();
        // 先隐藏掉所有的Fragment，以防止有多个Fragment显示在界面上的情况
        hideFragments(transaction);
        switch (index) {
            case 0:
                videoImage.setImageResource(R.drawable.video_selected);
                videoText.setTextColor(Color.WHITE);
                Log.d("setTabSelection", "On set Tab select 0 frame");
//                videoFragment = (VideoFragment) getFragmentManager().findFragmentByTag(VideoFragment.TAG);
                if (videoFragment == null) {
                    videoFragment = new VideoFragment();
                    transaction.add(R.id.content, videoFragment);
                } else {
                    transaction.show(videoFragment);
                }
                videoFragment.putCurTag(index);
                break;
            case 1:
                // 当点击了消息tab时，改变控件的图片和文字颜色
                messageImage.setImageResource(R.drawable.message_selected);
                messageText.setTextColor(Color.WHITE);
                Log.d("setTabSelection", "On set Tab select 1 frame");
                if (messageFragment == null) {
                    // 如果MessageFragment为空，则创建一个并添加到界面上
                    messageFragment = new FilesFragment();
                    transaction.add(R.id.content, messageFragment);
                } else {
                    // 如果MessageFragment不为空，则直接将它显示出来
                    transaction.show(messageFragment);
                }
                messageFragment.putCurTag(index);
                break;
//            case 2:
//                // 当点击了同心圆tab时，改变控件的图片和文字颜色
//                contactsImage.setImageResource(R.drawable.contacts_selected);
//                contactsText.setTextColor(Color.WHITE);
//                if (contactsFragment == null) {
//                    // 如果ContactsFragment为空，则创建一个并添加到界面上
//                    contactsFragment = new ContactsFragment();
//                    transaction.add(R.id.content, contactsFragment);
//                } else {
//                    // 如果ContactsFragment不为空，则直接将它显示出来
//                    transaction.show(contactsFragment);
//                }
//                break;
            default:
                // 当点击了设置tab时，改变控件的图片和文字颜色
                settingImage.setImageResource(R.drawable.setting_selected);
                settingText.setTextColor(Color.WHITE);
                if (settingFragment == null) {
                    // 如果SettingFragment为空，则创建一个并添加到界面上
                    settingFragment = new OthersFragment();
                    transaction.add(R.id.content, settingFragment);
                } else {
                    // 如果SettingFragment不为空，则直接将它显示出来
                    transaction.show(settingFragment);
                }
                settingFragment.putCurTag(index);
                break;
        }
        transaction.commit();
    }

    /**
     * 清除掉所有的选中状态。
     */
    private void clearSelection() {
        messageImage.setImageResource(R.drawable.message_unselected);
        messageText.setTextColor(Color.parseColor("#82858b"));
//        contactsImage.setImageResource(R.drawable.contacts_unselected);
//        contactsText.setTextColor(Color.parseColor("#82858b"));
        settingImage.setImageResource(R.drawable.setting_unselected);
        settingText.setTextColor(Color.parseColor("#82858b"));
        videoImage.setImageResource(R.drawable.video_unselected);
        videoText.setTextColor(Color.parseColor("#82858b"));
    }

    /**
     * 将所有的Fragment都置为隐藏状态。
     *
     * @param transaction
     *            用于对Fragment执行操作的事务
     */
    private void hideFragments(FragmentTransaction transaction) {
        if (messageFragment != null) {
            transaction.hide(messageFragment);
            Log.d("hideFragments", "On hide fragment 1 frame");
        }
//        if (contactsFragment != null) {
//            transaction.hide(contactsFragment);
//        }
        if (settingFragment != null) {
            transaction.hide(settingFragment);
        }
        if (videoFragment != null){
            transaction.hide(videoFragment);
            Log.d("hideFragments", "On hide fragment 0 frame");
        }
    }

}


class OnDoubleClickListener implements View.OnTouchListener {
    private final String TAG = this.getClass().getSimpleName();
    private int count = 0;
    private long firClick = 0;
    private long secClick = 0;
    /**
     * 两次点击时间间隔，单位毫秒
     */
    private final int interval = 1000;
    private DoubleClickCallback mCallback;

    public interface DoubleClickCallback {
        void onDoubleClick();
    }

    public OnDoubleClickListener(DoubleClickCallback callback) {
        super();
        this.mCallback = callback;
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if (MotionEvent.ACTION_DOWN == event.getAction()) {
            count++;
            if (1 == count) {
                firClick = System.currentTimeMillis();
            } else if (2 == count) {
                secClick = System.currentTimeMillis();
                if (secClick - firClick < interval) {
                    if (mCallback != null) {
                        mCallback.onDoubleClick();
                    } else {
                        Log.e(TAG, "请在构造方法中传入一个双击回调");
                    }
                    count = 0;
                    firClick = 0;
                } else {
                    firClick = secClick;
                    count = 1;
                }
                secClick = 0;
            }
        }
        return true;
    }
}