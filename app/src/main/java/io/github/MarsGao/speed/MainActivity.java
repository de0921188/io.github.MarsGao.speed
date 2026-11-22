package io.github.MarsGao.speed;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.Intent;
import android.net.Uri;
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
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.HorizontalScrollView;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.Arrays;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private SharedPreferences prefs;
    private AppAdapter appAdapter;
    private EditText searchInput;
    private Spinner appFilter;
    private TextView appCount;
    private boolean saving;
    private static final int ENABLE_IDLE_ACCESSIBILITY = 42;
    private int pendingIdleMinutes;
    private static final int EXPORT_SETTINGS = 40, IMPORT_SETTINGS = 41;
    private final java.util.concurrent.ExecutorService fileWorker =
            java.util.concurrent.Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        prefs = SpeedPreferences.open(this);
        makePrefsReadable();
        if (savedInstanceState != null) pendingIdleMinutes = savedInstanceState.getInt("pendingIdleMinutes");
        findViewById(R.id.idleSettings).setOnClickListener(view -> editIdleLock());
        refreshIdleLock();

        EditText globalInput = findViewById(R.id.editTextText);
        globalInput.setText(formatSpeed(effectiveSpeed(null)));
        addQuickSpeeds(findViewById(R.id.globalPresets), globalInput);
        Button saveGlobal = findViewById(R.id.button);
        saveGlobal.setOnClickListener(view -> {
            Float speed = readInput(globalInput);
            if (speed != null) save(prefs.edit().putFloat(SpeedSettings.GLOBAL_KEY, speed), () -> {
                globalInput.setText(formatSpeed(speed));
                Toast.makeText(this, R.string.global_saved, Toast.LENGTH_SHORT).show();
            });
        });

        appAdapter = new AppAdapter(loadApps());
        searchInput = findViewById(R.id.appSearch);
        appCount = findViewById(R.id.appCount);
        appFilter = findViewById(R.id.appFilter);
        ArrayAdapter<CharSequence> filters = ArrayAdapter.createFromResource(this,
                R.array.app_filters, android.R.layout.simple_spinner_item);
        filters.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        appFilter.setAdapter(filters);
        appFilter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (view instanceof TextView) ((TextView) view).setTextColor(Color.WHITE);
                refreshApps();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        findViewById(R.id.resetAppSpeeds).setOnClickListener(view -> confirmResetApps());
        findViewById(R.id.backupSettings).setOnClickListener(view -> new AlertDialog.Builder(this)
                .setTitle(R.string.backup_settings)
                .setItems(new String[]{getString(R.string.export_settings), getString(R.string.import_settings)},
                        (dialog, which) -> openBackupDocument(which == 0)).show());
        ListView apps = findViewById(R.id.appSpeedList);
        apps.setAdapter(appAdapter);
        apps.setEmptyView(findViewById(R.id.emptyApps));
        apps.setOnItemClickListener((parent, view, position, id) -> editApp(appAdapter.getItem(position)));
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                refreshApps();
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        refreshApps();
    }

    @Override
    protected void onRestoreInstanceState(Bundle state) {
        super.onRestoreInstanceState(state);
        // Text and Spinner restore after onCreate; reapply both to the backing list.
        refreshApps();
    }

    private void refreshApps() {
        refreshIdleLock();
        if (appAdapter == null || appFilter == null || searchInput == null) return;
        appAdapter.filter(searchInput.getText().toString(), appFilter.getSelectedItemPosition());
        int custom = 0;
        for (AppEntry app : appAdapter.all) {
            if (SpeedSettings.isValid(SpeedSettings.override(prefs::getFloat, app.packageName))) custom++;
        }
        appCount.setText(getString(R.string.app_count, appAdapter.getCount(), appAdapter.all.size(), custom));
    }

    @Override protected void onResume() {
        super.onResume();
        if (prefs != null) { refreshIdleLock(); IdleLockService.sync(this); }
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putInt("pendingIdleMinutes", pendingIdleMinutes);
        super.onSaveInstanceState(state);
    }

    private void refreshIdleLock() {
        if (prefs == null) return;
        Button button = findViewById(R.id.idleSettings);
        boolean enabled = SpeedSettings.idleEnabled(prefs::getFloat);
        button.setText(!enabled ? getString(R.string.idle_off) : !IdleLockRoot.active(this)
                ? getString(R.string.idle_permission_missing)
                : getString(R.string.idle_on, SpeedSettings.idleMinutes(prefs::getFloat)));
    }

    private void saveIdleLock(boolean enabled, int minutes) {
        save(prefs.edit().putFloat(SpeedSettings.IDLE_ENABLED, enabled ? 1f : 0f)
                .putFloat(SpeedSettings.IDLE_MINUTES, minutes), () -> {
            refreshIdleLock();
            IdleLockService.sync(this);
            Toast.makeText(this, R.string.idle_saved, Toast.LENGTH_LONG).show();
        });
    }

    private void editIdleLock() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(12), dp(24), dp(12));
        CheckBox enabled = new CheckBox(this);
        enabled.setText(R.string.idle_enable);
        enabled.setChecked(SpeedSettings.idleEnabled(prefs::getFloat));
        content.addView(enabled);
        TextView label = new TextView(this);
        label.setText(R.string.idle_minutes);
        content.addView(label);
        EditText minutes = new EditText(this);
        minutes.setInputType(InputType.TYPE_CLASS_NUMBER);
        minutes.setSingleLine(true);
        minutes.setSelectAllOnFocus(true);
        minutes.setContentDescription(getString(R.string.idle_minutes));
        minutes.setText(getString(R.string.idle_minutes_value, SpeedSettings.idleMinutes(prefs::getFloat)));
        content.addView(minutes);
        TextView help = new TextView(this);
        help.setText(R.string.idle_help);
        help.setTextSize(13);
        content.addView(help);
        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.addView(content);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(R.string.idle_title).setView(scroll)
                .setNegativeButton(R.string.cancel, null)
                .setNeutralButton(R.string.idle_system_settings, (d, w) -> {
                    try { startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))); }
                    catch (android.content.ActivityNotFoundException unavailable) {
                        Toast.makeText(this, R.string.idle_system_unavailable, Toast.LENGTH_LONG).show();
                    }
                })
                .setPositiveButton(R.string.save, null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            int value;
            try { value = Integer.parseInt(minutes.getText().toString().trim()); }
            catch (NumberFormatException invalid) { minutes.setError(getString(R.string.idle_minutes_error)); return; }
            if (!SpeedSettings.validIdleMinutes(value)) { minutes.setError(getString(R.string.idle_minutes_error)); return; }
            if (enabled.isChecked()) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                fileWorker.execute(() -> {
                    boolean granted = IdleLockRoot.authorize(this);
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) return;
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                        if (granted) { dialog.dismiss(); saveIdleLock(true, value); }
                        else Toast.makeText(this, R.string.idle_admin_required, Toast.LENGTH_LONG).show();
                    });
                });
            } else { dialog.dismiss(); saveIdleLock(false, value); }
        }));
        dialog.show();
    }

    private void confirmResetApps() {
        int count = 0;
        for (AppEntry app : appAdapter.all) {
            if (prefs.contains(SpeedSettings.appKey(app.packageName))) count++;
        }
        if (count == 0) {
            Toast.makeText(this, R.string.no_overrides, Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.reset_apps_title)
                .setMessage(getString(R.string.reset_apps_confirm, count, formatSpeed(effectiveSpeed(null))))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.reset_apps_action, (dialog, which) -> {
                    SharedPreferences.Editor editor = prefs.edit();
                    for (AppEntry app : appAdapter.all) editor.remove(SpeedSettings.appKey(app.packageName));
                    save(editor, () -> Toast.makeText(this, R.string.apps_reset, Toast.LENGTH_SHORT).show());
                }).show();
    }

    private void addQuickSpeeds(LinearLayout parent, EditText input) {
        TextView label = new TextView(this);
        label.setText(R.string.quick_speeds);
        label.setTextSize(12);
        // Match the input's color in both the dark page and the system dialog.
        label.setTextColor(input.getCurrentTextColor());
        parent.addView(label);
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        for (float speed : new float[]{1f, 1.5f, 2f, 2.5f, 3f}) {
            Button button = new Button(this);
            button.setText(getString(R.string.speed_value, formatSpeed(speed)));
            button.setContentDescription(getString(R.string.fill_speed, formatSpeed(speed)));
            button.setTextSize(13);
            button.setOnClickListener(view -> {
                input.setText(formatSpeed(speed));
                input.setError(null);
            });
            buttons.addView(button, new LinearLayout.LayoutParams(dp(68), dp(48)));
        }
        scroll.addView(buttons);
        parent.addView(scroll);
    }

    private List<AppEntry> loadApps() {
        String[] packages = getResources().getStringArray(R.array.xposedscope);
        String[] labels = getResources().getStringArray(R.array.supported_app_labels);
        List<AppEntry> apps = new ArrayList<>();
        PackageManager manager = getPackageManager();
        for (int i = 0; i < packages.length; i++) {
            String label = i < labels.length ? labels[i] : packages[i];
            boolean installed = false;
            try {
                label = manager.getApplicationLabel(manager.getApplicationInfo(packages[i], 0)).toString();
                installed = true;
            } catch (PackageManager.NameNotFoundException | RuntimeException ignored) {
                // Keep uninstalled supported apps configurable using the bundled label.
            }
            apps.add(new AppEntry(packages[i], label, installed));
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

    private void save(SharedPreferences.Editor editor, Runnable onSaved) {
        if (saving) return;
        saving = true;
        setControlsEnabled(findViewById(android.R.id.content), false);
        fileWorker.execute(() -> {
            boolean successful = false;
            try {
                successful = editor.commit();
                if (successful) makePrefsReadable();
            } catch (RuntimeException error) {
                successful = false;
                android.util.Log.w("VideoSpeed", "Settings write failed", error);
            }
            final boolean saved = successful;
            runOnUiThread(() -> {
                saving = false;
                if (isDestroyed() || isFinishing()) return;
                setControlsEnabled(findViewById(android.R.id.content), true);
                refreshApps();
                if (saved) onSaved.run();
                else Toast.makeText(this, R.string.save_failed, Toast.LENGTH_LONG).show();
            });
        });
    }

    private void setControlsEnabled(View view, boolean enabled) {
        view.setEnabled(enabled);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) setControlsEnabled(group.getChildAt(i), enabled);
        }
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

        CheckBox enabled = new CheckBox(this);
        enabled.setText(R.string.enable_app);
        enabled.setChecked(SpeedSettings.isEnabled(prefs::getFloat, app.packageName));
        content.addView(enabled);

        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setSelectAllOnFocus(true);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setHint(R.string.speed_hint);
        input.setContentDescription(getString(R.string.edit_app_speed, app.label));
        input.setText(formatSpeed(effectiveSpeed(app.packageName)));
        content.addView(input, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));
        addQuickSpeeds(content, input);
        TextView help = new TextView(this);
        help.setText(R.string.app_speed_help);
        help.setTextSize(13);
        content.addView(help);

        android.widget.ScrollView editorScroll = new android.widget.ScrollView(this);
        editorScroll.addView(content);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.edit_app_speed, app.label))
                .setView(editorScroll)
                .setPositiveButton(R.string.save, null)
                .setNeutralButton(R.string.use_global, null)
                .setNegativeButton(R.string.cancel, null)
                .create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                Float speed = readInput(input);
                boolean changed = enabled.isChecked() != SpeedSettings.isEnabled(prefs::getFloat, app.packageName);
                if (speed != null) save(prefs.edit().putFloat(SpeedSettings.appKey(app.packageName), speed)
                        .putFloat(SpeedSettings.enabledKey(app.packageName), enabled.isChecked() ? 1f : 0f), () -> {
                    dialog.dismiss();
                    Toast.makeText(this, changed ? getString(R.string.restart_app)
                                    : getString(R.string.app_saved, app.label, formatSpeed(speed)),
                            Toast.LENGTH_SHORT).show();
                });
            });
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(view -> {
                boolean changed = enabled.isChecked() != SpeedSettings.isEnabled(prefs::getFloat, app.packageName);
                save(prefs.edit().remove(SpeedSettings.appKey(app.packageName))
                        .putFloat(SpeedSettings.enabledKey(app.packageName), enabled.isChecked() ? 1f : 0f), () -> {
                    dialog.dismiss();
                    Toast.makeText(this, changed ? getString(R.string.restart_app)
                            : getString(R.string.app_reset, app.label), Toast.LENGTH_SHORT).show();
                });
            });
        });
        dialog.show();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private Set<String> supportedPackages() {
        return new LinkedHashSet<>(Arrays.asList(getResources().getStringArray(R.array.xposedscope)));
    }

    private void openBackupDocument(boolean exporting) {
        Intent intent = new Intent(exporting ? Intent.ACTION_CREATE_DOCUMENT : Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE).setType("text/plain");
        if (exporting) intent.putExtra(Intent.EXTRA_TITLE, "VideoSpeed-settings.txt");
        try { startActivityForResult(intent, exporting ? EXPORT_SETTINGS : IMPORT_SETTINGS); }
        catch (android.content.ActivityNotFoundException error) {
            Toast.makeText(this, R.string.document_unavailable, Toast.LENGTH_LONG).show();
        }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == ENABLE_IDLE_ACCESSIBILITY) {
            int minutes = pendingIdleMinutes;
            pendingIdleMinutes = 0;
            if (SpeedSettings.validIdleMinutes(minutes) && IdleLockRoot.active(this)) saveIdleLock(true, minutes);
            else Toast.makeText(this, R.string.idle_admin_required, Toast.LENGTH_LONG).show();
            return;
        }
        if (result != RESULT_OK || data == null || data.getData() == null
                || (request != EXPORT_SETTINGS && request != IMPORT_SETTINGS)) return;
        Uri uri = data.getData();
        Set<String> packages = supportedPackages();
        String snapshot = request == EXPORT_SETTINGS ? SpeedBackup.encode(prefs::getFloat, packages) : null;
        android.content.ContentResolver resolver = getContentResolver();
        fileWorker.execute(() -> {
            try {
                if (request == EXPORT_SETTINGS) {
                    try (java.io.OutputStream output = resolver.openOutputStream(uri, "wt")) {
                        if (output == null) throw new java.io.IOException("Cannot open output");
                        output.write(snapshot.getBytes(StandardCharsets.UTF_8));
                    }
                    runOnUiThread(() -> { if (!isDestroyed()) Toast.makeText(this, R.string.backup_exported, Toast.LENGTH_SHORT).show(); });
                } else {
                    java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
                    try (java.io.InputStream input = resolver.openInputStream(uri)) {
                        if (input == null) throw new java.io.IOException("Cannot open input");
                        byte[] buffer = new byte[4096];
                        int size;
                        while ((size = input.read(buffer)) != -1) {
                            if (bytes.size() + size > SpeedBackup.MAX_CHARS) throw new java.io.IOException("Backup too large");
                            bytes.write(buffer, 0, size);
                        }
                    }
                    Map<String, Float> values = SpeedBackup.parse(bytes.toString("UTF-8"), packages);
                    runOnUiThread(() -> { if (!isDestroyed() && !isFinishing()) previewImport(values); });
                }
            } catch (Exception error) {
                android.util.Log.w("VideoSpeed", "Settings backup failed", error);
                runOnUiThread(() -> { if (!isDestroyed()) Toast.makeText(this,
                        getString(R.string.backup_failed, error.getMessage()), Toast.LENGTH_LONG).show(); });
            }
        });
    }

    private void previewImport(Map<String, Float> values) {
        StringBuilder preview = new StringBuilder(getString(R.string.import_help)).append("\n\n");
        preview.append(getString(R.string.idle_import_help)).append("\n");
        if (values.containsKey(SpeedSettings.IDLE_ENABLED) || values.containsKey(SpeedSettings.IDLE_MINUTES)) {
            preview.append(SpeedSettings.IDLE_ENABLED).append("=").append(values.get(SpeedSettings.IDLE_ENABLED))
                    .append("\n").append(SpeedSettings.IDLE_MINUTES).append("=")
                    .append(values.get(SpeedSettings.IDLE_MINUTES)).append("\n\n");
        }
        preview.append(getString(R.string.speed_label)).append(": ").append(values.get(SpeedSettings.GLOBAL_KEY)).append("x\n");
        for (AppEntry app : appAdapter.all) {
            Float speed = values.get(SpeedSettings.appKey(app.packageName));
            Float enabled = values.get(SpeedSettings.enabledKey(app.packageName));
            if (speed != null || enabled != null) {
                preview.append("\n").append(app.label).append("\n").append(app.packageName).append("\n");
                if (speed != null) preview.append(speed).append("x ");
                if (enabled != null) preview.append(getString(enabled == 0f ? R.string.speed_disabled : R.string.enabled_label));
                preview.append("\n");
            }
        }
        TextView text = new TextView(this);
        text.setText(preview);
        text.setPadding(dp(24), dp(12), dp(24), dp(12));
        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.addView(text);
        new AlertDialog.Builder(this).setTitle(R.string.import_preview).setView(scroll)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.import_merge, (dialog, which) -> importValues(values, false))
                .setNeutralButton(R.string.import_replace, (dialog, which) -> importValues(values, true)).show();
    }

    private void importValues(Map<String, Float> values, boolean replace) {
        SharedPreferences.Editor editor = prefs.edit();
        if (replace) for (String pkg : supportedPackages()) {
            editor.remove(SpeedSettings.appKey(pkg));
            editor.remove(SpeedSettings.enabledKey(pkg));
        }
        for (Map.Entry<String, Float> entry : values.entrySet()) {
            float value = entry.getValue();
            if (SpeedSettings.IDLE_ENABLED.equals(entry.getKey()) && !IdleLockRoot.active(this)) value = 0f;
            editor.putFloat(entry.getKey(), value);
        }
        save(editor, () -> {
            ((EditText) findViewById(R.id.editTextText)).setText(formatSpeed(effectiveSpeed(null)));
            IdleLockService.sync(this);
            Toast.makeText(this, R.string.import_saved, Toast.LENGTH_LONG).show();
        });
    }

    @Override protected void onDestroy() {
        fileWorker.shutdown();
        super.onDestroy();
    }

    private static String formatSpeed(float speed) {
        return Float.toString(speed);
    }

    private static final class AppEntry {
        final String packageName;
        final String label;
        final boolean installed;
        AppEntry(String packageName, String label, boolean installed) {
            this.packageName = packageName;
            this.label = label;
            this.installed = installed;
        }
    }

    private final class AppAdapter extends BaseAdapter {
        private final List<AppEntry> all;
        private final List<AppEntry> visible = new ArrayList<>();
        AppAdapter(List<AppEntry> apps) {
            all = apps;
            visible.addAll(apps);
        }
        void filter(String text, int mode) {
            String query = text.trim().toLowerCase(Locale.ROOT);
            visible.clear();
            for (AppEntry app : all) {
                if (mode == 1 && !app.installed) continue;
                if (mode == 2 && !SpeedSettings.isValid(
                        SpeedSettings.override(prefs::getFloat, app.packageName))) continue;
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
            if (!SpeedSettings.isEnabled(prefs::getFloat, app.packageName)) status = getString(R.string.speed_disabled);
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
