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
            String err = CacheCleaner.clear(app);
            final String msg = err == null ? app.getString(R.string.cleaned_up)
                    : app.getString(R.string.cleanup_partial, err);
            ui.post(() -> Toast.makeText(app, msg, Toast.LENGTH_LONG).show());
        }).start();
    }
}
