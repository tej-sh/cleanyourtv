package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Remote-friendly settings owned by the launcher; system settings stay one row away. */
public final class LauncherSettingsActivity extends Activity {
    static final String EXTRA_REORDER = "com.gathin.tvhome.extra.REORDER_FROM_SETTINGS";
    private SharedPreferences choices;
    private final TextView[] columnChoices = new TextView[4];
    private final TextView[] rowChoices = new TextView[4];
    private static final int[] DOCK_OPACITIES = {100, 70, 50, 30};
    private final TextView[] dockChoices = new TextView[DOCK_OPACITIES.length];
    private TextView wallpaperChoice;
    private TextView miButtonChoice;
    private TextView miButtonDoubleChoice;
    private TextView patchwallButtonChoice;
    private TextView patchwallButtonDoubleChoice;
    private TextView animationsChoice;
    private TextView advancedChoice;
    private TextView followThemeChoice;
    private int columns;
    private int rowsBelowDock;
    private int dockOpacity;
    private boolean animations;
    private boolean advancedEffects;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_launcher_settings);
        choices = getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE);
        Config config = Config.load(this);
        View reorderChoice = findViewById(R.id.reorderChoice);
        reorderChoice.setVisibility(config.launcherSettingsButton ? View.VISIBLE : View.GONE);
        reorderChoice.setOnClickListener(v -> {
            setResult(RESULT_OK, new Intent().putExtra(EXTRA_REORDER, true));
            finish();
        });
        columns = config.columns;
        rowsBelowDock = config.rowsBelowDock;
        dockOpacity = config.dockOpacity;
        animations = config.animations;
        advancedEffects = config.animations && config.advancedEffects;

        int[] ids = {R.id.columns4, R.id.columns5, R.id.columns6, R.id.columns7};
        for (int i = 0; i < ids.length; i++) {
            final int value = i + 4;
            columnChoices[i] = findViewById(ids[i]);
            columnChoices[i].setOnClickListener(v -> {
                if (columns == value) return;
                columns = value;
                choices.edit().putInt("columns", value).apply();
                changed();
                refresh();
            });
        }
        int[] rowIds = {R.id.rows0, R.id.rows1, R.id.rows2, R.id.rows3};
        for (int i = 0; i < rowIds.length; i++) {
            final int value = i;
            rowChoices[i] = findViewById(rowIds[i]);
            rowChoices[i].setOnClickListener(v -> {
                if (rowsBelowDock == value) return;
                rowsBelowDock = value;
                choices.edit().putInt(Config.ROWS_BELOW_DOCK_KEY, value).apply();
                changed();
                refresh();
            });
        }
        int[] dockIds = {R.id.dock100, R.id.dock70, R.id.dock50, R.id.dock30};
        for (int i = 0; i < dockIds.length; i++) {
            final int value = DOCK_OPACITIES[i];
            dockChoices[i] = findViewById(dockIds[i]);
            dockChoices[i].setOnClickListener(v -> {
                if (dockOpacity == value) return;
                dockOpacity = value;
                choices.edit().putInt(Config.DOCK_OPACITY_KEY, value).apply();
                changed();
                refresh();
            });
        }
        // Keep the numeric choices increasing in the Hebrew reading direction. Reorder the
        // actual views so D-pad focus follows their positions without changing the saved values.
        if (getResources().getBoolean(R.bool.rtl_app_grid)) {
            reverseRow(columnChoices);
            reverseRow(rowChoices);
            reverseRow(dockChoices);
        }
        wallpaperChoice = findViewById(R.id.wallpaperChoice);
        wallpaperChoice.setOnClickListener(v -> showWallpaperPicker());
        miButtonChoice = findViewById(R.id.miButtonChoice);
        miButtonChoice.setOnClickListener(v -> showButtonRemapPicker(
                RemoteKeys.MI_BUTTON_KEY, RemoteKeys.ACTION_HOME, R.string.mi_button_title));
        miButtonDoubleChoice = findViewById(R.id.miButtonDoubleChoice);
        miButtonDoubleChoice.setOnClickListener(v -> showButtonRemapPicker(
                RemoteKeys.MI_BUTTON_KEY + "_double", RemoteKeys.ACTION_NONE, R.string.mi_button_double_title));
        patchwallButtonChoice = findViewById(R.id.patchwallButtonChoice);
        patchwallButtonChoice.setOnClickListener(v -> showButtonRemapPicker(
                RemoteKeys.PATCHWALL_BUTTON_KEY, RemoteKeys.ACTION_NONE, R.string.patchwall_button_title));
        patchwallButtonDoubleChoice = findViewById(R.id.patchwallButtonDoubleChoice);
        patchwallButtonDoubleChoice.setOnClickListener(v -> showButtonRemapPicker(
                RemoteKeys.PATCHWALL_BUTTON_KEY + "_double", RemoteKeys.ACTION_NONE, R.string.patchwall_button_double_title));
        animationsChoice = findViewById(R.id.animationsChoice);
        animationsChoice.setOnClickListener(v -> {
            animations = !animations;
            if (!animations) advancedEffects = false;
            choices.edit().putBoolean("animations", animations)
                    .putBoolean("advanced_effects", advancedEffects).apply();
            changed();
            refresh();
        });
        advancedChoice = findViewById(R.id.advancedChoice);
        advancedChoice.setOnClickListener(v -> {
            if (advancedEffects) {
                setAdvanced(false);
            } else {
                new AlertDialog.Builder(this)
                        .setTitle(R.string.advanced_effects)
                        .setMessage(R.string.advanced_effects_warning)
                        .setPositiveButton(R.string.enable, (dialog, which) -> setAdvanced(true))
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            }
        });
        followThemeChoice = findViewById(R.id.followThemeChoice);
        followThemeChoice.setOnClickListener(v -> {
            if (!hasOnTvChoices()) return;
            choices.edit().remove("columns").remove(Config.ROWS_BELOW_DOCK_KEY).remove("animations")
                    .remove("advanced_effects").remove(Config.WALLPAPER_CHOICE_KEY)
                    .remove(Config.DOCK_OPACITY_KEY)
                    .remove(RemoteKeys.MI_BUTTON_KEY)
                    .remove(RemoteKeys.MI_BUTTON_KEY + "_double")
                    .remove(RemoteKeys.PATCHWALL_BUTTON_KEY)
                    .remove(RemoteKeys.PATCHWALL_BUTTON_KEY + "_double").commit();
            Config current = Config.load(this);
            columns = current.columns;
            rowsBelowDock = current.rowsBelowDock;
            dockOpacity = current.dockOpacity;
            animations = current.animations;
            advancedEffects = current.animations && current.advancedEffects;
            changed();
            refresh();
        });
        findViewById(R.id.defaultHomeChoice).setOnClickListener(v -> {
            Toast.makeText(this, R.string.home_settings_help, Toast.LENGTH_LONG).show();
            open(new Intent(Settings.ACTION_HOME_SETTINGS));
        });
        findViewById(R.id.tvSettingsChoice).setOnClickListener(v ->
                open(SystemSettings.intent(this, Settings.ACTION_SETTINGS)));
        refresh();
        if (config.launcherSettingsButton) reorderChoice.requestFocus();
        else columnChoices[Math.max(0, Math.min(columns - 4, 3))].requestFocus();
    }

    private void reverseRow(TextView[] views) {
        LinearLayout row = (LinearLayout) views[0].getParent();
        row.removeAllViews();
        for (int i = views.length - 1; i >= 0; i--) row.addView(views[i]);
    }

    private void showButtonRemapPicker(String prefKey, String defaultAction, int titleRes) {
        String current = choices.getString(prefKey, defaultAction);
        String[] options = {
                getString(R.string.remap_action_home),
                getString(R.string.remap_action_cleanup),
                getString(R.string.remap_action_app),
                getString(R.string.remap_action_none)
        };
        int checked = RemoteKeys.ACTION_HOME.equals(current) ? 0
                : RemoteKeys.ACTION_CLEANUP.equals(current) ? 1
                : current != null && current.startsWith(RemoteKeys.ACTION_APP_PREFIX) ? 2
                : 3;
        new AlertDialog.Builder(this)
                .setTitle(titleRes)
                .setSingleChoiceItems(options, checked, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == 0) {
                        saveButtonRemap(prefKey, RemoteKeys.ACTION_HOME);
                    } else if (which == 1) {
                        saveButtonRemap(prefKey, RemoteKeys.ACTION_CLEANUP);
                    } else if (which == 2) {
                        showAppPickerForButton(prefKey);
                    } else {
                        saveButtonRemap(prefKey, RemoteKeys.ACTION_NONE);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showAppPickerForButton(String prefKey) {
        PackageManager pm = getPackageManager();
        Map<String, String> apps = new LinkedHashMap<>();
        Intent leanback = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        for (ResolveInfo ri : pm.queryIntentActivities(leanback, 0)) {
            String pkg = ri.activityInfo.packageName;
            if (!getPackageName().equals(pkg)) {
                CharSequence label = ri.loadLabel(pm);
                apps.put(pkg, label != null ? label.toString() : pkg);
            }
        }
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        for (ResolveInfo ri : pm.queryIntentActivities(main, 0)) {
            String pkg = ri.activityInfo.packageName;
            if (!getPackageName().equals(pkg) && !apps.containsKey(pkg)) {
                CharSequence label = ri.loadLabel(pm);
                apps.put(pkg, label != null ? label.toString() : pkg);
            }
        }
        List<Map.Entry<String, String>> sorted = new ArrayList<>(apps.entrySet());
        Collator collator = Collator.getInstance();
        Collections.sort(sorted, (a, b) -> collator.compare(a.getValue(), b.getValue()));

        String[] labels = new String[sorted.size()];
        for (int i = 0; i < sorted.size(); i++) labels[i] = sorted.get(i).getValue();

        String current = choices.getString(prefKey, "");
        String currentPkg = current.startsWith(RemoteKeys.ACTION_APP_PREFIX)
                ? current.substring(RemoteKeys.ACTION_APP_PREFIX.length()) : "";
        int checked = -1;
        for (int i = 0; i < sorted.size(); i++) {
            if (sorted.get(i).getKey().equals(currentPkg)) {
                checked = i;
                break;
            }
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.remap_pick_app)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    saveButtonRemap(prefKey, RemoteKeys.ACTION_APP_PREFIX + sorted.get(which).getKey());
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void saveButtonRemap(String prefKey, String action) {
        choices.edit().putString(prefKey, action).apply();
        changed();
        refresh();
        String miAction = choices.getString(RemoteKeys.MI_BUTTON_KEY, RemoteKeys.ACTION_HOME);
        String pwAction = choices.getString(RemoteKeys.PATCHWALL_BUTTON_KEY, RemoteKeys.ACTION_NONE);
        if (!RemoteKeys.ACTION_HOME.equals(miAction) && !RemoteKeys.ACTION_HOME.equals(pwAction)) {
            Toast.makeText(this, R.string.remap_home_tip, Toast.LENGTH_LONG).show();
        }
    }

    private String formatRemapState(String action) {
        if (RemoteKeys.ACTION_HOME.equals(action)) return getString(R.string.remap_action_home);
        if (RemoteKeys.ACTION_CLEANUP.equals(action)) return getString(R.string.remap_action_cleanup);
        if (action != null && action.startsWith(RemoteKeys.ACTION_APP_PREFIX)) {
            String pkg = action.substring(RemoteKeys.ACTION_APP_PREFIX.length());
            PackageManager pm = getPackageManager();
            try {
                CharSequence label = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0));
                if (label != null) return label.toString();
            } catch (PackageManager.NameNotFoundException ignored) {
            }
            return pkg;
        }
        return getString(R.string.remap_action_none);
    }

    private void showWallpaperPicker() {
        File dir = Config.dir(this);
        List<File> images = new java.util.ArrayList<>(WallpaperRotation.images(
                dir == null ? null : new File(dir, "wallpapers")));
        // A theme with a single wallpaper.jpg offers that picture too (Config maps the choice back to it).
        File single = dir == null ? null : new File(dir, Config.WALLPAPER_NAME);
        if (single != null && single.isFile()) images.add(0, single);
        String[] labels = new String[images.size() + 1];
        for (int i = 0; i < images.size(); i++) labels[i] = images.get(i).getName();
        labels[images.size()] = getString(R.string.wallpaper_none);
        String current = choices.getString(Config.WALLPAPER_CHOICE_KEY, null);
        int checked = "none".equals(current) ? images.size()
                : indexOf(images, current);
        new AlertDialog.Builder(this)
                .setTitle(R.string.wallpaper_label)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == images.size()) {
                        choices.edit().putString(Config.WALLPAPER_CHOICE_KEY, "none").apply();
                    } else {
                        choices.edit().putString(Config.WALLPAPER_CHOICE_KEY,
                                images.get(which).getName()).apply();
                    }
                    changed();
                    Toast.makeText(this, R.string.wallpaper_applied, Toast.LENGTH_SHORT).show();
                    refresh();
                })
                .setNeutralButton(R.string.wallpaper_theme_default, (dialog, which) -> {
                    choices.edit().remove(Config.WALLPAPER_CHOICE_KEY).apply();
                    changed();
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static int indexOf(List<File> images, String name) {
        if (name == null) return -1;
        for (int i = 0; i < images.size(); i++) {
            if (images.get(i).getName().equals(name)) return i;
        }
        return -1;
    }

    /**
     * A choice was saved. Home rebuilds when it next resumes because the change count moved, whether these
     * settings are left with Back or with the remote's Home button (which cancels the activity result).
     */
    private void changed() {
        setResult(RESULT_OK);
        choices.edit().putLong(Config.SETTINGS_REVISION_KEY,
                choices.getLong(Config.SETTINGS_REVISION_KEY, 0) + 1).apply();
    }

    private void setAdvanced(boolean enabled) {
        advancedEffects = enabled;
        choices.edit().putBoolean("advanced_effects", enabled).apply();
        changed();
        refresh();
    }

    private void refresh() {
        for (int i = 0; i < columnChoices.length; i++) {
            columnChoices[i].setSelected(columns == i + 4);
        }
        for (int i = 0; i < rowChoices.length; i++) {
            rowChoices[i].setSelected(rowsBelowDock == i);
        }
        for (int i = 0; i < dockChoices.length; i++) {
            dockChoices[i].setSelected(dockOpacity == DOCK_OPACITIES[i]);
        }
        animationsChoice.setText(getString(R.string.animations_state,
                getString(animations ? R.string.on : R.string.off)));
        advancedChoice.setText(getString(R.string.advanced_effects_state,
                getString(advancedEffects ? R.string.on : R.string.off)));
        advancedChoice.setEnabled(animations);
        advancedChoice.setAlpha(animations ? 1f : 0.5f);
        String wallpaperCurrent = choices.getString(Config.WALLPAPER_CHOICE_KEY, null);
        wallpaperChoice.setText(getString(R.string.wallpaper_state,
                "none".equals(wallpaperCurrent) ? getString(R.string.wallpaper_none)
                        : wallpaperCurrent != null ? wallpaperCurrent
                        : getString(R.string.wallpaper_theme_default)));
        String miCurrent = choices.getString(RemoteKeys.MI_BUTTON_KEY, RemoteKeys.ACTION_HOME);
        miButtonChoice.setText(getString(R.string.mi_button_state, formatRemapState(miCurrent)));
        String miDoubleCurrent = choices.getString(RemoteKeys.MI_BUTTON_KEY + "_double", RemoteKeys.ACTION_NONE);
        miButtonDoubleChoice.setText(getString(R.string.mi_button_double_state, formatRemapState(miDoubleCurrent)));
        String pwCurrent = choices.getString(RemoteKeys.PATCHWALL_BUTTON_KEY, RemoteKeys.ACTION_NONE);
        patchwallButtonChoice.setText(getString(R.string.patchwall_button_state, formatRemapState(pwCurrent)));
        String pwDoubleCurrent = choices.getString(RemoteKeys.PATCHWALL_BUTTON_KEY + "_double", RemoteKeys.ACTION_NONE);
        patchwallButtonDoubleChoice.setText(getString(R.string.patchwall_button_double_state, formatRemapState(pwDoubleCurrent)));
        followThemeChoice.setSelected(!hasOnTvChoices());
    }

    private boolean hasOnTvChoices() {
        return choices.contains("columns") || choices.contains(Config.ROWS_BELOW_DOCK_KEY)
                || choices.contains("animations") || choices.contains("advanced_effects")
                || choices.contains(Config.WALLPAPER_CHOICE_KEY) || choices.contains(Config.DOCK_OPACITY_KEY)
                || choices.contains(RemoteKeys.MI_BUTTON_KEY) || choices.contains(RemoteKeys.MI_BUTTON_KEY + "_double")
                || choices.contains(RemoteKeys.PATCHWALL_BUTTON_KEY) || choices.contains(RemoteKeys.PATCHWALL_BUTTON_KEY + "_double");
    }

    private void open(Intent intent) {
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG).show();
        }
    }
}
