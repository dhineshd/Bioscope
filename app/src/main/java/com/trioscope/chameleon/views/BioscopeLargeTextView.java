package com.trioscope.chameleon.views;

import android.content.Context;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.widget.TextView;

/**
 * Created by rohitraghunathan on 10/11/15.
 */
public class BioscopeLargeTextView extends TextView {

    private static final String FONT_LOCATION = "fonts/roboto-slab/RobotoSlab-Regular.ttf";

    public BioscopeLargeTextView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    public BioscopeLargeTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public BioscopeLargeTextView(Context context) {
        super(context);
        init();
    }

    private void init() {
        Typeface tf = Typeface.createFromAsset(getContext().getAssets(),
                FONT_LOCATION);
        setTypeface(tf);
    }

}
