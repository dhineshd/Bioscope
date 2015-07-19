package com.trioscope.chameleon.util;

import android.content.Context;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import lombok.RequiredArgsConstructor;

/**
 * Util to depackage assets from APK to storage directory
 * <p/>
 * Created by phand on 7/1/15.
 */
@RequiredArgsConstructor
public class DepackageUtil {
    private static final Logger LOG = LoggerFactory.getLogger(DepackageUtil.class);
    private static final String DPKG_DIR_NAME = "dpkg";
    private final Context context;

    public boolean depackageAsset(String assetName, String outputName) {
        // TODO: Perform this as an async task

        // Open your local db as the input stream
        InputStream myInput = null;
        try {
            myInput = context.getAssets().open(assetName);
            // Path to the just created empty db
            File outputDir = getOutputDirectory();
            LOG.info("Acquired directory {} for depackaging", outputDir);

            File outputFile = new File(outputDir.getPath() + File.separator + outputName);
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
}
