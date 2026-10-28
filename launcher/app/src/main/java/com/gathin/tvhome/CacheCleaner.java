package com.gathin.tvhome;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.storage.StorageManager;
import android.widget.Toast;

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

    static void run(Context context) {
        final Context app = context.getApplicationContext();
        final Handler ui = new Handler(Looper.getMainLooper());
        Toast.makeText(app, R.string.clearing_caches, Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            boolean ok = false;
            File tmp = new File(app.getCacheDir(), "clear.tmp");
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                StorageManager sm = app.getSystemService(StorageManager.class);
                sm.allocateBytes(out.getFD(), sm.getAllocatableBytes(StorageManager.UUID_DEFAULT));
                ok = true;
            } catch (IOException | RuntimeException ignored) {
            } finally {
                tmp.delete();
            }
            final int msg = ok ? R.string.caches_cleared : R.string.not_available;
            ui.post(() -> Toast.makeText(app, msg, Toast.LENGTH_LONG).show());
        }).start();
    }
}
