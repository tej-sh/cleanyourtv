package com.gathin.tvhome;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;

/**
 * Stops every other app's background processes (KILL_BACKGROUND_PROCESSES, a normal permission), like
 * swiping them out of recents. The launcher is skipped; apps still in the foreground are left alone.
 */
final class BackgroundKiller {
    private BackgroundKiller() {
    }

    /** Blocking; call off the main thread. */
    static void killAll(Context app) {
        ActivityManager am = (ActivityManager) app.getSystemService(Context.ACTIVITY_SERVICE);
        for (ApplicationInfo ai : app.getPackageManager().getInstalledApplications(0)) {
            if (!ai.packageName.equals(app.getPackageName())) am.killBackgroundProcesses(ai.packageName);
        }
    }
}
