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
        // Ask for a little less than the limit: free space shifts between the query and the allocation
        // (the system rejected a request 12 KB over), so retry with a larger safety margin.
        long[] margins = {16L << 20, 128L << 20};
        String reason = "unknown";
        try {
            StorageManager sm = app.getSystemService(StorageManager.class);
            long room = sm.getAllocatableBytes(StorageManager.UUID_DEFAULT);
            for (long margin : margins) {
                try (FileOutputStream out = new FileOutputStream(tmp)) {
                    sm.allocateBytes(out.getFD(), Math.max(room - margin, 0));
                    return null;
                } catch (IOException e) {
                    android.util.Log.w("CacheCleaner", "clear failed", e);
                    reason = e.getClass().getSimpleName() + ": " + e.getMessage();
                } finally {
                    tmp.delete();
                }
            }
        } catch (IOException | RuntimeException e) {
            android.util.Log.w("CacheCleaner", "clear failed", e);
            reason = e.getClass().getSimpleName() + ": " + e.getMessage();
        }
        return reason;
    }
}
