package com.trioscope.chameleon.util;

import android.content.Context;

import com.trioscope.chameleon.util.merge.ProgressUpdatable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

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

    public boolean downloadAsset(Asset asset, ProgressUpdatable progressUpdatable) {
        if (asset.getUrl() == null) {
            log.info("No URL set for asset {}", asset);
            return true;
        }

        log.info("Requested to download {}", asset.getUrl());

        if (hasDownloaded(asset)) {
            log.info("Asset already downloaded, not going to download again {}", asset);
            return true;
        }

        DownloadAsyncTask downloadTask = new DownloadAsyncTask(context, progressUpdatable, this);

        downloadTask.execute(asset);

        return true;
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

    public File getDownloadFile(String outputName) {
        return new File(context.getExternalFilesDir(null).getPath() + File.separator + outputName);
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

    public boolean hasDownloaded(Asset asset) {
        File file = getOutputFile(asset.getOutputName());

        if (file.getAbsolutePath().endsWith(".bz2")) {
            file = getOutputFile(asset.getOutputName().substring(0, asset.getOutputName().length() - 4));
        }

        if (file.exists()) {
            // File exists, check the md5 too
            String md5sum = getMd5Sum(file);

            if (md5sum.equals(asset.getExpectedMd5())) {
                log.info("Md5sum {} matches for file {}", md5sum, file);
                return true;
            } else {
                log.info("Md5sum {} doesnt match {} for file {}", md5sum, asset.getExpectedMd5(), file);
                return false;
            }

        } else {
            log.info("File doesnt exist, so it has not been downloaded");
            return false;
        }
    }

    public String getMd5Sum(File f) {
        log.info("Calculating md5 for {}", f);
        InputStream is = null;
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            is = new FileInputStream(f);
            is = new DigestInputStream(is, md);
            while (is.read() != -1) ;
            byte[] digest = md.digest();
            String md5sum = bytesToHex(digest);
            log.info("Calculated md5sum {} for {}", md5sum, f);
            return md5sum;
        } catch (FileNotFoundException e) {
            log.warn("File not found, cant calculate MD5", e);
        } catch (NoSuchAlgorithmException e) {
            log.warn("MD5 algorithm not detected", e);
        } catch (IOException e) {
            log.warn("Error reading file, cant calculate MD5", e);
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                }
            }
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
}
