package com.trioscope.chameleon;

import android.app.NotificationManager;
import android.content.Context;

import com.trioscope.chameleon.aop.Timed;
import com.trioscope.chameleon.storage.BioscopeDBHelper;
import com.trioscope.chameleon.storage.VideoInfoType;
import com.trioscope.chameleon.types.NotificationIds;
import com.trioscope.chameleon.util.FileUtil;

import java.io.File;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Created by phand on 10/10/15.
 */
@RequiredArgsConstructor
@Slf4j
public class DestroyPartialData implements Runnable {
    private static final int MERGING_NOTIFICATION_ID = NotificationIds.MERGING_VIDEOS.getId();
    private final Context context;

    @Override
    @Timed
    public void run() {
        log.info("Cleaning up previous data");

        BioscopeDBHelper helper = new BioscopeDBHelper(context);
        List<String> videos = helper.getVideosWithType(VideoInfoType.BEING_MERGED, "true");

        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(MERGING_NOTIFICATION_ID);
        log.info("Found {} videos that are in merged state, going to delete them: {}", videos.size(), videos);

        for (String fileName : videos) {
            File file = FileUtil.getMergedOutputFile(fileName);
            if (file.exists()) {
                log.info("Deleting unfinished merge {}", file);
                file.delete();
            }

            helper.deleteAllVideoInfo(fileName);
        }

        helper.close();

        log.info("Done cleaning up previous data");
    }
}
