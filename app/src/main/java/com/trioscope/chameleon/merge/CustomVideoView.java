package com.trioscope.chameleon.merge;

import android.content.Context;
import android.media.MediaPlayer;
import android.widget.VideoView;

/**
 * Created by dhinesh.dharman on 7/18/15.
 */
public class CustomVideoView extends VideoView {
    public CustomVideoView(Context context){
        super(context);
    }


    // The client's listener which is the notification callback.
    private OnSeekCompleteListener mOnSeekCompleteListener;

    // Set up MediaPlayer to forward notifications to client.
    private MediaPlayer.OnSeekCompleteListener mSeekCompleteListener =
            new MediaPlayer.OnSeekCompleteListener() {
                public void onSeekComplete(MediaPlayer mp) {

                }
            };

    // API for client to set their listener.
    public void setOnSeekCompleteListener(OnSeekCompleteListener l)
    {
        mOnSeekCompleteListener = l;
    }
}
