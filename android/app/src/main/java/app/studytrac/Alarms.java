package app.studytrac;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

/** Exact alarms that ring even when the app is closed. The list is stored so it survives a reboot. */
public final class Alarms {
    public static final String CHANNEL = "study_alarms";

    public static void ensureChannel(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm.getNotificationChannel(CHANNEL) != null) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL, "Review alarms", NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("Rings when a review is due");
        ch.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
        ch.enableVibration(true);
        ch.setBypassDnd(true);
        nm.createNotificationChannel(ch);
    }

    public static void replaceAll(Context c, JSONArray items) {
        cancelStored(c);
        JSONArray kept = new JSONArray();
        long now = System.currentTimeMillis();
        for (int i = 0; i < items.length(); i++) {
            JSONObject o = items.optJSONObject(i);
            if (o == null || o.optLong("at") <= now) continue;
            if (schedule(c, o)) kept.put(o);
        }
        Prefs.setAlarmsJson(c, kept.toString());
    }

    /** Re-arms stored alarms after a reboot or app update. */
    public static void rescheduleStored(Context c) {
        try { replaceAll(c, new JSONArray(Prefs.alarmsJson(c))); } catch (Exception ignored) {}
    }

    private static void cancelStored(Context c) {
        try {
            JSONArray old = new JSONArray(Prefs.alarmsJson(c));
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            for (int i = 0; i < old.length(); i++) am.cancel(pending(c, old.getJSONObject(i).optString("id"), null));
        } catch (Exception ignored) {}
    }

    public static boolean schedule(Context c, JSONObject o) {
        try {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            PendingIntent pi = pending(c, o.optString("id"), o);
            long at = o.optLong("at");
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            } else if (Build.VERSION.SDK_INT >= 23) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, at, pi);
            }
            return true;
        } catch (Exception e) { return false; }
    }

    private static PendingIntent pending(Context c, String id, JSONObject o) {
        Intent i = new Intent(c, AlarmReceiver.class).setAction("app.studytrac.ALARM").setData(android.net.Uri.parse("alarm://" + id));
        if (o != null) { i.putExtra("id", id); i.putExtra("title", o.optString("title", "Study Tracker")); i.putExtra("body", o.optString("body", "A review is due.")); }
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        return PendingIntent.getBroadcast(c, id.hashCode(), i, flags);
    }
}
