package com.bumptech.glide.load.resource.gif;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.os.Build;

import com.bumptech.glide.gifdecoder.GifDecoder;
import com.bumptech.glide.gifdecoder.GifHeader;
import com.bumptech.glide.load.Transformation;

/**
 * An animated {@link android.graphics.drawable.Drawable} that plays the frames of an animated GIF.
 */
public class GifDrawable extends Drawable implements Animatable, GifFrameManager.FrameCallback {

    private final Paint paint = new Paint();
    private final GifFrameManager frameManager;
    private final GifState state;
    private final GifDecoder decoder;

    /** The current frame to draw, or null if no frame has been loaded yet */
    private Bitmap currentFrame;
    /** True if the drawable is currently animating */
    private boolean isRunning;
    /** True if the drawable should animate while visible */
    private boolean isStarted;
    /** True if the drawable's resources have been recycled */
    private boolean isRecycled;
    /** True if the drawable is currently visible. */
    private boolean isVisible;

    /**
     * Constructor for GifDrawable.
     *
     * @see #setFrameTransformation(com.bumptech.glide.load.Transformation, int, int)
     *
     * @param context A context.
     * @param bitmapProvider An {@link com.bumptech.glide.gifdecoder.GifDecoder.BitmapProvider} that can be used to
     *                       retrieve re-usable {@link android.graphics.Bitmap}s.
     * @param frameTransformation An {@link com.bumptech.glide.load.Transformation} that can be applied to each frame.
     * @param targetFrameWidth The desired width of the frames displayed by this drawable (the width of the view or
     *                         {@link com.bumptech.glide.request.target.Target} this drawable is being loaded into).
     * @param targetFrameHeight The desired height of the frames displayed by this drawable (the height of the view or
     *                          {@link com.bumptech.glide.request.target.Target} this drawable is being loaded into).
     * @param id An id that uniquely identifies this particular gif.
     * @param gifHeader The header data for this gif.
     * @param data The full bytes of the gif.
     * @param finalFrameWidth The final width of the frames displayed by this drawable after they have been transformed.
     * @param finalFrameHeight The final height of the frames displayed by this drwaable after they have been
     *                         transformed.
     */
    public GifDrawable(Context context, GifDecoder.BitmapProvider bitmapProvider,
            Transformation<Bitmap> frameTransformation, int targetFrameWidth, int targetFrameHeight, String id,
            GifHeader gifHeader, byte[] data, int finalFrameWidth, int finalFrameHeight) {
        this(new GifState(id, gifHeader, data, context, frameTransformation, targetFrameWidth, targetFrameHeight,
                bitmapProvider, finalFrameWidth, finalFrameHeight));
    }

    private GifDrawable(GifState state) {
        this.state = state;
        this.decoder = new GifDecoder(state.bitmapProvider);
        decoder.setData(state.id, state.gifHeader, state.data);
        frameManager = new GifFrameManager(state.context, decoder, state.frameTransformation, state.targetWidth,
                state.targetHeight, state.finalFrameWidth, state.finalFrameHeight);
    }

    // For testing.
    GifDrawable(GifDecoder decoder, GifFrameManager frameManager, int finalFrameWidth, int finalFrameHeight) {
        this.decoder = decoder;
        this.frameManager = frameManager;
        this.state = new GifState(null);
        state.finalFrameWidth = finalFrameWidth;
        state.finalFrameHeight = finalFrameHeight;
    }

    public void setFrameTransformation(Transformation<Bitmap> frameTransformation, int finalFrameWidth,
            int finalFrameHeight) {
        state.frameTransformation = frameTransformation;
        state.finalFrameWidth = finalFrameWidth;
        state.finalFrameHeight = finalFrameHeight;
    }

    public Transformation<Bitmap> getFrameTransformation() {
        return state.frameTransformation;
    }

    public byte[] getData() {
        return state.data;
    }

    @Override
    public void start() {
        isStarted = true;
        if (isVisible) {
            startRunning();
        }
    }

    @Override
    public void stop() {
        isStarted = false;
        stopRunning();
    }

    private void startRunning() {
        if (!isRunning) {
            isRunning = true;
            frameManager.getNextFrame(this);
            invalidateSelf();
        }
    }

    private void stopRunning() {
        isRunning = false;
    }

    @Override
    public boolean setVisible(boolean visible, boolean restart) {
        isVisible = visible;
        if (!visible) {
            stopRunning();
        } else if (isStarted) {
            startRunning();
        }
        return super.setVisible(visible, restart);
    }

    @Override
    public int getIntrinsicWidth() {
        return state.finalFrameWidth;
    }

    @Override
    public int getIntrinsicHeight() {
        return state.finalFrameHeight;
    }

    @Override
    public boolean isRunning() {
        return isRunning;
    }

    // For testing.
    void setIsRunning(boolean isRunning) {
        this.isRunning = isRunning;
    }

    @Override
    public void draw(Canvas canvas) {
        if (currentFrame != null) {
            canvas.drawBitmap(currentFrame, 0, 0, paint);
        }
    }

    @Override
    public void setAlpha(int i) {
        paint.setAlpha(i);
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        paint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return decoder.isTransparent() ? PixelFormat.TRANSPARENT : PixelFormat.OPAQUE;
    }

    @TargetApi(11)
    @Override
    public void onFrameRead(Bitmap frame) {
        if (Build.VERSION.SDK_INT >= 11 && getCallback() == null) {
            stop();
            return;
        } if (!isRunning) {
            return;
        }

        if (frame != null) {
            currentFrame = frame;
            invalidateSelf();
        }

        frameManager.getNextFrame(this);
    }

    @Override
    public ConstantState getConstantState() {
        return state;
    }

    /**
     * Clears any resources for loading frames that are currently held on to by this object.
     */
    public void recycle() {
        isRecycled = true;
        frameManager.clear();
    }

    // For testing.
    boolean isRecycled() {
        return isRecycled;
    }

    static class GifState extends ConstantState {
        String id;
        GifHeader gifHeader;
        byte[] data;
        int finalFrameWidth;
        int finalFrameHeight;
        Context context;
        Transformation<Bitmap> frameTransformation;
        int targetWidth;
        int targetHeight;
        GifDecoder.BitmapProvider bitmapProvider;

        public GifState(String id, GifHeader header, byte[] data, Context context,
                Transformation<Bitmap> frameTransformation, int targetWidth, int targetHeight,
                GifDecoder.BitmapProvider provider, int finalFrameWidth, int finalFrameHeight) {
            this.id = id;
            gifHeader = header;
            this.data = data;
            this.finalFrameWidth = finalFrameWidth;
            this.finalFrameHeight = finalFrameHeight;
            this.context = context.getApplicationContext();
            this.frameTransformation = frameTransformation;
            this.targetWidth = targetWidth;
            this.targetHeight = targetHeight;
            bitmapProvider = provider;
        }

        public GifState(GifState original) {
            if (original != null) {
                id = original.id;
                gifHeader = original.gifHeader;
                data = original.data;
                context = original.context;
                frameTransformation = original.frameTransformation;
                targetWidth = original.targetWidth;
                targetHeight = original.targetHeight;
                bitmapProvider = original.bitmapProvider;
                finalFrameWidth = original.finalFrameWidth;
                finalFrameHeight = original.finalFrameHeight;
            }
        }

        @Override
        public Drawable newDrawable(Resources res) {
            return newDrawable();
        }

        @Override
        public Drawable newDrawable() {
            return new GifDrawable(this);
        }

        @Override
        public int getChangingConfigurations() {
            return 0;
        }
    }
}
