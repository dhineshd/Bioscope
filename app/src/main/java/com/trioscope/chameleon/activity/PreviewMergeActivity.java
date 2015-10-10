package com.trioscope.chameleon.activity;

import android.content.Intent;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.os.Bundle;
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
import android.widget.TextView;

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
public class PreviewMergeActivity extends EnableForegroundDispatchForNFCMessageActivity {
    private final Gson gson = new Gson();
    private String majorVideoPath, minorVideoPath;
    private int majorVideoAheadOfMinorVideoByMillis;
    private TextureView majorVideoTextureView;
    private TextureView minorVideoTextureView;
    private MediaPlayer majorVideoMediaPlayer;
    private MediaPlayer minorVideoMediaPlayer;
    private boolean majorVideoSurfaceReady;
    private boolean minorVideoSurfaceReady;
    private File outputFile;
    private TextView touchReplayTextView;
    private Button buttonMerge;
    private RecordingMetadata localRecordingMetadata, remoteRecordingMetadata;
    private boolean publishedDurationMetrics = false;
    private boolean isMergeRequested;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_preview_merge);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        buttonMerge = (Button) findViewById(R.id.button_merge);

        Intent intent = getIntent();
        localRecordingMetadata = gson.fromJson(
                intent.getStringExtra(ConnectionEstablishedActivity.LOCAL_RECORDING_METADATA_KEY),
                RecordingMetadata.class);
        remoteRecordingMetadata = gson.fromJson(
                intent.getStringExtra(ConnectionEstablishedActivity.REMOTE_RECORDING_METADATA_KEY),
                RecordingMetadata.class);

        majorVideoAheadOfMinorVideoByMillis = (int) (remoteRecordingMetadata.getStartTimeMillis() -
                localRecordingMetadata.getStartTimeMillis());

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
                            majorVideoAheadOfMinorVideoByMillis);
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
                            majorVideoAheadOfMinorVideoByMillis);                }
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
                    offsetMillis =  remoteRecordingMetadata.getStartTimeMillis() -
                            localRecordingMetadata.getStartTimeMillis();
                } else {
                    majorMetadata = remoteRecordingMetadata;
                    minorMetadata = localRecordingMetadata;
                    offsetMillis =  localRecordingMetadata.getStartTimeMillis() -
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
                // swapping the video paths
                majorVideoAheadOfMinorVideoByMillis = -majorVideoAheadOfMinorVideoByMillis;
                startVideos(minorVideoPath, majorVideoPath, majorVideoAheadOfMinorVideoByMillis);
            }
        });

        touchReplayTextView = (TextView) findViewById(R.id.textView_touch_replay);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        startVideos(majorVideoPath, minorVideoPath, majorVideoAheadOfMinorVideoByMillis);
        return true;
    }

    private void startVideos(
            final String majorVideoPath,
            final String minorVideoPath,
            final int majorVideoAheadOfMinorVideoByMillis) {

        // TODO : Use majorVideoAheadOfMinorVideoByMillis to ensure playback of both videos is in sync

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
                minorVideoMediaPlayer.stop();
                touchReplayTextView.setVisibility(View.VISIBLE);
            }
        });

        touchReplayTextView.setVisibility(View.INVISIBLE);

        majorVideoMediaPlayer.start();
        minorVideoMediaPlayer.start();

        if (!publishedDurationMetrics) {
            //publish time metrics
            ChameleonApplication.getMetrics().sendTime(
                    MetricNames.Category.VIDEO.getName(),
                    MetricNames.Label.DURATION.getName(),
                    majorVideoMediaPlayer.getDuration());
            publishedDurationMetrics = true;
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
}
