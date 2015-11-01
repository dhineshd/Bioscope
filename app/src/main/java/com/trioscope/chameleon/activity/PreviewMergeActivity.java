package com.trioscope.chameleon.activity;

import android.content.Intent;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;
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

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PreviewMergeActivity extends EnableForegroundDispatchForNFCMessageActivity {
    public static final long UPDATE_SEEK_TIME_DELAY = 500;
    private static final int FIRST_FRAMES_MS_INCREMENT = 100;

    private final Gson gson = new Gson();
    private String majorVideoPath, minorVideoPath;
    private TextureView majorVideoTextureView;
    private TextureView minorVideoTextureView;
    private MediaPlayer majorVideoMediaPlayer;
    private MediaPlayer minorVideoMediaPlayer;
    private boolean majorVideoSurfaceReady;
    private boolean minorVideoSurfaceReady;
    private File outputFile;
    private TextView touchReplayTextView;
    private RecordingMetadata localRecordingMetadata, remoteRecordingMetadata;
    private boolean publishedDurationMetrics = false;
    private boolean isMergeRequested;
    boolean doubleBackToExitPressedOnce = false;
    private Handler seekBarHandler;
    private UpdateSeekBarRunnable updateSeekBarRunnable;
    private long majorVideoAheadOfMinorByMillis;

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

        majorVideoAheadOfMinorByMillis = getMajorVideoAheadOfMinorVideoByMillis(localRecordingMetadata.getAbsoluteFilePath());

        majorVideoMediaPlayer = new MediaPlayer();
        minorVideoMediaPlayer = new MediaPlayer();

        majorVideoTextureView = (TextureView) findViewById(R.id.textureview_major_video);

        majorVideoTextureView.getViewTreeObserver().addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        majorVideoTextureView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                        int width = majorVideoTextureView.getMeasuredWidth();
                        int height = majorVideoTextureView.getMeasuredHeight();
                        ViewGroup.LayoutParams layoutParams = majorVideoTextureView.getLayoutParams();
                        layoutParams.width = width;
                        layoutParams.height = height;
                        majorVideoTextureView.setLayoutParams(layoutParams);
                    }
                });

        majorVideoTextureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {

                majorVideoMediaPlayer.setSurface(new Surface(surface));
                majorVideoSurfaceReady = true;
                if (minorVideoSurfaceReady && majorVideoSurfaceReady) {
                    startVideos(
                            localRecordingMetadata.getAbsoluteFilePath(),
                            remoteRecordingMetadata.getAbsoluteFilePath(),
                            majorVideoAheadOfMinorByMillis);
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
        });

        minorVideoTextureView = (TextureView) findViewById(R.id.textureview_minor_video);

        minorVideoTextureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {

                minorVideoMediaPlayer.setSurface(new Surface(surface));
                minorVideoMediaPlayer.setVolume(0, 0);
                minorVideoSurfaceReady = true;

                if (minorVideoSurfaceReady && majorVideoSurfaceReady) {
                    startVideos(
                            localRecordingMetadata.getAbsoluteFilePath(),
                            remoteRecordingMetadata.getAbsoluteFilePath(),
                            majorVideoAheadOfMinorByMillis
                    );
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
        });

        seekBarHandler = new Handler();
        final SeekBar seekBar = (SeekBar) findViewById(R.id.merge_preview_seekbar);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    log.info("Seekbar dropped at {}", seekBar.getProgress());
                    int seekPoint = (int) Math.round(majorVideoMediaPlayer.getDuration() * (double) progress / 100.0);
                    seekVideosTo(seekPoint);
                } else {
                    log.info("Seekbar not changed from user");
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

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

                outputFile = ((ChameleonApplication) getApplication()).getOutputMediaFile(
                        ChameleonApplication.MEDIA_TYPE_VIDEO);

                VideoMerger videoMerger = ((ChameleonApplication) getApplication()).getVideoMerger();

                MergeConfiguration.MergeConfigurationBuilder config = MergeConfiguration.builder();
                config.videoStartOffsetMilli(offsetMillis);
                videoMerger.mergeVideos(
                        VideoConfiguration.builder()
                                .file(new File(majorMetadata.getAbsoluteFilePath()))
                                .horizontallyFlipped(majorMetadata.isHorizontallyFlipped()).build(),
                        VideoConfiguration.builder()
                                .file(new File(minorMetadata.getAbsoluteFilePath()))
                                .horizontallyFlipped(minorMetadata.isHorizontallyFlipped()).build(),
                        new File(outputFile.getAbsolutePath()),
                        config.build());

                // Log aggregate metadata into local DB
                log.info("Adding metadata (videographer) to local DB");
                BioscopeDBHelper helper = new BioscopeDBHelper(PreviewMergeActivity.this);
                if (minorMetadata.getVideographer() != null) {
                    log.info("Inserting {} as minor videographer", minorMetadata.getVideographer());
                    helper.insertVideoInfo(outputFile.getName(), VideoInfoType.VIDEOGRAPHER, minorMetadata.getVideographer());
                }
                if (majorMetadata.getVideographer() != null) {
                    log.info("Inserting {} as major videographer", majorMetadata.getVideographer());
                    helper.insertVideoInfo(outputFile.getName(), VideoInfoType.VIDEOGRAPHER, majorMetadata.getVideographer());
                }
                helper.close();

                Intent moveToLibrary = new Intent(PreviewMergeActivity.this, VideoLibraryGridActivity.class);
                startActivity(moveToLibrary);

                isMergeRequested = true;
            }
        });

        ImageButton swapMergePreviewButton = (ImageButton) findViewById(R.id.button_swap_merge_preview);
        swapMergePreviewButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                majorVideoAheadOfMinorByMillis *= -1;
                // swapping the video paths
                startVideos(minorVideoPath, majorVideoPath,
                        getMajorVideoAheadOfMinorVideoByMillis(minorVideoPath));
            }
        });

        touchReplayTextView = (TextView) findViewById(R.id.textView_touch_replay);
    }

    Object seekLock = new Object();
    Integer majorVideoSeekedTo, minorVideoSeekedTo;

    private void seekVideosTo(int seekPoint) {
        log.info("Seeking videos to seekPoint {}", seekPoint);

        majorVideoMediaPlayer.pause();
        minorVideoMediaPlayer.pause();

        seekVideosToWithCallback(seekPoint, new SeekVideosCallback() {
            @Override
            public void videosSeeked(int majorVideoSeekedTo, int minorVideoSeekedTo) {
                long diff = majorVideoAheadOfMinorByMillis - (majorVideoSeekedTo - minorVideoSeekedTo);

                startWithDelay(diff);
            }
        });
    }

    private void seekVideosToWithCallback(int seekPoint, final SeekVideosCallback callback) {
        majorVideoSeekedTo = null;
        minorVideoSeekedTo = null;
        majorVideoMediaPlayer.setOnSeekCompleteListener(new MediaPlayer.OnSeekCompleteListener() {
            @Override
            public void onSeekComplete(MediaPlayer mp) {
                synchronized (seekLock) {
                    majorVideoSeekedTo = majorVideoMediaPlayer.getCurrentPosition();
                    log.info("Major video seek completed, current position is {}", majorVideoSeekedTo);

                    if (minorVideoSeekedTo != null) {
                        log.info("Minor video already seeked, going to call callback");
                        callback.videosSeeked(majorVideoSeekedTo, minorVideoSeekedTo);
                    }
                }
            }
        });

        minorVideoMediaPlayer.setOnSeekCompleteListener(new MediaPlayer.OnSeekCompleteListener() {
            @Override
            public void onSeekComplete(MediaPlayer mp) {
                synchronized (seekLock) {
                    minorVideoSeekedTo = minorVideoMediaPlayer.getCurrentPosition();
                    log.info("Minor video seek completed, current position is {}", minorVideoSeekedTo);

                    if (majorVideoSeekedTo != null) {
                        log.info("Major video already seeked, going to call callback");
                        callback.videosSeeked(majorVideoSeekedTo, minorVideoSeekedTo);
                    }
                }
            }
        });

        majorVideoMediaPlayer.seekTo(seekPoint);
        minorVideoMediaPlayer.seekTo(seekPoint);
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
        startVideos(majorVideoPath, minorVideoPath, getMajorVideoAheadOfMinorVideoByMillis(majorVideoPath));
        return true;
    }

    private void startVideos(
            final String majorVideoPath,
            final String minorVideoPath,
            final long majorVideoAheadOfMinorVideoByMillis) {
        majorVideoMediaPlayer.reset();
        minorVideoMediaPlayer.reset();

        this.majorVideoPath = majorVideoPath;
        this.minorVideoPath = minorVideoPath;

        if (majorVideoPath.equals(localRecordingMetadata.getAbsoluteFilePath())) {
            updateDisplayOrientation(majorVideoTextureView, localRecordingMetadata.isHorizontallyFlipped());
            updateDisplayOrientation(minorVideoTextureView, remoteRecordingMetadata.isHorizontallyFlipped());
        } else {
            updateDisplayOrientation(majorVideoTextureView, remoteRecordingMetadata.isHorizontallyFlipped());
            updateDisplayOrientation(minorVideoTextureView, localRecordingMetadata.isHorizontallyFlipped());
        }

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
                log.info("Video has completed");
                if (updateSeekBarRunnable != null)
                    updateSeekBarRunnable.setCancelled(true);
                minorVideoMediaPlayer.stop();
                touchReplayTextView.setVisibility(View.VISIBLE);
            }
        });

        touchReplayTextView.setVisibility(View.INVISIBLE);

        log.info("Video Media Players are starting {}", majorVideoAheadOfMinorVideoByMillis);

        skipFirstFrames(majorVideoAheadOfMinorVideoByMillis);

        // Record progress with the seekbar
        SeekBar seekBar = (SeekBar) findViewById(R.id.merge_preview_seekbar);
        updateSeekBarRunnable = new UpdateSeekBarRunnable(seekBar, majorVideoMediaPlayer);
        seekBarHandler.postDelayed(updateSeekBarRunnable, UPDATE_SEEK_TIME_DELAY);

        if (!publishedDurationMetrics) {
            //publish time metrics
            ChameleonApplication.getMetrics().sendTime(
                    MetricNames.Category.VIDEO.getName(),
                    MetricNames.Label.DURATION.getName(),
                    majorVideoMediaPlayer.getDuration());
            publishedDurationMetrics = true;
        }
    }

    private void skipFirstFrames(long majorVideoAheadOfMinorVideoByMillis) {
        skipFirstFramesHelper(majorVideoAheadOfMinorVideoByMillis, FIRST_FRAMES_MS_INCREMENT);
    }

    private void skipFirstFramesHelper(final long majorVideoAheadOfMinorVideoByMillis, final int seekTo) {
        log.info("Seeking to {}ms", seekTo);
        seekVideosToWithCallback(seekTo, new SeekVideosCallback() {
            @Override
            public void videosSeeked(int majorVideoSeekedTo, int minorVideoSeekedTo) {
                if (majorVideoSeekedTo == 0 || minorVideoSeekedTo == 0) {
                    log.info("Attempted to seek to {}, but we are still at 0ms", seekTo);
                    skipFirstFramesHelper(majorVideoAheadOfMinorVideoByMillis, seekTo + PreviewMergeActivity.FIRST_FRAMES_MS_INCREMENT);
                } else {
                    startWithDelay(majorVideoAheadOfMinorVideoByMillis);
                }
            }
        });
    }

    private void startWithDelay(long majorVideoAheadOfMinorVideoByMillis) {
        log.info("Putting videos into start state with delay {}", majorVideoAheadOfMinorVideoByMillis);
        if (majorVideoAheadOfMinorVideoByMillis < 0) {
            new Handler(Looper.myLooper()).postDelayed(new MediaPlayerStartRunnable(majorVideoMediaPlayer), -majorVideoAheadOfMinorVideoByMillis);
            minorVideoMediaPlayer.start();
        } else {
            new Handler(Looper.myLooper()).postDelayed(new MediaPlayerStartRunnable(minorVideoMediaPlayer), majorVideoAheadOfMinorVideoByMillis);
            majorVideoMediaPlayer.start();
        }
    }

    private void updateDisplayOrientation(
            final TextureView textureView,
            final boolean horizontallyFlipped) {

        Matrix matrix = new Matrix();
        // Need to generate mirror image
        if (horizontallyFlipped) {
            matrix.setScale(-1, 1);
            matrix.postTranslate(textureView.getLayoutParams().width, 0);
        } else {
            matrix.setScale(1, 1);
            matrix.postTranslate(0, 0);
        }
        textureView.setTransform(matrix);

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

        if (updateSeekBarRunnable != null)
            updateSeekBarRunnable.setCancelled(true);

        // Stop runnable
        if (updateSeekBarRunnable != null)
            updateSeekBarRunnable.setCancelled(true);

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

        // Release surfaces
        if (majorVideoTextureView != null &&
                majorVideoTextureView.getSurfaceTexture() != null) {
            majorVideoTextureView.getSurfaceTexture().release();
        }
        if (minorVideoTextureView != null &&
                minorVideoTextureView.getSurfaceTexture() != null) {
            minorVideoTextureView.getSurfaceTexture().release();
        }

        // Cleanup videos if we are not merging them
        if (!isMergeRequested) {

            log.info("Performing cleanup of single videos since we are not merging them");

            try {
                if (localRecordingMetadata != null) {
                    new File(localRecordingMetadata.getAbsoluteFilePath()).delete();
                }
                if (remoteRecordingMetadata != null) {
                    new File(remoteRecordingMetadata.getAbsoluteFilePath()).delete();
                }
            } catch (Exception e) {
                // Ignore failures
            }
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

    private class MediaPlayerStartRunnable implements Runnable {
        private final MediaPlayer mediaPlayer;
        private final long startTime;

        public MediaPlayerStartRunnable(MediaPlayer mp) {
            mediaPlayer = mp;
            startTime = System.currentTimeMillis();
        }

        @Override
        public void run() {
            long elapsed = System.currentTimeMillis() - startTime;
            log.info("Actual elapsed time between Runnable creation and run() method = {}ms", elapsed);
            mediaPlayer.start();
        }
    }

    private class UpdateSeekBarRunnable implements Runnable {
        private final SeekBar seekBar;
        private final MediaPlayer mediaPlayer;
        private int duration;

        @Setter
        private boolean cancelled;

        public UpdateSeekBarRunnable(SeekBar seekBar, MediaPlayer mediaPlayer) {
            this.duration = mediaPlayer.getDuration();
            this.seekBar = seekBar;
            this.mediaPlayer = mediaPlayer;
            this.cancelled = false;
        }

        @Override
        public void run() {
            if (!cancelled) {
                if (majorVideoMediaPlayer.is)
                    int curPosition = mediaPlayer.getCurrentPosition();

                int progress = (int) Math.min(Math.round(100.0 * (double) curPosition / duration), 100);

                log.info("Setting progress to {}/100", progress);
                seekBar.setProgress(progress);

                if (progress < 100)
                    seekBarHandler.postDelayed(this, UPDATE_SEEK_TIME_DELAY);
            }
        }
    }
}
