package com.gathin.tvhome;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

/** The Home "Clean up" tile: stop background apps, then clear caches. */
final class Cleanup {
    private Cleanup() {
    }

    static void run(Context context) {
        final Context app = context.getApplicationContext();
        final Handler ui = new Handler(Looper.getMainLooper());
        Toast.makeText(app, R.string.cleaning_up, Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            BackgroundKiller.killAll(app);
            final int msg = CacheCleaner.clear(app) ? R.string.cleaned_up : R.string.not_available;
            ui.post(() -> Toast.makeText(app, msg, Toast.LENGTH_LONG).show());
        }).start();
    }
}
