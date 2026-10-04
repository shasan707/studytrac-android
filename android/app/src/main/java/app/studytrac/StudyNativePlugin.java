package app.studytrac;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.TextUtils;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Bridge between the web app and Android: focus lock (screen pinning + guard), real alarms,
 * and the permission wizard the phone needs (especially MIUI).
 */
@CapacitorPlugin(name = "StudyNative")
public class StudyNativePlugin extends Plugin {

    @PluginMethod
    public void status(PluginCall call) {
        Context c = getContext();
        JSObject r = new JSObject();
        r.put("platform", "android");
        r.put("version", Build.VERSION.SDK_INT);
        r.put("miui", isMiui());
        try { r.put("appVersion", c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName); } catch (Exception e) { r.put("appVersion", "?"); }
        r.put("lockActive", Prefs.lockActive(c));
        r.put("lockUntil", Prefs.lockUntil(c));
        r.put("distractions", Prefs.distractions(c));
        r.put("notifications", Build.VERSION.SDK_INT < 33 || c.checkSelfPermission("android.permission.POST_NOTIFICATIONS") == PackageManager.PERMISSION_GRANTED);
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        r.put("exactAlarms", Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms());
        r.put("overlay", Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(c));
        PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
        r.put("battery", pm.isIgnoringBatteryOptimizations(c.getPackageName()));
        r.put("guard", GuardService.isEnabled(c));
        call.resolve(r);
    }

    @PluginMethod
    public void requestNotifications(PluginCall call) {
        if (Build.VERSION.SDK_INT >= 33) getActivity().requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 77);
        call.resolve();
    }

    /** Opens the right Settings page: notifications, alarms, overlay, battery, guard (accessibility), autostart (MIUI). */
    @PluginMethod
    public void openSetting(PluginCall call) {
        String kind = call.getString("kind", "");
        Context c = getContext();
        Intent i = null;
        try {
            switch (kind) {
                case "alarms":
                    if (Build.VERSION.SDK_INT >= 31) i = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + c.getPackageName()));
                    break;
                case "overlay":
                    i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + c.getPackageName()));
                    break;
                case "battery":
                    i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + c.getPackageName()));
                    break;
                case "guard":
                    i = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                    break;
                case "autostart":
                    i = new Intent();
                    i.setComponent(new ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"));
                    if (c.getPackageManager().resolveActivity(i, 0) == null) i = null;
                    break;
                case "miuiperms":
                    i = new Intent("miui.intent.action.APP_PERM_EDITOR");
                    i.setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity");
                    i.putExtra("extra_pkgname", c.getPackageName());
                    if (c.getPackageManager().resolveActivity(i, 0) == null) i = null;
                    break;
                case "notifications":
                    i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, c.getPackageName());
                    break;
            }
            if (i == null) i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + c.getPackageName()));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
            call.resolve();
        } catch (Exception e) {
            call.reject("Could not open that settings page: " + e.getMessage());
        }
    }

    // ----- Focus lock -----
    @PluginMethod
    public void startLock(PluginCall call) {
        int minutes = call.getInt("minutes", 25);
        String topic = call.getString("topic", "");
        JSArray allowed = call.getArray("allowed", new JSArray());
        long until = minutes > 0 ? System.currentTimeMillis() + minutes * 60_000L : System.currentTimeMillis() + 12 * 3600_000L;
        Prefs.setLock(getContext(), until, TextUtils.join(",", toList(allowed)), topic);
        getActivity().runOnUiThread(() -> { try { getActivity().startLockTask(); } catch (Exception ignored) {} });
        LockEndReceiver.schedule(getContext(), until);
        JSObject r = new JSObject(); r.put("until", until); r.put("guard", GuardService.isEnabled(getContext()));
        call.resolve(r);
    }

    @PluginMethod
    public void stopLock(PluginCall call) {
        int n = Prefs.distractions(getContext());
        Prefs.clearLock(getContext());
        getActivity().runOnUiThread(() -> { try { getActivity().stopLockTask(); } catch (Exception ignored) {} });
        LockEndReceiver.cancel(getContext());
        JSObject r = new JSObject(); r.put("distractions", n);
        call.resolve(r);
    }

    // ----- Alarms -----
    /** items: [{id, at (epoch ms), title, body}] replaces every scheduled alarm. */
    @PluginMethod
    public void scheduleAlarms(PluginCall call) {
        try {
            JSArray items = call.getArray("items", new JSArray());
            Alarms.replaceAll(getContext(), new JSONArray(items.toString()));
            JSObject r = new JSObject(); r.put("count", items.length());
            call.resolve(r);
        } catch (Exception e) { call.reject(e.getMessage()); }
    }

    @PluginMethod
    public void cancelAlarms(PluginCall call) {
        Alarms.replaceAll(getContext(), new JSONArray());
        call.resolve();
    }

    @PluginMethod
    public void testAlarm(PluginCall call) {
        try {
            JSONArray a = new JSONArray();
            JSONObject o = new JSONObject(); o.put("id", "test"); o.put("at", System.currentTimeMillis() + 5000); o.put("title", "Study Tracker test alarm"); o.put("body", "This is how a review alarm will sound.");
            a.put(o);
            Alarms.schedule(getContext(), o);
            call.resolve();
        } catch (Exception e) { call.reject(e.getMessage()); }
    }

    private static java.util.List<String> toList(JSArray a) {
        java.util.List<String> out = new java.util.ArrayList<>();
        try { for (int i = 0; i < a.length(); i++) out.add(a.getString(i)); } catch (Exception ignored) {}
        return out;
    }
    private static boolean isMiui() {
        try {
            Class<?> c = Class.forName("android.os.SystemProperties");
            String v = (String) c.getMethod("get", String.class).invoke(null, "ro.miui.ui.version.name");
            return v != null && !v.isEmpty();
        } catch (Exception e) { return false; }
    }
}
