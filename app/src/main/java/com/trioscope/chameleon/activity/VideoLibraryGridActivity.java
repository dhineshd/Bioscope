package com.trioscope.chameleon.activity;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.media.ThumbnailUtils;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v4.app.ActivityOptionsCompat;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;

import com.trioscope.chameleon.ChameleonApplication;
import com.trioscope.chameleon.R;

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

    private static final Comparator<File> LAST_MODIFIED_COMPARATOR = new Comparator<File>() {
        @Override
        public int compare(File lhs, File rhs) {
            return new Long(lhs.lastModified()).compareTo(rhs.lastModified());
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_library_grid);

        final ImageView minimizeGallery = (ImageView) findViewById(R.id.minimize_gallery);
        minimizeGallery.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                log.info("Minimizing gallery, showing MainActivity");
                Bundle options = ActivityOptionsCompat.makeCustomAnimation(VideoLibraryGridActivity.this, R.anim.abc_slide_in_top, R.anim.abc_slide_out_bottom).toBundle();
                Intent i = new Intent(VideoLibraryGridActivity.this, MainActivity.class);
                i.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(i, options);
            }
        });

        File folder = new File(((ChameleonApplication) getApplication()).getOutputMediaDirectory());
        List<File> libraryFiles = Arrays.asList(folder.listFiles());

        Collections.sort(libraryFiles, LAST_MODIFIED_COMPARATOR);

        LibraryGridAdapter adapter = new LibraryGridAdapter(this, libraryFiles);
        GridView videoGrid = (GridView) findViewById(R.id.video_grid_view);
        videoGrid.setAdapter(adapter);
    }

    private class LibraryGridAdapter extends ArrayAdapter<File> {

        public LibraryGridAdapter(Context context, List<File> objects) {
            super(context, 0, objects);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            // Get the data item for this position
            File videoFile = getItem(position);

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

            String timeAgo = DateUtils.getRelativeTimeSpanString(videoFile.lastModified(), System.currentTimeMillis(), 10).toString();
            TextView videoAgoTextView = (TextView) convertView.findViewById(R.id.video_grid_age);
            videoAgoTextView.setText(timeAgo);
            videoAgoTextView.setTypeface(appFontTypeface);

            TextView videoWithTextView = (TextView) convertView.findViewById(R.id.video_grid_title);
            videoWithTextView.setText("Video with");
            videoWithTextView.setTypeface(appFontTypeface);

            TextView videoDurationTextView = (TextView) convertView.findViewById(R.id.video_grid_duration);
            videoDurationTextView.setText("1:42");
            videoDurationTextView.setTypeface(appFontTypeface);

            return convertView;
        }
    }

}
