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
import android.widget.Button;
import android.widget.ImageButton;

import com.google.gson.Gson;
import com.trioscope.chameleon.R;
import com.trioscope.chameleon.types.RecordingMetadata;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_preview_merge);

        Intent intent = getIntent();
        final RecordingMetadata localRecordingMetadata = gson.fromJson(
                intent.getStringExtra(ConnectionEstablishedActivity.LOCAL_RECORDING_METADATA_KEY), RecordingMetadata.class);
        final RecordingMetadata remoteRecordingMetadata = gson.fromJson(
                intent.getStringExtra(ConnectionEstablishedActivity.REMOTE_RECORDING_METADATA_KEY), RecordingMetadata.class);

        majorVideoAheadOfMinorVideoByMillis = (int) (remoteRecordingMetadata.getStartTimeMillis() -
                localRecordingMetadata.getStartTimeMillis());

        majorVideoMediaPlayer = new MediaPlayer();
        minorVideoMediaPlayer = new MediaPlayer();

        majorVideoPath = localRecordingMetadata.getAbsoluteFilePath();
        minorVideoPath = remoteRecordingMetadata.getAbsoluteFilePath();

        majorVideoTextureView = (TextureView) findViewById(R.id.textureview_major_video);

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

        Button startMergeButton = (Button) findViewById(R.id.button_merge);
        startMergeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(getApplicationContext(), MergeVideosActivity.class);
                // Decide which is major video depending on user's latest choice of preview playback
                if (localRecordingMetadata.getAbsoluteFilePath().equalsIgnoreCase(majorVideoPath)) {
                    intent.putExtra(MergeVideosActivity.MAJOR_VIDEO_METADATA_KEY, gson.toJson(localRecordingMetadata));
                    intent.putExtra(MergeVideosActivity.MINOR_VIDEO_METADATA_KEY, gson.toJson(remoteRecordingMetadata));
                } else {
                    intent.putExtra(MergeVideosActivity.MAJOR_VIDEO_METADATA_KEY, gson.toJson(remoteRecordingMetadata));
                    intent.putExtra(MergeVideosActivity.MINOR_VIDEO_METADATA_KEY, gson.toJson(localRecordingMetadata));
                }
                startActivity(intent);
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
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        startVideos(majorVideoPath, minorVideoPath, majorVideoAheadOfMinorVideoByMillis);
        return true;
    }

    /**
     * Sets the TextureView transform to preserve the aspect ratio of the video.
     */
    private void adjustAspectRatio(TextureView textureView, int videoWidth, int videoHeight) {
        int viewWidth = textureView.getWidth();
        int viewHeight = textureView.getHeight();
        double aspectRatio = (double) videoHeight / videoWidth;

        log.info("view width = {}, height = {}", videoWidth, viewHeight);
        log.info("video width = {}, height = {}", videoWidth, videoHeight);

        int newWidth, newHeight;
        if (viewHeight > (int) (viewWidth * aspectRatio)) {
            // limited by narrow width; restrict height
            newWidth = viewWidth;
            newHeight = (int) (viewWidth * aspectRatio);
        } else {
            // limited by short height; restrict width
            newWidth = (int) (viewHeight / aspectRatio);
            newHeight = viewHeight;
        }
        int xoff = (viewWidth - newWidth) / 2;
        int yoff = (viewHeight - newHeight) / 2;

        log.info("x off = {}, y off = {}", xoff, yoff);
        log.info("new width = {}, height = {}", newWidth, newHeight);

        Matrix txform = new Matrix();
        textureView.getTransform(txform);
        txform.setScale((float) newWidth / viewWidth, (float) newHeight / viewHeight);
        txform.postTranslate(xoff, yoff);
        textureView.setTransform(txform);
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

        majorVideoMediaPlayer.start();
        minorVideoMediaPlayer.start();
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
