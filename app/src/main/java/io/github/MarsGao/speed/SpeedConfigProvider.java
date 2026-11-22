package io.github.MarsGao.speed;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

/**
 * Read-only IPC bridge for hook processes. App-private SharedPreferences remain
 * the source of truth; this provider avoids direct cross-UID file access.
 */
public class SpeedConfigProvider extends ContentProvider {
    public static final String AUTHORITY = "io.github.MarsGao.speed.config";
    public static final String PATH_SPEED = "speed";
    public static final String COLUMN_SPEED = "speed";
    public static final String COLUMN_ENABLED = "enabled";
    public static final Uri SPEED_URI = Uri.parse("content://" + AUTHORITY + "/" + PATH_SPEED);
    private static final float DEFAULT_SPEED = 1.5f;
    static final String QUERY_PACKAGE = "package";

    static Uri uriForPackage(String packageName) {
        return SpeedSettings.isPackageName(packageName)
                ? SPEED_URI.buildUpon().appendQueryParameter(QUERY_PACKAGE, packageName).build()
                : SPEED_URI;
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override public android.os.Bundle call(String method, String pkg, android.os.Bundle data) {
        if (!"report".equals(method) || data == null || pkg == null) return null;
        String[] callers = getContext().getPackageManager().getPackagesForUid(android.os.Binder.getCallingUid());
        if (callers == null || !java.util.Arrays.asList(callers).contains(pkg)
                || !java.util.Arrays.asList(getContext().getResources().getStringArray(R.array.xposedscope)).contains(pkg))
            throw new SecurityException("Caller/package mismatch");
        String kind = data.getString("kind", ""), detail = data.getString("detail", "");
        Diagnostics.record(getContext(), pkg, kind, detail);
        return new android.os.Bundle();
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        if (!PATH_SPEED.equals(uri.getLastPathSegment())) {
            throw new IllegalArgumentException("Unsupported URI: " + uri);
        }
        SharedPreferences prefs = SpeedPreferences.open(getContext());
        String packageName = uri.getQueryParameter(QUERY_PACKAGE);
        if (packageName != null && !SpeedSettings.isPackageName(packageName)) {
            throw new IllegalArgumentException("Invalid package name");
        }
        float speed = SpeedSettings.resolve(prefs::getFloat, packageName, DEFAULT_SPEED);
        boolean enabledOnly = projection != null && projection.length == 1
                && COLUMN_ENABLED.equals(projection[0]);
        MatrixCursor cursor = new MatrixCursor(new String[]{enabledOnly ? COLUMN_ENABLED : COLUMN_SPEED}, 1);
        cursor.addRow(new Object[]{enabledOnly
                ? SpeedSettings.enabledValue(prefs::getFloat, packageName, 1f) : speed});
        return cursor;
    }

    @Override
    public String getType(Uri uri) {
        return "vnd.android.cursor.item/vnd." + AUTHORITY + ".speed";
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("VideoSpeed configuration is read-only");
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("VideoSpeed configuration is read-only");
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("VideoSpeed configuration is read-only");
    }
}
