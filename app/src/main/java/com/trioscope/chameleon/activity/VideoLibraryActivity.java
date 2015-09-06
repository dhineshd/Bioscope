package com.trioscope.chameleon.activity;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.trioscope.chameleon.ChameleonApplication;
import com.trioscope.chameleon.R;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class VideoLibraryActivity extends EnableForegroundDispatchForNFCMessageActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_library);

        final ListView libListview = (ListView) findViewById(R.id.libListView);


        File folder = new File(((ChameleonApplication)getApplication()).getOutputMediaDirectory());
        File[] arrayOfFiles = folder.listFiles();



        final CustomArrayAdapter adapter = new CustomArrayAdapter(this,
                android.R.layout.simple_list_item_1, arrayOfFiles);

        libListview.setAdapter(adapter);

        libListview.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                File item = (File) libListview.getAdapter().getItem(position);

                Intent intentToPlayVideo = new Intent(Intent.ACTION_VIEW);
                intentToPlayVideo.setDataAndType(Uri.parse(item.getAbsolutePath()), "video/*");
                startActivity(intentToPlayVideo);
            }
        });
    }

    private class CustomArrayAdapter extends ArrayAdapter<File> {

        Context context;

        Map<File, Integer> mIdMap = new HashMap<>();
        File[] values;

        public CustomArrayAdapter(Context context, int textViewResourceId,
                                  File[] objects) {
            super(context, textViewResourceId, objects);

            this.context = context;
            this.values = objects;

            for (int i = 0; i < objects.length; ++i) {
                this.mIdMap.put(objects[i], i);
            }
        }

        @Override
        public long getItemId(int position) {
            File item = getItem(position);
            return mIdMap.get(item);
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LayoutInflater inflater = (LayoutInflater) context
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);

            View rowView = inflater.inflate(R.layout.video_library_row_view, parent, false);
            TextView textView = (TextView) rowView.findViewById(R.id.lib_name_of_vid);
            ImageView imageView = (ImageView) rowView.findViewById(R.id.lib_video_thumbnail);
            TextView libVideoDate = (TextView) rowView.findViewById(R.id.lib_vid_date);
            TextView libVideoDuration = (TextView) rowView.findViewById(R.id.lib_vid_duration);

            String fileName = values[position].getName();

            String[] fileNames = fileName.split("\\.");

            if(fileNames.length > 0) {
                fileName = fileNames[0];
            }
            textView.setText(fileName);

            Bitmap bitmap = ThumbnailUtils.createVideoThumbnail(values[position].getAbsolutePath(), MediaStore.Video.Thumbnails.MINI_KIND);

            if(bitmap != null) {
                imageView.setImageBitmap(bitmap);
            } else {
                imageView.setImageResource(R.drawable.video_file);
            }

            final int position_final = position;
            ImageButton shareButton = (ImageButton) rowView.findViewById(R.id.lib_share_button);
            shareButton.setFocusable(false);
            shareButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent shareIntent = new Intent();
                    shareIntent.setAction(Intent.ACTION_SEND);
                    File mergedFile = values[position_final];
                    shareIntent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(mergedFile));
                    shareIntent.setType("image/jpeg");
                    startActivity(Intent.createChooser(shareIntent, getResources().getText(R.string.share_via)));
                }
            });

            MediaMetadataRetriever mmr = new MediaMetadataRetriever();
            mmr.setDataSource(values[position].getAbsolutePath());

            String videoDuration = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            String creationDate = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE);


            libVideoDuration.setText(convertToMinutesAndSeconds(videoDuration));
            libVideoDate.setText(creationDate);

            return rowView;
        }

        private String convertToMinutesAndSeconds(String durationInMillis) {

            long millis = 0;
            try {
                millis = Long.parseLong(durationInMillis);
            }
            catch (Exception e) {
                log.warn("Exception while parsing string", e);
            }

            if(TimeUnit.MILLISECONDS.toHours(millis) > 0) {
                return String.format("%d:%02d:%02d",
                        TimeUnit.MILLISECONDS.toHours(millis),
                        TimeUnit.MILLISECONDS.toMinutes(millis) -
                                TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS.toHours(millis))
                        ,
                        TimeUnit.MILLISECONDS.toSeconds(millis) -
                                TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(millis))
                );
            }

            return String.format("%02d:%02d",
                        TimeUnit.MILLISECONDS.toMinutes(millis),
                        TimeUnit.MILLISECONDS.toSeconds(millis) -
                                TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(millis))
            );
        }

    }

}
