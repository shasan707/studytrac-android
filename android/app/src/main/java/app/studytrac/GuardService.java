package app.studytrac;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityEvent;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Level 2 lock. While a focus session is active, watches which app is in front.
 * Leaving for anything not on the allowed list counts a distraction and pulls the study screen back.
 */
public class GuardService extends AccessibilityService {
    private static final Set<String> ALWAYS_ALLOWED = new HashSet<>(Arrays.asList(
            "com.android.systemui", "com.android.incallui", "com.android.dialer", "com.google.android.dialer", "com.android.phone",
            "com.miui.home", "com.android.launcher3", "com.google.android.apps.nexuslauncher"));

    public static boolean isEnabled(Context c) {
        String enabled = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (TextUtils.isEmpty(enabled)) return false;
        String me = new ComponentName(c, GuardService.class).flattenToString();
        for (String s : enabled.split(":")) if (s.equalsIgnoreCase(me) || s.equalsIgnoreCase(new ComponentName(c, GuardService.class).flattenToShortString())) return true;
        return false;
    }

    @Override
    protected void onServiceConnected() {
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.notificationTimeout = 150;
        setServiceInfo(info);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || event.getPackageName() == null) return;
        if (!Prefs.lockActive(this)) return;
        String pkg = event.getPackageName().toString();
        if (pkg.equals(getPackageName()) || ALWAYS_ALLOWED.contains(pkg)) return;
        // The home screen counts as leaving; the dialer does not.
        for (String a : Prefs.allowed(this).split(",")) if (!a.isEmpty() && pkg.startsWith(a)) return;
        long now = System.currentTimeMillis();
        if (now - Prefs.lastDistraction(this) > 4000) { Prefs.addDistraction(this); Prefs.setLastDistraction(this, now); }
        Intent back = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        try { startActivity(back); } catch (Exception ignored) {}
    }

    @Override public void onInterrupt() {}
}
