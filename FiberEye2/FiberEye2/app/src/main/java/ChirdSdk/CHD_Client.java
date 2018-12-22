package ChirdSdk;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import ChirdSdk.Apis.chd_coder;
import ChirdSdk.Apis.chd_wmp_apis;
import ChirdSdk.Apis.st_AudioFrame;
import ChirdSdk.Apis.st_GpioInfo;
import ChirdSdk.Apis.st_VideoAbilityInfo;
import ChirdSdk.Apis.st_VideoCtrlInfo;
import ChirdSdk.Apis.st_VideoFrame;
import ChirdSdk.Apis.st_VideoParamInfo;
import ChirdSdk.Apis.st_WirelessInfo;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Surface;

import com.szetn.rencoder.MediaMuxerRunnable;
import com.szetn.service.AnaIntentService;
import com.szetn.util.FileUtils;

public class CHD_Client {
	
	public static final int     RET_ERROR_SUCCESS                           =  0;
	public static final int     RET_ERROR_RET_FAILED                        = -1;
	public static final int     RET_ERROR_RET_TIMEOUT                       = -2;
	public static final int     RET_ERROR_RET_NET_TIMEOUT                   = -4;
	public static final int     RET_ERROR_RET_DEVICE_OFFLINE                = -6;
	public static final int     RET_ERROR_RET_INVALID_HANDLE                = -7;
	public static final int     RET_ERROR_SESSIONID                         = -8;
	public static final int     RET_ERROR_PROTOCOL                          = -9;
	public static final int     RET_ERROR_NET_BIND                          = -10;
	public static final int     RET_ERROR_RET_INVALID_ADDRESS               = -11;
	public static final int     RET_ERROR_RET_DEVICE_NOT_ONLINE             = -12;
	public static final int     RET_ERROR_PASSWD                            = -13;
	public static final int     RET_ERROR_INVALID_PARAMETER                 = -15;
	public static final int     RET_ERROR_PARAMETER_LENGHTH_OVERFLOW        = -14;
	public static final int     RET_ERROR_INITIALIZED_FAIL                  = -16;

	public static final int DEFAULT_WIGHT = 1280;
	public static final int DEFAULT_HEIGHT = 720;

	private static final int SIGNAL_DISCONNECT = 0X01;
	private static final int SIGNAL_RECORD_TIME = 0X02;
	private static final int SIGNAL_RECORD_STOP = 0X03;
	private static final int SIGNAL_SNAP = 0X04;
	private static final int SIGNAL_PARAM_CHANGE = 0X05;

	private static final int VIDEO_DECODER_CACHE_NUMBER = 3;

	private chd_wmp_apis mClient = new chd_wmp_apis();
	private chd_coder mCoder = new chd_coder();
	private Decoder mDecoder = new Decoder(DEFAULT_WIGHT, DEFAULT_HEIGHT, null);

	private long mHandle;
	private long jpegHandle;
	private long h264Handle;
	private long recordHandle;

	private boolean isConnect;

	private int videoFps, videoBps;
	private Semaphore decoderSemp;
	private Semaphore displaySemp;
	private Queues videoQueue = new Queues();

	private Bitmap mBitmap = null;
	public boolean isOpenVideoStream;

	private String mSnapFileName;
	private int snapCnt;

	public static final int RECORD_STATUE_NONE = 0;
	public static final int RECORD_STATUE_START = 1;
	public static final int RECORD_STATUE_WRITEDATA = 2;
	public static final int RECORD_STATUE_STOP = 3;
	private boolean isRecord;
	private boolean isSupportRecord;
	private int recordStatue;
	private int recordTime;
	private Handler mRecordTimeHandler;
	private Runnable RecordTimeRunnable;
	private String mRecordFileName;

	private boolean isOpenAudio;
	private AudioTrack m_AudioTrack;
	static final int frequency = 8000;
	static final int channel2 = AudioFormat.CHANNEL_CONFIGURATION_STEREO;
	static final int channel3 = AudioFormat.CHANNEL_OUT_MONO;
	static final int audioEncoding = AudioFormat.ENCODING_PCM_16BIT;

	private boolean isOpenSerial;

	private boolean bGpioget;
	private st_GpioInfo mGpioInfo;

	private Handler mSignalHandler;

	private ClientCallBack mClientCallBack;

	private boolean isThreadQuit = true;

	private Surface mSurface = null;
	private boolean openHardwareDecode = false;

	public void setSurface(Surface surfece) {
		mSurface = surfece;
		mDecoder.setSurface(surfece);
	}

	public boolean isSupportH264HardwareDecoding() {
		return mDecoder.isH264CodecSupport();
	}

	public boolean isOpenH264HardwareDecoding() {
		return openHardwareDecode;
	}

	public int setH264HardwareDecoding(boolean isOpen) {

		if (isOpen) {
			if (mDecoder.isH264CodecSupport()) {
				openHardwareDecode = true;
				return 0;
			} else {
				return -1;
			}
		}

		openHardwareDecode = isOpen;

		return 0;
	}

	public CHD_Client() {
		mHandle = -1;
		isConnect = false;

		isOpenVideoStream = false;
		isOpenAudio = false;
		isOpenSerial = false;
		isSupportRecord = true;

		mClientCallBack = null;

		jpegHandle = mCoder.chird_vdec_create(mCoder.CODE_FMT_MJPEG,
				mCoder.CODE_PIXEL_FMT_RGB565);
		h264Handle = mCoder.chird_vdec_create(mCoder.CODE_FMT_H264,
				mCoder.CODE_PIXEL_FMT_RGB565);

		bGpioget = false;
		mGpioInfo = new st_GpioInfo();

		isRecord = false;

		decoderSemp = new Semaphore(0);
		displaySemp = new Semaphore(0);

		recordTime = 0;
		mRecordTimeHandler = new Handler();
		RecordTimeRunnable = new Runnable() {
			public void run() {
				SendMessage(SIGNAL_RECORD_TIME, recordTime++, 0);
				mRecordTimeHandler.postDelayed(this, 1000);
				Log.v("test", "record TimeCount:" + recordTime);
			}
		};

		mSignalHandler = new Handler() {
			public void handleMessage(Message msg) {

				if (mClientCallBack != null) {
					switch (msg.what) {
					case SIGNAL_DISCONNECT:
						mClientCallBack.disConnectCallBack();

					case SIGNAL_PARAM_CHANGE:
						mClientCallBack.paramChangeCallBack(msg.arg1);
						break;

					case SIGNAL_RECORD_STOP:

						recordTime = 0;
						/* record thumbnail Bitmap CallBack */
						synchronized (this) {
							if (mBitmap != null) {
								mClientCallBack
										.recordStopBitmapCallBack(mBitmap);
							}
						}

						break;
					case SIGNAL_RECORD_TIME:
						mClientCallBack
								.recordTimeCountCallBack(getRecordTimerString(msg.arg1));
						break;
					case SIGNAL_SNAP:
						/* snap thumbnail Bitmap CallBack */
						synchronized (this) {
							if (mBitmap != null) {
								mClientCallBack.snapBitmapCallBack(mBitmap);
							}
						}

						break;
					}
				}
			}
		};
	}

	private void SendMessage(int signal, int arg1, int arg2) {
		Message message = new Message();
		message.what = signal;
		message.arg1 = arg1;
		message.arg2 = arg2;
		mSignalHandler.sendMessage(message);
	}

	public void setClientCallBack(ClientCallBack callback) {
		mClientCallBack = callback;
	}

	public boolean isConnect() {
		return isConnect;
	}

	public int connectDevice(String Address, String Passwd) {
		if (isConnect){
			return RET_ERROR_SUCCESS;
		}

		if (mHandle > 0) {
			mClient.CHD_WMP_Disconnect(mHandle);
			mHandle = -1;
		}

		while (!isThreadQuit);

		Address = Address.replace(" ", "");
		Passwd = Passwd.replace(" ", "");

		mHandle = mClient.CHD_WMP_ConnectDeviceIndex(Address, 34674, Passwd);
		Log.v("test", "Handle:" + mHandle + "Address[" + Address + "]Passwd[" + Passwd + "]");

		if (mHandle < 0)		return (int)mHandle;

		isConnect = true;
		DataPollThread thread = new DataPollThread();
		thread.start();

		videoDecoderThread videoDecThread = new videoDecoderThread();
		videoDecThread.start();

		displayThread displayThread = new displayThread();
		displayThread.start();

		return RET_ERROR_SUCCESS;
	}

	public int disconnectDevice() {

		mClient.CHD_WMP_Disconnect(mHandle);
		isConnect = false;
		mHandle = -1;

		return RET_ERROR_SUCCESS;
	}

	public String getConnectPasswd() {
		if (!isConnect)
			return null;

		return mClient.CHD_WMP_GetEncrypt(mHandle);
	}

	public int setConnectPasswd(String passwd) {
		if (!isConnect)
			return -1;
		return mClient.CHD_WMP_SetEncrypt(mHandle, passwd);
	}

	public int getDeviceVersion() {
		if (!isConnect)
			return -1;
		int version = mClient.CHD_WMP_GetVersion(mHandle);
		return version >> 16;
	}

	public int getDeviceId() {
		if (!isConnect)
			return -1;
		return mClient.CHD_WMP_Device_GetId(mHandle);
	}

	public String getDeviceAlias() {
		if (!isConnect)
			return null;

		return mClient.CHD_WMP_Device_GetAlias(mHandle);
	}

	public int setDeviceId(int id) {
		if (!isConnect)
			return -1;
		return mClient.CHD_WMP_Device_SetId(mHandle, id);
	}

	public int setDeviceAlias(String alias) {
		if (!isConnect)
			return -1;
		return mClient.CHD_WMP_Device_SetAlias(mHandle, alias);
	}

	public int rebootDevice() {
		if (!isConnect)
			return -1;
		int ret = mClient.CHD_WMP_Device_Reboot(mHandle);
		if (ret < 0)
			return -1;

		disconnectDevice();

		return 0;
	}

	public int resetDevice() {
		if (!isConnect)
			return -1;

		return mClient.CHD_WMP_Device_Reset(mHandle);
	}

	public String getMacAddress() {
		if (!isConnect)
			return null;

		return mClient.CHD_WMP_GetMac(mHandle);
	}

	public static final int DEVICE_WIRELESS_MODE_AP = 0;
	public static final int DEVICE_WIRELESS_MODE_STA = 1;

	public int getDeviceWirelessMode() {
		if (!isConnect)
			return -1;
		return mClient.CHD_WMP_Wireless_GetNetType(mHandle);
	}

	public String getApName() {
		if (!isConnect)
			return null;
		st_WirelessInfo param = new st_WirelessInfo();

		int ret = mClient.CHD_WMP_Wireless_GetApInfo(mHandle, param);
		if (ret < 0)
			return null;

		return param.apssid;
	}

	public String getApPasswd() {
		if (!isConnect)
			return null;
		st_WirelessInfo param = new st_WirelessInfo();

		int ret = mClient.CHD_WMP_Wireless_GetApInfo(mHandle, param);
		if (ret < 0)
			return null;

		return param.apkey;
	}

	public int setApInfo(String name, String passwd) {
		if (!isConnect)
			return -1;
		st_WirelessInfo param = new st_WirelessInfo();

		param.apssid = name;
		param.apkey = passwd;

		int ret = mClient.CHD_WMP_Wireless_SetApInfo(mHandle, param);
		if (ret < 0)
			return ret;

		return 0;
	}

	public String getStaName() {
		if (!isConnect)
			return null;
		st_WirelessInfo param = new st_WirelessInfo();

		int ret = mClient.CHD_WMP_Wireless_GetStaInfo(mHandle, param);
		if (ret < 0)
			return null;

		return param.stassid;
	}

	public String getStaPasswd() {
		if (!isConnect)
			return null;
		st_WirelessInfo param = new st_WirelessInfo();

		int ret = mClient.CHD_WMP_Wireless_GetStaInfo(mHandle, param);
		if (ret < 0)
			return null;

		return param.stakey;
	}

	public int setStaInfo(String name, String passwd) {
		if (!isConnect)
			return -1;
		st_WirelessInfo param = new st_WirelessInfo();

		param.stassid = name;
		param.stakey = passwd;

		int ret = mClient.CHD_WMP_Wireless_SetStaInfo(mHandle, param);
		if (ret < 0)
			return ret;

		return 0;
	}

	public boolean isOpenVideoStream() {
		if (!isConnect)
			return false;
		return isOpenVideoStream;
	}

	public int openVideoStream() {
		if (!isConnect) {
			return -1;
		}

		snapCnt = 0;
		isOpenVideoStream = true;
		recordStatue = RECORD_STATUE_NONE;

		return mClient.CHD_WMP_Video_Begin(mHandle);
	}

	public int closeVideoStream() {
		if (!isConnect){
			return -1;
		}

		int ret = mClient.CHD_WMP_Video_End(mHandle);
		if (ret < 0)		return ret;
			
		if (mDecoder.isConfigure()){
			mDecoder.releaseConfigure();
		}

		isOpenVideoStream = false;
		return 0;
	}

	public int Video_getAbiFormatNum() {
		if (!isConnect)
			return -1;
		st_VideoAbilityInfo abi = new st_VideoAbilityInfo();
		int ret = mClient.CHD_WMP_Video_GetAbility(mHandle, abi);
		if (ret < 0)
			return ret;

		return abi.FormatNum;
	}

	public String Video_getAbiFormat(int Cnt) {
		if (!isConnect)
			return null;
		st_VideoAbilityInfo abi = new st_VideoAbilityInfo();
		int ret = mClient.CHD_WMP_Video_GetAbility(mHandle, abi);
		if (ret < 0 || abi.FormatNum < Cnt)
			return null;

		return abi.GetFormatString(abi.format[Cnt]);
	}

	public int Video_getAbiResoluNum() {
		if (!isConnect)
			return -1;
		st_VideoAbilityInfo abi = new st_VideoAbilityInfo();
		int ret = mClient.CHD_WMP_Video_GetAbility(mHandle, abi);
		if (ret < 0)
			return ret;

		return abi.ResoluNum;
	}

	public String Video_getAbiResolu(int Cnt) {
		if (!isConnect)
			return null;
		st_VideoAbilityInfo abi = new st_VideoAbilityInfo();
		int ret = mClient.CHD_WMP_Video_GetAbility(mHandle, abi);
		if (ret < 0 || abi.ResoluNum < Cnt)
			return null;

		return abi.GetResoluString(abi.width[Cnt], abi.height[Cnt]);
	}

	/* 视频参数（格式、分辨率、帧率） */
	public static int VIDEO_FORMAT_YUYV = 0X01;
	public static int VIDEO_FORMAT_MJPEG = 0X02;
	public static int VIDEO_FORMAT_H264 = 0X03;
	public static int VIDEO_FORMAT_YUV420SP = 0X04;

	public int Video_getFormat() {
		if (!isConnect)
			return -1;

		return mClient.CHD_WMP_Video_GetFormat(mHandle);
	}

	public int Video_getResoluWidth() {
		if (!isConnect)
			return -1;

		st_VideoParamInfo param = new st_VideoParamInfo();
		if (mClient.CHD_WMP_Video_GetResolu(mHandle, param) < 0)
			return -1;

		return param.width;
	}

	public int Video_getResoluHeight() {
		if (!isConnect)
			return -1;

		st_VideoParamInfo param = new st_VideoParamInfo();
		if (mClient.CHD_WMP_Video_GetResolu(mHandle, param) < 0)
			return -1;

		return param.height;
	}

	public int Video_getFps() {
		return mClient.CHD_WMP_Video_GetFPS(mHandle);
	}

	public int Video_getMaxFps() {
		int maxfps = -1;
		st_VideoParamInfo param = new st_VideoParamInfo();
		int ret = mClient.CHD_WMP_Video_GetParam(mHandle, param);
		if (ret >= 0) {
			maxfps = param.maxfps;
		}

		return maxfps;
	}

	public int Video_setFormat(int format) {
		return mClient.CHD_WMP_Video_SetFormat(mHandle, format);
	}

	public int Video_setResolu(int width, int height) {
		return mClient.CHD_WMP_Video_SetResolu(mHandle, width, height);
	}

	public int Video_setFps(int fps) {
		return mClient.CHD_WMP_Video_SetFPS(mHandle, fps);
	}

	/* camera control param type */
	public static int VIDEO_CTRL_TYPE_BRIGHTNESS = 0; // 亮度
	public static int VIDEO_CTRL_TYPE_CONTRAST = 1; // 对比度
	public static int VIDEO_CTRL_TYPE_SATURATION = 2; // 饱和度
	public static int VIDEO_CTRL_TYPE_HUE = 3; // 色调
	public static int VIDEO_CTRL_TYPE_WHITE_BALANCE = 4; // 白平衡
	public static int VIDEO_CTRL_TYPE_GAMMA = 5; // 伽马
	public static int VIDEO_CTRL_TYPE_GAIN = 6; // 增益
	public static int VIDEO_CTRL_TYPE_SHARPNESS = 7; // 清晰度
	public static int VIDEO_CTRL_TYPE_BACKLIGH = 8; // 背光补偿
	public static int VIDEO_CTRL_TYPE_EXPOSURE = 9; // 曝光值

	public boolean VCtrl_isSupportAutoCtrl(int CtrlType) {
		if (!isConnect)
			return false;

		st_VideoCtrlInfo vctrl = new st_VideoCtrlInfo();
		int ret = mClient.CHD_WMP_Video_GetVideoCtrl(mHandle, CtrlType, vctrl);
		if (ret < 0)
			return false;

		if (vctrl.auto_valid == 1)
			return true;
		else
			return false;
	}

	public boolean VCtrl_isAutoCtrl(int CtrlType) {
		if (!isConnect)
			return false;

		st_VideoCtrlInfo vctrl = new st_VideoCtrlInfo();
		int ret = mClient.CHD_WMP_Video_GetVideoCtrl(mHandle, CtrlType, vctrl);
		if (ret < 0)
			return false;
		if (vctrl.auto_valid == 0)
			return false;

		if (vctrl.autoval == 1)
			return true;
		else
			return false;
	}

	public boolean VCtrl_isSupportValueCtrl(int CtrlType) {
		if (!isConnect)
			return false;

		st_VideoCtrlInfo vctrl = new st_VideoCtrlInfo();
		int ret = mClient.CHD_WMP_Video_GetVideoCtrl(mHandle, CtrlType, vctrl);
		if (ret < 0)
			return false;

		if (vctrl.val_valid == 1)
			return true;
		else
			return false;
	}

	public int VCtrl_getMaxValue(int CtrlType) {
		if (!isConnect)
			return -1;

		st_VideoCtrlInfo vctrl = new st_VideoCtrlInfo();
		int ret = mClient.CHD_WMP_Video_GetVideoCtrl(mHandle, CtrlType, vctrl);
		if (ret < 0)
			return -1;

		return vctrl.maxval;
	}

	public int VCtrl_getMinValue(int CtrlType) {
		if (!isConnect)
			return -1;

		st_VideoCtrlInfo vctrl = new st_VideoCtrlInfo();
		int ret = mClient.CHD_WMP_Video_GetVideoCtrl(mHandle, CtrlType, vctrl);
		if (ret < 0)
			return -1;

		return vctrl.minval;
	}

	public int VCtrl_getCurValue(int CtrlType) {
		if (!isConnect)
			return -1;

		st_VideoCtrlInfo vctrl = new st_VideoCtrlInfo();
		int ret = mClient.CHD_WMP_Video_GetVideoCtrl(mHandle, CtrlType, vctrl);
		if (ret < 0)
			return -1;

		return vctrl.curval;
	}

	public int VCtrl_setAutoCtrl(int CtrlType, int Auto) {
		if (!isConnect)
			return -1;

		st_VideoCtrlInfo vctrl = new st_VideoCtrlInfo();
		int ret = mClient.CHD_WMP_Video_GetVideoCtrl(mHandle, CtrlType, vctrl);

		if (ret < 0 || vctrl.auto_valid == 0)
			return -1;

		vctrl.autoval = Auto;
		return mClient.CHD_WMP_Video_SetVideoCtrl(mHandle, CtrlType, vctrl);
	}

	public int VCtrl_setCtrlValue(int CtrlType, int Value) {
		if (!isConnect)
			return -1;

		st_VideoCtrlInfo vctrl = new st_VideoCtrlInfo();
		int ret = mClient.CHD_WMP_Video_GetVideoCtrl(mHandle, CtrlType, vctrl);

		if (ret < 0 || vctrl.val_valid == 0)
			return -1;

		if (Value > vctrl.maxval) {
			Value = vctrl.maxval;
		} else if (Value < vctrl.minval) {
			Value = vctrl.minval;
		}

		vctrl.curval = Value;
		return mClient.CHD_WMP_Video_SetVideoCtrl(mHandle, CtrlType, vctrl);
	}

	public int VCtrl_Reset() {
		if (!isConnect)
			return -1;

		return mClient.CHD_WMP_Video_ResetVCtrl(mHandle);
	}

	public int snapShot(String filename) {
		if (!isConnect)
			return -1;

		File file = new File(filename);
		File parentFile = file.getParentFile();
		if (!parentFile.exists()) {
			parentFile.mkdirs();
		}

		int ret;
		mSnapFileName = filename;
		ret = mClient.CHD_WMP_Video_SnapShot(mHandle);

		if (ret < 0) {
			snapCnt++;
			ret = 0;
		}

		return ret;
	}

	public boolean isRecord() {
		return isRecord;
	}

	public int starRecord(String filename) {

		if (!isConnect || !isOpenVideoStream || !isSupportRecord) {
			return -1;
		}

		if (isRecord)
			return 0;

		isRecord = true;
		recordTime = 0;
		mRecordFileName = filename;
		recordStatue = RECORD_STATUE_START;
		
		setVideoH264ForceI();

		/* open record timer */
		mRecordTimeHandler.postDelayed(RecordTimeRunnable, 1000);

		return 0;
	}

	public int stopRecord() {
		if (!isRecord || !isConnect)
			return 0;

		if (recordStatue == RECORD_STATUE_WRITEDATA) {
			recordStatue = RECORD_STATUE_STOP;
		} else {
			recordStatue = RECORD_STATUE_NONE;
		}
		isRecord = false;

		/* close record timer */
		mRecordTimeHandler.removeCallbacks(RecordTimeRunnable);

		SendMessage(SIGNAL_RECORD_STOP, 0, 0);

		return 0;
	}


	//use MediaMuxerRunnable.startMuxer();
	public int starMuxerRecord(){
		if (!isConnect || !isOpenVideoStream || !isSupportRecord){
			return -1;
		}
//		isRecord	 	 = true;
		recordTime      = 0;
		 /* open record timer */
		mRecordTimeHandler.postDelayed(RecordTimeRunnable, 1000);
		MediaMuxerRunnable.startMuxer();
		return 0;
	}
	//use MediaMuxerRunnable.stopMuxer();
	public int stopMuxerRecord(){
		if (!isConnect)     return 0;
//		isRecord = false;
	     /* close record timer */
		mRecordTimeHandler.removeCallbacks(RecordTimeRunnable);
		SendMessage(SIGNAL_RECORD_STOP, 0, 0);
		MediaMuxerRunnable.stopMuxer();
		return 0;
	}

	/* H264传输相关函数 I帧间隔(10~240) */
	public int getVideoH264StreamGop() {
		if (!isConnect)
			return -1;

		return mClient.CHD_WMP_Video_GetH264KeyInter(mHandle);
	}

	public int getVideoH264StreamQpValue() {
		if (!isConnect)
			return -1;

		return mClient.CHD_WMP_Video_GetH264QpValue(mHandle);
	}

	public int setVideoH264StreamGop(int value) {
		if (!isConnect)
			return -1;

		return mClient.CHD_WMP_Video_SetH264KeyInter(mHandle, value);
	}

	/* QP值(1~100) */
	public int setVideoH264StreamQpValue(int value) {
		if (!isConnect)
			return -1;

		return mClient.CHD_WMP_Video_SetH264QpValue(mHandle, value);
	}

	public int setVideoH264ForceI() {
		if (!isConnect)
			return -1;

		return mClient.CHD_WMP_Video_SetForceI(mHandle);
	}

	public String getVideoFrameFps() {

		return String.valueOf(videoFps);
	}

	public String getVideoFrameBps() {
		return SizeLongToString(videoBps) + "/s";
	}

	public boolean isOpenSerial() {
		if (!isConnect)
			return false;

		return isOpenSerial;
	}

	public int openSerial() {
		if (!isConnect)
			return -1;

		int ret = mClient.CHD_WMP_Serial_Begin(mHandle);
		if (ret < 0)
			return ret;

		isOpenSerial = true;

		return 0;
	}

	public int closeSerial() {
		if (!isConnect)
			return -1;

		int ret = mClient.CHD_WMP_Serial_End(mHandle);
		if (ret < 0)
			return ret;

		isOpenSerial = false;

		return 0;
	}

	/* 串口相关 */
	public static int SERIAL_SPEED_BS300 = 300;
	public static int SERIAL_SPEED_BS1200 = 1200;
	public static int SERIAL_SPEED_BS2400 = 2400;
	public static int SERIAL_SPEED_BS4800 = 4800;
	public static int SERIAL_SPEED_BS9600 = 9600;
	public static int SERIAL_SPEED_BS19200 = 19200;
	public static int SERIAL_SPEED_BS38400 = 38400;
	public static int SERIAL_SPEED_BS57600 = 57600;
	public static int SERIAL_SPEED_BS115200 = 115200;
	public static int SERIAL_SPEED_BS230400 = 230400;

	public int getSerialSpeed() {
		if (!isConnect)
			return -1;

		return mClient.CHD_WMP_Serial_GetSpeed(mHandle);
	}

	public int setSerialSpeed(int value) {
		if (!isConnect)
			return -1;

		return mClient.CHD_WMP_Serial_SetSpeed(mHandle, value);
	}

	public static int SERIAL_DATABIT_7 = 7;
	public static int SERIAL_DATABIT_8 = 8;

	public int getSerialDataBit() {
		if (!isConnect)
			return -1;

		return mClient.CHD_WMP_Serial_GetDataBit(mHandle);
	}

	public int setSerialDataBit(int value) {
		if (!isConnect)
			return -1;

		return mClient.CHD_WMP_Serial_SetDataBit(mHandle, value);
	}

	public static int SERIAL_STOPBIT_1 = 1;
	public static int SERIAL_STOPBIT_0 = 0;

	public int getSerialStopBit() {
		if (!isConnect)
			return -1;

		return mClient.CHD_WMP_Serial_GetStopBit(mHandle);
	}

	public int setSerialStopBit(int value) {
		if (!isConnect)
			return -1;

		return mClient.CHD_WMP_Serial_SetStopBit(mHandle, value);
	}

	public static int SERIAL_PARITY_EVEN = 69;
	public static int SERIAL_PARITY_NONE = 78;
	public static int SERIAL_PARITY_ODD = 79;
	public static int SERIAL_PARITY_SPACE = 83;

	public int getSerialParityBit() {
		if (!isConnect)
			return -1;

		return mClient.CHD_WMP_Serial_GetParity(mHandle);
	}

	public int setSerialParity(int value) {
		if (!isConnect)
			return -1;

		return mClient.CHD_WMP_Serial_SetParity(mHandle, value);
	}

	public int sendSerialData(byte[] data, int datalen) {
		if (!isConnect || !isOpenSerial)
			return -1;

		return mClient.CHD_WMP_Serial_SendData(mHandle, data, datalen);
	}

	public boolean isOpenAudio() {
		if (!isConnect)
			return false;

		return isOpenAudio;
	}

	public int openAudioStream() {
		if (!isConnect)
			return -1;

		if (mClient.CHD_WMP_Audio_Begin(mHandle) < 0) {
			return -1;
		}

		if ((mClient.CHD_WMP_GetVersion(mHandle) >> 16) > 2) {
			int plyBufSize = AudioTrack.getMinBufferSize(frequency, channel3,
					audioEncoding) * 2;
			m_AudioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, frequency,
					channel3, audioEncoding, plyBufSize, AudioTrack.MODE_STREAM);
		} else {
			int plyBufSize = AudioTrack.getMinBufferSize(frequency, channel2,
					audioEncoding) * 2;
			m_AudioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, frequency,
					channel2, audioEncoding, plyBufSize, AudioTrack.MODE_STREAM);
		}

		m_AudioTrack.play();
		isOpenAudio = true;

		return 0;
	}

	public int closeAudioStream() {
		if (!isConnect)
			return -1;

		if (mClient.CHD_WMP_Audio_End(mHandle) < 0) {
			return -1;
		}
		isOpenAudio = false;
		m_AudioTrack.stop();

		return 0;
	}

	public int getGpioNum() {
		if (!isConnect)
			return -1;

		if (!bGpioget) {
			bGpioget = true;
			mClient.CHD_WMP_Gpio_GetAll(mHandle, mGpioInfo);
		}

		return mGpioInfo.number;
	}

	public static int GPIO_DIR_IN = 1;
	public static int GPIO_DIR_OUT = 0;

	public int getGpioDir(int gpio) {
		if (!isConnect)
			return -1;

		if (!bGpioget) {
			bGpioget = true;
			mClient.CHD_WMP_Gpio_GetAll(mHandle, mGpioInfo);
		}

		if (gpio > mGpioInfo.number)
			return -1;

		if (mClient.CHD_WMP_Gpio_GetStatus(mHandle, gpio, mGpioInfo) < 0) {
			return -1;
		}

		return mGpioInfo.dir[gpio];
	}

	public int setGpioDir(int gpio, int dir) {
		if (!isConnect)
			return -1;

		if (!bGpioget) {
			bGpioget = true;
			mClient.CHD_WMP_Gpio_GetAll(mHandle, mGpioInfo);
		}
		if (gpio > mGpioInfo.number)
			return -1;

		return mClient.CHD_WMP_Gpio_SetStatus(mHandle, gpio, dir,
				mGpioInfo.state[gpio]);
	}

	public static int GPIO_VALUE_HIGH = 1;
	public static int GPIO_VALUE_LOW = 0;

	public int getGpioState(int gpio) {
		if (!isConnect)
			return -1;

		if (!bGpioget) {
			bGpioget = true;
			mClient.CHD_WMP_Gpio_GetAll(mHandle, mGpioInfo);
		}
		if (gpio > mGpioInfo.number)
			return -1;

		int ret = mClient.CHD_WMP_Gpio_GetStatus(mHandle, gpio, mGpioInfo);
		if (ret < 0) {
			return ret;
		}

		return mGpioInfo.state[gpio];
	}

	public int setGpioValue(int gpio, int state) {
		if (!isConnect)
			return -1;

		if (!bGpioget) {
			bGpioget = true;
			mClient.CHD_WMP_Gpio_GetAll(mHandle, mGpioInfo);
		}
		if (gpio > mGpioInfo.number)
			return -1;

		return mClient.CHD_WMP_Gpio_SetStatus(mHandle, gpio,
				mGpioInfo.dir[gpio], state);
	}

	public int H264HardwareDecoding(byte[] data, int length) {

		if (mDecoder.isStart()) {
			mDecoder.start();
		}

		return mDecoder.decodeToSurface(data, length);
	}

	class DataPollThread extends Thread {
		private int type;
		private boolean bNewAudioBuf;
		private int audioBufLen;
		private byte[] audioBuffer;

		private Boolean bNewSerialBuf;
		byte[] serialBuffer;

		private boolean bNewPicBuf;
		private byte[] pictureBuffer;
		private st_VideoFrame vframe = new st_VideoFrame();

		private int timeoutCnt;

		public void run() {
			timeoutCnt = 1;
			audioBufLen = 0;
			bNewAudioBuf = false;
			bNewPicBuf = false;
			bNewSerialBuf = false;

			long handle = mHandle;

			isThreadQuit = false;

			while (isConnect) {
				type = mClient.CHD_WMP_Poll(mHandle, 200);

				/* Devcice Disconnect */
				if (type == -6 || type == -7)
					break;

				/* Receive Timeout */
				if (type == -2) {
					if (timeoutCnt++ % 10 == 0 && isOpenVideoStream) {
						mClient.CHD_WMP_Video_Begin(mHandle);
					}
					continue;
				}

				timeoutCnt = 1;

				/* Video Data */
				if ((type & mClient.CHD_DATA_YTPE_VIDEO) == mClient.CHD_DATA_YTPE_VIDEO) {

					int ret = mClient.CHD_WMP_Video_RequestVideoDataAddress(
							mHandle, vframe);
					if (ret >= 0) {
						// Log.v("test", "Videofps:"+vframe.fps);
						videoFps = vframe.fps;
						videoBps = vframe.BPS;

						/* recording */
						if (vframe.format == vframe.CHD_FMT_YUYV
								|| vframe.bexist == 0) {
							isSupportRecord = false;
						} else {
							isSupportRecord = true;
						}

						if (isSupportRecord && isRecord
								&& recordStatue == RECORD_STATUE_START) {
							int srcvideofmt = mCoder.CODE_FMT_MJPEG;
							if (vframe.format == vframe.CHD_FMT_H264) {
								srcvideofmt = mCoder.CODE_FMT_H264;
							}

							recordHandle = mCoder
									.chird_mixer_create(mRecordFileName,
											vframe.fps, srcvideofmt, 0);
							recordStatue = RECORD_STATUE_WRITEDATA;
						}

						if (isRecord && recordStatue == RECORD_STATUE_WRITEDATA) {
							mCoder.chird_mixer_processbyaddress(recordHandle,
									mCoder.MIXER_TYPE_VIDEO,
									vframe.pDataAddress, vframe.datalen, 0);
						}

						if (recordStatue == RECORD_STATUE_STOP) {
							mCoder.chird_mixer_destory(recordHandle);

							recordStatue = RECORD_STATUE_NONE;
						}

						/*
						 * Put Data To Queue And Send Signal Notification thread
						 * decoding
						 */
						videoQueue.putQueue(vframe);
						decoderSemp.release();
					}

				}

				/* Picture Data */
				if ((type & mClient.CHD_DATA_YTPE_PICTURE) == mClient.CHD_DATA_YTPE_PICTURE) {

					if (!bNewPicBuf) {
						bNewPicBuf = true;
						pictureBuffer = new byte[1024 * 1024 * 2];
					}

					st_VideoFrame pframe = new st_VideoFrame();
					int ret = mClient.CHD_WMP_Video_RequestPicData(mHandle,
							pframe, pictureBuffer);
					//add install mSnapFileName when triger by hardware
					if(mSnapFileName == null)
						mSnapFileName = FileUtils.getPhoteFullName();

					if (ret >= 0 && mSnapFileName != null) {
						//chage it if can't snap
//						if (pframe.format == pframe.CHD_FMT_MJPEG){
//							mClient.CHD_WMP_File_Save(mSnapFileName,
//									pframe.datalen, pictureBuffer);
//						}
//						else
							SaveBitmap(mSnapFileName, mBitmap);
						//auto analysis
						AnaIntentService.addVideoFrameBitmap(mSnapFileName,mBitmap);
						mSnapFileName = null;
						SendMessage(SIGNAL_SNAP, 0, 0);
					}
				}

				/* Audio Data */
				if ((type & mClient.CHD_DATA_YTPE_AUDIO) == mClient.CHD_DATA_YTPE_AUDIO) {
					if (!bNewAudioBuf) {
						audioBuffer = new byte[1024 * 300];
					}

					st_AudioFrame stAudioFrame = new st_AudioFrame();
					int ret = mClient.CHD_WMP_Audio_RequestData(mHandle,
							stAudioFrame, audioBuffer);
					if (isOpenAudio && ret >= 0) {
						m_AudioTrack
								.write(audioBuffer, 0, stAudioFrame.datalen);
					}

					/* get audio Frame Data CallBack */
					if (mClientCallBack != null) {
						mClientCallBack.audioDataCallBack(stAudioFrame.datalen,
								audioBuffer);
					}
				}

				/* Serial Data */
				if ((type & mClient.CHD_DATA_YTPE_SERIAL) == mClient.CHD_DATA_YTPE_SERIAL) {
					if (!bNewSerialBuf) {
						bNewSerialBuf = true;
						serialBuffer = new byte[1024 * 300];
					}

					int len = mClient.CHD_WMP_Serial_RequestData(mHandle,
							serialBuffer);

					if (mClientCallBack != null && len > 0) {
						mClientCallBack.serialDataCallBack(len, serialBuffer);
					}
				}

				/* Device Param Changes */
				if ((type & mClient.CHD_PARAM_CHANGE) == mClient.CHD_PARAM_CHANGE) {
					int changeType = mClient
							.CHD_WMP_GetParamChangeType(mHandle);

					SendMessage(SIGNAL_PARAM_CHANGE, changeType, 0);
				}

			}

			/* if the record is not closed, turn off the record */
			if (isRecord && recordStatue == RECORD_STATUE_STOP) {
				mCoder.chird_mixer_destory(recordHandle);

				isRecord = false;
				recordStatue = RECORD_STATUE_NONE;
				SendMessage(SIGNAL_RECORD_STOP, 0, 0);
			}

			/* unexpected exit, reclaim space */
			if (handle == mHandle) {
				mClient.CHD_WMP_Disconnect(mHandle);
				isConnect = false;

				isOpenAudio = false;
				isRecord = false;
				isOpenVideoStream = false;
			}

			decoderSemp.release();
			SendMessage(SIGNAL_DISCONNECT, 0, 0);
			mRecordTimeHandler.removeCallbacks(RecordTimeRunnable);

//			Log.v("test", "ret = " + type + " disconnectHandle:" + mHandle);

			mHandle = -1;
			isThreadQuit = true;
		}

	}

	class videoDecoderThread extends Thread {
		private int ret;
		private Bitmap bitmap = null;

		private Boolean bNewVideoDataBuf;
		private int videoDataBufLen;
		private byte[] videoDataBuf;

		private boolean geIflag = false;

		public void run() {

			bNewVideoDataBuf = false;

			while (isConnect) {
				try {
					decoderSemp.acquire();
				} catch (InterruptedException e) {
					e.printStackTrace();
					continue;
				}
				if (videoQueue.getLength() <= 0)
					continue;

				st_VideoFrame videoframe = videoQueue.getQueue();
				if (videoframe == null)		continue;
					

//				 Log.v("test", "videoQueueCnt:"+videoQueue.getLength());

				if (!bNewVideoDataBuf || videoDataBufLen < videoframe.datalen) {
					bNewVideoDataBuf = true;
					videoDataBufLen = videoframe.datalen;
					videoDataBuf = new byte[videoDataBufLen];
				}
				mClient.CHD_WMP_Video_CopyVideoDataToByteArray(mHandle, videoframe, videoDataBuf);
				/* get Video Frame Data CallBack */
				if (mClientCallBack != null) {
					mClientCallBack.videoStreamDataCallBack(videoframe.format,
							videoframe.width, videoframe.height, videoframe.datalen, videoDataBuf);
				}

				/* H264 Hardware Decoding... */
				if (openHardwareDecode
						&& videoframe.format == videoframe.CHD_FMT_H264) {
					if (videoframe.width != mDecoder.getWidth()
							|| videoframe.height != mDecoder.getHeight()) {
						setVideoH264ForceI();
						mDecoder.setConfigure(mSurface, videoframe.width, videoframe.height);
						geIflag = true;
					}
					
					if (!mDecoder.isConfigure()){
						mDecoder.setConfigure(mSurface, videoframe.width, videoframe.height);
					}

					if (geIflag) {
						if (videoframe.iflag == 1) {
							mDecoder.decodeToSurface(videoDataBuf, videoframe.datalen);
							geIflag = false;
						}
					} else {
						int ret = mDecoder.decodeToSurface(videoDataBuf, videoframe.datalen);
					}
					mClient.CHD_WMP_Video_ReleaseVideoDataAddress(mHandle, videoframe);
					continue;
				}else {
					if (mDecoder.isConfigure()){
						mDecoder.releaseConfigure();
					}
				}

				if (videoQueue.getLength() >= VIDEO_DECODER_CACHE_NUMBER
						&& videoframe.format != videoframe.CHD_FMT_H264) {
					mClient.CHD_WMP_Video_ReleaseVideoDataAddress(mHandle,
							videoframe);
					continue;
				}

				/* Resolu Change, recreate bitmap to display */
				if (bitmap == null || mBitmap == null ||bitmap.getWidth() != videoframe.width
						|| bitmap.getHeight() != videoframe.height) {
					if (bitmap != null && !bitmap.isRecycled()) {
						bitmap.recycle();
						bitmap = null;
						System.gc();
					}
					try {
						bitmap = Bitmap.createBitmap(videoframe.width,
								videoframe.height, Config.RGB_565);
					} catch (OutOfMemoryError e) {
						bitmap = Bitmap
								.createBitmap(160, 120, Config.RGB_565);
					}
					synchronized (this) {
						if (mBitmap != null && !mBitmap.isRecycled()) {
							mBitmap.recycle();
							mBitmap = null;
							System.gc();
						}
						try {
							mBitmap = Bitmap.createBitmap(videoframe.width,
									videoframe.height, Config.RGB_565);
						} catch (OutOfMemoryError e) {
							mBitmap = Bitmap.createBitmap(160, 120,
									Config.RGB_565);
						}
					}
				}

				ret = -1;
				if (videoframe.format == videoframe.CHD_FMT_YUYV) {
					ret = mCoder.chird_sws_processbyaddress(
							mCoder.CODE_PIXEL_FMT_YUYV422,
							videoframe.pDataAddress,
							mCoder.CODE_PIXEL_FMT_RGB565, bitmap,
							videoframe.width, videoframe.height);
				} else if (videoframe.format == videoframe.CHD_FMT_MJPEG) {
					ret = mCoder
							.chird_vdec_processbyaddress(jpegHandle,
									videoframe.width, videoframe.height,
									videoframe.datalen,
									videoframe.pDataAddress, bitmap);
				} else if (videoframe.format == videoframe.CHD_FMT_H264) {
					ret = mCoder
							.chird_vdec_processbyaddress(h264Handle,
									videoframe.width, videoframe.height,
									videoframe.datalen,
									videoframe.pDataAddress, bitmap);
				}

				if (ret >= 0){
					synchronized (this) {
						if (mCoder.chird_vdec_bitmapcopy(bitmap, mBitmap) >= 0) {
							displaySemp.release();
						}
					}
				}

				mClient.CHD_WMP_Video_ReleaseVideoDataAddress(mHandle, videoframe);
			}

			/* Recovery Bitmap Space */
			if (bitmap != null && !bitmap.isRecycled()) {
				bitmap.recycle();
				bitmap = null;
				System.gc();
			}
			
			mDecoder.releaseConfigure();

			displaySemp.release();
		}
	}

	class displayThread extends Thread {

		public void run() {

			while (isConnect) {
				/* Wait Decoding Successful Signal */
				try {
					displaySemp.acquire();
				} catch (InterruptedException e) {
					e.printStackTrace();
					continue;
				}

				synchronized (this) {
					/* Display Bitmap CallBack */
					if (mClientCallBack != null && mBitmap != null) {
						mClientCallBack.videoStreamBitmapCallBack(mBitmap);
					}

					/* save picture and send return thumbnail signal */
					if (snapCnt > 0) {
						snapCnt--;
						SendMessage(SIGNAL_SNAP, 0, 0);
						if (mSnapFileName != null) {
							SaveBitmap(mSnapFileName, mBitmap);
						}
					}
				}

			}
		}
	}

	protected void finalize() {
		isConnect = false;
		mClient.CHD_WMP_Disconnect(mHandle);
		m_AudioTrack.stop();

		mDecoder.stop();

		mCoder.chird_vdec_destory(jpegHandle);
		mCoder.chird_vdec_destory(h264Handle);
	}

	public int SaveBitmap(String filename, Bitmap mBitmap) {
		File file = new File(filename);
		try {
			file.createNewFile();
		} catch (IOException e) {
			return -1;
		}
		FileOutputStream fOut = null;
		try {
			fOut = new FileOutputStream(file);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			return -1;
		}

		mBitmap.compress(Bitmap.CompressFormat.PNG, 100, fOut);
		try {
			fOut.flush();
		} catch (IOException e) {
			e.printStackTrace();
			return -1;
		}
		try {
			fOut.close();
		} catch (IOException e) {
			e.printStackTrace();
			return -1;
		}

		return 0;
	}

	private String getRecordTimerString(int timer) {
		String Stimer;

		if ((timer / 3600 % 60 % 23) >= 10)
			Stimer = String.valueOf(timer / 3600 % 60 % 23) + ":";
		else
			Stimer = "0" + String.valueOf(timer / 3600 % 60 % 23) + ":";

		if ((timer / 60 % 60) >= 10)
			Stimer += String.valueOf(timer / 60 % 60) + ":";
		else
			Stimer += "0" + String.valueOf(timer / 60 % 60) + ":";

		if ((timer % 60) >= 10)
			Stimer += String.valueOf(timer % 60);
		else
			Stimer += "0" + String.valueOf(timer % 60);

		return Stimer;
	}

	public String SizeLongToString(long fileS) {
		DecimalFormat df = new DecimalFormat("#.00");
		String fileSizeString = "";
		String wrongSize = "0B";
		if (fileS == 0) {
			return wrongSize;
		}
		if (fileS - 1024.0 < 0) {
			fileSizeString = df.format((double) fileS) + "B";
		} else if (fileS - 1048576.0 < 0) {
			fileSizeString = df.format((double) fileS / 1024) + "KB";
		} else if (fileS - 1073741824.0 < 0) {
			fileSizeString = df.format((double) fileS / 1048576) + "MB";
		} else {
			fileSizeString = df.format((double) fileS / 1073741824) + "GB";
		}
		return fileSizeString;
	}

}
