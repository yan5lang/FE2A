package com.szetn.rencoder;

import android.graphics.Bitmap;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.util.Log;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.Vector;

/**
 * Created by robi on 2016-04-01 10:50.
 */
public class VideoRunnable extends Thread {
    public static boolean DEBUG = false;

    private static final String TAG = "VideoRunnable";
    private static final boolean VERBOSE = false; // lots of logging
    // parameters for the encoder
    private static final String MIME_TYPE = "video/avc"; // H.264 Advanced Video
    private static final int FRAME_RATE = 10;//25; // 15fps
    private static final int IFRAME_INTERVAL = 1; // 10 between
    // I-frames
    private static final int TIMEOUT_USEC = 2000;
    private static final int COMPRESS_RATIO = 256;
    private static final int BIT_RATE = MediaMuxerRunnable.IMAGE_HEIGHT * MediaMuxerRunnable.IMAGE_WIDTH * 3 * 8 * FRAME_RATE / COMPRESS_RATIO; // bit rate CameraWrapper.
    private final Object lock = new Object();
    byte[] mFrameData;
    Vector<byte[]> frameBytes;
    private int mWidth;
    private int mHeight;
    private MediaCodec mMediaCodec;
    private MediaCodec.BufferInfo mBufferInfo;
    private int mColorFormat;
    private long mStartTime = 0;
    private volatile boolean isExit = false;
    private WeakReference<MediaMuxerRunnable> mediaMuxerRunnable;
    private MediaFormat mediaFormat;
    private MediaCodecInfo codecInfo;
    private volatile boolean isStart = false;
    private volatile boolean isMuxerReady = false;

    public VideoRunnable(int mWidth, int mHeight, WeakReference<MediaMuxerRunnable> mediaMuxerRunnable) {
        this.mWidth = mWidth;
        this.mHeight = mHeight;
        this.mediaMuxerRunnable = mediaMuxerRunnable;
        frameBytes = new Vector<byte[]>();
        prepare();
    }

    private static int selectColorFormat(MediaCodecInfo codecInfo,
                                         String mimeType) {
        MediaCodecInfo.CodecCapabilities capabilities = codecInfo
                .getCapabilitiesForType(mimeType);
        for (int i = 0; i < capabilities.colorFormats.length; i++) {
            int colorFormat = capabilities.colorFormats[i];
            if (isRecognizedFormat(colorFormat)) {
                return colorFormat;
            }
        }
        if(DEBUG) Log.e(TAG,
                "couldn't find a good color format for " + codecInfo.getName()
                        + " / " + mimeType);
        return 0; // not reached
    }

    private static boolean isRecognizedFormat(int colorFormat) {
        switch (colorFormat) {
            // these are the formats we know how to handle for this test
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_TI_FormatYUV420PackedSemiPlanar:
                return true;
            default:
                return false;
        }
    }

    private static MediaCodecInfo selectCodec(String mimeType) {
        int numCodecs = MediaCodecList.getCodecCount();
        for (int i = 0; i < numCodecs; i++) {
            MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);
            if (!codecInfo.isEncoder()) {
                continue;
            }
            String[] types = codecInfo.getSupportedTypes();
            for (int j = 0; j < types.length; j++) {
                if (types[j].equalsIgnoreCase(mimeType)) {
                    return codecInfo;
                }
            }
        }
        return null;
    }



    private static void NV21toI420SemiPlanar(byte[] nv21bytes, byte[] i420bytes,
                                             int width, int height) {
        //byte[] to int[]
//        int [] argb = new int[width * height];
//        argb = Bytes2Ints(nv21bytes);
        //ARGB(int[]) to YUV(btte[])
//        byte [] yuv = new byte[width*height*3/2];
//        encodeYUV420SP(yuv,argb,width,height);
        //YUV(nv21byte[]) to H264(i420bytes[])
        System.arraycopy(nv21bytes, 0, i420bytes, 0, width * height);
        for (int i = width * height; i < nv21bytes.length; i += 2) {
            i420bytes[i] = nv21bytes[i + 1];
            i420bytes[i + 1] = nv21bytes[i];
        }
    }
/*
    public static int[] Bytes2Ints(byte[] buf) {
        byte bLength = 4;
        int[] s = new int[buf.length / bLength];

        for (int iLoop = 0; iLoop < s.length; iLoop++) {
            byte[] temp = new byte[bLength];

            for (int jLoop = 0; jLoop < bLength; jLoop++) {
                temp[jLoop] = buf[iLoop * bLength + jLoop];
            }

            s[iLoop] = byteArray2Int(temp);

            System.out.println("2out->"+s[iLoop]);
        }

        return s;
    }

    *//**
     * 把byte数组转换成int类型
     * @return 返回的int值
     *//*
    public static int byteArray2Int(byte[] b) {
        int a = (((int) b[0]) << 24) + (((int) b[1]) << 16) + (((int) b[2]) << 8) + b[3];
        if (a < 0) {
            a = a + 256;
        }
        return a;
    }

    static void encodeYUV420SP(byte[] yuv420sp, int[] argb, int width, int height) {
        final int frameSize = width * height;

        int yIndex = 0;
        int uvIndex = frameSize;

        int a, R, G, B, Y, U, V;
        int index = 0;
        for (int j = 0; j < height; j++) {
            for (int i = 0; i < width; i++) {

                a = (argb[index] & 0xff000000) >> 24; // a is not used obviously
                R = (argb[index] & 0xff0000) >> 16;
                G = (argb[index] & 0xff00) >> 8;
                B = (argb[index] & 0xff) >> 0;

                // well known RGB to YUV algorithm
                Y = ( (  66 * R + 129 * G +  25 * B + 128) >> 8) +  16;
                U = ( ( -38 * R -  74 * G + 112 * B + 128) >> 8) + 128;
                V = ( ( 112 * R -  94 * G -  18 * B + 128) >> 8) + 128;

                // NV21 has a plane of Y and interleaved planes of VU each sampled by a factor of 2
                //    meaning for every 4 Y pixels there are 1 V and 1 U.  Note the sampling is every other
                //    pixel AND every other scanline.
                yuv420sp[yIndex++] = (byte) ((Y < 0) ? 0 : ((Y > 255) ? 255 : Y));
                if (j % 2 == 0 && index % 2 == 0) {
                    yuv420sp[uvIndex++] = (byte)((V<0) ? 0 : ((V > 255) ? 255 : V));
                    yuv420sp[uvIndex++] = (byte)((U<0) ? 0 : ((U > 255) ? 255 : U));
                    System.out.println("2out uvIndex->"+uvIndex);
                }
                System.out.println("2out yIndex->"+yIndex);
                index ++;
            }
        }

    }*/

    public void exit() {
        isExit = true;
    }

    public void add(byte[] data) {
        if (frameBytes != null && isMuxerReady) {
            frameBytes.add(data);
        }
    }
    public void add(Bitmap data) {
        //convert bitmap to yuv here
        if (frameBytes != null && isMuxerReady) {
            frameBytes.add(getYUV420sp(640,480,data));
        }
    }

    public static byte[] getYUV420sp(int inputWidth, int inputHeight,
                                     Bitmap scaled) {

        int[] argb = new int[inputWidth * inputHeight];

        scaled.getPixels(argb, 0, inputWidth, 0, 0, inputWidth, inputHeight);

        byte[] yuv = new byte[inputWidth * inputHeight * 3 / 2];
        encodeYUV420SP(yuv, argb, inputWidth, inputHeight);

//        scaled.recycle();

        return yuv;
    }

    /**
     * RGB转YUV420sp
     *
     * @param yuv420sp
     *            inputWidth * inputHeight * 3 / 2
     * @param argb
     *            inputWidth * inputHeight
     * @param width
     * @param height
     */
    private static void encodeYUV420SP(byte[] yuv420sp, int[] argb, int width,
                                       int height) {
        // 帧图片的像素大小
        final int frameSize = width * height;
        // ---YUV数据---
        int Y, U, V;
        // Y的index从0开始
        int yIndex = 0;
        // UV的index从frameSize开始
        int uvIndex = frameSize;

        // ---颜色数据---
        int a, R, G, B;
        //
        int argbIndex = 0;
        //

        // ---循环所有像素点，RGB转YUV---
        for (int j = 0; j < height; j++) {
            for (int i = 0; i < width; i++) {

                // a is not used obviously
                a = (argb[argbIndex] & 0xff000000) >> 24;
                R = (argb[argbIndex] & 0xff0000) >> 16;
                G = (argb[argbIndex] & 0xff00) >> 8;
                B = (argb[argbIndex] & 0xff);
                //
                argbIndex++;

                // well known RGB to YUV algorithm
                Y = ((66 * R + 129 * G + 25 * B + 128) >> 8) + 16;
                U = ((-38 * R - 74 * G + 112 * B + 128) >> 8) + 128;
                V = ((112 * R - 94 * G - 18 * B + 128) >> 8) + 128;

                //
                Y = Math.max(0, Math.min(Y, 255));
                U = Math.max(0, Math.min(U, 255));
                V = Math.max(0, Math.min(V, 255));

                // NV21 has a plane of Y and interleaved planes of VU each
                // sampled by a factor of 2
                // meaning for every 4 Y pixels there are 1 V and 1 U. Note the
                // sampling is every other
                // pixel AND every other scanline.
                // ---Y---
                yuv420sp[yIndex++] = (byte) Y;
                // ---UV---
                if ((j % 2 == 0) && (i % 2 == 0)) {
                    //
                    yuv420sp[uvIndex++] = (byte) V;
                    //
                    yuv420sp[uvIndex++] = (byte) U;
                }
            }
        }
    }



    private void prepare() {
        if(DEBUG) Log.i(TAG, "VideoEncoder()");
        mFrameData = new byte[this.mWidth * this.mHeight * 3 / 2];
        mBufferInfo = new MediaCodec.BufferInfo();
        codecInfo = selectCodec(MIME_TYPE);
        if (codecInfo == null) {
            if(DEBUG) Log.e(TAG, "Unable to find an appropriate codec for " + MIME_TYPE);
            return;
        }
        if (VERBOSE)
            if(DEBUG) Log.d(TAG, "found codec: " + codecInfo.getName());
        mColorFormat = selectColorFormat(codecInfo, MIME_TYPE);
        if (VERBOSE)
            if(DEBUG) Log.d(TAG, "found colorFormat: " + mColorFormat);
        mediaFormat = MediaFormat.createVideoFormat(MIME_TYPE,
                this.mWidth, this.mHeight);
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE);
        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
        mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, mColorFormat);
        mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL);
        if (VERBOSE)
            if(DEBUG) Log.d(TAG, "format: " + mediaFormat);
    }

    private void startMediaCodec() throws IOException {
        mMediaCodec = MediaCodec.createByCodecName(codecInfo.getName());
        mMediaCodec.configure(mediaFormat, null, null,
                MediaCodec.CONFIGURE_FLAG_ENCODE);
        mMediaCodec.start();

        isStart = true;
    }

    private void stopMediaCodec() {
        if (mMediaCodec != null) {
            mMediaCodec.stop();
            mMediaCodec.release();
            mMediaCodec = null;
        }
        isStart = false;
        if(DEBUG) Log.e("angcyo-->", "stop video 录制...");
    }

    public synchronized void restart() {
        isStart = false;
        isMuxerReady = false;
        frameBytes.clear();
    }

    public void setMuxerReady(boolean muxerReady) {
        synchronized (lock) {
            if(DEBUG) Log.e("angcyo-->", Thread.currentThread().getId() + " video -- setMuxerReady..." + muxerReady);
            isMuxerReady = muxerReady;
            lock.notifyAll();
        }
    }

    private void encodeFrame(byte[] input/* , byte[] output */) {
        if (VERBOSE)
            if(DEBUG) Log.i(TAG, "encodeFrame()");
        NV21toI420SemiPlanar(input, mFrameData, this.mWidth, this.mHeight);

        ByteBuffer[] inputBuffers = mMediaCodec.getInputBuffers();
        ByteBuffer[] outputBuffers = mMediaCodec.getOutputBuffers();
        int inputBufferIndex = mMediaCodec.dequeueInputBuffer(TIMEOUT_USEC);
        if (VERBOSE)
            if(DEBUG) Log.i(TAG, "inputBufferIndex-->" + inputBufferIndex);
        if (inputBufferIndex >= 0) {
            long endTime = System.nanoTime();
            long ptsUsec = (endTime - mStartTime) / 1000;
            if (VERBOSE)
                if(DEBUG) Log.i(TAG, "resentationTime: " + ptsUsec);
            ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
            inputBuffer.clear();
            inputBuffer.put(mFrameData);
            mMediaCodec.queueInputBuffer(inputBufferIndex, 0,
                    mFrameData.length, System.nanoTime() / 1000, 0);
        } else {
            // either all in use, or we timed out during initial setup
            if (VERBOSE)
                if(DEBUG) Log.d(TAG, "input buffer not available");
        }

        int outputBufferIndex = mMediaCodec.dequeueOutputBuffer(mBufferInfo, TIMEOUT_USEC);
        if (VERBOSE)
            if(DEBUG) Log.i(TAG, "outputBufferIndex-->" + outputBufferIndex);
        do {
            if (outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
            } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                outputBuffers = mMediaCodec.getOutputBuffers();
            } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                MediaFormat newFormat = mMediaCodec.getOutputFormat();
                MediaMuxerRunnable mediaMuxerRunnable = this.mediaMuxerRunnable.get();
                if (mediaMuxerRunnable != null) {
                    mediaMuxerRunnable.addTrackIndex(MediaMuxerRunnable.TRACK_VIDEO, newFormat);
                }

                if(DEBUG) Log.e("angcyo-->", "添加视轨 INFO_OUTPUT_FORMAT_CHANGED " + newFormat.toString());
            } else if (outputBufferIndex < 0) {
            } else {
                if (VERBOSE)
                    if(DEBUG) Log.d(TAG, "perform encoding");
                ByteBuffer outputBuffer = outputBuffers[outputBufferIndex];
                if (outputBuffer == null) {
                    throw new RuntimeException("encoderOutputBuffer " + outputBufferIndex +
                            " was null");
                }
                if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    // The codec config data was pulled out and fed to the muxer when we got
                    // the INFO_OUTPUT_FORMAT_CHANGED status.  Ignore it.
                    if (VERBOSE) if(DEBUG) Log.d(TAG, "ignoring BUFFER_FLAG_CODEC_CONFIG");
                    mBufferInfo.size = 0;
                }
                if (mBufferInfo.size != 0) {
                    MediaMuxerRunnable mediaMuxerRunnable = this.mediaMuxerRunnable.get();

                    if (mediaMuxerRunnable != null && !mediaMuxerRunnable.isVideoAdd()) {
                        MediaFormat newFormat = mMediaCodec.getOutputFormat();
                        if(DEBUG) Log.e("angcyo-->", "添加视轨  " + newFormat.toString());
                        mediaMuxerRunnable.addTrackIndex(MediaMuxerRunnable.TRACK_VIDEO, newFormat);
                    }
                    // adjust the ByteBuffer values to match BufferInfo (not needed?)
                    outputBuffer.position(mBufferInfo.offset);
                    outputBuffer.limit(mBufferInfo.offset + mBufferInfo.size);

                    if (mediaMuxerRunnable != null && mediaMuxerRunnable.isMuxerStart()) {
                        mediaMuxerRunnable.addMuxerData(new MediaMuxerRunnable.MuxerData(
                                MediaMuxerRunnable.TRACK_VIDEO, outputBuffer, mBufferInfo
                        ));
                    }
                    if (VERBOSE) {
                        if(DEBUG) Log.d(TAG, "sent " + mBufferInfo.size + " frameBytes to muxer");
                    }
                }
                mMediaCodec.releaseOutputBuffer(outputBufferIndex, false);
            }
            outputBufferIndex = mMediaCodec.dequeueOutputBuffer(mBufferInfo, TIMEOUT_USEC);
        } while (outputBufferIndex >= 0);
    }

    @Override
    public void run() {
        while (!isExit) {
            if (!isStart) {
                stopMediaCodec();

                if (!isMuxerReady) {
                    synchronized (lock) {
                        try {
                            if(DEBUG) Log.e("ang-->", "video -- 等待混合器准备...");
                            lock.wait();
                        } catch (InterruptedException e) {
                        }
                    }
                }

                if (isMuxerReady) {
                    try {
                        if(DEBUG) Log.e("angcyo-->", "video -- startMediaCodec...");
                        startMediaCodec();
                    } catch (IOException e) {
                        isStart = false;
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e1) {
                        }
                    }
                }

            } else if (!frameBytes.isEmpty()) {
                byte[] bytes = this.frameBytes.remove(0);
//                if(DEBUG) Log.e("ang-->", "解码视频数据:" + bytes.length);
                try {
                    encodeFrame(bytes);
                } catch (Exception e) {
                    if(DEBUG) Log.e("angcyo-->", "解码视频(Video)数据 失败");
                    e.printStackTrace();
                }
            }
        }

        if(DEBUG) Log.e("angcyo-->", "Video 录制线程 退出...");
    }
}
