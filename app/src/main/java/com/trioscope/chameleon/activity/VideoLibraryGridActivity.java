package com.trioscope.chameleon.activity;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.media.MediaMetadataRetriever;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.view.GestureDetectorCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.format.DateUtils;
import android.util.LruCache;
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

import com.google.gson.Gson;
import com.trioscope.chameleon.ChameleonApplication;
import com.trioscope.chameleon.R;
import com.trioscope.chameleon.aop.Timed;
import com.trioscope.chameleon.storage.BioscopeDBHelper;
import com.trioscope.chameleon.storage.VideoInfoType;
import com.trioscope.chameleon.types.RecordingMetadata;
import com.trioscope.chameleon.util.FileUtil;
import com.trioscope.chameleon.util.merge.MergeConfiguration;
import com.trioscope.chameleon.util.merge.ProgressUpdatable;
import com.trioscope.chameleon.util.merge.VideoConfiguration;
import com.trioscope.chameleon.util.merge.VideoMerger;
import com.trioscope.chameleon.util.ui.GestureUtils;

import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;


/**
 * Created by phand on 9/23/15.
 */
@Slf4j
public class VideoLibraryGridActivity extends AppCompatActivity {
    // Newest files first comparator
    private static final Comparator<File> LAST_MODIFIED_COMPARATOR = new Comparator<File>() {
        @Override
        public int compare(File lhs, File rhs) {
            return new Long(rhs.lastModified()).compareTo(lhs.lastModified());
        }
    };

    public static final String MAJOR_VIDEO_PATH_KEY = "MAJOR_VIDEO_PATH_KEY";
    public static final String MINOR_VIDEO_PATH_KEY = "MINOR_VIDEO_PATH_KEY";
    public static final String VIDEO_START_OFFSET_KEY = "VIDEO_START_OFFSET_KEY";
    public static final String VIDEO_MERGE_LAYOUT_KEY = "VIDEO_MERGE_LAYOUT_KEY";

    private GestureDetectorCompat gestureDetector;
    private Executor backgroundThumbnailExecutor = Executors.newSingleThreadExecutor();
    private Map<String, Boolean> mergingFilenamesMap = new ConcurrentHashMap<>();// presence of filename in this map means video is currently merging
    private LruCache<String, VideoInfo> videoInfoCache;
    private LruCache<String, Bitmap> thumbnailCache;
    private Typeface appFontTypefaceRegular;
    private Typeface appFontTypefaceBold;
    private List<File> libraryFiles = new ArrayList<>();
    private CacheVideoInfoTask cacheVideoInfoTask;
    private Map<String, Integer> fileNameToPercentMerged = new ConcurrentHashMap<>();
    private Gson gson = new Gson();

    public void updatePercentMerged(String filename, int percent) {
        fileNameToPercentMerged.put(filename, percent);
    }

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
                    minimizeGallery();
                    return true;
                }
                return super.onFling(e1, e2, velocityX, velocityY);
            }
        });

        final ImageView settingsButton = (ImageView) findViewById(R.id.settings_button);

        settingsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(VideoLibraryGridActivity.this, PreferencesActivity.class);
                startActivity(i);
            }
        });

        final ImageButton minimizeGallery = (ImageButton) findViewById(R.id.minimize_gallery);
        minimizeGallery.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                log.info("Minimizing gallery, showing MainActivity");
                minimizeGallery();
            }
        });

        VideoMerger videoMerger = ((ChameleonApplication) getApplication()).getVideoMerger();
        File folder = FileUtil.getOutputMediaDirectory();
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
        List<String> mergingFilenames = db.getVideosWithType(VideoInfoType.BEING_MERGED, "true");
        if(mergingFilenames != null && !mergingFilenames.isEmpty()) {
            for(String name : mergingFilenames) {
                mergingFilenamesMap.put(name, Boolean.TRUE);
            }
        }
        for (String fileName : mergingFilenamesMap.keySet()) {
            File file = FileUtil.getMergedOutputFile(fileName);
            if (!libraryFiles.contains(file)) {
                log.info("File {} is being merged, but the output file hasnt yet been created. " +
                        "We want to include it anyways", file);
                libraryFiles.add(0, file);
                updatePercentMerged(file.getName(), 0);
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
                File item = libraryFiles.get(position);
                // Don't allow user to click videos being merged
                if (!mergingFilenamesMap.containsKey(item.getName())) {
                    Intent intentToPlayVideo = new Intent(Intent.ACTION_VIEW);
                    intentToPlayVideo.setDataAndType(Uri.parse("file://" + item.getAbsolutePath()), "video/*");
                    startActivity(intentToPlayVideo);
                } else {

                    VideoMerger merger = ((ChameleonApplication) getApplication()).getVideoMerger();

                    VideoConfiguration majorVideoConfig = merger.getMajorVideo(item.getName());
                    VideoConfiguration minorVideoConfig = merger.getMinorVideo(item.getName());
                    MergeConfiguration mergeConfiguration = merger.getMergeConfiguration(item.getName());

                    if(majorVideoConfig != null && majorVideoConfig.getFile()!= null &&
                            minorVideoConfig != null && minorVideoConfig.getFile() != null &&
                            mergeConfiguration != null) {

                        Long videoStartOffset = mergeConfiguration.getVideoStartOffsetMilli();

                        if(videoStartOffset == null) {
                            videoStartOffset = 0L;
                        }

                        //The video is currently being merged, show the individual videos instead.
                        Intent intent = new Intent(getApplicationContext(), CustomPreviewActivity.class);
                        intent.putExtra(VideoLibraryGridActivity.MAJOR_VIDEO_PATH_KEY, majorVideoConfig.getFile().getAbsolutePath());
                        intent.putExtra(VideoLibraryGridActivity.MINOR_VIDEO_PATH_KEY, minorVideoConfig.getFile().getAbsolutePath());
                        intent.putExtra(VideoLibraryGridActivity.VIDEO_START_OFFSET_KEY, videoStartOffset);
                        intent.putExtra(VideoLibraryGridActivity.VIDEO_MERGE_LAYOUT_KEY, mergeConfiguration.getMergeLayoutType());
                        startActivity(intent);
                    }
                }
            }
        });

        appFontTypefaceRegular = Typeface.createFromAsset(getAssets(), ChameleonApplication.APP_REGULAR_FONT_LOCATION);
        appFontTypefaceBold = Typeface.createFromAsset(getAssets(), ChameleonApplication.APP_BOLD_FONT_LOCATION);

        // Get max available VM memory, exceeding this amount will throw an
        // OutOfMemory exception. Stored in kilobytes as LruCache takes an
        // int in its constructor.
        final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);

        // Use 1/8th of the available memory for this memory cache.
        final int cacheSize = maxMemory / 8;

        thumbnailCache = new LruCache<String, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                // The cache size will be measured in kilobytes rather than
                // number of items.
                return bitmap.getByteCount() / 1024;
            }
        };

        videoInfoCache = new LruCache<>(1000);
    }

    private void minimizeGallery() {
        finish();
        overridePendingTransition(R.anim.slide_in_top, R.anim.slide_out_bottom);
    }

    private class CacheVideoInfoTask extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(Void... params) {

            try {
                // Retrieve info for all library files
                BioscopeDBHelper helper = new BioscopeDBHelper(VideoLibraryGridActivity.this);
                for (final File file : libraryFiles) {

                    // Task cancelled
                    if (isCancelled()) {
                        break;
                    }

                    if (file != null) {
                        getVideoInfo(file, helper);
                    }
                }
                helper.close();
            } catch (Exception e) {
                log.warn("Failed to cache video info", e);
            }

            return null;
        }
    }

    ;

    @Builder
    @Getter
    private static class VideoInfo {
        private String title;
        private String duration;
        private long lastModified;
    }


    @Override
    protected void onPause() {
        super.onPause();
        log.info("onPause invoked!");
        if (cacheVideoInfoTask != null) {
            cacheVideoInfoTask.cancel(true);
            cacheVideoInfoTask = null;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        log.info("onResume invoked!");

        // Cache video info to save time when loading video in library
        if (cacheVideoInfoTask == null) {
            cacheVideoInfoTask = new CacheVideoInfoTask();
            cacheVideoInfoTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return gestureDetector.onTouchEvent(event);
    }

    private class LibraryGridAdapter extends ArrayAdapter<File> {
        @Setter
        private VideoMerger videoMerger = ((ChameleonApplication) getApplication()).getVideoMerger();



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
                    convertView = inflateView(parent);
                    viewHolder = createViewHolder(convertView);
                    convertView.setTag(viewHolder);
                } else {
                    viewHolder = (ViewHolder) convertView.getTag();
                }

                BioscopeDBHelper helper = new BioscopeDBHelper(VideoLibraryGridActivity.this);

                Bitmap thumbnail = thumbnailCache.get(videoFile.getName());

                if (thumbnail == null) {
                    // We need to generate the thumbnail, but to make scrolling smooth we do so on a separate thread
                    viewHolder.thumbnail.setImageDrawable(null);
                    backgroundThumbnailExecutor.execute(new RetrieveThumbnailRunnable(videoFile,
                            new Handler(Looper.getMainLooper())));
                } else {
                    viewHolder.thumbnail.setImageBitmap(thumbnail);
                }

                // Check if this video is being merged
                if (mergingFilenamesMap.containsKey(videoFile.getName())) {
                    videoMerger.addProgressUpdateable(videoFile.getName(), new UpdateVideoMerge(videoFile));
                    int percentMerged = fileNameToPercentMerged.get(videoFile.getName()) == null? 0 : fileNameToPercentMerged.get(videoFile.getName());
                    viewHolder.progressBar.setProgress(percentMerged);
                    viewHolder.progressBarText.setText(percentMerged + "%");
                    viewHolder.progressBarText.setTypeface(appFontTypefaceBold);
                    setProgressVisible(viewHolder, true);
                } else {
                    setProgressVisible(viewHolder, false);
                    updateUIElements(videoFile, getVideoInfo(videoFile, helper), viewHolder);
                }
                helper.close();

            } catch (Exception e) {
                log.warn("Failed to load view for position = {}", position);
            }

            return convertView;
        }

        @Timed
        private View inflateView(ViewGroup parent) {
            return LayoutInflater.from(getContext()).inflate(R.layout.video_grid_item, parent, false);
        }

        @Timed
        private ViewHolder createViewHolder(final View view) {
            ViewHolder viewHolder = new ViewHolder();
            viewHolder.thumbnail = (ImageView) view.findViewById(R.id.video_grid_background_image);
            viewHolder.title = (TextView) view.findViewById(R.id.video_grid_title);
            viewHolder.duration = (TextView) view.findViewById(R.id.video_grid_duration);
            viewHolder.age = (TextView) view.findViewById(R.id.video_grid_age);
            viewHolder.shareButton = (ImageButton) view.findViewById(R.id.video_grid_share);
            viewHolder.progressBar = (ProgressBar) view.findViewById(R.id.library_progress_bar);
            viewHolder.progressBarText = (TextView) view.findViewById(R.id.library_progress_text);
            return viewHolder;
        }

        @Timed
        private void updateUIElements(
                final File videoFile,
                final VideoInfo videoInfo,
                final ViewHolder viewHolder) {
            viewHolder.title.setText(videoInfo.getTitle());
            viewHolder.title.setTypeface(appFontTypefaceBold);

            viewHolder.age.setText(DateUtils.getRelativeTimeSpanString
                    (videoInfo.getLastModified(), System.currentTimeMillis(), 10).toString());
            viewHolder.age.setTypeface(appFontTypefaceRegular);

            viewHolder.duration.setText(videoInfo.duration);
            viewHolder.duration.setTypeface(appFontTypefaceRegular);

            viewHolder.shareButton.setFocusable(false);
            viewHolder.shareButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent shareIntent = new Intent();
                    shareIntent.setAction(Intent.ACTION_SEND);
                    shareIntent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(videoFile));
                    shareIntent.setType("video/mpeg");
                    startActivity(Intent.createChooser(shareIntent,
                            getResources().getText(R.string.share_via)));
                }
            });
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
        }
    }


    @Timed
    private VideoInfo getVideoInfo(final File videoFile, final BioscopeDBHelper dbHelper) {
        VideoInfo videoInfo = videoInfoCache.get(videoFile.getName());
        if (videoInfo == null) {
            // Loading video in gallery for first time, retrieve and cache info.

            String videographer = getVideographer(videoFile, dbHelper);
            String title = "";
            if(!videographer.isEmpty()) {
                title = "with " + videographer;
            }
            videoInfo = VideoInfo.builder()
                    .title(title)
                    .duration(milliToMinutes(Double.valueOf(getVideoDuration(videoFile))))
                    .lastModified(videoFile.lastModified())
                    .build();
            videoInfoCache.put(videoFile.getName(), videoInfo);
        }
        return videoInfo;
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
        String videoWith = "";
        //Load the other videographers from db
        List<String> videographers = dbHelper.getVideoInfo(videoFile.getName(), VideoInfoType.VIDEOGRAPHER);
        if (!videographers.isEmpty()) {
            videoWith = StringUtils.join(videographers, ", ");
        }
        return videoWith;
    }

    // Not using getter/setter or Lombok for optimization
    private static class ViewHolder {
        ImageView thumbnail;
        TextView title;
        TextView duration;
        TextView age;
        ImageButton shareButton;
        ProgressBar progressBar;
        TextView progressBarText;
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

        return String.format("%02d:%02d", minutes, seconds);
    }

    private class UpdateVideoMerge implements ProgressUpdatable {
        private final Handler handler;
        private final File file;

        private UpdateVideoMerge(File file) {
            this.file = file;
            this.handler = new Handler(Looper.getMainLooper());
        }

        @Override
        public void onProgress(final double progress, final double outOf) {
            log.info("Progress is now {}/{}", progress, outOf);
            int percent = getPercent(progress, outOf);
            int lastPercent = fileNameToPercentMerged.get(file.getName()) == null ? 0 : fileNameToPercentMerged.get(file.getName());
            log.info("percent is {}, lastPercent is {}.", percent, lastPercent);
            if (percent > lastPercent) {
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
                    log.info("firstvisiblie {}; lastvisible {}", start, videoGrid.getLastVisiblePosition());
                    for (int i = start, j = videoGrid.getLastVisiblePosition(); i <= j; i++) {
                        if (file.equals(videoGrid.getItemAtPosition(i))) {
                            // The file in question is on the screen as position i
                            View view = videoGrid.getChildAt(i - start);
                            // Calling getView will refresh the view
                            LibraryGridAdapter adapter = (LibraryGridAdapter) videoGrid.getAdapter();
                            updatePercentMerged(file.getName(), percent);
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
            mergingFilenamesMap.remove(file.getName());
            fileNameToPercentMerged.remove(file.getName());
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

    @RequiredArgsConstructor
    private class RetrieveThumbnailRunnable implements Runnable {
        private final File videoFile;
        private final Handler handler;

        @Override
        public void run() {
            log.debug("Retrieving thumbnail for {}", videoFile);

            BioscopeDBHelper helper = new BioscopeDBHelper(VideoLibraryGridActivity.this);
            Bitmap dbThumbnail = helper.getThumbnail(videoFile.getName());
            Bitmap createdThumbnail = null;
            if (dbThumbnail == null) {
                createdThumbnail = createThumbnail(videoFile);
                helper.insertThumbnail(videoFile.getName(), createdThumbnail);
            }
            helper.close();
            final Bitmap thumbnail = (dbThumbnail != null) ? dbThumbnail : createdThumbnail;
            if (thumbnail != null) {
                thumbnailCache.put(videoFile.getName(), thumbnail);
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
