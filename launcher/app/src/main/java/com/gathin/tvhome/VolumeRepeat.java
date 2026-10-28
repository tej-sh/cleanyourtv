package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * volume_repeat_hold (Launcher settings, off by default): on firmware where a key-filtering helper
 * costs the remote's auto-repeat, drop key filtering for a short window when the volume changes, so
 * the TV's own repeat can ramp, then put the filter back. Remapped buttons do not work while the
 * window is open. The window extends while the volume keeps changing. FILTER_WINDOW_VOLUME (marker).
 */
final class VolumeRepeat {
    static final String TAG = "VolumeRepeat";
    static final String PREF_ENABLED = "volume_repeat_hold";
    private static final long INITIAL_WINDOW_MS = 3000;
    private static final long EXTEND_MS = 1000;
    private static final long MAX_WINDOW_MS = 10000;

    private static final Handler HANDLER = new Handler(Looper.getMainLooper());
    private static final List<VolumeRepeat> INSTANCES = new ArrayList<>();
    private static final Runnable CLOSE = VolumeRepeat::closeWindow;
    private static boolean open;
    private static long openedAt;
    private static VolumeRepeat owner;

    private final AccessibilityService service;
    private final ContentObserver observer = new ContentObserver(HANDLER) {
        @Override
        public void onChange(boolean selfChange, Uri uri) {
            String key = uri == null ? null : uri.getLastPathSegment();
            if (key == null || key.toLowerCase(Locale.ROOT).contains("volume")) {
                Log.i(TAG, "settings change: " + uri);
                onVolumeChanged();
            }
        }
    };
    private boolean observing;
    private boolean keyFilter;
    private boolean enabled;

    VolumeRepeat(AccessibilityService service) {
        this.service = service;
        INSTANCES.add(this);
        Log.i(TAG, "created " + service.getClass().getSimpleName());
    }

    /** Call from loadConfig after RemoteKeys.applyKeyFilter. */
    void onConfig(boolean keyFilter) {
        this.keyFilter = keyFilter;
        SharedPreferences prefs = service.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
        enabled = prefs.getBoolean(PREF_ENABLED, true);
        if (!keyFilter) {
            stopObserving();
            if (open) closeWindow();
            return;
        }
        if (keyFilter) {
            if (!observing) {
                try {
                    service.getContentResolver().registerContentObserver(
                            Settings.System.CONTENT_URI, true, observer);
                    observing = true;
                    Log.i(TAG, "observing volume changes");
                } catch (SecurityException denied) {
                    Log.w(TAG, "cannot observe volume settings: " + denied);
                }
            }
            if (open) applyFilter(false); else applyFilter(true);
        }
        if (!enabled && open) closeWindow();
    }

    private void onVolumeChanged() {
        if (!enabled || !keyFilter) return;
        SharedPreferences prefs = service.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
        enabled = prefs.getBoolean(PREF_ENABLED, true);
        if (!enabled) return;
        if (open && owner != null && owner != this) return;
        if (open && owner == null) owner = this;
        long now = SystemClock.elapsedRealtime();
        boolean wasOpen = open;
        if (!wasOpen) {
            owner = this;
            open = true;
            openedAt = now;
            setFilter(false);
            Log.i(TAG, "window open: key filtering off for " + INITIAL_WINDOW_MS + " ms");
        }
        HANDLER.removeCallbacks(CLOSE);
        long left = MAX_WINDOW_MS - (now - openedAt);
        if (left <= 0) {
            closeWindow();
            return;
        }
        HANDLER.postDelayed(CLOSE, wasOpen ? Math.min(EXTEND_MS, left) : Math.min(INITIAL_WINDOW_MS, left));
    }

    private static void setFilter(boolean on) {
        for (VolumeRepeat r : new ArrayList<>(INSTANCES)) r.applyFilter(on);
    }

    private void applyFilter(boolean on) {
        if (!keyFilter) return;
        AccessibilityServiceInfo info = service.getServiceInfo();
        if (info == null) return;
        if (on) {
            info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
        } else {
            info.flags &= ~AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
        }
        service.setServiceInfo(info);
    }

    private static void closeWindow() {
        HANDLER.removeCallbacks(CLOSE);
        if (!open) return;
        open = false;
        owner = null;
        setFilter(true);
        Log.i(TAG, "window close: key filtering on");
    }

    private void stopObserving() {
        if (!observing) return;
        try {
            service.getContentResolver().unregisterContentObserver(observer);
        } catch (IllegalStateException ignored) {
        }
        observing = false;
    }

    /** onUnbind/onDestroy: drop the observer and never leave a filter off. */
    void destroy() {
        stopObserving();
        if (owner == this) owner = null;
        INSTANCES.remove(this);
        if (INSTANCES.isEmpty()) {
            HANDLER.removeCallbacks(CLOSE);
            if (open) {
                open = false;
                Log.i(TAG, "window close: no helpers left");
            }
        } else if (open) {
            applyFilter(true);
        }
    }
}
