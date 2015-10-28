package com.trioscope.chameleon.util;

import android.content.Context;
import android.os.AsyncTask;
import android.os.PowerManager;

import com.trioscope.chameleon.util.DepackageUtil.Asset;
import com.trioscope.chameleon.util.merge.ProgressUpdatable;

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.DigestInputStream;
import java.security.MessageDigest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Created by phand on 10/9/15.
 */
@RequiredArgsConstructor
@Slf4j
public class DownloadAsyncTask extends AsyncTask<Asset, Integer, String> {
    private final Context context;
    private final ProgressUpdatable progressUpdatable;
    private final DepackageUtil depackageUtil;
    private PowerManager.WakeLock wakeLock;

    @Override
    protected String doInBackground(DepackageUtil.Asset... params) {
        Asset asset = params[0];
        log.info("Downloading asset {}", asset);
        InputStream input = null;
        OutputStream output = null;
        HttpURLConnection connection = null;
        String expectedMd5sum = asset.getExpectedZippedMd5();
        try {
            URL url = new URL(asset.getUrl());
            log.info("Connecting to url {}", url);
            connection = (HttpURLConnection) url.openConnection();
            connection.connect();

            // expect HTTP 200 OK, so we don't mistakenly save error report
            // instead of the file
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return "Server returned HTTP " + connection.getResponseCode()
                        + " " + connection.getResponseMessage();
            }

            // this will be useful to display download percentage
            // might be -1: server did not report the length
            int fileLength = connection.getContentLength();

            // download the file
            input = connection.getInputStream();
            File downloadOutputFile = depackageUtil.getDownloadFile(asset.getOutputName());
            output = new FileOutputStream(downloadOutputFile);

            byte data[] = new byte[4096];
            long total = 0;
            int count;
            while ((count = input.read(data)) != -1) {
                // allow canceling with back button
                if (isCancelled()) {
                    input.close();
                    return null;
                }
                total += count;
                // publishing the progress....
                if (fileLength > 0) // only if total length is known
                    publishProgress((int) (total * 100 / fileLength));
                output.write(data, 0, count);
            }


            if (asset.getOutputName().endsWith(".bz2")) {
                String unzippedOutputName = asset.getOutputName().substring(0, asset.getOutputName().length() - 4);
                File unzippedOutputFile = depackageUtil.getOutputFile(unzippedOutputName);
                log.info("Unzipping bz2 file {} to {}", output, unzippedOutputFile);
                FileOutputStream out = null;
                BZip2CompressorInputStream bzIn = null;
                try {
                    MessageDigest md = MessageDigest.getInstance("MD5");
                    FileInputStream fin = new FileInputStream(downloadOutputFile);
                    DigestInputStream dis = new DigestInputStream(fin, md);
                    BufferedInputStream in = new BufferedInputStream(dis);
                    out = new FileOutputStream(unzippedOutputFile);
                    bzIn = new BZip2CompressorInputStream(in);
                    final byte[] buffer = new byte[1024];
                    int n = 0;
                    while (-1 != (n = bzIn.read(buffer))) {
                        out.write(buffer, 0, n);
                    }
                    byte[] md5sum = md.digest();
                    out.close();
                    bzIn.close();

                    String md5sumStr = bytesToHex(md5sum);
                    log.info("Finished unzipping, closing streams, md5sum was {}", md5sumStr);
                    if (!expectedMd5sum.equals(md5sumStr)) {
                        throw new IOException(String.format("MD5Sum does not match expected %s!=%s", md5sumStr, expectedMd5sum));
                    }
                } catch (IOException e) {
                    log.error("Unable to unzip file due to error", e);
                    return e.toString();
                } finally {
                    if (out != null)
                        out.close();
                    if (bzIn != null)
                        bzIn.close();
                }
            }
        } catch (Exception e) {
            log.error("Unable to download file due to exception", e);
            return e.toString();
        } finally {
            try {
                if (output != null)
                    output.close();
                if (input != null)
                    input.close();
            } catch (IOException ignored) {
            }

            if (connection != null)
                connection.disconnect();

            log.info("Completed download and closed connection");
        }
        return null;
    }

    final protected static char[] hexArray = "0123456789abcdef".toCharArray();

    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        // take CPU lock to prevent CPU from going off if the user
        // presses the power button during download
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, getClass().getName());
        wakeLock.acquire();
    }

    @Override
    protected void onProgressUpdate(Integer... progress) {
        super.onProgressUpdate(progress);
        progressUpdatable.onProgress(progress[0], 100);
    }

    @Override
    protected void onPostExecute(String result) {
        wakeLock.release();
        if (result != null) {
            log.info("Error downloading file: {}", result);
            progressUpdatable.onError();
        } else {
            progressUpdatable.onCompleted();
        }
    }
}