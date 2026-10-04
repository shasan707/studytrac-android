package app.studytrac;

import android.content.Context;
import android.content.SharedPreferences;

/** Small shared state: whether a focus lock is active, which apps are allowed, distraction count, stored alarms. */
public final class Prefs {
    private static final String FILE = "studytrac";

    private static SharedPreferences p(Context c) { return c.getSharedPreferences(FILE, Context.MODE_PRIVATE); }

    public static boolean lockActive(Context c) { return p(c).getLong("lock_until", 0) > System.currentTimeMillis(); }
    public static long lockUntil(Context c) { return p(c).getLong("lock_until", 0); }
    public static void setLock(Context c, long until, String allowed, String topic) {
        p(c).edit().putLong("lock_until", until).putString("allowed", allowed == null ? "" : allowed).putString("lock_topic", topic == null ? "" : topic).putInt("distractions", 0).apply();
    }
    public static void clearLock(Context c) { p(c).edit().putLong("lock_until", 0).apply(); }
    public static String allowed(Context c) { return p(c).getString("allowed", ""); }
    public static int distractions(Context c) { return p(c).getInt("distractions", 0); }
    public static int addDistraction(Context c) { int n = distractions(c) + 1; p(c).edit().putInt("distractions", n).apply(); return n; }
    public static long lastDistraction(Context c) { return p(c).getLong("last_distraction", 0); }
    public static void setLastDistraction(Context c, long t) { p(c).edit().putLong("last_distraction", t).apply(); }

    public static String alarmsJson(Context c) { return p(c).getString("alarms", "[]"); }
    public static void setAlarmsJson(Context c, String json) { p(c).edit().putString("alarms", json).apply(); }
}
