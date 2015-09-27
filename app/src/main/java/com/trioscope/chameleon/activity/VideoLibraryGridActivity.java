package com.trioscope.chameleon.activity;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.media.MediaMetadataRetriever;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Bundle;
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
import android.widget.TextView;

import com.trioscope.chameleon.ChameleonApplication;
import com.trioscope.chameleon.R;
import com.trioscope.chameleon.util.ui.GestureUtils;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import lombok.extern.slf4j.Slf4j;


/**
 * Created by phand on 9/23/15.
 */
@Slf4j
public class VideoLibraryGridActivity extends EnableForegroundDispatchForNFCMessageActivity {
    private GestureDetectorCompat gestureDetector;
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
                overridePendingTransition(R.anim.abc_slide_in_top, R.anim.abc_slide_out_bottom);
            }
        });

        File folder = new File(((ChameleonApplication) getApplication()).getOutputMediaDirectory());
        final List<File> libraryFiles = Arrays.asList(folder.listFiles());

        log.info("Showing {} library files", libraryFiles.size());

        Collections.sort(libraryFiles, LAST_MODIFIED_COMPARATOR);

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

        public LibraryGridAdapter(Context context, List<File> objects) {
            super(context, 0, objects);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            // Get the data item for this position
            final File videoFile = getItem(position);

            // Check if an existing view is being reused, otherwise inflate the view
            if (convertView == null) {
                convertView = LayoutInflater.from(getContext()).inflate(R.layout.video_grid_item, parent, false);
            }

            Typeface appFontTypeface = Typeface.createFromAsset(getAssets(), ChameleonApplication.APP_FONT_LOCATION);

            Bitmap bitmap = ThumbnailUtils.createVideoThumbnail(videoFile.getAbsolutePath(), MediaStore.Video.Thumbnails.MINI_KIND);
            if (bitmap != null) {
                ImageView backgroundImage = (ImageView) convertView.findViewById(R.id.video_grid_background_image);
                backgroundImage.setImageBitmap(bitmap);
            } else {
                log.warn("Unable to create thumbnail from video file {}", videoFile);
            }

            MediaMetadataRetriever mmr = new MediaMetadataRetriever();
            mmr.setDataSource(videoFile.getAbsolutePath());

            String videoDuration = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            String creationDate = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE);
            log.info("Extracted meta data videoDuration={}, creationDate={}", videoDuration, creationDate);

            String timeAgo = DateUtils.getRelativeTimeSpanString(videoFile.lastModified(), System.currentTimeMillis(), 10).toString();
            TextView videoAgoTextView = (TextView) convertView.findViewById(R.id.video_grid_age);
            videoAgoTextView.setText(timeAgo);
            videoAgoTextView.setTypeface(appFontTypeface);

            TextView videoWithTextView = (TextView) convertView.findViewById(R.id.video_grid_title);
            videoWithTextView.setText("Video with");
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

            return convertView;
        }
    }

    private String milliToMinutes(Double aDouble) {
        int seconds = (int) Math.round(aDouble / 1000.0);
        int minutes = seconds / 60;
        seconds %= 60;

        return String.format("%d:%02d", minutes, seconds);
    }

}
