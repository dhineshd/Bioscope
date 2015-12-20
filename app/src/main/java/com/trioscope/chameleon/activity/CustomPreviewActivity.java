package com.trioscope.chameleon.activity;

import android.content.Intent;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.Display;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.RelativeLayout;

import com.google.gson.Gson;
import com.trioscope.chameleon.ChameleonApplication;
import com.trioscope.chameleon.R;
import com.trioscope.chameleon.metrics.MetricNames;
import com.trioscope.chameleon.util.merge.VideoMerger;

import java.io.IOException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CustomPreviewActivity extends AppCompatActivity
        implements TextureView.SurfaceTextureListener {

    private static final Gson gson = new Gson();
    private String majorVideoPath, minorVideoPath;
    private TextureView majorVideoTextureView, minorVideoTextureView;
    private MediaPlayer majorVideoMediaPlayer, minorVideoMediaPlayer;
    private RelativeLayout majorVideoLayout, minorVideoLayout;
    private long latestUserInteractionTimeMillis;
    private long videoStartOffset;
    private int mergeLayoutType;
    private ImageButton minimizeButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_custom_preview);

        minimizeButton = (ImageButton) findViewById(R.id.minimize_custom_preview);
        minimizeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        Intent intent = getIntent();
        majorVideoPath = intent.getStringExtra(VideoLibraryGridActivity.MAJOR_VIDEO_PATH_KEY);
        minorVideoPath = intent.getStringExtra(VideoLibraryGridActivity.MINOR_VIDEO_PATH_KEY);
        videoStartOffset = intent.getLongExtra(VideoLibraryGridActivity.VIDEO_START_OFFSET_KEY, 0);
        mergeLayoutType = intent.getIntExtra(VideoLibraryGridActivity.VIDEO_MERGE_LAYOUT_KEY, VideoMerger.MERGE_LAYOUT_TYPE_SIDE_BY_SIDE);

        majorVideoMediaPlayer = new MediaPlayer();
        minorVideoMediaPlayer = new MediaPlayer();

        majorVideoTextureView = (TextureView) findViewById(R.id.custom_preview_textureview_major_video);
        majorVideoTextureView.setSurfaceTextureListener(this);
        minorVideoTextureView = (TextureView) findViewById(R.id.custom_preview_textureview_minor_video);
        minorVideoTextureView.setSurfaceTextureListener(this);

        majorVideoLayout = (RelativeLayout) findViewById(R.id.custom_preview_relativeLayout_major_video);
        minorVideoLayout = (RelativeLayout) findViewById(R.id.custom_preview_relativeLayout_minor_video);

        createPreviewLayoutMode(majorVideoLayout, minorVideoLayout, mergeLayoutType);
    }

    private void createPreviewLayoutMode(
            final RelativeLayout majorVideoLayout,
            final RelativeLayout minorVideoLayout,
            final int mergeLayoutType
            ) {
        FrameLayout.LayoutParams majorLayoutParams =
                (FrameLayout.LayoutParams) majorVideoLayout.getLayoutParams();
        FrameLayout.LayoutParams minorLayoutParams =
                (FrameLayout.LayoutParams) minorVideoLayout.getLayoutParams();

        Display display = getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);

        if (mergeLayoutType == VideoMerger.MERGE_LAYOUT_TYPE_SIDE_BY_SIDE) {
            majorLayoutParams.width = size.x / 2;
            majorLayoutParams.height = majorLayoutParams.width *
                    ChameleonApplication.DEFAULT_ASPECT_RATIO.getWidth() /
                    ChameleonApplication.DEFAULT_ASPECT_RATIO.getHeight();
            majorLayoutParams.setMargins(0, 200, 0, 0);
            majorLayoutParams.gravity = Gravity.TOP | Gravity.LEFT;
            majorVideoLayout.setLayoutParams(majorLayoutParams);

            minorLayoutParams.width = size.x / 2;
            minorLayoutParams.height = minorLayoutParams.width *
                    ChameleonApplication.DEFAULT_ASPECT_RATIO.getWidth() /
                    ChameleonApplication.DEFAULT_ASPECT_RATIO.getHeight();
            minorLayoutParams.setMargins(0, 200, 0, 0);
            minorLayoutParams.gravity = Gravity.TOP | Gravity.RIGHT;
            minorVideoLayout.setLayoutParams(minorLayoutParams);

        } else {

            int majorTopMargin = 200;
            int majorBottomMargin = 250;
            majorLayoutParams.height = size.y - majorTopMargin - majorBottomMargin;
            majorLayoutParams.width = majorLayoutParams.height *
                    ChameleonApplication.DEFAULT_ASPECT_RATIO.getHeight() /
                    ChameleonApplication.DEFAULT_ASPECT_RATIO.getWidth();
            majorLayoutParams.setMargins(0, majorTopMargin, 0, majorBottomMargin);
            majorLayoutParams.gravity = Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM;
            majorVideoLayout.setLayoutParams(majorLayoutParams);

            int majorLeftMargin = (size.x - majorLayoutParams.width) / 2;
            int minorBottomMargin = majorBottomMargin + 50;
            int minorLeftMargin = majorLeftMargin + 50;
            double majorMinorScalingFactor = 2.5;
            minorLayoutParams.height = (int) (majorLayoutParams.height / majorMinorScalingFactor);
            minorLayoutParams.width = (int) (majorLayoutParams.width / majorMinorScalingFactor);
            minorLayoutParams.setMargins(minorLeftMargin, 0, 0, minorBottomMargin);
            minorLayoutParams.gravity = Gravity.BOTTOM | Gravity.LEFT;
            minorVideoLayout.setLayoutParams(minorLayoutParams);
        }

        updateZOrderOfLayouts(majorVideoLayout, minorVideoLayout);
    }

    private void updateZOrderOfLayouts(
            final View majorVideoLayout,
            final View minorVideoLayout) {

        if (majorVideoLayout.getLayoutParams().height < minorVideoLayout.getLayoutParams().height) {
            majorVideoLayout.bringToFront();
        } else if (majorVideoLayout.getLayoutParams().height > minorVideoLayout.getLayoutParams().height) {
            minorVideoLayout.bringToFront();
        } else {
            // no change in view position in stack since both layouts are of equal height
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_custom_preview, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {

        log.info("Surface texture available w = {}, h = {}", width, height);
        if (majorVideoTextureView.isAvailable() && minorVideoTextureView.isAvailable()) {

            majorVideoMediaPlayer.setSurface(new Surface(majorVideoTextureView.getSurfaceTexture()));
            minorVideoMediaPlayer.setSurface(new Surface(minorVideoTextureView.getSurfaceTexture()));

            playVideos(majorVideoPath, minorVideoPath, videoStartOffset);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        playVideos(majorVideoPath, minorVideoPath,
                videoStartOffset);
        return true;
    }

    private void playVideos(
            final String majorVideoPath,
            final String minorVideoPath,
            final long majorVideoAheadOfMinorVideoByMillis) {

        try {
            majorVideoMediaPlayer.reset();
            minorVideoMediaPlayer.reset();

            this.majorVideoPath = majorVideoPath;
            this.minorVideoPath = minorVideoPath;

            try {
                majorVideoMediaPlayer.setDataSource(majorVideoPath);
                majorVideoMediaPlayer.prepare();
            } catch (IllegalArgumentException | IllegalStateException | IOException e) {
                log.error("Failed to start major media player", e);
            }

            try {
                minorVideoMediaPlayer.setDataSource(minorVideoPath);
                minorVideoMediaPlayer.prepare();
                minorVideoMediaPlayer.setVolume(0f, 0f);
            } catch (IllegalArgumentException | IllegalStateException | IOException e) {
                log.error("Failed to start minor media player", e);
            }

            majorVideoMediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    minorVideoMediaPlayer.stop();
                    playVideos(majorVideoPath, minorVideoPath, majorVideoAheadOfMinorVideoByMillis);
                }
            });

            log.info("Video media players are starting {}", majorVideoAheadOfMinorVideoByMillis);

            // Skip initial part of video
            log.info("majorVideoAheadOfMinorVideoByMillis = {}", majorVideoAheadOfMinorVideoByMillis);

            if (majorVideoAheadOfMinorVideoByMillis < 0) {
                new Handler(Looper.myLooper()).postDelayed(
                        new MediaPlayerStartRunnable(majorVideoMediaPlayer),
                        -majorVideoAheadOfMinorVideoByMillis);
                minorVideoMediaPlayer.start();
            } else {
                new Handler(Looper.myLooper()).postDelayed(
                        new MediaPlayerStartRunnable(minorVideoMediaPlayer),
                        majorVideoAheadOfMinorVideoByMillis);
                majorVideoMediaPlayer.start();
            }

        } catch (Exception e) {
            log.error("Failed to play major and minor videos..", e);
        }

    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {

    }

    private class MediaPlayerStartRunnable implements Runnable {
        private final MediaPlayer mediaPlayer;
        private final long startTime;

        public MediaPlayerStartRunnable(MediaPlayer mp) {
            mediaPlayer = mp;
            startTime = System.currentTimeMillis();
        }

        @Override
        public void run() {
            try {
                long elapsed = System.currentTimeMillis() - startTime;
                log.info("Actual elapsed time between Runnable creation and run() method = {}ms", elapsed);
                mediaPlayer.start();
            } catch (Exception e) {
                log.error("Failed to start media player", e);
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        log.info("onPause invoked!");
        if (isFinishing()) {
            cleanup();
        }
    }

    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        latestUserInteractionTimeMillis = System.currentTimeMillis();
        log.info("User is interacting with the app");
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        log.info("User leave hint triggered");
        if (ChameleonApplication.isUserLeavingOnLeaveHintTriggered(latestUserInteractionTimeMillis)) {
            log.info("User leave hint triggered and interacted with app recently. " +
                    "Assuming that user pressed home button.Finishing activity");
            finish();
        }
    }

    private void cleanup() {
        log.info("Performing cleanup");

        // Release mediaplayers
        if (majorVideoMediaPlayer != null) {
            majorVideoMediaPlayer.setSurface(null);
            majorVideoMediaPlayer.release();
            majorVideoMediaPlayer = null;
        }
        if (minorVideoMediaPlayer != null) {
            minorVideoMediaPlayer.setSurface(null);
            minorVideoMediaPlayer.release();
            minorVideoMediaPlayer = null;
        }
    }
}
