package com.szetn.fibereye;

import android.Manifest;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.drawable.BitmapDrawable;
import android.location.LocationManager;
import android.net.Uri;
import android.net.wifi.ScanResult;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.support.annotation.Nullable;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TableLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.szetn.rencoder.MediaMuxerRunnable;
import com.szetn.service.AnaIntentService;
import com.szetn.util.FileUtils;
import com.szetn.util.WifiAdmin;
import com.szetn.util.WifiConnector;
import com.szetn.util.permission.AfterPermissionGranted;
import com.szetn.util.permission.PermissionUtils;
import com.wang.avi.AVLoadingIndicatorView;

import java.util.List;

import ChirdSdk.Apis.st_GpioInfo;
import ChirdSdk.CHD_Client;
import ChirdSdk.CHD_LocalScan;
import ChirdSdk.ClientCallBack;
import ChirdSdk.FE_Client;
import ChirdSdk.StreamView;

import static android.content.Context.LOCATION_SERVICE;
import static android.content.Context.MODE_PRIVATE;


/**
 * Created by yan5l on 9/6/2016.
 */
public class VideoFragment extends BaseFragment implements PermissionUtils.PermissionCallbacks{

    private View videoLayout;

    private FrameLayout mVideoFrameLayout;
    private StreamView mStreamView;
    private AVLoadingIndicatorView avi;

    private CHD_Client mClient;
    private WifiAdmin wifiAdmin;
    private Handler mWifiHandler;
    private Runnable mWifiRunnable;
    private WifiConnector wac;
    private SharedPreferences preferences;

    private CHD_LocalScan mScan;
    private Handler mTimeHandler;
    private Runnable mTimeRunnable;

    private ToggleButton on_off, start_stop, toggle_menus;
    private ImageButton snapButton;
    private ToggleButton switchGpio;
    private TextView recordTextView;
    private ImageView thumbnailImageView;
    private TableLayout toggleMenusView;
    private SeekBar brightness, contraster;
    private Switch switch_calibrate, switch_bright_manual, switch_contrast_manual;
    private Switch switch_analyse;
    private AlertDialog.Builder moreProbes;

    private boolean isRecording=false;
    private boolean isShowChoose=false;
//    private Intent service  = new Intent();
//    private LocationManager gpsService;


    //cirlce
    Matrix matrix = new Matrix();
    Matrix savedMatrix = new Matrix();
    ImageView cirCleImgView;

    PointF startPoint = new PointF();
    PointF midPoint = new PointF();
    float oldDist = 1f;

    private long lastClickTime = 0;
    private static final int DOUBLE_CLICK_TIME_SPACE = 500;
    private static final int DOUBLE_POINT_DISTANCE = 10;
    private static int menusViewDistance;
    boolean barsFlag;
    /** 最大缩放比例*/
    static final float MAX_SCALE = 10f;
    /** 初始状态*/
    static final int NONE = 0;
    /** 拖动*/
    static final int DRAG = 1;
    /** 缩放*/
    static final int ZOOM = 2;
    /** 当前模式*/
    int mode = NONE;
    //endcircle

    private static final int DEFAULT_BIRGHTNESS = 128;
    private static final int DEFAULT_CONTRAST = 30;
    public static final String PRIFIX_WIFI = "FE";
    private static final int REQUEST_PHONE_STATE = 0x01;

    private int HASFAILCOUNTS=0, HASCONNECTCOUNTS=0;
    private boolean isFE_LASTTIME = false;
    private boolean isFE = false;
    private boolean hasFE = false;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        if(videoLayout==null){
            videoLayout=inflater.inflate(R.layout.video_layout, container, false );
        }

        //content 缓存
        preferences = getActivity().getSharedPreferences(PRIFIX_WIFI,MODE_PRIVATE);

        //缓存的rootView需要判断是否已经被加过parent， 如果有parent需要从parent删除，要不然会发生这个rootview已经有parent的错误。
//        ViewGroup parent = (ViewGroup) videoLayout.getParent();
//        if (parent != null) {
//            parent.removeView(videoLayout);
//        }

        mStreamView = new StreamView(videoLayout.getContext(), new StreamView.CallBack() {
            public void callbackSurface(Surface surface) {

            }
        });
//        mStreamView = new StreamView(videoLayout.getContext());
        mVideoFrameLayout = (FrameLayout) videoLayout.findViewById(R.id.video_frameLayout);
        mVideoFrameLayout.addView(mStreamView);
        avi = (AVLoadingIndicatorView)videoLayout.findViewById(R.id.avi);
        avi.show();
//        mStreamView.setDisplayMode(mStreamView.SIZE_BEST_FIT);

        on_off = (ToggleButton)videoLayout.findViewById(R.id.on_off);
        start_stop = (ToggleButton)videoLayout.findViewById(R.id.start_stop);
        toggle_menus = (ToggleButton)videoLayout.findViewById(R.id.toggleMenus);
        switch_calibrate = (Switch)videoLayout.findViewById(R.id.switch_calibrate);
        switch_bright_manual = (Switch)videoLayout.findViewById(R.id.switch_bright_manual);
        switch_contrast_manual = (Switch)videoLayout.findViewById(R.id.switch_contrast_manual);
        switch_analyse = (Switch)videoLayout.findViewById(R.id.switch_autoAnaly);
        toggleMenusView = (TableLayout)videoLayout.findViewById(R.id.toggleMenusView);
        brightness = (SeekBar)videoLayout.findViewById(R.id.action_brightness);
        contraster = (SeekBar)videoLayout.findViewById(R.id.action_contrast);
        snapButton = (ImageButton) videoLayout.findViewById(R.id.snapButton);
        switchGpio = (ToggleButton) videoLayout.findViewById(R.id.switch_gpio);
        recordTextView = (TextView) videoLayout.findViewById(R.id.recordTextView);

        thumbnailImageView = (ImageView) videoLayout.findViewById(R.id.thumbnailImageView);
        super.onCreateView(inflater,container,savedInstanceState);
        moreProbes = new AlertDialog.Builder(videoLayout.getContext())
                .setTitle(R.string.info_choose_probe).setIcon( android.R.drawable.ic_dialog_info);

        locanScanListener();
        wifiScanListener();
        clientCallBackListener();
        viewOnClickListener();
        try2Open();

//        try2getAuthority();

//        videoLayout.setBackgroundColor(0xffffff);
        menusViewDistance = getResources().getDimensionPixelSize(R.dimen.menusViewDistance);

        DisplayMetrics dm = new DisplayMetrics();
        getActivity().getWindowManager().getDefaultDisplay().getMetrics(dm);

        cirCleImgView = (ImageView) videoLayout.findViewById(R.id.circle);// 获取控件
//        cirCleImgView.setScaleType(ImageView.ScaleType.MATRIX);
        initImageView(dm.widthPixels, dm.heightPixels+180);
        setCircleListener();
        needAuthority(videoLayout);

        return videoLayout;
    }

    private static final int REQUEST_STORAGE_AND_RECORD = 0x02;
    public void needAuthority(View view) {
        String[] perms = new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE,Manifest.permission.READ_EXTERNAL_STORAGE};
        if (PermissionUtils.hasPermissions(view.getContext(), perms)) {
//            threePermissionsGranted();
        } else {
            PermissionUtils.requestPermissions(this, "需要相应权限", REQUEST_STORAGE_AND_RECORD, perms);
        }
    }

//    @AfterPermissionGranted(REQUEST_PHONE_STATE)
//    private void onPermissionGranted() {
//        Toast.makeText(getActivity(), "授权成功", Toast.LENGTH_SHORT).show();
//    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    private void setCircleListener() {
        cirCleImgView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                ImageView view = (ImageView) v;
//                System.out.println("matrix=" + savedMatrix.toString());
                switch (event.getAction() & MotionEvent.ACTION_MASK) {
                    case MotionEvent.ACTION_DOWN:
                        if (event.getEventTime() - lastClickTime < DOUBLE_CLICK_TIME_SPACE) {
                            //@Todo 暂时不加全屏效果
//                            changeSize();
                            lastClickTime = event.getEventTime();
                        }else {
                            lastClickTime = event.getEventTime();
                            savedMatrix.set(matrix);
                            startPoint.set(event.getX(), event.getY());
                            mode = DRAG;
                        }
                        break;
                    case MotionEvent.ACTION_POINTER_DOWN:
                        oldDist = spacing(event);
                        if (oldDist > DOUBLE_POINT_DISTANCE) {
                            savedMatrix.set(matrix);
                            midPoint(midPoint, event);
                            mode = ZOOM;
                        }
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_POINTER_UP:
                        mode = NONE;
                        break;
                    case MotionEvent.ACTION_MOVE:
                        if (mode == DRAG) {
                            matrix.set(savedMatrix);
                            matrix.postTranslate(event.getX() - startPoint.x, event.getY() - startPoint.y);
                        } else if (mode == ZOOM) {
                            float newDist = spacing(event);
                            if (newDist > MAX_SCALE) {
                                matrix.set(savedMatrix);
                                float scale = newDist / oldDist;
                                matrix.postScale(scale, scale, midPoint.x, midPoint.y);
                            }
                        }
                        break;
                }
                view.setImageMatrix(matrix);
                return true;
            }

            private void changeSize() {
                if(barsFlag)
                    getActivity().findViewById(R.id.fragementBars).setVisibility(View.VISIBLE);
                else
                    getActivity().findViewById(R.id.fragementBars).setVisibility(View.GONE);
                barsFlag = !barsFlag;
            }

            private float spacing(MotionEvent event) {
                float x = event.getX(0) - event.getX(1);
                float y = event.getY(0) - event.getY(1);
                return (float)Math.sqrt(x * x + y * y);
            }

            private void midPoint(PointF point, MotionEvent event) {
                float x = event.getX(0) + event.getX(1);
                float y = event.getY(0) + event.getY(1);
                point.set(x / 2, y / 2);
            }
        });// 设置触屏监听
    }

    private void initImageView(int screenWidth, int screenHeight) {
        Bitmap touchImg = ((BitmapDrawable) cirCleImgView.getDrawable()).getBitmap();
        float touchImgWidth = touchImg.getWidth();
        float touchImgHeight = touchImg.getHeight();
        float scaleX = (float) screenWidth / touchImgWidth;
        float scaleY = (float) screenHeight / touchImgHeight;
        float defaultScale = scaleX < scaleY ? scaleX : scaleY;

        float subX = (screenWidth - touchImgWidth * defaultScale) / 2;
        float subY = (screenHeight - touchImgHeight * defaultScale) / 2;
        cirCleImgView.setScaleType(ImageView.ScaleType.MATRIX);
        savedMatrix.reset();
        savedMatrix.postScale(defaultScale, defaultScale);
        savedMatrix.postTranslate(subX, subY);
        matrix.set(savedMatrix);
//        invalidate();
    }

    //wifi连接
    private void wifiScanListener() {
        wifiAdmin = new WifiAdmin(videoLayout.getContext());
        wac = new WifiConnector(wifiAdmin.getmWifiManager());
        wifiAdmin.startScan();
        isFE = wifiAdmin.isFE(PRIFIX_WIFI, isFE_LASTTIME);
        isFE_LASTTIME = isFE;
        hasFE = wifiAdmin.getFilterWifiList(PRIFIX_WIFI, isFE_LASTTIME).size()>0;
//        gpsService = (LocationManager) videoLayout.getContext().getSystemService(LOCATION_SERVICE);

        mWifiHandler = new Handler(){
            @Override
            public void handleMessage(Message msg) {
                if(!isCurTag(0))
                    return;
                // 操作界面
                if (msg.obj.toString().contentEquals("连接成功!")) {
                    try2Open();
                    Toast.makeText(videoLayout.getContext(), R.string.info_device_connect, Toast.LENGTH_SHORT).show();
                } else if(msg.obj.toString().contentEquals("多个!") && !isShowChoose) {
                    showChooseWifiDialog();
                    isShowChoose = true;
                }else if(msg.obj.toString().contentEquals("没有!") && !isFE) {
                    Toast.makeText(videoLayout.getContext(), R.string.info_no_probe, Toast.LENGTH_SHORT).show();
                }else if(msg.obj.toString().contentEquals("没有检测到3次") && !isFE){
                    Toast.makeText(videoLayout.getContext(), R.string.info_device_lose, Toast.LENGTH_SHORT).show();
//                    try2Open();
                }
                else if(msg.obj.toString().contentEquals("wifiIsConnecting")) {
                    Toast.makeText(videoLayout.getContext(), "LOADING...", Toast.LENGTH_SHORT).show();
                }
                super.handleMessage(msg);
            }
        };
        mWifiRunnable = new Runnable(){
            public void run() {
                isFE = wifiAdmin.isFE(PRIFIX_WIFI, isFE_LASTTIME);
                isFE_LASTTIME = isFE;
                hasFE = wifiAdmin.getFilterWifiList(PRIFIX_WIFI, isFE_LASTTIME).size()>0;
                List<ScanResult> scanResults = wifiAdmin.getFilterWifiList(PRIFIX_WIFI, isFE_LASTTIME);
//                if(scanResults.size() == 1)
//                    scanResults.add(scanResults.get(0));
                if (hasFE && !isFE && scanResults.size()==1){ //有且只有一个可用设备且没连接
                    ScanResult result = scanResults.get(0);
                    try {
//                        sendWifiMsg("wifiIsConnecting");
                        wac.connect(result.SSID, preferences.getString(result.SSID,"12345678"), WifiConnector.WifiCipherType.WIFICIPHER_WPA);
                        sendWifiMsg("连接成功!");//set success message
                        HASCONNECTCOUNTS++;
                        if(HASCONNECTCOUNTS>1)
                            wifiAdmin.startScan();
                        HASFAILCOUNTS=0;
                    } catch (Exception e){
                        e.printStackTrace();
                    }
                } else if(!isFE && scanResults.size()>1 && !isShowChoose){ //有2个以上设备没连接，并且没显示提升框
                    sendWifiMsg("多个!");
                } else if(!hasFE && !isFE){ //无可用设备
                    //在没有设备时尝试打开GPS, 也许不用，在权限正常的情况下
//                    boolean enabled = gpsService.isProviderEnabled(LocationManager.GPS_PROVIDER);
//                    if (!enabled) {
//                        Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
//                        startActivity(intent);
//                    }
                    HASCONNECTCOUNTS=0;
                    HASFAILCOUNTS++;
                    if(HASFAILCOUNTS > 1) {
                        sendWifiMsg("没有检测到3次");
                        wifiAdmin.startScan();
                    }
                    else
                        sendWifiMsg("没有!");
                }
                mWifiHandler.postDelayed(this, 4000);
            }
        };
        mWifiHandler.postDelayed(mWifiRunnable, 4000);
    }
    //向UI显示多个探头的选择框
    private void showChooseWifiDialog(){
        // custom dialog
        if(isShowChoose)
            return;
        List<ScanResult> ls = wifiAdmin.getFilterWifiList(PRIFIX_WIFI, isFE_LASTTIME);
        final String[] wifiNames = new String[ls.size()];
        for(int i=0;i<ls.size();i++)
            wifiNames[i] = ls.get(i).SSID;
//        new AlertDialog.Builder(videoLayout.getContext()).setTitle(R.string.info_choose_probe).setIcon( android.R.drawable.ic_dialog_info).setSingleChoiceItems(
//                wifiNames, 0,
//                new DialogInterface.OnClickListener() {
//                    public void onClick(DialogInterface dialog, int which) {
//                        dialog.dismiss();
//                        wac.connect(wifiNames[which], preferences.getString(wifiNames[which],"12345678"), WifiConnector.WifiCipherType.WIFICIPHER_WPA);
//                        isShowChoose = false;
//                    }
//                }).show();

        moreProbes.setSingleChoiceItems(wifiNames, 0,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        sendWifiMsg("wifiIsConnecting");
                        wac.connect(wifiNames[which], preferences.getString(wifiNames[which],"12345678"), WifiConnector.WifiCipherType.WIFICIPHER_WPA);
                        sendWifiMsg("连接成功!");
                        isShowChoose = false;
                    }
                });
        moreProbes.show();
        /*new AlertDialog.Builder(videoLayout.getContext())
                .setTitle("Delete entry")
                .setMessage("Are you sure you want to delete this entry?")
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        // continue with delete
                    }
                })
                .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        // do nothing
                    }
                })
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();*/

    }

    /**
     * 向UI发送消息
     * @param info 消息
     */
    public void sendWifiMsg(String info) {
        if (mWifiHandler != null) {
            Message msg = new Message();
            msg.obj = info;
            mWifiHandler.sendMessage(msg);// 向Handler发送消息
        } else {
            Log.e("wifi", info);
        }
    }

    // 本地设备搜索
    private void locanScanListener() {
        mScan = new CHD_LocalScan();

        mTimeHandler = new Handler();
        mTimeRunnable = new Runnable() {
            public void run() {
                int Num = mScan.getLocalScanNum();
                for (int i = 0; i < Num; i++) {
                    Log.v("test",
                            "Num = " + i + "Address:"
                                    + mScan.getLocalDevAddress(i));
                }
                if(Num>0 && !mClient.isOpenVideoStream()){ //已连接没打开的情况
                    try2Open();
                    System.out.println("run");
                }
                if (avi.isShown())
                    try2Open();
                mTimeHandler.postDelayed(this, 5000);
            }
        };
        mTimeHandler.postDelayed(mTimeRunnable, 5000);
    }

    // 客户端回调函数监听
    private void clientCallBackListener() {
        mClient = FE_Client.getInst().getClient();

        mClient.setClientCallBack(new ClientCallBack() {

            @Override
            public void paramChangeCallBack(int changeType) {
                // TODO Auto-generated method stub
            }

            @Override
            public void disConnectCallBack() {
//                try2Open();
            }

            @Override
            public void snapBitmapCallBack(Bitmap bitmap) {
                thumbnailImageView.setVisibility(View.VISIBLE);
                thumbnailImageView.setImageBitmap(bitmap);
                snapButton.setEnabled(true);
                snapButton.setBackgroundResource(R.drawable.ic_switch_camera);
//				延时1s后隐藏
                new Handler().postDelayed(new Runnable() {
                    public void run() {
                        thumbnailImageView.setVisibility(View.GONE);
                    }}, 500);
            }

            @Override
            public void recordTimeCountCallBack(String times) {
                recordTextView.setVisibility(View.VISIBLE);
                recordTextView.setText(times);
            }

            @Override
            public void recordStopBitmapCallBack(Bitmap bitmap) {
                thumbnailImageView.setVisibility(View.VISIBLE);
                thumbnailImageView.setImageBitmap(bitmap);
//				延时1s后隐藏
                new Handler().postDelayed(new Runnable() {
                    public void run() {
                        thumbnailImageView.setVisibility(View.GONE);
                        recordTextView.setText("00:00:00");
                        recordTextView.setVisibility(View.GONE);
                    }}, 1000);

            }

            @Override
            public void videoStreamBitmapCallBack(Bitmap bitmap) {
                // TODO Auto-generated method stub
                mStreamView.showBitmap(bitmap);
                if(isRecording)
                    MediaMuxerRunnable.addVideoFrameBitmap(bitmap);
            }

            @Override
            public void videoStreamDataCallBack(int format, int width,
                                                int height, int datalen, byte[] data) {
                // TODO Auto-generated method stub
            }

            @Override
            public void serialDataCallBack(int datalen, byte[] data) {
                // TODO Auto-generated method stub
                Log.v("test", "RecvSerialDataLen:"+datalen);
            }

            @Override
            public void audioDataCallBack(int datalen, byte[] data) {
                // TODO Auto-generated method stub

            }

        });
    }

    //打开或关闭视频流
    private boolean try2Open(){
        try{
            if(hasFE || isFE){
                mClient = FE_Client.getInst().getClient();
                if(mClient.openVideoStream() == -1){
                    mStreamView.clearScreen();
                    avi.show();
                    on_off.setChecked(false);
                    on_off.setVisibility(View.VISIBLE);
                } else {
                    avi.hide();
                    on_off.setVisibility(View.GONE);
                    on_off.setChecked(true);
                }
            } else {
                mStreamView.clearScreen();
                avi.show();
//                mClient.closeVideoStream();
//                mClient.disconnectDevice();
//                Toast.makeText(getActivity(), getActivity().getString(R.string.info_device_lose), Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e){ }
        return true;
    }

    // UI控件交互监听
    private void viewOnClickListener() {
        on_off.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener(){
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if(isChecked) {
                    mClient = FE_Client.getInst().getClient();
                    int out = mClient.openVideoStream();
                    if(out == -1){
                        on_off.setChecked(false);
//                        Toast.makeText(getActivity(), getActivity().getString(R.string.info_connect_first), Toast.LENGTH_SHORT).show();
                        Intent intent = new Intent(Settings.ACTION_WIFI_SETTINGS);//系统设置界面
                        startActivity(intent);
                        //delay 2.5s
//                        new Handler().postDelayed(new Runnable() {
//                            public void run() { try2Open();  }}, 4000);
                    } else {
                        on_off.setVisibility(View.GONE);
                        AnaIntentService.setEnable(switch_analyse.isChecked());
//                        service.setClass(getActivity(),AnaIntentService.class);
//                        getActivity().startService(service);
                    }
                }
                else{
                    mClient.disconnectDevice();
                    on_off.setVisibility(View.VISIBLE);
                    Toast.makeText(getActivity(), getActivity().getString(R.string.info_stoped), Toast.LENGTH_SHORT).show();
                    try{
                        //delay 0.3s
                        new Handler().postDelayed(new Runnable() {
                            public void run() { mStreamView.clearScreen();    avi.show();  }}, 300);
                        AnaIntentService.setEnable(switch_analyse.isChecked());
                    } catch (Exception e){
                    }
                }
            }
        });
//		拍照
        snapButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try2getAuthority();
                if(mClient.isOpenVideoStream()){
                    mClient.snapShot(FileUtils.getPhoteFullName());
                    snapButton.setEnabled(false);
                    snapButton.setBackgroundResource(R.drawable.ic_switch_camera_on);
                }
                else
                    Toast.makeText(getActivity(), getActivity().getString(R.string.info_open_first), Toast.LENGTH_SHORT).show();
            }
        });

        switchGpio.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(mClient.isConnect()){
                    for(int i=1;i<mClient.getGpioNum();i++){
//                        System.out.println("pro Gpio"+i+" is: "+mClient.getGpioState(i));
                        if(mClient.getGpioState(i)==st_GpioInfo.CHD_GPIO_STATE_HIGH)
                            mClient.setGpioValue(i, st_GpioInfo.CHD_GPIO_STATE_LOW);
                        else
                            mClient.setGpioValue(i, st_GpioInfo.CHD_GPIO_STATE_HIGH);
//                        System.out.println("Gpio"+i+" is: "+mClient.getGpioState(i));
                    }
                }else
                    Toast.makeText(getActivity(), getActivity().getString(R.string.info_connect_first), Toast.LENGTH_SHORT).show();
            }
        });

        start_stop.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener(){
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if(!try2getAuthority())
                    return;
                if(!mClient.isOpenVideoStream()){
                    Toast.makeText(getActivity(), getActivity().getString(R.string.record), Toast.LENGTH_SHORT).show();
                    buttonView.setChecked(false);
                    return;
                }
                if(isChecked) {
                    mClient.starMuxerRecord();
                    isRecording = true;
                }
                else{
                    mClient.stopMuxerRecord();
                    isRecording = false;
                }
            }
        });

        toggle_menus.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener(){
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if(!mClient.isOpenVideoStream()){
                    Toast.makeText(getActivity(), getActivity().getString(R.string.info_connect_first), Toast.LENGTH_SHORT).show();
                    buttonView.setChecked(false);
                    return;
                }
                if(isChecked) {
                    brightness.setProgress(FE_Client.getInst().getClient().VCtrl_getCurValue(CHD_Client.VIDEO_CTRL_TYPE_BRIGHTNESS));
                    contraster.setProgress(FE_Client.getInst().getClient().VCtrl_getCurValue(CHD_Client.VIDEO_CTRL_TYPE_CONTRAST));
                    toggleMenusView.setVisibility(View.VISIBLE);
                    toggleMenusView.animate()
                            .translationY(menusViewDistance)
                            .alpha(1.0f);
                    toggle_menus.setChecked(true);
                }
                else{
                    toggleMenusView.setVisibility(View.GONE);
                    toggleMenusView.animate()
                            .translationY(-menusViewDistance)
                            .alpha(0.0f);
                    toggle_menus.setChecked(false);
                }
            }
        });
        brightness.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                FE_Client.getInst().getClient().VCtrl_setCtrlValue(CHD_Client.VIDEO_CTRL_TYPE_BRIGHTNESS, progress);
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        contraster.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                FE_Client.getInst().getClient().VCtrl_setCtrlValue(CHD_Client.VIDEO_CTRL_TYPE_CONTRAST, progress);
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        switch_calibrate.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener(){

            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if(isChecked){
                    cirCleImgView.setVisibility(View.VISIBLE);
                    toggleMenusView.bringToFront();
                    toggleMenusView.invalidate();
                } else {
                    cirCleImgView.setVisibility(View.GONE);
                }
            }
        });
        switch_bright_manual.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener(){
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if(isChecked){
                    brightness.setEnabled(true);
                } else {
                    FE_Client.getInst().getClient().VCtrl_setCtrlValue(CHD_Client.VIDEO_CTRL_TYPE_BRIGHTNESS, DEFAULT_BIRGHTNESS);
                    brightness.setProgress(DEFAULT_BIRGHTNESS);
                    brightness.setEnabled(false);
                }
            }
        });
        switch_contrast_manual.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener(){
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if(isChecked){
                    contraster.setEnabled(true);
                } else {
                    FE_Client.getInst().getClient().VCtrl_setCtrlValue(CHD_Client.VIDEO_CTRL_TYPE_CONTRAST, DEFAULT_CONTRAST);
                    contraster.setProgress(DEFAULT_CONTRAST);
                    contraster.setEnabled(false);
                }
            }
        });
        switch_analyse.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener(){
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                AnaIntentService.setEnable(switch_analyse.isChecked());
                if(isChecked){
                    AnaIntentService.startAnalyer();
                } else {
                    AnaIntentService.stopAnalyer();
                }
            }
        });
    }


    private boolean try2getAuthority(){
        try {
            FileUtils.getInst().getStorageUriList();

        } catch (RuntimeException e) {
            Intent intent = new Intent();
            intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            Uri uri = Uri.fromParts("package", getActivity().getPackageName(), null);
            intent.setData(uri);
            getActivity().startActivity(intent);
            Toast.makeText(getActivity(), R.string.info_open_authority, Toast.LENGTH_LONG).show();
            return false;
        }
        return true;
    }
}
