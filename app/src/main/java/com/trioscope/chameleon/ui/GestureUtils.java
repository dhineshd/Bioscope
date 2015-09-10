package com.trioscope.chameleon.ui;

import android.view.MotionEvent;

/**
 * Created by dhinesh.dharman on 9/9/15.
 */
public class GestureUtils {
    private static final int SWIPE_MIN_DISTANCE = 120;
    private static final int SWIPE_THRESHOLD_VELOCITY = 50;

    public static boolean isSwipeUp(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        return ((e1.getY() - e2.getY()) > SWIPE_MIN_DISTANCE)
                && (Math.abs(velocityY) > SWIPE_THRESHOLD_VELOCITY);
    }

    public static boolean isSwipeDown(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        return ((e2.getY() - e1.getY()) > SWIPE_MIN_DISTANCE)
                && (Math.abs(velocityY) > SWIPE_THRESHOLD_VELOCITY);
    }

    public static boolean isSwipeLeft(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        return ((e1.getX() - e2.getX()) > SWIPE_MIN_DISTANCE)
                && (Math.abs(velocityX) > SWIPE_THRESHOLD_VELOCITY);
    }

    public static boolean isSwipeRight(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        return ((e2.getX() - e1.getX()) > SWIPE_MIN_DISTANCE)
                && (Math.abs(velocityX) > SWIPE_THRESHOLD_VELOCITY);
    }

}
