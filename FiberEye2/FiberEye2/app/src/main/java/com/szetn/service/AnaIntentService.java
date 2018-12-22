package com.szetn.service;

import android.graphics.Bitmap;
import android.util.Log;

import com.szetn.util.FileUtils;

import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

/**
 * Created by yan5l on 12/27/2016.
 */

public class AnaIntentService extends Thread {

    private Mat mRgbaSS;					//ef1
    private String imgFile;
    boolean efd_init_done = false;

    private static final boolean DEBUG = true;			//ef1 tmp
    private static final String TAG = "AnaIntentService";

    private static AnaIntentService mAnaIntentService;
    private final Object lock = new Object();
    private volatile boolean isExit = false;
    private static boolean isNeedAnalysis = true;
    private boolean isAnalysising = false;


    private AnaIntentService(){}

    public static void startAnalyer() {
        if (mAnaIntentService == null && isNeedAnalysis) {
            synchronized (AnaIntentService.class) {
                if (mAnaIntentService == null) {
                    mAnaIntentService = new AnaIntentService();
                    mAnaIntentService.start();
                }
            }
        }
    }
    public static void stopAnalyer() {
        if (mAnaIntentService != null && isNeedAnalysis) {
            mAnaIntentService.exit();
            try {
                mAnaIntentService.join();
            } catch (InterruptedException e) {

            }
            mAnaIntentService = null;
        }
    }

    public static void setEnable(boolean enable){
        isNeedAnalysis = enable;
    }

    private void exit() {
        isExit = true;
        synchronized (lock) {
            lock.notify();
        }
    }

    public static void addVideoFrameBitmap(String imgFile, Bitmap bitmap) {
        if (mAnaIntentService != null && isNeedAnalysis) {
            mAnaIntentService.analyzeImage(imgFile, bitmap);
        }
    }

    private void initAnalyer(){
        if (!efd_init_done) {
            try {
                efd_SetStorageRoot();  //初始化根目录
                FileUtils.getInst().delete( FileUtils.getInst().getExtFile(FileUtils.FIBERROOT+"EndFaceData") );
                ;
                efdInitSystem();        //初始化EFD系统，用户改过的值，如没改动用初始值
                efdSelectScopeProfile(0);        //ef1 tmp//精度
                efdSelectFiberProfile(0);        //fiber 标准， IPC/IEC etc.
            } catch (Exception e){
                e.printStackTrace();
            }
            efd_init_done = true;
        }
        readyStart();
    }

    private void readyStart() {
        isExit = false;
        if(mRgbaSS != null)
            mRgbaSS.empty();
        synchronized (lock) {
            lock.notify();
        }
    }
    @Override
    public void run() {
        initAnalyer();
        while (!isExit && efd_init_done) {
            if (mRgbaSS == null || mRgbaSS.empty()) {
                synchronized (lock) {
                    try {
                        if (DEBUG) Log.e("ang-->", "等待分析数据...");
                        lock.wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            } else {
                synchronized (lock) {
                    analyzeImage();
                    if (DEBUG) Log.e("ang-->", "分析完成...");
                    lock.notify();
                }
            }
        }
        if (DEBUG) Log.e("angcyo-->", "分析器退出...");
    }

    private void analyzeImage(String oriFile, Bitmap mBitmap){
        synchronized (lock) {
            if (DEBUG) Log.v(TAG, "A-Snapshot: " + oriFile);
            imgFile = get2ndMarkupFileName( oriFile);
            mRgbaSS = new Mat(640, 480, CvType.CV_8UC3);		//ef1  	TODO: is this needed?
            try {
                int watchdog = 6;
                do {
                    Utils.bitmapToMat(mBitmap, mRgbaSS);
    //                mRgbaSS = Imgcodecs.imread(oriFile, Imgcodecs.IMREAD_COLOR); // Simulated image input
                    watchdog--;
                    if (watchdog == 0)
                        break;
                } while (mRgbaSS.empty());
            }catch( Exception e) {

            }
            lock.notify();
        }
    }

    private void analyzeImage() {					//ef1
        // wait for max 3 seconds till the imgFile is available)  ----]]]
        if (!mRgbaSS.empty() && !isAnalysising){
            if (DEBUG) Log.v(TAG, "B: " + "Height: " + mRgbaSS.height() + " Width: " + mRgbaSS.width());
            isAnalysising = true;
            int anaResult = efdSnapShotAnalyzeMM( mRgbaSS.getNativeObjAddr(), 1 ); // 1:write result data on image / 0: dont write data
//              efdSnapShotCalibrate( mRgbaSS.getNativeObjAddr() );  //自动校正放大率(找圆)+分析
            //-1:fail, 1:pass
            if (DEBUG) Log.e("ang-->", "分析结果："+ (anaResult>0?"PASS":"FAIL"));

            // write 2nd markup image file
            // then  you will see the markup image in USBFiberEye filing system, in addition to the EFD filing system
            Imgproc.cvtColor(mRgbaSS, mRgbaSS, Imgproc.COLOR_RGB2BGR, 3);		//ef1
            Imgcodecs.imwrite( imgFile, mRgbaSS );								//ef1
            // ... insertCaptureData(fileNameX);		// then  you will see the markup image in USBFiberEye filing system,
            // 											// in addition to the EFD filing system
            isAnalysising = false;
        } else {
            if (DEBUG) Log.v(TAG, "C: Failed Read Image: " + imgFile);
            if (DEBUG) Log.e("ang-->", "正在分析中.....");
        }

        mRgbaSS.release();

//		Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
//				.setAction("Action", null).show();
    }

    private String get2ndMarkupFileName(String originalImgFile){
        // Prepare 2nd output markup filename
        int index = originalImgFile.lastIndexOf( '.' );
        String rootfN = originalImgFile.substring( 0, index);
        String fileNameX = rootfN + "-A.png";
        if (DEBUG) Log.v(TAG, "B-Analyzed:" + fileNameX);
        return fileNameX;
    }

    // #############################################
    public void efd_SetStorageRoot() {						//ef1
//        String rootDirName = Environment.getExternalStorageDirectory().getPath();
        String rootDirName = FileUtils.getInst().getPhotoSavedPath();
        efdSetStorageRoot(rootDirName);        //$$$  const char*   => long => jlong
    }
    // #############################################
    //Native Declarations for Java				//ef1

    // --- Main Functions ------------------------------
// False = Fail, this must be called once only once in the start of the android program!!!
    public native void efdSetStorageRoot(String AndroidRootDir);        //$$$  const char* =>jstring //+efd

    // *** full analyze demo	//void	ef_analyze( Mat& mRgbSS);	// return false if fail to complete test
// also return the markup image in mRgbSS
    public native void efdHelloTest(long matAddrRgba);

    public native void efdHelloTest0(long matAddrRgba);      // very simple test

    // *** System Init - set default configs, open batch, ...	//void ef_init_sys();	// return false if fail to init
    //public native bool InitSystem( );
    public native void efdInitSystem();

    // *** Save new image, analyze, save data	// return: the record number saved to. return -1 if fail to save
    public native int efdSnapShotCalibrate(long matAddrRgba);

    // *** Save new image, analyze, save data	// return: the record number saved to. return -1 if fail to save
    public native int efdSnapShotAnalyze(long matAddrRgba);

    // *** Save new image, analyze, save data	// return: <0=fail  1=pass, else pass
    // markupMode: 0-default, 1+PassFail, default count, filename,  wire-number
    //1：pass, -1:fail
    public native int efdSnapShotAnalyzeMM(long matAddrRgba, int markupMode);
// ---- Switch Batch ---------------------------------

    // Manually create the next new batch, probably due to file nearly full
    // return false if fail to create/open the next batch
    //public native bool NextNewBatch();
    public native void efdNextNewBatch();

    // Open the previous N old batch file, & updates: Batch_FileName, batch_dir
    // Open the previous N batch, -1= the previous batch. 0=the latest batch
    // return false if fail to open the previous batch
    //public native bool PrevOldNBatch(    int prevN);
    public native void efdPrevOldNBatch(int prevN);

// ---- Review Sample Record --------------------------//// *** efdReviewXXX: 0: first record,  999 last record	// return: the record number found. return -1 if batch is empty

    // *** 0: first record,  999 last record	// return: the record number found. return -1 if batch is empty
    public native int efdReviewMarkupImg(long addrRgb, int SampleN);

    // *** 0: first record,  999 last record	// return: the record number found. return -1 if batch is empty
    public native int efdReviewImg(long addrRgb, int SampleN);

    // *** 0: first record,  999 last record	// return: the record number found. return -1 if batch is empty
    //public native int efdReviewSummary(int SampleN, long ImageFileName,  long Pass, long TotalDefects);        //$$$ * => long => jlong


    public native int efdGetSampleCount();


// --- Select Profiles --------------------------------

    // scopeProfileN: 0~2
    public native int efdSelectScopeProfile(int scopeProfileN);


    // fiberProfileN: 0~3
    public native int efdSelectFiberProfile(int fiberProfileN);

// -----------------------------------------------------

    static {									//ef1
        System.loadLibrary("ffeature");
        System.loadLibrary("opencv_java3");
    }


}
