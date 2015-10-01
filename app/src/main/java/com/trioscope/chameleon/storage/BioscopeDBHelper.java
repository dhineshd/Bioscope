package com.trioscope.chameleon.storage;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BaseColumns;

import java.util.ArrayList;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

/**
 * Created by phand on 9/30/15.
 */
@Slf4j
public class BioscopeDBHelper extends SQLiteOpenHelper {
    private static final int DATABASE_VERSION = 1;
    private static final String DATABASE_NAME = "BIOSCOPE_DB";

    private static final String VIDEO_INFO_TABLE_NAME = "video_info";
    private static final String INFO_TYPE_COL = "INFO_TYPE";
    private static final String FILE_NAME_COL = "FILE_NAME";
    private static final String INFO_VALUE_COL = "INFO_VALUE_COL";
    private static final String VIDEO_INFO_CREATE =
            "CREATE TABLE " + VIDEO_INFO_TABLE_NAME + " (" + BaseColumns._ID + " INTEGER PRIMARY KEY, " +
                    INFO_TYPE_COL + " INT, " +
                    FILE_NAME_COL + " TEXT, " +
                    INFO_VALUE_COL + " TEXT);";

    public BioscopeDBHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        log.info("Creating database...");
        log.info("Creating video_info table using cmd {}", VIDEO_INFO_CREATE);
        db.execSQL(VIDEO_INFO_CREATE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Version 1, no upgrades yet
    }

    public boolean insertVideoInfo(String videoFileName, VideoInfoType type, String value) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(INFO_TYPE_COL, type.getTypeValue());
        cv.put(FILE_NAME_COL, videoFileName);
        cv.put(INFO_VALUE_COL, value);

        long rowId = db.insert(VIDEO_INFO_TABLE_NAME, null, cv);
        if (rowId != -1)
            log.info("Successfully inserted {}={} for {}", type, value, videoFileName);
        else
            log.info("Failed to insert {}={} for {}", type, value, videoFileName);

        return rowId != -1;
    }


    public List<String> getVideoInfo(String videoFileName, VideoInfoType type) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(VIDEO_INFO_TABLE_NAME, new String[]{INFO_VALUE_COL}, FILE_NAME_COL + "=? AND " + INFO_TYPE_COL + "=?", new String[]{videoFileName, type.getTypeValue() + ""}, null, null, null, null);
        List<String> result = new ArrayList<>();
        if (cursor.moveToFirst()) {
            while (!cursor.isAfterLast()) {
                result.add(cursor.getString(0));
                cursor.moveToNext();
            }
        }

        cursor.close();
        return result;
    }
}
