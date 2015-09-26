package com.trioscope.chameleon.views;

import android.content.Context;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.widget.Button;

/**
 * Created by rohitraghunathan on 9/26/15.
 */
public class TextButton extends Button {

    private static final String FONT_LOCATION = "fonts/roboto/RobotoCondensed-Bold.ttf";

    public TextButton(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    public TextButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public TextButton(Context context) {
        super(context);
        init();
    }

    private void init() {
        Typeface tf = Typeface.createFromAsset(getContext().getAssets(), FONT_LOCATION);
        setTypeface(tf);
    }
}
