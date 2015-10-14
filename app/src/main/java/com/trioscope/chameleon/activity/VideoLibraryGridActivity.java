package com.trioscope.chameleon.activity;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
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
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;


/**
 * Created by phand on 9/23/15.
 */
@Slf4j
public class VideoLibraryGridActivity extends EnableForegroundDispatchForNFCMessageActivity {
    // Newest files first comparator
    private static final Comparator<File> LAST_MODIFIED_COMPARATOR = new Comparator<File>() {
        @Override
        public int compare(File lhs, File rhs) {
            return new Long(rhs.lastModified()).compareTo(lhs.lastModified());
        }
    };

    private GestureDetectorCompat gestureDetector;
    private Executor backgroundThumbnailExecutor = Executors.newSingleThreadExecutor();
    private Set<String> mergingFilenames = new HashSet<>();
    private Map<String, VideoInfo> videoInfos = createFixedSizeLRUCache(1000);

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
        Collections.addAll(libraryFiles, folder.listFiles(new FileFilter() {
            @Override
            public boolean accept(File pathname) {
                // Show only mp4 files
                return pathname.getName().endsWith(".mp4");
            }
        }));
        libraryFiles.removeAll(videoMerger.getTemporaryFiles());
        Collections.sort(libraryFiles, LAST_MODIFIED_COMPARATOR);

        log.info("Num files in library = {}", libraryFiles.size());

        BioscopeDBHelper db = new BioscopeDBHelper(this);
        mergingFilenames = new HashSet<>(db.getVideosWithType(VideoInfoType.BEING_MERGED, "true"));
        for (String fileName : mergingFilenames) {
            File file = FileUtil.getMergedOutputFile(fileName);
            if (!libraryFiles.contains(file)) {
                log.info("File {} is being merged, but the output file hasnt yet been created. " +
                        "We want to include it anyways", file);
                libraryFiles.add(0, file);
            }
        }

        db.close();

        log.info("Showing {} library files, including {} currently being merged ({}). Files are {}",
                libraryFiles.size(), mergingFilenames.size(), mergingFilenames, libraryFiles);

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

    private static <K,V> Map<K,V> createFixedSizeLRUCache(final int maxSize) {
        return new LinkedHashMap<K,V>(maxSize * 4/3, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K,V> eldest) {
                return size() > maxSize;
            }
        };
    }

    @Builder
    @Getter
    private static class VideoInfo{
        private String title;
        private String duration;
        private long lastModified;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return gestureDetector.onTouchEvent(event);
    }

    private class LibraryGridAdapter extends ArrayAdapter<File> {
        @Setter
        private volatile int percentageMerged = 0;
        private Drawable itemDefaultBackground = getResources().getDrawable(R.drawable.direct_logo);

        public LibraryGridAdapter(Context context, List<File> objects) {
            super(context, 0, objects);
        }

        @Override
        @Timed
        public View getView(int position, View convertView, ViewGroup parent) {
            try {
                // Get the data item for this position
                final File videoFile = getItem(position);

                ViewHolder viewHolder;
                // Check if an existing view is being reused, otherwise inflate the view
                if (convertView == null) {
                    convertView = LayoutInflater.from(getContext()).inflate(R.layout.video_grid_item, parent, false);
                    viewHolder = new ViewHolder();
                    viewHolder.thumbnail = (ImageView) convertView.findViewById(R.id.video_grid_background_image);
                    viewHolder.title = (TextView) convertView.findViewById(R.id.video_grid_title);
                    viewHolder.duration = (TextView) convertView.findViewById(R.id.video_grid_duration);
                    viewHolder.age = (TextView) convertView.findViewById(R.id.video_grid_age);
                    viewHolder.shareButton = (ImageView) convertView.findViewById(R.id.video_grid_share);
                    viewHolder.progressBar = (ProgressBar) convertView.findViewById(R.id.library_progress_bar);
                    viewHolder.progressBarText = (TextView) convertView.findViewById(R.id.library_progress_text);
                    viewHolder.progresBarBackground = (ImageView) convertView.findViewById(R.id.video_grid_progress_interior);
                    convertView.setTag(viewHolder);
                } else {
                    viewHolder = (ViewHolder) convertView.getTag();
                }

                BioscopeDBHelper helper = new BioscopeDBHelper(VideoLibraryGridActivity.this);

                Bitmap thumbnail = helper.getThumbnail(videoFile.getName());

                if (thumbnail == null) {
                    // We need to generate the thumbnail, but to make scrolling smooth we do so on a separate thread
                    viewHolder.thumbnail.setImageDrawable(null);
                    backgroundThumbnailExecutor.execute(new RetrieveThumbnailRunnable(videoFile,
                            new Handler(Looper.getMainLooper())));
                } else {
                    viewHolder.thumbnail.setImageBitmap(thumbnail);
                }

                // Check if this video is being merged
                VideoMerger videoMerger = ((ChameleonApplication) getApplication()).getVideoMerger();
                if (mergingFilenames.contains(videoFile.getName())) {
                    videoMerger.setProgressUpdatable(new UpdateVideoMerge(videoFile));

                    setProgressVisible(viewHolder, true);
                    viewHolder.progressBar.setProgress(percentageMerged);
                    viewHolder.progressBarText.setText(percentageMerged + "%");

                } else {
                    setProgressVisible(viewHolder, false);

                    VideoInfo videoInfo = videoInfos.get(videoFile.getName());

                    if (videoInfo == null) {
                        // Loading video in gallery for first time, retrieve and cache info.
                        videoInfo = VideoInfo.builder()
                                .title("with " + getVideographer(videoFile, helper))
                                .duration(milliToMinutes(Double.valueOf(getVideoDuration(videoFile))))
                                .lastModified(videoFile.lastModified())
                                .build();
                        videoInfos.put(videoFile.getName(), videoInfo);
                    }

                    viewHolder.title.setText(videoInfo.getTitle());
                    viewHolder.age.setText(DateUtils.getRelativeTimeSpanString
                            (videoInfo.getLastModified(), System.currentTimeMillis(), 10).toString());
                    viewHolder.duration.setText(videoInfo.duration);

                    ImageView shareButton = viewHolder.shareButton;
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

        @Timed
        private String getVideoDuration(final File videoFile) {
            MediaMetadataRetriever mmr = new MediaMetadataRetriever();
            log.info("Video file path = {}", videoFile.getAbsolutePath());
            mmr.setDataSource(videoFile.getAbsolutePath());
            return mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
        }

        @Timed
        private String getVideographer(final File videoFile, final BioscopeDBHelper dbHelper) {
            String videoWith = "Unknown";
            //Load the other videographers from db
            List<String> videographers = dbHelper.getVideoInfo(videoFile.getName(), VideoInfoType.VIDEOGRAPHER);
            if (!videographers.isEmpty()) {
                videoWith = StringUtils.join(videographers, ", ");
            }
            return videoWith;
        }

        private void setProgressVisible(ViewHolder viewHolder, boolean isVisible) {
            int statusA = isVisible ? View.INVISIBLE : View.VISIBLE;
            int statusB = isVisible ? View.VISIBLE : View.INVISIBLE;

            viewHolder.title.setVisibility(statusA);
            viewHolder.duration.setVisibility(statusA);
            viewHolder.age.setVisibility(statusA);
            viewHolder.shareButton.setVisibility(statusA);

            viewHolder.progressBar.setVisibility(statusB);
            viewHolder.progressBarText.setVisibility(statusB);
            viewHolder.progresBarBackground.setVisibility(statusB);
        }
    }

    private static class ViewHolder {
        ImageView thumbnail;
        TextView title;
        TextView duration;
        TextView age;
        ImageView shareButton;
        ProgressBar progressBar;
        TextView progressBarText;
        ImageView progresBarBackground;
    }

    @Timed
    private Bitmap createThumbnail(File videoFile) {
        log.info("Creating thumbnail for videoFile {}", videoFile);
        return ThumbnailUtils.createVideoThumbnail(videoFile.getAbsolutePath(),
                MediaStore.Images.Thumbnails.MINI_KIND);
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
            mergingFilenames.remove(file.getName());
        }

        @Override
        public void onError() {
            // TODO
        }

        private int getPercent(double progress, double outOf) {
            return (int) Math.min(100, Math.ceil(100.0 * progress / outOf));
        }
    }

    @RequiredArgsConstructor
    private class RetrieveThumbnailRunnable implements Runnable {
        private final File videoFile;
        private final Handler handler;

        @Override
        public void run() {
            log.debug("Retrieving thumbnail for {}", videoFile);

            final Bitmap thumbnail = createThumbnail(videoFile);
            if (thumbnail != null) {

                // Store thumbnail in DB
                BioscopeDBHelper helper = new BioscopeDBHelper(VideoLibraryGridActivity.this);
                helper.insertThumbnail(videoFile.getName(), thumbnail);
                helper.close();

                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        GridView videoGrid = (GridView) findViewById(R.id.video_grid_view);
                        int start = videoGrid.getFirstVisiblePosition();
                        boolean found = false;
                        for (int i = start, j = videoGrid.getLastVisiblePosition(); i <= j; i++) {
                            if (videoFile.equals(videoGrid.getItemAtPosition(i))) {
                                // The file in question is on the screen as position i
                                View view = videoGrid.getChildAt(i - start);

                                ImageView backgroundImage = (ImageView) view.findViewById(R.id.video_grid_background_image);
                                backgroundImage.setImageBitmap(thumbnail);
                                found = true;
                                break;
                            }
                        }

                        if (!found) {
                            log.debug("Created thumbnail for {}, but image is no longer on the screen, so not setting it at the moment", videoFile);
                        }
                    }
                });

            } else {
                log.debug("Failed to create thumbnail for video = {}" + videoFile);
            }
        }
    }
}
