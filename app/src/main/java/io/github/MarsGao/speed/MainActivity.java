package io.github.MarsGao.speed;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private SharedPreferences prefs;
    private AppAdapter appAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        prefs = SpeedPreferences.open(this);
        if (!prefs.contains(SpeedSettings.GLOBAL_KEY)) {
            prefs.edit().putFloat(SpeedSettings.GLOBAL_KEY, SpeedSettings.DEFAULT_SPEED).commit();
        }
        makePrefsReadable();

        EditText globalInput = findViewById(R.id.editTextText);
        globalInput.setText(formatSpeed(effectiveSpeed(null)));
        Button saveGlobal = findViewById(R.id.button);
        saveGlobal.setOnClickListener(view -> {
            Float speed = readInput(globalInput);
            if (speed != null && save(prefs.edit().putFloat(SpeedSettings.GLOBAL_KEY, speed))) {
                globalInput.setText(formatSpeed(speed));
                Toast.makeText(this, R.string.global_saved, Toast.LENGTH_SHORT).show();
            }
        });

        appAdapter = new AppAdapter(loadApps());
        ListView apps = findViewById(R.id.appSpeedList);
        apps.setAdapter(appAdapter);
        apps.setEmptyView(findViewById(R.id.emptyApps));
        apps.setOnItemClickListener((parent, view, position, id) -> editApp(appAdapter.getItem(position)));
        EditText search = findViewById(R.id.appSearch);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                appAdapter.filter(s.toString());
            }
            @Override public void afterTextChanged(Editable s) { }
        });
    }

    private List<AppEntry> loadApps() {
        String[] packages = getResources().getStringArray(R.array.xposedscope);
        String[] labels = getResources().getStringArray(R.array.supported_app_labels);
        List<AppEntry> apps = new ArrayList<>();
        PackageManager manager = getPackageManager();
        for (int i = 0; i < packages.length; i++) {
            String label = i < labels.length ? labels[i] : packages[i];
            try {
                label = manager.getApplicationLabel(manager.getApplicationInfo(packages[i], 0)).toString();
            } catch (PackageManager.NameNotFoundException | RuntimeException ignored) {
                // Keep uninstalled supported apps configurable using the bundled label.
            }
            apps.add(new AppEntry(packages[i], label));
        }
        return apps;
    }

    private float effectiveSpeed(String packageName) {
        return SpeedSettings.resolve(prefs::getFloat, packageName, SpeedSettings.DEFAULT_SPEED);
    }

    private Float readInput(EditText input) {
        try {
            float speed = Float.parseFloat(input.getText().toString().trim());
            if (SpeedSettings.isValid(speed)) return speed;
        } catch (NumberFormatException ignored) {
            // Keep the editor open and show an inline validation error.
        }
        input.setError(getString(R.string.invalid_speed));
        return null;
    }

    private boolean save(SharedPreferences.Editor editor) {
        if (!editor.commit()) {
            Toast.makeText(this, R.string.save_failed, Toast.LENGTH_SHORT).show();
            return false;
        }
        makePrefsReadable();
        if (appAdapter != null) appAdapter.notifyDataSetChanged();
        return true;
    }

    private void editApp(AppEntry app) {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(24);
        content.setPadding(padding, dp(12), padding, dp(8));
        TextView packageName = new TextView(this);
        packageName.setText(app.packageName);
        packageName.setTextSize(12);
        content.addView(packageName);

        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setSelectAllOnFocus(true);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setHint(R.string.speed_hint);
        input.setContentDescription(getString(R.string.edit_app_speed, app.label));
        input.setText(formatSpeed(effectiveSpeed(app.packageName)));
        content.addView(input, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));
        TextView help = new TextView(this);
        help.setText(R.string.app_speed_help);
        help.setTextSize(13);
        content.addView(help);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.edit_app_speed, app.label))
                .setView(content)
                .setPositiveButton(R.string.save, null)
                .setNeutralButton(R.string.use_global, null)
                .setNegativeButton(R.string.cancel, null)
                .create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                Float speed = readInput(input);
                if (speed != null && save(prefs.edit().putFloat(SpeedSettings.appKey(app.packageName), speed))) {
                    dialog.dismiss();
                    Toast.makeText(this, getString(R.string.app_saved, app.label, formatSpeed(speed)),
                            Toast.LENGTH_SHORT).show();
                }
            });
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(view -> {
                if (save(prefs.edit().remove(SpeedSettings.appKey(app.packageName)))) {
                    dialog.dismiss();
                    Toast.makeText(this, getString(R.string.app_reset, app.label), Toast.LENGTH_SHORT).show();
                }
            });
        });
        dialog.show();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static String formatSpeed(float speed) {
        return Float.toString(speed);
    }

    private static final class AppEntry {
        final String packageName;
        final String label;
        AppEntry(String packageName, String label) {
            this.packageName = packageName;
            this.label = label;
        }
    }

    private final class AppAdapter extends BaseAdapter {
        private final List<AppEntry> all;
        private final List<AppEntry> visible = new ArrayList<>();
        AppAdapter(List<AppEntry> apps) {
            all = apps;
            visible.addAll(apps);
        }
        void filter(String text) {
            String query = text.trim().toLowerCase(Locale.ROOT);
            visible.clear();
            for (AppEntry app : all) {
                if (app.label.toLowerCase(Locale.ROOT).contains(query)
                        || app.packageName.toLowerCase(Locale.ROOT).contains(query)) visible.add(app);
            }
            notifyDataSetChanged();
        }
        @Override public int getCount() { return visible.size(); }
        @Override public AppEntry getItem(int position) { return visible.get(position); }
        @Override public long getItemId(int position) { return getItem(position).packageName.hashCode(); }
        @Override public View getView(int position, View recycled, ViewGroup parent) {
            View row = recycled != null ? recycled : LayoutInflater.from(MainActivity.this)
                    .inflate(R.layout.item_app_speed, parent, false);
            AppEntry app = getItem(position);
            ((TextView) row.findViewById(R.id.appName)).setText(app.label);
            ((TextView) row.findViewById(R.id.appPackage)).setText(app.packageName);
            boolean custom = SpeedSettings.isValid(SpeedSettings.override(prefs::getFloat, app.packageName));
            String status = getString(custom ? R.string.speed_custom : R.string.speed_inherit,
                    formatSpeed(effectiveSpeed(app.packageName)));
            Button button = row.findViewById(R.id.appSpeedButton);
            button.setText(status);
            button.setTextColor(Color.parseColor(custom ? "#a8e6cf" : "#f4d19e"));
            button.setContentDescription(getString(R.string.app_speed_description, app.label,
                    status.replace('\n', ' ')));
            button.setOnClickListener(view -> editApp(app));
            return row;
        }
    }

    private void makePrefsReadable() {
        SpeedPreferences.makeSpeedFileReadable(prefs);
        File dataDir = new File(getApplicationInfo().dataDir);
        dataDir.setReadable(false, false);
        dataDir.setReadable(true, true);
        dataDir.setExecutable(true, false);
        File prefsDir = new File(dataDir, "shared_prefs");
        if (prefsDir.exists()) prefsDir.setReadable(true, false);
        File prefsFile = new File(getApplicationInfo().dataDir + "/shared_prefs/speed.xml");
        if (prefsFile.exists()) prefsFile.setReadable(true, false);
    }
}
