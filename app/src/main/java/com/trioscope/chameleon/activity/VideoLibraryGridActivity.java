package com.trioscope.chameleon.activity;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.media.MediaMetadataRetriever;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.support.v4.view.GestureDetectorCompat;
import android.text.format.DateUtils;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.trioscope.chameleon.ChameleonApplication;
import com.trioscope.chameleon.R;
import com.trioscope.chameleon.aop.Timed;
import com.trioscope.chameleon.storage.BioscopeDBHelper;
import com.trioscope.chameleon.storage.VideoInfoType;
import com.trioscope.chameleon.util.FileUtil;
import com.trioscope.chameleon.util.merge.ProgressUpdatable;
import com.trioscope.chameleon.util.merge.VideoMerger;
import com.trioscope.chameleon.util.ui.GestureUtils;

import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;


/**
 * Created by phand on 9/23/15.
 */
@Slf4j
public class VideoLibraryGridActivity extends EnableForegroundDispatchForNFCMessageActivity {
    private GestureDetectorCompat gestureDetector;

    // Newest files first comparator
    private static final Comparator<File> LAST_MODIFIED_COMPARATOR = new Comparator<File>() {
        @Override
        public int compare(File lhs, File rhs) {
            return new Long(rhs.lastModified()).compareTo(lhs.lastModified());
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_library_grid);

        gestureDetector = new GestureDetectorCompat(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                log.info("Detected gesture");
                if (GestureUtils.isSwipeDown(e1, e2, velocityX, velocityY)) {
                    log.info("Gesture is swipe down");
                    finish();
                    overridePendingTransition(R.anim.abc_slide_in_top, R.anim.abc_slide_out_bottom);
                    return true;
                }
                return super.onFling(e1, e2, velocityX, velocityY);
            }
        });

        final ImageButton minimizeGallery = (ImageButton) findViewById(R.id.minimize_gallery);
        minimizeGallery.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                log.info("Minimizing gallery, showing MainActivity");
                finish();
            }
        });

        VideoMerger videoMerger = ((ChameleonApplication) getApplication()).getVideoMerger();
        File folder = new File(((ChameleonApplication) getApplication()).getOutputMediaDirectory());
        final List<File> libraryFiles = new ArrayList<File>();
        Collections.addAll(libraryFiles, folder.listFiles());
        libraryFiles.removeAll(videoMerger.getTemporaryFiles());
        Collections.sort(libraryFiles, LAST_MODIFIED_COMPARATOR);


        BioscopeDBHelper db = new BioscopeDBHelper(this);
        List<String> mergingFileNames = db.getVideosWithType(VideoInfoType.BEING_MERGED, "true");
        for (String fileName : mergingFileNames) {
            File file = FileUtil.getMergedOutputFile(fileName);
            if (!libraryFiles.contains(file)) {
                log.info("File {} is being merged, but the output file hasnt yet been created. We want to include it anyways", file);
                libraryFiles.add(0, file);
            }
        }
        db.close();

        log.info("Showing {} library files, including {} currently being merged ({})", libraryFiles.size(), mergingFileNames.size(), mergingFileNames);

        LibraryGridAdapter adapter = new LibraryGridAdapter(this, libraryFiles);
        GridView videoGrid = (GridView) findViewById(R.id.video_grid_view);
        videoGrid.setAdapter(adapter);

        videoGrid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                File item = (File) libraryFiles.get(position);

                Intent intentToPlayVideo = new Intent(Intent.ACTION_VIEW);
                intentToPlayVideo.setDataAndType(Uri.parse(item.getAbsolutePath()), "video/*");
                startActivityForResult(intentToPlayVideo, 1);
            }
        });
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return gestureDetector.onTouchEvent(event);
    }

    private class LibraryGridAdapter extends ArrayAdapter<File> {
        @Setter
        private volatile int percentageMerged = 0;

        public LibraryGridAdapter(Context context, List<File> objects) {
            super(context, 0, objects);
        }

        @Override
        @Timed
        public View getView(int position, View convertView, ViewGroup parent) {
            try {
                // Get the data item for this position
                final File videoFile = getItem(position);
                log.info("Getting view for videoFile {}", videoFile);

                // Check if an existing view is being reused, otherwise inflate the view
                if (convertView == null) {
                    convertView = LayoutInflater.from(getContext()).inflate(R.layout.video_grid_item, parent, false);
                }

                Typeface appFontTypeface = Typeface.createFromAsset(getAssets(), ChameleonApplication.APP_FONT_LOCATION);

                //TODO: we might want to do this only for files that exist and use some other method for currently merging videos.
                Bitmap bitmap = ThumbnailUtils.createVideoThumbnail(videoFile.getAbsolutePath(), MediaStore.Video.Thumbnails.MINI_KIND);
                if (bitmap != null) {
                    ImageView backgroundImage = (ImageView) convertView.findViewById(R.id.video_grid_background_image);
                    backgroundImage.setImageBitmap(bitmap);
                } else {
                    log.warn("Unable to create thumbnail from video file {}", videoFile);
                }

                BioscopeDBHelper helper = new BioscopeDBHelper(VideoLibraryGridActivity.this);
                List<String> isBeingMergedValues = helper.getVideoInfo(videoFile.getName(), VideoInfoType.BEING_MERGED);

                // Check if this video is being merged
                VideoMerger videoMerger = ((ChameleonApplication) getApplication()).getVideoMerger();
                if (isBeingMergedValues.size() > 0) {
                    log.info("{} is currently being merged, we'll show progress", videoFile);
                    videoMerger.setProgressUpdatable(new UpdateVideoMerge(videoFile));

                    setProgressVisible(convertView, true);
                    ProgressBar progressBar = (ProgressBar) convertView.findViewById(R.id.library_progress_bar);
                    TextView progressText = (TextView) convertView.findViewById(R.id.library_progress_text);
                    progressBar.setProgress(percentageMerged);
                    progressText.setText(percentageMerged + "%");

                } else {
                    setProgressVisible(convertView, false);

                    String videoDuration, creationDate;

                    MediaMetadataRetriever mmr = new MediaMetadataRetriever();
                    log.info("Video file path = {}", videoFile.getAbsolutePath());
                    mmr.setDataSource(videoFile.getAbsolutePath());
                    videoDuration = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                    creationDate = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE);
                    log.info("Extracted meta data videoDuration={}, creationDate={}", videoDuration, creationDate);

                    String timeAgo = DateUtils.getRelativeTimeSpanString(videoFile.lastModified(), System.currentTimeMillis(), 10).toString();
                    TextView videoAgoTextView = (TextView) convertView.findViewById(R.id.video_grid_age);
                    videoAgoTextView.setText(timeAgo);
                    videoAgoTextView.setTypeface(appFontTypeface);


                    //Load the other videographers from db
                    List<String> videographers = helper.getVideoInfo(videoFile.getName(), VideoInfoType.VIDEOGRAPHER);
                    String videoWith = "Unknown";
                    if (!videographers.isEmpty()) {
                        videoWith = StringUtils.join(videographers, ", ");
                    } else {
                        log.warn("Unable to retrieve any videographers for {}", videoFile.getName());
                    }
                    TextView videoWithTextView = (TextView) convertView.findViewById(R.id.video_grid_title);
                    videoWithTextView.setText("with " + videoWith);
                    videoWithTextView.setTypeface(appFontTypeface);

                    TextView videoDurationTextView = (TextView) convertView.findViewById(R.id.video_grid_duration);
                    videoDurationTextView.setText(milliToMinutes(Double.valueOf(videoDuration)));
                    videoDurationTextView.setTypeface(appFontTypeface);

                    ImageView shareButton = (ImageView) convertView.findViewById(R.id.video_grid_share);
                    shareButton.setFocusable(false);
                    shareButton.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            Intent shareIntent = new Intent();
                            shareIntent.setAction(Intent.ACTION_SEND);
                            shareIntent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(videoFile));
                            shareIntent.setType("video/mpeg");
                            startActivity(Intent.createChooser(shareIntent, getResources().getText(R.string.share_via)));
                        }
                    });
                }
                helper.close();

            } catch (Exception e) {
                log.warn("Failed to load view for position = {}", position);
            }

            return convertView;
        }

        private void setProgressVisible(View convertView, boolean isVisible) {
            int statusA = isVisible ? View.INVISIBLE : View.VISIBLE;
            int statusB = isVisible ? View.VISIBLE : View.INVISIBLE;

            convertView.findViewById(R.id.video_grid_title).setVisibility(statusA);
            convertView.findViewById(R.id.video_grid_duration).setVisibility(statusA);
            convertView.findViewById(R.id.video_grid_age).setVisibility(statusA);
            convertView.findViewById(R.id.video_grid_buttons).setVisibility(statusA);

            convertView.findViewById(R.id.library_progress_bar).setVisibility(statusB);
            convertView.findViewById(R.id.library_progress_text).setVisibility(statusB);
            convertView.findViewById(R.id.video_grid_progress_interior).setVisibility(statusB);
        }
    }

    @Timed
    private Bitmap getThumbnail(File videoFile) {
        log.info("Getting thumbnail for videoFile {}", videoFile);
        BioscopeDBHelper helper = new BioscopeDBHelper(this);
        Bitmap bm = helper.getThumbnail(videoFile.getName());
        helper.close();
        if (bm != null) {
            log.info("Found thumbnail in DB, using that");

            return bm;
        } else {
            log.info("Creating bitmap on the fly");

            try {
                MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                retriever.setDataSource(videoFile.getAbsolutePath());
                int timeInSeconds = 30;
                Bitmap bitmap = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);

                return bitmap;
            } catch (Exception ex) {
                log.error("Exception getting thumbnail for file {}", videoFile, ex);
            }
            return null;
        }
    }

    private String milliToMinutes(Double aDouble) {
        int seconds = (int) Math.round(aDouble / 1000.0);
        int minutes = seconds / 60;
        seconds %= 60;

        return String.format("%d:%02d", minutes, seconds);
    }

    private class UpdateVideoMerge implements ProgressUpdatable {
        private final Handler handler;
        private final File file;
        int lastPercent = 0;

        private UpdateVideoMerge(File file) {
            this.file = file;
            this.handler = new Handler(Looper.getMainLooper());
        }

        @Override
        public void onProgress(final double progress, final double outOf) {
            log.info("Progress is now {}/{}", progress, outOf);
            int percent = getPercent(progress, outOf);
            if (percent > lastPercent) {
                lastPercent = percent;
                updateVideoGridWithPercentage(percent);
            }
        }

        private void updateVideoGridWithPercentage(final int percent) {
            // TODO: Performace: We can speed this up by not calling getView but instead calling view.findViewById after finding the child

            handler.post(new Runnable() {
                @Override
                public void run() {
                    GridView videoGrid = (GridView) findViewById(R.id.video_grid_view);
                    int start = videoGrid.getFirstVisiblePosition();
                    for (int i = start, j = videoGrid.getLastVisiblePosition(); i <= j; i++) {
                        if (file.equals(videoGrid.getItemAtPosition(i))) {
                            // The file in question is on the screen as position i
                            View view = videoGrid.getChildAt(i - start);
                            // Calling getView will refresh the view
                            LibraryGridAdapter adapter = (LibraryGridAdapter) videoGrid.getAdapter();
                            adapter.setPercentageMerged(percent);
                            adapter.getView(i, view, videoGrid);
                            break;
                        }
                    }
                }
            });
        }

        @Override
        public void onCompleted() {
            log.info("Video is complete!");
            updateVideoGridWithPercentage(100);
        }

        @Override
        public void onError() {
            // TODO
        }

        private int getPercent(double progress, double outOf) {
            return (int) Math.min(100, Math.ceil(100.0 * progress / outOf));
        }
    }
}
