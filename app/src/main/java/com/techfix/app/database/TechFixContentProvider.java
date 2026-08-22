package com.techfix.app.database;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * ContentProvider over the appointments table — the structured, external-facing
 * face of TechFix's offline SQLite store (Content Providers deliverable).
 *
 * URIs:
 *   content://com.techfix.app.providers/appointments          → all appointments
 *   content://com.techfix.app.providers/appointments/{id}     → one appointment
 *   content://com.techfix.app.providers/appointments/user/{userId} → one customer's repairs
 */
public class TechFixContentProvider extends ContentProvider {
    public static final String AUTHORITY = "com.techfix.app.providers";
    public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/appointments");

    private static final int MATCH_ALL = 1;
    private static final int MATCH_ONE = 2;
    private static final int MATCH_USER = 3;

    private static final UriMatcher MATCHER = new UriMatcher(UriMatcher.NO_MATCH);
    static {
        MATCHER.addURI(AUTHORITY, "appointments", MATCH_ALL);
        MATCHER.addURI(AUTHORITY, "appointments/#", MATCH_ONE);
        MATCHER.addURI(AUTHORITY, "appointments/user/#", MATCH_USER);
    }

    private DatabaseHelper helper;

    @Override
    public boolean onCreate() {
        helper = DatabaseHelper.getInstance(getContext());
        return true;
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection,
                        @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        SQLiteDatabase db = helper.getReadableDatabase();
        Cursor cursor;
        switch (MATCHER.match(uri)) {
            case MATCH_ONE:
                cursor = db.query("appointments", projection,
                        "id=" + ContentUris.parseId(uri),
                        null, null, null, sortOrder == null ? "id DESC" : sortOrder);
                break;
            case MATCH_USER:
                cursor = db.query("appointments", projection,
                        "user_id=" + uri.getLastPathSegment(),
                        null, null, null, sortOrder == null ? "id DESC" : sortOrder);
                break;
            case MATCH_ALL:
                cursor = db.query("appointments", projection, selection,
                        selectionArgs, null, null, sortOrder == null ? "id DESC" : sortOrder);
                break;
            default:
                throw new IllegalArgumentException("Unknown URI: " + uri);
        }
        if (getContext() != null) cursor.setNotificationUri(getContext().getContentResolver(), uri);
        return cursor;
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        switch (MATCHER.match(uri)) {
            case MATCH_ONE: return "vnd.android.cursor.item/vnd." + AUTHORITY + ".appointment";
            case MATCH_ALL:
            case MATCH_USER: return "vnd.android.cursor.dir/vnd." + AUTHORITY + ".appointment";
            default: throw new IllegalArgumentException("Unknown URI: " + uri);
        }
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        if (MATCHER.match(uri) != MATCH_ALL) throw new IllegalArgumentException("Insert requires /appointments");
        long id = helper.getWritableDatabase().insert("appointments", null, values);
        if (id <= 0 || getContext() == null) return null;
        Uri result = ContentUris.withAppendedId(CONTENT_URI, id);
        getContext().getContentResolver().notifyChange(result, null);
        getContext().getContentResolver().notifyChange(CONTENT_URI, null);
        return result;
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection,
                      @Nullable String[] selectionArgs) {
        SQLiteDatabase db = helper.getWritableDatabase();
        int count;
        switch (MATCHER.match(uri)) {
            case MATCH_ONE:
                count = db.update("appointments", values, "id=" + ContentUris.parseId(uri), null);
                break;
            case MATCH_ALL:
                count = db.update("appointments", values, selection, selectionArgs);
                break;
            default:
                throw new IllegalArgumentException("Update requires /appointments or /appointments/{id}");
        }
        if (count > 0 && getContext() != null) getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
        SQLiteDatabase db = helper.getWritableDatabase();
        int count;
        switch (MATCHER.match(uri)) {
            case MATCH_ONE:
                count = db.delete("appointments", "id=" + ContentUris.parseId(uri), null);
                break;
            case MATCH_ALL:
                count = db.delete("appointments", selection, selectionArgs);
                break;
            default:
                throw new IllegalArgumentException("Delete requires /appointments or /appointments/{id}");
        }
        if (count > 0 && getContext() != null) getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }
}
