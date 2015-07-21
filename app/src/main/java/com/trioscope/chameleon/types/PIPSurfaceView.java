package com.trioscope.chameleon.types;

import android.content.Context;
import android.util.AttributeSet;
import android.view.SurfaceView;

/**
 * Created by phand on 7/16/15.
 */
public class PIPSurfaceView extends SurfaceView {
    public PIPSurfaceView(Context context) {
        super(context);
    }

    public PIPSurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        int parentWidth = MeasureSpec.getSize(widthMeasureSpec);
        int parentHeight = MeasureSpec.getSize(heightMeasureSpec);
        this.setMeasuredDimension(parentWidth / 2, parentHeight / 2);
    }
}
