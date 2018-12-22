package ChirdSdk;

/**
 * Created by LC on 2015/7/17.
 */
import android.R;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Bitmap.Config;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

public class StreamView extends SurfaceView implements SurfaceHolder.Callback {

	private int mWidth;
	private int mHeight;
	private Bitmap mBitmap = null;
	private Bitmap newb;
	private boolean surfaceDone = false;
	private SurfaceHolder mSurfaceHolder;
	private BitmapFactory.Options opts = new BitmapFactory.Options();

	private CallBack myCallBack = null;
	
	private boolean isMirrorImage = false;

	public interface CallBack {
		void callbackSurface(Surface surface);
	}

	public StreamView(Context context, CallBack myListener) {
		super(context);

		setFocusable(true);		
		mWidth = getWidth();
		mHeight = getHeight();

		mSurfaceHolder = getHolder();
		this.myCallBack = myListener;
		mSurfaceHolder.addCallback(this);
		mShowMode = StreamView.SHOW_MODE_BEST_FIT;
	}

	private float rate = 1;

	private int mShowMode;
	public final static int SHOW_MODE_STANDARD = 1;
	public final static int SHOW_MODE_BEST_FIT = 4;
	public final static int SHOW_MODE_FULLSCREEN = 8;

	public int getShowMode() {
		return mShowMode;
	}

	public void setShowMode(int mode) {
		mShowMode = mode;
	}
	
	public boolean getMirrorImage(){
		return isMirrorImage;
	}
	
	public void setMirrorImage(boolean mirror){
		isMirrorImage = mirror;
	}

	public void setSurfaceSize(int width, int height) {
		synchronized (mSurfaceHolder) {
			mWidth = width;
			mHeight = height;
		}
	}

	public Surface getSurface(){
		return mSurfaceHolder.getSurface();
	}
	
	public void showBitmap(Bitmap bitmap) {
		Rect destRect;
		Canvas c = null;
		Paint p = new Paint();

		if (bitmap == null) {
			return;
		}

		if (surfaceDone) {
			try {
				c = mSurfaceHolder.lockCanvas();
				synchronized (mSurfaceHolder) {
					try {
						mBitmap = bitmap;
						if (mBitmap == null)
							return;
						synchronized (mBitmap) {

							destRect = destRect(mBitmap.getWidth(),
									mBitmap.getHeight());
							c.drawColor(Color.rgb(0x00, 0x00, 0x00)); 
							
							c.scale(rate, rate, mBitmap.getWidth(),
									mBitmap.getHeight());
							
							if (isMirrorImage){
								Matrix m = new Matrix();
								m.postScale(-1, 1); 
								c.drawBitmap(Bitmap.createBitmap(mBitmap, 0, 0, mBitmap.getWidth(), mBitmap.getHeight(), m, true)
							, null, destRect, p);
							}else{
								c.drawBitmap(mBitmap, null, destRect, p);
							}
							
						}
					} catch (Exception e) {
					}
				}
			} finally {
				if (c != null) {
					mSurfaceHolder.unlockCanvasAndPost(c);
				}
			}
		}

	}

	public void showData(byte[] Data, int Length) {
		Rect destRect;
		Canvas c = null;
		Paint p = new Paint();
		if (surfaceDone) {
			try {
				c = mSurfaceHolder.lockCanvas();
				synchronized (mSurfaceHolder) {
					try {
						mBitmap = null;
						opts.inSampleSize = 1;
						mBitmap = BitmapFactory.decodeByteArray(Data, 0,
								Length, opts);
						destRect = destRect(mBitmap.getWidth(),
								mBitmap.getHeight());

						c.drawColor(Color.BLUE);
						c.drawBitmap(mBitmap, null, destRect, p);
					} catch (Exception e) {
					}
				}
			} finally {
				if (c != null) {
					mSurfaceHolder.unlockCanvasAndPost(c);
				}

			}
		}
	}

	public void clearScreen() {
		Rect destRect;
		Canvas c = null;
		Paint p = new Paint();

		if (surfaceDone) {
			try {
				c = mSurfaceHolder.lockCanvas();
				synchronized (mSurfaceHolder) {
					try {
						destRect = destRect(mWidth, mHeight);
						c.drawColor(Color.rgb(0x00, 0x00, 0x00));
						c.drawBitmap(null, null, destRect, p);
					} catch (Exception e) {
					}
				}
			} finally {
				if (c != null) {
					mSurfaceHolder.unlockCanvasAndPost(c);
				}
			}
		}

	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width,
			int height) {
		setSurfaceSize(width, height);

	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		surfaceDone = true;
		
		myCallBack.callbackSurface(holder.getSurface());
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		surfaceDone = false;
	}

	private Rect destRect(int bmw, int bmh) {
		int tempx;
		int tempy;

		if (mShowMode == StreamView.SHOW_MODE_STANDARD) {
			tempx = (mWidth / 2) - (bmw / 2);
			tempy = (mHeight / 2) - (bmh / 2);
			return new Rect(tempx, tempy, bmw + tempx, bmh + tempy);
		}
		if (mShowMode == StreamView.SHOW_MODE_BEST_FIT) {

			float a = (float) mWidth / (float) bmw;
			float b = (float) mHeight / (float) bmh;
			float bmasp = (a - b) < 0 ? a : b;

			bmw = (int) (bmw * bmasp);
			bmh = (int) (bmh * bmasp);
			tempx = (mWidth / 2) - (bmw / 2);
			tempy = (mHeight / 2) - (bmh / 2);
			return new Rect(tempx, tempy, bmw + tempx, bmh + tempy);
		}
		if (mShowMode == StreamView.SHOW_MODE_FULLSCREEN) {
			return new Rect(0, 0, mWidth, mHeight);
		}

		return null;
	}
	


}