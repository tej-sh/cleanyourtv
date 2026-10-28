package com.gathin.tvhome;

import android.content.Context;
import android.os.storage.StorageManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Clears other apps' cached files without any special permission: asking the system for all the
 * space it could give (StorageManager.allocateBytes, API 26) makes it evict cached data to satisfy
 * that. App data and logins are not touched. The scratch file is deleted straight away.
 */
final class CacheCleaner {
    private CacheCleaner() {
    }

    /** Blocking; call off the main thread. */
    /** Returns null on success, else a short reason. */
    static String clear(Context app) {
        File tmp = new File(app.getCacheDir(), "clear.tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            StorageManager sm = app.getSystemService(StorageManager.class);
            sm.allocateBytes(out.getFD(), sm.getAllocatableBytes(StorageManager.UUID_DEFAULT));
            return null;
        } catch (IOException | RuntimeException e) {
            android.util.Log.w("CacheCleaner", "clear failed", e);
            return e.getClass().getSimpleName() + ": " + e.getMessage();
        } finally {
            tmp.delete();
        }
    }
}
