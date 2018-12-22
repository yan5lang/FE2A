package ChirdSdk.Apis;

import android.graphics.Bitmap;

public class chd_coder {
	
	public static final int  CODE_FMT_H264    			= 28;
	public static final int  CODE_FMT_MJPEG  			= 8;
	
	public static final int  CODE_PIXEL_FMT_YUYV420    	= 0;
	public static final int  CODE_PIXEL_FMT_YUYV422    	= 1;
	public static final int  CODE_PIXEL_FMT_RGB24    	= 2;
	public static final int  CODE_PIXEL_FMT_BGR24    	= 3;
	public static final int  CODE_PIXEL_FMT_GRAY8    	= 8;
	public static final int  CODE_PIXEL_FMT_YUV420SP    = 25;
	public static final int  CODE_PIXEL_FMT_RGBA8888    = 28;
	public static final int  CODE_PIXEL_FMT_BGRA8888    = 30;
	public static final int  CODE_PIXEL_FMT_RGB565    	= 44;
	
	public static final int  CODE_FMT_G711U    			= 0xAA00;
	public static final int  CODE_FMT_G711A    			= 0xAA01;
	
	public static final int  MIXER_TYPE_VIDEO    		= 0;
	public static final int  MIXER_TYPE_AUDIO    		= 1;

	public native  long chird_vdec_create(int srcfmt, int destfmt);
	public native  int  chird_vdec_process(long handle, int width, int height, int datalen, byte [] data, Bitmap bitmap);
	public native  int  chird_vdec_processbyaddress(long handle, int width, int height, int datalen, long pdata, Bitmap bitmap);

	public native  int  chird_vdec_destory(long handle);
	
	public native  int  chird_vdec_bitmapcopy(Bitmap src, Bitmap dest);
	
	public native  int  chird_sws_process(int srcPixel, byte [] data, int destPixel, Bitmap bitmap, int width, int height);
	public native  int  chird_sws_processbyaddress(int srcPixel, long pdata, int destPixel, Bitmap bitmap, int width, int height);
	
	public native  long chird_adec_create(int srcfmt);
	public native  int  chird_adec_process(long handle,  byte [] srcdata, int srclen, byte [] destdata);
	public native  int  chird_adec_destory(long handle);
	
	public native  long chird_mixer_create(String fileName, int destfps, int srcvideofmt, int srcaudiofmt);
	public native  int  chird_mixer_process(long handle, int type, byte [] pdata, int datalen, int timerstamp);	
	public native  int  chird_mixer_processbyaddress(long handle, int type, long pdata, int datalen, int timerstamp);
	public native  int  chird_mixer_destory(long handle);
	
	
	
	public native  long chird_avidecoder_create(String fileName);
	
	public native  int  chird_avidecoder_destory(long handle);
	
	public native  int  chird_avidecoder_getInfo(long handle, st_AviInfo aviInfo);
	
	public native  int  chird_avidecoder_setFrameIdx(long handle, int idx);
	
	public native  int  chird_avidecoder_getFrameData(long handle, st_AviInfo aviInfo);
	
	static {
		System.loadLibrary("avutil-54");
    	System.loadLibrary("swresample-1");
    	System.loadLibrary("avcodec-56");
    	System.loadLibrary("avformat-56");
    	System.loadLibrary("swscale-3");
		System.loadLibrary("chd_coder");	
	} 
}
