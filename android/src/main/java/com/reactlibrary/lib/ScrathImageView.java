package com.reactlibrary.lib;

import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff.Mode;
import android.graphics.PorterDuffXfermode;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

/**
 * This view start with full gray color bitmap and onTouch to make it
 * transparent
 *
 * @author winsontan520
 */
public class ScrathImageView extends SurfaceView implements IScratchImageView, SurfaceHolder.Callback {
    private static final String TAG = "WScratchView";

    // default value constants
    private final int DEFAULT_COLOR = 0x00000000; // default color is transparent
    private final int DEFAULT_REVEAL_SIZE = 30;

    public static final int DEFAULT_SCRATCH_TEST_SPEED = 4;

    private Context mContext;
    private WScratchViewThread mThread;
    List<Path> mPathList = new ArrayList<Path>();
    private int mOverlayColor;
    private Paint mOverlayPaint;
    private int mRevealSize;
    private boolean mIsScratchable = true;
    private boolean mIsAntiAlias = false;
    private Path path;
    private float startX = 0;
    private float startY = 0;
    private boolean mScratchStart = false;
    private Bitmap mScratchBitmap;
    private Drawable mScratchDrawable = null;
    private Paint mBitmapPaint;
    private Matrix mMatrix;
    private Bitmap mScratchedTestBitmap;
    private Canvas mScratchedTestCanvas;
    private OnScratchCallback mOnScratchCallback;

    //Enable scratch all area if mClearCanvas is true
    private boolean mClearCanvas = false;
    //Enable click on WScratchView if mIsClickable is true
    private boolean mIsClickable = false;

    public ScrathImageView(Context ctx, AttributeSet attrs) {
        super(ctx, attrs);
        init(ctx, attrs);
    }

    public ScrathImageView(Context context) {
        super(context);
        init(context, null);
    }

    private void init(Context context, AttributeSet attrs) {
        mContext = context;

        // default value
        mOverlayColor = DEFAULT_COLOR;
        mRevealSize = DEFAULT_REVEAL_SIZE;

        setZOrderOnTop(true);
        SurfaceHolder holder = getHolder();
        holder.addCallback(this);
        holder.setFormat(PixelFormat.TRANSPARENT);

        mOverlayPaint = new Paint();
        mOverlayPaint.setXfermode(new PorterDuffXfermode(Mode.CLEAR));
        mOverlayPaint.setStyle(Paint.Style.STROKE);
        mOverlayPaint.setStrokeCap(Paint.Cap.ROUND);
        mOverlayPaint.setStrokeJoin(Paint.Join.ROUND);

        // convert drawable to bitmap if drawable already set in xml
        if (mScratchDrawable != null) {
            mScratchBitmap = ((BitmapDrawable) mScratchDrawable).getBitmap();
        }

        mBitmapPaint = new Paint();
        mBitmapPaint.setAntiAlias(true);
        mBitmapPaint.setFilterBitmap(true);
        mBitmapPaint.setDither(true);
    }

    @Override
    public void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        //Clear all area if mClearCanvas is true
        if(mClearCanvas){
            canvas.drawColor(Color.TRANSPARENT, Mode.CLEAR);
            return;
        }

        if (mScratchBitmap != null) {
            if (mMatrix == null) {
                float scaleWidth = (float) canvas.getWidth() / mScratchBitmap.getWidth();
                float scaleHeight = (float) canvas.getHeight() / mScratchBitmap.getHeight();
                mMatrix = new Matrix();
                mMatrix.postScale(scaleWidth, scaleHeight);
            }
            canvas.drawBitmap(mScratchBitmap, mMatrix, mBitmapPaint);
        } else {
            canvas.drawColor(mOverlayColor);
        }

        for (Path path : mPathList) {
            mOverlayPaint.setAntiAlias(mIsAntiAlias);
            mOverlayPaint.setStrokeWidth(mRevealSize);

            canvas.drawPath(path, mOverlayPaint);
        }
    }

    private void updateScratchedPercentage() {
        if(mOnScratchCallback == null) return;
        mOnScratchCallback.onScratch(getScratchedRatio());
    }

    @Override
    public boolean onTouchEvent(MotionEvent me) {
        synchronized (mThread.getSurfaceHolder()) {
            if (!mIsScratchable) {
                return true;
            }

            switch (me.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    path = new Path();
                    path.moveTo(me.getX(), me.getY());
                    startX = me.getX();
                    startY = me.getY();
                    mPathList.add(path);
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (mScratchStart) {
                        path.lineTo(me.getX(), me.getY());
                    } else {
                        if (isScratch(startX, me.getX(), startY, me.getY())) {
                            mScratchStart = true;
                            path.lineTo(me.getX(), me.getY());
                        }
                    }
                    updateScratchedPercentage();
                    break;
                case MotionEvent.ACTION_UP:
                    //Set call back if user's finger detach
                    if(mOnScratchCallback != null){
                        mOnScratchCallback.onDetach(true);
                    }
                    //perform Click action if the motion is not move
                    //and the WScratchView is clickable
                    if(!mScratchStart && mIsClickable){
                        post(new Runnable() {
                            @Override
                            public void run() {
                                performClick();
                            }
                        });
                    }
                    mScratchStart = false;
                    break;
            }
            return true;
        }
    }

    private boolean isScratch(float oldX, float x, float oldY, float y) {
        float distance = (float) Math.sqrt(Math.pow(oldX - x, 2) + Math.pow(oldY - y, 2));
        if (distance > mRevealSize * 2) {
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder arg0, int arg1, int arg2, int arg3) {
        // do nothing
    }

    @Override
    public void surfaceCreated(SurfaceHolder arg0) {
        mThread = new WScratchViewThread(getHolder(), this);
        mThread.setRunning(true);
        mThread.start();

        mScratchedTestBitmap = Bitmap.createBitmap(arg0.getSurfaceFrame().width(), arg0.getSurfaceFrame().height(), Bitmap.Config.ARGB_8888);
        mScratchedTestCanvas = new Canvas(mScratchedTestBitmap);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder arg0) {
        boolean retry = true;
        mThread.setRunning(false);
        while (retry) {
            try {
                mThread.join();
                retry = false;
            } catch (InterruptedException e) {
                // do nothing but keep retry
            }
        }

    }

    class WScratchViewThread extends Thread {
        private SurfaceHolder mSurfaceHolder;
        private ScrathImageView mView;
        private boolean mRun = false;

        public WScratchViewThread(SurfaceHolder surfaceHolder, ScrathImageView view) {
            mSurfaceHolder = surfaceHolder;
            mView = view;
        }

        public void setRunning(boolean run) {
            mRun = run;
        }

        public SurfaceHolder getSurfaceHolder() {
            return mSurfaceHolder;
        }

        @Override
        public void run() {
            Canvas c;
            while (mRun) {
                c = null;
                try {
                    c = mSurfaceHolder.lockCanvas(null);
                    synchronized (mSurfaceHolder) {
                        if (c != null) {
                            mView.draw(c);
                        }
                    }
                } finally {
                    if (c != null) {
                        mSurfaceHolder.unlockCanvasAndPost(c);
                    }
                }
            }
        }
    }

    @Override
    public void resetView() {
        synchronized (mThread.getSurfaceHolder()) {
            mPathList.clear();
        }
    }

    @Override
    public boolean isScratchable() {
        return mIsScratchable;
    }

    @Override
    public void setScratchable(boolean flag) {
        mIsScratchable = flag;
    }

    @Override
    public void setOverlayColor(int ResId) {
        mOverlayColor = ResId;
    }

    @Override
    public void setRevealSize(int size) {
        mRevealSize = size;
    }

    @Override
    public void setAntiAlias(boolean flag) {
        mIsAntiAlias = flag;
    }

    @Override
    public void setScratchDrawable(Drawable d) {
        mScratchDrawable = d;
        if (mScratchDrawable != null) {
            mScratchBitmap = ((BitmapDrawable) mScratchDrawable).getBitmap();
        }
    }

    @Override
    public void setScratchBitmap(Bitmap b) {
        mScratchBitmap = b;
    }

    @Override
    public float getScratchedRatio() {
        return getScratchedRatio(DEFAULT_SCRATCH_TEST_SPEED);
    }

    /**
     * thanks to https://github.com/daveyfong for providing this method
     */
    @Override
    public float getScratchedRatio(int speed) {
        if (null == mScratchedTestBitmap) {
            return 0;
        }
        draw(mScratchedTestCanvas);

        final int width = mScratchedTestBitmap.getWidth();
        final int height = mScratchedTestBitmap.getHeight();

        int count = 0;
        for (int i = 0; i < width; i += speed) {
            for (int j = 0; j < height; j += speed) {
                if (0 == Color.alpha(mScratchedTestBitmap.getPixel(i, j))) {
                    count++;
                }
            }
        }
        float completed = (float) count / ((width / speed) * (height / speed)) * 100;

        return completed;
    }

    @Override
    public void setOnScratchCallback(OnScratchCallback callback) {
        mOnScratchCallback = callback;
    }

    public static abstract class OnScratchCallback{
        public abstract void onScratch(float percentage);
        //Call back funtion to monitor the status of finger
        public abstract void onDetach(boolean fingerDetach);
    }

    //Set the mClearCanvas
    @Override
    public void setScratchAll(boolean scratchAll){
        mClearCanvas = scratchAll;
    }

    //Set the WScartchView clickable
    @Override
    public void setBackgroundClickable(boolean clickable){
        mIsClickable = clickable;
    }
}