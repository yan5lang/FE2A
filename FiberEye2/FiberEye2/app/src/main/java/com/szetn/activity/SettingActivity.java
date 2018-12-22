package com.szetn.activity;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.szetn.fibereye.R;
import com.szetn.util.StringUtils;

import ChirdSdk.CHD_Client;
import ChirdSdk.FE_Client;

import static com.szetn.fibereye.VideoFragment.PRIFIX_WIFI;

/**
 * Created by yan5l on 11/20/2016.
 */
public class SettingActivity extends Activity implements View.OnClickListener{

    private CHD_Client mClient;
    TextView anNewWP,apName,apID,anOldWP;
    String oldApWP;

    SharedPreferences preferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.setting_layout);
//        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);// 横屏
        preferences = getSharedPreferences(PRIFIX_WIFI, MODE_APPEND);

        mClient = FE_Client.getInst().getClient();
        String oldApName = mClient.getApName();
        oldApWP = mClient.getApPasswd();

        apName = (TextView)findViewById(R.id.apName);
        anOldWP = (TextView)findViewById(R.id.apOldWp);
        anNewWP = (TextView)findViewById(R.id.apNewWP);
        apID = (TextView)findViewById(R.id.apID);
        apName.setText(mClient.getDeviceAlias());
        apID.setText(mClient.getApName());

        Button save = (Button)findViewById(R.id.apSave);
        Button cancle = (Button)findViewById(R.id.apCancle);
        Button reset = (Button)findViewById(R.id.apReset);
        save.setOnClickListener(this);
        cancle.setOnClickListener(this);
        reset.setOnClickListener(this);

        if(StringUtils.isEmpty(oldApName)){
            save.setEnabled(false);
            reset.setEnabled(false);
        }else {
            save.setEnabled(true);
            reset.setEnabled(true);
            anOldWP.setText(oldApWP);
        }
    }

    @Override
    public void onClick(View v) {

        switch (v.getId()) {
            case R.id.apSave:
                System.out.print(mClient.getApName());
                System.out.print(mClient.getApPasswd());
                if(anNewWP.getText().length()<8){
                    Toast.makeText(this, this.getString(R.string.info_save_fail_reason1), Toast.LENGTH_SHORT).show();
                    anNewWP.setText("");
                }
                else if(!anNewWP.getText().toString().equals(oldApWP)){
                    int out = mClient.setApInfo(apID.getText().toString(),anNewWP.getText().toString());
                    if(out == 0){
                        //更新本地缓存记录
                        preferences.edit().putString(apID.getText().toString(), anNewWP.getText().toString()).apply();
                        anOldWP.setText(anNewWP.getText().toString());
                        anNewWP.setText("");
                        Toast.makeText(this, this.getString(R.string.info_save_suc), Toast.LENGTH_SHORT).show();
                        mClient.rebootDevice();
                    }
                    else
                        Toast.makeText(this, this.getString(R.string.info_save_fail), Toast.LENGTH_SHORT).show();
                }
                else {
                    Toast.makeText(this, this.getString(R.string.info_save_fail_reason2), Toast.LENGTH_SHORT).show();
                    anNewWP.setText("");
                }
                break;
            case R.id.apReset:
                int out = mClient.setApInfo(apID.getText().toString(),"12345678");
                if(out == 0){
                    //更新本地缓存记录
                    preferences.edit().remove(apID.getText().toString()).apply();
                    anOldWP.setText("12345678");
                    Toast.makeText(this, this.getString(R.string.info_save_suc), Toast.LENGTH_SHORT).show();
                    mClient.rebootDevice();
                }
                else
                    Toast.makeText(this, this.getString(R.string.info_save_fail), Toast.LENGTH_SHORT).show();
                anNewWP.setText("");
                break;
            default:
                break;
        }

    }
}
