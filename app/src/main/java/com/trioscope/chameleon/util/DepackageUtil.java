package com.trioscope.chameleon.util;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;

import com.trioscope.chameleon.types.ThreadWithHandler;

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
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

    public boolean downloadAsset(String assetUrl, String outputName) {
        log.info("Using download manager to download {}", assetUrl);


        if (outputName.endsWith(".bz2")) {
            String unzippedFileName = getOutputDirectory().getPath() + File.separator + outputName.substring(0, outputName.length() - 4);
            File f = new File(unzippedFileName);
            if (f.exists()) {
                log.info("File {} already exists, not going to download", unzippedFileName);
                return true;
            }
        }

        registerBroadcastReceiver();

        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(assetUrl));
        request.setDescription("Downloading OpenH264 for H.264 encoding");
        request.setTitle("Downloading encoding library");
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.setDestinationInExternalFilesDir(context, null, outputName);

        // get download service and enqueue file
        log.info("Retrieving download manager and enqueueing request to download to {}", context.getExternalFilesDir(null));
        manager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        downloadId = manager.enqueue(request);

        // TODO: Take this off UI thread
        try {
            synchronized (this) {
                this.wait();
            }
        } catch (InterruptedException e) {
            log.error("Error waiting for download");
            return false;
        }

        if (outputName.endsWith(".bz2")) {
            String fileName = context.getExternalFilesDir(null).getPath() + File.separator + outputName;
            String unzippedFileName = getOutputDirectory().getPath() + File.separator + outputName.substring(0, outputName.length() - 4);
            log.info("Unzipping bz2 file {} to {}", fileName, unzippedFileName);
            try {
                FileInputStream fin = new FileInputStream(fileName);
                BufferedInputStream in = new BufferedInputStream(fin);
                FileOutputStream out = new FileOutputStream(unzippedFileName);
                BZip2CompressorInputStream bzIn = new BZip2CompressorInputStream(in);
                final byte[] buffer = new byte[1024];
                int n = 0;
                while (-1 != (n = bzIn.read(buffer))) {
                    out.write(buffer, 0, n);
                }
                log.info("Finished unzipping, closing streams");
                out.close();
                bzIn.close();
            } catch (IOException e) {
                log.error("Unable to unzip file due to error", e);
                return false;
            }
        }

        log.info("Listing depackage directory: ");
        File dir = new File("/data/data/com.trioscope.chameleon/app_dpkg/");
        for (File f : dir.listFiles()) {
            log.info("File: {}", f);
        }

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
            log.info("Output file {} already exists - not going to depackage again");
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
