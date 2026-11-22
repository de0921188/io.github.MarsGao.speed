package io.github.MarsGao.speed;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.SystemClock;
import android.widget.*;
import android.content.Intent;
import java.nio.charset.StandardCharsets;

/** On-demand controls keep the main application list compact. */
public final class ModuleToolsActivity extends Activity {
    private TextView status;
    private String exportSnapshot;
    private boolean details;
    private final java.util.concurrent.ExecutorService worker = java.util.concurrent.Executors.newSingleThreadExecutor();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        LinearLayout body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * getResources().getDisplayMetrics().density); body.setPadding(pad,pad,pad,pad);
        scroll.addView(body); setContentView(scroll);
        status = new TextView(this); status.setTextIsSelectable(true);
        button(body,getString(R.string.tools_refresh),this::refresh);
        button(body,getString(R.string.tools_verify_root),() -> execute(() -> { boolean ok = IdleLockRoot.authorize(this);
            IdleLockService.sync(this); return ok ? getString(R.string.tools_root_ok) : getString(R.string.tools_root_failed); }));
        button(body,getString(R.string.tools_pause),() -> control(SpeedSettings.PAUSE_UNTIL,30));
        button(body,getString(R.string.tools_resume),() -> control(SpeedSettings.PAUSE_UNTIL,0));
        button(body,getString(R.string.tools_sleep_start),this::sleepDialog);
        button(body,getString(R.string.tools_sleep_cancel),() -> control(SpeedSettings.SLEEP_UNTIL,0));
        button(body,getString(R.string.tools_expand),() -> {details=!details;refresh();});
        button(body,getString(R.string.tools_export),() -> {
            exportSnapshot = snapshot();
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("text/plain").putExtra(Intent.EXTRA_TITLE,"VideoSpeed-diagnostics.txt");
            try { startActivityForResult(intent,51); }
            catch (android.content.ActivityNotFoundException e) { toast(getString(R.string.tools_no_picker)); }
        });
        body.addView(status);refresh();
    }

    private void button(LinearLayout parent,String title,Runnable action) {
        Button button = new Button(this); button.setText(title); parent.addView(button);
        button.setOnClickListener(v -> action.run());
    }

    private void sleepDialog() {
        EditText minutes = new EditText(this); minutes.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        minutes.setText("30"); minutes.setSelectAllOnFocus(true);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(getString(R.string.tools_sleep_title))
                .setMessage(getString(R.string.tools_sleep_help))
                .setView(minutes).setNegativeButton(getString(R.string.tools_cancel),null).setPositiveButton(getString(R.string.tools_start),null).create();
        dialog.setOnShowListener(v -> dialog.getButton(-1).setOnClickListener(b -> {
            try { int n = Integer.parseInt(minutes.getText().toString());
                if (!SpeedSettings.validIdleMinutes(n)) throw new NumberFormatException();
                dialog.dismiss();control(SpeedSettings.SLEEP_UNTIL,n);
            } catch (NumberFormatException e) { minutes.setError(getString(R.string.tools_minutes_invalid)); }
        }));dialog.show();
    }

    private void control(String key,int minutes) {
        execute(() -> {
            if (SpeedSettings.SLEEP_UNTIL.equals(key) && minutes > 0 && !IdleLockRoot.authorize(this))
                return getString(R.string.tools_root_required);
            TimerControls.set(this,key,minutes);
            getContentResolver().notifyChange(SpeedConfigProvider.SPEED_URI,null,0);
            return getString(R.string.tools_saved);
        });
    }

    private void execute(java.util.concurrent.Callable<String> action) {
        worker.execute(() -> {
            String result;
            try { result = action.call(); } catch (Exception e) { result = getString(R.string.tools_failed_prefix) + e.getMessage(); }
            final String text = result;
            runOnUiThread(() -> { if (!isDestroyed()) {toast(text);refresh();} });
        });
    }

    private String snapshot() {
        StringBuilder s = new StringBuilder(getString(R.string.tools_report_title))
                .append(getString(R.string.tools_root_cache)).append(IdleLockRoot.active(this) ? getString(R.string.tools_cache_only) : getString(R.string.tools_none))
                .append(getString(R.string.tools_service)).append(IdleLockService.isRunning() ? getString(R.string.tools_running) : getString(R.string.tools_not_running))
                .append(getString(R.string.tools_paused)).append(TimerControls.paused(this) ? getString(R.string.tools_yes) : getString(R.string.tools_no))
                .append(getString(R.string.tools_sleep_state)).append(remaining(SpeedSettings.SLEEP_UNTIL))
                .append(getString(R.string.tools_report_notice));
        try {
            java.io.File file = new java.io.File(getFilesDir(),"idle-recovery/heartbeat");
            String[] beat = new String(java.nio.file.Files.readAllBytes(file.toPath()),StandardCharsets.US_ASCII).trim().split("\\s+");
            if (beat.length!=2 || Integer.parseInt(beat[0])!=android.os.Process.myPid()) throw new java.io.IOException("Stale heartbeat process");
            long at = Long.parseLong(beat[1]);
            long now=SystemClock.elapsedRealtime()/1000;
            if (at<0 || at>now) throw new java.io.IOException("Invalid heartbeat");
            s.append(getString(R.string.tools_heartbeat)).append(now-at).append(getString(R.string.tools_seconds_suffix));
        } catch (Exception ignored) { s.append(getString(R.string.tools_no_heartbeat)); }
        try {
            java.io.File file = new java.io.File(getFilesDir(),"idle-recovery/recoveries");
            long count=Long.parseLong(new String(java.nio.file.Files.readAllBytes(file.toPath()),StandardCharsets.US_ASCII).trim());
            s.append(getString(R.string.tools_recovery_attempts)).append(count).append('\n');
        } catch (Exception ignored) { }
        java.util.Map<String,?> data = getSharedPreferences("diagnostics",MODE_PRIVATE).getAll();
        for (String pkg : getResources().getStringArray(R.array.xposedscope)) {
            s.append('\n').append(pkg).append('\n');
            try { String version=getPackageManager().getPackageInfo(pkg,0).versionName;
                s.append(getString(R.string.tools_app_version)).append(version).append('\n'); }
            catch (android.content.pm.PackageManager.NameNotFoundException ignored) { s.append(getString(R.string.tools_app_unavailable)); }
            for (String kind : new String[]{"hook","config","playback","idle"}) {
                Object value = data.get(pkg+"."+kind);
                s.append(getString(getResources().getIdentifier("diag_"+kind,"string",getPackageName()))).append(": ").append(value == null ? getString(R.string.tools_no_report) : localizeRecord(String.valueOf(value))).append('\n');
            }
        }
        return s.toString();
    }

    private String localizeRecord(String value) {
        String[] codes={"WAIT_PROGRESS","PLAYBACK_RESUMED","OBSERVING_IDLE_MINUTES=","CONFIG_RATE=","SOURCE=","RESTORE_RATE=","ROOT_COMMAND_FAILED","SLEEP_COMMAND_OK","IDLE_COMMAND_OK"};
        int[] labels={R.string.diag_wait_progress,R.string.diag_playback_resumed,R.string.diag_observing,R.string.diag_config_rate,R.string.diag_source,R.string.diag_restore_rate,R.string.diag_root_failed,R.string.diag_sleep_ok,R.string.diag_idle_ok};
        for (int i=0;i<codes.length;i++) value=value.replace(codes[i],getString(labels[i]));
        int separator=value.indexOf(" | ");
        if (separator>0) try {
            long at=Long.parseLong(value.substring(0,separator));
            java.util.Locale locale=getResources().getConfiguration().getLocales().get(0);
            value=java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT,java.text.DateFormat.MEDIUM,locale)
                    .format(new java.util.Date(at))+value.substring(separator);
        } catch (NumberFormatException ignored) { }
        return value;
    }

    private String remaining(String key) {
        long until = SpeedSettings.deadline(SpeedPreferences.open(this)::getFloat,key,TimerControls.boot(this));
        return until == 0 ? getString(R.string.tools_not_started) : getString(R.string.tools_remaining_prefix) + Math.max(0,(until-SystemClock.elapsedRealtime()+59999)/60000) + getString(R.string.minutes_suffix);
    }
    private void refresh() {
        String text=snapshot();
        int apps=text.indexOf("\n"+getResources().getStringArray(R.array.xposedscope)[0]+"\n");
        status.setText(details || apps<0 ? text : text.substring(0,apps)+getString(R.string.tools_expand_hint));
    }
    private void toast(String text) { Toast.makeText(this,text,Toast.LENGTH_LONG).show(); }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if (request != 51 || result != RESULT_OK || data == null || data.getData() == null) return;
        android.net.Uri uri = data.getData(); String text = exportSnapshot;
        if (text == null) { toast(getString(R.string.tools_export_restart));return; }
        execute(() -> { try (java.io.OutputStream output = getContentResolver().openOutputStream(uri,"wt")) {
            if (output == null) throw new java.io.IOException(getString(R.string.tools_file_unavailable));
            output.write(text.getBytes(StandardCharsets.UTF_8));
        } return getString(R.string.tools_exported); });
    }
    @Override protected void onDestroy() { worker.shutdown();super.onDestroy(); }
}
