package com.trioscope.chameleon.storage;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.provider.BaseColumns;

import com.trioscope.chameleon.aop.Timed;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

/**
 * Created by phand on 9/30/15.
 */
@Slf4j
public class BioscopeDBHelper extends SQLiteOpenHelper {
    private static final int DATABASE_VERSION = 2;
    private static final String DATABASE_NAME = "BIOSCOPE_DB";

    private static final String VIDEO_INFO_TABLE_NAME = "video_info";
    private static final String INFO_TYPE_COL = "INFO_TYPE";
    private static final String FILE_NAME_COL = "FILE_NAME";
    private static final String INFO_VALUE_COL = "INFO_VALUE_COL";
    private static final String THUMBS_COL = "THUMBS_DATA";
    private static final String VIDEO_INFO_CREATE =
            "CREATE TABLE " + VIDEO_INFO_TABLE_NAME + " (" + BaseColumns._ID + " INTEGER PRIMARY KEY, " +
                    INFO_TYPE_COL + " INT, " +
                    FILE_NAME_COL + " TEXT, " +
                    INFO_VALUE_COL + " TEXT);";

    private static final String THUMBS_TABLE_NAME = "video_thumbs";
    private static final String THUMBS_TABLE_CREATE =
            "CREATE TABLE " + THUMBS_TABLE_NAME + " (" + BaseColumns._ID + " INTEGER PRIMARY KEY, " +
                    FILE_NAME_COL + " TEXT, " +
                    THUMBS_COL + " BLOB);";

    public BioscopeDBHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        log.info("Creating database...");
        log.info("Creating video_info table using cmds {}, {}", VIDEO_INFO_CREATE, THUMBS_TABLE_CREATE);
        db.execSQL(VIDEO_INFO_CREATE);
        db.execSQL(THUMBS_TABLE_CREATE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Version 1, no upgrades yet
        log.info("Upgrading DB from {} to {}", oldVersion, newVersion);
        if (newVersion == 2) {
            db.execSQL(THUMBS_TABLE_CREATE);
        }
    }

    @Timed
    public boolean insertThumbnail(String videoFileName, Bitmap thumbnail) {
        int size = thumbnail.getAllocationByteCount();
        ByteBuffer b = ByteBuffer.allocate(size);
        thumbnail.copyPixelsToBuffer(b);
        byte[] bytes = new byte[size];
        int bytesToRead = Math.min(bytes.length, b.remaining());
        b.get(bytes, 0, bytesToRead);

        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(FILE_NAME_COL, videoFileName);
        cv.put(THUMBS_COL, bytes);

        long rowId = db.insert(THUMBS_TABLE_NAME, null, cv);
        if (rowId != -1)
            log.info("Successfully inserted thumbnail for {}", videoFileName);
        else
            log.info("Failed to insert thumbnail for {}", videoFileName);

        return rowId != -1;
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

    public Bitmap getThumbnail(String videoFileName) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(THUMBS_TABLE_NAME, new String[]{FILE_NAME_COL}, FILE_NAME_COL + "=?", new String[]{videoFileName}, null, null, null, null);
        Bitmap bitmap = null;
        if (cursor.moveToFirst()) {
            byte[] blob = cursor.getBlob(0);
            bitmap = BitmapFactory.decodeByteArray(blob, 0, blob.length);
        } else {
            log.info("No thumbnail in DB for {}", videoFileName);
        }

        return bitmap;
    }

    public List<String> getVideoInfo(String videoFileName, VideoInfoType type) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(VIDEO_INFO_TABLE_NAME, new String[]{INFO_VALUE_COL}, FILE_NAME_COL + "=? AND " + INFO_TYPE_COL + "=?", new String[]{videoFileName, String.valueOf(type.getTypeValue())}, null, null, null, null);
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

    public void deleteVideoInfo(String videoFileName, VideoInfoType type) {
        SQLiteDatabase db = getWritableDatabase();

        int numDeletedRows = db.delete(VIDEO_INFO_TABLE_NAME, FILE_NAME_COL + "=? AND " + INFO_TYPE_COL + "=?", new String[]{videoFileName, String.valueOf(type.getTypeValue())});

        log.info("Deleted {} rows in the DB with type {} for file {}", numDeletedRows, type, videoFileName);
        return;
    }

    public List<String> getVideosWithType(VideoInfoType type, String value) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(VIDEO_INFO_TABLE_NAME, new String[]{FILE_NAME_COL}, INFO_VALUE_COL + "=? AND " + INFO_TYPE_COL + "=?", new String[]{value, String.valueOf(type.getTypeValue())}, null, null, null, null);
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
