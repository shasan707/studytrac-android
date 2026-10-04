package app.studytrac;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import org.json.JSONObject;

/** Ends a focus lock when its time is up, even if the app is in the background, and rings to say so. */
public class LockEndReceiver extends BroadcastReceiver {
    private static PendingIntent pi(Context c) {
        Intent i = new Intent(c, LockEndReceiver.class).setAction("app.studytrac.LOCK_END");
        return PendingIntent.getBroadcast(c, 4242, i, PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
    }
    public static void schedule(Context c, long at) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (Build.VERSION.SDK_INT >= 23) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi(c)); else am.setExact(AlarmManager.RTC_WAKEUP, at, pi(c));
    }
    public static void cancel(Context c) { ((AlarmManager) c.getSystemService(Context.ALARM_SERVICE)).cancel(pi(c)); }

    @Override
    public void onReceive(Context c, Intent intent) {
        Prefs.clearLock(c);
        try {
            JSONObject o = new JSONObject();
            o.put("id", "lockend"); o.put("at", System.currentTimeMillis() + 500);
            o.put("title", "Focus session complete"); o.put("body", "Nice work. Open the app to log your time.");
            Alarms.schedule(c, o);
        } catch (Exception ignored) {}
    }
}
