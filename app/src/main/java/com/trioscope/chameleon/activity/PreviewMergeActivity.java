package com.trioscope.chameleon.activity;

import android.app.FragmentManager;
import android.content.Intent;
import android.graphics.SurfaceTexture;
import android.graphics.Typeface;
import android.media.MediaPlayer;
import android.net.Uri;
import android.graphics.Matrix;
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
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;
import com.trioscope.chameleon.ChameleonApplication;
import com.trioscope.chameleon.R;
import com.trioscope.chameleon.fragment.FfmpegTaskFragment;
import com.trioscope.chameleon.types.RecordingMetadata;
import com.trioscope.chameleon.util.merge.ProgressUpdatable;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PreviewMergeActivity extends EnableForegroundDispatchForNFCMessageActivity
        implements ProgressUpdatable{
    private static final String TASK_FRAGMENT_TAG = "ASYNC_TASK_FRAGMENT_TAG";
    private final Gson gson = new Gson();
    private String majorVideoPath, minorVideoPath;
    private int majorVideoAheadOfMinorVideoByMillis;
    private TextureView majorVideoTextureView;
    private TextureView minorVideoTextureView;
    private MediaPlayer majorVideoMediaPlayer;
    private MediaPlayer minorVideoMediaPlayer;
    private boolean majorVideoSurfaceReady;
    private boolean minorVideoSurfaceReady;
    private FfmpegTaskFragment taskFragment;
    private File outputFile;
    private TextView touchReplayTextView;
    private Button buttonMerge;
    private RecordingMetadata localRecordingMetadata, remoteRecordingMetadata;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_preview_merge);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        Typeface appFontTypeface = Typeface.createFromAsset(getAssets(),
                ChameleonApplication.APP_FONT_LOCATION);

        buttonMerge = (Button) findViewById(R.id.button_merge);
        buttonMerge.setTypeface(appFontTypeface);

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

        majorVideoPath = localRecordingMetadata.getAbsoluteFilePath();

        minorVideoPath = remoteRecordingMetadata.getAbsoluteFilePath();

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
                    startVideos(majorVideoPath, minorVideoPath, majorVideoAheadOfMinorVideoByMillis);
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
                    startVideos(majorVideoPath, minorVideoPath, majorVideoAheadOfMinorVideoByMillis);
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

        final Button startMergeButton = (Button) findViewById(R.id.button_merge);
        startMergeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                // Don't let user click again
                startMergeButton.setEnabled(false);

                // Decide which is major video depending on user's latest choice of preview playback
                String serializedMajorVideoMetadata, serializedMinorVideoMetadata;
                if (localRecordingMetadata.getAbsoluteFilePath().equalsIgnoreCase(majorVideoPath)) {
                    serializedMajorVideoMetadata = gson.toJson(localRecordingMetadata);
                    serializedMinorVideoMetadata = gson.toJson(remoteRecordingMetadata);
                } else {
                    serializedMajorVideoMetadata = gson.toJson(remoteRecordingMetadata);
                    serializedMinorVideoMetadata = gson.toJson(localRecordingMetadata);
                }

                // Initialize merge fragment
                FragmentManager fm = getFragmentManager();
                //taskFragment = (FfmpegTaskFragment) fm.findFragmentByTag(TASK_FRAGMENT_TAG);

                outputFile = ((ChameleonApplication) getApplication()).getOutputMediaFile(
                        "BIOSCOPE_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".mp4");
                        taskFragment = FfmpegTaskFragment.newInstance(
                                serializedMajorVideoMetadata,
                                serializedMinorVideoMetadata,
                                majorVideoAheadOfMinorVideoByMillis,
                                outputFile.getAbsolutePath());
                fm.beginTransaction().add(taskFragment, TASK_FRAGMENT_TAG).commit();
            }
        });

        ImageButton swapMergePreviewButton = (ImageButton) findViewById(R.id.button_swap_merge_preview);
        swapMergePreviewButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // swapping the video paths
                majorVideoAheadOfMinorVideoByMillis = -majorVideoAheadOfMinorVideoByMillis;
                //startVideoViews(minorVideoPath, majorVideoPath, majorVideoAheadOfMinorVideoByMillis);
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

        // Local video will be shown on outer player and remote video on inner player

        // TextureView

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

        // Replay both videos continuously on a loop
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
        cleanup();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        cleanup();

        //Re-use MainActivity instance if already present. If not, create new instance.
        Intent openMainActivity = new Intent(getApplicationContext(), MainActivity.class);
        openMainActivity.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(openMainActivity);
    }

    private void cleanup() {
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
    public void onProgress(double progress, double outOf) {
        int progressPerc = getPercent(progress, outOf);
        ProgressBar bar = (ProgressBar) findViewById(R.id.ffmpeg_progress_bar);
        if (View.VISIBLE != bar.getVisibility()) {
            bar.setVisibility(View.VISIBLE);
        }
        bar.setProgress(progressPerc);

        log.info("Now {}% done", progressPerc);
    }

    private static int getPercent(double progress, double outOf) {
        return (int) Math.min(100, Math.ceil(100.0 * progress / outOf));
    }

    @Override
    public void onCompleted() {
        ProgressBar bar = (ProgressBar) findViewById(R.id.ffmpeg_progress_bar);
        bar.setVisibility(View.GONE);
        log.info("FFMPEG Completed! Going to video library now!");

        //Send a broadcast about the newly added video file for Gallery Apps to recognize the video
        Intent addVideoIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        addVideoIntent.setData(Uri.fromFile(outputFile));
        sendBroadcast(addVideoIntent);

        Toast.makeText(this, outputFile.getName() + " saved to gallery", Toast.LENGTH_LONG).show();

        //Re-use MainActivity instance if already present. If not, create new instance.
        Intent openMainActivity = new Intent(getApplicationContext(), MainActivity.class);
        openMainActivity.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(openMainActivity);
    }
}
