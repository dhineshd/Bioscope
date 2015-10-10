package com.trioscope.chameleon.util;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;

import com.trioscope.chameleon.types.ThreadWithHandler;
import com.trioscope.chameleon.util.merge.ProgressUpdatable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Util to depackage assets from APK to storage directory
 * <p/>
 * Created by phand on 7/1/15.
 */
@RequiredArgsConstructor
@Slf4j
public class DepackageUtil {
    private static final Logger LOG = LoggerFactory.getLogger(DepackageUtil.class);
    private static final String DPKG_DIR_NAME = "dpkg";
    private final Context context;

    // TODO: Make this threadsafe so we can download multiple assets at once
    private long downloadId;
    private DownloadManager manager;

    public boolean downloadAsset(String assetUrl, String outputName, ProgressUpdatable progressUpdatable, String expectedMd5sum) {
        log.info("Using download manager to download {}", assetUrl);

        String directoryPath = getOutputDirectory().getPath();
        if (outputName.endsWith(".bz2")) {
            String unzippedFileName = directoryPath + File.separator + outputName.substring(0, outputName.length() - 4);
            File f = new File(unzippedFileName);
            if (f.exists()) {
                log.info("File {} already exists, not going to download", unzippedFileName);
                return true;
            }
        }


        DownloadAsyncTask downloadTask = new DownloadAsyncTask(context, progressUpdatable);

        String outputFileName = directoryPath + File.separator + outputName;
        downloadTask.execute(assetUrl, outputFileName, expectedMd5sum);

        return true;
    }

    private void registerBroadcastReceiver() {
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                log.info("Received ACTION_DOWNLOAD_COMPLETE intent: {}", intent);
                String action = intent.getAction();
                if (DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(action)) {
                    log.info("Intent was a completed download");
                    DownloadManager.Query query = new DownloadManager.Query();
                    query.setFilterById(downloadId);
                    Cursor c = manager.query(query);
                    if (c.moveToFirst()) {
                        int columnIndex = c.getColumnIndex(DownloadManager.COLUMN_STATUS);
                        if (DownloadManager.STATUS_SUCCESSFUL == c.getInt(columnIndex)) {
                            log.info("Download completed successfully");
                            synchronized (DepackageUtil.this) {
                                DepackageUtil.this.notifyAll();
                            }
                        }
                    }
                }
            }


        };

        context.registerReceiver(receiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), null, new ThreadWithHandler().getHandler());
    }

    public boolean depackageAsset(String assetName, String outputName) {
        File outputDir = getOutputDirectory();
        LOG.info("Acquired directory {} for depackaging", outputDir);

        File outputFile = new File(outputDir.getPath() + File.separator + outputName);

        if (outputFile.exists()) {
            log.info("Output file {} already exists - not going to depackage again", outputFile.getAbsolutePath());
            return true;
        }

        // Open your local db as the input stream
        InputStream myInput = null;
        try {
            myInput = context.getAssets().open(assetName);
            // Path to the just created empty db
            // Open the empty db as the output stream
            OutputStream myOutput = new FileOutputStream(outputFile);
            // transfer bytes from the inputfile to the outputfile
            byte[] buffer = new byte[1024];
            int length;
            while ((length = myInput.read(buffer)) > 0) {
                myOutput.write(buffer, 0, length);
            }
            // Close the streams
            myOutput.flush();
            myOutput.close();
            myInput.close();

            String chmodCmd = "/system/bin/chmod 744 " + outputFile.getPath();
            LOG.info("Making file executable with {}", chmodCmd);
            Process p = Runtime.getRuntime().exec(chmodCmd);
            int exitCode = p.waitFor();
            LOG.info("Chmod command returned with exit code {}", exitCode);
            if (exitCode != 0)
                return false;
        } catch (IOException e) {
            LOG.warn("Unable to depackage asset {}", assetName, e);
            return false;
        } catch (InterruptedException e) {
            LOG.warn("Unable to make asset {} executable", assetName, e);
            return false;
        }

        LOG.info("Successfully depackaged {} to {}", assetName, outputName);
        return true;
    }

    public File getOutputFile(String name) {
        File outputDir = getOutputDirectory();
        File outputFile = new File(outputDir.getPath() + File.separator + name);

        return outputFile;
    }

    public File getOutputDirectory() {
        File outputDir = context.getDir(DPKG_DIR_NAME, Context.MODE_PRIVATE);

        return outputDir;
    }

    public boolean hasDownloaded(String outputName) {
        String fileName = context.getExternalFilesDir(null).getPath() + File.separator + outputName;

        if (fileName.endsWith(".bz2")) {
            fileName = getOutputDirectory().getPath() + File.separator + outputName.substring(0, outputName.length() - 4);
        }

        return new File(fileName).exists();
    }
}
