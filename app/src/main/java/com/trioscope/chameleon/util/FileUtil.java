package com.trioscope.chameleon.util;

import android.os.Environment;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.TrueFileFilter;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.regex.Pattern;

import lombok.extern.slf4j.Slf4j;

/**
 * Created by phand on 7/8/15.
 */
@Slf4j
public class FileUtil {

    private static final File CPU_PROC = new File("/proc/cpuinfo");
    private static final Pattern NEON_FEATURE_PATTERN = Pattern.compile("Features.*neon.*");

    public static File getDCIMDirectory() {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
    }

    public static void printDirectoryContents(File dir, boolean recursive) {
        log.info("Files inside {}; recursive={}", dir.getAbsolutePath(), recursive);
        Collection<File> files;
        if (recursive)
            files = FileUtils.listFiles(dir, TrueFileFilter.INSTANCE, TrueFileFilter.INSTANCE);
        else
            files = Arrays.asList(dir.listFiles());

        for (File f : files) {
            log.info("  {}", f.getAbsolutePath());
        }
    }

    public static File getMergedOutputFile(String fileName) {
        File base = getDCIMDirectory();
        File mergedOutputDirectory = getMergedOutputDirectory();

        File result = new File(mergedOutputDirectory, fileName);
        log.info("Created merged output file {}", result.getPath());
        return result;
    }

    private static File getMergedOutputDirectory() {
        File mergedOutputDirectory = new File(getDCIMDirectory(), "Bioscope/");

        // Create the storage directory if it does not exist
        if (!mergedOutputDirectory.exists()) {
            if (!mergedOutputDirectory.mkdirs()) {
                log.error("Failed to create directory");
                throw new RuntimeException("Failed to create directory " + mergedOutputDirectory.getPath());
            }
        }
        return mergedOutputDirectory;
    }

    public static boolean checkForNeonProcessorSupport() {
        try {
            BufferedReader br = new BufferedReader(new FileReader(CPU_PROC));
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();

                if (NEON_FEATURE_PATTERN.matcher(line).matches()) {
                    log.info("CPU {}", line);
                    return true;
                }
            }
            br.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return false;
    }
}
