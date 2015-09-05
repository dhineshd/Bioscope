package com.trioscope.chameleon.activity;

import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.MediaController;
import android.widget.VideoView;

import com.google.gson.Gson;
import com.trioscope.chameleon.R;
import com.trioscope.chameleon.types.RecordingMetadata;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PreviewMergeActivity extends EnableForegroundDispatchForNFCMessageActivity
        implements MediaController.MediaPlayerControl{
    private final Gson gson = new Gson();
    private MediaController mediaController;
    private VideoView outerVideoView;
    private VideoView innerVideoView;
    private String innerVideoViewVideoPath, outerVideoViewVideoPath;
    private int outerVideoAheadOfInnerVideoByMillis;
    private Handler handler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_preview_merge);

        Intent intent = getIntent();
        final RecordingMetadata localRecordingMetadata = gson.fromJson(
                intent.getStringExtra(MergeVideosActivity.LOCAL_RECORDING_METADATA_KEY), RecordingMetadata.class);
        final RecordingMetadata remoteRecordingMetadata = gson.fromJson(
                intent.getStringExtra(MergeVideosActivity.REMOTE_RECORDING_METADATA_KEY), RecordingMetadata.class);

        outerVideoAheadOfInnerVideoByMillis = (int) (remoteRecordingMetadata.getStartTimeMillis() -
                localRecordingMetadata.getStartTimeMillis());

        outerVideoView = (VideoView) findViewById(R.id.videoView_local_video);

        innerVideoView = (VideoView) findViewById(R.id.videoView_remote_video);

        //localRecordingVideoView.setMediaController(null);
        //innerVideoView.setVideoPath(innerVideoViewVideoPath);
        innerVideoView.setZOrderMediaOverlay(true);

//        mediaController = new MediaController(this){
//            @Override
//            public void hide() {
//                super.show(0);
//            }
//        };
//        mediaController.setMediaPlayer(this);
//        mediaController.setAnchorView(outerVideoView);
//
//        handler.postDelayed(new Runnable() {
//            @Override
//            public void run() {
//                mediaController.show(0);
//            }
//        }, 1000);



//        mediaController = new MediaController(getApplicationContext());
//        mediaController.setAnchorView(outerVideoView);
//        mediaController.setMediaPlayer(this);
        //outerVideoView.setMediaController(mediaController);

        startVideoViews(
                localRecordingMetadata.getAbsoluteFilePath(),
                remoteRecordingMetadata.getAbsoluteFilePath(),
                outerVideoAheadOfInnerVideoByMillis);

        final MediaController.MediaPlayerControl mediaPlayerControl = this;
//        outerVideoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
//            @Override
//            public void onPrepared(MediaPlayer mp) {
////                mediaController = new MediaController(getApplicationContext());
////                mediaController.setMediaPlayer(mediaPlayerControl);
////                mediaController.setAnchorView(findViewById(R.id.videoView_local_video));
////                mediaController.show(0);
//            }
//        });

//        outerVideoView.start();
//        innerVideoView.start();


//        outerVideoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
//            @Override
//            public void onPrepared(final MediaPlayer mediaPlayer) {
//                log.info("Local video prepared");
//                if (localRecordingMetadata.getStartTimeMillis() < remoteRecordingMetadata.getStartTimeMillis()) {
//                    // Adjust playback if local video started before the remote video
//
//                    log.info("local started before remote by {} ms", absDifferenceBetweenLocalAndRemoteStartTimes);
//                    mediaPlayer.setOnSeekCompleteListener(new MediaPlayer.OnSeekCompleteListener() {
//                        @Override
//                        public void onSeekComplete(final MediaPlayer mediaPlayer) {
//
//
//                            log.info("local video onSeek complete. local video position = {}, remote video position = {}",
//                                    mediaPlayer.getCurrentPosition(), innerVideoView.getCurrentPosition());
//
//                            //remoteRecordingVideoView.setMediaController(null);
//                            innerVideoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
//                                @Override
//                                public void onPrepared(MediaPlayer mp) {
//                                    mp.setOnSeekCompleteListener(new MediaPlayer.OnSeekCompleteListener() {
//                                        @Override
//                                        public void onSeekComplete(MediaPlayer mp) {
//                                            log.info("remote video onSeek complete. local video position = {}, remote video position = {}",
//                                                    mediaPlayer.getCurrentPosition(), mp.getCurrentPosition());
//                                            mediaPlayer.start();
//                                            mp.start();
//                                            log.info("Playback started. local video position = {}, remote video position = {}",
//                                                    mediaPlayer.getCurrentPosition(),  mp.getCurrentPosition());
//                                        }
//                                    });
//                                    mp.seekTo(1);
//                                }
//                            });
//                            innerVideoView.setVideoPath(innerVideoViewVideoPath);
//                        }
//                    });
//                    mediaPlayer.seekTo(absDifferenceBetweenLocalAndRemoteStartTimes);
//                } else if (absDifferenceBetweenLocalAndRemoteStartTimes == 0) {
//                    innerVideoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
//                        @Override
//                        public void onPrepared(MediaPlayer mp) {
//                            mediaPlayer.start();
//                            mp.start();
//                            log.info("Playback started. local video position = {}, remote video position = {}",
//                                    mediaPlayer.getCurrentPosition(), mp.getCurrentPosition());
//                        }
//                    });
//                    innerVideoView.setVideoPath(innerVideoViewVideoPath);
//                }
//            }
//        });


//        remoteRecordingVideoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
//            @Override
//            public void onPrepared(MediaPlayer mediaPlayer) {
//                log.info("Remote video prepared");
//
//                // Disable sound for remote video playback
//                //mediaPlayer.setVolume(0f, 0f);
//                if (localRecordingMetadata.getStartTimeMillis() > remoteRecordingMetadata.getStartTimeMillis()) {
//                    // Adjust playback if remote video started before the local video
//                    log.info("remote started before local by {} ms", absDifferenceBetweenLocalAndRemoteStartTimes);
//                    mediaPlayer.setOnSeekCompleteListener(new MediaPlayer.OnSeekCompleteListener() {
//                        @Override
//                        public void onSeekComplete(MediaPlayer mediaPlayer) {
//                            log.info("remote video onSeek complete : current position = {}", mediaPlayer.getCurrentPosition());
//                            localRecordingVideoView.start();
//                            mediaPlayer.start();
//                            log.info("remote video : current position = {}", mediaPlayer.getCurrentPosition());
//                        }
//                    });
//                    mediaPlayer.seekTo(absDifferenceBetweenLocalAndRemoteStartTimes);
//                }
//            }
//        });

        Button startMergeButton = (Button) findViewById(R.id.button_preview_and_merge);
        startMergeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(getApplicationContext(), MergeVideosActivity.class);
                intent.putExtra(MergeVideosActivity.LOCAL_RECORDING_METADATA_KEY, gson.toJson(localRecordingMetadata));
                intent.putExtra(MergeVideosActivity.REMOTE_RECORDING_METADATA_KEY, gson.toJson(remoteRecordingMetadata));
                startActivity(intent);
            }
        });

        Button swapMergePreviewButton = (Button) findViewById(R.id.button_swap_merge_preview);
        swapMergePreviewButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                // swapping the video paths
                outerVideoAheadOfInnerVideoByMillis = -outerVideoAheadOfInnerVideoByMillis;
                startVideoViews(innerVideoViewVideoPath, outerVideoViewVideoPath, outerVideoAheadOfInnerVideoByMillis);
            }
        });
    }

    private void startVideoViews(
            final String outerVideoPath,
            final String innerVideoPath,
            final int outerVideoAheadOfInnerVideoByTimeMillis) {

        log.info("outer video ahead of local videos = {} ms", outerVideoAheadOfInnerVideoByTimeMillis);

        if (outerVideoView.isPlaying()) {
            outerVideoView.stopPlayback();
        }
        if (innerVideoView.isPlaying()) {
            innerVideoView.stopPlayback();
        }

        outerVideoViewVideoPath = outerVideoPath;
        outerVideoView.setVideoPath(outerVideoViewVideoPath);
        innerVideoViewVideoPath = innerVideoPath;
        innerVideoView.setVideoPath(innerVideoViewVideoPath);
        innerVideoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                mp.setVolume(0.0f, 0.0f);
            }
        });
        outerVideoView.start();
        innerVideoView.start();

//        if (outerVideoAheadOfInnerVideoByTimeMillis > 0) {
//            outerVideoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
//                @Override
//                public void onPrepared(final MediaPlayer mp1) {
//                    mp1.setOnSeekCompleteListener(new MediaPlayer.OnSeekCompleteListener() {
//                        @Override
//                        public void onSeekComplete(final MediaPlayer mp1) {
//                            innerVideoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
//                                @Override
//                                public void onPrepared(MediaPlayer mp2) {
//                                    mp2.setVolume(0.0f, 0.0f);
//                                    mp2.setOnSeekCompleteListener(new MediaPlayer.OnSeekCompleteListener() {
//                                        @Override
//                                        public void onSeekComplete(MediaPlayer mp2) {
//                                            log.info("outer video pos = {}, inner video pos = {}",
//                                                    mp1.getCurrentPosition(),
//                                                    mp2.getCurrentPosition());
//                                            mp1.start();
//                                            mp2.start();
//                                        }
//                                    });
//                                    innerVideoView.seekTo(0);
//                                }
//                            });
//                            innerVideoViewVideoPath = innerVideoPath;
//                            innerVideoView.setVideoPath(innerVideoViewVideoPath);
//                        }
//                    });
//                    mp1.seekTo(outerVideoAheadOfInnerVideoByTimeMillis);
//                }
//            });
//            outerVideoViewVideoPath = outerVideoPath;
//            outerVideoView.setVideoPath(outerVideoViewVideoPath);
//        } else if (outerVideoAheadOfInnerVideoByTimeMillis < 0){
//            innerVideoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
//                @Override
//                public void onPrepared(final MediaPlayer mp1) {
//                    mp1.setVolume(0.0f, 0.0f);
//                    mp1.setOnSeekCompleteListener(new MediaPlayer.OnSeekCompleteListener() {
//                        @Override
//                        public void onSeekComplete(final MediaPlayer mp1) {
//                            outerVideoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
//                                @Override
//                                public void onPrepared(MediaPlayer mp2) {
//
//                                    mp2.setOnSeekCompleteListener(new MediaPlayer.OnSeekCompleteListener() {
//                                        @Override
//                                        public void onSeekComplete(MediaPlayer mp2) {
//                                            log.info("inner video pos = {}, outer video pos = {}",
//                                                    mp1.getCurrentPosition(),
//                                                    mp2.getCurrentPosition());
//                                            mp1.start();
//                                            mp2.start();
//                                        }
//                                    });
//                                    outerVideoView.seekTo(1);
//                                }
//                            });
//                            outerVideoViewVideoPath = outerVideoPath;
//                            outerVideoView.setVideoPath(outerVideoViewVideoPath);
//                        }
//                    });
//                    mp1.seekTo(-outerVideoAheadOfInnerVideoByTimeMillis);
//                }
//            });
//            innerVideoViewVideoPath = innerVideoPath;
//            innerVideoView.setVideoPath(innerVideoViewVideoPath);
//        } else {
//            outerVideoViewVideoPath = outerVideoPath;
//            outerVideoView.setVideoPath(outerVideoViewVideoPath);
//            innerVideoViewVideoPath = innerVideoPath;
//            innerVideoView.setVideoPath(innerVideoViewVideoPath);
//            innerVideoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
//                @Override
//                public void onPrepared(MediaPlayer mp) {
//                    mp.setVolume(0.0f, 0.0f);
//                }
//            });
//            outerVideoView.start();
//            innerVideoView.start();
//        }

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
    public void start() {
        if (outerVideoView != null && innerVideoView != null) {
            startVideoViews(outerVideoViewVideoPath, innerVideoViewVideoPath,
                    outerVideoAheadOfInnerVideoByMillis);
        }
    }

    @Override
    public void pause() {
        if (outerVideoView != null && innerVideoView != null) {
            outerVideoView.pause();
            innerVideoView.pause();
        }
    }

    @Override
    public int getDuration() {
        if (outerVideoView != null && innerVideoView != null) {
            return Math.min(outerVideoView.getDuration(), innerVideoView.getDuration());
        }
        return 0;
    }

    @Override
    public int getCurrentPosition() {
        if (outerVideoView != null && innerVideoView != null) {
            return Math.min(outerVideoView.getCurrentPosition(), innerVideoView.getCurrentPosition());
        }
        return 0;
    }

    @Override
    public void seekTo(int pos) {

    }

    @Override
    public boolean isPlaying() {
        return (outerVideoView != null && outerVideoView.isPlaying());
    }

    @Override
    public int getBufferPercentage() {
        return 0;
    }

    @Override
    public boolean canPause() {
        return true;
    }

    @Override
    public boolean canSeekBackward() {
        return true;
    }

    @Override
    public boolean canSeekForward() {
        return true;
    }

    @Override
    public int getAudioSessionId() {
        return 0;
    }
}
