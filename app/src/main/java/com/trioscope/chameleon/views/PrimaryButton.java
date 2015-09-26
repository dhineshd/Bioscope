package com.trioscope.chameleon.views;

import android.content.Context;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.widget.Button;

/**
 * Created by rohitraghunathan on 9/26/15.
 */
public class PrimaryButton extends Button {

    private static final String FONT_LOCATION = "fonts/roboto-slab/RobotoSlab-Regular.ttf";

    public PrimaryButton(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    public PrimaryButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public PrimaryButton(Context context) {
        super(context);
        init();
    }

    private void init() {
        Typeface tf = Typeface.createFromAsset(getContext().getAssets(),
                FONT_LOCATION);
        setTypeface(tf);
    }
}
