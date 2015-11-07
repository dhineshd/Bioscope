package com.trioscope.chameleon.activity;

import android.content.Intent;
import android.graphics.Point;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Display;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.google.gson.Gson;
import com.trioscope.chameleon.ChameleonApplication;
import com.trioscope.chameleon.R;
import com.trioscope.chameleon.metrics.MetricNames;
import com.trioscope.chameleon.storage.BioscopeDBHelper;
import com.trioscope.chameleon.storage.VideoInfoType;
import com.trioscope.chameleon.types.RecordingMetadata;
import com.trioscope.chameleon.util.merge.MergeConfiguration;
import com.trioscope.chameleon.util.merge.VideoConfiguration;
import com.trioscope.chameleon.util.merge.VideoMerger;

import java.io.File;
import java.io.IOException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PreviewMergeActivity extends EnableForegroundDispatchForNFCMessageActivity
        implements SurfaceHolder.Callback {
    private static final Gson gson = new Gson();
    private String majorVideoPath, minorVideoPath;
    private SurfaceView majorVideoSurfaceView, minorVideoSurfaceView;
    private MediaPlayer majorVideoMediaPlayer, minorVideoMediaPlayer;
    private boolean majorVideoSurfaceReady, minorVideoSurfaceReady;
    private SurfaceHolder majorVideoHolder, minorVideoHolder;
    private RelativeLayout majorVideoLayout, minorVideoLayout;
    private ImageButton switchPreviewModeButton;

    private RecordingMetadata localRecordingMetadata, remoteRecordingMetadata;
    private boolean publishedDurationMetrics = false;
    boolean doubleBackToExitPressedOnce = false;
    private int mergeLayoutType = VideoMerger.MERGE_LAYOUT_TYPE_PICTURE_IN_PICTURE;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_preview_merge);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        Intent intent = getIntent();
        localRecordingMetadata = gson.fromJson(
                intent.getStringExtra(ConnectionEstablishedActivity.LOCAL_RECORDING_METADATA_KEY),
                RecordingMetadata.class);
        remoteRecordingMetadata = gson.fromJson(
                intent.getStringExtra(ConnectionEstablishedActivity.REMOTE_RECORDING_METADATA_KEY),
                RecordingMetadata.class);

        majorVideoMediaPlayer = new MediaPlayer();
        minorVideoMediaPlayer = new MediaPlayer();

        majorVideoSurfaceView = (SurfaceView) findViewById(R.id.surfaceview_major_video);
        majorVideoHolder = majorVideoSurfaceView.getHolder();
        majorVideoHolder.addCallback(this);
        minorVideoSurfaceView = (SurfaceView) findViewById(R.id.surfaceview_minor_video);
        minorVideoHolder = minorVideoSurfaceView.getHolder();
        minorVideoHolder.addCallback(this);

        final Button startMergeButton = (Button) findViewById(R.id.button_merge);
        startMergeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Don't let user click again
                startMergeButton.setEnabled(false);

                // Decide which is major video depending on user's latest choice of preview playback
                RecordingMetadata majorMetadata, minorMetadata;
                long offsetMillis = 0;
                if (localRecordingMetadata.getAbsoluteFilePath().equalsIgnoreCase(majorVideoPath)) {
                    majorMetadata = localRecordingMetadata;
                    minorMetadata = remoteRecordingMetadata;
                    offsetMillis = remoteRecordingMetadata.getStartTimeMillis() -
                            localRecordingMetadata.getStartTimeMillis();
                } else {
                    majorMetadata = remoteRecordingMetadata;
                    minorMetadata = localRecordingMetadata;
                    offsetMillis = localRecordingMetadata.getStartTimeMillis() -
                            remoteRecordingMetadata.getStartTimeMillis();
                }

                File outputFile = ((ChameleonApplication) getApplication()).createVideoFile(false);

                VideoMerger videoMerger = ((ChameleonApplication) getApplication()).getVideoMerger();

                MergeConfiguration.MergeConfigurationBuilder config = MergeConfiguration.builder();
                config.videoStartOffsetMilli(offsetMillis);
                config.mergeLayoutType(mergeLayoutType);
                videoMerger.mergeVideos(
                        VideoConfiguration.builder()
                                .file(new File(majorMetadata.getAbsoluteFilePath()))
                                .build(),
                        VideoConfiguration.builder()
                                .file(new File(minorMetadata.getAbsoluteFilePath()))
                                .build(),
                        new File(outputFile.getAbsolutePath()),
                        config.build());

                // Log aggregate metadata into local DB
                log.info("Adding metadata (videographer) to local DB");
                BioscopeDBHelper helper = new BioscopeDBHelper(PreviewMergeActivity.this);
                if (minorMetadata.getVideographer() != null) {
                    log.info("Inserting {} as minor videographer", minorMetadata.getVideographer());
                    helper.insertVideoInfo(outputFile.getName(), VideoInfoType.VIDEOGRAPHER,
                            minorMetadata.getVideographer());
                }
                if (majorMetadata.getVideographer() != null) {
                    log.info("Inserting {} as major videographer", majorMetadata.getVideographer());
                    helper.insertVideoInfo(outputFile.getName(), VideoInfoType.VIDEOGRAPHER,
                            majorMetadata.getVideographer());
                }
                helper.close();

                Intent moveToLibrary = new Intent(PreviewMergeActivity.this, VideoLibraryGridActivity.class);
                startActivity(moveToLibrary);
            }
        });

        ImageButton swapVideoPositions = (ImageButton) findViewById(R.id.button_swap_video_positions);
        swapVideoPositions.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // swapping the video paths
                playVideos(minorVideoPath, majorVideoPath,
                        getMajorVideoAheadOfMinorVideoByMillis(minorVideoPath));
            }
        });

        switchPreviewModeButton = (ImageButton) findViewById(R.id.button_switch_preview_mode);
        switchPreviewModeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                majorVideoSurfaceReady = false;
                minorVideoSurfaceReady = false;

                switchPreviewLayoutMode(majorVideoLayout, minorVideoLayout);
            }
        });

        majorVideoLayout = (RelativeLayout) findViewById(R.id.relativeLayout_major_video);
        minorVideoLayout = (RelativeLayout) findViewById(R.id.relativeLayout_minor_video);
    }

    private void switchPreviewLayoutMode(
            final RelativeLayout majorVideoLayout,
            final RelativeLayout minorVideoLayout) {
        FrameLayout.LayoutParams majorLayoutParams =
                (FrameLayout.LayoutParams) majorVideoLayout.getLayoutParams();
        FrameLayout.LayoutParams minorLayoutParams =
                (FrameLayout.LayoutParams) minorVideoLayout.getLayoutParams();

        if (mergeLayoutType == VideoMerger.MERGE_LAYOUT_TYPE_PICTURE_IN_PICTURE) {

            majorLayoutParams.height = 1280;
            majorLayoutParams.width = 720;
            majorLayoutParams.setMargins(0, 300, 0, 0);
            majorLayoutParams.gravity = Gravity.TOP | Gravity.LEFT;
            majorVideoLayout.setLayoutParams(majorLayoutParams);

            minorLayoutParams.height = 1280;
            minorLayoutParams.width = 720;
            minorLayoutParams.setMargins(0, 300, 0, 0);
            minorLayoutParams.gravity = Gravity.TOP | Gravity.RIGHT;
            minorVideoLayout.setLayoutParams(minorLayoutParams);

            switchPreviewModeButton.setImageResource(R.drawable.picture_in_picture);
            mergeLayoutType = VideoMerger.MERGE_LAYOUT_TYPE_SIDE_BY_SIDE;
        } else {
            Display display = getWindowManager().getDefaultDisplay();
            Point size = new Point();
            display.getSize(size);
            majorLayoutParams.width = size.x;
            majorLayoutParams.height = size.y;
            majorLayoutParams.setMargins(0, 0, 0, 0);
            majorLayoutParams.gravity = Gravity.NO_GRAVITY;
            majorVideoLayout.setLayoutParams(majorLayoutParams);

            minorLayoutParams.height = 732;
            minorLayoutParams.width = 412;
            minorLayoutParams.setMargins(54, 0, 0, 300);
            minorLayoutParams.gravity = Gravity.BOTTOM | Gravity.LEFT;
            minorVideoLayout.setLayoutParams(minorLayoutParams);

            switchPreviewModeButton.setImageResource(R.drawable.side_by_side);
            mergeLayoutType = VideoMerger.MERGE_LAYOUT_TYPE_PICTURE_IN_PICTURE;
        }
    }

    private long getMajorVideoAheadOfMinorVideoByMillis(final String majorVideoPath) {
        long majorVideoAheadOfMinorVideoByMillis = 0;
        if (localRecordingMetadata.getAbsoluteFilePath().equalsIgnoreCase(majorVideoPath)) {
            majorVideoAheadOfMinorVideoByMillis = remoteRecordingMetadata.getStartTimeMillis() -
                    localRecordingMetadata.getStartTimeMillis();
        } else {
            majorVideoAheadOfMinorVideoByMillis = localRecordingMetadata.getStartTimeMillis() -
                    remoteRecordingMetadata.getStartTimeMillis();
        }
        return majorVideoAheadOfMinorVideoByMillis;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        playVideos(majorVideoPath, minorVideoPath,
                getMajorVideoAheadOfMinorVideoByMillis(majorVideoPath));
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

            if (!publishedDurationMetrics) {
                //publish time metrics
                ChameleonApplication.getMetrics().sendTime(
                        MetricNames.Category.VIDEO.getName(),
                        MetricNames.Label.DURATION.getName(),
                        majorVideoMediaPlayer.getDuration());
                publishedDurationMetrics = true;
                log.info("Duration to be published is {}", majorVideoMediaPlayer.getDuration());
            }
        } catch (Exception e) {
            log.error("Failed to play major and minor videos..", e);
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
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        log.info("User is leaving. Finishing activity..");
        finish();
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


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_preview_merge, menu);
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
    public void onBackPressed() {
        if (doubleBackToExitPressedOnce) {
            super.onBackPressed();
            return;
        }

        this.doubleBackToExitPressedOnce = true;
        Toast.makeText(this, "Press back again to exit", Toast.LENGTH_SHORT).show();

        new Handler().postDelayed(new Runnable() {

            @Override
            public void run() {
                doubleBackToExitPressedOnce = false;
            }
        }, 3000);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {

        if (holder == majorVideoHolder) {
            minorVideoSurfaceView.setZOrderOnTop(true);
            majorVideoMediaPlayer.setDisplay(majorVideoHolder);
            majorVideoSurfaceReady = true;
        }

        if (holder == minorVideoHolder) {
            minorVideoSurfaceView.setZOrderOnTop(true);
            minorVideoMediaPlayer.setDisplay(minorVideoHolder);
            minorVideoMediaPlayer.setVolume(0f, 0f);
            minorVideoSurfaceReady = true;
        }

        if (majorVideoSurfaceReady && minorVideoSurfaceReady) {
            playVideos(
                    localRecordingMetadata.getAbsoluteFilePath(),
                    remoteRecordingMetadata.getAbsoluteFilePath(),
                    getMajorVideoAheadOfMinorVideoByMillis(
                            localRecordingMetadata.getAbsoluteFilePath()));
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {

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
}
